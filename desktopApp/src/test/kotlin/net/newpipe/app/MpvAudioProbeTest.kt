/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvAudioProbeTest {
    @Test fun identifiesExternalHlsAudioSegments() {
        val data = """
            #EXTM3U
            #EXT-X-TARGETDURATION:5
            #EXTINF:5.0,
            segment1.aac
            #EXTINF:5.0,
            segment2.aac
        """.trimIndent()
        assertEquals(
            "HLS audio segments listed=2, variants=0, keys=false",
            MpvAudioProbe.summarizeHls(data)
        )
    }

    @Test fun noticesEncryptionWithoutPrintingPrivateUris() {
        val data = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="https://example.com/signed-secret"
            #EXTINF:5,
            https://example.com/another-secret
        """.trimIndent()
        val result = MpvAudioProbe.summarizeHls(data)
        assertTrue(result.contains("keys=true"))
        assertTrue(!result.contains("secret"))
    }

    @Test fun invalidPlaylistDoesNotLookLikeRealAudio() {
        assertEquals("not an HLS playlist",
            MpvAudioProbe.summarizeHls("not a playlist"))
    }
}
