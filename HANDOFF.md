# 交接单：歌单页（TrackListScreen）顶部「照抄搜索页」

> 交接时间：2026-10-03 ｜ 分支：`main` ｜ 基线 commit：`911246d`

## 一句话

歌单页顶部的**标题条**和**圆角壳子**要按搜索页的写法**照抄**（用户原话：「不要模仿，是复制」）。
本次只完成了第一步（消掉多余的圆角顶），**照抄本身还没做**，下一步就做它。

---

## 一、用户的原话与已确认范围

- 原话：「拉取最新代码，修复歌单页面顶部的标题和播放全部的圆角问题，你先对比搜索页的标题和搜索框，可以类比这个，说出你发现的问题」
- 追问后用户选定：**标题条 + 壳子都照抄**（选项原文：「标题条换成搜索页 SearchTitleBar 的写法（headlineSmall 粗体、左内缩 24dp、行高居中裁剪），标题条高度跟着文字走、壳子紧贴其下；列表整块装进搜索页那种「clip(圆角 28dp)+background(surface)」的壳子，由壳子统一裁角，「播放全部」不再自己切圆角，顺带修掉封面从角外漏出。注意：左上角 ∨ 收起键会和 24dp 的标题相撞，需要一并调整」）

---

## 二、已完成（本次会话）

1. 拉取最新代码：HEAD 到 `911246d feat(tracklist): 详情页顶部标题条 + 内容圆角纸，「播放全部」吸顶`；`libnetease` 子模块 `7591a56`（干净）。
2. **修掉「两个圆角顶」**：`TrackListTitleBar` 原先自己切了一份 `clip(RoundedCornerShape(SheetCorner))` 圆角、还自带一层
   `graphicsLayer { alpha = reveal }.background(surface)` 底；而「播放全部」吸顶行和圆角纸又各切了一份。
   于是屏上同时立着两个圆角顶（标题条一个、纸一个），中间夹出一道束腰，且标题条那条 `surface`
   与圆角外露出的 `surfaceContainer` 还对不上色。
   → 改法：**标题条去掉自己的圆角与底色**，变成「透明铺在容器色上的一条带」，底交给 `TrackListBackdrop`
   的容器色条、圆角交给纸。
3. 编译 + 装机 + 真机截图确认：全屏只剩「播放全部」一处圆角顶，观感与搜索页一致。

**改动文件**：`app/src/main/java/com/thripleq/cirro/ui/screens/TrackListScreen.kt`（+18 / −22，纯 UI 层，无逻辑变更）

---

## 三、未完成（下一步就做这个）

### 目标结构 —— 照抄搜索页 `SearchScreen.kt:92-148`

```kotlin
Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
    SearchTitleBar()                       // 标题：铺在容器色上，无圆角、无底色
    Column(                                 // ← 圆角壳子
        Modifier.fillMaxWidth().weight(1f)
            .clip(RoundedCornerShape(topStart = HomeSheetRadius, topEnd = HomeSheetRadius))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        SearchField(...)                    // 壳子的「顶盖」：占满整宽、紧贴纸上沿，直接顺承纸的 28dp 顶角
        ...内容...
    }
}
```

搜索页标题条（`SearchScreen.kt:153-174`）原文：

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(start = 24.dp, end = 8.dp, top = 0.dp, bottom = 0.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        text = "搜索",
        style = MaterialTheme.typography.headlineSmall.copy(
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
```

### 歌单页当前结构（`TrackListScreen.kt:274-508`）

```
Box(fillMaxSize)                                        // 274
├── TrackListBackdrop(offset y=-backdropTop)            // 305 糊底：铺满全屏（含状态栏、含标题条那一带）
└── Column(fillMaxSize, nav 时 statusBarsPadding)       // 312 内容整体让开状态栏
    └── Box(fillMaxSize)                                // 342
        ├── Box(圆角纸独立层)                            // 374
        │     .offset { paperTop }                      //     上沿跟着面板走（自然位置 ↔ 停靠线）
        │     .clip(paperShape).graphicsLayer{alpha=contentReveal}.background(surface)
        ├── TrackListTitleBar(...)                      // 390 固定 56dp 高、透明
        ├── when (display) {
        │     TrackCollection -> LazyColumn(            // 394
        │         modifier = Modifier.fillMaxSize().padding(top = PanelStickyTop),   // 固定内缩=停靠线
        │     ) {
        │         item(key="header") { TrackListHeader }                              // 409
        │         stickyHeader(key="playall") { Box(clip(paperShape).background(surface)) { PlayAllRow } }  // 434
        │         itemsIndexed(tracks) { TrackListRow }                               // 456
        │         item(key="sheetTail") { Box(height=bottomPadding) }                 // 470
        │     }
        │     Empty -> CirroEmptyState / Error -> CirroErrorState
        │   }
        ├── 骨架 TrackListSkeleton（叠在最上，alpha 淡出）  // 490
        └── TrackListBackButton(align TopStart)           // 503
        }
    }
}
```

### 要做的四件事

1. **标题条换成搜索页写法**：`headlineSmall` 粗体、左内缩 24dp、`LineHeightStyle(Center, Trim.Both)`、
   **高度跟着文字走**（不再固定 `PanelStickyTop`）。
   ⚠️ `TrackListMetrics.PanelStickyTop = TopBarHeight = 56.dp`（`TrackListScreen.kt:144`）同时被用作
   ①LazyColumn 的固定上内缩 ②吸顶停靠线 ③`HeaderTopInset = HeroCoverTop - PanelStickyTop` 的减数
   ④`TitleBarTextStart = TopBarHeight + 8.dp`。标题条高度一变，这四处要一起重算，否则吸顶线错位、
   头部封面落点（`HeroCoverTop`，与 `CoverExpandShell` 的 hero 终点同源）会偏。

2. **列表整块装进圆角壳子**：壳子 = `clip(RoundedCornerShape(top = SheetCorner)) + background(surface)`；
   `stickyHeader("playall")` 去掉自己的 `clip(RoundedCornerShape(SheetCorner))`，改由壳子统一裁角
   （对照搜索页：搜索条自己**不**切角，直接顺承纸的 28dp 顶角）。

3. **糊底 `TrackListBackdrop` 要挪进壳子里**（`TrackListScreen.kt:567-625`）。壳子有 `background(surface)`
   不透明底，糊底留在外面会被整个盖住、hero 效果消失。挪进去后它的 `surfaceContainer` 条 + 渐隐蒙版
   照旧工作，且会一起被壳子的圆角裁掉。
   ⚠️ 现注释（`:585-591`）说「容器色条 = 吸顶后纸两角外露出那条」，在搜索页里那条容器色是在壳子**外**
   （标题带）。挪进壳子后这条注释与语义都要重写。

4. **左上角 ∨ 收起键与 24dp 标题相撞**：`TrackListBackButton`（`TrackListScreen.kt:826-836`）是
   `IconButton`（48dp）+ `padding(4.dp)`，占 4..52dp；搜索页标题左内缩是 24dp，会重叠。
   需二选一：标题保留 ≥56dp 左内缩，或把键改位/换形态。**用户已知并接受这个取舍点。**

### 顺带要修的真实缺陷（本次会话发现）

吸顶的「播放全部」行虽然自带圆角，但**列表没有被壳子裁切**，角外会让底下滚过的曲目封面透出来 ——
真机截图壳子左上角能看到一小条**红色漏出**（`%TEMP%\v2.png` 放大后可见）。搜索页因为内容整块装在
壳子里，不会有这个问题。**把列表装进壳子即可顺带修掉**，不用单独打补丁。

---

## 四、验证状态

| 项 | 状态 |
|---|---|
| 拉取最新代码 | ✅ HEAD `911246d` |
| 消掉「两个圆角顶」 | ✅ 已编译、已装机、已真机截图确认 |
| 照抄搜索页（标题条 + 壳子） | ❌ **未实现**，标题字号/位置、壳子裁切、角外漏色都还是旧状态 |
| 左上角收起键避让 | ❌ 未处理 |

**建议的下一步验证**：改完后 `adb exec-out screencap -p` 截图，把顶部裁出来与搜索页逐像素比对
（标题字号/左内缩、壳子上沿与标题的间距、圆角半径、吸顶后角外是否还有漏色）。

---

## 五、环境与命令

- **仓库**：`C:\Users\liuca\AppData\Roaming\TRAE SOLO CN\ModularData\ai-agent\work-mode-projects\6a8e36a0fb66796b8b24ca44\cirro`
- **关键文件**：`app/src/main/java/com/thripleq/cirro/ui/screens/TrackListScreen.kt`（63KB）
- **搜索页参考**：`app/src/main/java/com/thripleq/cirro/ui/screens/SearchScreen.kt`
  （外壳结构 `:89-148`，`SearchTitleBar` `:153-174`，`SearchField` `:178-268`）
- **adb**：`C:/Users/liuca/AppData/Local/Android/Sdk/platform-tools/adb.exe`
- **构建**：`.\gradlew.bat :app:assembleDebug --console=plain`
  ⚠️ 输出**重定向到文件**再看，别在终端里过滤 —— 过滤会让终端长时间空白，看起来像卡住（踩过）。
- **装机**：`adb install -r app\build\outputs\apk\debug\app-debug.apk`
- **签名坑**：debug 包的签名证书，与之前手动用 `~/.android/debug.keystore` 签的 release 包
  **不是同一把**，覆盖安装会报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。本次已由用户手动卸载后全新安装
  （**登录态已丢**，这是预期内的）。
- **设备**：已连（序列号见 `adb devices`）。

---

## 六、约束（来自项目记忆，改动时别踩）

- UI 结构优先、界面先加载内容后加载；不要改动无关 UI。
- 圆角半径必须与站内其它纸同源：`TrackListMetrics.SheetCorner = HomeSheetRadius`，不要写裸数字。
- 文字/底色都走主题色（`onSurface` / `onSurfaceVariant` / `surface` / `surfaceContainer`），
  不要引入 `on-image` 白字那套。
- 滚动相关的状态读在 layout/draw 阶段（`derivedStateOf` + `graphicsLayer` / `offset`），
  别让它进组合阶段，否则每帧重组。
