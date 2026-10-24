package com.thripleq.cirro.ui.playerbar

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 封面让位算式（`fittedCover`）的测试。
 *
 * 起因是用户 2026-10-24 的反馈：「播放卡片的『试听』标被压扁、VIP 标不存在」。根因不是
 * 徽标本身（迷你条里同一份徽标正常），而是**卡片档的竖向预算被封面吃满**：标题块是
 * `Column` 的最后一个子项，短缺全落在它身上，而它内部唯一「软」的就是徽标那一行 ——
 * 标题行高度固定、歌手跑马用 `requiredHeight` 忽略约束，只有两个 10dp 的 `Text` 会被压扁。
 *
 * 下面钉住的是「谁让位」这件事本身：**封面让，标题块不让**。数值取真机口径
 * （1080×2400 / 440dpi≈2.75；卡片档封面 = 内容区同宽 324.7dp；标题行 40dp + 间隔 4dp +
 * 歌手行盒 20dp = 64dp）。
 */
class FittedCoverTest {

    private val titleGap = 14.dp
    private val headingReserve = 64.dp

    @Test
    fun `槽位富余时封面原样返回（全屏档不受影响）`() {
        // 全屏档：槽位远大于封面，逐像素调过的尺寸必须一点不动。
        val cover = fittedCover(
            coverDim = 360.7.dp,
            slotHeight = 506.dp,
            titleGap = 20.dp,
            headingReserve = headingReserve,
        )
        assertThat(cover).isEqualTo(360.7.dp)
    }

    @Test
    fun `卡片档空间不足时扣的是封面，标题块整块保住`() {
        // 槽位 375dp、封面想做 324.7dp：324.7 + 14 + 64 = 402.7 > 375，差 27.7dp。
        val slot = 375.dp
        val cover = fittedCover(
            coverDim = 324.7.dp,
            slotHeight = slot,
            titleGap = titleGap,
            headingReserve = headingReserve,
        )
        // 封面缩掉的就是那 27.7dp。
        assertThat(cover).isEqualTo(297.dp)
        // 关键不变式：封面 + 间隔 + 标题块预留 = 槽位 ⇒ 标题块不缺高度，徽标不会被压。
        assertThat(cover + titleGap + headingReserve).isEqualTo(slot)
    }

    @Test
    fun `恰好装得下时不缩（边界）`() {
        val exact = 324.7.dp + titleGap + headingReserve
        val cover = fittedCover(
            coverDim = 324.7.dp,
            slotHeight = exact,
            titleGap = titleGap,
            headingReserve = headingReserve,
        )
        assertThat(cover).isEqualTo(324.7.dp)
    }

    @Test
    fun `槽位连间隔加标题块都装不下时归零，不返回负数`() {
        // 理论上到不了这一档；但 Dp 负数会让 size() 直接抛，宁可封面消失也不能崩。
        val cover = fittedCover(
            coverDim = 324.7.dp,
            slotHeight = 70.dp,
            titleGap = titleGap,
            headingReserve = headingReserve,
        )
        assertThat(cover).isEqualTo(0.dp)
    }
}
