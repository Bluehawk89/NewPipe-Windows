# M3.3 GPU failure regression and recovery gate

**Status: source changes submitted, packaged Windows preflight / user retest pending.**
Independent unofficial NewPipe-derived Windows app (GPL-3.0-or-later). The working mpv playback path remains default.

## Windows user evidence

The supervisor installed the first GPU-rendering alpha build and captured:

- Popup: **"Failed to initialize a backend for MemoryUtil"**.
- Selecting the experimental GPU renderer crashed/destabilized the desktop app.
- Switching between mpv and VLC became unusable after that fault.
- Prior control test confirmed libmpv playback with **synchronized picture and sound** for all tested alternate formats on video `XaSjdZ2MUkU`; HLS master failed. VLC still had its longstanding problems.

**Interpretation:** the error comes from LWJGL memory initialization **before** the experimental renderer proves video display. It is not evidence of an extractor/HLS decoding problem. Root cause hypothesis: Compose Desktop built a reduced jlink Java runtime without `jdk.unsupported`, while LWJGL 3.4.2 under JDK 21 uses `sun.misc.Unsafe`. The module problem is supported by LWJGL/Compose documentation but needs a concrete Windows packaged preflight to verify.

## Implementation decisions

1. Add `jdk.unsupported` and `jdk.unsupported.desktop` as explicit jpackage runtime modules.
2. Add a `--gpu-preflight` application entry point that runs without creating any UI. Explicitly initialize LWJGL `MemoryUtil`, allocate and free a small buffer, return nonzero status on failure without printing sensitive machine paths.
3. Add regression tests and **run that preflight on the Windows app-image runtime**, not merely the full CI JDK; hold back the MSI artifact on any failure.
4. Remove VLC from the normal player selector. Its earlier implementation remains in Git history for developers, but regular playback goes straight to user-tested mpv.
5. Gate the GPU experiment through preflight. Catch JVM-side class initialization/linkage errors and report a clean fallback to normal mpv. Handle canvas errors on the AWT thread, without crashing Compose. Do not confuse this with process isolation: graphics-driver access violations can still crash the entire JVM.
6. Keep **GPU lab (alpha)** opt-in. Final mpv OpenGL integration, Compose/Skia texture sharing, overlay controls, fullscreen, HDR/4K and robust GPU device-loss handling are **not** complete.

## Test gate before asking user to reinstall

- [ ] Build completes on **current branch head**, not an earlier intermediate commit.
- [ ] HLS/source-selection/media-guard/GL native ABI unit tests pass.
- [ ] Windows jpackage image includes `jdk.unsupported`.
- [ ] Package image launched with `--gpu-preflight` exits 0 within 30 seconds.
- [ ] Pinned official libmpv SHA-256 and native OpenGL symbols validated.
- [ ] MSI package exists, is non-expired and can be downloaded.
- [ ] Real user tests mpv by default, search navigation, GPU lab startup and fallback (plus moving video/audio when GPU lab succeeds). Do not claim success without that.

## Longer-term architectural requirement

A GPU renderer that can crash the JVM through a native access violation requires either very strong native integration and device-specific testing **or** a separate helper process with explicit IPC and watchdog recovery to ensure the main desktop app remains alive. The latter is worth evaluating before public v1.0 and should not be conflated with the current Java exception handling.

**Polish scope remains unchanged:** fullscreen, clean UI, subtitles, quality/audio controls, PiP/miniplayer if designed, accessibility, robust install/upgrades, library/subscriptions/history, advanced yt-dlp-style downloads, legal/branding reviews, and later optional account feasibility.
