# M3.2: native libmpv render API integration test

**Status: EXPERIMENTAL. DO NOT REPLACE WORKING PLAYER YET.**
Branch: `windows/mpv-gpu-render-poc`. Draft PR #9.
Independent unofficial Windows application built on NewPipe / NewPipe Extractor. Preserve upstream and media library attribution and the GPL-3.0-or-later source obligations.

## What changes

- The proven **mpv (working)** option remains the default native player, with its current window-handle embedding unchanged.
- **GPU renderer (alpha)** uses `mpv_render_context_create()` and `mpv_render_context_render()` with an application-owned OpenGL framebuffer. The mpv core does not create or own the video child HWND.
- Compose Desktop still embeds an AWTGLCanvas for the GL context. This removes *mpv's video-window takeover*, not the remaining heavyweight AWT/Skia compositing boundary. It does not yet prove that Compose UI controls can overlay or clip the GL surface.
- On Windows, hardware video decoding is disabled for this first graphics proof; the media is decoded in software and presented via the GL API. Direct GPU decoding and D3D11/ANGLE interoperability are a later, measured optimization.
- Signed media URLs never appear in logs or copied diagnostic text; mpv source/audio can be paired by the extractor.
- New native libraries: LWJGLX AWT 0.2.5 (MIT) + LWJGL 3.4.2 Windows x64 (BSD-3-Clause). Keep their licenses in third-party notices for distribution and inspect actual native dependency bundle.

## Test gates

### CI checks

- [ ] Pure-JVM tests for `mpv_render_param` native alignment and null terminator.
- [ ] Existing HLS and audio/source-ranking tests pass.
- [ ] Pinned checksum SHA-256 of official libmpv binary matches.
- [ ] Windows loads the native binary and exports `mpv_render_context_create`, `mpv_render_context_render`, `mpv_render_context_free`, swap/update/callback APIs.
- [ ] Windows MSI package and artifact successfully produced; no dependency-resolution errors.
- [ ] No default-player change, no premature merging or public release.

CI **cannot verify GPU frame presentation, monitor refresh, audio output or native window teardown**. These remain Windows hardware test requirements.

### User manual test (only after all CI checks pass)

1. Install approved experimental MSI. Launch existing **mpv (working)** test on video `XaSjdZ2MUkU` as the reference, verify video/audio/sync.
2. Switch to **GPU renderer (alpha)** in the same app. Wait for OpenGL to initialize. Verify actual moving picture and audible sound, not merely `OpenGL: initialized` or advancing `render calls`.
3. Check 2+ minutes uninterrupted audio/video, seek if implemented in next revision, pause/resume, switch quality, resize window, and navigate search while video is active. Note black frames, dead windows, ghost audio, crash/restart, and stuttering.
4. Check a short ordinary VOD, a live stream, 1080p and 360p formats, different DPI/multimonitor if available. Record exact symptoms and paste the safe **Copy GPU report**, never signed googlevideo or private account data.
5. Roll back to the known-working mpv player if the alpha renderer fails. It must not become the default until the Windows test matrix passes.

## Explicit unresolved engineering risks

1. Thread confinement: OpenGL current context on frame/release calls, no JNA mpv media commands from frame callback, callback lifetime pinned through render context lifetime. Stress the AWT disposal path.
2. AWT heavyweight compositing: may still limit clipping, layering and PiP; need next texture bridge / offscreen FBO / Skia plan after observing this proof.
3. Live audio sync: confirm that externally attached HLS audio has corresponding presentation timestamps; `audio-add` accepted does not mean it is audible.
4. GPU decoder: direct D3D11/ANGLE OpenGL interop, device loss, Intel/AMD/NVIDIA/driver differences, HDR, 60fps and 4K remain separate work.
5. Render performance: event-driven redraw vs display-vsync, zero-size and dpi framebuffer, background window throttling and high-frequency callbacks.
6. Repeated navigation/source switching: core teardown order, no leaks/dangling threads/ghost audio; 10-video loop and 1-hour hold.
7. Deployment/security: GL native distribution, libraries loaded from pinned app resources, version compatibility with Compose/Skia's OpenGL, Code Signing and GPL/source notices.

## Product polish stays on the release checklist

Fullscreen, subtitles/caption selection, quality/audio controls, keyboard shortcuts, accessible focus, reliable navigation/search while playing, smooth resize/DPI, local downloads/subscriptions/history, optional account research and independent product branding remain planned. See `docs/testing/WINDOWS_V1_POLISH_ACCEPTANCE.md`.

**Do not call M3.2 done from a green build.** It requires actual Windows A/V + visual rendering evidence.
