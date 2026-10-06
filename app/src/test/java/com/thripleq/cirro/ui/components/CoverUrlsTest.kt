package com.thripleq.cirro.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 封面地址缩放参数（`coverSizedUrl`）的测试。
 *
 * 起因是用户的体感：「进一个列表下载量变大、小封面出现变慢」。实测下来是**对的**：
 * Coil 的 `size(px)` 只管解码、不管下载，列表行 52dp（解码 120px）的小封面一直在下
 * 2048×2048 的原图。修法是让 CDN 自己缩（`?param=WxH`）。
 *
 * 下面这些断言全部对应 2026-10-07 对**同一张真实封面**的 curl 实测（K40 / 同 CDN）：
 *
 * | 请求 | 实际下载 |
 * |---|---|
 * | 原图 | 4,743,886 B |
 * | `param=1024y1024` | 1,048,576 B |
 * | `param=360y360` | 216,209 B |
 * | `param=143y143` | 42,933 B（非整十也支持） |
 * | `param=120y120` | 31,993 B |
 * | `param=24y24` | 2,396 B |
 * | `param=2048y2048` | 4,086,402 B（超过原图自动封顶） |
 * | `param=0y0` | **HTTP 400** ← 所以必须挡住 px<=0 |
 * | `param=abc` | 4,743,886 B（不认识就忽略、回原图） |
 */
class CoverUrlsTest {

    /** 实测用的那张《希忘Hope》封面，p1 / p2 是同一个文件的两个镜像子域。 */
    private val p2 = "https://p2.music.126.net/cpppq3vKqjQ5YwzNYM_C7Q==/109951168244956668.jpg"
    private val p1 = "https://p1.music.126.net/cpppq3vKqjQ5YwzNYM_C7Q==/109951168244956668.jpg"

    @Test
    fun realCover_getsRowSizeParam() {
        assertThat(coverSizedUrl(p2, 120))
            .isEqualTo("$p2?param=120y120")
    }

    /** 两个镜像子域都要认（同一首歌的 `artworkUrl` 实测会在 p1 / p2 之间变）。 */
    @Test
    fun bothMirrorHosts_areSized() {
        assertThat(coverSizedUrl(p1, 24)).isEqualTo("$p1?param=24y24")
        assertThat(coverSizedUrl(p2, 24)).isEqualTo("$p2?param=24y24")
    }

    /** 尺寸进 URL ⇒ 不同解码尺寸是两条缓存，互不覆盖。 */
    @Test
    fun differentSizes_produceDifferentUrls() {
        assertThat(coverSizedUrl(p2, 120)).isNotEqualTo(coverSizedUrl(p2, 360))
    }

    // ── 必须放行原地址的四种情况 ────────────────────────────────────────

    /** `param=0y0` 实测直接 400（不是回退原图）—— 非法尺寸必须原样放行。 */
    @Test
    fun nonPositiveSize_isLeftAlone() {
        assertThat(coverSizedUrl(p2, 0)).isEqualTo(p2)
        assertThat(coverSizedUrl(p2, -1)).isEqualTo(p2)
    }

    /** 非网易图床：`param` 是它自家的服务，别处加了会 404。 */
    @Test
    fun foreignHost_isLeftAlone() {
        val other = "https://i.scdn.co/image/ab67616d0000b273.jpg"
        assertThat(coverSizedUrl(other, 120)).isEqualTo(other)
    }

    /** 后缀判定的绕过面：`evilmusic.126.net` 与 `….music.126.net.evil.com` 都不是网易图床。 */
    @Test
    fun lookalikeHosts_areLeftAlone() {
        val a = "https://evilmusic.126.net/x.jpg"
        val b = "https://p1.music.126.net.evil.com/x.jpg"
        assertThat(coverSizedUrl(a, 120)).isEqualTo(a)
        assertThat(coverSizedUrl(b, 120)).isEqualTo(b)
    }

    /** 调用方已经指定过尺寸就不动它（尊重既定值，也不重复拼参数）。 */
    @Test
    fun existingParam_isKept() {
        val already = "$p2?param=200y200"
        assertThat(coverSizedUrl(already, 120)).isEqualTo(already)
    }

    @Test
    fun blankOrNull_isPassedThrough() {
        assertThat(coverSizedUrl(null, 120)).isNull()
        assertThat(coverSizedUrl("", 120)).isEmpty()
        assertThat(coverSizedUrl("   ", 120)).isEqualTo("   ")
    }

    /** 无 scheme 的地址（库里出现过 `//host/path` 形态）不猜协议，原样放行。 */
    @Test
    fun schemelessUrl_isLeftAlone() {
        val schemeless = "//p1.music.126.net/x.jpg"
        assertThat(coverSizedUrl(schemeless, 120)).isEqualTo(schemeless)
    }

    // ── 拼接正确性 ────────────────────────────────────────────────────

    /** 地址已带别的 query 时用 `&` 接，不能拼出两个 `?`。 */
    @Test
    fun existingQuery_usesAmpersand() {
        val withQuery = "$p2?foo=bar"
        val out = coverSizedUrl(withQuery, 64)
        assertThat(out).isEqualTo("$withQuery&param=64y64")
        assertThat(out!!.count { it == '?' }).isEqualTo(1)
    }

    /** 带端口也要能认出 host（`substringBefore(':')` 之后仍落在网易图床）。 */
    @Test
    fun hostWithPort_isRecognized() {
        val withPort = "https://p1.music.126.net:443/x.jpg"
        assertThat(coverSizedUrl(withPort, 480)).isEqualTo("$withPort?param=480y480")
    }

    /** 各调用点实际在用的尺寸，逐个确认拼得出来（含取色用的 24 与糊底用的 64）。 */
    @Test
    fun everySizeInUse_roundTrips() {
        for (px in listOf(24, 64, 120, 360, 480, 1080)) {
            assertThat(coverSizedUrl(p2, px)).isEqualTo("$p2?param=${px}y$px")
        }
    }
}
