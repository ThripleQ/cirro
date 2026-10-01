# Nume 架构

Nume 是桌面端播放器 [Netune](https://github.com/ThripleQ/Netune) 的安卓版，
共享同一个网易云数据网关 [libnetease](https://github.com/ThripleQ/libnetease)。
核心目标是：**把 Netune 桌面端的缓存能力（分段缓存、Range 断点续传、seek 冷区并行下载、真实总时长）
原样搬进安卓**，同时让 UI / 播放 / 网络完全由 Kotlin 掌控。

---

## 一、分层总览

```
┌───────────────────────────────────────────────┐
│ UI 层 · Jetpack Compose + Material 3           │
│   登录 · 探索/搜索 · 我的 · 详情列表 · 播放页   │
│   ViewModel ←→ UiState                         │
└───────────────────┬───────────────────────────┘
                    ▼
┌───────────────────────────────────────────────┐
│ 核心层 · Core                                    │
│   Repository               数据聚合/编排         │
│   Media3 ExoPlayer + MediaSessionService 播放  │
│   SimpleCache + CacheDataSource ★  字节缓存    │
│   libnetease (JNI 数据网关)  签名/加密/API 解析   │
│   OkHttp                   真正收发 HTTP         │
└───────────────────┬───────────────────────────┘
                    ▼
┌───────────────────────────────────────────────┐
│ 持久化层 · Persistence                          │
│   Room DB         元数据(歌单/歌词/下载表)      │
│   分段缓存索引      原子 JSON(.tmp→rename)      │
│   磁盘缓存         分段音频 + 封面图            │
└───────────────────────────────────────────────┘
```

## 二、两条数据流

- **元数据路径**（歌单 / 歌词 / 封面 / 账号）：
  `Repository → NetEaseGateway → libnetease(JNI 签名与解析) → OkHttp → 网易云 API`，
  结果落 Room，供 UI 复用。
- **播放 / 缓存路径**（核心亮点）：
  `Player → Media3 ExoPlayer → CachingDataSource → 分段缓存 + 原子索引`。
  未命中时 CachingDataSource 触发 **Range 分段下载**（走 libnetease 生成的签名 URL + OkHttp），
  写分段、原子更新索引，并把数据流回 ExoPlayer。

## 三、关键决策

| 项 | 决策 | 说明 |
|---|---|---|
| 缓存模式 | Media3 `SimpleCache` + `CacheDataSource` | 采用官方引擎（原 ExoPlayer 新家）做字节缓存：编码无关、按字节随机访问；不再手写分段/索引，桌面端“分段+原子索引”经验仅作理解参考 |
| 索引/并发 | `SimpleCache` 内部索引 + LRU evictor | SimpleCache 自管哈希索引（崩溃更不易损坏），LRU 限额 512MiB；Range 分段与 seek 冷区由 CacheDataSource/官方能力承接 |
| JNI 边界 | 传输注入 | libnetease 照常跑高层调用，仅在发 HTTP 一刻经注册的 transport 回调到 Kotlin(OkHttp)。 |
| libnetease 接入 | git submodule 钉版 | 单一事实源，`git pull` 升级即可。 |
| 线程契约 | 串行派发 | request-kernel 全局单线程，`NetEaseGateway` 用单派发 + 锁串行所有调用。 |

## 四、原生桥（libnetease，当前已落地）

libnetease 以 `NE_USE_CURL=OFF` 编译，**不依赖 curl**。所有请求照常走 C 端高层服务函数
（`ne_search` / `ne_song_url_v1` / `ne_lyric` …），只是真正发送时由注入的 transport 承接。

- `libnetease_jni.c`：`JNI_OnLoad` 保存 `JavaVM` + 注册 natives；安装 transport
  （C→Kotlin 的 `NumeTransport.httpRequest` shim），组 `ne_http_resp`；一张 op 派发表覆盖 29 个服务函数。
- Kotlin `core/net/`：`NumeNative`(JNI 声明)、`NumeTransport`(OkHttp 同步发 + 收 Set-Cookie)、
  `ApiResult` / `NumeTransportOut`、`NeteaseOp`(与 C 对齐的 op 码)、`NetEaseGateway`(串行派发)。
- Cookie jar 由 `NumeApplication` 接 `filesDir/netease_cookies.json`，`setApiBase` 可显式覆盖默认
  `https://music.163.com`。

> 澄清：这不是"零反向回调"。C 侧保留了**一个窄的 transport 回调**（仅 HTTP 发送原语），
> 但所有真实网络 I/O 仍在 OkHttp，Set-Cookie 吸收与加解密留在 C，边界干净、便于调试。

## 五、缓存设计（Media3 字节缓存，当前已落地）

缓存不再手写，而是用 Media3 官方引擎（原 ExoPlayer）的 `SimpleCache` + `CacheDataSource`：

- **编码无关**：按字节缓存，mp3/aac/flac/wav 一律走同一管道，ExoPlayer 原生解码。
- **Range/seek**：命中段本地直读，未命中段由 `CacheDataSource` 走上游 Range 请求；seek 冷区由官方能力接管。
- **LRU evictor**：`LeastRecentlyUsedCacheEvictor`，配额 512MiB。
- **队列播放**：点一首歌以整榜为队列，`Player.setMediaItems(...)` 让 ⏮/⏭/自动连播生效。
- **离线**(后续)：Media3 提供 `DownloadManager`，接入后支持“下载到离线”整批。

## 六、播放 UI（PlayerDock 合体 + 通用伸展壳）

播放界面不是独立页面，而是**常驻底部 dock 与全屏播放页合体**在 `ui/playerbar/PlayerDock.kt`：

- **单组件、单状态**：一份 `PlayerDockState` + 官方 `AnchoredDraggableState`，三档
  `Closed / Half / Full`；`progress = offset / travelPx ∈ [0,2]`（0=收在迷你条胶囊、1=悬浮卡、
  2=盖满全屏），**只在 draw 阶段（graphicsLayer）读，绝不驱动挂载/组合**（铁律）。
- **两段式几何**：`[0,1]` 胶囊原位展开成悬浮卡（dock 仍可见可点），`[1,2]` 卡片放大盖满全屏
  （dock 淡出）；几何/圆角在 draw 阶段用 `graphicsLayer` + `drawWithContent(clipPath)` 画实心卡，
  零重组、不透底。
- **手势**：迷你条上滑 1:1 跟手、松手按位置/速度吸附；点迷你条/列表项 `open(toFull=true)` 直达全屏；
  收起箭头/返回键回落。
- **单布局连续形变**：卡片与全屏共用一套骨架（封面→标题→歌手→弹性空白→进度→控制），随 `sc`
  连续插值；全屏专属动作行/分割线/功能胶囊行以「高度+alpha」长出，播放键由裸图标长成
  `primaryContainer` 圆；标题/歌手全程在封面下方。
- **状态与真相源**：`rememberPlayerState`（元数据/播放态，低频）与 `rememberPlayerPosition`
  （进度，高频）拆开订阅，避免 250ms 轮询触发整页重组；播放控制走 `PlayerHolder`。
- **通用伸展壳**：`ui/components/ExpandableShell.kt` 提供「胶囊 → 全屏面板」的通用过渡
  （我的页「喜欢的音乐」等复用），动画/尾帧/BackHandler 内聚，并约定与起点胶囊同构的对齐契约。
- **单一时间基（伸展壳）**：整只壳只有**一个** `progressAnim: 0..1`，宽 / 高 / 平移 / 圆角 / hero /
  scrim 全部由同一个 `t` 派生。过去横竖各跑一条独立 tween（展开 320/380、收起 280/340），
  两条曲线在不同时刻收尾 ⇒ 中间帧宽高比失真（看着「不像一个整体在动」），且圆角挂在竖轴上
  与宽度脱节。层次感不再靠「不同时长」实现（那正是失真来源），而靠**同轴上的不同映射**：
  圆角前段恒定后段收敛、scrim 随 t 渐深、hero 在阈值处才交接。
- **圆角与父级衔接**：圆角 = `min(令牌圆角 × cornerTaper(t), 当前短边 × 0.5)`。
  `t ≤ CornerHold` 期间完全不收敛 ⇒ p=0 与父级卡片真圆角逐像素吻合；钳制项镜像平台行为，
  任何尺寸的起点卡片都不会被画成药丸，也不会在末段「啪」地变方。
- **统一动效令牌**：`ui/theme/Motion.kt` 是壳与 dock **共用**的「时长 + 曲线 + 形状」唯一来源
  （Material3 Emphasized 家族：展开用减速、收起用 Emphasized 保证末段停稳）。
  dock 的 spring 与浮层显隐时长也改为读此处；**dock 的细胞分裂几何刻意未动**——它在分裂点
  已有逐值连续性保证，无设备盲改风险高于收益。
- **列表操作行**：`ui/playerbar/CollectionActions.kt` 提供收藏/播放/评论三按钮，列表头部与
  滚动浮岛共用；`TrackListScreen` 上报三按钮是否滑出视口，供 dock 切换为操作行。

## 七、持久化

- Room(后续)：元数据（歌单、歌单曲目、歌词缓存、下载记录）。
- Media3 `SimpleCache` 自带哈希索引 + `StandaloneDatabaseProvider` 落库，独立于应用 UI 数据。

## 八、构建 / 性能 / 诊断

- **变体**：`assembleDebug`（开发用，无 R8/AOT）· `assembleRelease`（发布用，开 R8 +
  `isShrinkResources`）。
- **性能 A/B 一律用 release**：debug 的 Compose（无 R8/AOT、debuggable）UI 线程约慢 1.5–1.6×，
  足以让最简列表在 90Hz（预算 11.1ms）掉帧。真机实测 release 90th≈12–14ms / janky≈1%，
  debug 18–22ms / 3–9%。
- **R8 keep 规则**：`app/proguard-rules.pro` 保住 JNI 按「名字」引用的类/成员
  （`NumeNative` / `NumeTransport` / `NumeTransportOut` / `ApiResult`），否则原生传输运行期崩。
- **Baseline Profile**：`:baselineprofile` 模块（官方 `androidx.baselineprofile` + macrobenchmark），
  `./gradlew :app:generateReleaseBaselineProfile` 产出安装期 AOT profile；部分 ROM（如 vivo）的
  安装拦截会挡住 UTP 自动安装，需换设备/模拟器生成。
- **诊断工具**：Compose 编译器报告（`app/build/compose-reports|metrics`，查稳定性/可跳过性）、
  JankStats（`MainActivity`，按生命周期启停）、LeakCanary / OkHttp 日志 / StrictMode（仅 debug）、
  `Theme.Nume.Starting` 冷启动 splash。
- **构建提速**：`gradle.properties` 开 configuration cache + build cache。
- CI：GitHub Actions 在 push 到 `main`/`beta` 时构建并上传 debug APK。

## 九、色彩体系（生成式调色板）

**单一 seed 推导全套 M3 角色**：`tools/gen_palette.py` 改一行 `SEED` 重跑，即产出
`ui/theme/Palette.kt`（纯字面量、标注勿手改）与全部明暗两套角色。色彩数学全部留在
可执行、可验证的 Python 侧，Kotlin 只有常量——避免无编译环境下盲写色彩引擎。

色空间用 **OKLCH**（感知均匀，tone 即 L×100），不是 Google 的 HCT/CAM16：后者需约 150 行
实现、盲写风险不划算。色值与 Material Theme Builder 不逐位相同，但**色相协调关系与对比度
由脚本严格验证**（所有正文色对 ≥4.5:1、次要文字 ≥3.0:1，超色域保 L/H 二分收缩彩度）。

### 关键教训：M3 角色必须逐个显式传入
`Theme.kt` 过去只传了 20 个角色，`surfaceContainer*` / `surfaceDim` / `surfaceBright` /
`outlineVariant` / `error*` **全部漏传 → 回落 M3 内置值（带紫调的中性）**。而 App 最显眼的
表面（dock 浮岛、伸展壳、scrim）用的恰恰是这几个角色。结果品牌是红的、主角表面是紫灰的，
三种色相（品牌红 / 暖浅色面 / 冷蓝灰暗色面）互相打架。**"颜色不协调"的根源是漏传角色，
不是硬编码散落**（全 app 硬编码本就集中在一个文件）。

### 三条不可动摇的原则
1. **不偷改品牌色**：浅色 `primary` 用 SEED 本色，不套 M3 惯例 tone 40（那会把
   `#C92027` 压成 `#8B000F`）。白字压品牌红实测 5.6:1 达 AA，没有理由压暗。
2. **不偷改明度**：暗色 `surface` 锚现网实测档（`#111214`≈tone 17 → 取 20），不用 M3 的
   tone 6（`#020000`）——后者会把整个 App 静默调暗。这次只把**色相**从冷蓝灰拧到品牌红调。
3. **层级不许被打平**：`on_*` 先试 M3 规范 tone（次要文字 30/80，而非极端 10/90），
   只有对比度不达标才向大反差修正。否则 `onSurfaceVariant` 撞上 `onSurface`，主次文字同色。

脚本 `verify()` 会逐对打印 PASS/FAIL 并额外检查「主次容器可区分」「主次文字可区分」
「容器明度单调」，**不通过就拒绝写入**。

### on-image 墨色与 colorScheme 刻意解耦
封面是不受控位图，主题色压上去都可能不可读。可读性由图上暗色渐变保证，不由主题保证，
所以 `NumeInk.*`（图上文字/水印）在明暗主题下是同一组白色。若改用 `onSurface`，浅色主题
下深字压中亮度封面直接看不清。`NumeFade.*` 收纳必须"主题色 × 透明度"的散落的魔数。

### 动态取色（Material You）
`dynamicColor = true` 时 Android 12+ 会**整套替换**本调色板（`Palette.kt` 与 `NumeInk` 全失效）。
`MainActivity` 当前显式传 false：一是保住品牌红，二是动态色在部分机型派生偏深的
`onBackground`、暗色主题下文字看不清（那次修复的注释还在）。要重启先回归这两点。

## 十、路线图 / 当前状态

- [x] 项目骨架、主题、导航
- [x] libnetease 子模块接入 + 原生桥（NDK/CMake、transport 注入）
- [x] OkHttp transport 走通 libnetease 请求内核
- [x] Media3 字节缓存（SimpleCache + CacheDataSource）接进 ExoPlayer
- [x] 真机播放链路：榜单 → 曲目 → 签名 URL → 缓存 → 出声
- [x] 队列播放（整榜 ⏮/⏭/连播）
- [x] 前台服务 + 系统媒体通知
- [x] Hilt 依赖注入（AppModule 提供 gateway；Repository/PlaybackLauncher 注入）
- [x] ViewModel + UiState(StateFlow) 引入；屏幕只订阅 UiState、发事件，不再自取数据
- [x] type-safe 导航（@Serializable destination，目的地集中定义，替代字符串路径）
- [x] 我的页：登录（Cookie 粘贴 / 短信验证码）+ 喜欢的音乐 + 已购 + 收藏/创建的歌单
- [ ] 登录二维码（另做）
- [x] 播放页打磨（PlayerDock 合体：迷你条↔卡片↔全屏两段式、单布局连续形变、随机/循环接 ExoPlayer）
- [ ] 探索(Home) / 搜索页（目前仍是占位，纯展示）
- [ ] 离线下载（DownloadManager）
