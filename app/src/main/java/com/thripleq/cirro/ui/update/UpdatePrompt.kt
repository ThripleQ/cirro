package com.thripleq.cirro.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thripleq.cirro.BuildConfig
import com.thripleq.cirro.core.update.UpdateManager

/**
 * 启动时的「发现新版本」提示：只负责把用户带到设置页，下载与安装都在那里完成
 * （用户 2026-10-24 的明确要求）。这样提示本身很轻 —— 一个对话框、两个选择，
 * 不会在开屏后被下载进度占着屏幕。
 *
 * 不弹的条件：
 * - 开屏还没退场（[startupReady] 为 false）：提示必须出现在有内容的页面上；
 * - 当前就在设置页（[onSettingsScreen]）：版本信息已经明明白白摆在页面上，
 *   再盖一个「去设置」的对话框是重复打扰（用户 2026-10-30 的反馈）；
 * - 当前状态不是「有新版本」：没有可说的东西；
 * - 这份「有新版本」不是启动检查查到的（[UpdateManager.isPromptEligible]）：
 *   手动检查的结果只留在设置页；
 * - 这个版本已经提示过（「以后再说」/「去设置」都会记下来，见
 *   [UpdateManager.dismissPrompt]）—— 同一版本只打扰一次，之后靠设置页那一行。
 */
@Composable
fun UpdatePromptHost(
    startupReady: Boolean,
    onSettingsScreen: Boolean,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val state by UpdateManager.state.collectAsStateWithLifecycle()
    val release = (state as? UpdateManager.State.Available)?.release ?: return
    // 本次进程内点过按钮之后不再弹（prefs 是跨进程那条，状态是当场这一条）。
    var dismissedVersion by remember { mutableStateOf<String?>(null) }

    // 人已经站在设置页、自己看见了：「知会」就算完成，回到首页也不再补弹。
    if (onSettingsScreen) {
        LaunchedEffect(release.versionName) {
            dismissedVersion = release.versionName
            UpdateManager.dismissPrompt(context, release)
        }
        return
    }

    if (!startupReady || dismissedVersion == release.versionName) return
    if (!UpdateManager.isPromptEligible()) return
    if (!UpdateManager.shouldPrompt(context, release)) return

    fun dismiss() {
        dismissedVersion = release.versionName
        UpdateManager.dismissPrompt(context, release)
    }

    AlertDialog(
        onDismissRequest = { dismiss() },
        title = { Text("发现新版本 v${release.versionName}") },
        text = {
            Column {
                Text(
                    text = "当前版本 ${BuildConfig.VERSION_NAME}。到设置里下载并安装，应用数据会保留。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (release.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = release.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    dismiss()
                    onOpenSettings()
                },
            ) { Text("去设置") }
        },
        dismissButton = {
            TextButton(onClick = { dismiss() }) { Text("以后再说") }
        },
    )
}
