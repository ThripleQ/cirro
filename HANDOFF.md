# 交接任务清单：播放页布局调整

> 写于 2026-10-01。上一任 agent 因 TraeWork 积分耗尽交接。请先通读本文件再动手。

---

## 0. 一句话任务

把播放页「**歌名 / 歌手 / 红心 / 评论**」这一块，改成网易云音乐播放页的排布：
**同一排 —— 左侧歌名+歌手上下叠放，右侧红心+评论；歌手在歌名下面；歌名和歌手文字过长时横向跑马（不截断）。**

背景：用户觉得播放页「太乱了、东西太多」，先从这一块开始改。

---

## 1. 仓库 / 设备 / 环境

| 项 | 值 |
|---|---|
| 仓库 | `C:\Users\liuca\AppData\Roaming\TRAE SOLO CN\ModularData\ai-agent\work-mode-projects\6a8e36a0fb66796b8b24ca44\nume` |
| 包名 | `com.thripleq.nume`（Android，Compose + Hilt + Media3） |
| 分支 | `main`，本地 HEAD = 远端 = **`20d8e94`** |
| 手机 | 设备号 `34738582830059P` |
| adb | `C:\Users\liuca\AppData\Local\Android\Sdk\platform-tools\adb.exe` |
| build-tools | `C:\Users\liuca\AppData\Local\Android\Sdk\build-tools\36.0.0` |

### ⚠️ 工作区有未提交改动，**不要动、不要提交**
- `app/src/main/java/com/thripleq/nume/ui/screens/ProfileCards.kt` —— 用户自己加的 1 行 import
- `libnetease` —— 子模块指针（用户自己更新的）

这两处是用户的其他在途改动，与本次任务无关。

---

## 2. 本次会话已完成（已提交并推送）

| commit | 内容 |
|---|---|
| `9a64804` | `revert(player)` 把播放页/卡片手感回退到改动前（用户对上一版手感不满意） |
| `20d8e94` | `feat(player)` 卡片↔全屏落档改缓入缓出 + 显式接入 flingBehavior（用户已确认「差不多」） |

`20d8e94` 涉及文件：
- 新增 `app/src/main/java/com/thripleq/nume/ui/playerbar/SheetFlingBehavior.kt`
- `ui/theme/Motion.kt` —— 新增 `sheetFullFrom(remain)`：中心对称缓入缓出曲线，时长 320–540ms 随剩余行程缩放
- `ui/playerbar/PlayerDockState.kt` —— `AnchoredDraggableState` 改用新构造器，新增并注入 `flingBehavior`
- `ui/playerbar/PlayerDock.kt` / `ui/playerbar/PlayerPage.kt` —— `anchoredDraggable` 显式传 `flingBehavior`

### 重要教训（避免重复踩坑）
> `Modifier.anchoredDraggable` **不显式传 `flingBehavior`** 时，库会退回写死的默认行为（`NoOpDecayAnimationSpec` 无惯性 + `FastOutSlowIn` 只减速），`AnchoredDraggableState` 上的旧构造器参数（positionalThreshold / velocityThreshold / snapAnimationSpec / decayAnimationSpec）在新版 Compose **读不到**。
> 这就是此前「改了很多参数但手感完全没变」的根因。**任何手感参数都必须经由自定义 `FlingBehavior` 显式接入。**

---

## 3. 待办（核心任务）：播放页「歌名/歌手/红心/评论」排布

### 3.1 目标排布（参照网易云音乐，已用真机截图确认）

```
┌─────────────────────────────────────────────┐
│  [歌名 · 大字 · 居中 · 过长跑马]      [♥ 19w+] │
│  [歌手 · 小字 · 居中 · 过长跑马 >]    [💬 999+] │
└─────────────────────────────────────────────┘
```

- 整体是**一排**：左侧「歌名 + 歌手」上下叠放（居中），右侧「红心 + 评论」上下叠放
- 歌名、歌手**超过一行宽度时横向跑马滚动**，不再省略号截断
- 网易云的文字块约占宽度 2/3，右侧图标+数量占 1/3

### 3.2 代码位置

文件：`app/src/main/java/com/thripleq/nume/ui/playerbar/PlayerPage.kt`
函数：`PlayerPageContent(...)`，约 **381–722 行**

Column 从上到下（行号为当前 `20d8e94` 版本）：

| 顺序 | 行号 | 内容 | 备注 |
|---|---|---|---|
| 1 | 464 | `Spacer` 让出状态栏 | 随 `sc` 长出 |
| 2 | 468–503 | 封面 / 歌词 Box | 点封面切歌词 |
| 3 | 508–516 | **歌名** Text | `fillMaxWidth` + `textAlign=Center` + `maxLines=1` + `Ellipsis` ← **要改跑马** |
| 4 | 518–529 | **歌手** Text | 同上 ← **要改跑马** |
| 5 | 531 | `Spacer(weight(1f))` | 弹性空白 |
| 6 | 534–563 | **动作行** | 左：红心 `Favorite` + 评论 `Chat`；右：播放列表 `QueueMusic`；`height=48dp*sc`、`alpha=sc` ← **红心/评论要从这里挪到歌名旁** |
| 7 | 565–570 | 分割线 | 随 `sc` |
| 8 | 574–615 | 进度条 Slider + 左右时间 | |
| 9 | 620–667 | 播放控制 上一首/播放/下一首 | 播放键由裸图标长成 primaryContainer 圆 |
| 10 | 671–703 | 功能胶囊行 | 随机 / 循环 / 定时 / 更多；`height=44dp*sc`、`alpha=sc` |

### 3.3 关键机制（**别踩坑**）

- **`sc` = 全屏程度**：`sc = ((contentProgress - HALF_ANCHOR_P) / (2f - HALF_ANCHOR_P)).coerceIn(0f, 1f)`
  - `sc = 0` → 卡片档；`sc = 1` → 全屏档
  - **全屏专属元素**统一用 `height = 某值 * sc` + `alpha = sc` 随展开「长出」，卡片档不显示
- `HALF_ANCHOR_P = 1.66f`（卡片档进度），全屏 = 2
- 字号随 `sc` 插值：`titleFont = lerp(21.sp, 28.sp, sc)`、`artistFont = lerp(14.sp, 16.sp, sc)`；`titleColor = lerp(onSurface, primary, sc)`
- **卡片档与全屏档共用同一套布局**，靠 `sc` 连续插值。不要为两档各写一套（会割裂）
- 参考：`Motion.kt` 里有全站动效令牌；`NumeShape` / `NumeFade` 是形状与透明度令牌

### 3.4 跑马实现建议

Compose 自带 **`Modifier.basicMarquee()`**（`androidx.compose.foundation.basicMarquee`）：
- 对 `maxLines = 1` 的 `Text` 生效：文字不溢出时静止，溢出时横向滚动
- 参数有 `iterations`、`repeatDelayMillis`、`initialDelayMillis`、`spacing`、`velocity` 等；持续滚动可传 `iterations = Int.MAX_VALUE`
- ⚠️ 注意「居中」与「跑马」同时存在时的对齐行为：通常是**外层容器居中、内层跑马**，或跑马时改用左对齐（网易云的做法是居中且溢出才滚）。**务必真机验证**两种情况的观感

### 3.5 待与用户确认的问题

1. **红心/评论挪走后，原「动作行」只剩一个播放列表按钮** —— 是删掉整行、把播放列表并进底部功能胶囊行，还是保留该行只放播放列表？
2. 是否要像网易云那样在红心/评论旁显示**数量**（如 `19w+` / `999+`）？当前 App 无此数据，需要接口支持。

---

## 4. 构建与装机流程（照抄）

```powershell
cd "C:\Users\liuca\AppData\Roaming\TRAE SOLO CN\ModularData\ai-agent\work-mode-projects\6a8e36a0fb66796b8b24ca44\nume"
.\gradlew.bat :app:assembleRelease --console=plain > build.log 2>&1

$bt  = "C:\Users\liuca\AppData\Local\Android\Sdk\build-tools\36.0.0"
$out = "C:\Users\liuca\AppData\Roaming\TRAE SOLO CN\ModularData\ai-agent\work-mode-projects\6a8e36a0fb66796b8b24ca44\nume\app\build\outputs\apk\release"
& "$bt\zipalign.exe" -p -f 4 "$out\app-release-unsigned.apk" "$out\app-release-aligned.apk"
& "$bt\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey "$out\app-release-aligned.apk"
& "C:\Users\liuca\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "$out\app-release-aligned.apk"
```

要点：
- **只做 Release 构建，不做 Debug**
- 项目**未配置正式签名**，release 产物是 unsigned，必须用上面的 zipalign + apksigner 手动签（用本机 debug keystore）
- 构建/编译输出**写日志文件**（如 `build.log`），**不要**在终端里过滤输出 —— 会导致终端长时间空白，看起来像卡死
- 日志文件（`build.log`、`build_verify.log` 等）**不要提交**，用完删掉
- 覆盖安装用 `-r`，保留登录态与数据

### 看手机屏幕（很常用）
```powershell
$adb = "C:\Users\liuca\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell screencap -p /sdcard/s.png
& $adb pull /sdcard/s.png "<本地路径>.png"
& $adb shell rm /sdcard/s.png
# 看当前前台是哪个 App：
& $adb shell dumpsys window | Select-String "mCurrentFocus"
```

---

## 5. 用户偏好与硬性约束（**务必遵守**）

- **不要擅自改 UI 结构** —— 本次是用户明确要求调整这一块，属例外；其余结构一律保留
- UI 要直观、简洁，**线框白、无背景色**；选中标识用原色块 + 等厚边框，焦点用外圈线框（不反色内容）
- **界面先加载、内容后台加载**（UI 优先）
- 沟通**避免技术术语**，用通俗易懂的话
- **提交前先给用户看/说明，得到确认再 push**；push 前确认暂存区没有夹带无关文件
- 手感/动画类改动：用户对「弹簧参数」很敏感，改完必须**真机验证**，别只看代码

---

## 6. 用户当前播放页长什么样（截图实况，供对照）

- 封面：方形大圆角图片
- 歌名「春雨落花」：大字、居中、主题色（偏粉）
- 歌手「遗憾」：小字、居中、灰色
- 动作行：左「红心（实心主题色）+ 评论图标」、右「音符图标（播放列表）」
- 分割线
- 进度条 + 左 `1:42` / 右 `2:12`
- 播放控制：上一首 / 大圆播放键（主题色底）/ 下一首
- 底部胶囊行：随机 / 循环 / 月亮（定时）/ 更多 —— 4 个圆形按钮

---

## 7. 建议的第一步

1. `git log --oneline -3` 确认 HEAD = `20d8e94`，`git status` 确认那两处未提交改动还在
2. 读 `PlayerPage.kt` 的 `PlayerPageContent`（381–722 行），对着 3.2 表格核对
3. **先和用户确认 3.5 的两个问题**，再动手
4. 改完编译 → 签名 → 装机 → 截图给用户看 → 确认后再 push