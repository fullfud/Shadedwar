# Third-Party Notices

This file documents third-party materials that are distributed inside this
repository and are not covered by the custom `Shadedwar` license.

For the files listed below, the upstream license or upstream distribution terms
control. See [LICENSE](LICENSE) for the custom project license carve-out.

## 1. Betaflight Configurator OSD `.mcm` Resources

Upstream project:
- Betaflight Configurator

Upstream repository:
- `C:\Projects\fpvosd\vendor\betaflight-configurator`
- https://github.com/betaflight/betaflight-configurator

Upstream license:
- GNU General Public License v3.0
- Local source: `C:\Projects\fpvosd\vendor\betaflight-configurator\LICENSE`

Shipped files in this repository:
- `src/main/resources/assets/fullfud/osd/betaflight.mcm`
  Source: `C:\Projects\fpvosd\vendor\betaflight-configurator\resources\osd\1\betaflight.mcm`
- `src/main/resources/assets/fullfud/osd/default.mcm`
  Source: `C:\Projects\fpvosd\vendor\betaflight-configurator\resources\osd\1\default.mcm`
- `src/main/resources/assets/fullfud/osd/betaflight_hd.mcm`
  Source: `C:\Projects\fpvosd\vendor\betaflight-configurator\resources\osd\2\betaflight.mcm`
- `src/main/resources/assets/fullfud/osd/crosshair_thin.mcm`
  Source: `C:\Projects\fpvosd\vendor\betaflight-configurator\resources\osd\2\default.mcm`

Verification note:
- The files above were matched against the local upstream vendor copy by exact
  SHA256 hash.

Applicable notice:
- These `.mcm` resources remain subject to the Betaflight Configurator GPL-3.0
  license and any applicable upstream copyright notices.

## 2. VCR OSD Mono Font

Upstream font:
- `VCR OSD Mono`

Upstream author / attribution:
- Riciery Leal
- The bundled font metadata identifies the font as `VCR OSD Mono`, version
  `1.001`, with internal designer string `MrManet`.

Shipped file in this repository:
- `src/main/resources/assets/fullfud/font/vcr_osd_mono.ttf`

Upstream distribution page:
- https://www.dafont.com/vcr-osd-mono.font

Upstream license / distribution status:
- No standalone SPDX license text has been identified for the bundled file.
- The upstream distribution page marks this font as `100% Free`.

Important note:
- No separate standalone license text for this font is bundled in this
  repository.
- This file should therefore be treated as subject to the upstream author's
  font terms and the distribution status shown on the upstream page, not the
  custom `Shadedwar` license.

## 3. Scope of This Notices File

This file is limited to third-party materials that are currently distributed in
`src/main/resources` and were identified as copied from or clearly derived from
external sources during the license audit.

If additional third-party assets, fonts, glyph sets, binaries, models, or
other imported materials are added later, this file should be updated at the
same time.
