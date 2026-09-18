"""Generates the village_head_table block textures.

Oak desk with an unrolled town plan on top: the road ring, four lamp posts at
the corners (gold dark) and the bell at the centre (gold). The sides hang a navy
cloth with gold trim - the village head's coat colours. `_front` is only used by
the optional facing variant (scroll rack); the default model is 4-way symmetric.

Output: src/main/resources/assets/placitum/textures/block/<name>.png (16x16 RGBA)
Run:    python tools/gen_village_head_table.py

Textures are drawn as 16 rows of 16 characters; each character is a key into
PALETTE. Recolour by editing PALETTE, reshape by editing the grid. Nothing here
is copied from vanilla - the palette borrows vanilla's value range so the block
sits next to oak planks and stone bricks without jumping out.
"""
import os

from PIL import Image

PALETTE = {
    ".": (162, 130, 78),  # oak plank base
    "w": (188, 152, 98),  # oak plank light
    "d": (124, 99, 58),  # oak plank dark
    "k": (96, 74, 44),  # oak seam
    "p": (232, 220, 190),  # paper
    "q": (208, 192, 156),  # paper shade
    "i": (74, 62, 50),  # ink
    "n": (58, 74, 107),  # village-head navy (coat)
    "N": (48, 62, 92),  # navy fold
    "g": (207, 182, 119),  # gold trim (coat)
    "h": (160, 132, 70),  # gold dark
}

TEXTURES = {
    "village_head_table_top": [
        "dddddddddddddddd",
        "d.wwwwwwwwwwww.d",
        "d.pqpiiiiippqp.d",
        "d.pqppppppppqp.d",
        "d.pqphiiiihpqp.d",
        "d.pqpippppipqp.d",
        "d.pqpippppipqp.d",
        "d.pqpipggpipqp.d",
        "d.pqpipggpipqp.d",
        "d.pqpippppipqp.d",
        "d.pqphiiiihpqp.d",
        "d.pqppppppppqp.d",
        "d.pqpiipiipiqp.d",
        "d.pqppppppppqp.d",
        "d..............d",
        "dddddddddddddddd",
    ],
    "village_head_table_side": [
        "wwwwwwwwwwwwwwww",
        "................",
        "dddddddddddddddd",
        "d.gggggggggggg.d",
        "d.nnnnnnnnnnnn.d",
        "d.nnNnnnnnnNnn.d",
        "d.nnNnnnnnnNnn.d",
        "d.nnNnnnnnnNnn.d",
        "d.nnNnnnnnnNnn.d",
        "d.nnNnnnnnnNnn.d",
        "d.nnNnnnnnnNnn.d",
        "d.nnNnnnnnnNnn.d",
        "d.nnnnnnnnnnnn.d",
        "d.gggggggggggg.d",
        "d..............d",
        "dddddddddddddddd",
    ],
    "village_head_table_front": [
        "wwwwwwwwwwwwwwww",
        "................",
        "dddddddddddddddd",
        "d.gggggggggggg.d",
        "d.nnnnnnnnnnnn.d",
        "d.nppppppppppn.d",
        "d.nppppiippppn.d",
        "d.nqqqqqqqqqqn.d",
        "d.nnnnnnnnnnnn.d",
        "d.nppppppppppn.d",
        "d.nppppiippppn.d",
        "d.nqqqqqqqqqqn.d",
        "d.nnnnnnnnnnnn.d",
        "d.gggggggggggg.d",
        "d..............d",
        "dddddddddddddddd",
    ],
    "village_head_table_bottom": [
        "wwwwwwwwwwwwwwww",
        ".......k........",
        ".......k........",
        "dddddddddddddddd",
        "wwwwwwwwwwwwwwww",
        "...k.......k....",
        "...k.......k....",
        "dddddddddddddddd",
        "wwwwwwwwwwwwwwww",
        ".......k........",
        ".......k........",
        "dddddddddddddddd",
        "wwwwwwwwwwwwwwww",
        "...k.......k....",
        "...k.......k....",
        "dddddddddddddddd",
    ],
}

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "placitum", "textures", "block")


def draw(rows):
    assert len(rows) == 16, "texture must be 16 rows"
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(rows):
        assert len(row) == 16, f"row {y} must be 16 chars: {row!r}"
        for x, ch in enumerate(row):
            img.putpixel((x, y), PALETTE[ch] + (255,))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, rows in TEXTURES.items():
        path = os.path.join(OUT, name + ".png")
        draw(rows).save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()
