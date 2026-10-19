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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.awt.Desktop
import java.net.URI

/**
 * Development playback prototype using VLC's native Windows engine.
 *
 * The desktop installer does not yet bundle LibVLC. VLC 3.x 64-bit must be
 * installed on the test machine (default location) for video rendering.
 * The video surface is embedded inside the Compose Desktop window itself.
 */
@Composable
internal fun DesktopVideoPlayer(video: VideoResult, onBack: () -> Unit) {
    var streamUrl by remember(video.url) { mutableStateOf<String?>(null) }
    var streamError by remember(video.url) { mutableStateOf<String?>(null) }
    var paused by remember(video.url) { mutableStateOf(false) }
    var position by remember(video.url) { mutableFloatStateOf(0f) }

    LaunchedEffect(video.url) {
        streamUrl = null
        streamError = null
        try {
            streamUrl = withContext(Dispatchers.IO) {
                DesktopVideoSearch.resolvePlaybackUrl(video.url)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            streamError = e.message ?: "NewPipe could not resolve a playable stream."
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("← Back to results") }
            Spacer(Modifier.width(16.dp))
            Text("Now playing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        Text(video.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(video.channel, color = Color(0xFFADB4C4))
        Spacer(Modifier.height(18.dp))

        when {
            streamError != null -> {
                Text("Unable to load this video in the prototype", color = Color(0xFFFF8A8A))
                Spacer(Modifier.height(8.dp))
                Text(streamError.orEmpty())
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { openBrowser(video.url) }) {
                    Text("Open in browser instead")
                }
            }
            streamUrl == null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(14.dp))
                    Text("Getting the stream from NewPipe Extractor…")
                }
            }
            else -> {
                // Components are keyed by the resolved URL to avoid leaking stale players.
                val playingUrl = streamUrl!!
                val player = remember(playingUrl) {
                    runCatching { EmbeddedMediaPlayerComponent() }.getOrNull()
                }

                if (player == null) {
                    Text("Native VLC engine not found", color = Color(0xFFFF8A8A))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This development build requires the 64-bit VLC desktop player installed " +
                            "on Windows. Install VLC 3.x, close this app, and try again."
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { openBrowser(video.url) }) {
                        Text("Open in browser instead")
                    }
                } else {
                    DisposableEffect(player) {
                        onDispose {
                            runCatching { player.mediaPlayer().controls().stop() }
                            runCatching { player.release() }
                        }
                    }
                    LaunchedEffect(player, playingUrl) {
                        withContext(Dispatchers.IO) {
                            player.mediaPlayer().media().play(playingUrl)
                        }
                    }
                    LaunchedEffect(player) {
                        while (isActive) {
                            delay(500)
                            runCatching {
                                player.mediaPlayer().controls().position()
                            }.onSuccess {
                                if (it.isFinite() && it >= 0f) position = it
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth().height(420.dp)
                            .background(Color.Black)
                    ) {
                        SwingPanel(
                            factory = { player },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Slider(
                        value = position.coerceIn(0f, 1f),
                        onValueChange = { position = it },
                        onValueChangeFinished = {
                            player.mediaPlayer().controls().setPosition(position)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = {
                            if (paused) {
                                player.mediaPlayer().controls().play()
                            } else {
                                player.mediaPlayer().controls().pause()
                            }
                            paused = !paused
                        }) { Text(if (paused) "▶ Resume" else "⏸ Pause") }
                        OutlinedButton(onClick = {
                            player.mediaPlayer().controls().stop()
                            paused = true
                        }) { Text("Stop") }
                        OutlinedButton(onClick = { openBrowser(video.url) }) {
                            Text("Open in browser ↗")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Playback prototype • VLC 3.x is required for this test build. " +
                            "Quality and availability depend on the video stream.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFADB4C4)
                    )
                }
            }
        }
    }
}

private fun openBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
    }
}
