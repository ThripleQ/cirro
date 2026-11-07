package com.thripleq.cirro.core.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 开关语义的锁：默认必须是老行为（独占焦点），否则一升级就变成「所有人的声音混在一起」。
 */
class AudioFocusPolicyTest {

    @Test
    fun default_keepsExclusiveAudioFocus() {
        assertThat(AudioFocusPolicy.DEFAULT_ALLOW_CONCURRENT_PLAYBACK).isFalse()
        assertThat(
            AudioFocusPolicy.handlesAudioFocus(
                AudioFocusPolicy.DEFAULT_ALLOW_CONCURRENT_PLAYBACK,
            ),
        ).isTrue()
    }

    @Test
    fun allowingConcurrentPlayback_releasesAudioFocus() {
        assertThat(AudioFocusPolicy.handlesAudioFocus(true)).isFalse()
    }

    @Test
    fun disablingConcurrentPlayback_restoresAudioFocus() {
        assertThat(AudioFocusPolicy.handlesAudioFocus(false)).isTrue()
    }
}
