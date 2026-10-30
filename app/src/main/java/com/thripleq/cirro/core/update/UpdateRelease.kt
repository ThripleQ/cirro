package com.thripleq.cirro.core.update

/** 一次 GitHub Release 里与自更新有关的字段（其余响应一律不关心）。 */
internal data class UpdateRelease(
    /** 原始 tag，形如 `v0.3.4`；展示时带 `v`。 */
    val tag: String,
    /** 去掉前缀的版本号，与 `BuildConfig.VERSION_NAME` 同一口径。 */
    val versionName: String,
    /** Release 正文（本项目就是中文提交信息），当更新日志用。 */
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    /** 资产字节数，下载完整性和进度总量都用它；拿不到时是 0。 */
    val sizeBytes: Long,
)

/**
 * 从 `GET /repos/{owner}/{repo}/releases/latest` 的响应里取更新信息；不适用时返回 null。
 *
 * 取第一个 `.apk` 资产。`/releases/latest` 本身已经排除 draft 与 prerelease，这里再判一次
 * 是防「以后改成列表接口」时静默地把 beta 包发给所有人。
 */
internal fun parseRelease(json: String): UpdateRelease? {
    val root = runCatching { org.json.JSONObject(json) }.getOrNull() ?: return null
    if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
    val tag = root.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
    val assets = root.optJSONArray("assets") ?: return null
    for (i in 0 until assets.length()) {
        val asset = assets.optJSONObject(i) ?: continue
        val name = asset.optString("name")
        if (!name.endsWith(".apk", ignoreCase = true)) continue
        val url = asset.optString("browser_download_url").takeIf { it.isNotBlank() } ?: continue
        return UpdateRelease(
            tag = tag,
            versionName = normalizeVersion(tag),
            notes = root.optString("body").trim(),
            apkName = name,
            apkUrl = url,
            sizeBytes = asset.optLong("size").coerceAtLeast(0L),
        )
    }
    return null
}
