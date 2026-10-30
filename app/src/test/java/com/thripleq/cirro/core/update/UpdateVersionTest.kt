package com.thripleq.cirro.core.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 版本比较是「要不要提示更新」的唯一判据：放大了说错一次就会把所有人都弹一遍，
 * 说小了是 0.3.10 被当成比 0.3.9 旧这种经典错误。这里把口径钉住。
 */
class UpdateVersionTest {

    @Test
    fun `higher patch wins`() {
        assertThat(compareVersions("v0.3.5", "0.3.4")).isGreaterThan(0)
        assertThat(compareVersions("0.3.4", "v0.3.5")).isLessThan(0)
    }

    @Test
    fun `equal versions with and without prefix`() {
        assertThat(compareVersions("v0.3.4", "0.3.4")).isEqualTo(0)
        assertThat(compareVersions("V0.3.4", "0.3.4")).isEqualTo(0)
    }

    @Test
    fun `missing segments count as zero`() {
        assertThat(compareVersions("0.4", "0.4.0")).isEqualTo(0)
        assertThat(compareVersions("0.4.0.1", "0.4")).isGreaterThan(0)
    }

    @Test
    fun `numeric comparison is not lexicographic`() {
        // 字符串比较会把 "0.3.10" 判成小于 "0.3.9"。
        assertThat(compareVersions("0.3.10", "0.3.9")).isGreaterThan(0)
        assertThat(compareVersions("0.10.0", "0.9.9")).isGreaterThan(0)
    }

    @Test
    fun `prerelease sorts before the release`() {
        assertThat(compareVersions("0.4.0-beta.1", "0.4.0")).isLessThan(0)
        assertThat(compareVersions("0.4.0", "0.4.0-beta.1")).isGreaterThan(0)
        assertThat(compareVersions("0.4.0-alpha", "0.4.0-beta")).isLessThan(0)
    }

    @Test
    fun `build metadata does not affect order`() {
        assertThat(compareVersions("0.4.0+build7", "0.4.0")).isEqualTo(0)
    }

    @Test
    fun `unparsable version is treated as no update`() {
        assertThat(compareVersions("vNext", "0.3.4")).isEqualTo(0)
        assertThat(compareVersions("", "0.3.4")).isEqualTo(0)
        assertThat(compareVersions("0.3.x", "0.3.4")).isEqualTo(0)
    }
}
