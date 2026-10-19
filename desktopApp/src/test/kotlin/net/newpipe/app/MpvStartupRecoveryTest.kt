/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvStartupRecoveryTest {
    @Test
    fun doNotSkipAWorkingStream() {
        assertFalse(MpvStartupRecovery.advance(true, false, 0, 0))
        assertFalse(MpvStartupRecovery.advance(true, false, 2, 8))
        assertFalse(MpvStartupRecovery.advance(true, false, 14, 11))
    }

    @Test
    fun moveOnWhenNeitherTrackAppearsOrVideoPlaysSilently() {
        assertTrue(MpvStartupRecovery.advance(true, false, 15, 0))
        assertTrue(MpvStartupRecovery.advance(true, false, 0, 12))
    }

    @Test
    fun neverSwitchOnPauseOrAfterLastCandidate() {
        assertFalse(MpvStartupRecovery.advance(false, false, 30, 30))
        assertFalse(MpvStartupRecovery.advance(true, true, 30, 30))
    }
}
