package com.thripleq.cirro.core.update

import android.content.Context
import com.thripleq.cirro.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 应用内更新的状态机（进程级单例）。
 *
 * 状态从「检查」一路推进到「待安装」；设置页和启动提示都只读 [state]，UI 不碰网络、
 * 不碰文件。所有 I/O 都在 [UpdateChecker] 里、跑在本对象的 IO 作用域上。
 *
 * **启动时机**：`CirroApplication.onCreate` 调 [checkOnLaunch]，按 24h 节流。查到新版
 * 只在设置页标出来 + 根上弹一次提示（可跳设置），**不自动下载** —— 十几 MB 的包该由
 * 用户自己决定什么时候下。
 *
 * **为什么每次进程重启都从 [State.Idle] 开始**：已下载的 APK 在 cacheDir 里，系统清
 * 缓存是常态；与其维护「文件还在不在」的对账，不如让用户再下一次（手动检查不节流）。
 */
internal object UpdateManager {

    sealed interface State {
        data object Idle : State

        data object Checking : State

        data class UpToDate(val current: String) : State

        data class Available(val release: UpdateRelease) : State

        data class Downloading(
            val release: UpdateRelease,
            /** 0..1；总量未知时为 0，进度文案改看已下载字节。 */
            val progress: Float,
            val downloadedBytes: Long,
        ) : State

        data class Ready(val release: UpdateRelease, val file: File) : State

        /** [release] 非空表示「包下载/校验失败」，可以原地重试下载。 */
        data class Failed(val message: String, val release: UpdateRelease? = null) : State
    }

    /** 启动静默检查的最小间隔。 */
    private val CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(24)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var job: Job? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** 启动时调用：距上次成功检查不足 24h 就什么都不做。 */
    fun checkOnLaunch(context: Context) {
        val prefs = UpdatePreferences(context)
        if (System.currentTimeMillis() - prefs.lastSuccessAt() < CHECK_INTERVAL_MS) return
        check(context)
    }

    /** 手动/自动检查。正在下载或已下载待装时不打断那两个状态。 */
    fun check(context: Context) {
        val app = context.applicationContext
        synchronized(lock) {
            if (job?.isActive == true) return
            if (_state.value is State.Downloading || _state.value is State.Ready) return
            _state.value = State.Checking
            job = scope.launch {
                try {
                    val release = UpdateChecker.fetchLatest()
                    // 只记录成功的检查：GitHub 国内时好时坏，失败不该把下一次尝试压 24h。
                    UpdatePreferences(app).markChecked()
                    _state.value = if (
                        compareVersions(release.versionName, BuildConfig.VERSION_NAME) > 0
                    ) {
                        State.Available(release)
                    } else {
                        State.UpToDate(BuildConfig.VERSION_NAME)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.value = State.Failed(e.message ?: "检查失败，请稍后重试")
                }
            }
        }
    }

    /** 下载 + 校验。只有 [State.Available] / 下载失败后的 [State.Failed] 能发起。 */
    fun download(context: Context) {
        val release = when (val s = _state.value) {
            is State.Available -> s.release
            is State.Failed -> s.release
            else -> null
        } ?: return
        val app = context.applicationContext
        synchronized(lock) {
            if (job?.isActive == true) return
            _state.value = State.Downloading(release, 0f, 0L)
            job = scope.launch {
                try {
                    val file = UpdateChecker.download(app, release) { progress, downloaded ->
                        _state.value = State.Downloading(release, progress, downloaded)
                    }
                    val problem = UpdateChecker.verify(app, file)
                    _state.value = if (problem == null) {
                        State.Ready(release, file)
                    } else {
                        file.delete()
                        State.Failed(problem, release)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.value = State.Failed(e.message ?: "下载失败，请重试", release)
                }
            }
        }
    }

    /** 交给系统安装器；失败时保持 [State.Ready]，用户可以再点一次。 */
    fun install(context: Context): UpdateChecker.InstallResult {
        val ready = _state.value as? State.Ready
            ?: return UpdateChecker.InstallResult.Failed("还没有下载好更新包")
        return UpdateChecker.install(context, ready.file)
    }

    /** 启动提示的「同一版本只打扰一次」判据。 */
    fun shouldPrompt(context: Context, release: UpdateRelease): Boolean =
        compareVersions(release.versionName, BuildConfig.VERSION_NAME) > 0 &&
            !UpdatePreferences(context).isPromptDismissed(release.versionName)

    fun dismissPrompt(context: Context, release: UpdateRelease) {
        UpdatePreferences(context).dismissPrompt(release.versionName)
    }
}
