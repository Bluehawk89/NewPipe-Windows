# Attribution, licensing, and release safeguards

This repository is an **independent, unofficial Windows desktop port experiment** built from [TeamNewPipe/NewPipe](https://github.com/TeamNewPipe/NewPipe). It is **not authored, maintained, endorsed, or released by TeamNewPipe or NewPipe e. V.** Its contributors are responsible for their modifications and distributions.

## Credit to the original projects

- **NewPipe application and shared desktop framework:** developed by the original NewPipe contributors. Project: https://github.com/TeamNewPipe/NewPipe
- **NewPipe Extractor:** developed by TeamNewPipe and its contributors. Project: https://github.com/TeamNewPipe/NewPipeExtractor
- **vlcj desktop media-player integration:** created by Caprica Software Limited and contributors. Project: https://github.com/caprica/vlcj (GPL-3.0-or-later). This independent fork uses it for its experimental Windows playback UI; libVLC is provided by the VideoLAN project and has its own applicable licensing and notices.
- **Other dependencies, code, translations, graphics, and libraries:** their respective contributors and licensors; see upstream notices, source headers and generated third-party license information where applicable.
- Windows-specific contributions in this fork are independent modifications. Do **not** attribute those modifications to the upstream team.

The upstream README and source history are preserved to credit upstream work. The upstream README's description of an Android application should not be mistaken for a description of this experimental Windows port.

## License

NewPipe is distributed under the **GNU General Public License, version 3 or later (GPL-3.0-or-later)**. Keep the repository's upstream [LICENSE](LICENSE), existing SPDX/copyright notices, license attributions, and Git history. Clearly mark significant changes and respect applicable notices for third-party libraries and assets.

When distributing modified executables, make corresponding source code available under the applicable GPL terms, provide the license and required notices, and identify modifications. Before any wider release, check the bundled dependencies and third-party notices in the actual build. A publicly readable GitHub fork alone is not a substitute for assessing and fulfilling every applicable distribution requirement.

## Name and trademark: test builds are not public releases

"NewPipe" is a registered trademark subject to the [NewPipe e. V. trademark policy](https://newpipe-ev.de/policy/trademark/).

The current `NewPipe Windows Prototype` labels and upstream icon are **temporary development/test artifacts**, not approved public-release branding. The policy makes a distinction between limited developer-facing test builds and end-user releases. **Before an end-user release, choose an independent application name, installer/package labels, icons, and presentation that do not misleadingly use the NewPipe trademark or imply official affiliation.** Technical names preserved in source paths are a separate matter from product branding.

Do not publish general-audience releases under NewPipe-branded names without confirming permission or clear compliance with the applicable policy. The documentation, app's About screen and release pages must visibly state that this is independent and unofficial.

## Release checklist (not yet completed)

- [ ] Choose a distinctive independent product name, artwork, app window title and installer labels.
- [ ] Present clear upstream credit and a prominent unofficial/non-affiliation notice in the app's About screen.
- [ ] Retain upstream authorship and license headers; identify this fork's modifications.
- [ ] Include GNU GPL license and meet corresponding-source delivery obligations for binaries.
- [ ] Review third-party dependencies, images, fonts and license acknowledgments.
- [ ] Clearly identify the exact source revision used to assemble a download and where to obtain its source.
- [ ] State that desktop builds are not made or endorsed by TeamNewPipe and are not compatible with official Android app installers.
- [ ] Review the latest upstream trademark policy before public distribution and avoid representing prototype builds as a stable release.

This document records project rules for development. It is not legal advice or a claim that a completed release has already passed a compliance review.
