/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MpvPlaybackHandoffTest {
    @Test
    fun preservesValidFractionalPosition() {
        assertEquals(81.75, MpvPlaybackHandoff.position(81.75)!!, 0.0)
        assertArrayEquals(
            arrayOf("seek", "81.75", "absolute"),
            MpvPlaybackHandoff.seekCommand(81.75)
        )
    }

    @Test
    fun rejectsUnknownNonFiniteAndNonsensicalPositions() {
        for (seconds in listOf<Double?>(null, -2.0, 0.0, 0.5, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 700000.0)) {
            assertNull(MpvPlaybackHandoff.position(seconds))
            assertNull(MpvPlaybackHandoff.seekCommand(seconds))
        }
    }

    @Test
    fun seeksAtTheStartOnlyWhenAlreadyPlaying() {
        assertEquals(0.51, MpvPlaybackHandoff.position(0.51)!!, 0.0)
    }
}
