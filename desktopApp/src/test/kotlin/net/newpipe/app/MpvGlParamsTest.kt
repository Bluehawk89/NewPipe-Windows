/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

import com.sun.jna.Native
import com.sun.jna.Pointer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MpvGlParamsTest {
    @Test
    fun nativeParameterEntriesRespectPointerAlignment() {
        val payload = Pointer.createConstant(0x1234L)
        val args = MpvGlParams.encode(
            MpvGlParams.API_TYPE to payload,
            MpvGlParams.OPENGL_INIT to null
        )
        val step = MpvGlParams.entrySize
        val pointerOffset = MpvGlParams.pointerOffset
        assertEquals(1, args.getInt(0L))
        assertEquals(0x1234L, Pointer.nativeValue(args.getPointer(pointerOffset)))
        assertEquals(2, args.getInt(step))
        assertNull(args.getPointer(step + pointerOffset))
        assertEquals(0, args.getInt(step * 2L))
        assertNull(args.getPointer(step * 2L + pointerOffset))
        assertEquals(
            (if (Native.POINTER_SIZE == 8) 16L else 8L),
            step
        )
    }

    @Test
    fun zeroParametersStillHaveRequiredTerminator() {
        val args = MpvGlParams.encode()
        assertEquals(0, args.getInt(0L))
        assertNull(args.getPointer(MpvGlParams.pointerOffset))
    }
}
