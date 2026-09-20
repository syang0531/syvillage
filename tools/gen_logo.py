"""Generates the CurseForge project logo.

Output: docs/curseforge/logo.png (512x512, CurseForge requires at least 400x400).
Run:    python tools/gen_logo.py

SyVillage has no item textures to compose from, so the logo is drawn from
primitives. The bell is the hero: ringing it is how the player registers a
village and how the village raises the alarm. Beneath it sits the palisade the
village builds for itself, lit from within - the mod in one picture.

Composition rule: it has to survive the 64px gallery thumbnail, so there are
exactly three shapes (bell, ring, glow) and nothing smaller than a few pixels.
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

SS = 4
S = 512 * SS

BG_TOP = (10, 13, 26)
BG_BOTTOM = (26, 33, 54)
GLOW = (255, 166, 72)
GROUND = (34, 42, 63)
GROUND_LIT = (74, 59, 52)
WOOD = (120, 88, 56)
WOOD_DARK = (52, 38, 26)
WOOD_LIT = (168, 122, 74)
ROOF = (146, 70, 52)
WALLC = (196, 173, 140)
BELL_HI = (255, 216, 120)
BELL_MID = (226, 172, 56)
BELL_LO = (150, 104, 26)
BELL_EDGE = (74, 50, 14)

RX, RY = int(S * 0.400), int(S * 0.120)
RING_Y = int(S * 0.765)
BELL_CX, BELL_CY = S // 2, int(S * 0.375)
BELL_W = int(S * 0.180)
BELL_H = int(S * 0.250)


def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def background(d):
    for y in range(S):
        d.line([(0, y), (S, y)], fill=lerp(BG_TOP, BG_BOTTOM, (y / S) ** 0.8))


def glow(img, cx, cy, radius, peak, squash=1.0, colour=GLOW):
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    steps = 140
    for i in range(steps, 0, -1):
        t = i / steps
        r = radius * t
        a = round(peak * (1 - t) ** 1.7)
        ld.ellipse([cx - r, cy - r * squash, cx + r, cy + r * squash], fill=colour + (a,))
    img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(S * 0.006)))


def ground(d):
    d.ellipse([S // 2 - RX * 1.22, RING_Y - RY * 1.5, S // 2 + RX * 1.22, RING_Y + RY * 1.5],
              fill=GROUND)
    d.ellipse([S // 2 - RX * 0.72, RING_Y - RY * 0.85, S // 2 + RX * 0.72, RING_Y + RY * 0.85],
              fill=GROUND_LIT)


def log_at(d, angle):
    x = S // 2 + RX * math.cos(angle)
    y = RING_Y + RY * math.sin(angle)
    depth = (math.sin(angle) + 1) / 2
    h = int(S * (0.088 + 0.052 * depth))
    w = int(S * (0.030 + 0.013 * depth))
    body = lerp(WOOD_DARK, WOOD, 0.30 + 0.70 * depth)
    if depth > 0.55:
        body = lerp(body, WOOD_LIT, (depth - 0.55) * 1.4)
    d.polygon(
        [(x - w / 2, y), (x - w / 2, y - h), (x, y - h - w * 0.8), (x + w / 2, y - h), (x + w / 2, y)],
        fill=body, outline=WOOD_DARK, width=max(1, int(SS * 1.5)))


def palisade(d):
    n = 30
    angles = [(-0.5 + i / n) * 2 * math.pi for i in range(n)]
    for a in sorted(angles, key=lambda a: math.sin(a)):
        aa = a % (2 * math.pi)
        if 0.5 * math.pi - 0.30 < aa < 0.5 * math.pi + 0.30:   # gate opening, front centre
            continue
        log_at(d, a)


def house(d, x, y, w, h):
    d.polygon([(x - w, y), (x - w, y - h), (x + w, y - h), (x + w, y)], fill=WALLC)
    d.polygon([(x - w * 1.3, y - h), (x, y - h - w * 1.1), (x + w * 1.3, y - h)], fill=ROOF)
    d.rectangle([x - w * 0.26, y - h * 0.58, x + w * 0.26, y], fill=(58, 40, 26))


def bell(d):
    cx, cy, w, h = BELL_CX, BELL_CY, BELL_W, BELL_H
    lip_y = cy + h / 2
    top_y = cy - h / 2

    # crown: a loop arch, not a ball
    lw = int(w * 0.14)
    d.arc([cx - w * 0.26, top_y - w * 0.42, cx + w * 0.26, top_y + w * 0.16],
          start=185, end=355, fill=BELL_MID, width=lw)

    # body: concave shoulder that stays narrow, then flares hard at the lip.
    # A convex profile reads as a tent; the flare has to arrive late.
    def half_at(t):
        return w * (0.33 + 0.12 * t + 0.55 * t ** 4)

    left, right = [], []
    steps = 60
    for i in range(steps + 1):
        t = i / steps                       # 0 at top, 1 at lip
        half = half_at(t)
        y = top_y + h * t
        left.append((cx - half, y))
        right.append((cx + half, y))
    d.polygon(left + right[::-1], fill=BELL_MID, outline=BELL_EDGE, width=max(1, int(SS * 1.6)))

    # lit left face / shadowed right face
    for i in range(steps):
        t = i / steps
        half = half_at(t)
        y = top_y + h * t
        d.line([(cx - half, y), (cx - half * 0.34, y)],
               fill=lerp(BELL_HI, BELL_MID, t), width=int(SS * 3))
        d.line([(cx + half * 0.42, y), (cx + half, y)],
               fill=lerp(BELL_MID, BELL_LO, 0.35 + 0.65 * t), width=int(SS * 3))

    # lip band and clapper
    d.rectangle([cx - w * 1.06, lip_y - h * 0.055, cx + w * 1.06, lip_y + h * 0.055],
                fill=BELL_MID, outline=BELL_EDGE, width=max(1, int(SS * 1.6)))
    d.ellipse([cx - w * 0.15, lip_y + h * 0.05, cx + w * 0.15, lip_y + h * 0.26],
              fill=BELL_LO, outline=BELL_EDGE, width=max(1, int(SS * 1.2)))


def vignette(img):
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    steps = 80
    for i in range(steps):
        t = i / steps
        r = S * (0.50 + 0.52 * t)
        ld.ellipse([S // 2 - r, S // 2 - r, S // 2 + r, S // 2 + r],
                   outline=(0, 0, 0, 7), width=S // steps + SS)
    img.alpha_composite(layer)


def main():
    img = Image.new("RGBA", (S, S), BG_TOP + (255,))
    background(ImageDraw.Draw(img))

    glow(img, S // 2, int(S * 0.52), int(S * 0.46), 135)             # hearth behind the bell
    glow(img, S // 2, RING_Y, int(S * 0.40), 90, squash=0.42)        # light pooling on the ground

    d = ImageDraw.Draw(img)
    ground(d)
    house(d, S // 2 - int(S * 0.180), RING_Y - int(S * 0.020), int(S * 0.066), int(S * 0.074))
    house(d, S // 2 + int(S * 0.185), RING_Y - int(S * 0.006), int(S * 0.058), int(S * 0.064))
    palisade(d)

    glow(img, BELL_CX, BELL_CY, int(S * 0.30), 95)                   # halo directly on the bell
    bell(ImageDraw.Draw(img))
    vignette(img)

    out_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                           "docs", "curseforge")
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, "logo.png")
    final = img.convert("RGB").resize((512, 512), Image.LANCZOS)
    final.save(out)
    print("wrote", out)
    # Sanity check the gallery thumbnail without committing it.
    if "--thumb" in sys.argv:
        final.resize((64, 64), Image.LANCZOS).save("logo_thumb_check.png")
        print("wrote logo_thumb_check.png")


if __name__ == "__main__":
    main()
