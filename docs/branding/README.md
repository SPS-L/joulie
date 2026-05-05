# Joulie brand assets

This folder is the canonical home for everything Joulie-branded in the repo. Anything that touches the launcher icon, splash screen, hero artwork, palette, typography, or voice should be cross-checked against [`Joulie_Brand_Guide.pdf`](Joulie_Brand_Guide.pdf) before landing.

## What lives here

| Path | Purpose |
|---|---|
| [`Joulie_Brand_Guide.pdf`](Joulie_Brand_Guide.pdf) | The full brand book (v1.0). Strategy, palette, typography, voice, asset inventory, step-by-step update instructions, light vs. dark rules, acceptance checklist. **Source of truth when in doubt.** |
| `joulie_logo_light.png` | Master logo for light surfaces (used as the README hero in `prefers-color-scheme: light`). |
| `joulie_logo_dark.png` | Master logo for dark surfaces (README hero in `prefers-color-scheme: dark`). |
| `joulie_logo_white_bg.png` / `joulie_logo_dark_bg.png` | Logos pre-composited on white / dark backgrounds for documents and slides. |
| `joulie_mark_only.png` | Bolt-J mark with no wordmark, full colour. |
| `joulie_mark_mono.png` | Bolt-J monochrome silhouette. |
| `brand_palette.png` | Swatch sheet for marketing. The Material 3 token mapping lives in `app/src/main/res/values/colors.xml`. |
| `svg/` | Editable SVG sources (`joulie_mark.svg`, `joulie_foreground.svg`, `joulie_logo_light.svg`). Use these as the starting point for any new asset. |
| `play-store/ic_launcher_512.png` | Hi-res icon for the Play Console listing. |
| `play-store/feature_graphic.png` / `feature_graphic_dark.png` | 1024x500 store-listing banners (light + dark). |
| `scripts/render_brand.py` | Pipeline that regenerates every PNG asset from the SVG sources. |
| `scripts/build_brand_guide.py` | Pipeline that regenerates `Joulie_Brand_Guide.pdf` from the same source palette + voice rules. |

## Brand summary

| Token | Hex | Role |
|---|---|---|
| Brand Blue | `#0D47FF` | Primary seed (`joulie_brand_blue`) |
| Deep Blue | `#0A2FB0` | Gradient anchor |
| Teal | `#00C2D1` | Secondary, AC chart series |
| Spark Green | `#A6F43C` | Accent, SPS-Lab credit (`joulie_spark_green`) |
| Energy Yellow | `#FFD54D` | Tertiary, DC chart series |
| Ink Deep | `#0A1B5E` | Wordmark on light surfaces (`joulie_ink_deep`) |

Tagline: **Log every charge. Understand every kilometre.**

## Voice rules (Brand Guide section 1)

Plain English. Numbers always have units. Never use the em-dash. The CI lint rule `TypographyDashes` is in error mode; new prose that introduces an em-dash will fail the build. Voice rules apply to every user-facing surface and to all contributor docs (the v1.9.0 retrofit swept all 9 markdown docs and 4 string locales).

## How an asset change ships

1. Edit the SVG in `svg/` (or whichever master is the right starting point).
2. Re-run `scripts/render_brand.py` against the SVG sources to regenerate every PNG bucket (Android mipmaps, Play Store assets, README hero variants).
3. Drop the regenerated assets over `app/src/main/res/` and `docs/branding/` per the Brand Guide section 8 step-by-step.
4. Re-run `scripts/build_brand_guide.py` only if the palette, voice, or asset inventory has changed.
5. Standard branch / merge / version-bump / tag flow.

## License

Brand assets are released under the same GPL-3.0-or-later license as the Joulie app. Built and maintained by the [Sustainable Power Systems Lab](https://sps-lab.org/), Cyprus University of Technology.
