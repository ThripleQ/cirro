package com.thripleq.cirro.ui.components

/**
 * 歌曲行里紧跟歌手的付费标记 —— 照抄 kanade 的歌曲信息设计（2026-10-06 用户指定）。
 *
 * **为什么判据单独成一个文件**：原来它和绘制（`CirroPayBadge`）同在 `PayBadge.kt`，
 * 而那个文件 import 了一堆 Compose —— JVM 单测碰不得。判据恰恰是全项目最值得断言的
 * 一批（每条都是探针实测换来的），所以把它挪到这份**零依赖**的纯 Kotlin 文件里，
 * 绘制留在原处。
 *
 * 三态，**颜色**是唯一的区别所在（框、字号、描边完全一致）：
 *
 * | 标记 | 颜色 | 含义 | 出现时机 |
 * |---|---|---|---|
 * | [VIP] | 灰（跟随副标题） | 需会员才能听 | `fee == 1` |
 * | [PAY] / [PayTag.PAY] | 红 | **需要购买，还没买** | `fee == 4` 且未购 |
 * | [PAY] / [PayTag.PAID] | 蓝 | **已经购买** | 已购（单曲或数字专辑） |
 *
 * ## 为什么「需要购买」与「已购」是两个枚举、却是同一个字符串
 *
 * 用户口径（2026-10-06）：「所有歌曲都能购买，所以用红色的 pay 标记需要购买但没有买的，
 * 用蓝色的 pay 标记已经购买的；如果是需要购买则把红色 pay 替换成蓝色的，vip 则加一个蓝色 pay」。
 *
 * 也就是说这两者是**同一枚徽标的两种状态**（文字都是 PAY），不是两种徽标 —— 拆成两个枚举
 * 只是为了拿到两种颜色，[PayTag.label] 故意相同。于是组合只有四种：
 *
 * - `fee=4` 未购 → `[PAY 红]`
 * - `fee=4` 已购 → `[PAY 蓝]`（红的那枚被**替换**，不是并排）
 * - `fee=1` 未购 → `[VIP]`
 * - `fee=1` 已购 → `[VIP] [PAY 蓝]`（VIP 是"要会员"、蓝 PAY 是"我买过"，两件事都要说）
 *
 * 最后那条**不是理论情形**：实测用户的 9 首已购单曲全部 `fee=1`（VIP 歌可以单曲购买，
 * 买完 `fee` 不变、只是 `privileges[].payed` 从 0 变 3），所以它出现在真实列表里。
 */
enum class PayTag(val label: String) {
    VIP("VIP"),
    PAY("PAY"),
    PAID("PAY"),
}

/**
 * `fee` + 「买了没」→ 徽标序列（空的、一枚、或 VIP+PAY 两枚）。
 *
 * 判据是**实测反验过**的：用户给的 kanade 截图里 `Kids Return`（久石譲）标 VIP、
 * `那些我尚未知道的美丽`（华晨宇）标 PAY，而这两首在 `/weapi/v3/song/detail` 里的
 * `fee` 分别是 **1** 和 **4**。
 *
 * 其余档位**不标**：
 * - `0` 免费；
 * - `8` 低音质免费（榜上占近半，且实测 `privilege.pl` 非 0、能正常播放）—— 给它加徽标
 *   会让半张榜单都挂上标，与截图里"只有个别行有标"的观感不符；
 * - 老端点缺字段 → `0`。
 *
 * 探针实测（2026-10-06，838 首普查）：`fee` 的值域只有 `{0, 1, 4, 8}`。`8` 约占 62%，
 * 是**最容易被误标**的一档 —— 别因为"它也是付费相关"就加进去。
 *
 * [owned] 的含义见 `LibraryStateStore.isOwned`：单曲购买 ∪ 所属数字专辑已购。它只影响
 * 颜色、不影响「标不标」，所以**没载入完时退化成全红**，而不是让徽标整体消失
 * （那会显得列表被改坏了）。
 */
fun payTagsOf(fee: Int, owned: Boolean): List<PayTag> = when (fee) {
    4 -> if (owned) listOf(PayTag.PAID) else listOf(PayTag.PAY)
    1 -> if (owned) listOf(PayTag.VIP, PayTag.PAID) else listOf(PayTag.VIP)
    else -> emptyList()
}
