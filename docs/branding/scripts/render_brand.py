"""Render the full Joulie brand asset pack.

Outputs (under joulie_brand/):
  logos/joulie_logo_light.png       — bolt-J + wordmark on transparent (for light surfaces)
  logos/joulie_logo_dark.png        — bolt-J + wordmark on transparent (for dark surfaces)
  logos/joulie_logo_white_bg.png    — wordmark lockup on solid white
  logos/joulie_logo_dark_bg.png     — wordmark lockup on solid dark surface
  logos/joulie_mark_only.png        — just the squircle mark, transparent
  logos/joulie_mark_mono.png        — monochrome mark for Android 13+ themed icons
  mipmap-*/ic_launcher.png          — square launcher (legacy, pre-O)
  mipmap-*/ic_launcher_round.png    — round launcher (legacy, pre-O)
  mipmap-*/ic_launcher_foreground.png — adaptive foreground (Android 8+)
  mipmap-*/ic_launcher_monochrome.png — themed-icon foreground (Android 13+)
  play-store/ic_launcher_512.png    — Play Store listing icon
  play-store/feature_graphic.png    — 1024×500 Play Store feature graphic
  play-store/feature_graphic_dark.png
  notification/ic_stat_joulie_*.png — single-colour status-bar icon, 5 densities
  palette/brand_palette.png         — palette swatch with hex codes
  drawable/ic_spslab_badge_v2.svg   — refreshed SPS-Lab badge (yellow bolt, M3)
"""
from __future__ import annotations

import os
from io import BytesIO

import cairosvg
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = os.path.dirname(os.path.abspath(__file__))

# ---------- Brand tokens ----------
BRAND_BLUE      = "#0D47FF"
BRAND_BLUE_DARK = "#0A2FB0"
BRAND_BLUE_HI   = "#1F3DCC"
BRAND_TEAL      = "#00C2D1"
BRAND_GREEN     = "#A6F43C"
BRAND_YELLOW    = "#FFD54D"
INK_DEEP        = "#0A1B5E"   # wordmark on light surfaces
SURFACE_LIGHT   = "#FDFCFF"
SURFACE_DARK    = "#1A1C1E"
ON_SURFACE_LIGHT= "#1A1C1E"
ON_SURFACE_DARK = "#E2E2E6"
NEUTRAL_TAGLINE_L = "#43474E"
NEUTRAL_TAGLINE_D = "#C3C7CF"

DISPLAY_FONT = "/usr/share/fonts/truetype/noto/NotoSansDisplay-Black.ttf"
BODY_FONT    = "/usr/share/fonts/truetype/noto/NotoSans-Medium.ttf"

# Mipmap densities (px @ "regular" launcher: 48dp; foreground/legacy: 108dp / 48dp)
MIPMAP = {
    "mipmap-mdpi":    {"legacy": 48,  "foreground": 108},
    "mipmap-hdpi":    {"legacy": 72,  "foreground": 162},
    "mipmap-xhdpi":   {"legacy": 96,  "foreground": 216},
    "mipmap-xxhdpi":  {"legacy": 144, "foreground": 324},
    "mipmap-xxxhdpi": {"legacy": 192, "foreground": 432},
}

# Notification stat icon: 24dp at each density (per Android guidelines).
# 24dp = 24/16 = 1.5 → density × 1.5; using mdpi=24, hdpi=36, xhdpi=48, xxhdpi=72, xxxhdpi=96.
NOTIF = {
    "drawable-mdpi":    24,
    "drawable-hdpi":    36,
    "drawable-xhdpi":   48,
    "drawable-xxhdpi":  72,
    "drawable-xxxhdpi": 96,
}

# ---------- SVG sources ----------

def mark_svg(monochrome: bool = False, foreground_only: bool = False) -> str:
    """Return the master mark SVG. Three variants:
       - default: full-colour squircle tile + bolt
       - foreground_only: bolt only (transparent), for adaptive icon foreground
       - monochrome: solid white bolt only (for Android 13+ themed icons)
    """
    if monochrome:
        return f"""<?xml version='1.0' encoding='UTF-8'?>
<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1024 1024' width='1024' height='1024'>
  <path fill='#FFFFFF'
        d='M 360 200 L 712 200 L 712 318 L 600 318 L 540 470 L 660 470
           L 470 760 C 432 818, 380 840, 332 840 C 248 840, 196 786, 196 706
           L 312 706 C 312 738, 332 758, 360 758 C 380 758, 396 748, 410 728
           L 488 608 L 360 608 Z'/>
</svg>"""
    if foreground_only:
        return f"""<?xml version='1.0' encoding='UTF-8'?>
<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1024 1024' width='1024' height='1024'>
  <defs>
    <linearGradient id='b' x1='0.2' y1='0' x2='0.7' y2='1'>
      <stop offset='0%' stop-color='#FFFFFF'/>
      <stop offset='40%' stop-color='{BRAND_GREEN}'/>
      <stop offset='100%' stop-color='{BRAND_TEAL}'/>
    </linearGradient>
    <filter id='s' x='-20%' y='-20%' width='140%' height='140%'>
      <feGaussianBlur in='SourceAlpha' stdDeviation='10'/>
      <feOffset dx='0' dy='8'/>
      <feComponentTransfer><feFuncA type='linear' slope='0.40'/></feComponentTransfer>
      <feMerge><feMergeNode/><feMergeNode in='SourceGraphic'/></feMerge>
    </filter>
  </defs>
  <g filter='url(#s)'>
    <path fill='url(#b)'
          d='M 360 200 L 712 200 L 712 318 L 600 318 L 540 470 L 660 470
             L 470 760 C 432 818, 380 840, 332 840 C 248 840, 196 786, 196 706
             L 312 706 C 312 738, 332 758, 360 758 C 380 758, 396 748, 410 728
             L 488 608 L 360 608 Z'/>
  </g>
  <path fill='#FFFFFF' opacity='0.32' d='M 360 200 L 712 200 L 712 248 L 360 248 Z'/>
</svg>"""
    return f"""<?xml version='1.0' encoding='UTF-8'?>
<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1024 1024' width='1024' height='1024'>
  <defs>
    <linearGradient id='t' x1='0' y1='0' x2='1' y2='1'>
      <stop offset='0%' stop-color='{BRAND_BLUE_HI}'/>
      <stop offset='55%' stop-color='{BRAND_BLUE}'/>
      <stop offset='100%' stop-color='{BRAND_BLUE_DARK}'/>
    </linearGradient>
    <linearGradient id='b' x1='0.2' y1='0' x2='0.7' y2='1'>
      <stop offset='0%' stop-color='#FFFFFF'/>
      <stop offset='40%' stop-color='{BRAND_GREEN}'/>
      <stop offset='100%' stop-color='{BRAND_TEAL}'/>
    </linearGradient>
    <radialGradient id='g' cx='0.5' cy='0.4' r='0.6'>
      <stop offset='0%' stop-color='#FFFFFF' stop-opacity='0.20'/>
      <stop offset='55%' stop-color='#FFFFFF' stop-opacity='0.05'/>
      <stop offset='100%' stop-color='#FFFFFF' stop-opacity='0'/>
    </radialGradient>
    <filter id='s' x='-20%' y='-20%' width='140%' height='140%'>
      <feGaussianBlur in='SourceAlpha' stdDeviation='10'/>
      <feOffset dx='0' dy='8'/>
      <feComponentTransfer><feFuncA type='linear' slope='0.40'/></feComponentTransfer>
      <feMerge><feMergeNode/><feMergeNode in='SourceGraphic'/></feMerge>
    </filter>
  </defs>
  <rect x='0' y='0' width='1024' height='1024' rx='230' ry='230' fill='url(#t)'/>
  <rect x='0' y='0' width='1024' height='1024' rx='230' ry='230' fill='url(#g)'/>
  <g filter='url(#s)'>
    <path fill='url(#b)'
          d='M 360 200 L 712 200 L 712 318 L 600 318 L 540 470 L 660 470
             L 470 760 C 432 818, 380 840, 332 840 C 248 840, 196 786, 196 706
             L 312 706 C 312 738, 332 758, 360 758 C 380 758, 396 748, 410 728
             L 488 608 L 360 608 Z'/>
  </g>
  <path fill='#FFFFFF' opacity='0.32' d='M 360 200 L 712 200 L 712 248 L 360 248 Z'/>
</svg>"""


def svg_to_pil(svg: str, w: int, h: int) -> Image.Image:
    png = cairosvg.svg2png(bytestring=svg.encode("utf-8"), output_width=w, output_height=h)
    return Image.open(BytesIO(png)).convert("RGBA")


# ---------- Renderers ----------

def render_mark_only(out_path: str, size: int = 1024) -> Image.Image:
    img = svg_to_pil(mark_svg(), size, size)
    img.save(out_path)
    return img


def render_foreground(out_path: str, size: int) -> Image.Image:
    img = svg_to_pil(mark_svg(foreground_only=True), size, size)
    img.save(out_path)
    return img


def render_monochrome(out_path: str, size: int) -> Image.Image:
    img = svg_to_pil(mark_svg(monochrome=True), size, size)
    img.save(out_path)
    return img


def render_legacy_launcher(out_path: str, size: int, round_icon: bool = False) -> None:
    """Pre-Android-O launcher: blue tile or circle with the bolt centered."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    # Solid brand-blue background, rounded
    bg = Image.new("RGBA", (size, size), _hex(BRAND_BLUE))
    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    if round_icon:
        md.ellipse((0, 0, size, size), fill=255)
    else:
        radius = int(size * 0.225)
        md.rounded_rectangle((0, 0, size, size), radius=radius, fill=255)
    canvas.paste(bg, (0, 0), mask)
    # Bolt foreground (centered, 60% of canvas)
    fg_size = int(size * 0.62)
    fg = svg_to_pil(mark_svg(foreground_only=True), fg_size, fg_size)
    canvas.alpha_composite(fg, ((size - fg_size) // 2, (size - fg_size) // 2))
    canvas.save(out_path)


def _hex(c: str):
    c = c.lstrip("#")
    return tuple(int(c[i:i+2], 16) for i in (0, 2, 4)) + (255,)


def render_wordmark_lockup(
    out_path: str,
    *,
    width: int = 1800,
    height: int = 600,
    bg=None,
    wordmark_color: str = INK_DEEP,
    tagline_color: str = NEUTRAL_TAGLINE_L,
    show_tagline: bool = True,
) -> Image.Image:
    """Horizontal lockup: mark on the left, wordmark stacked over the tagline
    on the right. Spacing is tuned so the j-descender clears the tagline."""
    img = Image.new("RGBA", (width, height), bg if bg else (0, 0, 0, 0))

    pad = 60
    mark_size = int(height * 0.78)
    mark = svg_to_pil(mark_svg(), mark_size, mark_size)
    mark_y = (height - mark_size) // 2
    img.alpha_composite(mark, (pad, mark_y))

    text_x = pad + mark_size + 70
    word_font = ImageFont.truetype(DISPLAY_FONT, int(height * 0.42))
    body_font = ImageFont.truetype(BODY_FONT, int(height * 0.075))

    draw = ImageDraw.Draw(img)
    word = "joulie"
    # Measure with the actual ascent/descent metrics so the j descender is
    # accounted for in vertical centring.
    a, d = word_font.getmetrics()
    word_visible_h = a + d
    # Optically centre the wordmark slightly above the canvas midline so the
    # tagline can sit beneath the descender with comfortable breathing room.
    if show_tagline:
        word_y = int(height * 0.18)
    else:
        word_y = (height - word_visible_h) // 2
    draw.text((text_x, word_y), word, font=word_font, fill=_hex(wordmark_color),
              anchor="la")

    if show_tagline:
        tagline = "Log every charge. Understand every kilometre."
        # Position tagline well below the descender baseline.
        tagline_y = word_y + word_visible_h + int(height * 0.06)
        draw.text((text_x + 6, tagline_y), tagline, font=body_font,
                  fill=_hex(tagline_color), anchor="la")

    img.save(out_path)
    return img


def render_palette_swatch(out_path: str) -> None:
    """Palette swatch card: 1600×800 with each brand colour + hex + token name."""
    W, H = 1600, 900
    img = Image.new("RGB", (W, H), _hex(SURFACE_LIGHT)[:3])
    draw = ImageDraw.Draw(img)

    title_font = ImageFont.truetype(DISPLAY_FONT, 64)
    label_font = ImageFont.truetype(DISPLAY_FONT, 28)
    hex_font   = ImageFont.truetype(BODY_FONT, 24)
    sub_font   = ImageFont.truetype(BODY_FONT, 20)

    draw.text((60, 50), "Joulie brand palette", font=title_font, fill=_hex(INK_DEEP)[:3])
    draw.text((60, 130), "Material 3 seed colours · WCAG-checked pairings",
              font=sub_font, fill=_hex(NEUTRAL_TAGLINE_L)[:3])

    swatches = [
        ("Brand Blue",   BRAND_BLUE,    "Primary seed · joulie_brand_blue"),
        ("Deep Blue",    BRAND_BLUE_DARK, "Gradient anchor"),
        ("Teal",         BRAND_TEAL,    "Secondary seed · AC sessions"),
        ("Spark Green",  BRAND_GREEN,   "Accent · success / efficiency"),
        ("Energy Yellow",BRAND_YELLOW,  "Tertiary seed · DC sessions"),
        ("Ink Deep",     INK_DEEP,      "Wordmark on light surfaces"),
        ("Surface Light",SURFACE_LIGHT, "Light theme background"),
        ("Surface Dark", SURFACE_DARK,  "Dark theme background"),
    ]
    cols = 4
    sw_w, sw_h = 340, 280
    gap_x, gap_y = 20, 60
    start_x, start_y = 60, 200

    for i, (name, hexv, sub) in enumerate(swatches):
        col, row = i % cols, i // cols
        x = start_x + col * (sw_w + gap_x)
        y = start_y + row * (sw_h + gap_y)
        draw.rounded_rectangle((x, y, x + sw_w, y + sw_h - 80), radius=24, fill=_hex(hexv)[:3])
        # Choose label colour based on luminance
        r, g, b = _hex(hexv)[:3]
        lum = 0.299*r + 0.587*g + 0.114*b
        chip_text = (255, 255, 255) if lum < 140 else _hex(INK_DEEP)[:3]
        draw.text((x + 24, y + 24), hexv, font=label_font, fill=chip_text)
        # Name + sub below the chip
        draw.text((x + 4, y + sw_h - 70), name, font=label_font, fill=_hex(INK_DEEP)[:3])
        draw.text((x + 4, y + sw_h - 32), sub, font=sub_font, fill=_hex(NEUTRAL_TAGLINE_L)[:3])

    img.save(out_path, optimize=True)


def render_feature_graphic(out_path: str, *, dark: bool = False) -> None:
    """Play Store feature graphic: 1024×500."""
    W, H = 1024, 500
    if dark:
        bg_color = _hex(SURFACE_DARK)[:3]
        word_color = "#FFFFFF"
        tag_color = NEUTRAL_TAGLINE_D
    else:
        bg_color = _hex(SURFACE_LIGHT)[:3]
        word_color = INK_DEEP
        tag_color = NEUTRAL_TAGLINE_L

    img = Image.new("RGB", (W, H), bg_color)

    # Subtle radial gradient overlay using a blurred ellipse
    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    accent = _hex(BRAND_BLUE) if not dark else _hex(BRAND_TEAL)
    od.ellipse((-200, -300, 700, 600), fill=accent[:3] + (35,))
    overlay = overlay.filter(ImageFilter.GaussianBlur(60))
    img = Image.alpha_composite(img.convert("RGBA"), overlay).convert("RGB")
    draw = ImageDraw.Draw(img)

    # Mark
    mark_size = 360
    mark = svg_to_pil(mark_svg(), mark_size, mark_size)
    img.paste(mark, (70, (H - mark_size)//2), mark)

    # Wordmark stacked above the tagline. Use anchor='la' so coordinates are
    # the top-left of the glyph cell and we can leave deterministic clearance
    # for the j descender.
    word_font = ImageFont.truetype(DISPLAY_FONT, 150)
    tag_font  = ImageFont.truetype(BODY_FONT, 28)
    a, d = word_font.getmetrics()
    word_y = 90
    draw.text((500, word_y), "joulie", font=word_font, fill=_hex(word_color)[:3], anchor="la")
    tag_y = word_y + a + d + 30
    draw.text((506, tag_y),       "Log every charge.",            font=tag_font, fill=_hex(tag_color)[:3], anchor="la")
    draw.text((506, tag_y + 40),  "Understand every kilometre.",  font=tag_font, fill=_hex(tag_color)[:3], anchor="la")

    img.save(out_path, optimize=True)


def render_notification_icon(out_path: str, size: int) -> None:
    """24dp solid-white bolt on transparent — Android status-bar icon."""
    img = svg_to_pil(mark_svg(monochrome=True), size, size)
    img.save(out_path)


def render_wizard_hero(out_path: str, *, dark: bool = False) -> None:
    """Vertical hero for wizard page 1.
    1080 px wide × 720 px tall — a bold gradient panel with the mark
    floating at the centre over a soft radial spark and the wordmark
    underneath. Designed to dominate the top half of the welcome screen."""
    W, H = 1080, 960
    bg_top    = BRAND_BLUE_HI   if not dark else "#08134A"
    bg_bottom = BRAND_BLUE_DARK if not dark else "#03082A"
    img = Image.new("RGB", (W, H), _hex(bg_top)[:3])
    # Vertical gradient, simple per-row interpolation
    pix = img.load()
    rt, gt, bt = _hex(bg_top)[:3]
    rb, gb, bb = _hex(bg_bottom)[:3]
    for y in range(H):
        t = y / (H - 1)
        r = int(rt + (rb - rt) * t)
        g = int(gt + (gb - gt) * t)
        b = int(bt + (bb - bt) * t)
        for x in range(W):
            pix[x, y] = (r, g, b)

    # Soft radial spark behind the mark (teal halo)
    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    od.ellipse((W//2 - 380, 60, W//2 + 380, 820), fill=_hex(BRAND_TEAL)[:3] + (55,))
    overlay = overlay.filter(ImageFilter.GaussianBlur(90))
    img = Image.alpha_composite(img.convert("RGBA"), overlay).convert("RGB")

    # Mark
    mark_size = 360
    mark_y = 90
    mark = svg_to_pil(mark_svg(), mark_size, mark_size)
    img.paste(mark, ((W - mark_size)//2, mark_y), mark)

    # Wordmark + tagline (always white on this gradient)
    draw = ImageDraw.Draw(img)
    word_font = ImageFont.truetype(DISPLAY_FONT, 130)
    tag_font  = ImageFont.truetype(BODY_FONT, 32)
    word = "joulie"
    bbox = draw.textbbox((0, 0), word, font=word_font, anchor="la")
    word_w = bbox[2] - bbox[0]
    a, d = word_font.getmetrics()
    word_y = mark_y + mark_size + 30
    draw.text(((W - word_w)//2, word_y), word, font=word_font, fill=(255, 255, 255), anchor="la")

    tag = "Log every charge.\nUnderstand every kilometre."
    tag_y = word_y + a + d + 30
    for i, line in enumerate(tag.split("\n")):
        bbox = draw.textbbox((0, 0), line, font=tag_font, anchor="la")
        line_w = bbox[2] - bbox[0]
        draw.text(((W - line_w)//2, tag_y + i * 46), line, font=tag_font,
                  fill=_hex("#C7D4FF")[:3], anchor="la")

    img.save(out_path, optimize=True)


def render_about_hero(out_path: str, *, dark: bool = False) -> None:
    """Wide gradient banner for the About-screen header.
    1600 × 480 — mark on the left, wordmark + version line + SPS-Lab credit
    stacked on the right. Designed to live in a CollapsingToolbarLayout."""
    W, H = 1600, 480
    bg_left  = BRAND_BLUE_HI   if not dark else "#08134A"
    bg_right = BRAND_BLUE_DARK if not dark else "#03082A"
    img = Image.new("RGB", (W, H), _hex(bg_left)[:3])
    pix = img.load()
    rl, gl, bl = _hex(bg_left)[:3]
    rr, gr, br = _hex(bg_right)[:3]
    for x in range(W):
        t = x / (W - 1)
        r = int(rl + (rr - rl) * t)
        g = int(gl + (gr - gl) * t)
        b = int(bl + (br - bl) * t)
        for y in range(H):
            pix[x, y] = (r, g, b)

    # Soft accent
    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    od.ellipse((-200, -200, 800, 700), fill=_hex(BRAND_TEAL)[:3] + (40,))
    overlay = overlay.filter(ImageFilter.GaussianBlur(80))
    img = Image.alpha_composite(img.convert("RGBA"), overlay).convert("RGB")

    # Mark
    mark_size = 320
    mark = svg_to_pil(mark_svg(), mark_size, mark_size)
    img.paste(mark, (90, (H - mark_size)//2), mark)

    # Wordmark + meta
    draw = ImageDraw.Draw(img)
    word_font = ImageFont.truetype(DISPLAY_FONT, 160)
    sub_font  = ImageFont.truetype(BODY_FONT, 28)
    cap_font  = ImageFont.truetype(BODY_FONT, 24)
    a, d = word_font.getmetrics()
    word_y = 90
    draw.text((460, word_y), "joulie", font=word_font, fill=(255, 255, 255), anchor="la")
    sub_y = word_y + a + d + 20
    draw.text((466, sub_y),      "Log every charge. Understand every kilometre.",
              font=sub_font, fill=_hex("#C7D4FF")[:3], anchor="la")
    draw.text((466, sub_y + 40), "By Sustainable Power Systems Lab · Cyprus University of Technology",
              font=cap_font, fill=_hex("#A6F43C")[:3], anchor="la")

    img.save(out_path, optimize=True)


def write_spslab_badge_v2(out_path: str) -> None:
    """Refreshed SPS-Lab badge — replaces the legacy DC-orange #FB8C00 with
    the Joulie tertiary yellow #FFD54D, matching the new M3 ramp."""
    svg = """<?xml version='1.0' encoding='utf-8'?>
<!-- SPS-Lab pill badge v2. Pair with a TextView overlay for the wordmark. -->
<vector xmlns:android='http://schemas.android.com/apk/res/android'
    android:width='240dp' android:height='60dp'
    android:viewportWidth='240' android:viewportHeight='60'>
  <!-- Pill (primaryContainer-tinted) -->
  <path android:fillColor='#DCE1FF'
        android:pathData='M30,0H210Q240,0 240,30Q240,60 210,60H30Q0,60 0,30Q0,0 30,0Z'/>
  <!-- Bolt (Joulie tertiary yellow, M3-aligned) -->
  <path android:fillColor='#FFD54D'
        android:pathData='M28,12 L21,30 L26,30 L22,48 L33,26 L28,26 L33,12Z'/>
</vector>
"""
    with open(out_path, "w") as f:
        f.write(svg)


# ---------- Main ----------

def main():
    os.makedirs(f"{ROOT}/logos", exist_ok=True)
    os.makedirs(f"{ROOT}/play-store", exist_ok=True)
    os.makedirs(f"{ROOT}/palette", exist_ok=True)
    os.makedirs(f"{ROOT}/drawable", exist_ok=True)
    for d in MIPMAP:
        os.makedirs(f"{ROOT}/{d}", exist_ok=True)
    for d in NOTIF:
        os.makedirs(f"{ROOT}/{d}", exist_ok=True)

    print("• mark-only / foreground / monochrome SVG renders")
    render_mark_only(f"{ROOT}/logos/joulie_mark_only.png", 1024)
    render_foreground(f"{ROOT}/logos/joulie_foreground.png", 1024)
    render_monochrome(f"{ROOT}/logos/joulie_mark_mono.png", 1024)

    print("• wordmark lockups (light + dark + with-bg variants)")
    render_wordmark_lockup(
        f"{ROOT}/logos/joulie_logo_light.png",
        wordmark_color=INK_DEEP, tagline_color=NEUTRAL_TAGLINE_L,
    )
    render_wordmark_lockup(
        f"{ROOT}/logos/joulie_logo_dark.png",
        wordmark_color="#FFFFFF", tagline_color=NEUTRAL_TAGLINE_D,
    )
    render_wordmark_lockup(
        f"{ROOT}/logos/joulie_logo_white_bg.png",
        bg=_hex(SURFACE_LIGHT),
        wordmark_color=INK_DEEP, tagline_color=NEUTRAL_TAGLINE_L,
    )
    render_wordmark_lockup(
        f"{ROOT}/logos/joulie_logo_dark_bg.png",
        bg=_hex(SURFACE_DARK),
        wordmark_color="#FFFFFF", tagline_color=NEUTRAL_TAGLINE_D,
    )

    print("• mipmap launcher PNGs (legacy + adaptive foreground + monochrome)")
    for dpi, sizes in MIPMAP.items():
        render_legacy_launcher(f"{ROOT}/{dpi}/ic_launcher.png", sizes["legacy"], round_icon=False)
        render_legacy_launcher(f"{ROOT}/{dpi}/ic_launcher_round.png", sizes["legacy"], round_icon=True)
        render_foreground(f"{ROOT}/{dpi}/ic_launcher_foreground.png", sizes["foreground"])
        render_monochrome(f"{ROOT}/{dpi}/ic_launcher_monochrome.png", sizes["foreground"])

    print("• Play Store assets")
    # Use a high-quality 512px legacy launcher render
    render_legacy_launcher(f"{ROOT}/play-store/ic_launcher_512.png", 512, round_icon=False)
    render_feature_graphic(f"{ROOT}/play-store/feature_graphic.png", dark=False)
    render_feature_graphic(f"{ROOT}/play-store/feature_graphic_dark.png", dark=True)

    print("• Palette swatch")
    render_palette_swatch(f"{ROOT}/palette/brand_palette.png")

    print("• Notification stat icons (5 densities)")
    for d, s in NOTIF.items():
        render_notification_icon(f"{ROOT}/{d}/ic_stat_joulie.png", s)

    print("• Hero artwork (wizard + about, light + dark)")
    os.makedirs(f"{ROOT}/hero", exist_ok=True)
    render_wizard_hero(f"{ROOT}/hero/wizard_hero_light.png", dark=False)
    render_wizard_hero(f"{ROOT}/hero/wizard_hero_dark.png",  dark=True)
    render_about_hero(f"{ROOT}/hero/about_hero_light.png",   dark=False)
    render_about_hero(f"{ROOT}/hero/about_hero_dark.png",    dark=True)

    print("• SPS-Lab badge v2")
    write_spslab_badge_v2(f"{ROOT}/drawable/ic_spslab_badge.xml")

    print("Done.")


if __name__ == "__main__":
    main()
