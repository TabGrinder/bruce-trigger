# Credits and Acknowledgements

## Inspiration

**Bruce Trigger was inspired by using Bruce firmware on the LILYGO T-Embed CC1101.**

The firmware and hardware made this project possible. Bruce Trigger brings an independent Android control interface to that setup.

## Bruce Trigger

**[TabGrinder](https://github.com/TabGrinder)** — Android companion application, USB control interface, Android rendering and integration of the temporary graphics bridge.

**Codex**, developed by **OpenAI**, was used to assist with code optimization and documentation. This acknowledges development assistance; Codex is not a runtime dependency of the Android app.

## Bruce firmware

Credit to the **Bruce firmware developers, maintainers and contributors** for the firmware, its interfaces, documentation and community work.

- [Bruce firmware repository](https://github.com/BruceDevices/firmware)
- [Project website](https://bruce.computer/)
- [Documentation](https://wiki.bruce.computer/)
- [Upstream acknowledgements](https://github.com/BruceDevices/firmware#-acknowledgements)
- [Contributors](https://github.com/BruceDevices/firmware/graphs/contributors)

The release's protocol reference is Bruce **1.16.1**, commit `ba519c936c87b89c9c667c15b932d7faca5360d5`.

## LILYGO

Credit to **LILYGO** for designing and documenting the **T-Embed CC1101**, the hardware that inspired Bruce Trigger's development.

- [T-Embed CC1101 repository](https://github.com/Xinyuan-LilyGO/T-Embed-CC1101)
- [Official hardware documentation](https://wiki.lilygo.cc/products/t-embed-series/t-embed-cc1101/)

## Bundled components

| Contributor / project | Contribution |
| --- | --- |
| **IncursioHack** | Original Megalodon game in Bruce, used as the basis for the bundled RAM adaptation |
| **Adafruit Industries** | Classic 5×7 font data used by the Android renderer |
| **Bodmer / TFT_eSPI** | Source and accompanying notices through which the classic font data was obtained |

The Megalodon adaptation is identified as **AGPL-3.0-or-later** in its source. Adafruit's font notices and the accompanying TFT_eSPI notices are retained. See [NOTICE.md](NOTICE.md) and [the bundled font notices](docs/THIRD_PARTY_FONT_NOTICES.txt).

The accompanying TFT_eSPI notices also credit **Limor Fried / Ladyada** for the original Adafruit_ILI9341 library and acknowledge contributors to **Adafruit_GFX** and **TFT_eSPI**. Their original copyright, permission and disclaimer text is preserved. This records the dependency's provenance; it does not imply that the complete display libraries are bundled in the APK.

## Interoperability reference

**BruceDevices/App-IOS** was consulted as a protocol interoperability reference. The supplied source states that no Swift code or assets from that project are bundled or ported.

- [BruceDevices/App-IOS](https://github.com/BruceDevices/App-IOS)

## Source-only test assets

The supplied application source includes test fixtures with their own attribution:

| Contributor / project | Source-only material | Attribution record |
| --- | --- | --- |
| **Bruce firmware contributors and upstream asset authors** | Sharky and Pirata example-theme images; Dino game and other interpreter fixtures | [Image provenance](docs/STOCK-IMAGE-PROVENANCE.json) and [game sources](docs/game-fixtures/sources.json) |
| **MSI** | Space Shooter, Ping Pong and Highway Racer | Each supplied game source preserves its on-screen “Developed by MSI” credit; [game sources](docs/game-fixtures/sources.json) |
| **hxd57** | Tamagochi | The supplied `tamagochi.js` header credits hxd57; [game sources](docs/game-fixtures/sources.json) |
| **eni9ma** | Cyberpunk 2077 theme | [Theme sources](docs/THEME-SOURCES.json) |
| **mostixgrand** | Flipper theme | [Flipper asset provenance](docs/FLIPPER-ASSETS.json) |
| **Valkrycxx** | [Flappy Bird](https://github.com/Valkrycxx/bruce-flappybird) | [Game sources](docs/game-fixtures/sources.json) |

These test assets are excluded from the production APK. Their detailed origins, hashes and applicable notices are preserved in the source's attribution records and [NOTICE.md](NOTICE.md). Credit for each asset remains with its author.

Where the supplied source does not identify an individual author, attribution stays with the upstream project and its contributors. This document complements the original notices and source history; it does not replace or narrow them.

## Documentation and screenshots

The documentation structure, authorized-use guidance and contribution workflow were informed by **[InoQuill](https://github.com/TabGrinder/inoquill)**, another project by TabGrinder, and adapted to Bruce Trigger's Android and USB scope. Bruce Trigger retains its own component attribution and license terms.

The [screenshot gallery](docs/SCREENSHOTS.md) contains images supplied for this project. The Bruce menus, games and other displayed content remain the work of their respective authors. The Space Shooter screen includes the visible credit **“Developed by MSI”**, which is preserved in the image. Megalodon is credited to **IncursioHack** above. Screenshots do not transfer ownership of the depicted content.

## Project relationship

Bruce Trigger is an independent community project. It is not an official BruceDevices, LILYGO or Flipper Devices release, and no endorsement is implied.

All referenced project names, logos and trademarks belong to their respective owners. Attribution does not transfer ownership or replace the applicable licenses.
