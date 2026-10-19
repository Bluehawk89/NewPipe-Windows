/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

/**
 * Order streams for mpv's independent demuxer. A signed HLS master URL is
 * preferable to a child rendition because it tells the native engine which
 * audio rendition belongs to a video variant. Unlike VLC, mpv can also load
 * separate NewPipe Extractor audio+video tracks via audio-add.
 *
 * Only the URL and safe local label are retained; NEVER log stream URLs.
 */
internal object MpvStreamSelection {
    fun prioritize(all: List<PlaybackOption>): List<PlaybackOption> {
        val muxed = all.filter { !it.label.startsWith("HLS") &&
            !it.label.startsWith("DASH") &&
            !it.label.startsWith("Separate tracks") &&
            it.audioUrl == null }
        val masters = all.filter { it.label.startsWith("HLS master") }
        val pairs = all.filter { it.audioUrl != null }
        return (muxed + masters + pairs + all)
            .filter { it.url.startsWith("https://") }
            .distinctBy { it.url }
            .take(14)
    }
}
