# 改动点速查表（change map）

目标：想改一件事时，先查这一行，就知道该动哪个文件，不用翻整个工程。

本表和导航地图（navigation-map.md）是正反两张表：那边回答"这东西在哪"，这边回答"想改 X 去改哪"。

---

## 1. 界面：页面长什么样

| 我想… | 去改 | 备注 |
|---|---|---|
| 改某个页面的文字 / 颜色 / 间距 / 按钮 | `ui/screens/<屏>Screen.kt` | 改界面基本只动这一个文件 |
| 改迷你条 / 播放页（含手势、两段式展开） | `ui/playerbar/PlayerDock.kt` | dock + 全屏播放页合体，同一文件 |
| 改「胶囊→全屏」通用伸展壳的动画 / 尾帧 | `ui/components/ExpandableShell.kt` | 我的页喜欢的音乐等复用；单一时间基，宽高/圆角/hero 全由同一个 t 派生 |
| 改动画**时长 / 曲线 / 圆角节奏**（壳与 dock 共用） | `ui/theme/Motion.kt` | 动效令牌唯一来源；两处手感不一致或想整体调快调慢，只改这里 |
| 改品牌色 / 整套配色（明暗一起变） | `tools/gen_palette.py` 顶部配置块 → 重跑生成 | `SEED` 换品牌色；`SECONDARY_HUE` / `TERTIARY_HUE` / `NEUTRAL_HUE` / `CHROMA` 换色相分工；`DARK_SURFACE_TONE` 换暗色黑度。**别手改 `Palette.kt`**（机器生成）；`--write` 会先验 WCAG 与层级再落盘，不通过拒绝写入 |
| 改启动图 / 窗口底色（与主题同步的两处平台色） | `res/values/colors.xml` + `res/values-night/colors.xml` + `res/values/themes.xml` | 都锚 Compose 的 `surface`；不同步会在启动/旋转时闪一帧反差色 |
| 改 M3 角色 → `ColorScheme` 的映射 | `ui/theme/Theme.kt` | 角色必须逐个显式传，漏传会回落 M3 内置紫灰 |
| 改图上文字/水印/遮罩等浮层墨色 | `ui/theme/Color.kt`（`NumeInk` / `NumeFade`） | 与 colorScheme 解耦，明暗共用 |
| 改字号、字重、字体 | `ui/theme/Type.kt` |
| 改歌曲行的**付费 / VIP 徽标**（三态样式、判据、位置） | `ui/components/PayBadge.kt`（`PayTag` / `payTagsOf` / `NumePayBadge`）；挂载点：`NumeMediaRow` / `TrackListRow` 的 `payTags` 参数，调用处一律 `rememberPayTags(track)` |
| 改徽标的**颜色**（红=需购买 / 蓝=已购） | `ui/theme/Color.kt` 的 `NumePay`（明暗各一档，按 `surface` 亮度选） |
| 改**「我买了什么」的判据** | `core/repo/LibraryStateStore.kt` 的 `isOwned`（已购单曲 ∪ 已购数字专辑）；两份清单在 `ensureOwnedLoaded` 里拉全 |
| 让徽标出现在**新的列表**里 | 该列表的行组件加 `payTags = rememberPayTags(track)`；不需要额外请求（镜像在 `NumeApp` 根上已经提供）。**前提是那些 `Track` 的 `fee` 有值** —— 只要来源是 song 对象（走 `parseTracks`）就自动有；来源不是 song 对象的要自己补，见下一行 |
| 改**「已购单曲」屏的档位**来源 | `core/repo/ProfileRepository.kt` 的 `purchasedSongs` —— 那个端点回的是**购买记录**（`songId`/`vip`/`sq`… 28 个键，**没有 `fee`**），所以拉完后按 songId 另发一次 `v3/song/detail` 补 `fee`/`durationMs`。**别改用购买记录里的 `vip` 字段**：样本全是 VIP 歌、无法证伪，且 song 对象里没有这个键可对照 |
| 徽标用的圆角 | `ui/theme/Shape.kt` 的 `NumeShape.Badge`（2dp，实测 kanade 截图 4px@d3 ≈ 1.3dp） | |
| 改底部导航胶囊：加删 tab、换图标、改顺序 | `NumeApp.kt` | 导航目的地也集中在这一个文件 |
| 改点某处跳到哪个页面 | `NumeApp.kt` | 跳转逻辑只在这里 |

## 2. 数据：列表里有什么、怎么排

| 我想… | 去改 |
|---|---|
| 排行榜页显示哪些榜单、什么顺序 | `ui/library/LibraryViewModel.kt` |
| 单榜 / 歌单 / 专辑 / 喜欢 / 已购曲目列表的排序 / 筛选 | `ui/profile/TrackListViewModel.kt` | 统一详情页共用 |
| 数据从哪取、怎么转换（网络规则） | `core/repo/ChartRepository.kt`、`core/repo/ProfileRepository.kt` |
| 曲目 JSON 怎么解析成 `Track` | `core/repo/TrackParser.kt` |
| 网络请求 / 签名 / 解析（基本不用动） | `core/net/` |

## 2.5 我的 / 登录 / 账号数据

| 我想… | 去改 |
|---|---|
| “我的”页区块（喜欢 / 已购 / 歌单）怎么摆、点哪跳哪 | `ui/screens/ProfileScreen.kt` |
| “我的”页状态：登录态 + 各区块数据加载 | `ui/profile/ProfileViewModel.kt` |
| 登录对话框（Cookie 粘贴 / 短信验证码） | `ui/screens/ProfileScreen.kt` 内 `LoginDialog` |
| 账号 / 喜欢 / 已购 / 歌单的数据获取与解析 | `core/repo/ProfileRepository.kt` |
| 曲目列表页（喜欢 / 已购 / 歌单 / 专辑） | `ui/screens/TrackListScreen.kt` + `ui/profile/TrackListViewModel.kt` |
| 曲目列表页的版式与间距（头部 / 胶囊 / 面板 / 糊底 / 行） | `ui/screens/TrackListScreen.kt`：令牌在 `TrackListMetrics`，各区块是紧随其后的 private 组件 |
| 曲目行 = nume 条目卡（8dp 外缩 + `surfaceContainer`） | `TrackListRow` 里用 `ui/components/Containers.kt` 的 `numeEntrySurface()`；面板底铺在整条 item 上，卡缝才不漏糊底 |
| 「播放全部」行吸顶 + 糊底随头部退场 | `TrackListScreen` 的 `stickyHeader(key = "playall")` + `washExit`（头部 item 的偏移）；停靠线由 `LazyColumn` 的固定 `padding(top = TrackListMetrics.PanelStickyTop)` 撑出（8dp，只在状态栏下一点），糊底见 `TrackListBackdrop` |
| 顶部标题条（类型标签 ↔ 实际标题随滚动交叉淡变、单行截断） | `TrackListScreen` 的 `TrackListTitleBar`（高度 = `TrackListMetrics.PanelStickyTop`，底色随 `washExit` 淡入）；类型标签取 `TrackListSource.label` |
| 吸顶后的容器色条 + 内容圆角纸（纸的上沿 = 标题条下沿） | 容器色：`TrackListBackdrop` 里 `graphicsLayer { alpha = exit }` + `background(surfaceContainer)`；纸：`TrackListScreen` 内层 `Box` 的 `.offset { paperTop }` + `Modifier.clip(SheetCorner)` + `background(surface)`（半径同 `ui/screens/HomeContent.kt` 的 `HomeSheetRadius`） |
| 头部封面离状态栏 68dp（hero 终点） | `TrackListMetrics.HeroCoverTop`，落成头部 item 自己的上内缩 `HeaderTopInset`；`CoverExpandShell` 的 `heroCoverTop` 参数须同源 |
| ~~底部滚动操作行（滑出头部按钮后出现的三按钮）~~ | **已全局删除，别再加回来** | `ActionNavRow` / `actionVisible` / `actionsOffscreen` 上报链都没了；dock 只有「迷你条 + 导航行」两段，「播放全部」是列表页自己的吸顶行（见上一行），评论入口只在全屏播放页 |
| 大卡 → 全屏列表时 hero 封面飞到哪里 | `ui/screens/{HomeExpandShell,ProfilePanels}.kt` 传的 `heroCover*`（必须与 `TrackListMetrics` 一致） |
| 登录 / 验证码接口（JNI op 30/31、cookie 导入） | `app/src/main/cpp/libnetease_jni.c` + `core/net/NeteaseOp.kt` |

## 2.6 点赞 / 收藏 / 分享 / 排序（写操作，2026-10-06）

| 我想… | 去改 |
|---|---|
| 红心歌曲 / 收藏歌单 / 收藏专辑 / 评论点赞这些**写**操作 | `core/repo/InteractionRepository.kt` — 统一返 `ActionResult`。服务端把失败放在 **HTTP 200 的 `message`** 里（下架歌曲回「下架歌曲无法收藏」，code=401），**只看 `err`/`code` 判不出任何东西** |
| 「当前账号收藏了什么」的内存镜像（喜欢歌曲 id 全量 / 已收藏专辑 id） | `core/repo/LibraryStateStore.kt` — 每账号拉一次、之后本地查表，切歌零请求。**歌单的收藏态不走这里**：`/weapi/v6/playlist/detail` 自带 `subscribed`，跟着 `TrackCollection` 走 |
| 全屏播放页那颗红心的状态与动作 | `ui/playerbar/PlayerActionsViewModel.kt`（状态取自上面那份镜像） |
| 评论浮层从哪打开（播放页 / 歌单页头部胶囊 / 专辑页） | 入口是 `ui/components/CommentsOpener.kt` 的 `LocalCommentsOpener`，**由 `NumeApp.kt` 在根上提供**；浮层按 `threadId` 工作（`CommentThread.song/playlist/album`），不再只认单曲 |
| 曲目列表页的「排序」（5 档，纯本地重排） | `ui/profile/TrackListViewModel.kt` 的 `TrackSort` + `applySort`（中文走拼音 Collator，原始顺序另存一份）；面板 UI 在 `TrackListScreen.kt` 的 `TrackListSortSheet` |
| 曲目列表页的「分享」文案与链接 | `TrackListScreen.kt` 的 `shareUrlOf` —— 只有歌单 / 榜单 / 专辑有公开页面，其余只分享文字（硬拼 id 会打开不相干的歌单） |
| 新增一个 native 接口（C → Kotlin 四处联动） | `libnetease/include/netease/services.h` + `src/service/services.c` → `app/src/main/cpp/libnetease_jni.c` 的 switch → `core/net/NeteaseOp.kt`。**当前号段用到 52**，新增从 53 起 |

## 3. 播放：点歌、进度、后台、缓存

| 我想… | 去改 |
|---|---|
| 开始播放的入口 / 行为 | `core/playback/PlaybackLauncher.kt` |
| 播放状态（当前歌、暂停 / 继续、进度、随机 / 循环）给界面用 | `core/playback/PlayerHolder.kt` |
| 播放控制（播放 / 切歌 / seek / 随机 / 循环） | `core/playback/PlayerHolder.kt` |
| 后台播放通知 / 服务生命周期 | `core/playback/PlaybackService.kt` |
| 边播边缓存的策略 | `core/playback/PlaybackCache.kt` |
| 播放页 / 迷你条界面与手势 | `ui/playerbar/PlayerDock.kt` |

## 4. 装配与其他

| 我想… | 去改 |
|---|---|
| 谁注入了谁、换个实现 | `di/AppModule.kt` |
| 应用启动时做的初始化 | `NumeApplication.kt`、`MainActivity.kt` |

## 5. 性能 / 诊断 / 构建

| 我想… | 去改 |
|---|---|
| 调 release 优化（R8 / 资源压缩） | `app/build.gradle.kts` 的 `buildTypes.release` |
| JNI 被 R8 裁剪/改名导致运行期崩 | `app/proguard-rules.pro` |
| 看 Compose 类稳定性 / composable 可跳过性 | 构建后 `app/build/compose-reports/`、`compose-metrics/`（开关在 `app/build.gradle.kts` 的 `composeCompiler{}`） |
| 线上掉帧统计 / 冷启动 splash / 启动初始化 | `MainActivity.kt`（JankStats、installSplashScreen） |
| StrictMode / 全局初始化 / 图片加载 | `NumeApplication.kt` |
| HTTP 请求日志（仅 debug） | `core/net/NumeTransport.kt`（`BuildConfig.DEBUG` 下 BASIC 级） |
| 生成/调整 Baseline Profile | `baselineprofile/`（`BaselineProfileGenerator.kt`）、`./gradlew :app:generateReleaseBaselineProfile` |
| 构建提速（配置/构建缓存） | `gradle.properties` |

---

## 6. 解析与单元测试（2026-10-06 起）

> 这些端点**没有公开文档**，键名是探针实测猜出来的。所以「JSON → 领域对象」这一层
> 单独成文件、**零 Android 依赖**，可以脱离设备断言。判据错了不崩，只会变成「封面
> 全灰 / 徽标不画 / 红心不亮」这类**静默**错误 —— 单测是唯一的护栏。

| 我想… | 去改（实现） | 去改（测试） |
|---|---|---|
| 改歌曲解析（两套字段、`fee`、`albumId`、封面升 https） | `core/repo/TrackParser.kt` | `app/src/test/.../core/repo/TrackParserTest.kt` |
| 改首页解析（推荐歌单 / 雷达卡 / 猜你喜欢 block） | `core/repo/HomeParsers.kt` | `HomeParsersTest.kt` |
| 改「已购 / 收藏 / 红心」的 id 镜像解析 | `core/repo/OwnedParsers.kt` | `OwnedParsersTest.kt` |
| 改付费 / VIP 徽标的**判据**（哪个 fee 标什么） | `ui/components/PayTagRules.kt`（纯 Kotlin，别搬回 `PayBadge.kt`） | `app/src/test/.../ui/components/PayTagRulesTest.kt` |
| 改徽标的**画法**（框、字号、颜色、间距） | `ui/components/PayBadge.kt` | 尺寸是照 kanade 截图逐像素量的，改前先读那里的注释 |
| 跑全部单测 | `./gradlew :app:testDebugUnitTest` | 纯 JVM，不需模拟器；CI（build.yml / release.yml）在出包前必跑 |

**加新解析时的两条纪律**：① 解析函数放进上面那几个 `*Parsers.kt`，**不要**塞进带
`Log` / `BuildConfig` / Compose 的 Repository 文件里（那样就测不了）；
② 断言里写清「这条判据是哪次实测来的」。**别为了让测试变绿而改断言** —— 那等于把实测
过的口径换成猜测。

---

## 兜底：不确定该动哪时

1. 先全局搜（`Ctrl+Shift+F`）你要改的功能名，例如"排行"、"缓存"、"进度"。
2. 还找不到，按"它是什么"定桶：界面→`ui/`，数据 / 播放 / 网络→`core/`，装配→`di/`（详见 navigation-map.md）。
3. 改完记得同步本表与导航地图——`docs/` 是唯一事实源。
