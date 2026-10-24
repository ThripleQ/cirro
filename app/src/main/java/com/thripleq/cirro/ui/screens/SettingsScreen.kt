package com.thripleq.cirro.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thripleq.cirro.BuildConfig
import com.thripleq.cirro.core.playback.PlaybackPreferences
import com.thripleq.cirro.ui.components.CirroPaperPage
import com.thripleq.cirro.ui.theme.CirroShape

/** 音质档的显示名，与播放页设置面板同一套文案。 */
private val QualityLabels = mapOf(
    "standard" to "标准",
    "higher" to "较高",
    "exhigh" to "极高",
    "lossless" to "无损",
)

/**
 * 设置页：详情页骨架（圆角纸 + 右上收起键），内容按「播放 / 关于」分组。
 *
 * 音质直接读写 [PlaybackPreferences] —— 与播放页的设置面板共用同一份偏好，
 * 两边改完立刻互见（面板每次打开都重读）。
 */
@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    islandHeight: Float = 0f,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var quality by remember { mutableStateOf(PlaybackPreferences.quality(context)) }
    val islandClearance = with(LocalDensity.current) { islandHeight.dp }

    CirroPaperPage(title = "设置", onClose = onBack) { sheetTop ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = sheetTop + 4.dp,
                bottom = islandClearance + 24.dp,
            ),
        ) {
            item(key = "play_header") { SettingsSection("播放") }
            item(key = "quality") {
                SettingsCard {
                    PlaybackPreferences.QUALITIES.forEachIndexed { index, value ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        QualityRow(
                            label = QualityLabels[value] ?: value,
                            selected = value == quality,
                            onSelect = {
                                quality = value
                                PlaybackPreferences.setQuality(context, value)
                            },
                        )
                    }
                }
            }
            item(key = "quality_note") {
                SettingsNote("在线播放取流时使用这一档；服务端没有该档位时自动降级。")
            }

            item(key = "about_header") { SettingsSection("关于") }
            item(key = "about") {
                SettingsCard {
                    InfoRow(label = "版本", value = BuildConfig.VERSION_NAME)
                }
            }
            item(key = "credit") {
                SettingsNote("歌词与曲库数据来自网易云音乐。")
            }
        }
    }
}

@Composable
private fun SettingsSection(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CirroShape.Card)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        content()
    }
}

@Composable
private fun QualityRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "已选中",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
    )
}
