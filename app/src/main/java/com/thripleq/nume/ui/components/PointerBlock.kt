package com.thripleq.nume.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 吃掉落在本节点范围内的**全部**指针事件（按下即消费），使其不再传给下层。
 *
 * ## 为什么需要它
 *
 * Compose 的指针命中是**穿透式**的：一个只铺了 `.background()` 的容器不是命中目标，
 * 点在它上面（按钮之外的空白处）时事件会继续落到**它下面的**内容上。典型症状是
 * 「点了没反应，但底下那一行被点开了」—— 也就是误触。
 *
 * 站内两处踩过：
 * - **常驻 dock**（[com.thripleq.nume.ui.playerbar.PlayerDock]）：导航行的两格之间、
 *   迷你条上下的留白，点下去会穿透到底下正在滚动的列表（用户 2026-10-05：「点 dock 栏，
 *   点到按钮外面会穿透到 dock 下层内容，造成误触」）；
 * - **展开壳的占位层**（[ExpandableShell]）：壳还小的时候，点在壳外的手指会去滑动底下的
 *   列表，一滚起来收起时的起点矩形就跟卡片错位了。
 *
 * ## 用法与注意
 *
 * ### ⚠️ 只能当**兄弟**铺在交互内容的下面，不能当它们的祖先
 *
 * 本修饰符每帧把 Main 阶段的 change 全部消费掉。放在**祖先**位置时会毒死后代的手势
 * 检测：`awaitPointerSlopOrCancellation`（`anchoredDraggable` / `draggable` /
 * `detectHorizontalDragGestures` / 列表滚动都用它）在**还没越过 touch slop** 时，会在
 * Final 阶段复核一次「这个事件有没有被别人消费过」，一被消费就 `return null` 把手势取消。
 * 于是后代只有**在单个事件里就跨过 slop**才活得下来 —— 也就是只剩"甩"，一切慢拖全废，
 * 而且因为手势在第一帧就死了，**拖多远都没反应**（不是弹回，是压根没动）。这条一旦命中，
 * 症状与用户 2026-10-05 报的「慢慢拉卡片不出来、拉多高都没用」**完全一致**；当天从
 * `74e4767`（12:29）起的包都带着它。
 * （排障提醒：先确认机器上装的是哪一版。真机上旧包慢拖是**正常**的 —— 那时症状另有其因，
 * 见 `SheetFlingBehavior.CARD_COMMIT_FRAC` 的落档门槛；别只盯着一处下结论。）
 *
 * 正确姿势（站内两处都是这个形状）：
 * ```
 * Box {
 *     Box(Modifier.matchParentSize().swallowPointerInput())  // 拦截层，声明在前 = 垫在下
 *     // 交互内容（迷你条 / 导航行 / 壳）声明在后 = 画在上、命中测试在前
 * }
 * ```
 * 手指落在交互内容上时，命中测试停在内容自己身上，本层根本不进命中路径 → 零干扰；
 * 只有落在真正的空白处才由本层接住。
 *
 * ### 其余
 * - 挂在**容器**上（不是按钮上）：容器负责"接住所有落在自己身上的点击"。
 * - 子节点先处理（Main 阶段子 → 父、上层 → 下层），本层只吃掉剩下的。
 */
fun Modifier.swallowPointerInput(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}
