"""Draws Taz's pixel-art logo and banners and writes them as PNGs (no libraries needed).

The logo is a "T" that is also a tree: a leafy canopy for the crossbar and a log for the stem.
Everything is drawn on a small pixel grid and scaled up with hard edges, like Minecraft textures.
Run: python make_branding.py  (writes next to this file)
"""
import math
import os
import struct
import zlib

OUT = os.path.dirname(os.path.abspath(__file__))
CLEAR = (0, 0, 0, 0)


def rgb(hex_color, a=255):
    h = hex_color.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


# ---- palette
BG_TOP, BG_BOTTOM = rgb("#14443f"), rgb("#0d2f2c")
RIM, RIM_LIGHT = rgb("#2a8f7b"), rgb("#4fd1b0")
OUTLINE = rgb("#071a19")
LEAF, LEAF_LIGHT, LEAF_SHADE, LEAF_DEEP = rgb("#2fbf71"), rgb("#6ee79a"), rgb("#1d8a50"), rgb("#135f37")
WOOD, WOOD_LIGHT, WOOD_SHADE, WOOD_DARK = rgb("#9a6532"), rgb("#c58a4c"), rgb("#6b4220"), rgb("#4a2c14")
GOLD, GOLD_LIGHT = rgb("#ffd75e"), rgb("#fff6c4")
WHITE = rgb("#f2fff7")


# ---- canvas and PNG

def canvas(w, h, fill=CLEAR):
    return [[fill] * w for _ in range(h)]


def put(c, x, y, color):
    if 0 <= y < len(c) and 0 <= x < len(c[0]):
        c[y][x] = color


def write_png(name, c, scale):
    h, w = len(c), len(c[0])
    raw = bytearray()
    for row in c:
        line = bytearray(b"\x00")
        for px in row:
            line += bytes(px) * scale
        raw += bytes(line) * scale

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", w * scale, h * scale, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    path = os.path.join(OUT, name)
    with open(path, "wb") as f:
        f.write(png)
    print(f"{name}: {w * scale}x{h * scale}")


def noise(x, y, seed=0):
    """Deterministic 0..1 value per pixel, for leaf texture and stars."""
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 0xFFFF


# ---- the T-tree mark (32x32 grid)

def tree_mask():
    """Which pixels are canopy (C), trunk (T) or root (R)."""
    m = {}
    canopy_rows = {5: [(9, 11), (14, 17), (20, 22)], 6: [(7, 24)], 7: [(5, 26)], 8: [(4, 27)], 9: [(4, 27)],
                   10: [(4, 27)], 11: [(4, 27)], 12: [(5, 26)], 13: [(7, 24)]}
    for y, spans in canopy_rows.items():
        for a, b in spans:
            for x in range(a, b + 1):
                m[(x, y)] = "C"
    for y in range(14, 25):
        for x in range(13, 19):
            m[(x, y)] = "T"
    for x in range(12, 20):
        m[(x, 24)] = "T"
    for x in range(11, 21):
        m[(x, 25)] = "R"
    for x in (10, 11, 20, 21):
        m[(x, 26)] = "R"
    return m


def draw_mark(c, ox=0, oy=0, outline=True):
    m = tree_mask()
    if outline:
        for (x, y) in list(m):
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
                if (x + dx, y + dy) not in m:
                    put(c, ox + x + dx, oy + y + dy, OUTLINE)
    top = {}
    bottom = {}
    for (x, y), part in m.items():
        if part == "C":
            top[x] = min(top.get(x, 99), y)
            bottom[x] = max(bottom.get(x, -1), y)
    for (x, y), part in m.items():
        if part == "C":
            r = noise(x, y, 7)
            if y - top[x] == 0:
                color = LEAF_LIGHT
            elif bottom[x] - y == 0:
                color = LEAF_DEEP
            elif bottom[x] - y == 1:
                color = LEAF_SHADE
            elif r < 0.18:
                color = LEAF_LIGHT
            elif r > 0.82:
                color = LEAF_SHADE
            else:
                color = LEAF
            # little leaf clusters: brighter on the upper left, darker lower right
            if r < 0.30 and x < 14 and y < 10:
                color = LEAF_LIGHT
            put(c, ox + x, oy + y, color)
        else:
            col = x - 13
            if part == "R":
                color = WOOD_SHADE if x in (10, 21) or y == 26 else WOOD
            elif y == 14:
                color = WOOD_DARK  # the canopy's shadow on the trunk
            elif col <= 0:
                color = WOOD_LIGHT
            elif col >= 4:
                color = WOOD_SHADE
            elif (y + col * 3) % 5 == 0:
                color = WOOD_DARK  # bark lines
            else:
                color = WOOD
            put(c, ox + x, oy + y, color)
    # a knot in the trunk
    put(c, ox + 15, oy + 19, WOOD_DARK)
    put(c, ox + 16, oy + 19, WOOD_SHADE)


def sparkle(c, x, y, big=True):
    put(c, x, y, GOLD_LIGHT)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        put(c, x + dx, y + dy, GOLD)
    if big:
        for dx, dy in ((2, 0), (-2, 0), (0, 2), (0, -2)):
            put(c, x + dx, y + dy, rgb("#c99a2e"))


# ---- badges

def inside_rounded(x, y, size, r):
    cx = min(max(x + 0.5, r), size - r)
    cy = min(max(y + 0.5, r), size - r)
    return (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r


def inside_circle(x, y, size):
    c0 = size / 2
    return (x + 0.5 - c0) ** 2 + (y + 0.5 - c0) ** 2 <= c0 * c0


def badge(shape="rounded", size=32):
    c = canvas(size, size)
    inside = (lambda x, y: inside_rounded(x, y, size, 6)) if shape == "rounded" else (lambda x, y: inside_circle(x, y, size))
    for y in range(size):
        for x in range(size):
            if not inside(x, y):
                continue
            # two tones with a two-row dithered seam
            seam = size // 2
            if y < seam - 1:
                color = BG_TOP
            elif y > seam:
                color = BG_BOTTOM
            else:
                color = BG_TOP if (x + y) % 2 == 0 else BG_BOTTOM
            c[y][x] = color
    # rim: badge pixels next to the outside
    for y in range(size):
        for x in range(size):
            if c[y][x] == CLEAR:
                continue
            edge = any(not (0 <= x + dx < size and 0 <= y + dy < size) or c[y + dy][x + dx] == CLEAR
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge:
                c[y][x] = RIM_LIGHT if (x + y) < size else RIM
    draw_mark(c)
    sparkle(c, 25, 3 if shape == "rounded" else 4, big=False)
    sparkle(c, 6, 27 if shape == "rounded" else 25, big=False)
    return c


# ---- pixel font (5x7, I is 3 wide)

GLYPHS = {
    "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "B": ["####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."],
    "C": [".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."],
    "D": ["####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."],
    "E": ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    "F": ["#####", "#....", "#....", "####.", "#....", "#....", "#...."],
    "G": [".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###."],
    "H": ["#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "I": ["###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"],
    "K": ["#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"],
    "L": ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
    "M": ["#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"],
    "N": ["#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"],
    "O": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "P": ["####.", "#...#", "#...#", "####.", "#....", "#....", "#...."],
    "R": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
    "S": [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
    "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    "U": ["#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "X": ["#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"],
    "Y": ["#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."],
    "Z": ["#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"],
    "·": ["...", "...", "...", ".#.", "...", "...", "..."],
    " ": ["..", "..", "..", "..", "..", "..", ".."],
}


def text_width(text, scale=1, gap=1):
    return sum(len(GLYPHS[ch][0]) * scale + gap * scale for ch in text) - gap * scale


def draw_text(c, text, x, y, scale=1, color=WHITE, shade=None, shadow=None, gap=1):
    """Pixel text; `shade` colours the lower rows of each glyph, `shadow` is a 1px drop shadow."""
    for layer in ("shadow", "fill"):
        if layer == "shadow" and shadow is None:
            continue
        cx = x
        for ch in text:
            g = GLYPHS[ch]
            for gy, row in enumerate(g):
                for gx, bit in enumerate(row):
                    if bit != "#":
                        continue
                    for sy in range(scale):
                        for sx in range(scale):
                            px, py = cx + gx * scale + sx, y + gy * scale + sy
                            if layer == "shadow":
                                put(c, px + 1, py + 1, shadow)
                            else:
                                lower = shade is not None and gy * scale + sy >= len(g) * scale * 0.55
                                put(c, px, py, shade if lower else color)
            cx += len(g[0]) * scale + gap * scale


# ---- banners

BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]

# Bold 8x9 letters for the TAZ wordmark.
BIG = {
    "T": ["########", "########", "...##...", "...##...", "...##...", "...##...", "...##...", "...##...", "...##..."],
    "A": ["..####..", ".######.", "##....##", "##....##", "########", "########", "##....##", "##....##", "##....##"],
    "Z": ["########", "########", ".....##.", "....##..", "...##...", "..##....", ".##.....", "########", "########"],
}


def draw_wordmark(c, text, x, y, scale=2, gap=2):
    """Bold pixel letters with a dark outline and a white-to-mint fill."""
    pixels = set()
    cx = x
    for ch in text:
        g = BIG[ch]
        for gy, row in enumerate(g):
            for gx, bit in enumerate(row):
                if bit == "#":
                    for sy in range(scale):
                        for sx in range(scale):
                            pixels.add((cx + gx * scale + sx, y + gy * scale + sy))
        cx += len(g[0]) * scale + gap * scale
    height = 9 * scale
    for px, py in pixels:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1, 2):
                if (px + dx, py + dy) not in pixels:
                    put(c, px + dx, py + dy, OUTLINE)
    for px, py in pixels:
        t = (py - y) / height
        put(c, px, py, WHITE if t < 0.45 else rgb("#c9f5e2") if t < 0.75 else rgb("#8fe3c0"))


def banner(w, h, logo_y=None, tagline="SKYBLOCK MOD", sub="FORAGING · DUNGEONS · SKYBLOCK XP"):
    c = canvas(w, h)
    # night sky over Galatea: deep blue to teal, dithered bands
    top, mid, low = rgb("#0b1426"), rgb("#13304a"), rgb("#1d5257")
    for y in range(h):
        t = y / (h - 1)
        for x in range(w):
            if t < 0.5:
                a, b, k = top, mid, t / 0.5
            else:
                a, b, k = mid, low, (t - 0.5) / 0.5
            threshold = (BAYER[y % 4][x % 4] + 0.5) / 16  # ordered dither: smooth bands, no stripes
            c[y][x] = b if k > threshold else a
    # stars
    for y in range(int(h * 0.55)):
        for x in range(w):
            r = noise(x, y, 3)
            if r > 0.985:
                put(c, x, y, rgb("#d9ecff") if r > 0.995 else rgb("#7fa3c8"))
    # moon
    mx, my, mr = w - 22, 14, 7
    for y in range(my - mr, my + mr + 1):
        for x in range(mx - mr, mx + mr + 1):
            d = (x - mx) ** 2 + (y - my) ** 2
            if d <= mr * mr:
                put(c, x, y, rgb("#efe9c2") if (x - mx) + (y - my) < 4 else rgb("#d8cf9c"))
    for x, y in ((mx - 2, my - 1), (mx + 2, my + 2), (mx - 1, my + 3)):
        put(c, x, y, rgb("#c4bb88"))
    # far hills, then near hills with little T-trees on them
    for layer, base, amp, color, seed in ((0, h - 20, 4, rgb("#103538"), 1.3), (1, h - 11, 3, rgb("#0a2426"), 2.7)):
        for x in range(w):
            hy = int(base + amp * math.sin(x / 11 + seed) + 1.5 * math.sin(x / 4.3 + seed * 2))
            for y in range(hy, h):
                put(c, x, y, color)
        if layer == 1:
            x = 3
            while x < w - 4:
                hy = int(base + amp * math.sin(x / 11 + seed) + 1.5 * math.sin(x / 4.3 + seed * 2))
                size = 2 + int(noise(x, 1, 9) * 3)
                tree_color = rgb("#0d2e2f")
                for dx in range(-size, size + 1):
                    for dy in range(size):
                        put(c, x + dx, hy - size * 2 - dy, tree_color)
                for dy in range(size * 2):
                    put(c, x, hy - dy, tree_color)
                x += 7 + int(noise(x, 2, 5) * 9)
    # logo badge and wordmark
    logo = badge("rounded")
    lx = 14
    ly = logo_y if logo_y is not None else (h - 32) // 2 - 4
    for y in range(32):
        for x in range(32):
            if logo[y][x] != CLEAR:
                put(c, lx + x, ly + y, logo[y][x])
    tx = lx + 32 + 9
    draw_wordmark(c, "TAZ", tx, ly + 3)
    draw_text(c, tagline, tx + 1, ly + 3 + 18 + 5, scale=1, color=LEAF_LIGHT, shadow=OUTLINE)
    if sub and text_width(sub) <= w - tx - 3:
        draw_text(c, sub, tx + 1, ly + 3 + 18 + 5 + 9, scale=1, color=rgb("#8fb8b0"), shadow=OUTLINE)
    return c


def main():
    rounded = badge("rounded")
    round_ = badge("circle")
    write_png("taz-logo-1024.png", rounded, 32)
    write_png("taz-logo-512.png", rounded, 16)
    write_png("taz-logo-256.png", rounded, 8)
    write_png("taz-logo-128.png", rounded, 4)
    write_png("taz-avatar-round-512.png", round_, 16)
    mark = canvas(32, 32)
    draw_mark(mark)
    sparkle(mark, 25, 3, big=False)
    write_png("taz-mark-transparent-512.png", mark, 16)
    write_png("taz-github-social-1280x640.png", banner(160, 80), 8)
    write_png("taz-discord-banner-960x540.png", banner(160, 90), 6)
    write_png("taz-profile-banner-600x240.png", banner(150, 60, logo_y=10, sub=None), 4)


if __name__ == "__main__":
    main()
