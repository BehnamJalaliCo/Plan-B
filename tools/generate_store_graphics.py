#!/usr/bin/env python3
"""Builds the Cafe Bazaar listing graphics from the shipped artwork and real screenshots.

Outputs (store/cafebazaar/graphics/):
  cover_fa.png, cover_en.png   1024×500 cover images
  screenshots/<lang>/NN_<screen>.png   phone screenshots in listing order

Requires Pillow with libraqm (for Persian shaping). The 512×512 icon (icon-512.png) is rendered
from the launcher icon by AppIconTest; screenshots come from the Roborazzi suite
(artifacts/screenshots). Run after `./gradlew recordRoborazziDebug`.
"""
import os
import shutil

from PIL import Image, ImageDraw, ImageFilter, ImageFont, features

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOTS = os.path.join(ROOT, "artifacts", "screenshots")
OUT = os.path.join(ROOT, "store", "cafebazaar", "graphics")
# The licensed app typeface (never committed; see docs/FONTS.md).
FONTS = os.environ.get("PLANB_FONTS_DIR") or os.path.join(ROOT, "private-fonts")
FONT_FILES = {"regular": "AnjomanMax-Regular.ttf", "medium": "AnjomanMax-Medium.ttf", "semibold": "AnjomanMax-SemiBold.ttf", "bold": "AnjomanMax-Bold.ttf"}

# Listing order: the first screenshots are the ones most people see.
SCREENS = [
    ("today", "today"),
    ("calendar", "calendar_holidays_month"),
    ("capture", "quick_capture_smart"),
    ("calendar", "calendar_time_blocking"),
    ("notebooks", "note_rich"),
    ("assistant", "assistant_chat"),
    ("focus", "focus_pro_running"),
    ("habits", "habit_stats"),
]

TEXT = {
    "fa": ("Plan-B", "برنامه‌ریز و دفترچهٔ آرام شما", "کارها · تقویم شمسی · یادداشت · عادت · تمرکز"),
    "en": ("Plan-B", "Your calm planner and notebook", "Tasks · Calendar · Notes · Habits · Focus"),
}


def font(weight, size):
    return ImageFont.truetype(os.path.join(FONTS, FONT_FILES[weight]), size, layout_engine=ImageFont.Layout.RAQM)


def gradient(size, start, end):
    w, h = size
    base = Image.new("RGB", size, start)
    top = Image.new("RGB", size, end)
    mask = Image.new("L", size)
    px = mask.load()
    for y in range(h):
        for x in range(w):
            px[x, y] = int(255 * (x / w * 0.6 + y / h * 0.4))
    return Image.composite(top, base, mask)


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, *img.size), radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def phone(path, height):
    shot = Image.open(path).convert("RGB")
    width = int(shot.width * height / shot.height)
    shot = shot.resize((width, height), Image.LANCZOS)
    framed = Image.new("RGBA", (width + 12, height + 12), (36, 27, 94, 255))
    framed = rounded(framed, 34)
    framed.alpha_composite(rounded(shot, 28), (6, 6))
    return framed


def cover(lang):
    w, h = 1024, 500
    img = gradient((w, h), (140, 124, 240), (91, 75, 196)).convert("RGBA")
    draw = ImageDraw.Draw(img)
    rtl = lang == "fa"
    title, tagline, features_line = TEXT[lang]

    # Two real screenshots, slightly overlapping, on the "end" side.
    a = phone(os.path.join(SHOTS, "today", f"today_{lang}_light.png"), 430)
    b = phone(os.path.join(SHOTS, "calendar", f"calendar_month_{lang}_dark.png"), 400)
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    phones_x = 70 if rtl else w - 70 - a.width - b.width + 60
    for i, (p, dx, dy) in enumerate(((b, 0, 70), (a, b.width - 60, 35)) if not rtl else ((a, 0, 35), (b, a.width - 60, 70))):
        ImageDraw.Draw(shadow).rounded_rectangle(
            (phones_x + dx + 8, dy + 14, phones_x + dx + p.width + 8, dy + p.height + 14), 34, fill=(20, 12, 60, 90))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(12)))
    order = ((b, 0, 70), (a, b.width - 60, 35)) if not rtl else ((b, a.width - 60, 70), (a, 0, 35))
    for p, dx, dy in order:
        img.alpha_composite(p, (phones_x + dx, dy))

    # Icon and texts on the "start" side.
    icon = Image.open(os.path.join(OUT, "icon-512.png")).convert("RGBA").resize((132, 132), Image.LANCZOS)
    icon = rounded(icon, 32)
    text_right = w - 60
    text_left = 60
    if rtl:
        img.alpha_composite(icon, (text_right - icon.width, 70))
        anchor_x, anchor = text_right, "ra"
    else:
        img.alpha_composite(icon, (text_left, 70))
        anchor_x, anchor = text_left, "la"
    draw.text((anchor_x, 225), title, font=font("bold", 64), fill="white", anchor=anchor)
    direction = "rtl" if rtl else "ltr"
    draw.text((anchor_x, 312), tagline, font=font("semibold", 32), fill="white", anchor=anchor, direction=direction)
    draw.text((anchor_x, 372), features_line, font=font("medium", 22), fill=(234, 230, 253), anchor=anchor, direction=direction)
    img.convert("RGB").save(os.path.join(OUT, f"cover_{lang}.png"), optimize=True)


def screenshots(lang):
    target = os.path.join(OUT, "screenshots", lang)
    shutil.rmtree(target, ignore_errors=True)
    os.makedirs(target)
    for i, (folder, screen) in enumerate(SCREENS, start=1):
        src = os.path.join(SHOTS, folder, f"{screen}_{lang}_light.png")
        shutil.copyfile(src, os.path.join(target, f"{i:02d}_{screen}.png"))


def main():
    if not features.check("raqm"):
        raise SystemExit("Pillow was built without libraqm; Persian text would not be shaped correctly.")
    os.makedirs(OUT, exist_ok=True)
    for lang in ("fa", "en"):
        cover(lang)
        screenshots(lang)
    print(f"Store graphics written to {OUT}")


if __name__ == "__main__":
    main()
