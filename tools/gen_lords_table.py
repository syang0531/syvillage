"""Generates the lords_table block textures.

Stone-brick desk. The top is a carved gate crest: two merlons, a recessed arch
inlaid with the lord's burgundy and a gold portcullis. The sides hang a burgundy
banner with gold trim over stone brickwork. `_front` (banner with a gold badge)
is only used by the optional facing variant.

Output: src/main/resources/assets/placitum/textures/block/<name>.png (16x16 RGBA)
Run:    python tools/gen_lords_table.py

Textures are drawn as 16 rows of 16 characters; each character is a key into
PALETTE. Recolour by editing PALETTE, reshape by editing the grid. Nothing here
is copied from vanilla - the palette borrows vanilla's value range so the block
sits next to oak planks and stone bricks without jumping out.
"""
import os

from PIL import Image

PALETTE = {
    "g": (207, 182, 119),  # gold trim (coat)
    "b": (110, 42, 46),  # lord burgundy (coat)
    "B": (88, 32, 36),  # burgundy fold
    "#": (140, 140, 140),  # stone base
    "+": (168, 168, 168),  # stone light
    "-": (112, 112, 112),  # stone dark
    "=": (86, 86, 86),  # mortar / carved recess
}

TEXTURES = {
    "lords_table_top": [
        "----------------",
        "-++++++++++++++-",
        "-+##=##==##=##+-",
        "-+##========##+-",
        "-+##=bgbbgb=##+-",
        "-+##=bgbbgb=##+-",
        "-+##=bgbbgb=##+-",
        "-+##=gggggg=##+-",
        "-+##=bgbbgb=##+-",
        "-+##=bgbbgb=##+-",
        "-+##=bgbbgb=##+-",
        "-+##========##+-",
        "-+############+-",
        "-+##-######-##+-",
        "-++++++++++++++-",
        "----------------",
    ],
    "lords_table_side": [
        "+++++++=++++++++",
        "#######=########",
        "####gggggggg####",
        "====bbbbbbbb====",
        "+++=bBbbbbbb=+++",
        "###=bBbbbbbb=###",
        "###=bBbbbbbb=###",
        "====bBbbbbbb====",
        "++++bBbbbbbb++++",
        "####bBbbbbbb####",
        "####bBbbbbbb####",
        "====bBbbbbbb====",
        "+++=bbbbbbbb=+++",
        "###=gggggggg=###",
        "###=########=###",
        "================",
    ],
    "lords_table_front": [
        "+++++++=++++++++",
        "#######=########",
        "####gggggggg####",
        "====bbbbbbbb====",
        "+++=bBbbbbbb=+++",
        "###=bBbbbbbb=###",
        "###=bBbbbbbb=###",
        "====bBbggbbb====",
        "++++bBbggbbb++++",
        "####bBbbbbbb####",
        "####bBbbbbbb####",
        "====bBbbbbbb====",
        "+++=bbbbbbbb=+++",
        "###=gggggggg=###",
        "###=########=###",
        "================",
    ],
    "lords_table_bottom": [
        "+++++++=++++++++",
        "#######=########",
        "-------=--------",
        "================",
        "+++=+++++++=++++",
        "###=#######=####",
        "---=-------=----",
        "================",
        "+++++++=++++++++",
        "#######=########",
        "-------=--------",
        "================",
        "+++=+++++++=++++",
        "###=#######=####",
        "---=-------=----",
        "================",
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
