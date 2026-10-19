/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvMediaEventGuardTest {
    private val signedA = "https://cdn.googlevideo.com/videoplayback?id=1&expire=100&sig=alpha"
    private val signedB = "https://cdn.googlevideo.com/videoplayback?id=2&expire=101&sig=beta"

    @Test
    fun acceptsOnlyCurrentFileLoadedEvent() {
        assertTrue(MpvMediaEventGuard.matchesLoadedMedia(8, signedA, signedA))
        assertFalse(MpvMediaEventGuard.matchesLoadedMedia(7, signedA, signedA))
        assertFalse(MpvMediaEventGuard.matchesLoadedMedia(8, signedA, signedB))
    }

    @Test
    fun neverTrustsMissingMediaPath() {
        assertFalse(MpvMediaEventGuard.matchesLoadedMedia(8, null, signedA))
        assertFalse(MpvMediaEventGuard.matchesLoadedMedia(8, signedA, null))
        assertFalse(MpvMediaEventGuard.matchesLoadedMedia(8, "", ""))
    }

    @Test
    fun treatsExpiredSignatureVariantsAsDifferentInputs() {
        val renewed = signedA.replace("expire=100", "expire=200")
        assertFalse(MpvMediaEventGuard.matchesLoadedMedia(8, signedA, renewed))
    }
}
