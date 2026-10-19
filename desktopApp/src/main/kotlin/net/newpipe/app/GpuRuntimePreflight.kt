/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

/**
 * Verifies packaged JVM support before constructing LWJGL AWT/OpenGL objects.
 * The old installer could compile successfully yet fail inside MemoryUtil's
 * static initializer because jlink had removed sun.misc.Unsafe.
 *
 * Catch LinkageError / initializer errors here, before Compose tries to
 * build a SwingPanel. This guards Java runtime failures, not all possible
 * native device-driver crashes. No signed media URL is involved.
 */
internal object GpuRuntimePreflight {
    internal fun missingRequiredModule(modules: Set<String>): String? =
        if ("jdk.unsupported" !in modules)
            "The installer is missing the Java graphics memory module."
        else null

    fun check(): String? {
        val modules = ModuleLayer.boot().modules().map { it.name }.toSet()
        missingRequiredModule(modules)?.let { return it }
        return try {
            // Class.forName(..., initialize=true) exercises the actual
            // MemoryUtil backend that failed on the supervisor's machine.
            Class.forName("org.lwjgl.system.MemoryUtil", true, javaClass.classLoader)
            val memory = org.lwjgl.system.MemoryUtil.memAlloc(8)
            try {
                memory.putLong(0, 0x1234L)
                check(memory.getLong(0) == 0x1234L)
            } finally {
                org.lwjgl.system.MemoryUtil.memFree(memory)
            }
            null
        } catch (_: Throwable) {
            // No exception messages: native loader messages can reveal
            // filesystem paths or other host-specific information.
            "The experimental graphics runtime could not initialize. " +
                "Your regular mpv player remains available."
        }
    }
}
