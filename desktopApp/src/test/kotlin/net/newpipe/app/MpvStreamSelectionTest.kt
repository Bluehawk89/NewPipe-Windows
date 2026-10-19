/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvStreamSelectionTest {
    @Test
    fun audioPairedHlsVariantBeforeMasterAndVideoOnly() {
        val child = PlaybackOption(
            "https://cdn.example.org/video720.m3u8", "HLS 720p + audio",
            "https://cdn.example.org/audio.m3u8"
        )
        val master = PlaybackOption(
            "https://cdn.example.org/master.m3u8", "HLS master / live stream"
        )
        val silent = PlaybackOption(
            "https://cdn.example.org/video-only.m3u8", "HLS 360p"
        )
        val ranked = MpvStreamSelection.prioritize(listOf(child, silent, master))
        assertEquals(child.url, ranked[0].url)
        assertEquals(master.url, ranked[1].url)
        assertEquals(silent.url, ranked[2].url)
    }

    @Test
    fun muxedFirstAndNoDuplicateUrls() {
        val muxed = PlaybackOption("https://cdn.example.org/mp4", "MPEG-4 360p")
        val master = PlaybackOption("https://cdn.example.org/master", "HLS master / live stream")
        val dashPair = PlaybackOption(
            "https://cdn.example.org/720", "Separate tracks MPEG-4 720p + audio",
            "https://cdn.example.org/aac"
        )
        val invalid = PlaybackOption("file:///C:/Users/private.txt", "Bad")
        val ranked = MpvStreamSelection.prioritize(
            listOf(master, dashPair, muxed, invalid, muxed)
        )
        assertEquals(listOf(muxed, dashPair, master).map { it.url },
            ranked.map { it.url })
    }

    @Test
    fun pairedDashAndHlsAlternativesPrecedeUnpairedMaster() {
        val unpaired = PlaybackOption("https://cdn.example.org/child", "HLS 360p")
        val master = PlaybackOption("https://cdn.example.org/master", "HLS master / live stream")
        val dash = PlaybackOption(
            "https://cdn.example.org/dash", "Separate tracks MPEG-4 720p + audio",
            "https://cdn.example.org/dash-audio"
        )
        val hls = PlaybackOption(
            "https://cdn.example.org/hls", "HLS 720p + audio",
            "https://cdn.example.org/hls-audio"
        )
        val ranked = MpvStreamSelection.prioritize(listOf(unpaired, master, hls, dash))
        assertEquals(listOf(hls, dash, master, unpaired).map { it.url },
            ranked.map { it.url })
    }

    @Test
    fun emptyAndManyListsRemainBounded() {
        assertTrue(MpvStreamSelection.prioritize(emptyList()).isEmpty())
        val streams = (0 until 50).map {
            PlaybackOption("https://cdn.example.org/$it", "HLS 360p")
        }
        assertEquals(14, MpvStreamSelection.prioritize(streams).size)
    }
}
