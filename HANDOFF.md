# 交接任务清单：跑马停顿位置 + 进度条恢复显示

> 写于 2026-10-02。请先通读本文件再动手。上一份交接单（播放页布局调整）已随 `28133a5` 删除，
> 其内容已被本次布局改动消化；本文件只记录**当前在途**的两件事。

---

## 0. 一句话任务

用户原话：

> 跑马有问题，停顿的位置有问题，没停到开头，另外顺便修复一下进度条的一些问题，进度保持功能在进度条上没体现。

拆成两件事：

1. **跑马灯停顿位置错误** —— 文字读完滚到末尾后，在末尾多停一拍；用户要的是**停顿只发生在行首**（读完立刻回卷、停在开头）。
2. **进度保持没体现在进度条上** —— 上次播放的进度是存下来的（`PlaybackStateStore`），进程重启后按播放也能从原位置继续；但**播放页/迷你条的进度条是空的、总时长显示 0:00**。

---

## 1. 仓库 / 设备 / 环境

| 项 | 值 |
|---|---|
| 仓库 | `C:\Users\liuca\AppData\Roaming\TRAE SOLO CN\ModularData\ai-agent\work-mode-projects\6a8e36a0fb66796b8b24ca44\nume` |
| 包名 | `com.thripleq.nume`（Android，Compose + Hilt + Media3） |
| 分支 | `main` |
| 手机 | 设备号 `34738582830059P`（V2148A，1080x2400，density 480） |
| adb | `C:\Users\liuca\AppData\Local\Android\Sdk\platform-tools\adb.exe` |
| build-tools | `C:\Users\liuca\AppData\Local\Android\Sdk\build-tools\36.0.0` |
| Media3 | 1.11.0 |

---

## 2. 本次会话已完成（代码改动）

三个文件，改动很小、集中：

### 2.1 `ui/components/FadingMarqueeText.kt` —— 停顿只留行首

自研跑马（带左右边缘淡出）。改动在 `LaunchedEffect` 的滚动循环里：

- **去掉末尾的 `delay(delayMillis)` 与末尾的 `fade.animateTo(0f)`**。
- 现在循环是：`snapTo(0) 归位 → 停 delayMillis（行首停顿）→ 与淡入并行滚到末尾 → 回到循环顶部瞬时回卷`。
- 效果：读完末尾**不停留、不淡出**，位置与遮罩一起在循环顶部瞬时归零，停顿全部留给行首。
- 这与 AOSP `BasicMarquee` 的 `repeatDelayMillis` 语义一致（重复间隔发生在行首，不在末尾）。
- 文档注释同步改写（`delayMillis` 的语义从「起滚前 + 读完后」改为「行首停顿」）。

### 2.2 `core/playback/PlaybackLauncher.kt` —— MediaItem 元数据声明时长

`trackMediaItem(track)` 里补了：

```kotlin
.apply { if (track.durationMs > 0L) setDurationMs(track.durationMs) }
```

原因：恢复态停在 `STATE_IDLE`（不 `prepare()`、不联网），此时 `player.duration` 为 0，
UI 拿不到总时长 → 进度条只能画成空轨道。把 `Track.durationMs` 写进元数据，UI 就有了兜底来源。

### 2.3 `ui/playerbar/PlayerState.kt` —— 用声明时长兜底

`rememberPlayerState` 里新增局部 `declaredDurationMs` 与 `effectiveDurationMs()`：

```kotlin
var declaredDurationMs = 0L
fun effectiveDurationMs(): Long =
    player.duration.takeIf { it > 0L } ?: declaredDurationMs
```

- `onMediaMetadataChanged` / 初始 seed / 250ms 轮询 / `STATE_READY` 分支**全部改用 `effectiveDurationMs()`**。
- 真实时长可用时（READY）优先真实值；未加载时退回元数据声明值。
- 关键：轮询里**不能再写 `player.duration.coerceAtLeast(0L)`** —— 那会把兜底值又覆盖回 0（这正是原 bug 的一半）。

### 2.4 结论 / 关键事实（已用字节码核实，别重复怀疑）

- `PlayerHolder.restore()` 调的是 `setMediaItems(items, idx, positionMs)`。
- 我反编译了 media3-exoplayer 1.11.0 的 `ExoPlayerImpl` 确认：`getCurrentPosition()` →
  `getCurrentPositionUsInternal()`，timeline 非空时返回 `playbackInfo.positionUs`；
  而 `setMediaSourcesInternal` 会经 `maskWindowPositionMsOrGetPeriodPositionUs` +
  `maskTimelineAndPosition` 把 `positionUs` 设成传入的起始位置。
- **所以恢复态（IDLE、未 prepare）`player.currentPosition` 就已经是恢复的进度值**，位置这一侧本来就是对的。
- 缺的只有**总时长**。故本次修的是「时长兜底」，位置不需要额外兜底。
- 迷你条（`PlayerDock.kt` 的 `MiniProgressBar`，约 526 行）读的是同一个
  `PlayerUiState.durationMs`，因此这次一并被修好。

---

## 3. 构建与装机流程（照抄）

```powershell
$repo = "C:\Users\liuca\AppData\Roaming\TRAE SOLO CN\ModularData\ai-agent\work-mode-projects\6a8e36a0fb66796b8b24ca44\nume"
$adb  = "C:\Users\liuca\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$bt   = "C:\Users\liuca\AppData\Local\Android\Sdk\build-tools\36.0.0"

# —— 快速自测：Debug（单 ABI arm64-v8a、自动用 debug 证书签名，34s 出包）——
.\gradlew.bat :app:assembleDebug --console=plain *> "$env:TEMP\build.log"
& $adb install -r "$repo\app\build\outputs\apk\debug\app-debug.apk"

# —— 正式：Release（ABI 全、minify+shrink，产物 unsigned，需手动签名）——
.\gradlew.bat :app:assembleRelease --console=plain *> "$env:TEMP\build.log"
$out = "$repo\app\build\outputs\apk\release"
& "$bt\zipalign.exe" -p -f 4 "$out\app-release-unsigned.apk" "$out\app-release-aligned.apk"
& "$bt\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey "$out\app-release-aligned.apk"
& $adb install -r "$out\app-release-aligned.apk"
```

要点：

- 项目**未配置正式签名**（`app/build.gradle.kts` 的 `release` 只有 minify/shrink），release 产物必须手动 zipalign + apksigner（本机 debug keystore）。debug 与 release 用的是同一把 debug 证书，签名一致，可互相 `-r` 覆盖安装。
- 构建/编译输出**写日志文件**，不要在终端里过滤输出 —— 会长时间空白，看起来像卡死。
- 日志/截图（`*.log`、`*.png`）**不要提交**，用完删掉。
- 覆盖安装用 `-r`，保留登录态与数据。

### 看手机屏幕
```powershell
& $adb exec-out screencap -p > shot.png     # 直接抓，不用先存 /sdcard
& $adb shell dumpsys window | Select-String "mCurrentFocus"   # 当前前台窗口
& $adb shell wm size ; & $adb shell wm density                 # 1080x2400 / 480
```

---

## 4. 验证状态（⚠️ 未完成，下一任请补）

- 代码改动已编译通过（`:app:assembleDebug` BUILD SUCCESSFUL，34s）。
- debug APK 已 `install -r` 覆盖安装成功，`MainActivity` 能启动，未见崩溃。
- 已确认**恢复态确实存在**：设备处于未登录状态，迷你条上仍显示上次曲目
  「追猎大手子🦌小小世…」/ 凌凌 —— 说明队列与进度是从 `PlaybackStateStore` 恢复出来的，
  正好是本次要验证的场景。
- **但「跑马只停行首」与「进度条显示恢复进度」都还没肉眼确认**：试图点迷你条进播放页时
  前台被切到了别的 App（截图是一屏直播/游戏画面），随后用户中断了操作。请重新走一遍：
  1. 进播放页 → 看进度条是否**有填充**、左右时间是「恢复进度 / 真实总时长」而非 `0:00 / 0:00`；
  2. 看歌名/歌手跑马：读完末尾应**立刻回卷**，只在行首停顿（`delayMillis` 默认 1200ms），末尾不停。
  - 若歌名不溢出（宽度够）则根本不会跑马，需用长标题验证；可临时把 `velocity` 调小或找长标题曲目。

---

## 5. 用户偏好与硬性约束（务必遵守）

- **不要擅自改 UI 结构** —— 除用户明确点名的范围外，一律保留。
- UI 要直观、简洁，**线框白、无背景色**；选中标识用原色块 + 等厚边框，焦点用外圈线框（不反色内容）。
- **界面先加载、内容后台加载**（UI 优先）。
- 沟通**避免技术术语**，用通俗易懂的话。
- **提交前先给用户看/说明，得到确认再 push**；push 前确认暂存区没有夹带无关文件
  （尤其别把 `build.log` / 截图 / 无关的未提交改动带进去）。
- 手感/动画类改动用户很敏感，改完必须**真机验证**，别只看代码。

---

## 6. 待办 / 下一步

1. **补真机验证**（见第 4 节两条），确认跑马停顿与进度条恢复显示都对。
2. 如验证发现跑马仍有「末尾停顿感」——检查是否有**别处**的跑马实现（迷你条用的是普通 `Text` + 省略号，不是 `FadingMarqueeText`；如需迷你条也跑马是另一个需求）。
3. 顺带可查：`rememberPlayerState` 里 `onPlayerError` 的 `error.errorCodeName ?: error.message` 编译期有 warning（`Elvis always returns left operand`），无害，但可清理。
4. 未决问题（上一份交接单遗留，用户未答）：红心/评论旁是否要显示数量（`19w+`/`999+`）？当前无此接口数据。

---

## 7. 本次会话的提交（push 后请核对）

| commit | 内容 |
|---|---|
| （见 `git log`） | `fix(player): 跑马只停行首 + 恢复态进度条显示时长` |
| （见 `git log`） | `docs: 新增交接单 HANDOFF.md` |
| （见 `git log`） | `chore(submodule): 拉取 libnetease 最新（jval 流式扫描）` |

> `libnetease` 子模块指针从 `82b1f6b` 前移到 `7591a56`（`perf(jval): 顶层 code 流式扫描替代双全树解析，端到端 -88%`），
> 是用户先前「拉取最新代码」时带进来的，本次一并提交指针。