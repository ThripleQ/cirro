package com.thripleq.cirro.ui.profile

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 列表能力表（[TrackListSource.capabilities]）的测试。
 *
 * 这张表是**界面结构的唯一判据**：头部三枚胶囊画哪三枚、面板行尾有没有收藏图标，
 * 全读它。表错了不会崩、不会报错，只会让某张列表长出一颗点了弹「不支持」的假按钮
 * —— 属于「静默错误」，正是这一类判据该被固化的理由。
 *
 * 分组依据只有一条（见 [TrackListCapabilities] 的说明）：**这个列表在服务端有没有
 * 真实对象**。歌单 / 榜单 / 专辑有（歌单对象自带 `subscribed`，评论线是 `A_PL_0_` /
 * `R_AL_3_`）；喜欢 / 已购 / 每日推荐是本地拿 uid 与已有曲目**拼出来的壳**，服务端
 * 没有对应的对象，硬拼 id 去调接口只会打开一条不相干的评论线或收藏一张不相干的歌单。
 */
class TrackListCapabilitiesTest {

    @Test
    fun serverBackedLists_supportCommentsAndSubscribe() {
        listOf(TrackListSource.PLAYLIST, TrackListSource.CHART, TrackListSource.ALBUM)
            .forEach { source ->
                assertThat(source.capabilities)
                    .isEqualTo(TrackListCapabilities(comments = true, subscribe = true))
            }
    }

    @Test
    fun localShellLists_supportNeither() {
        listOf(TrackListSource.LIKED, TrackListSource.PURCHASED, TrackListSource.DAILY)
            .forEach { source ->
                assertThat(source.capabilities)
                    .isEqualTo(TrackListCapabilities(comments = false, subscribe = false))
            }
    }

    /**
     * 三类本地壳的 id 不是资源 id（喜欢是 uid、已购与每日推荐是本地拼的串），
     * 所以它们**不能**有分享链接 —— 硬拼成 `playlist?id=<uid>` 会打开一张不相干的歌单。
     * 这条在 `TrackListScreen.shareUrlOf` 里靠 `else -> null` 保证，此处锁定的是
     * 「哪些来源算服务端资源」这个前提本身。
     */
    @Test
    fun onlyServerBackedListsAreShareableResources() {
        assertThat(TrackListSource.entries.filter { it.capabilities.subscribe })
            .containsExactly(
                TrackListSource.PLAYLIST,
                TrackListSource.CHART,
                TrackListSource.ALBUM,
            )
    }

    /** 六类列表都必须能被 `wire` 解析回来 —— 导航参数就是这些字符串。 */
    @Test
    fun everySourceRoundTripsThroughWire() {
        TrackListSource.entries.forEach { source ->
            assertThat(TrackListSource.from(source.wire)).isEqualTo(source)
        }
    }
}
