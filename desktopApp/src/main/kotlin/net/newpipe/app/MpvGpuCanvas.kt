/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import java.awt.EventQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns a real application-managed OpenGL framebuffer. Unlike --wid mode,
 * libmpv does not create or overpaint its own child video window; it draws
 * into our GL backbuffer from mpv_render_context_render().
 *
 * This still uses a heavyweight AWTGLCanvas to bootstrap an OpenGL context.
 * Compose/Skia framebuffer texture integration remains a later experiment.
 */
internal class MpvGpuCanvas(
    private val bridge: MpvGlRenderBridge,
    private val onReady: () -> Unit,
    private val onFailure: (String) -> Unit
) : AWTGLCanvas(GLData().apply {
    majorVersion = 3
    minorVersion = 2
    profile = GLData.Profile.CORE
}) {
    private val queued = AtomicBoolean(false)
    private val failureDelivered = AtomicBoolean(false)
    @Volatile private var finished = false

    private fun failSafely(stage: String, failure: Throwable) {
        if (!failureDelivered.compareAndSet(false, true)) return
        finished = true
        // Never include native exception messages: these may contain
        // sensitive host paths. Failure is reported on the AWT event thread
        // and the Compose parent restores the working mpv renderer.
        EventQueue.invokeLater {
            onFailure("GPU " + stage + " failed (" +
                failure.javaClass.simpleName + ").")
        }
    }

    fun requestGpuFrame() {
        if (finished || !queued.compareAndSet(false, true)) return
        EventQueue.invokeLater {
            queued.set(false)
            if (!finished && isDisplayable && isShowing) {
                try {
                    render()
                } catch (failure: Throwable) {
                    failSafely("render", failure)
                }
            }
        }
    }

    override fun requestRender() {
        requestGpuFrame()
    }

    override fun initGL() {
        try {
            GL.createCapabilities()
            bridge.createOnGlThread { requestGpuFrame() }
            if (bridge.ready) {
                EventQueue.invokeLater { if (!finished) onReady() }
            } else {
                failSafely(
                    "initialization",
                    IllegalStateException("libmpv OpenGL context unavailable")
                )
            }
        } catch (failure: Throwable) {
            failSafely("initialization", failure)
        }
    }

    override fun paintGL() {
        val width = getFramebufferWidth()
        val height = getFramebufferHeight()
        if (width <= 0 || height <= 0) return
        // When the player is still loading, paint a neutral black surface.
        // Actual playback frames are provided by the mpv render API.
        GL11.glViewport(0, 0, width, height)
        GL11.glClearColor(0f, 0f, 0f, 1f)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
        val rendered = bridge.renderOnGlThread(width, height)
        swapBuffers()
        if (rendered) bridge.reportSwapOnGlThread()
    }

    override fun disposeGL() {
        finished = true
        // This callback is invoked with OUR GL context current. Do not call
        // Swing invokeAndWait or wait for a native-media worker while holding
        // the native canvas lifecycle lock.
        runCatching { bridge.releaseRendererOnGlThread() }
            .onFailure { failSafely("release", it) }
        Thread({
            bridge.destroyCoreAfterRenderer()
        }, "mpv-gpu-core-destroy").apply {
            isDaemon = true
            start()
        }
    }
}
