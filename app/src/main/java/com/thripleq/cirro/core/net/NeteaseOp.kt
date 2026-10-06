package com.thripleq.cirro.core.net

/** Op codes shared between Kotlin and the C dispatch table in libnetease_jni.c. */
object NeteaseOp {
    const val SEARCH = 1
    const val CHECK_MUSIC = 2
    const val RECORD_RECENT = 3
    const val RECOMMEND_RESOURCE = 4
    const val SONG_URL_V1 = 5
    const val SONG_URL_OLD = 6
    const val SONG_DOWNLOAD_URL = 7
    const val SONG_MUSIC_QUALITY = 8
    const val SONG_PURCHASED = 9
    const val ALBUM_PURCHASED = 10
    const val ALBUM_DETAIL = 11
    const val SONG_DETAIL = 12
    /**
     * `/weapi/v6/playlist/detail`，参数 `(id, s, n)`。
     * - `n` 空串 = 库内回落到上游默认的全量（曲目详情最多 1000 首，trackIds 不受限）；
     * - `n = "0"` = **轻量检查**：只回元数据 + 完整 trackIds（约全量的 7~15%），
     *   用来判曲目有没有变（见 [com.thripleq.cirro.core.repo.CollectionRefresher]）。
     */
    const val PLAYLIST_DETAIL = 13
    const val USER_PLAYLIST = 14
    const val LYRIC = 15
    const val TOPLIST_DETAIL = 16
    const val RECOMMEND_SONGS = 17
    const val RECOMMEND_PLAYLISTS = 18
    const val USER_ACCOUNT = 19
    const val VIP_INFO = 20
    const val LIKE_LIST = 21
    const val PLAYLIST_SUBSCRIBE = 22
    const val PLAYLIST_TRACKS = 23
    const val PLAYLIST_CREATE = 24
    const val PLAYLIST_DELETE = 25
    const val PLAYLIST_UPDATE_NAME = 26
    const val LOGIN_EMAIL = 27
    const val LOGIN_CELLPHONE = 28
    const val LOGIN_REFRESH = 29
    const val SEND_CAPTCHA = 30
    const val LOGIN_CELLPHONE_CAPTCHA = 31
    const val ARTIST_DETAIL = 32
    const val ARTIST_SONGS = 33
    const val ARTIST_ALBUMS = 34
    const val ARTIST_DESC = 35
    const val RADIO_DETAIL = 36
    const val RADIO_PROGRAMS = 37
    const val COMMENTS = 38
    const val COMMENTS_HOT = 39

    // ── 探索页补齐（2026-10-04）────────────────────────────────────────
    // 端点钉自 api-enhanced 现行 module；与 libnetease_jni.c 的 case 一一对应。
    /** 首页「发现」页圆形入口（每日推荐/歌单/排行榜/私人 FM…）；未登录返回空。 */
    const val DRAGON_BALL = 40

    /** 曲风标签总表 `/api/tag/list/get`。 */
    const val STYLE_LIST = 41

    /**
     * 某曲风下的歌曲 `/weapi/style-tag/home/song`（args: tagId, size, cursor）。
     * 前缀必须是 weapi：服务端只在 /weapi 下注册了 style-tag 系列，打 /api 返回 400。
     */
    const val STYLE_SONG = 42

    /** 某曲风下的歌单 `/weapi/style-tag/home/playlist`（args: tagId, size, cursor）。 */
    const val STYLE_PLAYLIST = 43

    /**
     * 私人漫游（私人 FM）`/api/v1/radio/get`（args: mode, subMode, limit）。
     * 每次调用服务端按口味随机出一批歌，无分页；需登录。mode 留空表示默认模式。
     */
    const val RADIO = 44

    /** 歌单分类总表 `/weapi/playlist/catalogue`（场景 / 情感 / 风格…标签）。 */
    const val PLAYLIST_CATALOGUE = 45

    /** 某标签下的热门歌单 `/weapi/playlist/list`（args: cat, limit, offset）。 */
    const val PLAYLIST_LIST = 46

    /**
     * 首页「发现」页整条 block 流（args: refresh, cursor）。
     *
     * **「雷达歌单」区就在这里**：`data.blocks[blockCode == HOMEPAGE_BLOCK_MGC_PLAYLIST]`
     * 的 `creatives[]` 是一批官方雷达歌单（私人雷达 / 新歌雷达 / 会员雷达 / 乐迷雷达 /
     * 宝藏雷达…，实测稳定 6 张）。带 radar 字样的独立端点全 404 —— 它不是独立接口，
     * 而是这条通用 block 流里的一个 block（2026-10-04 探针实测）。
     *
     * cursor 一律**留空**：主流 weapi 路由只实现无分页形态，非空 cursor 固定返
     * 50002（含服务端自己返回的游标）。首页只要第一屏，留空即得全套，详见
     * [com.thripleq.cirro.core.repo.HomeRepository.homePage] 的说明。
     */
    const val HOME_BLOCK_PAGE = 47

    /**
     * 相似歌手 `/weapi/discovery/simiArtist`（args: artistId）。
     *
     * 「相似艺人」功能卡的正经链路（kanade 源码里这张卡叫 `artist_fm`）：
     * 种子歌 → 歌手 → **相似歌手** → 相似歌手的热门歌。以前我们拿「种子歌手
     * 自己的热门歌」冒充相似艺人，播出来永远是同一个人的歌，语义不对。
     * 字段名是 `artistid`（上游 module 原文）。
     */
    const val SIMI_ARTIST = 48

    // ── 收藏 / 点赞（2026-10-06，占位按钮接线）─────────────────────────
    // 这四条都是**写**接口。服务端把「下架歌曲无法收藏」这类失败放在 HTTP 200
    // 的 body.message 里（code=401），所以调用方一律走
    // [com.thripleq.cirro.core.repo.InteractionRepository]，由它统一把 message
    // 抽成 [com.thripleq.cirro.core.repo.ActionResult] —— 别直接 gateway.call 后
    // 只看 err/code，那样用户点了会「没反应」。

    /** 红心 / 取消红心某首歌 `/weapi/song/like`（args: trackId, like）。like 传 "true"/"1" 为喜欢。 */
    const val SONG_LIKE = 49

    /** 评论点赞 / 取消 `/weapi/v1/comment/{like,unlike}`（args: threadId, commentId, like）。 */
    const val COMMENT_LIKE = 50

    /** 收藏 / 取消收藏专辑 `/weapi/album/{sub,unsub}`（args: albumId, t）。t "1" 为收藏。 */
    const val ALBUM_SUBSCRIBE = 51

    /**
     * 已收藏专辑列表 `/weapi/album/sublist`（args: limit, offset）。
     * 专辑详情里**没有** subscribed 字段，所以「这张专辑收没收藏」只能靠它查。
     */
    const val ALBUM_SUBLIST = 52
}