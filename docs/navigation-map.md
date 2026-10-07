# 找东西的心智地图（navigation map）

目标：改了功能"能一次定位"，不靠背路径、不靠翻整个工程。三个桶 + 一个地图。

---

## 1. 三个桶：功能该放哪

代码只归三类，看"它是什么"定桶，不用记文件名：

| 桶 | 放什么 | 找它时想什么 |
|---|---|---|
| `ui/` | 界面：长什么样、点了干什么 | "图/交互" |
| `core/` | 数据从哪来、怎么播放、怎么联网 | "数据/播放/网络" |
| `di/` | 谁注入了谁、可替换的实现 | "装配/可换" |

一个类只属于一个桶。横跨三层的东西=还没归好位，是"找不到"的根源。

## 2. 桶内的功能分块（包）

每个功能一块：`ui/<功能>/<屏>Screen.kt + <屏>ViewModel.kt`（含其 `UiState`）。
现有块：

```
ui/
├── theme/         # 主题（颜色/字体/尺寸）
├── library/       # 排行榜列表（LibraryScreen + LibraryViewModel）
├── profile/       # 我的（ProfileViewModel：登录态+区块数据；TrackListViewModel：统一"壳+列表"页状态）
├── playerbar/     # 播放（PlayerDock.kt：常驻 dock + 全屏播放页**合体**，一份 PlayerDockState；
│                  #   rememberPlayerState/rememberPlayerPosition 是播放状态的唯一真相源；
│                  #   ActionNavRow 为滚动操作行（滑出头部按钮后出现），列表只负责上报）
├── components/    # 跨功能通用组件（ExpandableShell：胶囊→全屏通用伸展壳；
│                  #   CommentsOpener：评论浮层的打开入口，由 CirroApp 在根上提供；
│                  #   CoverUrls：封面地址的唯一出口；PayTagRules / PayBadge：徽标判据与画法）
└── screens/       # 布局主体（哑组件，跨功能）
    ├── HomeScreen.kt        # 探索 tab 首页（横滑卡片行 + 展开壳内嵌 TrackListScreen）
    ├── LibraryScreen.kt
    ├── SearchScreen.kt      # 搜索 tab（落地页 + 结果列表）
    ├── ProfileScreen.kt     # 我的：登录入口 + 喜欢/已购/歌单区块 + 登录对话框
    ├── TrackListScreen.kt   # 统一详情页主入口（编排 / 三态门 / 排序面板 / 分享）
    ├── TrackListMetrics.kt  # 版式令牌 + 本屏私有常量（糊底、退场编舞、色晕）
    ├── TrackListChrome.kt   # 外壳：状态栏让位、页底糊底、顶部标题条
    ├── TrackListPanel.kt    # 头部（封面+标题+作者+简介）+ 三胶囊 + 播放全部行
    └── TrackListRows.kt     # 曲目行 + 骨架
                             # 五件套同属「榜单/歌单/专辑/喜欢/已购」这一个详情页（2026-10-06 拆）
    └── WebLoginScreen.kt    # Web 登录（登录的单一入口）

core/
├── model/         # 领域模型的唯一去处（= **接口返回的形状**）：Track / TrackCollection /
│                  #   Profile / Artist / Chart / Comment / Lyric / Podcast / Search / Home
│                  #   零 Android / Compose / Dagger 依赖；**控制流类型不进这里**
│                  #   （ActionResult 在 InteractionRepository、RequestFailedException 在 RepoErrors）
├── net/           # libnetease JNI 网关（数据出口）
├── repo/          # Repository：取/转换数据（ChartRepository / ProfileRepository /
│                  #   TrackCollection 壳解析 / TrackParser 曲目解析 /
│                  #   InteractionRepository 写操作（红心·收藏·评论点赞）/
│                  #   LibraryStateStore「当前账号收藏了什么」的进程级镜像）
├── db/            # Room：歌单壳 + 曲目表的离线缓存（CollectionCache / CollectionDao / CollectionEntity）
├── util/          # 与 Android 解耦的小件（RefreshGate 刷新冷却 / Clock / Diagnostics）
└── playback/      # 播放四件套：PlayerHolder（进程级播放器+状态+错误恢复+随机/循环）/
                   #   PlaybackLauncher（播放入口+补队列）/ PlaybackService（后台+通知）/
                   #   PlaybackCache（边播边缓存）
```

> 注：原 `ui/chart/` 包与 `PlayerScreen.kt` 已删除——单榜曲目列表并入统一
> `ui/screens/TrackListScreen.kt` + `ui/profile/TrackListViewModel.kt`；播放页并入 `ui/playerbar/PlayerDock.kt`。

新增偶发：`ui/<功能>/` 建"Screen + ViewModel + UiState"，再把导航目的地加进 `CirroApp`。三步，无其他。

## 3. 找到一件东西的三步

1. **IDE 全局搜**（`Ctrl+Shift+F`）：按功能/术语名搜——前提是命名统一（见下）。
2. **按桶猜路径**：它是 UI / 数据 / 播放？进对应桶找文件。
3. **查 docs + README**：架构地图（architecture.md）选层，纪律（composing-code.md）查规，本文件查归位。

## 4. 命名统一（全局搜的前提）

- 一个东西一个词：歌曲一律 `Track`（禁混 `Song`/`audioItem`）。
- 每屏状态统一 `XxxUiState`（sealed：`Loading/Error/Ready`）。
- 播放状态统一走 `rememberPlayerState` / `rememberPlayerPosition`（`ui/playerbar/PlayerDock.kt`），迷你条与播放页共用，不再各写各的 listener + 轮询。
- 播放控制（播放/暂停/切歌/seek/随机/循环）统一走 `PlayerHolder.togglePlay/skipNext/skipPrevious/seekTo/toggleShuffle/cycleRepeat`，含错误状态恢复。
- 事件触发统一走 ViewModel。
- 目的地类型统一 `@Serializable`，集中在 `CirroApp`。

命名乱 → 全局搜搜不齐 → 找不到。这是"找不到"的第一杀手。

## 5. 找不到=通常是这三件事没做好

- 命名不统一（→ 第 4 节）
- 职责横跨多类（→ 职责守恒，composing-code.md §2）
- 改动没更新地图（→ 改完 `docs/` 同步，architecture 是唯一事实源）