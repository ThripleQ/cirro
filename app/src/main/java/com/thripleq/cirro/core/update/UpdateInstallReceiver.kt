package com.thripleq.cirro.core.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

/**
 * 接收 [PackageInstaller] 会话的结果（见 [UpdateChecker.install]）。
 *
 * 两条路：
 * - `STATUS_PENDING_USER_ACTION`：系统把「确认安装」的 Intent 递回来，必须由我们
 *   起一个 Activity 弹出去 —— 不处理它就永远停在「没有任何反应」。
 * - 失败：Toast 出人话。最常见的是签名不一致 / 版本降级，这两条在安装前已经拦过
 *   一次（[UpdateChecker.verify]），走到这里多半是系统侧的其他原因（空间不足、
 *   安装来源被策略禁用），把 `EXTRA_STATUS_MESSAGE` 原样展示最省事。
 *
 * 成功时应用进程会被换掉，这个 receiver 通常收不到回调；收到也只当无事发生。
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = confirmIntent(intent) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
                    .onFailure { toast(context, "无法打开安装确认：${it.message}") }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                toast(context, "安装失败：${message ?: statusName(status)}")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun confirmIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    private fun statusName(status: Int): String = when (status) {
        PackageInstaller.STATUS_FAILURE -> "未知错误"
        PackageInstaller.STATUS_FAILURE_ABORTED -> "已取消"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "被系统策略阻止"
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "与已安装版本冲突（签名或版本号）"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "与当前系统不兼容"
        PackageInstaller.STATUS_FAILURE_INVALID -> "安装包无效"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "存储空间不足"
        else -> "状态码 $status"
    }

    private fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val ACTION_STATUS = "com.thripleq.cirro.UPDATE_INSTALL_STATUS"
    }
}
