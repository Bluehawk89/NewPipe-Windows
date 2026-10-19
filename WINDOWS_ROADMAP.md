# Unofficial Windows Port — Draft v1.0 Product Roadmap

> This is a planning document for the **independent, unofficial Windows port experiment** of [TeamNewPipe/NewPipe](https://github.com/TeamNewPipe/NewPipe). All original NewPipe credit and applicable GPL obligations remain with upstream contributors. This is **not** an official TeamNewPipe project or release. Product identity, trademark compliance, dependency notices, and distribution review are required before a public release.

## Supervisor decisions (October 2026)

- Aim for a **feature-complete, stable and polished Windows v1.0**, not just a bare video player.
- Include these target integrations: subscriptions, playlists, local watch history, downloads, channel browsing, and native playback.
- **Investigate optional YouTube account sign-in** as a v1.0 target, while preserving an anonymous, privacy-minded mode as the default.
- **Plan for yt-dlp-style advanced download and media-management capabilities** in both the final UI and underlying Windows application. This means comparable user-facing functions where practical, **not** a decision to embed, depend on, or copy yt-dlp. We will review exact scope and technology later.
- The precise feature list, priority, UI design, and release criteria will be **reviewed together later**. These are initial product goals, not fixed commitments or finished implementations.
- Do not claim feature parity or Google account synchronization until the individual features have passed technical and policy review.

## Workstreams and acceptance tests

1. **M0: Windows packaging** — verified MSI installs and launches. Completed during prototype testing.
2. **M1: Native desktop UI and search** — results, thumbnails and real NewPipe Extractor search observed on Windows. Verified during prototype testing.
3. **M2: Playback** — built-in LibVLC player is functional for many videos and live streams; continue fixing silent failures, format/quality selection, seeking, subtitles and fullscreen. Production deployment must assess safe native library packaging and dependency notices.
4. **M3: Local library** — persistent subscriptions, playlists, saved channels, history, import/export and storage migration across app upgrades. The local library must work **without** Google sign-in.
5. **M4: Downloads and desktop features** — video/audio selection, downloads, progress and cancellation, playlists where feasible, Windows directory choices, keyboard shortcuts, picture-in-picture and updates. Include a design review for a polished, yt-dlp-style advanced downloads experience, with implementation and availability verified before claiming feature parity.
6. **M5: Optional online account integration (feasibility gated)** — assess Google OAuth and YouTube Data API for available signed-in features. Use system-browser authorization, request minimal scopes, protect/revoke tokens, and clearly distinguish locally stored history from YouTube server-side history. Do not ask for Google passwords or scrape login cookies. Some YouTube functions (including certain account history and Watch Later capabilities) may not be exposed through supported APIs; do not promise parity until verified. Account mode must never be required for ordinary anonymous use.
7. **M6: Stabilization/release** — Windows install/uninstall/update reliability; automated build/test coverage; playback compatibility matrix; crash/error reporting with user consent and no sensitive URLs/tokens; accessibility; independent app name, icon and trademark review; GPL source and third-party license notices.

## Advanced downloads and media-management goal (future UI and engine)

Supervisor request: plan for **yt-dlp-style functions in the finished Windows UI and application**, without requiring actual yt-dlp integration. This is an **experience/capability goal**, not a predetermined library choice or guaranteed complete compatibility. Keep the implementation native to the desktop app and compatible with its existing NewPipe Extractor workflow.

Potential features to review before finalizing scope:

- **Download video, audio or both**, with clear format/container and quality/bitrate options where sources permit them (including separate audio/video muxing when required).
- **Download queue** with per-item progress, cancel, pause/resume *where technically supported*, retry, friendly errors, and status after app restart.
- **Playlist and batch selection**, with filters and safeguards so a click cannot unintentionally trigger huge downloads.
- **Subtitles and captions**, chapters, thumbnails, metadata and filenames when available, with clear user choices for embedding/saving.
- **Destination folders**, conflict handling, file naming templates, disk-space checks, and easy access to files from the Windows Downloads view.
- **Advanced options that remain optional and understandable**: retain uncluttered defaults for everyday use; expose power-user options in an expanded panel or Settings rather than presenting a command-line interface.
- **Download-and-continue-watching** workflow, native Windows notifications and predictable installation/updating of any required media-processing tools where legally distributable.

### Proposed experience in the final UI

- A primary **Download** action near the native video player and result rows.
- A compact modal/side panel for **Video / Audio**, quality, format and destination, with an **Advanced** section when needed.
- A first-class **Downloads** navigation destination featuring a clear queue (queued/downloading/completed/failed), progress, retry/cancel and open-folder actions.
- Shared source-resolution, track-selection and audio/video-muxing logic with the playback engine where feasible, without coupling app responsiveness to network or transcoding work.
- Privacy-respecting local history, safe filenames, and clear failures when a source, format or permission is unavailable.

### Feasibility and quality gate

At the feature-planning review, compare: (a) native implementation using NewPipe Extractor + a vetted downloader/media muxer, (b) optional or bundled yt-dlp-style tool integration where licensing/security/distribution permit, and (c) another maintainable approach. Evaluate site compatibility, rate limiting, signed-link expiry, separate track alignment, download resumption, file integrity, update/security model, dependency licenses, and permitted uses. Do not promise downloading every site or protected/DRM content; respect applicable law, platform terms, and access restrictions. Do not collect or store Google passwords. Select the technology only after hands-on tests and a license/security review.

**Not implemented yet.** This section records the product direction without changing the current focus on a reliable, polished native playback engine. Exact v1.0 versus post-v1.0 priorities remain for later supervisor review.

## Release definition: v1.0

A candidate v1.0 is suitable for regular use only after the supervisor approves its feature scope, checks representative real-life workflows, and verifies that critical functions work across restarts, upgrades and failed network requests. The v1.0 **goal** includes optional sign-in, but its exact availability and sync coverage are subject to API/security/legal feasibility review. No marketing or release promises should precede that review.

## Next review point

After playback reliability is sufficiently improved, hold a **feature priority session** with the supervisor. Decide must-have vs post-v1.0 features; particularly confirm what “YouTube login” should sync (subscriptions, account playlists, channel actions) versus what cannot be reliably supported, and refine the advanced download feature list, preferred underlying tools, and UI mockups.

Last updated: October 2026.
