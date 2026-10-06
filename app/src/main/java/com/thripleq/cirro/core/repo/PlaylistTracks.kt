package com.thripleq.cirro.core.repo

import org.json.JSONObject

/** `/api/v3/song/detail` 单批上限（见上游 song_detail.js 注释）。 */
private const val SONG_DETAIL_BATCH = 1000

/**
 * 补全歌单/榜单曲目。
 *
 * `/api/v6/playlist/detail` 的 `playlist.tracks` 只给前一批，不保证全量——上游
 * 另开 `playlist/track/all` 才是取全量的路径，做法就是先拿 `playlist.trackIds`
 * 再分批走 `/api/v3/song/detail`（[CollectionRemote.songDetail]）。这里复用同样思路：
 * 仅当 trackIds 比预览多时才补，小歌单零额外请求；顺序以 trackIds 为准并去重。
 *
 * 收 [CollectionRemote] 而不是网关本体，是为了让这条补全路径在 JVM 上能被断言
 * （见 `CollectionRefresherTest` 里「大歌单分批补全」那条）。
 */
suspend fun completePlaylistTracks(
    remote: CollectionRemote,
    playlist: JSONObject,
    preview: List<Track>,
): List<Track> {
    val idsArr = playlist.optJSONArray("trackIds") ?: return preview
    val ids = buildList {
        for (i in 0 until idsArr.length()) {
            val id = idsArr.optJSONObject(i)?.optLong("id", 0L) ?: 0L
            if (id > 0) add(id.toString())
        }
    }
    if (ids.size <= preview.size) return preview

    val byId = HashMap<String, Track>(preview.size * 2)
    for (t in preview) byId[t.id] = t
    for (chunk in ids.filterNot { byId.containsKey(it) }.chunked(SONG_DETAIL_BATCH)) {
        val r = remote.songDetail(chunk.joinToString(","))
        if (r.err != 0 || r.body.isEmpty()) break
        val songs = try {
            JSONObject(String(r.body, Charsets.UTF_8)).optJSONArray("songs")
        } catch (_: Exception) {
            null
        } ?: break
        for (t in parseTracks(songs)) byId[t.id] = t
    }
    return ids.mapNotNull { byId[it] }
}
