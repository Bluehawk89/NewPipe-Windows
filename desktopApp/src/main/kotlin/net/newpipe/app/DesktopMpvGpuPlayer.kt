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
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Early integration test ONLY. GPU framebuffer controlled by our GL canvas,
 * but hosted via SwingPanel until texture compositing into Compose is proven.
 */
@Composable
internal fun DesktopMpvGpuPlayer(video: VideoResult, onFatalError: (String) -> Unit) {
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

    DisposableEffect(canvas) {
        onDispose {
            // Hide / change search immediately; don't keep ghost audio while
            // the GL canvas's disposeGL() finishes releasing native resources.
            Thread({
                bridge.stop()
            }, "mpv-gpu-stop").apply { isDaemon = true; start() }
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
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
            SwingPanel(factory = { canvas }, modifier = Modifier.fillMaxSize())
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
