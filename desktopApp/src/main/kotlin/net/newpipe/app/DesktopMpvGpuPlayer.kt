/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/**
 * Early integration test ONLY. GPU framebuffer controlled by our GL canvas,
 * but hosted via SwingPanel until texture compositing into Compose is proven.
 */
@Composable
internal fun DesktopMpvGpuPlayer(
    video: VideoResult,
    onFatalError: (String) -> Unit,
    onClose: () -> Unit
) {
    var options by remember(video.url) { mutableStateOf<List<PlaybackOption>>(emptyList()) }
    var selected by remember(video.url) { mutableIntStateOf(0) }
    var error by remember(video.url) { mutableStateOf<String?>(null) }
    var status by remember(video.url) { mutableStateOf<MpvGpuStatus?>(null) }
    var rendererReady by remember(video.url) { mutableStateOf(false) }
    var paused by remember(video.url) { mutableStateOf(false) }
    var copied by remember(video.url) { mutableStateOf(false) }
    val bridge = remember(video.url) { MpvGlRenderBridge() }
    val canvasResult = remember(video.url) {
        runCatching {
            MpvGpuCanvas(
                bridge = bridge,
                onReady = { rendererReady = true },
                onFailure = onFatalError
            )
        }
    }
    val canvas = canvasResult.getOrNull()
    if (canvas == null) {
        // Class initialization can fail before AWT even creates a canvas.
        // Never let a runtime LinkageError crash Compose composition.
        LaunchedEffect(video.url) {
            onFatalError("The experimental OpenGL canvas could not initialize.")
        }
        Text("GPU unavailable. Restoring normal mpv playback.")
        return
    }
    val scope = rememberCoroutineScope()
    val current = options.getOrNull(selected)

    // AWTGLCanvas owns a heavyweight peer and should not be layered over
    // Compose navigation controls. Host the laboratory in an explicitly
    // closeable, NON-MODAL JFrame, keeping the main mpv/navigation UI usable.
    // This is temporary until true Skia texture interop is implemented.
    val host = remember(canvas) {
        GpuLabWindowHandle(canvas) {
            scope.launch { onClose() }
        }
    }
    DisposableEffect(host) {
        host.show()
        onDispose {
            // First stop mpv off the UI thread, then dispose the native
            // OpenGL peer on the Swing EDT. Avoid blocking navigation.
            host.closeAsync(bridge)
        }
    }

    LaunchedEffect(video.url) {
        error = null
        try {
            options = withContext(Dispatchers.IO) {
                MpvStreamSelection.prioritize(
                    DesktopVideoSearch.resolvePlaybackOptions(video.url)
                )
            }
            if (options.isEmpty()) error = "No supported stream was discovered."
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            error = "Could not resolve video formats. Retry from search."
        }
    }

    LaunchedEffect(canvas, current) {
        val source = current ?: return@LaunchedEffect
        error = null
        status = null
        paused = false
        var attached = false
        for (attempt in 0 until 90) {
            if (canvas.isDisplayable && canvas.isShowing &&
                canvas.width > 0 && canvas.height > 0
            ) {
                attached = true
                canvas.requestGpuFrame()
                break
            }
            delay(100)
        }
        if (!attached) {
            error = "OpenGL window did not appear."
            onFatalError(error.orEmpty())
            return@LaunchedEffect
        }
        var ready = false
        for (attempt in 0 until 100) {
            if (bridge.ready) {
                ready = true
                break
            }
            if (bridge.lastFailure != null) break
            delay(100)
        }
        if (!ready) {
            error = bridge.lastFailure ?: "GPU renderer did not initialize."
            onFatalError(error.orEmpty())
            return@LaunchedEffect
        }
        error = withContext(Dispatchers.IO) { bridge.play(source) }
        if (error != null) {
            onFatalError(error.orEmpty())
            return@LaunchedEffect
        }
        while (isActive) {
            delay(1000)
            status = withContext(Dispatchers.IO) { bridge.poll() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text("GPU render API — experimental compositor test")
        Text(
            "Uses an application-owned OpenGL framebuffer, not mpv window embedding. " +
                "Final Compose texture/overlay integration and hardware decode need validation.",
            color = Color(0xFFADB4C4)
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF171B25)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("GPU lab is open in a separate Windows test window.")
                Text(
                    "Close it with ×, Escape, or Return to mpv. " +
                        "The main application remains available.",
                    color = Color(0xFFADB4C4)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onClose) {
                    Text("Close GPU lab / Return to mpv")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth().height(188.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Format " + (selected + 1) + "/" + options.size +
                ": " + (current?.label ?: "Finding streams…"))
            Text("OpenGL: " + (if (rendererReady) "initialized" else "starting") +
                " · render calls: " + (status?.renderFrames ?: 0L))
            if (error != null) {
                Text(error.orEmpty(), color = Color(0xFFFF9898))
            } else if (status != null) {
                Text("Video: " + status?.video + " · Audio: " + status?.audio +
                    " · Audio tracks: " + status?.audioTracks)
                Text("A/V offset: " + status?.aVSync + " · Media time: " +
                    status?.elapsed + "s")
                Text("External audio: " + status?.audioAttachment)
            } else {
                Text("Preparing libmpv GL framebuffer and media…")
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(enabled = selected < options.lastIndex,
                    onClick = { selected += 1 }) {
                    Text("Try next stream")
                }
                OutlinedButton(enabled = rendererReady, onClick = {
                    paused = !paused
                    scope.launch(Dispatchers.IO) { bridge.pause(paused) }
                }) { Text(if (paused) "Resume" else "Pause") }
                OutlinedButton(onClick = {
                    val value = listOf(
                        "Unofficial Windows GPU render prototype",
                        "Video: " + video.url,
                        "Format: " + (current?.label ?: "none"),
                        "Renderer ready: " + rendererReady,
                        "Native error: " + (error ?: bridge.lastFailure ?: "none"),
                        "Render calls: " + (status?.renderFrames ?: 0),
                        "Audio track: " + (status?.audio ?: "unknown"),
                        "Video track: " + (status?.video ?: "unknown"),
                        "Audio tracks found: " + (status?.audioTracks ?: 0),
                        "AV sync: " + (status?.aVSync ?: "unknown"),
                        "Audio attachment: " + (status?.audioAttachment ?: "unknown")
                    ).joinToString("\n")
                    copied = runCatching {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(
                            StringSelection(value), null
                        )
                    }.isSuccess
                }) { Text(if (copied) "Copied" else "Copy GPU report") }
            }
            Spacer(Modifier.width(2.dp))
        }
    }
}


/**
 * Dedicated non-modal GPU test window with predictable ownership and three
 * separate exit routes: native titlebar X, Escape, and visible Close button.
 *
 * The original AWTGLCanvas embedded in Compose could appear as an unmanaged
 * floating native surface and block navigation. A real JFrame owns the canvas
 * instead, while the normal Compose window stays accessible.
 */
private class GpuLabWindowHandle(
    private val canvas: MpvGpuCanvas,
    private val requestClose: () -> Unit
) {
    private val closing = AtomicBoolean(false)
    @Volatile private var window: JFrame? = null

    fun show() {
        SwingUtilities.invokeLater {
            if (closing.get()) return@invokeLater
            val frame = JFrame("GPU lab (alpha) — NewPipe Windows")
            frame.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
            frame.layout = BorderLayout()
            val controls = JPanel(BorderLayout())
            controls.add(JLabel(" OpenGL video laboratory — experimental "), BorderLayout.CENTER)
            val closeButton = JButton("Close GPU lab / Return to mpv")
            closeButton.addActionListener { requestCloseIfOpen() }
            controls.add(closeButton, BorderLayout.EAST)
            frame.add(controls, BorderLayout.NORTH)
            frame.add(canvas, BorderLayout.CENTER)
            frame.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(event: WindowEvent) {
                    requestCloseIfOpen()
                }
            })
            frame.rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("ESCAPE"), "gpu-lab-close"
            )
            frame.rootPane.actionMap.put("gpu-lab-close", object : AbstractAction() {
                override fun actionPerformed(event: ActionEvent) {
                    requestCloseIfOpen()
                }
            })
            frame.setSize(1000, 640)
            frame.setLocationRelativeTo(null)
            window = frame
            frame.isVisible = true
        }
    }

    private fun requestCloseIfOpen() {
        if (!closing.get()) requestClose()
    }

    fun closeAsync(bridge: MpvGlRenderBridge) {
        if (!closing.compareAndSet(false, true)) return
        Thread({
            runCatching { bridge.stop() }
            SwingUtilities.invokeLater {
                // JFrame.dispose() triggers the AWTGLCanvas's native
                // removeNotify / disposeGL path. Never invoke it on the
                // Compose render worker or while holding bridge locks.
                window?.dispose()
                window = null
            }
        }, "gpu-lab-stop-dispose").apply {
            isDaemon = true
            start()
        }
    }
}
