# -*- coding: utf-8 -*-
"""The trade items, drawn rather than copied.

Run from the project root:  python tools/gen_items.py

All are 16x16, transparent background, in the flat "item/generated" style. They are stand-ins
of the same standard as everything else in M4: recognisable, on-palette, and cheap to redraw
when M6 gets round to making things pretty.
"""
import os
from PIL import Image

OUT = os.path.join('src', 'main', 'resources', 'assets', 'syvillage', 'textures', 'item')


def canvas():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


def seal():
    """A gold-rimmed disc of red wax, pressed with a simple mark."""
    im = canvas()
    px = im.load()
    GOLD, GOLD_DARK = (212, 160, 23, 255), (140, 106, 12, 255)
    WAX, WAX_LIGHT, WAX_DARK = (163, 38, 38, 255), (201, 58, 58, 255), (110, 22, 22, 255)
    cx, cy = 7.5, 7.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= 4.6:
                px[x, y] = WAX
            elif d <= 6.4:
                px[x, y] = GOLD
            elif d <= 7.2:
                px[x, y] = GOLD_DARK
    # A highlight on the upper-left of the wax, and a pressed mark in the middle.
    for x, y in ((5, 5), (6, 4), (5, 6)):
        px[x, y] = WAX_LIGHT
    for x, y in ((7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8), (6, 7), (9, 7)):
        px[x, y] = WAX_DARK
    return im


def heart():
    """An iron heart with a warm core: the thing a golem is, if you could hold it."""
    im = canvas()
    px = im.load()
    IRON, IRON_DARK, IRON_LIGHT = (158, 163, 168, 255), (92, 97, 102, 255), (214, 218, 222, 255)
    CORE, CORE_LIGHT = (179, 38, 30, 255), (232, 90, 70, 255)
    rows = [
        '................',
        '................',
        '...XXX....XXX...',
        '..X###X..X###X..',
        '.X#####XX#####X.',
        '.X############X.',
        '.X############X.',
        '.X############X.',
        '..X##########X..',
        '...X########X...',
        '....X######X....',
        '.....X####X.....',
        '......X##X......',
        '.......XX.......',
        '................',
        '................',
    ]
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == 'X':
                px[x, y] = IRON_DARK
            elif c == '#':
                px[x, y] = IRON
    for x, y in ((4, 4), (5, 4), (4, 5)):
        px[x, y] = IRON_LIGHT
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8), (6, 7), (9, 7), (7, 6), (7, 9)):
        px[x, y] = CORE
    px[7, 7] = CORE_LIGHT
    return im


def charter():
    """A sheet of parchment, written on, with the village head's seal at the foot."""
    im = canvas()
    px = im.load()
    PAPER, PAPER_DARK, EDGE = (232, 216, 176, 255), (208, 188, 142, 255), (120, 96, 56, 255)
    INK = (78, 60, 40, 255)
    WAX, WAX_DARK = (163, 38, 38, 255), (110, 22, 22, 255)
    rows = [
        '................',
        '..EEEEEEEEEEE...',
        '..E#########E...',
        '..E#########E...',
        '..E#-----##.E...',
        '..E#########E...',
        '..E#------#.E...',
        '..E#########E...',
        '..E#-----##.E...',
        '..E#########E...',
        '..E#---#####E...',
        '..E#########E...',
        '..E#####WWW#E...',
        '..E#####WwW#E...',
        '..EEEEEEWWWEE...',
        '................',
    ]
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == 'E':
                px[x, y] = EDGE
            elif c == '#':
                px[x, y] = PAPER
            elif c == '.' and 2 < x < 12 and 1 < y < 14:
                px[x, y] = PAPER_DARK
            elif c == '-':
                px[x, y] = INK
            elif c == 'W':
                px[x, y] = WAX
            elif c == 'w':
                px[x, y] = WAX_DARK
    return im


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    seal().save(os.path.join(OUT, 'lords_seal.png'))
    heart().save(os.path.join(OUT, 'golem_heart.png'))
    charter().save(os.path.join(OUT, 'freemans_charter.png'))
    print('wrote lords_seal.png, golem_heart.png, freemans_charter.png')
