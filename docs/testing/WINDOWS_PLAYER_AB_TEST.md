# Controlled Windows A/B playback test — experimental libmpv vs VLC

> Internal development tester instructions only. This is not a release candidate.
> Test branch: windows/m3-player-ab-comparison (draft PR #7).
> The mpv renderer uses a native embedded HWND only for initial decoding/sync comparison. A successful result would **not** prove that our eventual GPU render integration is finished.

## Before asking for a tester

- [ ] Windows CI parser/regression tests pass.
- [ ] Official libmpv GPL x64 ZIP SHA-256 matches the pinned digest.
- [ ] Windows runner can load libmpv-2.dll and resolve mpv_create.
- [ ] MSI packages successfully with appResourcesRootDir windows-x64.
- [ ] Installer artifact is uploaded and not expired.
- [ ] User is explicitly told this is a development A/B test requiring Windows confirmation (CI cannot see/hear video/audio).
- [ ] No accidental distribution or trademark claims; retain independent unofficial fork and attribution.

## Supervisor test (after successful build)

1. Close the existing app; install the newly confirmed MSI, replacing the previous prototype. Keep 64-bit VLC installed for the baseline.
2. Open the native Windows application, click **Open known playback test video**.
3. Leave **VLC baseline** selected. Note whether video and audio start, whether scrolling below the fixed video viewport changes either stream, and whether playback continues for at least **two minutes**.
4. Click **mpv experiment** for the **same video**. Wait up to 20 seconds for video/audio to start. Verify moving picture, audible audio and synchronization for at least **two minutes**. Press Pause/Resume, seek ±10 seconds and adjust volume.
5. Resize the application window, scroll only the lower controls (video should stay fixed), and minimize/restore. Note any black frames, freezes, dropped audio or picture-only playback.
6. Click **Copy test report** in mpv mode and paste it back to this project's development conversation if anything is wrong. The report contains no signed media URLs or authentication secrets.
7. If both engines work, try another ordinary music video and one livestream using search; repeat the same comparison. Do not infer stability from only one clip.
8. Report separately what happened in **VLC baseline** and **mpv experiment**. If mpv displays an error about missing DLL, report the exact user-facing text.

## Interpretation

- mpv has moving picture, audible sound and steady sync while VLC fails: **evidence of different media pipeline behavior on this machine**, not final render API readiness.
- Both exhibit scrolling/render issues: likely the shared AWT HWND composition path; prioritize mpv render API to Compose/Skia or native GPU surface.
- mpv lacks audio: inspect selected HLS audio group, aid property, external audio loading, streaming status; do not treat network HTTP 200/206 as a complete decode test.
- Both work only initially and freeze over time: collect snapshots at 30 seconds, 2 minutes, and after resize/seek; investigate buffering, clocks and GPU decoder paths.
- Unclear or intermittent: do not merge, release or switch engines until reproduced and diagnosed.

## Security and privacy

Never request that a tester paste signed googlevideo media URLs, browser cookies, Google passwords, app data directories, or personal information. Engine diagnostics use the public YouTube watch link and safe status/format fields only.
