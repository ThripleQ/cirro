package com.thripleq.cirro.core.update

/**
 * 版本号比较：release tag（`v0.3.4`）对 `BuildConfig.VERSION_NAME`（`0.3.4`）。
 *
 * 为什么不用 `PackageInfo.versionCode` 比：GitHub API 的 release 响应里**没有** versionCode
 * 字段（它只存在于 APK 内部），所以「有没有新版」只能看 tag；versionCode 留给下载后的
 * 包校验（[UpdateChecker.verify]）兜底 —— 两个数字都过，才算真的能装。
 *
 * 口径按语义化版本（semver）的常用子集：
 * - 前缀 `v`/`V` 忽略；
 * - 按 `.` 切数字段逐段比，缺段按 0 补（`0.4` == `0.4.0`）；
 * - 数字段全同再看预发布后缀：`0.4.0-beta` < `0.4.0`（同一版本号下正式版更新）。
 *
 * 解析不了（含非数字段，如 `0.4.x`）时返回 0 —— 宁可当作「没更新」，也不要把一个
 * 解析不出的 tag 当作新版去打扰用户。
 */
internal fun compareVersions(candidate: String, current: String): Int {
    val a = parseVersion(candidate) ?: return 0
    val b = parseVersion(current) ?: return 0
    val size = maxOf(a.numbers.size, b.numbers.size)
    for (i in 0 until size) {
        val x = a.numbers.getOrElse(i) { 0 }
        val y = b.numbers.getOrElse(i) { 0 }
        if (x != y) return x.compareTo(y)
    }
    return when {
        a.preRelease == b.preRelease -> 0
        a.preRelease.isEmpty() -> 1
        b.preRelease.isEmpty() -> -1
        else -> a.preRelease.compareTo(b.preRelease)
    }
}

/** 去掉常见前缀与首尾空白，得到可以进 [compareVersions] / 展示的版本串。 */
internal fun normalizeVersion(text: String): String =
    text.trim().removePrefix("v").removePrefix("V")

private data class ParsedVersion(val numbers: List<Int>, val preRelease: String)

private fun parseVersion(text: String): ParsedVersion? {
    val s = normalizeVersion(text)
    if (s.isEmpty()) return null
    val core = s.substringBefore('-').substringBefore('+')
    val numbers = core.split('.').map { it.toIntOrNull() ?: return null }
    if (numbers.isEmpty()) return null
    // `0.4.0-beta.1+build` → 后缀 "beta.1"；`0.4.0+build` → 空（build 不影响先后）。
    val preRelease = s.removePrefix(core).removePrefix("-").substringBefore('+')
    return ParsedVersion(numbers, preRelease)
}
