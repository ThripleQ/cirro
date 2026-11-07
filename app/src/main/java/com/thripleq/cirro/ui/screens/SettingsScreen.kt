package com.thripleq.cirro.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thripleq.cirro.BuildConfig
import com.thripleq.cirro.core.playback.PlaybackPreferences
import com.thripleq.cirro.core.playback.PlayerHolder
import com.thripleq.cirro.core.playback.ProgressBarStyle
import com.thripleq.cirro.core.update.UpdateChecker
import com.thripleq.cirro.core.update.UpdateManager
import com.thripleq.cirro.core.update.UpdateRelease
import com.thripleq.cirro.ui.components.CirroPaperPage
import com.thripleq.cirro.ui.playerbar.ProgressStyleSample
import com.thripleq.cirro.ui.playerbar.ProgressStyleState
import com.thripleq.cirro.ui.profile.ProfileUiState
import com.thripleq.cirro.ui.profile.ProfileViewModel
import com.thripleq.cirro.ui.theme.CirroShape
import java.util.Locale

/** 音质档的显示名，与播放页设置面板同一套文案。 */
private val QualityLabels = mapOf(
    "standard" to "标准",
    "higher" to "较高",
    "exhigh" to "极高",
    "lossless" to "无损",
)

/** 进度条样式的显示名与一行说明（设置页 + 右侧静态小样）。 */
private val ProgressStyleLabels = mapOf(
    ProgressBarStyle.Capsule to "胶囊",
    ProgressBarStyle.Hairline to "发丝线",
    ProgressBarStyle.Dot to "细线圆点",
    ProgressBarStyle.Solid to "细实线",
    ProgressBarStyle.TimeChip to "时间胶囊",
    ProgressBarStyle.Waveform to "波形",
)

private val ProgressStyleNotes = mapOf(
    ProgressBarStyle.Capsule to "16dp 粗轨 + 竖条拇指（原来那款）",
    ProgressBarStyle.Hairline to "2dp 细轨 + 5.5dp 圆点，网易云版式",
    ProgressBarStyle.Dot to "3dp 轨道 + 7.5dp 圆点",
    ProgressBarStyle.Solid to "2dp 实线 + 7dp 圆点",
    ProgressBarStyle.TimeChip to "时长嵌在轨道左端，拖动时才见圆点",
    ProgressBarStyle.Waveform to "柱状波形，按曲目固定（示意）",
)

/**
 * 设置页：详情页骨架（圆角纸 + 右上收起键），内容按「账号 / 播放 / 外观 / 关于」分组。
 *
 * 音质直接读写 [PlaybackPreferences] —— 与播放页的设置面板共用同一份偏好，
 * 两边改完立刻互见（面板每次打开都重读）。
 *
 * [vm] 必须是 CirroApp 根部那个 Activity 作用域的 [ProfileViewModel]（与「我的」页同一实例）：
 * 退出登录要落在同一份 [ProfileUiState] 上，否则回到「我的」还是已登录的样子。
 */
@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    vm: ProfileViewModel,
    islandHeight: Float = 0f,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var quality by remember { mutableStateOf(PlaybackPreferences.quality(context)) }
    var allowConcurrentPlayback by remember {
        mutableStateOf(PlaybackPreferences.allowConcurrentPlayback(context))
    }
    // 直接读共享状态（Compose 状态，写入会触发重组），不另存本地副本 —— 与播放页同一份真相。
    val progressStyle = ProgressStyleState.current(context)
    val islandClearance = with(LocalDensity.current) { islandHeight.dp }

    // 「账号」一组只在登录时存在：登出后这一组连同确认框一起消失。
    val profileState by vm.uiState.collectAsStateWithLifecycle()
    val loggedIn = profileState is ProfileUiState.LoggedIn
    var confirmLogout by remember { mutableStateOf(false) }

    // 自更新状态机在进程级单例里（[UpdateManager]），设置页只负责展示与转发动作。
    val updateState by UpdateManager.state.collectAsStateWithLifecycle()
    // 「需要允许安装未知应用」这类一次性提示：状态机不关心 UI 文案，放在页面里。
    var installHint by remember { mutableStateOf<String?>(null) }

    fun installUpdate() {
        when (val result = UpdateManager.install(context)) {
            UpdateChecker.InstallResult.Started -> installHint = null
            UpdateChecker.InstallResult.NeedsPermission -> {
                installHint = "请允许 Cirro「安装未知应用」，然后回来再点安装"
                runCatching {
                    context.startActivity(
                        Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}"),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.onFailure { installHint = "打不开系统设置：${it.message}" }
            }
            is UpdateChecker.InstallResult.Failed -> {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun onUpdateRowClick() {
        installHint = null
        when (updateState) {
            is UpdateManager.State.Available -> UpdateManager.download(context)
            is UpdateManager.State.Ready -> installUpdate()
            is UpdateManager.State.Checking,
            is UpdateManager.State.Downloading,
            -> Unit
            else -> UpdateManager.check(context)
        }
    }

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
            if (loggedIn) {
                item(key = "account_header") { SettingsSection("账号") }
                item(key = "logout") {
                    SettingsCard { LogoutRow(onClick = { confirmLogout = true }) }
                }
            }

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
            item(key = "concurrent_playback") {
                SettingsCard {
                    SettingsSwitchRow(
                        label = "允许与其他应用同时播放",
                        checked = allowConcurrentPlayback,
                        onCheckedChange = { enabled ->
                            allowConcurrentPlayback = enabled
                            PlaybackPreferences.setAllowConcurrentPlayback(context, enabled)
                            // 播放器是进程级单例，不等下次冷启动：当场改音频焦点策略。
                            PlayerHolder.applyAudioFocusPolicy(context)
                        },
                    )
                }
            }
            item(key = "concurrent_note") {
                SettingsNote(
                    "开启后 Cirro 不再抢占音频焦点，可与视频、游戏、其他音乐一起出声；" +
                        "代价是别的 App 播放、来电或导航播报时也不会自动暂停 Cirro。" +
                        "拔掉耳机仍会自动暂停，切换后立刻生效。",
                )
            }

            item(key = "appearance_header") { SettingsSection("外观") }
            item(key = "progress_style") {
                SettingsCard {
                    ProgressBarStyle.entries.forEachIndexed { index, style ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        ProgressStyleRow(
                            style = style,
                            label = ProgressStyleLabels.getValue(style),
                            description = ProgressStyleNotes.getValue(style),
                            selected = style == progressStyle,
                            onSelect = {
                                ProgressStyleState.set(context, style)
                            },
                        )
                    }
                }
            }
            item(key = "progress_note") {
                SettingsNote("只改播放页进度条的外观，拖动与 seek 不受影响。波形是按曲目固定的示意图形，不是真实音频波形。")
            }

            item(key = "about_header") { SettingsSection("关于") }
            item(key = "about") {
                SettingsCard {
                    InfoRow(label = "版本", value = BuildConfig.VERSION_NAME)
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    UpdateRow(state = updateState, onClick = ::onUpdateRowClick)
                }
            }
            if (updateState.latestRelease != null) {
                item(key = "update_detail") {
                    UpdateDetailCard(
                        state = updateState,
                        installHint = installHint,
                        onDownload = { UpdateManager.download(context) },
                        onInstall = ::installUpdate,
                    )
                }
            }
            item(key = "update_note") {
                SettingsNote(
                    "新版来自 GitHub Releases。下载后会先核对包名、版本号与签名，" +
                        "再由系统安装器确认安装；应用数据会保留。",
                )
            }
            item(key = "credit") {
                SettingsNote("歌词与曲库数据来自网易云音乐。")
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录？") },
            text = { Text("会清除本机的登录状态，需要重新登录才能看到「我的」里的内容。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        vm.logout()
                        Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("取消") }
            },
        )
    }
}

/**
 * 退出登录：一行低调但认得出的按钮，点了**先确认**。
 *
 * 为什么必须确认：这不是「切个开关」，登出会清掉本机登录态 + 收藏/已购镜像，误触就得重走
 * 一遍 WebView 登录。用 error 色（读作「有后果」）而不是 onSurfaceVariant —— 后者和上面的
 * 行卡副标题一个色阶，会被当成又一个普通入口。
 */
@Composable
private fun LogoutRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Logout,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "退出登录",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
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

/**
 * 开关行：整行 `toggleable`（单一事件源 + 无障碍语义），右侧 Switch 只负责显示，
 * 免得「点开关」和「点整行」各触发一次。内边距与 [QualityRow] 同一套。
 */
@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * 进度条样式行：左侧名称+一句说明，右侧直接画这个样式的静态小样 —— 选之前就能看见。
 * 小样与播放页共用 `ProgressStyleSample` / `drawProgress`，改绘制只改一处。
 */
@Composable
private fun ProgressStyleRow(
    style: ProgressBarStyle,
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        ProgressStyleSample(
            style = style,
            modifier = Modifier.width(96.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center,
        ) {
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

/** 与更新相关的状态里，哪个还带着 Release（用于决定详情卡是否出现）。 */
private val UpdateManager.State.latestRelease: UpdateRelease?
    get() = when (this) {
        is UpdateManager.State.Available -> release
        is UpdateManager.State.Downloading -> release
        is UpdateManager.State.Ready -> release
        is UpdateManager.State.Failed -> release
        else -> null
    }

/**
 * 「检查更新」行：右侧就是**下一步该做什么**的答案，点整行执行那一步。
 * 不必先点检查、再点下载、再点安装 —— 一行依次推进，少三次点击。
 */
@Composable
private fun UpdateRow(state: UpdateManager.State, onClick: () -> Unit) {
    val busy = state is UpdateManager.State.Checking || state is UpdateManager.State.Downloading
    val (label, color) = when (state) {
        is UpdateManager.State.Checking -> "检查中…" to MaterialTheme.colorScheme.onSurfaceVariant
        is UpdateManager.State.UpToDate -> "已是最新" to MaterialTheme.colorScheme.onSurfaceVariant
        is UpdateManager.State.Available ->
            "v${state.release.versionName}" to MaterialTheme.colorScheme.primary
        is UpdateManager.State.Downloading ->
            "下载中 ${(state.progress * 100).toInt()}%" to MaterialTheme.colorScheme.primary
        is UpdateManager.State.Ready -> "待安装" to MaterialTheme.colorScheme.primary
        is UpdateManager.State.Failed -> "重试" to MaterialTheme.colorScheme.error
        else -> "检查" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "检查更新",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

/**
 * 发现新版之后的详情卡：版本对比 + 更新日志 + 下载/安装（或进度、失败原因）。
 *
 * 更新日志直接展示 Release 正文（本项目的提交信息），不折叠 —— 它至多十几行，
 * 真长了也在这一块里滚，不会把设置页撑爆。
 */
@Composable
private fun UpdateDetailCard(
    state: UpdateManager.State,
    installHint: String?,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    val release = state.latestRelease ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CirroShape.Card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "新版本 v${release.versionName}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "当前 ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state is UpdateManager.State.Failed) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (release.notes.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = release.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 14,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        when (state) {
            is UpdateManager.State.Downloading -> {
                // 总量未知（GitHub 没给 Content-Length 的极端情况）时不画假进度条。
                if (state.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildString {
                        append("正在下载 ")
                        if (state.progress > 0f) append("${(state.progress * 100).toInt()}% · ")
                        append(formatBytes(state.downloadedBytes))
                        if (release.sizeBytes > 0) append(" / ${formatBytes(release.sizeBytes)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is UpdateManager.State.Ready -> {
                Text(
                    text = "更新包已下载并通过校验。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                UpdateActionRow(label = "安装", onClick = onInstall)
            }
            is UpdateManager.State.Failed -> UpdateActionRow(label = "重试下载", onClick = onDownload)
            else -> UpdateActionRow(
                label = "下载更新（${formatBytes(release.sizeBytes)}）",
                onClick = onDownload,
            )
        }
        if (installHint != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = installHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun UpdateActionRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CirroShape.Card)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "大小未知"
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
