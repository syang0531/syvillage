"""Generates the guardian_statue block textures.

Two textures. `guardian_statue` is a front-projection atlas of the figure: each
model element's north/south face reads the pixels where that element sits when
seen from the front (head rows 0-2, torso 3-7, arms cols 2-3/12-13, legs 8-12).
Redstone eyes and an iron nose, belt and wrist bands are the only non-stone
pixels - a statue of a golem, not a golem. Spare corners hold plain side swatches
(cols 0-3 rows 9-14 arm side, cols 12-15 rows 9-13 leg side, cols 12-15 rows 0-2
head side). `guardian_statue_plinth`: top = iron ring inlay, rows 13-15 = the
plinth's side strip (light lip, riveted iron band, dark foot).

Output: src/main/resources/assets/syvillage/textures/block/<name>.png (16x16 RGBA)
Run:    python tools/gen_guardian_statue.py

Textures are drawn as 16 rows of 16 characters; each character is a key into
PALETTE. Recolour by editing PALETTE, reshape by editing the grid. Nothing here
is copied from vanilla - the palette borrows vanilla's value range so the block
sits next to oak planks and stone bricks without jumping out.
"""
import os

from PIL import Image

PALETTE = {
    "#": (140, 140, 140),  # stone base
    "+": (168, 168, 168),  # stone light
    "-": (112, 112, 112),  # stone dark
    "I": (222, 222, 222),  # iron
    "J": (186, 186, 186),  # iron shade
    "R": (236, 66, 50),  # redstone lit
}

TEXTURES = {
    "guardian_statue": [
        "######++++######",
        "######RIIR######",
        "######-II-######",
        "##++++++++++++##",
        "#+##############",
        "################",
        "#####-####-#####",
        "####IIIIIIII####",
        "##II###II###II##",
        "######-##-######",
        "######-##-######",
        "######-##-######",
        "#####--##--#####",
        "############----",
        "IIII############",
        "################",
    ],
    "guardian_statue_plinth": [
        "################",
        "################",
        "##++++++++++++##",
        "#####IIIIII#####",
        "####I######I####",
        "###I########I###",
        "###I########I###",
        "###I###JJ###I###",
        "###I###JJ###I###",
        "###I########I###",
        "###I########I###",
        "####I######I####",
        "#####IIIIII#####",
        "++++++++++++++++",
        "IIIJIIIIJIIIIJII",
        "----------------",
    ],
}

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "syvillage", "textures", "block")


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
