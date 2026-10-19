/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/**
 * A proper Compose Desktop window. The existing shared About screen is preserved.
 */
fun main(args: Array<String>) {
    // GitHub Actions invokes this on the ACTUAL jpackage app-image runtime,
    // not just the full development JDK. Exit without starting Compose/AWT.
    if (args.contains("--gpu-preflight")) {
        val failure = GpuRuntimePreflight.check()
        if (failure != null) {
            System.err.println("GPU preflight failed: " + failure)
            kotlin.system.exitProcess(24)
        }
        println("GPU preflight passed")
        return
    }
    application {
        var showingAbout by remember { mutableStateOf(false) }
        Window(
            onCloseRequest = ::exitApplication,
            title = "NewPipe Windows Prototype",
            state = rememberWindowState(width = 1160.dp, height = 760.dp)
        ) {
            if (showingAbout) {
                App(onCloseRequest = { showingAbout = false })
            } else {
                DesktopHome(onAbout = { showingAbout = true })
            }
        }
    }
}
