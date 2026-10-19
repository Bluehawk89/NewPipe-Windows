/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

/**
 * Keep native-player handoff positions finite, positive and bounded.
 * A live stream or non-seekable source can still reject an absolute seek;
 * callers treat this as best-effort and never block playback on failure.
 */
internal object MpvPlaybackHandoff {
    fun position(seconds: Double?): Double? =
        seconds?.takeIf { it.isFinite() && it > 0.5 && it <= 7.0 * 24 * 60 * 60 }

    fun seekCommand(seconds: Double?): Array<String>? =
        position(seconds)?.let { arrayOf("seek", it.toString(), "absolute") }
}
