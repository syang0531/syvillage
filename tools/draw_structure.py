# -*- coding: utf-8 -*-
"""Draw a saved structure (.nbt) as one image: every layer side by side, top-down, north up.

    python tools/draw_structure.py <file.nbt> <out.png>

Companion to dump_structure.py, for when a layer map has to be looked at rather than read.
One colour per block type, a darker tint for the top half of a stair, and an arrow for a
stair's facing. Empty layers are skipped.
"""
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from dump_structure import load, state_name  # noqa: E402

CELL = 14
GAP = 18
COLOURS = {
    'stone_bricks': (120, 120, 120),
    'stone_brick_stairs': (150, 150, 150),
    'stone_brick_slab': (165, 165, 165),
    'oak_fence': (160, 120, 60),
    'oak_fence_gate': (190, 140, 70),
    'lantern': (255, 190, 60),
    'torch': (255, 220, 120),
    'cobblestone': (100, 100, 100),
    'oak_planks': (185, 145, 85),
}
ARROW = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}


def draw(path, out):
    nbt = load(path)
    names = [state_name(p) for p in nbt['palette']]
    grid = {}
    for b in nbt['blocks']:
        x, y, z = b['pos']
        if not names[b['state']].startswith('air'):
            grid[(x, y, z)] = names[b['state']]
    xs = [p[0] for p in grid]
    ys = [p[1] for p in grid]
    zs = [p[2] for p in grid]
    x0, x1, y0, y1, z0, z1 = min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)
    w, d = x1 - x0 + 1, z1 - z0 + 1
    layers = [y for y in range(y0, y1 + 1) if any(p[1] == y for p in grid)]

    per_row = 5
    rows = (len(layers) + per_row - 1) // per_row
    W = per_row * (w * CELL + GAP) + GAP
    H = rows * (d * CELL + GAP + 16) + GAP + 40
    im = Image.new('RGB', (W, H), (250, 250, 250))
    dr = ImageDraw.Draw(im)
    dr.text((GAP, 8), '%s   %dx%dx%d (x east, z south; north is up; y=0 is the lowest layer)'
            % (os.path.basename(path), w, y1 - y0 + 1, d), fill=(0, 0, 0))

    for k, y in enumerate(layers):
        ox = GAP + (k % per_row) * (w * CELL + GAP)
        oy = 40 + (k // per_row) * (d * CELL + GAP + 16)
        dr.text((ox, oy), 'y=%d' % y, fill=(0, 0, 0))
        oy += 16
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                px, py = ox + (x - x0) * CELL, oy + (z - z0) * CELL
                name = grid.get((x, y, z))
                if name is None:
                    dr.rectangle([px, py, px + CELL - 1, py + CELL - 1], outline=(225, 225, 225))
                    continue
                base = name.split('[')[0]
                col = COLOURS.get(base, (200, 80, 200))
                if 'half=top' in name:
                    col = tuple(max(0, c - 50) for c in col)
                dr.rectangle([px, py, px + CELL - 1, py + CELL - 1], fill=col, outline=(60, 60, 60))
                for f, (dx, dz) in ARROW.items():
                    if 'facing=%s' % f in name and 'stairs' in base:
                        cx, cy = px + CELL // 2, py + CELL // 2
                        dr.line([cx - dx * 4, cy - dz * 4, cx + dx * 5, cy + dz * 5], fill=(0, 0, 0), width=2)
                        dr.ellipse([cx + dx * 4 - 2, cy + dz * 4 - 2, cx + dx * 4 + 2, cy + dz * 4 + 2], fill=(0, 0, 0))
                if base == 'lantern':
                    dr.ellipse([px + 3, py + 3, px + CELL - 4, py + CELL - 4], fill=(255, 120, 0))
    # legend
    ly = H - 30
    lx = GAP
    for base, col in COLOURS.items():
        if any(n.split('[')[0] == base for n in grid.values()):
            dr.rectangle([lx, ly, lx + 12, ly + 12], fill=col, outline=(0, 0, 0))
            dr.text((lx + 16, ly), base, fill=(0, 0, 0))
            lx += 16 + 7 * len(base) + 20
    im.save(out)
    print('wrote', out)


if __name__ == '__main__':
    draw(sys.argv[1], sys.argv[2])
