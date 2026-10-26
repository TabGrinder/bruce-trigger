# Notices

Bruce Trigger is an independent Android companion by TabGrinder. It is not an official BruceDevices, LILYGO, Flipper Devices or Hancock Ventures release. Project names and trademarks belong to their respective owners.

## Distribution license

The combined distribution is licensed under [GNU AGPLv3](LICENSE). The original Android companion's copyright and permission notice is retained in [LICENSE-MIT.txt](LICENSE-MIT.txt). Third-party components retain their own notices and license terms.

## Adafruit font

The app includes the unchanged, 1,280-byte classic Adafruit 5×7 font table from TFT_eSPI. The Adafruit BSD copyright, conditions and disclaimer are preserved in `app/src/main/res/raw/glcd_license.txt` and available in the app under **Settings → Open-source notices**. The accompanying TFT_eSPI notices are retained in [THIRD_PARTY_FONT_NOTICES.txt](docs/THIRD_PARTY_FONT_NOTICES.txt).

Source: `lib/TFT_eSPI/Fonts/glcdfont.c` at Bruce commit `ba519c936c87b89c9c667c15b932d7faca5360d5`.

Source file SHA-256: `bde369bf045a11a092f06d6073d44039c72515d8bc83353a87b8b8032e450d7c`.

The Java storage and rendering code was written for Bruce Trigger. The project's license does not relicense the font data.

## Megalodon

`app/src/main/res/raw/megalodon.txt` adapts the native Megalodon game by **IncursioHack** under **AGPL-3.0-or-later**.

Source: [Bruce's Megalodon implementation](https://github.com/BruceDevices/firmware/blob/ba519c936c87b89c9c667c15b932d7faca5360d5/src/modules/others/tururururu.cpp).

The adaptation runs in the device's RAM interpreter and adds pause/resume and USB graphics output. It does not replace the compiled firmware game. Its source and complete license terms accompany the APK.

## Protocol references

USB commands and the drawing protocol were reviewed against **Bruce 1.16.1**, commit `ba519c936c87b89c9c667c15b932d7faca5360d5`.

**BruceDevices/App-IOS**, revision `96b7fdd16437b58c3bfeabea33c8c403a11b851b`, was used as an interoperability reference. No Swift code or assets from that project are included.

The Android renderer and launcher icon were written for Bruce Trigger. The APK does not include a firmware image, firmware themes, the Flipper app or Biscuit Manager code or graphics.

## Test images and themes

The following assets are included in the source for testing and excluded from the APK. Original authorship and upstream license terms are retained; the Android companion's MIT notice does not relicense them.

| Material | Source | Attribution records |
| --- | --- | --- |
| Sharky and Pirata example images | Bruce firmware, commit `ba519c936c87b89c9c667c15b932d7faca5360d5`, `sd_files/themes/example/` | [Image provenance](docs/STOCK-IMAGE-PROVENANCE.json) |
| Cyberpunk 2077 theme by **eni9ma** | BruceDevices/App-Store-Apps, commit `c3a78d4a4e431351833d8124109bc81f7c267b57` | [Theme sources](docs/THEME-SOURCES.json) |
| Flipper theme by **mostixgrand** | BruceDevices/App-Store-Apps, commit `c3a78d4a4e431351833d8124109bc81f7c267b57` | [Flipper assets](docs/FLIPPER-ASSETS.json) |

The upstream Bruce license is preserved in [BRUCE-UPSTREAM-LICENSE.txt](docs/BRUCE-UPSTREAM-LICENSE.txt). The records above list exact paths and hashes.

## Test games

The source includes six game fixtures under `docs/game-fixtures/`, excluded from the APK.

Dino, Highway Racer, Ping Pong, Space Shooter and Tamagochi come from Bruce firmware commit `c9d3731b38e67598e5b66a6488d8b12aeb8bf4dd`, `sd_files/interpreter/`. **MSI** is credited for Highway Racer, Ping Pong and Space Shooter; **hxd57** is credited for Tamagochi. The upstream AGPLv3 text is retained in [BRUCE-UPSTREAM-LICENSE.txt](docs/BRUCE-UPSTREAM-LICENSE.txt).

Flappy Bird 1.0.7 is by **Valkrycxx**, from [bruce-flappybird](https://github.com/Valkrycxx/bruce-flappybird), commit `97a826ea98be2d108c71fbb1d296458a386033ad`.

All original authorship and applicable upstream terms remain in effect. Exact source URLs and hashes are listed in [game sources](docs/game-fixtures/sources.json).
