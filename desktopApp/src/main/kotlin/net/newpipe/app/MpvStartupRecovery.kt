/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

/**
 * A conservative bounded startup-recovery policy for the experimental player.
 *
 * Do NOT equate "mpv accepted URL" with both an audible audio track and
 * presented video. For an unplayable candidate move to the next one once,
 * keeping the currently selected source on success. Never interrupt a
 * deliberately paused player. This is a startup policy, not frame-freeze
 * detection during sustained playback.
 */
internal object MpvStartupRecovery {
    fun advance(
        optionsRemain: Boolean,
        paused: Boolean,
        secondsWithoutTracks: Int,
        secondsVideoOnly: Int
    ): Boolean = optionsRemain && !paused &&
        (secondsWithoutTracks >= 15 || secondsVideoOnly >= 12)
}
