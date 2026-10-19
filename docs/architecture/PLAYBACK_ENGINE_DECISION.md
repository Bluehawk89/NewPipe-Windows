# Windows native playback engine: architecture review (ADR-001)

**Status:** PROPOSED — evaluate independently before selecting or shipping a production engine.
**Target:** independent, unofficial NewPipe-based Windows desktop app with native user interface, high reliability, no Android emulator/subsystem, no WebView/YouTube iframe, and a self-contained installer.
**Date:** October 2026.

This is an engineering decision record, not an endorsement by Team NewPipe, NewPipe e.V., VLC/VideoLAN, or mpv. All upstream author and license obligations apply.

## 1. Test evidence, not speculation

User tested direct playback on 64-bit Windows with VLC installed:

- M2 played many normal and live YouTube videos with audio, including successful native in-window video on several.
- Regression target: https://www.youtube.com/watch?v=XaSjdZ2MUkU plays normally in a browser; M2 initially gave a silent black video.
- M2.2: `1/1 HLS / live stream`, VLC `STOPPED`, HTTP `200` playlist `application/vnd.apple.mpegurl`.
- M2.3: master `STOPPED`; eight rendition retries raised `IllegalStateException`; a media playlist listed 52 segments, playlist GET `206`, first child GET `200`.
- M2.4, after retaining the player between retries, showed **video** for that target but initially **without audible sound**. This suggests—but does not prove—a player-lifecycle bug caused the previous exceptions.
- M2.5 exposed distinct symptoms: user observed audio before picture; scrolling brought video into view but muted/interrupted audio; audio later resumed as video froze. This is **not** demonstrated to be caused only by scrolling. There may be a heavyweight AWT window composition problem, HLS separate audio attachment/sync problem, or both. Do not oversimplify or mark fixed.
- M2.6 altered heavyweight window placement; its Windows installer **compiled** but was not yet end-user playback tested. A successful MSI is not a valid test of audio/video or synchronization.

We currently lack captured exact VLC audio/video decoder track properties, clocks/PTS, A/V skew, rendering frame counts, hardware decoder stats, or logs (with credentials removed). HTTP success proves **only** network response success, not decode, demux, timestamp synchronization, or frame presentation.

### Known design weaknesses in the prototype to correct regardless of engine

1. Initial player put a native AWT video surface inside Compose `verticalScroll`: native HWND painting/clipping differs from Skia content. Keep native surface fixed and overlays outside it, or switch to engine-owned frame rendering.
2. Early retry code destroyed/recreated VLC and Swing components for each format: native resource disposal can race new playback. Exactly one player session, explicit asynchronous state machine and serialized commands.
3. Older players treated VLC `isPlaying` as proof of *video frames and audio*: it isn't. Detect video-decoded frames, audio stream presence, audio underruns, independent clock progress, latency/stalls, mute, and output devices.
4. Attempting HLS variant URLs may omit the separately advertised audio rendition. M2.5 attached audio as a media slave; we have not validated HLS timestamp alignment, track choices, or sustained A/V sync.
5. Reading player position/seekability/native state from Compose callbacks and recompositions risks thread contention and unnecessary invalidation. Polling and native callbacks belong on dedicated serialized workers; emit immutable UI state updates at a capped rate.
6. HLS stream ranking prefers 720p but doesn't reliably validate the codec pair, audio-group semantics, master audio tracks, signed-URL lifetime, encryption, or DRM status. Separate audio/video support must be designed, tested and prioritized.
7. HLS variant/segment network probes that get HTTP 200 or 206 are limited diagnostics; never expose actual signed URL values, authentication headers, cookies, user filesystem paths or request bodies.
8. Retry control has no bounded backoff/expiry/re-resolution policy, no user-friendly error taxonomy and no test fixture coverage for audio-only/video-only variations. Avoid infinite loops and stale media URLs.
9. Old `Stop` / `Pause` controls maintain separate Compose booleans that can disagree with the native engine. UI must derive state from the engine and handle buffering/end-of-media.
10. Installer depends on separately installed VLC 3.x; production candidate must bundle or otherwise manage exact 64-bit native runtime dependencies, notices, codec licenses and future updates.

## 2. Playback engine options

| Engine / rendering approach | Upsides | Downsides and risks | Decision |
|---|---|---|---|
| **libmpv**, with its **render API** (OpenGL or D3D11) | Designed to embed in another app; mature FFmpeg-based stream demux/decode, external audio tracks, HLS/DASH, precise A/V control and detailed properties; video frames can belong to app render lifecycle rather than a heavyweight scrollable HWND | Higher native integration effort: render context, thread rules, native DLL and codecs, hardware/swapchain compatibility; must test Kotlin/Compose/Skia interop and package the exact binaries legally | **Primary experimental candidate** |
| **libmpv with native HWND / `wid`** | Faster independent A/B proof of decoder, demux, audio and subtitles; official Java/JNA example exists | Same native window, clipping and focus family of problems as embedded VLC; **not acceptable by itself as proof of production UI polish** | Useful first diagnostic stage, not automatically the shipping renderer |
| **LibVLC 3 + vlcj 4** (current) | Already proven to play many videos and live streams; JVM bindings and robust media capabilities, limited rewrite | Current integration's heavyweight AWT surface; apparent HLS/audio-slave sync trouble; requires separately installed VLC; native calls/lifecycle brittle | Keep working fallback and baseline; do **not** commit to this as v1.0 engine |
| **LibVLC callback/direct renderer** | VLC audio/video pipeline can be retained without AWT video HWND; direct-render interfaces exist | CPU frame-copy/performance, Skia frame bridge, memory pinning/GC and texture handling, potentially high-resolution latency; VLC 4 APIs differ | Secondary proof of concept if libmpv render bridge is impractical |
| **GStreamer 1.22+ / playbin3 + hlsdemux2** | Genuine adaptive demux, alternate audio/video tracks, buffering and stream selection; industrial pipeline APIs | Large complex native runtime/plugins, custom render bridge, packaging/security/license audits; narrower JVM/Compose integration experience | Backup if libmpv fails validation |
| **Windows Media Foundation / WinUI playback** | Windows-native decoder, output and OS features; Microsoft supports adaptive HLS/DASH via WinUI APIs | WinRT/COM/WinUI embedding in Compose Desktop, varied codec coverage across Windows installs and DRM/format differences; complicates single Kotlin UI and extracted YouTube streams | Not recommended as primary; optional Windows-native specific features |
| **FFmpeg directly + our own clocks, decoders and audio/video renderer** | Ultimate control | Building a stable player from primitives would demand demux, timestamp sync, adaptive streaming, GPU, subtitles, audio APIs and extensive security review | Reject for v1.0; leverage an existing player engine |
| **Android ExoPlayer, Electron, WebView YouTube iframe** | Different deployment tradeoffs | Either needs Android environment, web-wrapper design, or YouTube embed; violates native Windows goal and/or request | Out of scope |

Sources:
- https://mpv.io/manual/stable/ (separate audio-files, HLS/DASH options, Windows wid)
- https://github.com/mpv-player/mpv/blob/master/include/mpv/render.h (render APIs and strict threading)
- https://github.com/mpv-player/mpv-examples/tree/master/libmpv (render/Java examples)
- https://github.com/mpv-player/mpv/blob/master/Copyright (GPL/LGPL variants)
- https://capricasoftware.co.uk/tutorials/vlcj/4/overlay (HWND/AWT Canvas embedding limitations)
- https://capricasoftware.co.uk/tutorials/vlcj/4/direct-rendering (callback option)
- https://gstreamer.freedesktop.org/documentation/additional/design/adaptive-demuxer.html
- https://learn.microsoft.com/en-us/windows/apps/develop/media-playback/adaptive-streaming

## 3. Proposed architecture / reliability contract

Keep the **NewPipe Extractor** as a separate discovery/stream-resolution layer and isolate the native engine via `PlaybackEngine`. A player implementation must offer:

- `open(PlayableSource)`, `play()`, `pause()`, `stop()`, `seekTo()`, `setVolume()`, `setMuted()`, `selectAudioTrack()`, `selectSubtitleTrack()`, `setQuality()`, `dispose()`.
- Events/state from **one serialized engine command executor**, never from UI composition; deterministic transitions: Idle → Resolving → Preparing → Buffering → Playing ↔ Paused → Ended/Error → Disposed. Commands must be cancellation-safe.
- An immutable UI-facing snapshot: media position/duration, playback state, selected codec/container, resolved audio/video tracks, dropped video frames (if supported), decoded frame progress, buffering count, current A/V timebase/sync telemetry (when possible), renderer readiness, audio device/volume/mute, error category, media-retry attempt, network failure summary.
- No URL/logging of YouTube signed query parameters. Debug switch off by default, no automatic upload or telemetry without user opt-in.
- A stable `VideoViewport` whose native/GPU renderer is **not recreated** by search controls, scrolling, volume changes, or status refresh.
- Bounded, conservative retries: expired signed links trigger fresh extractor resolution, not endless retries; prefer verified streams with both video and audio, handle manifests/renditions per spec, keep watch position if retrying, no aggressive quality jumps.
- Distinct engine and renderer: engine status `PLAYING` does **not** mean screen frames are reaching the user. Observe decoder output and real frame presentation where available.

## 4. Tests required **before** next user MSI request

### CI / automatic (can validate without a human Windows screen)

- Unit-test HLS master parsing: quoted CODECS with commas, audio-group linkage, URI resolution, relative variants, missing audio group, duplicate resolutions, audio-only rendition, and malicious external scheme.
- Unit-test deterministic player state machine and duplicate/rapid navigation; native stop/retry disposal ordering, cancellation and bounded network retries.
- Test source ranking for muxed MP4/HLS master/HLS external audio/video-only DASH and expired URLs.
- Synthetic public-domain/local media fixtures: short MP4/WebM/AV1 where codec is available; HLS with **muxed** audio/video; HLS video + **separate audio**; HLS live/event; manifest with missing/dead segment; invalid 403 media response. Do not depend on a constantly changing YouTube video in CI.
- Windows job runs unit tests, packaging, and static rules: no signed media URL in failure text; no native player `release` inside retry loop; no heavyweight player within a scrollable Compose container.
- A native smoke test (on a Windows runner) may validate silent headless demux/decode and frame/audio counts. Headless CI **cannot** certify actual GPU/desktop compositing or speaker audio.

### Windows manual gate (only once the headless checks pass)

Create two separate prototype builds, same extractor and UI/playlist fixture with engine choice hidden under a *development settings* switch:
A. corrected VLC implementation, B. libmpv experimental renderer.

Run the same **minimum matrix** on the same Windows PC: 
- trouble video `XaSjdZ2MUkU`;
- ordinary YouTube short and hour-long VOD;
- 1080p video+separate audio and 360/720p muxed variants;
- an active YouTube Live; seeking, pausing, quality changes, source exhaustion;
- resize/minimize/restore/fullscreen, display DPI change, scroll controls *without* moving video viewport;
- Windows audio output switching, mute/unmute, volume and audio track selection;
- consecutive 10-video queue, rapid Back/Open/Stop and restart;
- suspend/resume, network unplug/reconnect and bandwidth throttling.

For every case measure: **both moving image and audible sound**, A/V alignment sustained (target <150 ms when measurable), no freezes or blank renderer, no leaked processes/handles, no signed URL leaks, reproducible errors. Measure startup latency and CPU/GPU/memory under similar settings; do not claim performance without data. Prefer an engine only if it is reliable and maintainable, not merely faster for one clip. No public distribution until third-party licensing and source/notice compliance are reviewed.

## 5. Packaging / security / license

- This port is GPL-3.0-or-later; preserve Team NewPipe and NewPipe e.V. attribution/trademark policy and use independently distinct name/icon before public distribution.
- mpv is **GPLv2-or-later by default**, with a conditional LGPLv2.1-or-later mode for certain builds; the exact shipped libmpv binary and its FFmpeg dependencies determine notices and redistribution obligations. Prefer a reproducible pinned build and inspect the full dependency manifest, source licenses and supported codecs before bundling DLLs.
- VLC/LibVLC/vlcj and GStreamer bring their own license trees; neither may be repackaged without acknowledgments and corresponding compliance analysis. GPU codec support can differ between Windows installations.
- Do not fetch and execute unverified DLLs at runtime. Pin sha256 checksums, ship architecture-matched signed/reproducible libraries, audit dependencies, publish GPL sources and attribution.
- Automatic updates require independent application branding, integrity checking, rollback and user consent.

## 6. Architecture decision and milestones

**Decision today:** Do **not** declare VLC bug fixed or choose the final renderer. **Evaluate libmpv render API as the primary replacement candidate**, with LibVLC as a comparable baseline and GStreamer as contingency. The current code/installer remains a private development prototype with no forced migration.

1. [ ] Add regression fixtures, basic playback-state & HLS parser tests.
2. [ ] Establish error/telemetry schema and stop UI-native thread races in existing VLC implementation.
3. [ ] Implement libmpv **separate experimental** adapter in native Windows desktop app. First HWND test for media pipeline, then render API proof with a stable OpenGL/D3D11 surface. Do not release HWND-only result as polished UI.
4. [ ] Confirm exact Windows-native dependency packaging, security, licenses, and architecture. Ensure no external VLC/mpv installs required for eventual end users.
5. [ ] Complete side-by-side matrix, retaining original search/home/library code; choose engine based on evidence.
6. [ ] Only then ask supervisor to test a single vetted MSI. Retain rollback path.

**No A/V sync, decode, performance, or rendering results have been claimed for libmpv yet.**
