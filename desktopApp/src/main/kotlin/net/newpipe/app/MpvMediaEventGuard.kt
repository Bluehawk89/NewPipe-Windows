/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

/**
 * mpv event IDs from mpv/client.h: 8 = MPV_EVENT_FILE_LOADED.
 *
 * Native event notifications are asynchronous. After a new loadfile command
 * is queued, the previous FILE_LOADED can still be in the queue. Do not
 * attach a new source's audio rendition to the wrong loaded video.
 *
 * The signed URL is compared only in memory and NEVER logged.
 */
internal object MpvMediaEventGuard {
    fun matchesLoadedMedia(
        eventId: Int,
        observedMediaPath: String?,
        requestedMediaPath: String?
    ): Boolean = eventId == 8 &&
        !requestedMediaPath.isNullOrEmpty() &&
        observedMediaPath == requestedMediaPath
}
