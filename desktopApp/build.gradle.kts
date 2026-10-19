/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.jetbrains.compose.multiplatform)
    alias(libs.plugins.about.libraries)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.jetbrains.coroutines.swing)
    implementation(libs.jetbrains.compose.preview)

    // Extractor-backed desktop search and thumbnail loading (no Android runtime).
    implementation(libs.newpipe.extractor)
    implementation(libs.squareup.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // Native LibVLC-backed embedded player. Test builds require VLC installed locally.
    implementation("uk.co.caprica:vlcj:4.11.0")
}

compose.desktop {
    application {
        mainClass = "net.newpipe.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // Temporary development branding; use a distinct name before public release.
            packageName = "NewPipe Windows Prototype"
            packageVersion = NEWPIPE_VERSION_NAME
            description = "Experimental Windows desktop build based on NewPipe"
            windows {
                menu = true
                shortcut = true
                menuGroup = "NewPipe Windows Prototype"
                dirChooser = true
            }
        }
    }
}

aboutLibraries {
    export {
        outputFile = file("../shared/src/jvmMain/resources/aboutlibraries.json")
        prettyPrint = true
        excludeFields.addAll("organization", "scm", "funding")
    }
}
