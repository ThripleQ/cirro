package com.thripleq.cirro.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 付费 / VIP 徽标判据（`payTagsOf`）的测试。
 *
 * 这四条组合**全部来自真机数据**，不是设计出来的：
 * - 用户已购的 9 首单曲实测 `fee` 全是 **1**（VIP 歌可以单曲购买，买完 `fee` 不变，
 *   只是 `privileges[].payed` 从 0 变 3）⇒ `[VIP][蓝 PAY]` 会出现在真实列表里；
 * - 已购的数字专辑《希忘Hope》11 首全是 `fee=4` ⇒ `[蓝 PAY]`；
 * - 未购的《量变临界点》10 首也全是 `fee=4` ⇒ `[红 PAY]`（**「需要购买」不等于
 *   「已购买」**，这是内容属性，与买没买无关）。
 *
 * 判据来源：`fee` 是 weapi 里**唯一**存在的档位字段。kanade 用的 protobuf
 * `vipPlayFlag` / `payPlayFlag` 在 weapi 七个端点里实测**全都不存在**，所以只能
 * 「结果一致」，不能照搬字段。
 */
class PayTagRulesTest {

    @Test
    fun fee1NotOwned_isVipOnly() {
        assertThat(payTagsOf(fee = 1, owned = false)).containsExactly(PayTag.VIP).inOrder()
    }

    /** 不是理论情形：用户那 9 首已购单曲全是这一种。 */
    @Test
    fun fee1Owned_isVipPlusBluePay() {
        assertThat(payTagsOf(fee = 1, owned = true))
            .containsExactly(PayTag.VIP, PayTag.PAID).inOrder()
    }

    @Test
    fun fee4NotOwned_isRedPay() {
        assertThat(payTagsOf(fee = 4, owned = false)).containsExactly(PayTag.PAY).inOrder()
    }

    /** 已购时红色那枚被**替换**，不是并排 —— [PayTag.PAY] 不该出现在结果里。 */
    @Test
    fun fee4Owned_replacesRedWithBlue() {
        assertThat(payTagsOf(fee = 4, owned = true)).containsExactly(PayTag.PAID).inOrder()
    }

    /**
     * `0`（免费）与 `8`（低音质免费）**都不标**。
     *
     * `8` 尤其危险：838 首普查里它占 **62%**，是最大的一档；实测 `privilege.pl` 非 0、
     * 能正常播放。给它加徽标会让半张榜单挂满标。
     */
    @Test
    fun freeTiersDrawNothing() {
        assertThat(payTagsOf(fee = 0, owned = false)).isEmpty()
        assertThat(payTagsOf(fee = 0, owned = true)).isEmpty()
        assertThat(payTagsOf(fee = 8, owned = false)).isEmpty()
        assertThat(payTagsOf(fee = 8, owned = true)).isEmpty()
    }

    /** 老端点缺 `fee` → 0，与免费同形；未出现的档位一律不标（不猜）。 */
    @Test
    fun unknownTiersDrawNothing() {
        for (fee in listOf(-1, 2, 3, 5, 6, 7, 9, 99)) {
            assertThat(payTagsOf(fee, owned = false)).isEmpty()
            assertThat(payTagsOf(fee, owned = true)).isEmpty()
        }
    }

    /** PAY 与 PAID 是同一枚徽标的两种状态，文案必须一模一样 —— 区别只在颜色。 */
    @Test
    fun payAndPaidShareTheSameLabel() {
        assertThat(PayTag.PAY.label).isEqualTo("PAY")
        assertThat(PayTag.PAID.label).isEqualTo("PAY")
        assertThat(PayTag.VIP.label).isEqualTo("VIP")
    }
}
