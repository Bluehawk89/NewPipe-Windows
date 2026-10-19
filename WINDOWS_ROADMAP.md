# Unofficial Windows Port — Draft v1.0 Product Roadmap

> This is a planning document for the **independent, unofficial Windows port experiment** of [TeamNewPipe/NewPipe](https://github.com/TeamNewPipe/NewPipe). All original NewPipe credit and applicable GPL obligations remain with upstream contributors. This is **not** an official TeamNewPipe project or release. Product identity, trademark compliance, dependency notices, and distribution review are required before a public release.

## Supervisor decisions (October 2026)

- Aim for a **feature-complete, stable and polished Windows v1.0**, not just a bare video player.
- Include these target integrations: subscriptions, playlists, local watch history, downloads, channel browsing, and native playback.
- **Investigate optional YouTube account sign-in** as a v1.0 target, while preserving an anonymous, privacy-minded mode as the default.
- The precise feature list, priority, UI design, and release criteria will be **reviewed together later**. These are initial product goals, not fixed commitments or finished implementations.
- Do not claim feature parity or Google account synchronization until the individual features have passed technical and policy review.

## Workstreams and acceptance tests

1. **M0: Windows packaging** — verified MSI installs and launches. Completed during prototype testing.
2. **M1: Native desktop UI and search** — results, thumbnails and real NewPipe Extractor search observed on Windows. Verified during prototype testing.
3. **M2: Playback** — built-in LibVLC player is functional for many videos and live streams; continue fixing silent failures, format/quality selection, seeking, subtitles and fullscreen. Production deployment must assess safe native library packaging and dependency notices.
4. **M3: Local library** — persistent subscriptions, playlists, saved channels, history, import/export and storage migration across app upgrades. The local library must work **without** Google sign-in.
5. **M4: Downloads and desktop features** — video/audio selection, downloads, progress and cancellation, playlists where feasible, Windows directory choices, keyboard shortcuts, picture-in-picture and updates.
6. **M5: Optional online account integration (feasibility gated)** — assess Google OAuth and YouTube Data API for available signed-in features. Use system-browser authorization, request minimal scopes, protect/revoke tokens, and clearly distinguish locally stored history from YouTube server-side history. Do not ask for Google passwords or scrape login cookies. Some YouTube functions (including certain account history and Watch Later capabilities) may not be exposed through supported APIs; do not promise parity until verified. Account mode must never be required for ordinary anonymous use.
7. **M6: Stabilization/release** — Windows install/uninstall/update reliability; automated build/test coverage; playback compatibility matrix; crash/error reporting with user consent and no sensitive URLs/tokens; accessibility; independent app name, icon and trademark review; GPL source and third-party license notices.

## Future Downloads experience: yt-dlp-style capabilities, native Windows UI

**Supervisor goal (October 2026):** The finished application should feature a polished **native Windows Downloads workspace** offering many of the conveniences associated with yt-dlp-style download managers, **without committing to yt-dlp as the actual implementation**. We will compare backend options only after playback architecture and reliability are further along. This is a **feature goal, not implemented functionality**, and the exact scope/layout will be reviewed together later.

### Proposed UI placement

- Permanent **Downloads** item in the left sidebar, alongside Search, Subscriptions, Playlists and History.
- A prominent **Download** action on playable video detail/player screens, plus batch/playlist download entry points if technically feasible.
- Native download dialog for format/quality selection: video with audio, audio-only, resolution, container where available, subtitles/captions, and destination folder.
- A dedicated workspace with **Active / Queued / Completed / Failed** tabs, per-task progress, pause/resume/retry/cancel where supported, open-folder actions and clear failure messages.
- An advanced settings panel for download directory, filename template, duplicates, concurrency and network behavior; sensible beginner-friendly defaults.

### Capability goals / release checks

1. Correctly download **both video and its associated audio**, including YouTube adaptive formats that offer separate tracks; merge/remux with a maintained compatible tool as needed, verifying synchronized playback of the output.
2. Queue/batch work, reliable progress and cancellation, bounded retries, safe restart recovery, interrupt handling, expired link re-resolution, and appropriate duplicate/filename/disk-space safeguards.
3. Support subtitles, languages, audio-only and format-quality choices as sources permit. No promises about DRM-protected, unauthorized or service-restricted material.
4. Evaluate maintained download backends—including NewPipe Extractor APIs, yt-dlp as a potential component, and FFmpeg-compatible merging—against Windows packaging, maintainability, security, applicable service terms, licensing and source attribution before choosing.
5. Preserve anonymous use; optional YouTube account sign-in is an independent feasibility-gated feature and must **not** be a prerequisite for routine downloads.
6. Keep download processes off the UI thread; no terminal required for normal users, no untrusted executable downloads or unsafe credentials/logging. Build automated tests and real Windows end-to-end checks before describing as v1.0-quality.

**Not a current milestone:** prioritization, exact feature selection, and visual design remain open for a future supervisor review; current engineering focus remains native playback reliability and engine selection.

## Release definition: v1.0

A candidate v1.0 is suitable for regular use only after the supervisor approves its feature scope, checks representative real-life workflows, and verifies that critical functions work across restarts, upgrades and failed network requests. The v1.0 **goal** includes optional sign-in, but its exact availability and sync coverage are subject to API/security/legal feasibility review. No marketing or release promises should precede that review.

## Next review point

After playback reliability is sufficiently improved, hold a **feature priority session** with the supervisor. Decide must-have vs post-v1.0 features; particularly confirm what “YouTube login” should sync (subscriptions, account playlists, channel actions) versus what cannot be reliably supported.

Last updated: October 2026.
