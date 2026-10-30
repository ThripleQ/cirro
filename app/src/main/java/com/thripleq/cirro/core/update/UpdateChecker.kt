package com.thripleq.cirro.core.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.thripleq.cirro.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 自更新的 I/O 层：查 GitHub Release、下载 APK、校验、交给系统安装器。
 *
 * 全部是阻塞调用，调用方（[UpdateManager]）统一在 IO 上跑；本类不持有状态，
 * 方便单测只测纯逻辑（JSON 解析在 [parseRelease]，版本比较在 [compareVersions]）。
 *
 * ## 为什么更新源是 GitHub Releases
 *
 * 发布流程（`.github/workflows/release.yml`）本来就把 `cirro-vX.Y.Z.apk` 挂在 Releases
 * 页，链接永久有效、不用登录；项目没有后端，搬一套分发服务不值得。代价是国内直连
 * `api.github.com` / 资产下载地址时快时慢 —— 检查失败只提示、不打扰。
 *
 * ## 校验为什么不能省
 *
 * Releases 上的 APK 用仓库里那份 `debug.keystore` 签名（与本地/CI 构建一致），所以
 * 正常情况下能直接覆盖安装。但下载可能被截断、也可能被人换包：装之前必须确认
 * **包名、versionCode、签名** 三项都对。签名不一致时系统也会拒绝（先卸载才装得上，
 * 会丢数据），提前拦下来至少能给出人话解释。
 */
internal object UpdateChecker {

    private const val API_LATEST =
        "https://api.github.com/repos/thripleq/cirro/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    // 下载单独一个 client：APK 十几 MB，30s 的整体硬顶不够用；readTimeout 是「两次
    // 读到数据之间」的间隔，30s 已经足以判定连接死掉。
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLatest(): UpdateRelease = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(API_LATEST)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            // GitHub API 对 User-Agent 是硬性要求，缺了直接 403。
            .header("User-Agent", "Cirro/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub 返回 HTTP ${response.code}")
            }
            parseRelease(response.body.string())
                ?: throw IOException("发布页里没有找到 APK")
        }
    }

    /**
     * 下载到 `cacheDir/updates/`。[onProgress] 在 IO 线程上被调（节流到约 120ms 一次），
     * 参数是 0..1 的进度与已下载字节数（总量未知时进度恒 0，由调用方按字节显示）。
     */
    suspend fun download(
        context: Context,
        release: UpdateRelease,
        onProgress: (Float, Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // 旧包不留：同一时刻只可能有一个人在用，留着只会占空间。
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, release.apkName)
        val request = Request.Builder()
            .url(release.apkUrl)
            .header("User-Agent", "Cirro/${BuildConfig.VERSION_NAME}")
            .build()
        try {
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("下载失败：HTTP ${response.code}")
                }
                val body = response.body
                val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes
                var downloaded = 0L
                var lastEmitAt = 0L
                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastEmitAt >= 120) {
                                lastEmitAt = now
                                onProgress(
                                    if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f,
                                    downloaded,
                                )
                            }
                        }
                    }
                }
                onProgress(1f, downloaded)
            }
            if (release.sizeBytes > 0 && file.length() != release.sizeBytes) {
                throw IOException("下载不完整（${file.length()} / ${release.sizeBytes} 字节）")
            }
            file
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
    }

    /** 校验通过返回 null，否则返回一句可以直接展示给用户的原因。 */
    fun verify(context: Context, file: File): String? {
        if (!file.isFile) return "更新包已丢失，请重新下载"
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive = runCatching { pm.getPackageArchiveInfo(file.absolutePath, flags) }
            .getOrNull() ?: return "更新包无法解析"
        if (archive.packageName != context.packageName) return "更新包不是 Cirro"
        val archiveCode = versionCodeOf(archive)
        // 系统安装器同样会拒（INSTALL_FAILED_VERSION_DOWNGRADE），提前说清楚原因。
        if (archiveCode <= BuildConfig.VERSION_CODE) return "更新包版本不高于当前版本"
        val archiveCerts = certsOf(archive) ?: return "更新包没有签名"
        val ownInfo = runCatching { pm.getPackageInfo(context.packageName, flags) }.getOrNull()
            ?: return "读不到当前应用的签名"
        val ownCerts = certsOf(ownInfo) ?: return "读不到当前应用的签名"
        if (!archiveCerts.contentEquals(ownCerts)) {
            return "更新包签名与当前应用不一致，需卸载后重装"
        }
        return null
    }

    sealed interface InstallResult {
        /** 已交给系统安装器，接下来会弹出系统确认框。 */
        data object Started : InstallResult

        /** 还没获准「安装未知应用」，调用方需把用户带到系统设置页。 */
        data object NeedsPermission : InstallResult

        data class Failed(val message: String) : InstallResult
    }

    /**
     * 用 [PackageInstaller] 会话安装。`commit` 之后系统会弹出确认框；结果通过
     * [UpdateInstallReceiver] 回来（失败时弹一下 Toast，成功时进程一般已被换掉）。
     */
    fun install(context: Context, file: File): InstallResult {
        if (!context.packageManager.canRequestPackageInstalls()) {
            return InstallResult.NeedsPermission
        }
        if (!file.isFile) return InstallResult.Failed("更新包已丢失，请重新下载")
        return try {
            val installer = context.packageManager.packageInstaller
            val params = android.content.pm.PackageInstaller.SessionParams(
                android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            ).apply {
                // 声明这就是本应用自己的升级包：系统据此走「覆盖安装」而不是新装。
                setAppPackageName(context.packageName)
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("base.apk", 0L, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                // 系统要往这个 Intent 里塞 EXTRA_STATUS / 确认框 EXTRA_INTENT，
                // API 31+ 必须允许修改，否则回调里什么都读不到。
                val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
                val callback = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(context, UpdateInstallReceiver::class.java)
                        .setAction(UpdateInstallReceiver.ACTION_STATUS),
                    PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag,
                )
                session.commit(callback.intentSender)
            }
            InstallResult.Started
        } catch (e: Exception) {
            InstallResult.Failed(e.message ?: "安装器启动失败")
        }
    }

    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    /** 取签名证书的 SHA-256 摘要（多签名时排序后拼接，不看顺序）。 */
    private fun certsOf(info: PackageInfo): String? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return null
        if (signatures.isEmpty()) return null
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return signatures
            .map { signature ->
                digest.digest(signature.toByteArray())
                    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            }
            .sorted()
            .joinToString(";")
    }
}
