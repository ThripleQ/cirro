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
| 改字号、字重、字体 | `ui/theme/Type.kt` | |
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
| 列表头部的收藏 / 播放 / 评论三按钮、滚动操作行 | `ui/playerbar/CollectionActions.kt` + `ui/screens/TrackListScreen.kt` |
| 登录 / 验证码接口（JNI op 30/31、cookie 导入） | `app/src/main/cpp/libnetease_jni.c` + `core/net/NeteaseOp.kt` |

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

## 兜底：不确定该动哪时

1. 先全局搜（`Ctrl+Shift+F`）你要改的功能名，例如"排行"、"缓存"、"进度"。
2. 还找不到，按"它是什么"定桶：界面→`ui/`，数据 / 播放 / 网络→`core/`，装配→`di/`（详见 navigation-map.md）。
3. 改完记得同步本表与导航地图——`docs/` 是唯一事实源。
