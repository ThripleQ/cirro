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
 * - 挂在**容器**上（不是按钮上）：容器负责"接住所有落在自己身上的点击"，按钮照常自己消费。
 * - 本修饰符在 **Main 阶段**消费，而 Main 阶段是**子 → 父**冒泡：先由子节点（按钮、
 *   可拖拽的迷你条）处理完，本层再把剩下的吃掉 —— 所以它不会抢掉子节点的手势，
 *   只是不让事件继续往下漏。
 */
fun Modifier.swallowPointerInput(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}
