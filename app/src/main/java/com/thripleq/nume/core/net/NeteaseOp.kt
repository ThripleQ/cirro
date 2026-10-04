package com.thripleq.nume.core.net

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

    /** 某曲风下的歌曲 `/api/style-tag/home/song`（args: tagId, size, cursor）。 */
    const val STYLE_SONG = 42

    /** 某曲风下的歌单 `/api/style-tag/home/playlist`（args: tagId, size, cursor）。 */
    const val STYLE_PLAYLIST = 43

    /**
     * 私人漫游（私人 FM）`/api/v1/radio/get`（args: mode, subMode, limit）。
     * 每次调用服务端按口味随机出一批歌，无分页；需登录。mode 留空表示默认模式。
     */
    const val RADIO = 44
}