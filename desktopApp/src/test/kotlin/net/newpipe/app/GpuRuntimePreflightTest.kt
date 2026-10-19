/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpuRuntimePreflightTest {
    @Test
    fun warnsAboutMissingUnsafeBackendBeforeCanvasIsConstructed() {
        assertEquals(
            "The installer is missing the Java graphics memory module.",
            GpuRuntimePreflight.missingRequiredModule(setOf("java.base", "java.desktop"))
        )
    }

    @Test
    fun acceptsRuntimeWithUnsafeModule() {
        assertNull(GpuRuntimePreflight.missingRequiredModule(
            setOf("java.base", "java.desktop", "jdk.unsupported")
        ))
    }

    @Test
    fun initializesLwjglMemoryBackendOnWindowsBuildMachine() {
        // A full CI JDK can pass while the packaged image still fails. The
        // workflow separately executes --gpu-preflight from the actual
        // jpackage app-image launcher to close that gap.
        assertNull(GpuRuntimePreflight.check())
    }
}
