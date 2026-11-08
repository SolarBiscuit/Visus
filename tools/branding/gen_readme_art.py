"""Generate the release-gates chart for the README.

The banner and the duotone icon now come from the Atmosphera branding scripts
(`[1.20.1] Atmosphera/branding/gen_readme_art_extra.py visus`), so this script no longer writes the banner;
the old `banner()` below is kept only for reference.

    python -I tools/branding/gen_readme_art.py

Needs Pillow and the Windows fonts Bahnschrift and Consolas (any TTF can be swapped in below).
The logo is read from docs/logo/visus-logo-01-voxel-iris-dark.svg, so the banner always uses the
chosen logo. Output: docs/logo/visus-banner.png and docs/img/release-gates.png.

Every number in the chart is either measured (see CHANGELOG.md and docs/VISUS-ARCHITECTURE.md) or a
release gate from docs/VISUS-ROADMAP.md. Gates are drawn outlined and labelled as targets.
"""
import math
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
LOGO = ROOT / "docs/logo/visus-logo-01-voxel-iris-dark.svg"
OUT_BANNER = ROOT / "docs/logo/visus-banner.png"
OUT_CHART = ROOT / "docs/img/release-gates.png"

FONTS = Path("C:/Windows/Fonts")
BG = (20, 20, 24)          # #141418, the logo's dark background
PANEL = (28, 29, 35)
INK = (228, 228, 231)      # #E4E4E7
MUTED = (150, 152, 163)
TEAL = (94, 224, 238)      # #5EE0EE
TEAL_D = (42, 169, 184)    # #2AA9B8
GRID = (40, 41, 49)


def font(name, size, variation=None):
    f = ImageFont.truetype(str(FONTS / name), size)
    if variation:
        try:
            f.set_variation_by_name(variation)
        except (OSError, AttributeError):
            pass
    return f


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def logo_rects():
    """The voxel logo is a set of <rect x y width height fill>. Returns (rects, bbox)."""
    svg = LOGO.read_text(encoding="utf-8")
    rects = []
    for m in re.finditer(r'<rect x="([\d.]+)" y="([\d.]+)" width="([\d.]+)" height="([\d.]+)"(?: rx="([\d.]+)")? fill="(#[0-9A-Fa-f]{6})"', svg):
        x, y, w, h, rx, c = m.groups()
        rects.append((float(x), float(y), float(w), float(h), float(rx or 0), hex_rgb(c)))
    x0 = min(r[0] for r in rects)
    y0 = min(r[1] for r in rects)
    x1 = max(r[0] + r[2] for r in rects)
    y1 = max(r[1] + r[3] for r in rects)
    return rects, (x0, y0, x1, y1)


def draw_logo(img, left, top, height):
    """Draw the logo scaled so its height is `height`, at 4x supersampling for clean edges."""
    rects, (x0, y0, x1, y1) = logo_rects()
    s = height / (y1 - y0)
    w = int((x1 - x0) * s)
    ss = 4
    layer = Image.new("RGBA", (w * ss, height * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for x, y, rw, rh, rx, c in rects:
        box = [(x - x0) * s * ss, (y - y0) * s * ss, (x - x0 + rw) * s * ss - 1, (y - y0 + rh) * s * ss - 1]
        d.rounded_rectangle(box, radius=rx * s * ss, fill=c + (255,))
    layer = layer.resize((w, height), Image.LANCZOS)
    img.paste(layer, (left, top), layer)
    return w


def spaced(d, xy, text, f, fill, tracking):
    x, y = xy
    for ch in text:
        d.text((x, y), ch, font=f, fill=fill)
        x += d.textlength(ch, font=f) + tracking
    return x


def banner():
    W, H = 2560, 640  # shown at 1280x320
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    # voxel grid fading in from the right, echoing the logo's pixel construction
    cell, gap = 40, 6
    for gx in range(W // cell + 1):
        for gy in range(H // cell + 1):
            t = gx * cell / W
            if t < 0.45:
                continue
            a = (t - 0.45) / 0.55
            # deterministic sparkle: a few cells light up in teal
            h = (gx * 73856093 ^ gy * 19349663) % 100
            if h < 7 * a * 4:
                col = tuple(int(BG[i] + (TEAL_D[i] - BG[i]) * (0.25 + 0.5 * (h / 28))) for i in range(3))
            else:
                col = tuple(int(BG[i] + (GRID[i] - BG[i]) * min(1.0, a * 1.4)) for i in range(3))
            x, y = gx * cell, gy * cell
            d.rounded_rectangle([x + gap // 2, y + gap // 2, x + cell - gap // 2, y + cell - gap // 2], radius=5, fill=col)

    # left fade so text stays readable over the grid
    fade = Image.new("L", (W, H), 0)
    fd = ImageDraw.Draw(fade)
    for x in range(W):
        v = int(255 * min(1.0, max(0.0, 1 - (x - 1500) / 700))) if x > 1500 else 255
        fd.line([(x, 0), (x, H)], fill=v)
    img = Image.composite(img, Image.new("RGB", (W, H), BG), fade)
    d = ImageDraw.Draw(img)

    # logo
    lh = 330
    lw = draw_logo(img, 150, (H - lh) // 2 - 10, lh)

    # wordmark
    tx = 150 + lw + 110
    f_big = font("bahnschrift.ttf", 210, "Bold")
    f_tag = font("bahnschrift.ttf", 56, "SemiLight")
    f_small = font("bahnschrift.ttf", 36, "Regular")
    spaced(d, (tx, 130), "VISUS", f_big, INK, 22)
    d.rectangle([tx + 4, 372, tx + 340, 380], fill=TEAL)
    d.text((tx + 4, 410), "Shader pack loader for Minecraft Forge 1.20.1", font=f_tag, fill=INK)
    d.text((tx + 4, 490), "UNOFFICIAL FORK OF OCULUS 1.8.0   \u00b7   IRIS-COMPATIBLE   \u00b7   AGPL-3.0", font=f_small, fill=TEAL_D)

    OUT_BANNER.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT_BANNER, optimize=True)
    print("wrote", OUT_BANNER, img.size)


def chip(d, x, y, text, f, fg, bg):
    w = d.textlength(text, font=f) + 36
    d.rounded_rectangle([x, y, x + w, y + 46], radius=23, fill=bg)
    d.text((x + 18, y + 6), text, font=f, fill=fg)
    return w


def chart():
    # (label, baseline text, baseline fraction 1.0, target text, target fraction, release, note)
    rows = [
        ("Terrain program link wait", "172.6 ms average (Oculus 1.8.0)", "\u2264 20 ms", 20 / 172.6, "met in 0.4.0",
         (3.6 / 172.6, "measured: 3.6 ms")),
        ("Shadow-pass entity CPU, MakeUp", "0.54 ms per frame (Oculus 1.8.0)", "\u2265 20 % lower", 0.80, "Absorb, not yet measured"),
        ("Pack apply, cold start", "previous release = 100 %", "\u2265 50 % faster", 0.50, "Fast load"),
        ("Pack apply, warm start", "previous release = 100 %", "\u2265 80 % faster", 0.20, "Fast load"),
        ("CPU-bound frame time, MakeUp", "p50 under Oculus 1.8.0 = 100 %", "p50 \u2265 8 % better", 0.92, "Frame cost"),
    ]
    W = 2000
    top, rowh = 330, 190
    H = top + rowh * len(rows) + 130
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    f_title = font("bahnschrift.ttf", 64, "Bold")
    f_sub = font("bahnschrift.ttf", 32, "Regular")
    f_lab = font("bahnschrift.ttf", 40, "SemiBold")
    f_val = font("consola.ttf", 32)
    f_chip = font("bahnschrift.ttf", 28, "SemiBold")

    d.text((70, 60), "Release gates: where Visus is aiming", font=f_title, fill=INK)
    d.text((70, 150), "Dim bars are measured baselines. Outlined bars are release gates (targets); a solid bright bar is a measured result.", font=f_sub, fill=MUTED)
    d.text((70, 196), "Each gate is a pass/fail test in docs/VISUS-ROADMAP.md; a failed gate is dropped and reported.", font=f_sub, fill=MUTED)

    x0, barw = 70, W - 140
    d.line([(70, 260), (W - 70, 260)], fill=GRID, width=2)
    for i, (label, base, tgt, frac, rel, *meas) in enumerate(rows):
        y = top + i * rowh - 30
        d.text((x0, y), label, font=f_lab, fill=INK)
        chip(d, W - 70 - int(d.textlength(rel, font=f_chip) + 36), y + 2, rel, f_chip, BG, TEAL)
        by = y + 62
        bh = 40
        # baseline
        d.rounded_rectangle([x0, by, x0 + barw, by + bh], radius=8, fill=TEAL_D)
        d.text((x0 + 16, by + 3), base, font=f_val, fill=BG)
        # target
        ty = by + bh + 14
        tw = max(int(barw * frac), 12)
        d.rounded_rectangle([x0, ty, x0 + tw, ty + bh], radius=8, fill=PANEL, outline=TEAL, width=4)
        if meas:  # a measured result: solid bar, drawn over the gate outline
            mw = max(int(barw * meas[0][0]), 12)
            d.rounded_rectangle([x0, ty, x0 + mw, ty + bh], radius=8, fill=TEAL)
            d.text((x0 + tw + 18, ty + 3), f"{meas[0][1]}, gate: {tgt}", font=f_val, fill=TEAL)
            continue
        label_w = d.textlength("gate: " + tgt, font=f_val)
        if x0 + tw + 18 + label_w > W - 70:
            d.text((x0 + 16, ty + 3), "gate: " + tgt, font=f_val, fill=TEAL)  # long bar: label inside
        else:
            d.text((x0 + tw + 18, ty + 3), "gate: " + tgt, font=f_val, fill=TEAL)
    fy = H - 120
    d.line([(70, fy - 20), (W - 70, fy - 20)], fill=GRID, width=2)
    d.text((70, fy), "Baselines measured on the RatPack 2.9 test client (499 mods), RX 7900 XTX. Bench noise is \u00b15\u20136 %.", font=f_sub, fill=MUTED)
    d.text((70, fy + 44), "Only the terrain link-wait gate has been measured so far (0.4.0); the others are targets, not results.", font=f_sub, fill=MUTED)

    OUT_CHART.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT_CHART, optimize=True)
    print("wrote", OUT_CHART, img.size)


if __name__ == "__main__":
    chart()
