package com.thripleq.cirro.core.playback

import org.json.JSONObject

/**
 * 试听片段：非会员播放 VIP 歌时，官方只给一段被截取的音频，用 `freeTrialInfo`
 * 声明这段的起止（**单位秒**，实测 `{"start": 0, "end": 30}`）。
 *
 * ## 判据是实测反验过的，不是推理出来的（2026-10-11 探针，账号 vipType=0）
 *
 * | 情形 | `url` | `freeTrialInfo` | 判定 |
 * |---|---|---|---|
 * | 登录 + 非会员 + VIP 歌（fee=1, payed=0） | 有，0.48MB ≈ 30s（整曲的 9.5%） | `{start:0,end:30}` | **试听** |
 * | 已购单曲（fee=1, payed=3） | 有，10.53MB / 263s = 整曲 100% | `null` | 整曲，**不是**试听 |
 * | 未登录 + VIP 歌 | `null` | `null` | 拿不到（走「跳过」） |
 * | 免费 / 数字专辑可播整曲 | 有，整曲 100% | `null` | 整曲 |
 *
 * ⇒ **`freeTrialInfo` 非 null 才是试听的判据**。第二行是这条判据最危险的错法：
 * 若「fee==1 就算试听」或「有 url 且 payed>0 也算试听」，用户**已经买断的歌**会被标成
 * 试听、还会被在 30 秒处叫停 —— 所以判据只能取自响应本身，不能从 `fee` / `payed` 推。
 *
 * `freeTrialPrivilege.cannotListenReason` 与它同向（试听时 1、可播整曲时 null），但它
 * **不携带区间**，且语义是「为什么不能听」而不是「给的是哪一段」，故只作旁证、不作判据。
 *
 * ## 服务端给的字节流本身就是片段
 *
 * 实测 0.48MB ÷ 128kbps ≈ 30s，与 `end - start` 吻合 —— 也就是说 `url` 指向的文件
 * **已经**是那 30 秒，不需要（也不应该）在客户端再裁一刀。播放器侧因此只做一件事：
 * 在片段末尾停住（见 [PlayerHolder]），而不是按 [durationMs] 去裁剪。
 * 若哪天实测发现服务端开始给整曲，那时才需要 `MediaItem.ClippingConfiguration` ——
 * 注意那是 **MediaItem** 上的字段，改不了 [androidx.media3.datasource.DataSpec]。
 */
data class TrialClip(val startMs: Long, val endMs: Long) {

    /** 片段长度（毫秒）。实测 start 恒为 0，但按差值算以免哪天变成中段片段。 */
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)

    /** 给界面用的秒数：向上取整，免得 29.6s 显示成「29 秒」。 */
    val durationSeconds: Int get() = ((durationMs + 999L) / 1000L).toInt()
}

/** 一次「歌曲 URL」解析的结果：可播 URL + 它是不是试听片段。 */
data class PlayableUrl(val url: String, val trial: TrialClip?)

/**
 * 把 `/weapi/song/enhance/player/url/v1` 的响应体解析成 [PlayableUrl]；不可播返回 null。
 *
 * 抽成不依赖 Android 的纯函数，是为了能直接被 JVM 单测喂 JSON —— 这里的每条判据都是
 * 探针实测出来的，**必须能被回归验证**（见 `PlayableUrlTest`）。
 */
internal fun parseSongUrlResponse(json: String): PlayableUrl? = try {
    val item = JSONObject(json).getJSONArray("data").getJSONObject(0)
    item.optString("url")
        // 陷阱：不可播时接口返回 `"url": null`，org.json 的 optString 会得到
        // **字面量 "null"**（不是空串），必须显式过滤，否则会被当成有效 URL，
        // 既不回退下一档、也把 "null" 交给 ExoPlayer 当地址去请求。
        .takeIf { it.isNotBlank() && it != "null" && it != "undefined" }
        ?.let { PlayableUrl(it.toHttps(), trialClipOf(item)) }
} catch (_: Exception) {
    null
}

/**
 * `freeTrialInfo` → [TrialClip]；不是试听片段（字段为 JSON null / 缺失 / 区间不合法）返回 null。
 *
 * 区间**必须**校验：`end <= start` 或负数一旦漏进来，播放器会拿到一个零长片段而
 * 立刻「停在末尾」，读起来就是「点了播放没声音」。宁可不认它是试听（退化成普通整曲播放），
 * 也不要认错 —— 这条方向与 cookie 那边的「读不懂一律不删」是同一条：**失败要安全**。
 */
private fun trialClipOf(item: JSONObject): TrialClip? {
    val fti = item.optJSONObject("freeTrialInfo") ?: return null
    val start = fti.optLong("start", -1L)
    val end = fti.optLong("end", -1L)
    if (start < 0L || end <= start) return null
    return TrialClip(start * 1000L, end * 1000L)
}

/**
 * 网易云 CDN 音频 URL 常回 `http://`，Android 禁明文；CDN 同样支持 https。
 *
 * 注意**只换 scheme**，其余部分（含 query 里的签名）一字不动 —— 改动会让签名失效。
 */
internal fun String.toHttps(): String =
    if (startsWith("http://", ignoreCase = true)) "https://" + substring(7) else this
