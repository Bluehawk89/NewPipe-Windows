/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class HlsMasterParserTest {
    private val base = URI("https://media.googlevideo.com/live/master.m3u8")

    @Test
    fun quotedCodecsDoNotBreakAudioGroupLookup() {
        val master = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio01",NAME="English",LANGUAGE="en",DEFAULT=YES,URI="audio/eng.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=2400000,CODECS="avc1.4d401f,mp4a.40.2",RESOLUTION=1280x720,AUDIO="audio01"
            video/720.m3u8
        """.trimIndent()
        val streams = HlsMasterParser.variants(master, base)
        assertEquals(1, streams.size)
        assertEquals("HLS 720p + audio", streams[0].label)
        assertEquals("https://media.googlevideo.com/live/video/720.m3u8", streams[0].url)
        assertEquals("https://media.googlevideo.com/live/audio/eng.m3u8", streams[0].audioUrl)
        assertEquals(
            "avc1.4d401f,mp4a.40.2",
            HlsMasterParser.attributes(
                """BANDWIDTH=100,CODECS="avc1.4d401f,mp4a.40.2",AUDIO="audio01" """
            )["CODECS"]
        )
    }

    @Test
    fun pairedRenditionsArePreferredAndQualityIsCapped() {
        val master = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",DEFAULT=NO,LANGUAGE="es",URI="es.m3u8"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",DEFAULT=YES,LANGUAGE="en",URI="en.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=100,RESOLUTION=1920x1080,AUDIO="aud"
            v1080.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=100,RESOLUTION=1280x720,AUDIO="aud"
            v720.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=100,RESOLUTION=640x360
            v360.m3u8
        """.trimIndent()
        val options = HlsMasterParser.variants(master, base)
        assertEquals(3, options.size)
        assertTrue(options[0].url.endsWith("/v720.m3u8"))
        assertTrue(options[1].url.endsWith("/v1080.m3u8"))
        assertTrue(options[2].url.endsWith("/v360.m3u8"))
        assertEquals("https://media.googlevideo.com/live/en.m3u8", options[0].audioUrl)
        assertNull(options[2].audioUrl)
    }

    @Test
    fun unsafeSchemesAndMalformedPlaylistsAreExcluded() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=100,RESOLUTION=1280x720
            file:///C:/Windows/private.txt
            #EXT-X-STREAM-INF:BANDWIDTH=100,RESOLUTION=640x360
            http://127.0.0.1/admin
            #EXT-X-STREAM-INF:BANDWIDTH=100,RESOLUTION=640x480
            https://media.googlevideo.com/safe.m3u8
        """.trimIndent()
        val streams = HlsMasterParser.variants(master, base)
        assertEquals(1, streams.size)
        assertEquals("https://media.googlevideo.com/safe.m3u8", streams[0].url)
        assertTrue(HlsMasterParser.variants("not an HLS playlist", base).isEmpty())
        assertTrue(HlsMasterParser.variants(master, base, limit = 0).isEmpty())
    }

    @Test
    fun duplicateVideoUrlsCollapseAndMissingAudioGroupsRemainGraceful() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=100,RESOLUTION=1280x720,AUDIO="missing"
            vid.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=200,RESOLUTION=1920x1080,AUDIO="missing"
            vid.m3u8
        """.trimIndent()
        val streams = HlsMasterParser.variants(master, base)
        assertEquals(1, streams.size)
        assertFalse(streams[0].label.contains("+ audio"))
        assertNull(streams[0].audioUrl)
    }

    @Test
    fun rejectsCrossSchemeAudioButKeepsLegitimateVideo() {
        val master = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",DEFAULT=YES,URI="javascript:alert(1)"
            #EXT-X-STREAM-INF:BANDWIDTH=100,RESOLUTION=1280x720,AUDIO="a"
            720.m3u8
        """.trimIndent()
        val streams = HlsMasterParser.variants(master, base)
        assertEquals(1, streams.size)
        assertNull(streams.first().audioUrl)
    }
}
