package com.thripleq.cirro.ui.screens

import com.thripleq.cirro.core.model.SearchAlbum
import com.thripleq.cirro.ui.search.SearchTab
import com.thripleq.cirro.ui.search.SearchUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ── 小工具 ─────────────────────────────────────────── */

internal fun isTabEmpty(s: SearchUiState): Boolean = when (s.tab) {
    SearchTab.SONGS -> s.songs.isEmpty()
    SearchTab.PLAYLISTS -> s.playlists.isEmpty()
    SearchTab.RADIOS -> s.radios.isEmpty()
    SearchTab.ALBUMS -> s.albums.isEmpty()
    SearchTab.ARTISTS -> s.artists.isEmpty()
}

internal fun mediaSubtitle(count: String, creator: String, playCount: Long): String {
    val by = creator.takeIf { it.isNotBlank() }?.let { " by $it" } ?: ""
    val play = if (playCount > 0) " · 播放${formatPlay(playCount)}" else ""
    return count + by + play
}

internal fun albumSubtitle(a: SearchAlbum): String {
    val date = if (a.publishTime > 0) {
        SimpleDateFormat("yyyy.M.d", Locale.getDefault()).format(Date(a.publishTime))
    } else {
        ""
    }
    return listOf(a.artist, date).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun formatPlay(n: Long): String = when {
    n >= 100_000_000 -> trim1(n / 100_000_000.0) + "亿"
    n >= 10_000 -> trim1(n / 10_000.0) + "万"
    else -> n.toString()
}

private fun trim1(v: Double): String {
    val s = String.format(Locale.US, "%.1f", v)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}
