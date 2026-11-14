# Notices

This is an independent Android companion project by TabGrinder. It is not an official BruceDevices, Flipper Devices, LILYGO, or Hancock Ventures release. Those projects and their trademarks belong to their respective owners.

Bruce firmware 1.16.1 source was consulted to document USB commands and the draw-call wire format. The production APK does not bundle a firmware image, firmware theme images, the Flipper app, Biscuit Manager code, or their graphic assets. The Android renderer and launcher icon are independently written. The app bundles the 1,280-byte classic Adafruit 5×7 font table from TFT_eSPI, with the original Adafruit BSD copyright, conditions and disclaimer retained in `app/src/main/res/raw/glcd_license.txt` and exposed in Settings → Open-source notices. The font table is unchanged; Java storage, layout and rendering are independently implemented. This project license does not relicense that third-party data.

Font provenance: `lib/TFT_eSPI/Fonts/glcdfont.c` at Bruce commit `ba519c936c87b89c9c667c15b932d7faca5360d5`; source file SHA256 `bde369bf045a11a092f06d6073d44039c72515d8bc83353a87b8b8032e450d7c`. The full TFT_eSPI notices accompanying this dependency are retained, including the Adafruit font license.

The app runtime uses Android platform APIs only. The Android SDK/JDK used to build it are separate tools, not app dependencies. No downloaded web fonts, analytics libraries or network SDKs are included. Gradle configuration is provided for Android Studio import; the delivered APK was built using tools/build.py, not Gradle, and the Gradle path is not claimed as tested.

## Protocol references

The native stock protocol and lifecycle were reviewed against BruceDevices/firmware 1.16.1 (ba519c936c87b89c9c667c15b932d7faca5360d5). BruceDevices/App-IOS, revision 96b7fdd16437b58c3bfeabea33c8c403a11b851b, was consulted as an interoperability reference. No Swift code or assets from that AGPL project are bundled or ported. Native screen state and Android safeguards were implemented independently. There are no firmware binaries, patches or flashing tools in this package.

## QA image fixtures

The source/audit archive includes generated StockImageFixtures.java with unmodified image bytes from sd_files/themes/example/Sharky.zip and Pirata_png.zip in the pinned Bruce repository. These QA-only example assets retain their original upstream authorship and applicable upstream terms; they are not relicensed under this project's MIT notice and are excluded from the production APK. The upstream repository AGPLv3 text is preserved in docs/BRUCE-UPSTREAM-LICENSE.txt. docs/STOCK-IMAGE-PROVENANCE.json records each exact asset and archive hash. The separately supplied firmware source is read to generate serialization fixtures; no firmware binary or patch is distributed. Source: https://github.com/brucedevices/firmware/tree/ba519c936c87b89c9c667c15b932d7faca5360d5/sd_files/themes/example

## Cyberpunk QA theme

QA-only StockImageFixtures includes the 14 original Cyberpunk2077 PNGs by eni9ma from BruceDevices/App-Store-Apps commit c3a78d4a4e431351833d8124109bc81f7c267b57, Themes/Cyberpunk2077 320x170 (the repository path contains a space between Cyberpunk and 2077). Original attribution and applicable upstream terms are retained; this project's MIT license does not relicense those assets. They are absent from the production APK. The official store metadata and exact source URLs/hashes are in docs/THEME-SOURCES.json. The user-specific directory spelling is a test path only.

## Flipper QA theme

FlipperMenuFixtures.java contains the 14 unmodified PNGs from BruceDevices/App-Store-Apps commit c3a78d4a4e431351833d8124109bc81f7c267b57, Themes/Flipper 320x170, attributed by the official store to mostixgrand. This is an upstream QA example, not asserted identical to the user’s local HackTheW0r1d variant. Original authorship and applicable upstream terms are retained; the project license does not relicense these assets. They are excluded from the production APK. docs/FLIPPER-ASSETS.json records the source metadata and exact asset hashes.

## QA game fixtures

The source archive includes unchanged QA sources and metadata for the six reported games under `docs/game-fixtures`. They are excluded from the production APK. Dino, Highway Racer, Ping Pong, Space Shooter and Tamagochi come from BruceDevices/firmware commit c9d3731b38e67598e5b66a6488d8b12aeb8bf4dd, sd_files/interpreter, as referenced by the official App Store. Their upstream AGPLv3 text is retained in docs/BRUCE-UPSTREAM-LICENSE.txt; this project's MIT notice does not relicense these fixtures.

Flappy Bird 1.0.7 is by Valkrycxx, https://github.com/Valkrycxx/bruce-flappybird, commit 97a826ea98be2d108c71fbb1d296458a386033ad. Its original authorship and applicable upstream terms remain intact. Exact provenance and source SHA256 values are in docs/game-fixtures/sources.json. The generated Android fixtures represent drawing operations under hardware doubles, not physical-device captures. The APK contains a fixed graphics adapter and the separate Megalodon RAM adaptation; these six QA games are not bundled. The selected Files route has no one-game hash restriction.

## Megalodon RAM adaptation

`app/src/main/res/raw/megalodon.txt` is an AGPL-3.0-or-later adaptation of the native Megalodon game by IncursioHack in Bruce `src/modules/others/tururururu.cpp`, commit `ba519c936c87b89c9c667c15b932d7faca5360d5`. Source: https://github.com/brucedevices/firmware/blob/ba519c936c87b89c9c667c15b932d7faca5360d5/src/modules/others/tururururu.cpp . The corresponding source and complete AGPL terms accompany this APK. The combined distribution uses the AGPLv3 license in LICENSE; the original Android companion's MIT copyright and permission notice remain in LICENSE-MIT.txt. Third-party notices remain applicable.

The adaptation runs in the device's RAM interpreter, uses a fixed dark background, two cached drawing assets and a complete device display sprite. It adds pause/resume, bounded execution and USB graphics transport. It does not include the native game's hidden credits dialog sequence; attribution is preserved here and in the app. It does not capture or modify the original compiled game, firmware or stored scripts.
