package com.thripleq.cirro.ui.screens

/**
 * 「播放全部」行的**可见副本**该落在壳子里的哪个 y。
 *
 * 背景（2026-11-07 第二版，用户：「处理得不好，我希望只有那个按钮不变形」）：
 * Android 12+ 的 stretch「吃力变形」是系统 `EdgeEffect` 加在**整个滚动容器绘制层**上的
 * 一次变换，`stickyHeader` 与曲目行同属这一层，官方没有「豁免某个 item」的开关。上一版
 * 用 `rememberBottomGuardOverscrollEffect` 在到底时把整张列表的拉伸掐掉，虽然按钮不歪了，
 * 但曲目行该有的手感也没了 —— 被否。这一版改成：列表原封不动（顶部底部都照常拉伸），
 * 只把那一行画到 `LazyColumn` **之外**的兄弟层，靠列表里那份透明占位回传的位置贴上去。
 *
 * 两个入参都是 `LayoutCoordinates.positionInRoot()` 的**布局坐标**，不含 graphicsLayer
 * 变换、也不含 EdgeEffect 的绘制变换 —— 这正是要害：系统拉伸只改绘制层，布局坐标纹丝
 * 不动，于是按钮跟着的永远是「没被拉过」的位置。两者同处一个根坐标系，页面动画 /
 * 壳位移会同时加减到两者上，相减即抵消。
 *
 * 首帧 `panelTopInRoot` 还没测出来时是 `Float.POSITIVE_INFINITY`（糊底「先不裁」的哨兵
 * 值，见 `TrackListScreen`），不能把它喂给 `translationY`：给 0，第二帧布局回传即修正，
 * 而这一帧还在骨架期、本来就看不见。
 */
internal fun playAllOverlayOffsetY(panelTopInRoot: Float, shellTopInRoot: Float): Float =
    if (panelTopInRoot.isFinite()) panelTopInRoot - shellTopInRoot else 0f
