package com.thripleq.nume.core.repo

import android.util.Log
import com.thripleq.nume.BuildConfig
import com.thripleq.nume.core.net.NetEaseGateway
import com.thripleq.nume.core.net.NeteaseOp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** A recommended playlist card (personalized/playlist, served anonymously). */
data class PlaylistCard(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val playCount: Long,
    val trackCount: Long,
)

/**
 * Explore (Home) content source. 推荐歌单 / 雷达歌单 / 排行榜匿名可拉；每日推荐歌曲
 * 需要登录（否则接口返回 301 / 空）。所有拉取容错返回空列表，不抛。
 */
@Singleton
class HomeRepository @Inject constructor(
    private val gateway: NetEaseGateway,
    private val profileRepo: ProfileRepository,
) {

    /** 是否已登录（复用 Profile 的 account 查询，code 301 = 未登录）。 */
    suspend fun loggedIn(): Boolean = profileRepo.account() != null

    /** 个性化推荐歌单（匿名可用）。 */
    suspend fun recommendPlaylists(limit: String = "12"): List<PlaylistCard> =
        withContext(Dispatchers.IO) {
            val r = gateway.call(NeteaseOp.RECOMMEND_PLAYLISTS, limit)
            if (r.err != 0 || r.body.isEmpty()) return@withContext emptyList()
            try {
            parseCards(JSONObject(String(r.body, Charsets.UTF_8)))
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 每日推荐歌曲（需登录）。 */
    suspend fun dailySongs(): List<Track> = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.RECOMMEND_SONGS)
        if (r.err != 0 || r.body.isEmpty()) return@withContext emptyList()
        try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val data = root.optJSONObject("data")
            parseTracks(data?.optJSONArray("dailySongs") ?: data?.optJSONArray("recommend"))
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 个性化推荐资源（`/weapi/personalized/playlist`，匿名可用）。
     *
     * 与 [recommendPlaylists] 的分工（布局参照 kanade 主页，2026-10-04）：
     * kanade 的「雷达歌单」区显示 私人雷达/新歌雷达/时光雷达 三张卡 —— 这类"雷达"
     * 歌单恰好是 RECOMMEND_RESOURCE 返回里的常客（实测首条即「私人雷达」），而
     * RECOMMEND_PLAYLISTS 偏向大众化歌单，作「场景音乐」区的数据源。
     */
    suspend fun radarPlaylists(): List<PlaylistCard> = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.RECOMMEND_RESOURCE)
        if (r.err != 0 || r.body.isEmpty()) return@withContext emptyList()
        try {
            parseCards(JSONObject(String(r.body, Charsets.UTF_8)))
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── 探索页补齐（2026-10-04）──────────────────────────────────────
    // 布局照抄 kanade 主页时，kanade 的「精选推荐」用的是客户端枚举的功能卡、
    // 「猜你喜欢的「风格」好歌」用的是它的 styleList、场景音乐走 scene/radio。
    // 我们没有那套 OpenAPI（个人开发者需成年，暂不可用），所以退一步把官方
    // **移动端同源**的 weapi 端点接上来，能拿到就拿，拿不到保持原样。

    /**
     * 首页「发现」页圆形入口（每日推荐 / 歌单 / 排行榜 / 私人 FM…）。
     *
     * 端点 `/api/homepage/dragon/ball/static`：**未登录返回空数组**，
     * 调用方必须能接受空结果并回落到本地拼装的功能卡。
     */
    suspend fun dragonBall(): List<DragonBallEntry> = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.DRAGON_BALL)
        dbg { "dragonBall err=${r.err} len=${r.body.size} body=${String(r.body, Charsets.UTF_8).take(200)}" }
        if (r.err != 0 || r.body.isEmpty()) return@withContext emptyList()
        try {
            val arr = firstArray(JSONObject(String(r.body, Charsets.UTF_8)), "data", "result", "balls")
                ?: return@withContext emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    // 名称字段上游不统一：龙珠用 name，个别皮肤条目只有 title
                    val name = o.optString("name").ifBlank { o.optString("title") }
                    if (name.isBlank()) continue
                    add(
                        DragonBallEntry(
                            name = name,
                            iconUrl = httpsUrl(
                                o.optString("iconUrl").ifBlank { o.optString("picUrl") },
                            ),
                            targetType = o.optString("targetType").ifBlank { o.optString("skinType") },
                            targetId = o.optString("targetId").ifBlank { o.optString("id") },
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 曲风标签总表（`/api/tag/list/get`）。用来给「猜你喜欢的「XX」好歌」
     * 提供风格词 —— kanade 的标题就是带风格词的，我们以前写死了「好歌」。
     */
    suspend fun styleList(): List<StyleTag> = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.STYLE_LIST)
        dbg { "styleList err=${r.err} len=${r.body.size} body=${String(r.body, Charsets.UTF_8).take(300)}" }
        if (r.err != 0 || r.body.isEmpty()) return@withContext emptyList()
        try {
            val arr = firstArray(
                JSONObject(String(r.body, Charsets.UTF_8)),
                "data", "tags", "result", "list",
            ) ?: return@withContext emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    // wire 上字段是 tagId/tagName（不是 id/name，2026-10-04 探针实测，
                    // 之前按 id/name 解析导致整表为空、曲风体系全体哑火）。
                    // 有 childrenTags 的（如 流行 → 华语流行/粤语流行/…）优先收二级标签：
                    // style-tag 端点吃的是二级 tagId，kanade 的「华语流行日推」也是二级词。
                    val children = o.optJSONArray("childrenTags")
                    if (children != null) {
                        for (j in 0 until children.length()) {
                            val c = children.optJSONObject(j) ?: continue
                            val cid = c.optLong("tagId", 0L)
                            val cname = c.optString("tagName")
                            if (cid > 0 && cname.isNotBlank()) add(StyleTag(cid.toString(), cname))
                        }
                    } else {
                        val id = o.optLong("tagId", o.optLong("id", 0L))
                        val name = o.optString("tagName").ifBlank { o.optString("name") }
                        if (id > 0 && name.isNotBlank()) add(StyleTag(id.toString(), name))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 某曲风下的歌曲（`style_song`）。返回结构多层不定，按「能解析出歌」来认。 */
    suspend fun styleSongs(tagId: String, size: String = "6"): List<Track> =
        withContext(Dispatchers.IO) {
            val r = gateway.call(NeteaseOp.STYLE_SONG, tagId, size, "0")
            dbg { "styleSongs tag=$tagId err=${r.err} len=${r.body.size} body=${String(r.body, Charsets.UTF_8).take(300)}" }
            if (r.err != 0 || r.body.isEmpty()) return@withContext emptyList()
            try {
                findTrackArray(JSONObject(String(r.body, Charsets.UTF_8)))?.let { parseTracks(it) }
                    ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }

    /**
     * 私人漫游（私人 FM，`/api/v1/radio/get`）。需登录。
     *
     * kanade 主页「精选推荐」里那张「私人漫游」卡（副标题「多种听歌模式随心播放」）
     * 就是这个。服务端每次调用按口味随机出一批歌，**没有分页**，也不保证两次
     * 调用结果不同 —— 所以调用方只能「拉一批直接播」，不能当歌单列表缓存。
     * mode/subMode 留空 = 官方 App 的默认模式（官方没公开取值表）。
     */
    suspend fun radioSongs(limit: String = "10"): List<Track> =
        withContext(Dispatchers.IO) {
            val r = gateway.call(NeteaseOp.RADIO, "", "", limit)
            dbg { "radio err=${r.err} len=${r.body.size} body=${String(r.body, Charsets.UTF_8).take(300)}" }
            if (r.err != 0 || r.body.isEmpty()) return@withContext emptyList()
            try {
                findTrackArray(JSONObject(String(r.body, Charsets.UTF_8)))?.let { parseTracks(it) }
                    ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }

    /**
     * 「相似艺人」卡的内容（kanade 对应 artist_fm「从你喜欢的艺人听起」）。
     *
     * 官方的艺人 FM 我们没有（OpenAPI 不可用，weapi 侧没这个端点），所以走
     * **三段拼**：拿一首你口味里的歌当种子 → `song/detail` 取它的歌手 →
     * `artist/songs` 拉该歌手热门歌。任一段失败即返回空，调用方回落。
     */
    suspend fun artistRadio(seedSongId: String?): List<Track> =
        withContext(Dispatchers.IO) {
            val seed = seedSongId?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
            val d = gateway.call(NeteaseOp.SONG_DETAIL, seed)
            if (d.err != 0 || d.body.isEmpty()) return@withContext emptyList()
            val artistId = try {
                val root = JSONObject(String(d.body, Charsets.UTF_8))
                val song = firstArray(root, "songs")?.optJSONObject(0)
                val ar = song?.optJSONArray("artists") ?: song?.optJSONArray("ar")
                ar?.optJSONObject(0)?.optLong("id", 0L) ?: 0L
            } catch (_: Exception) {
                0L
            }
            if (artistId <= 0L) return@withContext emptyList()
            // 注意参数顺序是 (id, offset, limit, order) —— C 侧签名 offset 在 limit 前。
            val r = gateway.call(NeteaseOp.ARTIST_SONGS, artistId.toString(), "0", "50", "hot")
            dbg { "artistRadio artist=$artistId err=${r.err} len=${r.body.size}" }
            if (r.err != 0 || r.body.isEmpty()) return@withContext emptyList()
            try {
                findTrackArray(JSONObject(String(r.body, Charsets.UTF_8)))?.let { parseTracks(it) }
                    ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }

    /** 某曲风下的歌单（`style_playlist`）。返回壳是 `data.playlist`，封面键是
     *  `cover`（不是 picUrl）、曲目数是 `songCount`（不是 trackCount）。 */
    suspend fun stylePlaylists(tagId: String, size: String = "6"): List<PlaylistCard> =
        withContext(Dispatchers.IO) {
            val r = gateway.call(NeteaseOp.STYLE_PLAYLIST, tagId, size, "0")
            dbg { "stylePlaylists tag=$tagId err=${r.err} len=${r.body.size} body=${String(r.body, Charsets.UTF_8).take(300)}" }
            if (r.err != 0 || r.body.isEmpty()) return@withContext emptyList()
            try {
                val root = JSONObject(String(r.body, Charsets.UTF_8))
                val arr = root.optJSONObject("data")?.optJSONArray("playlist")
                    ?: firstArray(root, "data", "result", "list", "playlists")
                if (arr != null) {
                    buildList {
                        for (i in 0 until arr.length()) {
                            parseCard(arr.optJSONObject(i))?.let { add(it) }
                        }
                    }
                } else {
                    parseCards(root)
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    /**
     * 歌单分类总表（`/weapi/playlist/catalogue`）。
     *
     * kanade 的「场景音乐」是 **sceneTags**（清晨 / 夜晚 / 伤感 / 治愈…），
     * 我们拿不到它那套 OpenAPI，但官方这套歌单分类里 category 2 = 场景、
     * 3 = 情感，标签名与 kanade 卡面高度重合，语义对得上。
     * 只返回这两类的标签名（**标签本身没有封面**，封面要另取，见 [playlistsByCat]）。
     */
    suspend fun sceneTags(limit: Int = 8): List<String> =
        withContext(Dispatchers.IO) {
            val r = gateway.call(NeteaseOp.PLAYLIST_CATALOGUE)
            dbg { "catalogue err=${r.err} len=${r.body.size} body=${String(r.body, Charsets.UTF_8).take(300)}" }
            if (r.err != 0 || r.body.isEmpty()) return@withContext emptyList()
            try {
                val root = JSONObject(String(r.body, Charsets.UTF_8))
                val sub = root.optJSONArray("sub") ?: return@withContext emptyList()
                buildList {
                    for (i in 0 until sub.length()) {
                        val o = sub.optJSONObject(i) ?: continue
                        // 2 = 场景，3 = 情感；其余（语种/风格/主题）不进场景音乐区
                        val cat = o.optInt("category", -1)
                        if (cat != 2 && cat != 3) continue
                        val name = o.optString("name")
                        if (name.isBlank()) continue
                        add(name)
                        if (size >= limit) break
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    /** 某标签（场景 / 情感词）下的热门歌单（`/weapi/playlist/list`）。 */
    suspend fun playlistsByCat(cat: String, limit: String = "3"): List<PlaylistCard> =
        withContext(Dispatchers.IO) {
            val r = gateway.call(NeteaseOp.PLAYLIST_LIST, cat, limit, "0")
            dbg { "playlistList cat=$cat err=${r.err} len=${r.body.size}" }
            if (r.err != 0 || r.body.isEmpty()) return@withContext emptyList()
            try {
                parseCards(JSONObject(String(r.body, Charsets.UTF_8)))
            } catch (_: Exception) {
                emptyList()
            }
        }
}

/** 「发现」页一个圆形入口。 */
data class DragonBallEntry(
    val name: String,
    val iconUrl: String?,
    val targetType: String,
    val targetId: String,
)

/** 一个曲风标签（`/api/tag/list/get`）。 */
data class StyleTag(val id: String, val name: String)

/**
 * 探索页这几个新端点没有公开文档，返回壳子全靠猜 —— 解析不出来时 UI 只会
 * 「和以前一样」，看不出是端点死了还是键名猜错了。所以 debug 包留一条
 * `Log.e`（vivo 屏蔽 Log.d，诊断一律用 e）：拿到原始 body 才能一眼分清。
 */
private inline fun dbg(msg: () -> String) {
    if (BuildConfig.DEBUG) Log.e("HomeApi", msg())
}

/**
 * 在 root（及其 `data` / `result` 子对象）里按候选键找第一个数组。
 *
 * 这些新端点的返回壳子没有公开文档，键名可能变；与其猜死一个键，不如
 * 给一串候选，谁先命中用谁 —— 拿不到就返回空让调用方回落，绝不抛。
 */
private fun firstArray(root: JSONObject, vararg keys: String): JSONArray? {
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

/** 找到第一个能解析出至少一首歌的数组（style-tag 端点嵌套不定）。 */
private fun findTrackArray(root: JSONObject): JSONArray? {
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
    return candidates.firstOrNull { parseTracks(it).isNotEmpty() }
}

/**
 * 把任意一层里的歌单对象解析成 [PlaylistCard]。封面键 `picUrl` / `coverImgUrl`
 * 都试 —— 不同端点返回的不一样（见 [httpsUrl] 注释里 v6 playlist/detail 的坑）。
 */
private fun parseCard(o: JSONObject?): PlaylistCard? {
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

/** 从响应根里解析歌单列表（推荐歌单 / 雷达 / 曲风歌单共用）。 */
private fun parseCards(root: JSONObject): List<PlaylistCard> {
    val arr = firstArray(root, "result", "recommend", "data", "playlists")
        ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) parseCard(arr.optJSONObject(i))?.let { add(it) }
    }
}
