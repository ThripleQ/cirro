package com.thripleq.cirro.core.repo

import com.thripleq.cirro.core.model.PlaylistCard
import com.thripleq.cirro.core.model.Track
import org.json.JSONArray
import org.json.JSONObject

/**
 * 探索页（首页）那些**没有公开文档**的端点的解析：JSON → 领域对象，全是纯函数。
 *
 * ## 为什么单独成一个文件
 *
 * 原来它们散在 `HomeRepository.kt` 底部，全是 private，而且和 `Log` / `BuildConfig`
 * 同处一个文件（那边的 `dbg` 要打诊断日志）。结果是**一个都测不了**：JVM 单测里
 * `android.util.Log` 是空壳，一碰就抛 not mocked。
 *
 * 挪到这里之后这份文件**零 Android 依赖**，解析可以脱离设备直接断言 —— 而这些判据
 * 恰恰是全项目最脆的一批（键名靠猜、壳子会变、漏读一个字段就是静默的「封面全灰 /
 * 徽标不画」）。用 internal 而不是 public：只对本模块与测试可见，不对外承诺 API。
 */
/**
 * 在 root（及其 `data` / `result` 子对象）里按候选键找第一个数组。
 *
 * 这些新端点的返回壳子没有公开文档，键名可能变；与其猜死一个键，不如
 * 给一串候选，谁先命中用谁 —— 拿不到就返回空让调用方回落，绝不抛。
 */
internal fun firstArray(root: JSONObject, vararg keys: String): JSONArray? {
    for (k in keys) {
        when (val v = root.opt(k)) {
            is JSONArray -> if (v.length() > 0) return v
            is JSONObject -> for (k2 in keys) {
                val inner = v.opt(k2)
                if (inner is JSONArray && inner.length() > 0) return inner
            }
        }
    }
    return null
}

/**
 * 候选数组的**轻量**判定：首元素是歌曲对象（id>0，与 [parseTrack] 同一判据）。
 * 参数可空 —— 候选表本身就是 `JSONArray?`（[JSONObject.opt] 拿不到时塞 null）。
 */
internal fun looksLikeSongs(arr: JSONArray?): Boolean =
    arr?.optJSONObject(0)?.optLong("id", 0L)?.let { it > 0L } == true

/** 找到第一个能解析出至少一首歌的数组（style-tag 端点嵌套不定）。 */
internal fun findTrackArray(root: JSONObject): JSONArray? {
    val candidates = mutableListOf<JSONArray?>()
    fun collect(o: JSONObject) {
        for (k in arrayOf("songs", "data", "list", "result", "resources", "songList", "tracks")) {
            when (val v = o.opt(k)) {
                is JSONArray -> candidates += v
                is JSONObject -> collect(v)
            }
        }
    }
    collect(root)
    // 先按轻量判据挑，**只对命中的那一个**真正解析。原写法
    // `firstOrNull { parseTracks(it).isNotEmpty() }` 会把每个候选整数组都完整构造成
    // Track 再丢掉 —— 这些端点常同时带 playlists / artists 等大数组，白解析几百个对象。
    candidates.firstOrNull { looksLikeSongs(it) }?.let { return it }
    // 首元素字段异常（老端点变体）时保留原路径，避免把行为收窄。
    return candidates.firstOrNull { parseTracks(it).isNotEmpty() }
}

/**
 * 把任意一层里的歌单对象解析成 [PlaylistCard]。封面键 `picUrl` / `coverImgUrl`
 * 都试 —— 不同端点返回的不一样（见 [httpsUrl] 注释里 v6 playlist/detail 的坑）。
 */
internal fun parseCard(o: JSONObject?): PlaylistCard? {
    if (o == null) return null
    val id = o.optLong("id", 0L)
    if (id <= 0) return null
    // 封面键三个端点三种写法：picUrl（推荐/雷达）、coverImgUrl（playlist/list）、
    // cover（style-tag/home/playlist）—— 全都试，谁非空用谁。
    val cover = o.optString("picUrl")
        .ifBlank { o.optString("coverImgUrl") }
        .ifBlank { o.optString("cover") }
    val tracks = o.optLong("trackCount", -1L)
        .let { if (it >= 0) it else o.optLong("songCount", 0L) }
    return PlaylistCard(
        id = id.toString(),
        name = o.optString("name"),
        coverUrl = httpsUrl(cover),
        playCount = o.optLong("playCount", o.optLong("playcount", 0L)),
        trackCount = tracks,
    )
}

/** 从响应根里解析歌单列表（推荐歌单 / 曲风歌单共用）。 */
internal fun parseCards(root: JSONObject): List<PlaylistCard> {
    val arr = firstArray(root, "result", "recommend", "data", "playlists")
        ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) parseCard(arr.optJSONObject(i))?.let { add(it) }
    }
}

/** 首页 block 流里「雷达歌单」区的 blockCode（官方移动端固定值）。 */
internal const val RADAR_BLOCK_CODE = "HOMEPAGE_BLOCK_MGC_PLAYLIST"

/**
 * 首页 block 流里「猜你喜欢的「XX」好歌」区的 blockCode。
 *
 * 这一栏的用户可见标题是**服务端拼的**（`uiElement.subTitle.title`，风格词随口味
 * 变化，实测「猜你喜欢的「日文」好歌」），所以用户会觉得它「没有固定标题」。
 * 内容 4 组 × 3 首单曲，可左右翻页。
 */
internal const val GUESS_BLOCK_CODE = "HOMEPAGE_BLOCK_STYLE_RCMD"

/** 「相似艺人」取前几个相似歌手的歌。 */
internal const val SIMI_ARTIST_TAKE = 3

/** 每位相似歌手取几首热门歌（3 × 8 = 24 首，够听完一轮）。 */
internal const val SIMI_SONGS_PER_ARTIST = 8

/**
 * 解析「猜你喜欢」区的 creatives：每个 `SONG_LIST_HOMEPAGE` creative 是**一页**，
 * 内含 3 个 song 资源。返回分页结构而不是拍平 —— 见 [HomePageBlocks.guessPages]。
 */
internal fun parseSongListCreatives(arr: JSONArray?): List<List<Track>> {
    if (arr == null) return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val page = parseBlockSongs(arr.optJSONObject(i)?.optJSONArray("resources"))
            if (page.isNotEmpty()) add(page)
        }
    }
}

/**
 * 「猜你喜欢」第一首的歌手 id（`resourceExtInfo.artists[0].id`），给「相似艺人」卡面当种子。
 *
 * 顺手取而不是另调 `/song/detail`：block 里本来就有这个 id（实测 "id": 18122）。
 * block 的 artists[0] 是**精简结构**（只有 id/name/picId…），所以不能复用 `simiArtist`
 * 那套解析，这里单独取一个数就够。
 */
internal fun firstResourceArtistId(arr: JSONArray?): String? {
    val res = arr?.optJSONObject(0)?.optJSONArray("resources")?.optJSONObject(0) ?: return null
    return res.optJSONObject("resourceExtInfo")?.optJSONArray("artists")
        ?.optJSONObject(0)?.optLong("id", 0L)?.takeIf { it > 0L }?.toString()
}

/**
 * block 流里的 song 资源 → [Track]。
 *
 * 字段名与 `/song/detail` 那套完全不同，这里逐个对上（2026-10-04 探针实测）：
 * `resourceId` = 歌曲 id、`uiElement.mainTitle.title` = 歌名、
 * `uiElement.image.imageUrl` = 封面（给的是明文 http 协议，必须过 [httpsUrl]）、
 * `resourceExtInfo.artists[0].name` = 首位艺人。
 *
 * **时长在 `resourceExtInfo.song.dt`**（毫秒），2026-10-05 复查实测 12 首**全都有值**
 * （257893 / 534532 …）—— 旧注释写「block 里没有时长」是只看了 uiElement 一层就下的结论。
 * 取不到才给 0：播放器起来后会由播放引擎填真实时长。
 */
internal fun parseBlockSongs(arr: JSONArray?): List<Track> {
    if (arr == null) return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optLong("resourceId", 0L)
            if (id <= 0L) continue
            val ui = o.optJSONObject("uiElement")
            val name = ui?.optJSONObject("mainTitle")?.optString("title").orEmpty()
            if (name.isBlank()) continue
            val ext = o.optJSONObject("resourceExtInfo")
            // 档位与专辑都在 `resourceExtInfo.song` 这个内嵌 song 对象上（实测它有 `fee`、
            // 也有完整的 `al{id,name,picUrl}`）—— 不读它就等于「首页的歌永远是免费 + 没有
            // 归属」，徽标在猜你喜欢里一个都画不出来（2026-10-06 修）。
            val song = ext?.optJSONObject("song")
            val al = song?.optJSONObject("al")
            add(
                Track(
                    id = id.toString(),
                    name = name,
                    artist = ext?.optJSONArray("artists")?.optJSONObject(0)?.optString("name").orEmpty(),
                    artworkUrl = httpsUrl(ui?.optJSONObject("image")?.optString("imageUrl")),
                    durationMs = song?.optLong("dt", 0L) ?: 0L,
                    albumName = al?.optString("name").orEmpty(),
                    albumId = al?.optLong("id", 0L)?.takeIf { it > 0L }?.toString() ?: "",
                    fee = song?.optInt("fee", 0) ?: 0,
                ),
            )
        }
    }
}

/**
 * 解析 block 流里 creative 卡片（结构见 [HomeRepository.radarPlaylists]）。
 *
 * 歌单名形如「华晨宇的歌,总令人心动|华语私人雷达」—— 竖线前是推荐理由、
 * 后面才是雷达名，卡面只该显示后者（kanade 实测卡面就是「私人雷达」
 * 「新歌雷达」这种短名，长理由塞不进 146dp 的卡）。没有竖线就整名照用。
 */
internal fun parseRadarCards(arr: JSONArray?): List<PlaylistCard> {
    if (arr == null) return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            // 歌单 id 就在 creativeId 上（卡上的 action 是 orpheus://playlist/<id>）。
            val id = c.optString("creativeId")
            if (id.isBlank()) continue
            val ui = c.optJSONObject("uiElement")
            val raw = ui?.optJSONObject("mainTitle")?.optString("title").orEmpty()
            val cover = ui?.optJSONObject("image")?.optString("imageUrl")
            val playCount = c.optJSONArray("resources")?.optJSONObject(0)
                ?.optJSONObject("resourceExtInfo")?.optLong("playCount", 0L) ?: 0L
            add(
                PlaylistCard(
                    id = id,
                    name = raw.substringAfterLast('|').trim().ifBlank { raw },
                    coverUrl = httpsUrl(cover),
                    playCount = playCount,
                    trackCount = 0L,
                ),
            )
        }
    }
}
