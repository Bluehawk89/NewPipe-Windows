/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

/**
 * Rank playable sources for mpv using evidence from Windows A/B testing.
 *
 * mpv decoded video but could not play the HLS master on the known failure
 * case; HLS variants explicitly paired with audio were synchronized.
 * Prefer a muxed stream or a verified video/audio pair, while keeping the
 * master as a later option for streams where mpv handles it correctly.
 *
 * A label is only a format hint, not proof of decodability. Native player
 * health checks decide whether to advance to the next candidate.
 * Never log temporary signed media URLs.
 */
internal object MpvStreamSelection {
    fun prioritize(all: List<PlaybackOption>): List<PlaybackOption> {
        val muxed = all.filter { !it.label.startsWith("HLS") &&
            !it.label.startsWith("DASH") &&
            !it.label.startsWith("Separate tracks") &&
            it.audioUrl == null }
        val pairs = all.filter { it.audioUrl != null }
        val masters = all.filter { it.label.startsWith("HLS master") }
        // Explicit audio pairing worked on the problematic live-like video,
        // while the master manifest failed. Avoid making that one bad
        // candidate block video startup every time.
        return (muxed + pairs + masters + all)
            .filter { it.url.startsWith("https://") }
            .distinctBy { it.url }
            .take(14)
    }
}
