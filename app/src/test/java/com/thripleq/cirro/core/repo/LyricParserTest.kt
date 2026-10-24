package com.thripleq.cirro.core.repo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 歌词解析（`LyricRepository.kt`）的判据测试。
 *
 * 两件事最容易静默错：**分行**（CR/CRLF、BOM、一行多标签）和**逐字片段边界**
 * （文本在标记之后、下一个标记之前，英文空格挂在词尾）。错了不会崩，只会显示成
 * 串行、缺行或高亮错位。样本取自 2026-10-24 登录态探针的官方返回。
 */
class LyricParserTest {

    // ------------------------------------------------------------ 行级 LRC

    @Test
    fun `lrc handles crlf cr bom and multiple tags`() {
        val lyrics = parseLyrics(
            mainLrc = "\uFEFF[00:01.00]第一行\r\n[00:02.00][00:03.50]第二行\r[00:04.00]",
            translationLrc = null,
        )
        assertThat(lyrics.lines.map { it.timeMs })
            .containsExactly(1_000L, 2_000L, 3_500L)
            .inOrder()
        assertThat(lyrics.lines.map { it.text })
            .containsExactly("第一行", "第二行", "第二行")
            .inOrder()
    }

    @Test
    fun `translation matches exact timestamp first`() {
        val lyrics = parseLyrics(
            mainLrc = "[00:01.00]main\n[00:02.00]next",
            translationLrc = "[00:01.00]译\n[00:02.00]次",
        )
        assertThat(lyrics.lines[0].translation).isEqualTo("译")
        assertThat(lyrics.lines[1].translation).isEqualTo("次")
    }

    @Test
    fun `translation tolerates small offset but not a far one`() {
        val near = parseLyrics("[00:01.00]a", "[00:01.10]近")
        assertThat(near.lines[0].translation).isEqualTo("近")

        val far = parseLyrics("[00:01.00]a", "[00:01.30]远")
        assertThat(far.lines[0].translation).isNull()
    }

    // ------------------------------------------------------------ 逐字 yrc

    @Test
    fun `yrc splits cjk per character and keeps english words whole`() {
        val lyrics = parseLyrics(
            mainLrc = "[00:00.00]fallback",
            translationLrc = null,
            wordLrc = """
                [1000,1000](1000,400,0)We (1400,600,0)were
                [2000,700](2000,300,0)你(2300,400,0)好
            """.trimIndent(),
        )
        assertThat(lyrics.lines).hasSize(2)

        val english = lyrics.lines[0]
        assertThat(english.timeMs).isEqualTo(1_000L)
        assertThat(english.text).isEqualTo("We were")
        assertThat(english.words.map { it.text }).containsExactly("We ", "were").inOrder()
        assertThat(english.words.map { it.timeMs }).containsExactly(1_000L, 1_400L).inOrder()

        val chinese = lyrics.lines[1]
        assertThat(chinese.text).isEqualTo("你好")
        assertThat(chinese.words.map { it.timeMs }).containsExactly(2_000L, 2_300L).inOrder()
        assertThat(chinese.words.map { it.durationMs }).containsExactly(300L, 400L).inOrder()
    }

    @Test
    fun `yrc trims edge whitespace but preserves inner spaces`() {
        val lyrics = parseLyrics(
            mainLrc = null,
            translationLrc = null,
            wordLrc = "[0,276](0,276,0) 作词 : 米津玄師",
        )
        assertThat(lyrics.lines).hasSize(1)
        assertThat(lyrics.lines[0].text).isEqualTo("作词 : 米津玄師")
        assertThat(lyrics.lines[0].words[0].text).isEqualTo("作词 : 米津玄師")
    }

    @Test
    fun `yrc keeps zero duration punctuation as its own segment`() {
        val lyrics = parseLyrics(
            mainLrc = null,
            translationLrc = null,
            wordLrc = "[26720,5460](26720,450,0)On (27170,90,0)a (27260,840,0)balcony " +
                "(28100,150,0)in (28250,600,0)summer (28850,1560,0)air(30410,1770,0).",
        )
        val line = lyrics.lines.single()
        assertThat(line.text).isEqualTo("On a balcony in summer air.")
        val last = line.words.last()
        assertThat(last.text).isEqualTo(".")
        assertThat(last.timeMs).isEqualTo(30_410L)
        assertThat(last.durationMs).isEqualTo(1_770L)
    }

    @Test
    fun `yrc wins over lrc and takes ytlrc for translation`() {
        val lyrics = parseLyrics(
            mainLrc = "[00:01.00]行级文本",
            translationLrc = "[00:01.00]行级翻译",
            wordLrc = "[1000,500](1000,250,0)你(1250,250,0)好",
            wordTranslationLrc = "[00:01.05]你好呀",
        )
        assertThat(lyrics.lines).hasSize(1)
        assertThat(lyrics.lines[0].text).isEqualTo("你好")
        assertThat(lyrics.lines[0].translation).isEqualTo("你好呀")
        assertThat(lyrics.lines[0].words).hasSize(2)
    }

    @Test
    fun `empty yrc falls back to lrc without word timings`() {
        val lyrics = parseLyrics(
            mainLrc = "[00:01.00]a\n[00:02.00]b",
            translationLrc = null,
            wordLrc = "",
            wordTranslationLrc = "",
        )
        assertThat(lyrics.lines.map { it.text }).containsExactly("a", "b").inOrder()
        assertThat(lyrics.lines[0].words).isEmpty()
        assertThat(lyrics.lines[1].words).isEmpty()
    }
}
