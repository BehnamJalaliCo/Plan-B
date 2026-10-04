#!/usr/bin/env python3
"""Regenerates docs/UI_GALLERY.md from the PNGs Roborazzi wrote to artifacts/screenshots/."""
import os
import re
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOTS = os.path.join(ROOT, "artifacts", "screenshots")
OUT = os.path.join(ROOT, "docs", "UI_GALLERY.md")
ORDER = ["today", "capture", "tasks", "calendar", "notebooks", "more", "projects", "habits", "goals",
         "focus", "search", "templates", "review", "settings", "onboarding", "states", "icon"]
VARIANT_ORDER = ["fa_light", "fa_dark", "en_light", "en_dark", "fa_light_font150", "en_light_font150"]

def main():
    groups = defaultdict(lambda: defaultdict(list))
    for folder in sorted(os.listdir(SHOTS)):
        path = os.path.join(SHOTS, folder)
        if not os.path.isdir(path):
            continue
        for name in sorted(os.listdir(path)):
            if name.endswith(".png") and not name.endswith("_compare.png") and not name.endswith("_actual.png"):
                stem = name[:-4]
                match = re.match(r"(.+)_(fa|en)(_.+)$", stem)
                if match:
                    screen, label = match.group(1), match.group(2) + match.group(3)
                else:  # e.g. the launcher icon, which has no language variants
                    screen, label = stem, stem.replace("_", " ")
                groups[folder][screen].append((label, f"../artifacts/screenshots/{folder}/{name}"))
    folders = sorted(groups, key=lambda f: ORDER.index(f) if f in ORDER else len(ORDER))
    lines = ["# Plan-B UI Gallery", "",
             "Real screenshots of the running app: the production Hilt graph, Room database and navigation,",
             "seeded with sample data and a frozen clock (12 Mehr 1405 / 4 Oct 2026, 10:00 Tehran), rendered",
             "by Roborazzi on Robolectric with native graphics. Variants: Persian/English, light/dark and 150% font.",
             "Regenerate with `./gradlew recordRoborazziDebug && python3 tools/generate_ui_gallery.py`.",
             "Verified in CI with `./gradlew verifyRoborazziDebug`.", ""]
    total = 0
    for folder in folders:
        lines += [f"## {folder.capitalize()}", ""]
        for screen, variants in sorted(groups[folder].items()):
            variants.sort(key=lambda v: VARIANT_ORDER.index(v[0]) if v[0] in VARIANT_ORDER else 99)
            lines += [f"### `{screen}`", "", "| " + " | ".join(v for v, _ in variants) + " |",
                      "|" + "---|" * len(variants),
                      "| " + " | ".join(f'<img src="{p}" width="220"/>' for _, p in variants) + " |", ""]
            total += len(variants)
    lines.insert(7, f"Total screenshots: **{total}**.\n")
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"{total} screenshots -> {OUT}")

if __name__ == "__main__":
    main()
