# Windows v1.0 — quality, polish, and compatibility acceptance gates

**Status:** Draft requirements, not implemented. Supervisor explicitly requested that all previously discussed polish features remain in scope during the mpv reliability audit. Last reviewed October 2026.

This project is an independent, unofficial Windows derivative building on NewPipe's GPL-3.0-or-later code and NewPipe Extractor. Preserve upstream attribution and license terms; choose independent public identity before release.

## 0. Non-negotiable engineering standards

- **No regressions in existing functionality**: search, results, thumbnails, native playback, video/audio sync, local anonymous mode and Windows MSI survive every major branch integration.
- **Functional must mean more than a successful build**: each test confirms audible audio, moving video, no severe drift, no crashes or stuck surfaces, clean closure, recovery after network faults and no media URL leaks.
- **One stable media session**: switching source, quality, subtitles, volume, fullscreen or navigation must never leak a native mpv core, leave audio playing from an abandoned video, or display frames from a previous stream.
- **No UI-blocking native operations**: start/stop/seek, extraction, stream probing, format switching and resource teardown run on controlled workers. Canceled user actions cannot mutate a newer screen or source.
- **One long-term player architecture**: preserve the verified libmpv decoding pipeline, but move beyond HWND-over-SwingPanel to a GPU/render-API path proven to integrate cleanly with Compose Desktop/Skia (DPI, clipping, overlays, keyboard focus, multiple monitors).
- **Native, privacy-minded product**: no emulator/Android subsystem and no embedded YouTube browser. Anonymous searching and local features remain available without any Google account.

## 1. P0 — blocking v1.0 release

| Requirement | Automated gate | Real Windows acceptance |
|---|---|---|
| Video and audio decode + sync | Validated muxed, split audio/video, HLS live/event, DASH, old/exhausted links, parsed audio groups, audio track fallback | 10 representative videos + live tested continuously; both audible sound and moving frames; sync within practical observable tolerance |
| Stream selection | Deterministic tested ranking and bounded failover; explicit source validity, container/codec compatibility | Known regression `XaSjdZ2MUkU` starts in usable format without manual format cycling; no endless retries |
| Player/session lifetime | State machine tests, stale event rejection, race tests for source switches and route changes | 10 rapid source changes; 10 navigate-search-back cycles; no ghost audio, black screen or handle leaks |
| Native rendering | GPU frame-presentation proof, renderer lifecycle unit/integration smoke tests | Resize, minimize/restore, fullscreen, overlay controls, different Windows DPI/monitor profiles, scrollable adjacent UI with no video freeze |
| Transport recovery | 403/404, 429, transient 5xx, expired signed URL and timeout fixtures | Disconnect/reconnect network; retry/re-resolve only when justified; clear errors, no invalid infinite loop |
| Security | Signed URLs/cookies/headers are excluded from diagnostic text and logs; HTTPS/safe redirects, bounded playlist sizes; native DLL checksum gate | Diagnostics share no credentials; no unexplained runtime executable downloads or extra apps |
| Installer reliability | Windows tests, pinned libmpv + JNA, architecture/dependency/license manifest, MSI build and artifact assertion | Fresh install, uninstall, upgrade, rollback, launch without separately installing mpv; no stale DLLs or ghost processes |

## 2. P1 — polished daily-use video player

- **Controls**: responsive Play/Pause, Stop, skip ±10s, scrubbing with duration and live/DVR distinctions, buffering spinner, elapsed/remaining time, volume and mute, speed selector, clear error/retry buttons; keyboard shortcuts.
- **Display**: fullscreen using the true video renderer, restore window layout, mini-player/PiP only after a deliberate product design, letterboxing/aspect-ratio preservation, safe handling of tall/portrait video, multi-monitor scaling and display changes.
- **Audio**: alternate audio languages/tracks, audio-description recognition when metadata is available, correct default output device, mute/volume persistence by user preference, Bluetooth and wired device switches.
- **Subtitles**: caption selection, supported languages/auto-generated labels, enable/disable, positioning and font-size/contrast customization where engine capabilities allow.
- **Quality**: show actual available resolutions/frame rates/codecs, auto/manual switching without duplicate audio or long freezes, defaults appropriate to the user's display and connection, do not promise unavailable formats.
- **Navigation**: search while playing transitions to results and closes media *for the current version*; don't cover search results with an active player. Later, a mini-player can offer continued playback intentionally with clear focus and stop rules.
- **Search**: cancellation-safe, debounced/throttled enough for responsiveness, accessible suggestions/navigation, visible empty/error/loading states; no stale search overwrite, retry after timeout, correct Back behavior.
- **Polish**: accessible keyboard focus, screen-reader labels, logical tab order, readable contrast, high-DPI scaling, resizable layout, dark/light theme review, consistent feedback for asynchronous actions, graceful offline states and selectable diagnostics.

## 3. P2 — product ambitions to review with supervisor

- Local persistent subscriptions, playlists, channels, history, import/export with safe migration and recovery; always functional offline and without Google login.
- yt-dlp-*style* desktop download workflow: video/audio quality and format, queue/progress/pause-resume where possible, playlist/batch, subtitles/metadata/chapters, conflict handling, location and naming, power-user options behind a simple UI. **No commitment to embedding yt-dlp itself.**
- Optional consent-based account/OAuth features: use system browser, least-privilege scopes, secure token vault, explicit account-vs-local distinction; feasibility depends on YouTube API constraints.
- Windows integration: file associations where justified, toast notifications, keyboard/media keys, taskbar control, configurable update policy, automatic crash recovery without collecting sensitive data.
- Independent application identity, original artwork, attribution/about/licenses and GPL source publication before public release.

## 4. Mandatory testing layers

1. **Pure JVM unit tests:** playlist parser, media/quality ranking, state transitions, retries, cancellation, video-vs-audio track preference, diagnostic sanitization, and limits for hostile manifests.
2. **Native Windows CI smoke tests:** actual DLL load/export lookup, fixed short public-domain test fixture where practical, audio/video stream availability and frame/decode counts where instrumented, MSI packaging, source/license manifest.
3. **Interactive Windows matrix:** standard VOD, long VOD, 4K/1080p/360p, 60fps, AV1/H.264/VP9 where available, music, live/DVR, captions, alternate audio, seek/pause/resume, rapid navigation/search, network dropout, suspend/resume, multiple displays, DPI and accessibility.
4. **Performance and reliability:** 1-hour uninterrupted playback, repeated 10-video cycles, startup time, memory/handle leak trends, decoding CPU/GPU use, renderer disposal, no dangling audio, deterministic feedback on failures.
5. **Release sign-off:** supervisor personally verifies the essential workflows. Pass of CI alone is not sign-off. Features without verified feasibility are labeled explicitly and not represented as complete.

## 5. Conflict and upgrade gates

- Retain feature branches / draft PRs; no direct merge of an experimental renderer into `dev` solely due to green CI.
- Check every PR for **cross-feature conflicts**: search→video and video→search transitions, download jobs competing for media URLs, subtitles changing playback tracks, account auth changing extractor behavior, full-screen/mini-player affecting window native lifetimes.
- Keep exact names/versions of native bundled codecs, Gradle/JDK plugins and upstream extractor pinned and compatible. Confirm no accidental Android-only dependency reaches the Windows runtime.
- Audit licenses for NewPipe, libmpv and all bundled codecs, JNA, FFmpeg and optional downloads infrastructure. Preserve original copyright files and independent branding.
- At each validated milestone, update release notes and regression matrix; a failed case stays tracked until a Windows test confirms its resolution.

**Current actual status:** mpv alternate formats played with synced sound and picture on the user's Windows PC; HLS master failed; VLC remained unreliable. Actual final GPU renderer, fullscreen, subtitles, PiP, persistence, downloader, account sign-in, long-run A/V durability and public release are not yet completed.
