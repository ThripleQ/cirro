package com.thripleq.cirro.core.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * `parseSongUrlResponse` 的判据测试 —— 也就是「这首歌拿到的到底是不是试听片段」。
 *
 * ## 为什么值得单测
 *
 * 试听身份**只从响应体里读得到**（`freeTrialInfo`），而这个字段的语义不是靠文档推出来的，
 * 是 2026-10-11 用探针逐类实测出来的（账号 `vipType=0`，见下表的采样来源）。
 * 它判错的表现全在**别人的耳朵里**：把整曲判成试听 → 已买断的歌播 30 秒被叫停；
 * 把试听判成整曲 → 就是改这一版之前的老行为（静默播完 30 秒跳走）。
 * 都不报错、都不崩溃，所以只能靠这组断言钉住。
 *
 * ## 每条断言的实测出处
 *
 * | 用例 | 探针端点 | 样本 |
 * |---|---|---|
 * | 试听片段 | `POST /weapi/song/enhance/player/url/v1` | `666443` Beautiful World：`url` 0.48MB ≈ 30s（整曲的 9.5%） |
 * | 已购整曲 | 同上 | `2653727977` Kids Return（fee=1 **payed=3**）：10.53MB / 263s = 整曲 100% |
 * | 未登录 | 同上（不带 cookie） | `666443`：`url` = null |
 *
 * **改断言前先去实测一遍**，别为了让测试变绿而改 —— 那等于把实测过的口径换成猜测。
 */
class PlayableUrlTest {

    // ──────────────────────────── 试听片段

    /**
     * 非会员点 VIP 歌：官方给的是**试听片段**，用 `freeTrialInfo` 声明区间（单位秒）。
     * 采样就是探针原文（只截掉与本判据无关的字段）。
     */
    @Test
    fun trialClip_isRecognisedFromFreeTrialInfo() {
        val r = parseSongUrlResponse(
            """
            {"code":200,"data":[{
              "id":666443,"url":"http://m701.music.126.net/x/y.mp3?auth=abc",
              "br":128012,"fee":1,"payed":0,"level":"standard",
              "freeTrialInfo":{"fragmentType":-1,"start":0,"end":30,
                               "algData":{"fragSource":"default"}},
              "freeTrialPrivilege":{"cannotListenReason":1,"playReason":null}
            }]}
            """.trimIndent()
        )

        assertThat(r).isNotNull()
        assertThat(r!!.url).isEqualTo("https://m701.music.126.net/x/y.mp3?auth=abc")
        val trial = r.trial
        assertThat(trial).isNotNull()
        assertThat(trial!!.startMs).isEqualTo(0L)
        assertThat(trial.endMs).isEqualTo(30_000L)
        assertThat(trial.durationMs).isEqualTo(30_000L)
        assertThat(trial.durationSeconds).isEqualTo(30)
    }

    /**
     * **本组最重要的一条**：已购单曲（`fee=1` 但 `payed=3`）拿到的是**整曲**，
     * `freeTrialInfo` 为 JSON `null`。
     *
     * 这条是在否证一个「看起来对」的替代判据：若按 `fee == 1`（或 `fee==1 && payed>0`）
     * 猜试听，用户**已经买断的 9 首歌**会全被标成「试听」并在 30 秒处停住 ——
     * 而它们实测是 10.53MB / 263s 的完整音频。所以判据必须取自响应里的 `freeTrialInfo`，
     * 不能从 fee / payed 推。
     */
    @Test
    fun purchasedFullTrack_isNotTrialEvenThoughFeeIs1() {
        val r = parseSongUrlResponse(
            """
            {"code":200,"data":[{
              "id":2653727977,"url":"http://m801.music.126.net/a/b.mp3?auth=def",
              "br":320000,"fee":1,"payed":3,"level":"exhigh",
              "freeTrialInfo":null,
              "freeTrialPrivilege":{"cannotListenReason":null,"playReason":null}
            }]}
            """.trimIndent()
        )

        assertThat(r).isNotNull()
        assertThat(r!!.trial).isNull()
        assertThat(r.url).isEqualTo("https://m801.music.126.net/a/b.mp3?auth=def")
    }

    /**
     * 免费歌 / 可播整曲：`freeTrialInfo` 直接**没有这个键**（不是显式 null）。
     * 两种都要落到「不是试听」。
     */
    @Test
    fun fullTrack_withoutFreeTrialKey_isNotTrial() {
        val r = parseSongUrlResponse(
            """
            {"code":200,"data":[{
              "id":34274310,"url":"http://m10.music.126.net/c/d.mp3",
              "br":320000,"fee":0,"payed":0
            }]}
            """.trimIndent()
        )
        assertThat(r).isNotNull()
        assertThat(r!!.trial).isNull()
    }

    /**
     * 中段片段（`start` 不为 0）按**差值**算长度。
     *
     * 实测所有样本的 `start` 都是 0，但判据不该把这件事写死：`durationMs = end - start`
     * 在 start>0 时同样成立，而按 `end` 当长度会把 30 秒的片段当 60 秒。
     */
    @Test
    fun midSongFragment_usesDifferenceAsDuration() {
        val r = parseSongUrlResponse(
            """
            {"code":200,"data":[{
              "url":"http://m1.music.126.net/e/f.mp3",
              "freeTrialInfo":{"start":30,"end":60}
            }]}
            """.trimIndent()
        )
        assertThat(r).isNotNull()
        val trial = r!!.trial
        assertThat(trial).isNotNull()
        assertThat(trial!!.durationMs).isEqualTo(30_000L)
        assertThat(trial.durationSeconds).isEqualTo(30)
    }

    // ──────────────────────────── 失败要安全

    /**
     * **区间不合法（`end <= start`）宁可不认它是试听**。
     *
     * 一旦把零长/负长片段认成试听，播放器会在末尾立刻停住 —— 读起来就是「点了播放没声音」，
     * 而且这个错会挂在**每一首**被判错的歌上。退化方向必须是「当整曲播」（失败要安全，
     * 与 cookie jar 那边「读不懂一律不删」同一条原则）。
     */
    @Test
    fun invalidTrialRange_isNotTreatedAsTrial() {
        val sameRange = parseSongUrlResponse(
            """{"code":200,"data":[{"url":"http://a/1.mp3","freeTrialInfo":{"start":30,"end":30}}]}"""
        )
        assertThat(sameRange!!.trial).isNull()

        val reversed = parseSongUrlResponse(
            """{"code":200,"data":[{"url":"http://a/2.mp3","freeTrialInfo":{"start":60,"end":30}}]}"""
        )
        assertThat(reversed!!.trial).isNull()

        val negative = parseSongUrlResponse(
            """{"code":200,"data":[{"url":"http://a/3.mp3","freeTrialInfo":{"start":-1,"end":30}}]}"""
        )
        assertThat(negative!!.trial).isNull()

        val missingEnd = parseSongUrlResponse(
            """{"code":200,"data":[{"url":"http://a/4.mp3","freeTrialInfo":{"start":0}}]}"""
        )
        assertThat(missingEnd!!.trial).isNull()
    }

    /**
     * `"url": null` 这个陷阱：org.json 的 `optString` 会给出**字面量 "null"**（不是空串）。
     * 不显式过滤的话它会当成有效地址交给 ExoPlayer（还会阻止音质回退）。
     *
     * 实测**未登录**打 VIP 歌就是这一形态（`url=null`、`br=0`、`freeTrialInfo=null`）——
     * 所以「未登录时跳过这首」正是靠这条兜住的。
     */
    @Test
    fun literalNullUrl_isNotPlayable_andNotTrial() {
        val r = parseSongUrlResponse(
            """{"code":200,"data":[{"id":666443,"url":null,"br":0,"fee":1,"payed":0,"freeTrialInfo":null}]}"""
        )
        assertThat(r).isNull()
    }

    /** 空串 / `"undefined"` 同样不是地址。 */
    @Test
    fun blankAndUndefinedUrl_areNotPlayable() {
        assertThat(
            parseSongUrlResponse("""{"code":200,"data":[{"url":""}]}""")
        ).isNull()
        assertThat(
            parseSongUrlResponse("""{"code":200,"data":[{"url":"undefined"}]}""")
        ).isNull()
    }

    /** 结构缺失 / 非法 JSON：一律 null，**不抛异常**（这条路径在加载线程上，抛出去会变成播放错误）。 */
    @Test
    fun malformedResponses_returnNullInsteadOfThrowing() {
        assertThat(parseSongUrlResponse("")).isNull()
        assertThat(parseSongUrlResponse("not json")).isNull()
        assertThat(parseSongUrlResponse("[]")).isNull()
        assertThat(parseSongUrlResponse("""{"code":200}""")).isNull()
        assertThat(parseSongUrlResponse("""{"code":200,"data":[]}""")).isNull()
        assertThat(parseSongUrlResponse("""{"code":200,"data":{}}""")).isNull()
        assertThat(parseSongUrlResponse("""{"code":200,"data":[{}]}""")).isNull()
    }

    // ──────────────────────────── 其它

    /** 已经是 https 的地址一字不动（签名在 query 里，改写会让它失效）。 */
    @Test
    fun httpsUrl_isLeftUntouched() {
        val raw = "https://m804.music.126.net/2026/x.mp3?auth=AAA%3D%3D&expire=1"
        val r = parseSongUrlResponse("""{"code":200,"data":[{"url":"$raw"}]}""")
        assertThat(r!!.url).isEqualTo(raw)
    }

    /** 片段秒数向上取整：29.2s 显示成 30 而不是 29（用户看到的总时长不能数字更小）。 */
    @Test
    fun durationSeconds_roundsUp() {
        assertThat(TrialClip(0L, 29_200L).durationSeconds).isEqualTo(30)
        assertThat(TrialClip(0L, 30_000L).durationSeconds).isEqualTo(30)
        assertThat(TrialClip(0L, 0L).durationSeconds).isEqualTo(0)
    }
}
