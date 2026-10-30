package com.thripleq.cirro.core.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * GitHub `releases/latest` 响应的解析。真实响应字段很多，这里只钉住我们会用到的
 * 那几项与两条排除规则（draft / prerelease）。
 */
class ReleaseParsingTest {

    @Test
    fun `picks the apk asset and normalizes the tag`() {
        val json = """
            {
              "tag_name": "v0.3.5",
              "body": "fix(player): 修一个东西\nfeat: 加一个东西",
              "draft": false,
              "prerelease": false,
              "assets": [
                {"name": "checksums.txt", "size": 120, "browser_download_url": "https://example.com/checksums.txt"},
                {"name": "cirro-v0.3.5.apk", "size": 12345678, "browser_download_url": "https://example.com/cirro-v0.3.5.apk"}
              ]
            }
        """.trimIndent()

        val release = parseRelease(json)
        assertThat(release).isNotNull()
        assertThat(release!!.tag).isEqualTo("v0.3.5")
        assertThat(release.versionName).isEqualTo("0.3.5")
        assertThat(release.apkName).isEqualTo("cirro-v0.3.5.apk")
        assertThat(release.apkUrl).isEqualTo("https://example.com/cirro-v0.3.5.apk")
        assertThat(release.sizeBytes).isEqualTo(12345678L)
        assertThat(release.notes).contains("fix(player)")
    }

    @Test
    fun `prerelease and draft are rejected`() {
        val prerelease = """{"tag_name":"v0.4.0-beta.1","prerelease":true,
            "assets":[{"name":"a.apk","browser_download_url":"https://example.com/a.apk"}]}"""
        val draft = """{"tag_name":"v0.4.0","draft":true,
            "assets":[{"name":"a.apk","browser_download_url":"https://example.com/a.apk"}]}"""
        assertThat(parseRelease(prerelease)).isNull()
        assertThat(parseRelease(draft)).isNull()
    }

    @Test
    fun `no apk asset means no update`() {
        val json = """{"tag_name":"v0.3.5","assets":[{"name":"source.zip","browser_download_url":"https://example.com/s.zip"}]}"""
        assertThat(parseRelease(json)).isNull()
    }

    @Test
    fun `broken json does not throw`() {
        assertThat(parseRelease("not json")).isNull()
        assertThat(parseRelease("{}")).isNull()
    }
}
