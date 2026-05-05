"""Build the Joulie Brand Guide PDF.

Single-file ReportLab pipeline. All assets are referenced from the
joulie_brand/ workspace. The PDF is intentionally text-first (no
chrome decoration) but uses generous typography and the brand palette
sparingly for hierarchy.
"""
from __future__ import annotations

import os
import urllib.request

from reportlab.lib.colors import HexColor
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate, Frame, Image, PageBreak, PageTemplate,
    Paragraph, Preformatted, Spacer, Table, TableStyle, KeepTogether,
)

ROOT = os.path.dirname(os.path.abspath(__file__))
OUT  = f"{ROOT}/Joulie_Brand_Guide.pdf"

# ---------- Fonts ----------
FONT_DIR = "/tmp/fonts"
os.makedirs(FONT_DIR, exist_ok=True)

# Use locally installed fonts. Noto Sans has full Unicode coverage (subscripts,
# Greek, Cyrillic, arrows) and is the canonical Android system stack, a good
# match for documenting an Android app.
LOCAL_FONTS = {
    "NotoSans-Regular":      "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
    "NotoSans-Medium":       "/usr/share/fonts/truetype/noto/NotoSans-Medium.ttf",
    "NotoSans-Bold":         "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf",
    "NotoSansDisplay-Black": "/usr/share/fonts/truetype/noto/NotoSansDisplay-Black.ttf",
    "NotoSansMono-Regular":  "/usr/share/fonts/truetype/noto/NotoSansMono-Regular.ttf",
}
for name, path in LOCAL_FONTS.items():
    if os.path.exists(path):
        try:
            pdfmetrics.registerFont(TTFont(name, path))
        except Exception as exc:
            print(f"  ! Could not register {name}: {exc}")
    else:
        print(f"  ! Missing local font {path}; will fall back to built-in.")

def font(name: str, fallback: str) -> str:
    return name if name in pdfmetrics.getRegisteredFontNames() else fallback

H_FONT     = font("NotoSans-Bold",         "Helvetica-Bold")
DISPLAY    = font("NotoSansDisplay-Black", H_FONT)
M_FONT     = font("NotoSans-Medium",       "Helvetica")
R_FONT     = font("NotoSans-Regular",      "Helvetica")
MONO       = font("NotoSansMono-Regular",  "Courier")

# Register a font family so Paragraph <b> / <i> tags resolve to the right TTF.
try:
    from reportlab.pdfbase.pdfmetrics import registerFontFamily
    registerFontFamily(R_FONT, normal=R_FONT, bold=H_FONT, italic=R_FONT, boldItalic=H_FONT)
except Exception:
    pass

# ---------- Brand palette (mirror of render_brand.py) ----------
BRAND_BLUE = HexColor("#0D47FF")
BRAND_DEEP = HexColor("#0A2FB0")
BRAND_HI   = HexColor("#1F3DCC")
BRAND_TEAL = HexColor("#00C2D1")
BRAND_GREEN= HexColor("#A6F43C")
BRAND_YELLOW = HexColor("#FFD54D")
INK_DEEP   = HexColor("#0A1B5E")
INK_BODY   = HexColor("#1A1C1E")
INK_MUTED  = HexColor("#43474E")
INK_FAINT  = HexColor("#73777F")
SURFACE    = HexColor("#FDFCFF")
SURFACE_ALT= HexColor("#F1F0F4")

# ---------- Styles ----------
styles = getSampleStyleSheet()

H1 = ParagraphStyle("H1", parent=styles["Heading1"], fontName=H_FONT,
                    fontSize=32, leading=38, textColor=INK_DEEP,
                    spaceAfter=10, spaceBefore=0)
H2 = ParagraphStyle("H2", parent=styles["Heading2"], fontName=H_FONT,
                    fontSize=20, leading=26, textColor=INK_DEEP,
                    spaceAfter=8, spaceBefore=18)
H3 = ParagraphStyle("H3", parent=styles["Heading3"], fontName=H_FONT,
                    fontSize=14, leading=20, textColor=BRAND_BLUE,
                    spaceAfter=6, spaceBefore=12)
BODY = ParagraphStyle("Body", parent=styles["BodyText"], fontName=R_FONT,
                      fontSize=10.5, leading=16, textColor=INK_BODY,
                      spaceAfter=6, alignment=TA_LEFT)
LEAD = ParagraphStyle("Lead", parent=BODY, fontName=M_FONT,
                      fontSize=12, leading=18, textColor=INK_MUTED,
                      spaceAfter=10)
SMALL= ParagraphStyle("Small", parent=BODY, fontName=R_FONT,
                      fontSize=9, leading=13, textColor=INK_FAINT)
CAP  = ParagraphStyle("Caption", parent=BODY, fontName=M_FONT,
                      fontSize=9, leading=12, textColor=INK_FAINT,
                      spaceAfter=4)
CODE = ParagraphStyle("Code", parent=BODY, fontName=MONO,
                      fontSize=8.5, leading=12, textColor=INK_BODY,
                      spaceAfter=8, leftIndent=12, rightIndent=12,
                      backColor=SURFACE_ALT, borderPadding=8)
BULLET = ParagraphStyle("Bullet", parent=BODY, leftIndent=14,
                        bulletIndent=2, spaceAfter=3)


# ---------- Helpers ----------
def hr(width: float = 165 * mm, color=SURFACE_ALT, thickness: float = 0.5):
    """Horizontal rule using a thin Table; works inside flowables."""
    t = Table([[""]], colWidths=[width], rowHeights=[0.1])
    t.setStyle(TableStyle([("LINEABOVE", (0, 0), (-1, 0), thickness, color)]))
    return t


def palette_table() -> Table:
    swatches = [
        ("Brand Blue",    "#0D47FF", "Primary seed (joulie_brand_blue)", "AAA on white"),
        ("Deep Blue",     "#0A2FB0", "Gradient anchor; container backdrops", "AAA on white"),
        ("Teal",          "#00C2D1", "Secondary seed; AC chart series",  "AA  on white"),
        ("Spark Green",   "#A6F43C", "Success / accent; SPS-Lab credit", "Use on dark only"),
        ("Energy Yellow", "#FFD54D", "Tertiary seed; DC chart series",   "Use on dark only"),
        ("Ink Deep",      "#0A1B5E", "Wordmark on light surfaces",       "AAA on white"),
        ("On Surface",    "#1A1C1E", "Default body text",                "AAA on white"),
        ("Surface Light", "#FDFCFF", "Light theme background",           "n/a"),
        ("Surface Dark",  "#1A1C1E", "Dark theme background",            "n/a"),
    ]
    rows = [["", "Token", "Hex", "Role", "Contrast"]]
    for name, hexv, role, contrast in swatches:
        rows.append(["", name, hexv, role, contrast])

    t = Table(rows,
              colWidths=[16 * mm, 35 * mm, 22 * mm, 70 * mm, 28 * mm])
    style = TableStyle([
        ("FONT", (0, 0), (-1, 0), H_FONT, 9),
        ("TEXTCOLOR", (0, 0), (-1, 0), INK_DEEP),
        ("FONT", (1, 1), (-1, -1), R_FONT, 9),
        ("TEXTCOLOR", (1, 1), (-1, -1), INK_BODY),
        ("FONT", (2, 1), (2, -1), MONO, 9),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("LINEBELOW", (0, 0), (-1, 0), 0.5, INK_FAINT),
        ("LINEBELOW", (0, 1), (-1, -1), 0.25, SURFACE_ALT),
    ])
    for i, (_, hexv, *_rest) in enumerate(swatches, start=1):
        style.add("BACKGROUND", (0, i), (0, i), HexColor(hexv))
    t.setStyle(style)
    return t


def two_image_row(left: str, right: str, *, height_mm: float = 50,
                  caption_left: str = "", caption_right: str = "") -> Table:
    L = Image(left,  width=85 * mm, height=height_mm * mm, kind="proportional")
    R = Image(right, width=85 * mm, height=height_mm * mm, kind="proportional")
    rows = [[L, R]]
    if caption_left or caption_right:
        rows.append([Paragraph(caption_left,  CAP),
                     Paragraph(caption_right, CAP)])
    t = Table(rows, colWidths=[88 * mm, 88 * mm])
    t.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 0),
        ("RIGHTPADDING", (0, 0), (-1, -1), 0),
        ("TOPPADDING", (0, 0), (-1, -1), 0),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]))
    return t


def code_block(text: str) -> Preformatted:
    return Preformatted(text, CODE)


def file_table(rows: list[tuple[str, str, str]]) -> Table:
    """Three-column file inventory: path | type | description."""
    # CJK wordWrap lets long mono paths break at any character so they don't overflow.
    path_style = ParagraphStyle("path_cell", parent=SMALL, fontName=MONO, fontSize=8,
                                leading=10, wordWrap="CJK")
    cell_style = ParagraphStyle("file_cell", parent=SMALL, leading=11)
    head_style = ParagraphStyle("file_head", parent=SMALL, fontName=H_FONT, leading=11, textColor=INK_DEEP)
    header = [Paragraph("Path in repo", head_style),
              Paragraph("Format", head_style),
              Paragraph("Description", head_style)]
    body_rows = [header]
    for path, fmt, desc in rows:
        body_rows.append([
            Paragraph(path, path_style),
            Paragraph(fmt,  cell_style),
            Paragraph(desc, cell_style),
        ])
    t = Table(body_rows, colWidths=[82 * mm, 20 * mm, 70 * mm], repeatRows=1)
    t.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
        ("LEFTPADDING", (0, 0), (-1, -1), 4),
        ("RIGHTPADDING", (0, 0), (-1, -1), 4),
        ("LINEBELOW", (0, 0), (-1, 0), 0.5, INK_FAINT),
        ("LINEBELOW", (0, 1), (-1, -1), 0.25, SURFACE_ALT),
    ]))
    return t


def bullets(items: list[str]) -> list[Paragraph]:
    return [Paragraph(f"• {item}", BULLET) for item in items]


# ---------- Page template ----------
def on_page(canvas, doc):
    canvas.saveState()
    # Footer
    canvas.setFont(R_FONT, 8)
    canvas.setFillColor(INK_FAINT)
    canvas.drawString(20 * mm, 12 * mm,
                      "Joulie Brand Guide v1.0 · SPS-Lab · Cyprus University of Technology")
    canvas.drawRightString(190 * mm, 12 * mm, f"Page {doc.page}")
    # Header rule
    canvas.setStrokeColor(SURFACE_ALT)
    canvas.setLineWidth(0.5)
    canvas.line(20 * mm, 282 * mm, 190 * mm, 282 * mm)
    canvas.restoreState()


def build():
    doc = BaseDocTemplate(
        OUT, pagesize=A4,
        leftMargin=20 * mm, rightMargin=20 * mm,
        topMargin=22 * mm, bottomMargin=20 * mm,
        title="Joulie Brand Guide",
        author="Perplexity Computer",
    )
    frame = Frame(doc.leftMargin, doc.bottomMargin,
                  doc.width, doc.height, id="body")
    doc.addPageTemplates([PageTemplate(id="main", frames=[frame], onPage=on_page)])

    story: list = []

    # ===== Cover =====
    story.append(Spacer(1, 8 * mm))
    cover = Image(f"{ROOT}/logos/joulie_logo_white_bg.png",
                  width=170 * mm, height=57 * mm, kind="proportional")
    story.append(cover)
    story.append(Spacer(1, 12 * mm))
    story.append(Paragraph("Brand Guide", H1))
    story.append(Paragraph(
        "Logo, palette, typography, voice, and exact code-update instructions "
        "for the Joulie EV charging tracker by SPS-Lab.", LEAD))
    story.append(Spacer(1, 6 * mm))
    story.append(hr())
    story.append(Spacer(1, 4 * mm))
    story.append(Paragraph(
        "<b>Repository:</b> https://github.com/SPS-L/joulie<br/>"
        "<b>Maintainer:</b> Sustainable Power Systems Lab · Cyprus University of Technology<br/>"
        "<b>Audience:</b> Anyone modifying app icons, colours, logos, or first-impression screens.",
        BODY))

    story.append(PageBreak())

    # ===== 1. Brand strategy =====
    story.append(Paragraph("1 · Brand strategy", H2))
    story.append(Paragraph(
        "Joulie helps EV drivers understand how their car uses energy. "
        "The brand is the visual promise that the answer is precise, "
        "private, and free of marketing noise.", LEAD))

    story.append(Paragraph("Personality", H3))
    for line in bullets([
        "<b>Precise.</b> The product computes kWh, km, gCO₂. The brand looks like an instrument, not a billboard.",
        "<b>Energetic but composed.</b> A single bolt does the talking. Nothing flashes, sparkles, or pulses unprompted.",
        "<b>Open and academic.</b> Built by a lab, GPL-licensed, no telemetry. The SPS-Lab credit is a feature, not a footer.",
        "<b>European.</b> Defaults to km/kWh, EUR, and the Cyprus 2025 grid intensity. The palette leans cool and contemporary.",
    ]):
        story.append(line)

    story.append(Paragraph("Voice", H3))
    for line in bullets([
        "Plain English. Greek users get plain Greek; Turkish users get plain Turkish. No jargon for its own sake.",
        "Imperative tagline: <i>Log every charge. Understand every kilometre.</i>",
        "Numbers always have units. <font name='%s'>kWh</font>, <font name='%s'>km/kWh</font>, <font name='%s'>gCO₂/kWh</font>." % (MONO, MONO, MONO),
        "Never use the words <i>scrape</i>, <i>crawl</i>, or marketing fluff like <i>revolutionise</i>. Never use the em-dash.",
    ]):
        story.append(line)

    story.append(PageBreak())

    # ===== 2. Logo =====
    story.append(Paragraph("2 · Logo", H2))
    story.append(Paragraph(
        "The mark is a stylised lowercase <i>j</i> drawn as a lightning bolt. "
        "The <i>J</i> anchors the brand name; the bolt encodes the product (energy "
        "logging). It sits inside a squircle tile filled with a brand-blue "
        "to deep-blue gradient, so it reads identically on the launcher, "
        "splash, and any other surface.", BODY))

    story.append(Spacer(1, 4 * mm))
    story.append(two_image_row(
        f"{ROOT}/logos/joulie_logo_white_bg.png",
        f"{ROOT}/logos/joulie_logo_dark_bg.png",
        height_mm=50,
        caption_left="Light surface lockup · joulie_logo_light.png",
        caption_right="Dark surface lockup · joulie_logo_dark.png",
    ))

    story.append(Paragraph("Variants", H3))
    story.append(Paragraph(
        "Always pick the variant that matches the surface luminance. Never invert.", BODY))
    for line in bullets([
        "<b>Full colour.</b> Default. Use on photography, brand-blue surfaces, or any neutral surface.",
        "<b>Light surface lockup.</b> Wordmark in <font name='%s'>#0A1B5E</font> (Ink Deep). Use on white or off-white." % MONO,
        "<b>Dark surface lockup.</b> Wordmark in <font name='%s'>#FFFFFF</font>. Use on Surface Dark or solid brand-blue." % MONO,
        "<b>Monochrome.</b> Single-fill SVG. Used by Android 13+ themed icons; the OS tints it to match the user's wallpaper accent.",
    ]):
        story.append(line)

    story.append(Paragraph("Clear space &amp; minimum size", H3))
    for line in bullets([
        "Clear space = the height of the bolt's crossbar on every side.",
        "Minimum size on screen: 24 dp tall (the launcher and notification icons).",
        "Minimum size in print: 9 mm tall (do not place the wordmark below this).",
    ]):
        story.append(line)

    story.append(Paragraph("Misuse", H3))
    for line in bullets([
        "Don't recolour the bolt; the gradient is the spark.",
        "Don't place the full-colour mark on a busy photo without the squircle tile.",
        "Don't replace the wordmark with a different typeface.",
        "Don't use the SPS-Lab pill badge as a substitute for the Joulie logo.",
    ]):
        story.append(line)

    story.append(PageBreak())

    # ===== 3. Palette =====
    story.append(Paragraph("3 · Palette", H2))
    story.append(Paragraph(
        "Material 3 seed colours, unchanged from the existing app palette so "
        "the rebrand is fully backwards-compatible. Two new tokens are added: "
        "<b>joulie_ink_deep</b> for wordmark text on light surfaces, and "
        "<b>joulie_spark_green</b> for the SPS-Lab credit and success accents.",
        BODY))
    story.append(Spacer(1, 4 * mm))
    story.append(palette_table())
    story.append(Spacer(1, 4 * mm))
    story.append(Image(f"{ROOT}/palette/brand_palette.png",
                       width=170 * mm, height=95 * mm, kind="proportional"))

    story.append(PageBreak())

    # ===== 4. Typography =====
    story.append(Paragraph("4 · Typography", H2))
    story.append(Paragraph(
        "Joulie ships with the Android system stack. The Material 3 type scale "
        "(textAppearanceHeadlineSmall, titleMedium, bodyMedium, etc.) is the "
        "single source of truth in-app. The wordmark in the rendered logo PNGs "
        "uses Noto Sans Display Black for visual punch. Never re-set the "
        "wordmark in another font.", BODY))

    story.append(Paragraph("In-app type roles", H3))
    type_roles = [
        ("Hero / page title", "textAppearanceHeadlineSmall", "24 sp · 400 wt", "Dashboard title, About header, Wizard subtitle"),
        ("Section title",     "textAppearanceTitleMedium",   "16 sp · 500 wt · primary tint", "Card section headers"),
        ("Body",              "textAppearanceBodyMedium",    "14 sp · 400 wt · onSurface",    "Paragraphs, list rows"),
        ("Label",             "textAppearanceLabelMedium",   "12 sp · 500 wt · primary",      "Field labels, chip text"),
        ("Caption / footer",  "textAppearanceBodySmall",     "12 sp · 400 wt · onSurfaceVariant", "Timestamps, hints"),
    ]
    # Cell styles that allow wrapping inside table cells.
    cell_h   = ParagraphStyle("cell_h",   parent=BODY, fontName=H_FONT, fontSize=9, textColor=INK_DEEP, leading=11, spaceAfter=0)
    cell_b   = ParagraphStyle("cell_b",   parent=BODY, fontName=R_FONT, fontSize=9,  leading=11, spaceAfter=0)
    cell_m   = ParagraphStyle("cell_m",   parent=BODY, fontName=MONO,   fontSize=8,  leading=10, spaceAfter=0)
    rows = [[Paragraph(c, cell_h) for c in ("Role", "M3 attribute", "Spec", "Where")]]
    for role, attr, spec, where in type_roles:
        rows.append([
            Paragraph(role,  cell_b),
            Paragraph(attr,  cell_m),
            Paragraph(spec,  cell_b),
            Paragraph(where, cell_b),
        ])
    t = Table(rows, colWidths=[32*mm, 55*mm, 40*mm, 43*mm])
    t.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("LEFTPADDING", (0, 0), (-1, -1), 4),
        ("RIGHTPADDING", (0, 0), (-1, -1), 4),
        ("LINEBELOW", (0, 0), (-1, 0), 0.5, INK_FAINT),
        ("LINEBELOW", (0, 1), (-1, -1), 0.25, SURFACE_ALT),
    ]))
    story.append(t)

    story.append(PageBreak())

    # ===== 5. App icon =====
    story.append(Paragraph("5 · App icon", H2))
    story.append(Paragraph(
        "Three layers, each rendered at the five Android densities "
        "(mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi):", BODY))
    for line in bullets([
        "<b>Background.</b> Solid <font name='%s'>@color/joulie_brand_blue</font> tile (already in colors.xml)." % MONO,
        "<b>Foreground.</b> Gradient bolt-J PNG with a subtle drop shadow and crossbar highlight.",
        "<b>Monochrome.</b> Single-fill vector for Android 13+ themed icons. NEW.",
    ]):
        story.append(line)
    story.append(Spacer(1, 4 * mm))
    story.append(Image(f"{ROOT}/play-store/ic_launcher_512.png",
                       width=70 * mm, height=70 * mm, kind="proportional"))
    story.append(Paragraph("Above: 512×512 Play Store icon · ic_launcher_512.png", CAP))

    story.append(Paragraph("Notification stat icon", H3))
    story.append(Paragraph(
        "Pure-white bolt on transparent, rendered at 24 dp for each density "
        "and dropped into <font name='%s'>res/drawable-*dpi/ic_stat_joulie.png</font>. "
        "The Android system tints it to match the notification surface; never bake colour into this asset." % MONO,
        BODY))

    story.append(PageBreak())

    # ===== 6. Hero artwork & screen redesigns =====
    story.append(Paragraph("6 · Hero artwork &amp; screen redesigns", H2))
    story.append(Paragraph(
        "The wizard welcome page and the About screen are the two screens "
        "that decide whether Joulie feels like a hobby project or a real "
        "product. Both now lead with a branded hero panel.", BODY))

    story.append(Paragraph("Wizard welcome (page 1)", H3))
    story.append(Image(f"{ROOT}/hero/wizard_hero_light.png",
                       width=80 * mm, height=72 * mm, kind="proportional"))
    story.append(Paragraph(
        "Top 52% of the page is the gradient hero (mark + wordmark + tagline, "
        "baked into a single PNG so it scales crisply at any density). "
        "The lower 48% is a white surface card with rounded top corners that "
        "tucks behind the hero, giving the welcome screen a depth and "
        "rhythm that the previous all-text version lacked.", BODY))

    story.append(Paragraph("About screen", H3))
    story.append(Image(f"{ROOT}/hero/about_hero_light.png",
                       width=170 * mm, height=51 * mm, kind="proportional"))
    story.append(Paragraph(
        "240-dp <font name='%s'>CollapsingToolbarLayout</font> with the gradient banner "
        "as the parallax background. As the user scrolls, the title collapses "
        "into the toolbar over the brand-blue scrim. The cards below now use "
        "the M3 <i>filled</i> card style with a 20-dp corner radius and zero "
        "elevation, replacing the harder 16-dp outlined cards." % MONO, BODY))

    story.append(PageBreak())

    # ===== 7. Asset inventory =====
    story.append(Paragraph("7 · Asset inventory", H2))
    story.append(Paragraph(
        "Everything below is delivered as static files in the <b>code_dropins/</b> "
        "folder, structured to mirror the repo's <font name='%s'>app/src/main/</font> tree. "
        "Copy each file to the matching path in the repo (no rename needed)." % MONO,
        BODY))
    story.append(Spacer(1, 3 * mm))
    story.append(file_table([
        ("res/mipmap-*/ic_launcher.png",            "PNG (5 densities)", "Pre-Android-O launcher (square)."),
        ("res/mipmap-*/ic_launcher_round.png",      "PNG (5 densities)", "Pre-Android-O launcher (round)."),
        ("res/mipmap-*/ic_launcher_foreground.png", "PNG (5 densities)", "Adaptive-icon foreground (gradient bolt)."),
        ("res/mipmap-anydpi-v26/ic_launcher.xml",   "XML",               "Adaptive icon. Adds &lt;monochrome&gt; layer."),
        ("res/mipmap-anydpi-v26/ic_launcher_round.xml", "XML",           "Round adaptive icon. Adds &lt;monochrome&gt; layer."),
        ("res/drawable/ic_launcher_monochrome.xml", "Vector",            "Themed-icon source for Android 13+."),
        ("res/drawable/ic_splash_bolt.xml",         "Vector",            "Splash-screen bolt icon."),
        ("res/drawable/bg_brand_gradient.xml",      "Vector",            "135° brand-blue gradient (fallback hero bg)."),
        ("res/drawable/about_hero.png",             "PNG",               "About-screen hero banner (light)."),
        ("res/drawable-night/about_hero.png",       "PNG",               "About-screen hero banner (dark)."),
        ("res/drawable/wizard_hero.png",            "PNG",               "Wizard welcome hero (light)."),
        ("res/drawable-night/wizard_hero.png",      "PNG",               "Wizard welcome hero (dark)."),
        ("res/drawable/ic_spslab_badge.xml",        "Vector",            "SPS-Lab pill badge. Bolt recoloured to FFD54D."),
        ("res/drawable-*dpi/ic_stat_joulie.png",    "PNG (5 densities)", "Notification status-bar icon (replaces existing)."),
        ("res/layout/fragment_about.xml",           "Layout",            "Redesigned About screen with collapsing hero."),
        ("res/layout/fragment_wizard_page1.xml",    "Layout",            "Redesigned wizard welcome page."),
        ("res/values/colors_added.xml",             "Patch",             "Two new tokens. Merge into colors.xml."),
        ("res/values/strings_added.xml",            "Patch",             "wizard_page1_intro_title. Merge into strings.xml."),
        ("res/values/themes_splash_patch.xml",      "Patch",             "Splash style. Replace in themes.xml."),
        ("docs/branding/joulie_logo_light.png",     "PNG",               "README hero (light)."),
        ("docs/branding/joulie_logo_dark.png",      "PNG",               "README hero (dark)."),
        ("docs/branding/play-store/ic_launcher_512.png", "PNG",          "Play Store listing icon."),
        ("docs/branding/play-store/feature_graphic.png", "PNG",          "Play Store feature graphic (light)."),
        ("docs/branding/play-store/feature_graphic_dark.png", "PNG",     "Play Store feature graphic (dark)."),
    ]))

    story.append(PageBreak())

    # ===== 8. Step-by-step update instructions =====
    story.append(Paragraph("8 · Step-by-step update instructions", H2))
    story.append(Paragraph(
        "Apply these in order. Each step is independent and reversible (git revert).",
        BODY))

    story.append(Paragraph("Step 1 · drop in the new assets", H3))
    story.append(Paragraph(
        "From the brand pack root, copy <b>code_dropins/res/</b> over <b>app/src/main/res/</b>. "
        "Existing files of the same name are intentionally overwritten; that is the rebrand.", BODY))
    story.append(code_block(
        "rsync -av code_dropins/res/  app/src/main/res/\n"
        "# Or, if you prefer cp:\n"
        "cp -r code_dropins/res/* app/src/main/res/"))

    story.append(Paragraph("Step 2 · merge the colour patch", H3))
    story.append(Paragraph(
        "Open <font name='%s'>app/src/main/res/values/colors.xml</font> and append the two new tokens "
        "from <font name='%s'>colors_added.xml</font> right above the closing &lt;/resources&gt; tag:" % (MONO, MONO),
        BODY))
    story.append(code_block(
        '<color name="joulie_ink_deep">#0A1B5E</color>\n'
        '<color name="joulie_spark_green">#A6F43C</color>'))

    story.append(Paragraph("Step 3 · merge the string patch", H3))
    story.append(Paragraph(
        "Add to <font name='%s'>res/values/strings.xml</font> and translate into the three localised string files:" % MONO,
        BODY))
    story.append(code_block(
        '<string name="wizard_page1_intro_title">Welcome to Joulie</string>'))
    story.append(Paragraph("Suggested translations:", BODY))
    story.append(code_block(
        'values-el/strings.xml   →  Καλώς ήρθες στο Joulie\n'
        'values-tr/strings.xml   →  Joulie\'ye hoş geldin\n'
        'values-ru/strings.xml   →  Добро пожаловать в Joulie'))

    story.append(Paragraph("Step 4 · replace the splash style", H3))
    story.append(Paragraph(
        "In <font name='%s'>res/values/themes.xml</font>, replace the existing "
        "<b>Theme.EVTracker.SplashScreen</b> style with the contents of "
        "<font name='%s'>themes_splash_patch.xml</font>. The new style points to "
        "<font name='%s'>@drawable/ic_splash_bolt</font> on the brand-blue background." % (MONO, MONO, MONO),
        BODY))

    story.append(Paragraph("Step 5 · opt into the splash icon background", H3))
    story.append(Paragraph(
        "If you want the splash to use the brand-blue tile (recommended, "
        "matches the launcher), keep <font name='%s'>windowSplashScreenBackground=@color/joulie_brand_blue</font>. "
        "If you prefer the splash to flip with the system theme, change it to "
        "<font name='%s'>?android:colorBackground</font> (white in light mode, near-black in dark)." % (MONO, MONO),
        BODY))

    story.append(Paragraph("Step 6 · verify the layouts compile", H3))
    story.append(Paragraph(
        "Both replaced layouts use only existing IDs and existing string IDs "
        "(plus the one new string from step 3). The matching ViewModels and "
        "Fragments need no Kotlin changes; verified against the current "
        "<font name='%s'>AboutFragment.kt</font> and <font name='%s'>WizardPage1Fragment.kt</font>." % (MONO, MONO),
        BODY))
    story.append(code_block("./gradlew :app:assembleDebug"))

    story.append(Paragraph("Step 7 · Play Store listing", H3))
    for line in bullets([
        "Replace the listing icon with <font name='%s'>play-store/ic_launcher_512.png</font>." % MONO,
        "Replace the feature graphic with <font name='%s'>play-store/feature_graphic.png</font> (or _dark for the dark variant)." % MONO,
        "Refresh the listing screenshots once the new About + Wizard surfaces are live; the existing screenshots will look out-of-brand against the new icon.",
    ]):
        story.append(line)

    story.append(Paragraph("Step 8 · README", H3))
    story.append(Paragraph(
        "Replace the README hero block with the dual-source picture so it "
        "auto-switches in GitHub light vs. dark mode:", BODY))
    story.append(code_block(
        '<p align="center">\n'
        '  <picture>\n'
        '    <source media="(prefers-color-scheme: dark)"\n'
        '            srcset="docs/branding/joulie_logo_dark.png">\n'
        '    <img alt="Joulie. Log every charge. Understand every kilometre."\n'
        '         src="docs/branding/joulie_logo_light.png" width="520" />\n'
        '  </picture>\n'
        '</p>'))

    story.append(PageBreak())

    # ===== 9. Light vs. dark guidance =====
    story.append(Paragraph("9 · Light vs. dark · the rules", H2))
    story.append(Paragraph(
        "The full-colour mark sits on its own gradient tile, so it works "
        "identically in both modes. Everything else has an explicit "
        "light / dark variant.", BODY))
    rows = [
        ["Surface",                "Light mode asset",                       "Dark mode asset"],
        ["Launcher icon",          "ic_launcher (brand-blue tile)",          "Same. OS owns the surface."],
        ["Themed icon (Android 13+)", "ic_launcher_monochrome (tinted by OS)", "Same. OS owns the surface."],
        ["Splash",                 "Brand-blue tile + white bolt",           "Same. Matches launcher."],
        ["About hero",             "drawable/about_hero.png",                "drawable-night/about_hero.png"],
        ["Wizard hero",            "drawable/wizard_hero.png",               "drawable-night/wizard_hero.png"],
        ["README / GitHub",        "joulie_logo_light.png",                  "joulie_logo_dark.png (auto via <picture>)"],
        ["Wordmark colour",        "#0A1B5E (Ink Deep)",                     "#FFFFFF"],
        ["Tagline colour",         "#43474E (onSurfaceVariant)",             "#C3C7CF (onSurfaceVariant, dark)"],
        ["Notification icon",      "ic_stat_joulie.png · single asset, OS tints it", "Same"],
    ]
    t = Table(rows, colWidths=[42*mm, 64*mm, 64*mm])
    t.setStyle(TableStyle([
        ("FONT", (0, 0), (-1, 0), H_FONT, 9),
        ("TEXTCOLOR", (0, 0), (-1, 0), INK_DEEP),
        ("FONT", (0, 1), (-1, -1), R_FONT, 9),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("LINEBELOW", (0, 0), (-1, 0), 0.5, INK_FAINT),
        ("LINEBELOW", (0, 1), (-1, -1), 0.25, SURFACE_ALT),
    ]))
    story.append(t)

    # ===== 10. Acceptance checklist =====
    story.append(Paragraph("10 · Acceptance checklist", H2))
    for line in bullets([
        "Launcher icon shows the bolt on brand blue at every density (check Settings &gt; Apps &gt; Joulie).",
        "Long-press the icon, enable Themed icons, the bolt is tinted by the wallpaper accent (Android 13+).",
        "First launch shows brand-blue splash with the white bolt, then the wizard welcome with the gradient hero.",
        "About screen header collapses smoothly; SPS-Lab credit visible inside the gradient banner.",
        "Toggle system theme to dark. Both heroes and the wordmark switch correctly.",
        "Notification (trigger a Drive backup failure) shows the white bolt status-bar icon.",
        "Play Store listing preview uses the new 512 icon and feature graphic; no orange remnants from the legacy SPS-Lab badge.",
    ]):
        story.append(line)

    story.append(Spacer(1, 8 * mm))
    story.append(hr())
    story.append(Spacer(1, 4 * mm))
    story.append(Paragraph(
        "Questions? File an issue on github.com/SPS-L/joulie or contact the "
        "Sustainable Power Systems Lab.", SMALL))

    doc.build(story)
    print(f"Wrote {OUT}")


if __name__ == "__main__":
    build()
