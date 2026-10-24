package com.thripleq.cirro.ui.playerbar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 焦点歌词行停靠位置的判据测试。
 *
 * ## 为什么值得单测
 *
 * 这个换算只有两个整数输入、四行代码，却决定了「焦点行到底落在屏幕哪个高度」。它错的时候
 * **不报错、不崩溃**，只是让人一直觉得「歌词跑到屏幕中间了」—— 用户 2026-10-24 的原话就是
 * 「焦点歌词行应该在屏幕靠上部分，而不是中间」。
 *
 * 病根不在比例（0.40 与黄金 0.382 只差 2%，肉眼看不出），而在**口径**：原先按视口高度乘
 * 比例，而视口高度在两档之间差约 200dp（内联档底部压着控制簇、沉浸档整簇退场），同一个
 * 比例因此算出两个不同的像素值 —— 换成沉浸档焦点行就从屏幕 38% 掉到 48%。
 *
 * 所以下面钉住的不是「比例等于多少」，而是**两条不变式**：
 * 1. 焦点行的绝对位置始终 = 屏幕 × 黄金比例（与视口多高无关）；
 * 2. 测量缺失时不产生垃圾锚点，而是交给调用方走退化路径。
 */
class LyricAnchorTest {

    /** 典型手机：屏 2400px 高，歌词视口顶在 340px（状态栏 + 标题下方）。 */
    private val windowH = 2400
    private val viewportTop = 340

    @Test
    fun `焦点行顶边落在屏幕的黄金分割线上`() {
        val anchor = lyricAnchorPx(windowH, viewportTop)
        // 锚点是「视口内」的偏移，加上视口顶才是屏幕上的绝对位置。
        assertThat(viewportTop + anchor).isWithin(1f).of(windowH * 0.382f)
    }

    @Test
    fun `锚点必须落在最矮的那个视口里`() {
        // 内联档底部压着进度/三键/胶囊，视口最矮（约 1430px）。锚点若比它还大，目标行
        // 永远滚不到那个位置 —— 停靠位置就成了死值。
        val anchor = lyricAnchorPx(windowH, viewportTop)
        assertThat(anchor).isLessThan(1430f)
        // 与旧口径对照：沉浸档视口约 2040px，按视口比例会给出 ≈816px，比内联档多出 244px。
        // 同一首歌「进沉浸」焦点行就下移这么多 —— 这就是用户看到「跑到屏幕中间」的来源，
        // 也是这里改成绝对锚点的原因。
        assertThat(anchor).isLessThan(2040f * 0.40f)
    }

    @Test
    fun `未测量时返回负值而不是零`() {
        // 0 会被当成合法锚点（把焦点行顶到视口最上沿），必须先被识别为「不可用」。
        assertThat(lyricAnchorPx(windowH, viewportTopPx = -1)).isEqualTo(-1f)
        assertThat(lyricAnchorPx(windowHeightPx = 0, viewportTopPx = viewportTop)).isEqualTo(-1f)
    }

    @Test
    fun `视口顶已在黄金线以下时锚点归零，不给负值`() {
        // 横屏 / 悬浮窗：视口顶可能比「屏幕 38.2%」还低，此时多出来的量只能是 0。
        // 负锚点会把目标行推到视口上方外侧，列表再也回不来。
        assertThat(lyricAnchorPx(windowHeightPx = 800, viewportTopPx = 600)).isEqualTo(0f)
    }

    @Test
    fun `小屏同样停在黄金线上`() {
        val anchor = lyricAnchorPx(windowHeightPx = 800, viewportTopPx = 100)
        assertThat(100 + anchor).isWithin(1f).of(800 * 0.382f)
    }
}
