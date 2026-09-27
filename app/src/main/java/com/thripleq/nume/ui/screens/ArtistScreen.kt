package com.thripleq.nume.ui.screens

import com.thripleq.nume.ui.theme.Motion
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.thripleq.nume.core.repo.ArtistAlbum
import com.thripleq.nume.core.repo.ArtistProfile
import com.thripleq.nume.core.repo.Track
import com.thripleq.nume.ui.artist.ArtistUiState
import com.thripleq.nume.ui.artist.ArtistViewModel
import com.thripleq.nume.ui.components.ShimmerImagePlaceholder
import com.thripleq.nume.ui.components.SharedKeys
import com.thripleq.nume.ui.components.SkeletonBox
import com.thripleq.nume.ui.components.SkeletonLine
import com.thripleq.nume.ui.theme.NumeShape
import com.valentinilk.shimmer.shimmer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 歌手主页：头像/别名/简介 + 专辑横滑 + 热门歌曲列表。
 * 点歌整单起播并弹出播放页；点专辑进入既有专辑详情（TrackListScreen）。
 */
@Composable
fun ArtistScreen(
    id: String,
    // 来源页带来的资料：数据未回来前先把头部头像/名字画出来，
    // 这是共享元素能成立的前提（目标端必须立即存在）。
    name: String = "",
    avatarUrl: String = "",
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenAlbum: (id: String, title: String, origin: Rect) -> Unit,
    islandHeight: Float = 0f,
    // 共享元素试验：头部头像与「搜索结果里的歌手行头像」共享（键见 [SharedKeys]）。
    shared: SharedTransitionScope? = null,
    avScope: AnimatedVisibilityScope? = null,
    vm: ArtistViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    LaunchedEffect(id) { vm.load(id) }
    LaunchedEffect(Unit) { vm.openPlayer.collect { onOpenPlayer() } }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val bottomPadding = (islandHeight + 16f).dp

    val avatarModifier = if (shared != null && avScope != null) {
        with(shared) {
            Modifier.sharedElement(
                rememberSharedContentState(key = SharedKeys.artistAvatar(id)),
                animatedVisibilityScope = avScope,
                // 与 SearchScreen 对侧同 spec：Emphasized 族（见 Motion.sharedBoundsSpec）。
                boundsTransform = { _, _ -> Motion.sharedBoundsSpec() },
            )
        }
    } else {
        Modifier
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(onBack = onBack, title = (state as? ArtistUiState.Ready)?.profile?.name ?: "歌手")
        when (val s = state) {
            is ArtistUiState.Loading -> LoadingContent(
                name = name,
                avatarUrl = avatarUrl,
                avatarModifier = avatarModifier,
            )
            is ArtistUiState.Error -> Center {
                Text(
                    text = "歌手加载失败",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { vm.retry() },
                )
            }
            is ArtistUiState.Ready -> ReadyContent(
                profile = s.profile,
                hotSongs = s.hotSongs,
                albums = s.albums,
                bottomPadding = bottomPadding,
                onPlayTrack = vm::onPlayTrack,
                onOpenAlbum = onOpenAlbum,
                avatarModifier = avatarModifier,
            )
        }
    }
}

@Composable
private fun ReadyContent(
    profile: ArtistProfile,
    hotSongs: List<Track>,
    albums: List<ArtistAlbum>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onPlayTrack: (Int) -> Unit,
    onOpenAlbum: (String, String, Rect) -> Unit,
    avatarModifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
    ) {
        item(key = "header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(profile.avatarUrl, profile.name, avatarModifier)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                if (profile.aliases.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = profile.aliases.joinToString(" / "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${profile.albumSize} 张专辑 · ${profile.musicSize} 首单曲",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (profile.briefDesc.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = profile.briefDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (albums.isNotEmpty()) {
            item(key = "albums_header") { SectionHeader("专辑") }
            item(key = "albums") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(albums.size, key = { "al_${albums[it].id}" }) { i ->
                        AlbumCard(albums[i]) { rect -> onOpenAlbum(albums[i].id, albums[i].name, rect) }
                    }
                }
            }
        }

        if (hotSongs.isNotEmpty()) {
            item(key = "songs_header") { SectionHeader("热门歌曲") }
            itemsIndexed(
                hotSongs,
                key = { _, t -> "s_${t.id}" },
                contentType = { _, _ -> "song" },
            ) { index, track ->
                SongRow(track) { onPlayTrack(index) }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
    )
}

@Composable
private fun AlbumCard(album: ArtistAlbum, onClick: (Rect) -> Unit) {
    var rect by remember { mutableStateOf(Rect.Zero) }
    Column(
        modifier = Modifier
            .width(118.dp)
            .onGloballyPositioned { rect = it.boundsInWindow() }
            .clickable { onClick(rect) },
    ) {
        Cover(album.coverUrl, album.name, 118.dp, circle = false)
        Spacer(Modifier.height(6.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val year = if (album.publishTime > 0) {
            SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(album.publishTime))
        } else {
            ""
        }
        if (year.isNotBlank()) {
            Text(
                text = year,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SongRow(track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(track.artworkUrl, track.name, 48.dp, circle = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.albumName.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track.albumName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Avatar(url: String?, name: String, modifier: Modifier = Modifier) {
    Cover(url, name, 96.dp, circle = true, modifier = modifier)
}

@Composable
private fun Cover(
    url: String?,
    contentDescription: String?,
    size: androidx.compose.ui.unit.Dp,
    circle: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = if (circle) CircleShape else NumeShape.CardSmall
    val context = LocalContext.current
    val model = url?.takeIf { it.isNotBlank() }?.let {
        ImageRequest.Builder(context).data(it).size(360).build()
    }
    Box(
        modifier
            .size(size)
            .clip(shape),
    ) {
        if (model != null) {
            val painter = rememberAsyncImagePainter(model)
            ShimmerImagePlaceholder(painter, Modifier.matchParentSize())
            Image(
                painter = painter,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/**
 * 加载态：头部头像**立即用来源页带来的 url/name 渲染**（不放进骨架），
 * 让共享元素目标在转场期间就存在；其余（专辑/歌曲）仍是骨架。
 */
@Composable
private fun LoadingContent(
    name: String,
    avatarUrl: String,
    avatarModifier: Modifier,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Avatar(avatarUrl, name, avatarModifier)
            if (name.isNotBlank()) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            } else {
                SkeletonLine(modifier = Modifier.shimmer(), widthFraction = 0.45f, height = 20.dp)
            }
        }
        ArtistSkeletonBody()
    }
}

/** 歌手页骨架（头部以下）：专辑横滑 + 热门歌曲行。 */
@Composable
private fun ArtistSkeletonBody() {
    Column(
        Modifier
            .fillMaxSize()
            .shimmer(),
    ) {
        SkeletonHeading()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(3) {
                Column(Modifier.width(118.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBox(Modifier.size(118.dp), NumeShape.CardSmall)
                    SkeletonLine(widthFraction = 0.9f, height = 12.dp)
                }
            }
        }
        SkeletonHeading()
        repeat(6) { ArtistSkeletonSongRow() }
    }
}

@Composable
private fun SkeletonHeading() {
    SkeletonLine(
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 10.dp),
        widthFraction = 0.22f,
        height = 18.dp,
    )
}

@Composable
private fun ArtistSkeletonSongRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(Modifier.size(48.dp), NumeShape.CardSmall)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonLine(widthFraction = 0.6f, height = 14.dp)
            SkeletonLine(widthFraction = 0.35f, height = 12.dp)
        }
    }
}
