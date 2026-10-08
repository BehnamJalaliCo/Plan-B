#!/usr/bin/env python3
"""Builds the promotional Cafe Bazaar screenshots: a headline card over a brand gradient and a
real app screenshot in a phone frame.

Output: store/cafebazaar/graphics/promo/fa/NN_<screen>.png (1080×1920).

Requires Pillow with libraqm (Persian shaping) and the licensed app typeface (docs/FONTS.md).
Screenshots come from the Roborazzi suite (artifacts/screenshots).
"""
import math
import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont, features

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOTS = os.path.join(ROOT, "artifacts", "screenshots")
GRAPHICS = os.path.join(ROOT, "store", "cafebazaar", "graphics")
OUT = os.path.join(GRAPHICS, "promo", "fa")
FONTS = os.environ.get("PLANB_FONTS_DIR") or os.path.join(ROOT, "private-fonts")
FONT_FILES = {"medium": "AnjomanMax-Medium.ttf", "bold": "AnjomanMax-Bold.ttf", "semibold": "AnjomanMax-SemiBold.ttf"}

W, H = 1080, 1920
INK = (46, 36, 112)        # lavender "on container" (Accents.kt)
ACCENT = (200, 62, 122)    # rose highlight
SUB = (92, 84, 140)

# Background gradients (top-left → bottom-right) drawn from the app's lavender family.
THEMES = [
    ((72, 52, 170), (128, 74, 196)),
    ((58, 44, 140), (104, 86, 214)),
    ((98, 58, 176), (176, 72, 150)),
]

# (folder, screen, first headline line, highlighted second line, subtitle, tilt in degrees)
SLIDES = [
    ("today", "today", "همهٔ روزت", "در یک نگاه", "کارها، رویدادها، عادت‌ها و تمرکز در صفحهٔ امروز", 0),
    ("capture", "quick_capture_smart", "فارسی بنویس،", "خودش می‌فهمد", "«فردا ساعت ۵ عصر جلسه» خودش کاری با تاریخ و ساعت می‌شود", -6),
    ("calendar", "calendar_holidays_month", "تقویم شمسی", "با تعطیلات رسمی", "تاریخ قمری، مناسبت‌ها و همگام‌سازی با تقویم گوشی", 6),
    ("calendar", "calendar_time_blocking", "روزت را", "ساعت‌به‌ساعت بچین", "بلوک‌بندی زمان با کشیدن و رها کردن و چیدن خودکار روز", 0),
    ("notebooks", "note_rich", "یادداشتی", "فراتر از متن", "عکس، جدول، طراحی، اسکن و صدا در یک یادداشت", -6),
    ("assistant", "assistant_chat", "دستیار", "هوش مصنوعی", "از برنامه‌ات بپرس، روزت را بچین و یادداشت‌ها را خلاصه کن", 6),
    ("focus", "focus_pro_running", "تمرکز عمیق،", "بدون مزاحمت", "صداهای محیطی آرام و حالت «مزاحم نشوید» در جلسهٔ تمرکز", 0),
    ("habits", "habit_stats", "عادت‌های", "ماندگار بساز", "زنجیرهٔ روزها، آمار سالانه، چالش‌ها و نشان‌ها", -6),
    ("notebooks", "journal", "دفتر روزانه", "و تقویم حال", "هر روز بنویس و حالت را ببین؛ همه چیز فقط روی گوشی خودت", 6),
]


def font(weight, size):
    return ImageFont.truetype(os.path.join(FONTS, FONT_FILES[weight]), size, layout_engine=ImageFont.Layout.RAQM)


def background(theme):
    start, end = THEMES[theme]
    # A diagonal gradient computed at low resolution and scaled up (smooth and fast).
    small = Image.new("RGB", (W // 8, H // 8))
    px = small.load()
    for y in range(small.height):
        for x in range(small.width):
            t = min(1.0, max(0.0, x / small.width * 0.45 + y / small.height * 0.55))
            px[x, y] = tuple(int(start[i] + (end[i] - start[i]) * t) for i in range(3))
    img = small.resize((W, H), Image.BICUBIC).convert("RGBA")
    # Soft blobs and a light arc, as in the brand cover.
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(glow)
    d.ellipse((-380, 980, 520, 1880), fill=(255, 255, 255, 26))
    d.ellipse((640, -260, 1400, 500), fill=(255, 255, 255, 22))
    d.arc((-600, 520, 1700, 1900), 200, 330, fill=(255, 255, 255, 60), width=4)
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(6)))
    return img


def wrap(text, fnt, width):
    """One line if it fits, otherwise the two-line split with the most even widths."""
    if fnt.getlength(text, direction="rtl") <= width:
        return [text]
    words = text.split(" ")
    best = None
    for i in range(1, len(words)):
        a, b = " ".join(words[:i]), " ".join(words[i:])
        widest = max(fnt.getlength(a, direction="rtl"), fnt.getlength(b, direction="rtl"))
        if words[i - 1] == "و":  # never leave "and" dangling at the end of a line
            widest += width
        if best is None or widest < best[0]:
            best = (widest, [a, b])
    return best[1]


def card(line1, line2, subtitle):
    width, pad = 940, 64
    big, small = font("bold", 92), font("medium", 42)
    sub_lines = wrap(subtitle, small, width - 2 * pad)
    height = pad + 108 + 116 + 26 + len(sub_lines) * 64 + pad - 10
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    ImageDraw.Draw(img).rounded_rectangle((0, 0, width - 1, height - 1), 84, fill=(255, 255, 255, 255))
    d = ImageDraw.Draw(img)
    y = pad
    d.text((width / 2, y), line1, font=big, fill=INK, anchor="ma", direction="rtl")
    y += 116
    d.text((width / 2, y), line2, font=big, fill=ACCENT, anchor="ma", direction="rtl")
    y += 108 + 26
    for line in sub_lines:
        d.text((width / 2, y), line, font=small, fill=SUB, anchor="ma", direction="rtl")
        y += 64
    return img


def phone(path, width):
    shot = Image.open(path).convert("RGB")
    screen_w = width - 2 * 22
    screen_h = int(shot.height * screen_w / shot.width)
    shot = shot.resize((screen_w, screen_h), Image.LANCZOS)
    body = Image.new("RGBA", (width + 12, screen_h + 44), (0, 0, 0, 0))
    d = ImageDraw.Draw(body)
    # Side buttons, then the body with a thin metallic rim, then the screen.
    d.rounded_rectangle((width + 2, 330, width + 11, 450), 4, fill=(70, 70, 82, 255))
    d.rounded_rectangle((width + 2, 500, width + 11, 700), 4, fill=(70, 70, 82, 255))
    d.rounded_rectangle((0, 0, width - 1, screen_h + 43), 96, fill=(150, 150, 164, 255))
    d.rounded_rectangle((4, 4, width - 5, screen_h + 39), 92, fill=(18, 18, 24, 255))
    mask = Image.new("L", shot.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, screen_w - 1, screen_h - 1), 74, fill=255)
    body.paste(shot, (22, 22), mask)
    # Camera hole.
    d.ellipse((width / 2 - 13, 40, width / 2 + 13, 66), fill=(10, 10, 14, 255))
    return body


def slide(index, folder, screen, line1, line2, subtitle, tilt):
    img = background(index % len(THEMES))

    device = phone(os.path.join(SHOTS, folder, f"{screen}_fa_light.png"), 700)
    device = device.rotate(tilt, resample=Image.BICUBIC, expand=True)
    x = (W - device.width) // 2
    y = 760 - int(abs(math.sin(math.radians(tilt))) * 140)
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    alpha = device.split()[3].point(lambda a: int(a * 0.45))
    shadow_layer = Image.new("RGBA", device.size, (20, 10, 60, 255))
    shadow_layer.putalpha(alpha)
    shadow.alpha_composite(shadow_layer, (x + 18, y + 34))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(26)))
    img.alpha_composite(device, (x, y))

    # The card sits over the top of the phone.
    c = card(line1, line2, subtitle)
    card_shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(card_shadow).rounded_rectangle(
        ((W - c.width) // 2 + 6, 214, (W + c.width) // 2 + 6, 200 + c.height + 20), 84, fill=(20, 10, 60, 70))
    img.alpha_composite(card_shadow.filter(ImageFilter.GaussianBlur(22)))
    img.alpha_composite(c, ((W - c.width) // 2, 196))

    # Brand mark above the card.
    icon = Image.open(os.path.join(GRAPHICS, "icon-512.png")).convert("RGBA").resize((76, 76), Image.LANCZOS)
    m = Image.new("L", icon.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, 75, 75), 20, fill=255)
    icon.putalpha(ImageChops.multiply(icon.split()[3], m))
    d = ImageDraw.Draw(img)
    label = font("bold", 46)
    text_w = label.getlength("Plan-B")
    total = icon.width + 18 + text_w
    left = (W - total) / 2
    img.alpha_composite(icon, (int(left + text_w + 18), 74))
    d.text((left, 112), "Plan-B", font=label, fill="white", anchor="lm")

    name = f"{index + 1:02d}_{screen}.png"
    img.convert("RGB").save(os.path.join(OUT, name), optimize=True)
    return name


def main():
    if not features.check("raqm"):
        raise SystemExit("Pillow needs libraqm for Persian text shaping")
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        if f.endswith(".png"):
            os.remove(os.path.join(OUT, f))
    for i, s in enumerate(SLIDES):
        print(slide(i, *s))


if __name__ == "__main__":
    main()
