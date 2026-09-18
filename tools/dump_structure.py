# -*- coding: utf-8 -*-
"""Print a saved structure (.nbt) layer by layer, as text.

    python tools/dump_structure.py <file.nbt> [<file.nbt> ...]

A vanilla structure block writes gzipped NBT: size, a palette of block states, and a list of
blocks as (pos, palette index). This reads that with no library, because the point is to be
able to look at a hand-built gatehouse in a terminal without starting the game.

Each Y layer is printed north-up (z increasing downward, x increasing to the right), with one
character per block and a legend underneath. Air is '.', and layers that are all air are
skipped. Block properties are kept in the legend so a stair's facing and a slab's half are
visible.
"""
import gzip
import struct
import sys
from collections import Counter, OrderedDict

# ----------------------------------------------------------------------------- NBT reader

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Reader:
    def __init__(self, data):
        self.d = data
        self.i = 0

    def take(self, fmt):
        v = struct.unpack_from('>' + fmt, self.d, self.i)
        self.i += struct.calcsize('>' + fmt)
        return v[0] if len(v) == 1 else v

    def string(self):
        n = self.take('H')
        s = self.d[self.i:self.i + n].decode('utf-8', 'replace')
        self.i += n
        return s

    def payload(self, t):
        if t == BYTE:
            return self.take('b')
        if t == SHORT:
            return self.take('h')
        if t == INT:
            return self.take('i')
        if t == LONG:
            return self.take('q')
        if t == FLOAT:
            return self.take('f')
        if t == DOUBLE:
            return self.take('d')
        if t == BYTE_ARRAY:
            n = self.take('i')
            v = list(struct.unpack_from('>%db' % n, self.d, self.i))
            self.i += n
            return v
        if t == STRING:
            return self.string()
        if t == LIST:
            et = self.take('b')
            n = self.take('i')
            return [self.payload(et) for _ in range(n)]
        if t == COMPOUND:
            out = OrderedDict()
            while True:
                tt = self.take('b')
                if tt == END:
                    return out
                name = self.string()
                out[name] = self.payload(tt)
        if t == INT_ARRAY:
            n = self.take('i')
            v = list(struct.unpack_from('>%di' % n, self.d, self.i))
            self.i += 4 * n
            return v
        if t == LONG_ARRAY:
            n = self.take('i')
            v = list(struct.unpack_from('>%dq' % n, self.d, self.i))
            self.i += 8 * n
            return v
        raise ValueError('tag %d' % t)

    def root(self):
        t = self.take('b')
        assert t == COMPOUND, 'root is not a compound'
        self.string()
        return self.payload(COMPOUND)


def load(path):
    raw = open(path, 'rb').read()
    try:
        raw = gzip.decompress(raw)
    except OSError:
        pass
    return Reader(raw).root()


# ------------------------------------------------------------------------------ rendering

def state_name(entry):
    name = entry['Name'].replace('minecraft:', '')
    props = entry.get('Properties')
    if props:
        name += '[' + ','.join('%s=%s' % kv for kv in props.items()) + ']'
    return name


def dump(path):
    nbt = load(path)
    sx, sy, sz = nbt['size']
    palette = nbt['palette']
    names = [state_name(p) for p in palette]
    grid = {}
    for b in nbt['blocks']:
        x, y, z = b['pos']
        grid[(x, y, z)] = b['state']

    solid = [(p, s) for p, s in grid.items() if not names[s].startswith('air')]
    if not solid:
        print('%s: %dx%dx%d, nothing but air' % (path, sx, sy, sz))
        return
    xs = [p[0] for p, _ in solid]
    ys = [p[1] for p, _ in solid]
    zs = [p[2] for p, _ in solid]
    x0, x1, y0, y1, z0, z1 = min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)

    # One character per block *type* (properties folded), so the map stays readable; the
    # legend lists every state that type appeared with.
    counts = Counter(names[s] for _, s in solid)
    by_type = OrderedDict()
    for name, n in counts.most_common():
        base = name.split('[')[0]
        by_type.setdefault(base, []).append((name, n))
    chars = '#=+*o%&@$xXvVnN^~-:;abcdefghijklmpqrstuwyz'
    glyph = {}
    for k, base in enumerate(by_type):
        glyph[base] = chars[k] if k < len(chars) else '?'

    print('=' * 78)
    print('%s' % path)
    print('saved size %dx%dx%d, occupied x %d..%d (%d), y %d..%d (%d), z %d..%d (%d)' % (
        sx, sy, sz, x0, x1, x1 - x0 + 1, y0, y1, y1 - y0 + 1, z0, z1, z1 - z0 + 1))
    print('entities: %d' % len(nbt.get('entities', [])))
    print()
    for y in range(y0, y1 + 1):
        rows = []
        any_block = False
        for z in range(z0, z1 + 1):
            row = ''
            for x in range(x0, x1 + 1):
                s = grid.get((x, y, z))
                if s is None or names[s].startswith('air'):
                    row += '.'
                else:
                    row += glyph[names[s].split('[')[0]]
                    any_block = True
            rows.append(row)
        if not any_block:
            continue
        print('--- y = %d (layer %d of %d)   north is up, x -> east' % (y, y - y0 + 1, y1 - y0 + 1))
        for z, row in zip(range(z0, z1 + 1), rows):
            print('  z=%3d  %s' % (z, row))
    print()
    print('legend:')
    for base, states in by_type.items():
        total = sum(n for _, n in states)
        print('  %s  %-22s %4d' % (glyph[base], base, total))
        if len(states) > 1 or '[' in states[0][0]:
            for name, n in states:
                print('        %-40s %4d' % (name, n))


if __name__ == '__main__':
    for p in sys.argv[1:]:
        dump(p)
