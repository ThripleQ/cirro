package com.thripleq.cirro.core.repo

import android.util.Log
import com.thripleq.cirro.BuildConfig
import com.thripleq.cirro.core.net.NetEaseGateway
import com.thripleq.cirro.core.net.NeteaseOp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

/** 一位相似歌手（`/api/discovery/simiArtist` 的一项）。 */
data class SimiArtist(
    val id: String,
    val name: String,
    val picUrl: String?,
)

/**
 * 首页 block 流一次请求的解析结果（见 [HomeRepository.homePage]）。
 *
 * [guessPages] 是**分页**的：每一页 3 首，对应 kanade 主页那一栏「一屏 3 行、
 * 左右翻页」的结构（`showType = HOMEPAGE_SLIDE_SONGLIST_ALIGN`）。不要把它拍平 ——
 * 拍平就退化成一条纵向长列表，与 kanade 不像。
 */
data class HomePageBlocks(
    val radar: List<PlaylistCard> = emptyList(),
    /**
     * 雷达 block 的服务端栏目标题。**必须用服务端给的，不要写死「雷达歌单」**：
     * 同一个 block 在未登录时给「网易云音乐的雷达歌单」、登录后给「<昵称>的雷达歌单」
     * （2026-10-05 实测两态返回），写死就少了一截信息。
     */
    val radarTitle: String? = null,
    /** 「猜你喜欢的「XX」好歌」的完整标题（风格词由服务端给）。 */
    val guessTitle: String? = null,
    val guessPages: List<List<Track>> = emptyList(),
    /**
     * 「猜你喜欢」第一首歌的**首位歌手 id** —— 顺路从 block 里带出来，给「相似艺人」
     * 功能卡当种子（拿相似歌手头像做卡面）。
     *
     * 这一层 data class 里塞一个歌手 id 看着越界，但它省掉一次 `/song/detail`：
     * block 的 `resourceExtInfo.artists[0].id` 本来就在手上（实测 18122 这种真值），
     * 调 simiArtist 前没必要再回去查一遍歌曲详情。null = block 没给或未登录。
     */
    val seedArtistId: String? = null,
) {
    /** 拍平后的全部单曲（播放入口用）。 */
    val guessSongs: List<Track> get() = guessPages.flatten()
}

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
     * 首页 block 流（`/api/homepage/block/page`）—— **一次请求喂两块内容**。
     *
     * 这条通用流里同时住着探索页的两个区块，原先各请求一次等于同一条流拉两遍，
     * 现在一次解析、一起返回（见 [HomePageBlocks]）：
     *
     * - `HOMEPAGE_BLOCK_MGC_PLAYLIST` → **雷达歌单** `creatives[]`（实测 6 张）。
     *   每张卡 `creativeId` 即歌单 id、`uiElement.mainTitle.title` 是名称、
     *   `uiElement.image.imageUrl` 是封面。曾经判定「weapi 拿不到雷达」并把整个
     *   区块删掉：带 radar 字样的 7 个候选路径全 404、api-enhanced 439 个 module
     *   里也没有 radar —— 因为它**从来不是独立端点**，服务端把雷达卡塞在这条通用流里，
     *   按名字找永远找不到（2026-10-04 复查 block 流才挖出来）。
     *
     * - `HOMEPAGE_BLOCK_STYLE_RCMD` → **「猜你喜欢的「XX」好歌」**。标题在
     *   `uiElement.subTitle.title`，风格词是服务端按口味给的（今天实测是「日文」），
     *   所以这一栏看起来「没有固定标题」；内容 4 组 × 3 首 = 12 首单曲，
     *   `showType = HOMEPAGE_SLIDE_SONGLIST_ALIGN` —— 它是**横滑翻页**的。
     *
     * refresh 传 false：首次进页走服务端当日缓存，不必每次重新出卡。
     * **这个 "false" 曾经是假的**：C 层原先按「不等于 "0" 就算真」解析，于是它被判成
     * true，等于每次进探索页都在强制服务端重新出卡（2026-10-05 在 `ne_homepage_block_page`
     * 里改成「只有 "true"/"1" 算真」）。改 C 层时别把这里换成 "0" 来将就旧口径。
     *
     * cursor 传**空串 = 不传**，这是刻意的。本端点走主流 `/weapi/` 时只实现了
     * 「无分页」形态：cursor 只要非空（"-1" 哨兵、0、字符串还是数字、甚至服务端
     * 自己返回的那个真游标，全都一样）就固定返 HTTP 200 + 74 字节 {"code":50002}，
     * 于是解析不出 data.blocks → **雷达栏整栏消失 + 猜你喜欢只剩登录提示**，表象
     * 与 UI 层 bug 完全一样。2026-10-05 定测：不带 cursor（weapi）与带 cursor（api
     * 老网关）在未登录态下**逐字节等价**（397574 字节、15 个 blocks、雷达 6 张、
     * 猜你喜欢 4 组）。首页只要第一屏 blocks，所以不带 —— 这也正是上游
     * api-enhanced 的默认形态（它的 cursor 默认 undefined，序列化时被丢掉）。
     * 将来真要按 cursor 翻 block 流，得让 C 层改走 /api/ 老网关（ne_create_weapi_asis），
     * 光把这里填上值只会拿到 50002。
     */
    suspend fun homePage(): HomePageBlocks = withContext(Dispatchers.IO) {
        // 第三个参数是 cursor：留空 = 不传，原因见上。
        val r = gateway.call(NeteaseOp.HOME_BLOCK_PAGE, "false", "")
        dbg { "blockPage err=${r.err} len=${r.body.size}" }
        // 哨兵：正常响应 100KB+。短响应只剩「路径或参数又变了」一种可能，整段
        // 打出来，省掉再猜一轮。
        if (r.body.size in 1..600) {
            Log.e("HomeApi", "blockPage SMALL body=${String(r.body, Charsets.UTF_8)}")
        }
        if (r.err != 0 || r.body.isEmpty()) return@withContext HomePageBlocks()
        try {
            val blocks = JSONObject(String(r.body, Charsets.UTF_8))
                .optJSONObject("data")?.optJSONArray("blocks")
                ?: return@withContext HomePageBlocks()
            var radar: List<PlaylistCard> = emptyList()
            var radarTitle: String? = null
            var guessTitle: String? = null
            var guessPages: List<List<Track>> = emptyList()
            var seedArtistId: String? = null
            for (i in 0 until blocks.length()) {
                val b = blocks.optJSONObject(i) ?: continue
                // 标题在 subTitle；个别皮肤把它放在 mainTitle，两个都试（两块都用得上）。
                val ui = b.optJSONObject("uiElement")
                val blockTitle = ui?.optJSONObject("subTitle")?.optString("title")
                    ?.takeIf { it.isNotBlank() }
                    ?: ui?.optJSONObject("mainTitle")?.optString("title")?.takeIf { it.isNotBlank() }
                when (b.optString("blockCode")) {
                    RADAR_BLOCK_CODE -> {
                        radar = parseRadarCards(b.optJSONArray("creatives"))
                        radarTitle = blockTitle
                        dbg { "radarBlock cards=${radar.size} ${radar.map { it.name }}" }
                    }
                    GUESS_BLOCK_CODE -> {
                        guessTitle = blockTitle
                        val creatives = b.optJSONArray("creatives")
                        guessPages = parseSongListCreatives(creatives)
                        seedArtistId = firstResourceArtistId(creatives)
                        dbg {
                            "guessBlock title=$guessTitle pages=${guessPages.map { it.size }} " +
                                "seedArtist=$seedArtistId " +
                                guessPages.flatten().joinToString { it.name }
                        }
                    }
                }
            }
            // **release 包也留一行**（dbg 受 BuildConfig.DEBUG 管，CI 出的 release 没有）。
            // 这两块一旦拿不到，表现是「猜你喜欢不能翻页（只剩一页回落）+ 雷达栏整栏消失」，
            // 与 UI 层 bug 的表象一模一样，真机排查时没有这行就只能靠猜。
            Log.e(
                "HomeApi",
                "homePage blocks=${blocks.length()} radar=${radar.size} pages=${guessPages.map { it.size }} " +
                    "radarTitle=$radarTitle guessTitle=$guessTitle",
            )
            HomePageBlocks(
                radar = radar,
                radarTitle = radarTitle,
                guessTitle = guessTitle,
                guessPages = guessPages,
                seedArtistId = seedArtistId,
            )
        } catch (_: Exception) {
            HomePageBlocks()
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
     * 「相似艺人」卡的内容（kanade 源码里这张卡的标识就是 `artist_fm`）。
     *
     * **正经链路**（2026-10-04 换掉旧的三段拼）：
     * ```
     * 种子歌 ──song/detail──> 歌手 id ──/api/discovery/simiArtist──> 相似歌手（取前 3）
     *        ──artist/songs(hot)──> 各拉一批热门歌 ──交替合并──> 播放
     * ```
     * 上游 `module/simi_artist.js` 就是 `/api/discovery/simiArtist`（weapi），
     * NeteaseCloudMusicApi 的 `/simi/artist` 也是它。
     *
     * 旧实现止步于**种子歌手自己的热门歌** —— 播出来永远是同一个人的歌，与
     * 「相似艺人」四个字正好相反。相似歌手拿不到时才退化成它（至少还有得听）。
     */
    suspend fun artistRadio(seedSongId: String?): List<Track> = withContext(Dispatchers.IO) {
        val seed = seedSongId?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
        val artistId = seedArtistId(seed) ?: return@withContext emptyList()
        val simi = simiArtist(artistId).take(SIMI_ARTIST_TAKE)
        if (simi.isEmpty()) return@withContext artistTopSongs(artistId)
        // 相似歌手之间无依赖 → 并行拉；再**交替合并**，让前几首来自不同歌手，
        // 而不是把第一个歌手的歌全播完才轮到第二个。
        val lists = coroutineScope {
            simi.map { a -> async { artistTopSongs(a.id, SIMI_SONGS_PER_ARTIST) } }.awaitAll()
        }
        interleave(lists)
    }

    /** 种子歌 → 它的第一个歌手 id（`song/detail`）。 */
    private suspend fun seedArtistId(songId: String): String? {
        val d = gateway.call(NeteaseOp.SONG_DETAIL, songId)
        if (d.err != 0 || d.body.isEmpty()) return null
        return try {
            val song = firstArray(JSONObject(String(d.body, Charsets.UTF_8)), "songs")
                ?.optJSONObject(0)
            val ar = song?.optJSONArray("artists") ?: song?.optJSONArray("ar")
            ar?.optJSONObject(0)?.optLong("id", 0L)?.takeIf { it > 0L }?.toString()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 相似歌手（`/api/discovery/simiArtist`，args: artistId）。返回壳是 `artists[]`，
     * 每项含 id / name / picUrl / albumSize。
     */
    suspend fun simiArtist(artistId: String): List<SimiArtist> = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.SIMI_ARTIST, artistId)
        dbg { "simiArtist artist=$artistId err=${r.err} len=${r.body.size} body=${String(r.body, Charsets.UTF_8).take(300)}" }
        if (r.err != 0 || r.body.isEmpty()) return@withContext emptyList()
        try {
            val arr = firstArray(JSONObject(String(r.body, Charsets.UTF_8)), "artists", "data", "result")
                ?: return@withContext emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optLong("id", 0L)
                    val name = o.optString("name")
                    if (id > 0L && name.isNotBlank()) {
                        add(SimiArtist(id.toString(), name, httpsUrl(o.optString("picUrl"))))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 某歌手的 50 首热门歌（`artist/songs`, order=hot）。注意 C 侧签名 offset 在 limit 前。 */
    private suspend fun artistTopSongs(artistId: String, limit: Int = 50): List<Track> {
        val r = gateway.call(NeteaseOp.ARTIST_SONGS, artistId, "0", limit.toString(), "hot")
        if (r.err != 0 || r.body.isEmpty()) return emptyList()
        return try {
            findTrackArray(JSONObject(String(r.body, Charsets.UTF_8)))?.let { parseTracks(it) }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 交替合并多份列表（轮转取，各自耗尽即止）。 */
    private fun interleave(lists: List<List<Track>>): List<Track> {
        val out = ArrayList<Track>()
        var i = 0
        while (true) {
            var added = false
            for (l in lists) {
                if (i < l.size) {
                    out += l[i]
                    added = true
                }
            }
            if (!added) break
            i++
        }
        return out
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

/**
 * 候选数组的**轻量**判定：首元素是歌曲对象（id>0，与 [parseTrack] 同一判据）。
 * 参数可空 —— 候选表本身就是 `JSONArray?`（[JSONObject.opt] 拿不到时塞 null）。
 */
private fun looksLikeSongs(arr: JSONArray?): Boolean =
    arr?.optJSONObject(0)?.optLong("id", 0L)?.let { it > 0L } == true

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

/** 从响应根里解析歌单列表（推荐歌单 / 曲风歌单共用）。 */
private fun parseCards(root: JSONObject): List<PlaylistCard> {
    val arr = firstArray(root, "result", "recommend", "data", "playlists")
        ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) parseCard(arr.optJSONObject(i))?.let { add(it) }
    }
}

/** 首页 block 流里「雷达歌单」区的 blockCode（官方移动端固定值）。 */
private const val RADAR_BLOCK_CODE = "HOMEPAGE_BLOCK_MGC_PLAYLIST"

/**
 * 首页 block 流里「猜你喜欢的「XX」好歌」区的 blockCode。
 *
 * 这一栏的用户可见标题是**服务端拼的**（`uiElement.subTitle.title`，风格词随口味
 * 变化，实测「猜你喜欢的「日文」好歌」），所以用户会觉得它「没有固定标题」。
 * 内容 4 组 × 3 首单曲，可左右翻页。
 */
private const val GUESS_BLOCK_CODE = "HOMEPAGE_BLOCK_STYLE_RCMD"

/** 「相似艺人」取前几个相似歌手的歌。 */
private const val SIMI_ARTIST_TAKE = 3

/** 每位相似歌手取几首热门歌（3 × 8 = 24 首，够听完一轮）。 */
private const val SIMI_SONGS_PER_ARTIST = 8

/**
 * 解析「猜你喜欢」区的 creatives：每个 `SONG_LIST_HOMEPAGE` creative 是**一页**，
 * 内含 3 个 song 资源。返回分页结构而不是拍平 —— 见 [HomePageBlocks.guessPages]。
 */
private fun parseSongListCreatives(arr: JSONArray?): List<List<Track>> {
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
private fun firstResourceArtistId(arr: JSONArray?): String? {
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
private fun parseBlockSongs(arr: JSONArray?): List<Track> {
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
            add(
                Track(
                    id = id.toString(),
                    name = name,
                    artist = ext?.optJSONArray("artists")?.optJSONObject(0)?.optString("name").orEmpty(),
                    artworkUrl = httpsUrl(ui?.optJSONObject("image")?.optString("imageUrl")),
                    durationMs = ext?.optJSONObject("song")?.optLong("dt", 0L) ?: 0L,
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
private fun parseRadarCards(arr: JSONArray?): List<PlaylistCard> {
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
