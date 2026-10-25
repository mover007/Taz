"""Draws Taz's logo and banners and writes them as PNGs, with no libraries.

The logo is a black head with angry glowing red eyes, a red aura and TAZ engraved on its forehead.
Shapes are drawn from distance functions (smooth edges and glows); the tagline uses a Windows font
read straight from its .ttf file.
Run: python make_branding.py  (writes next to this file)
"""
import math
import os
import random
import struct
import zlib

OUT = os.path.dirname(os.path.abspath(__file__))
FONT = r"C:\Windows\Fonts\segoeuib.ttf"


def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def hexc(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16) / 255, int(h[2:4], 16) / 255, int(h[4:6], 16) / 255)


# ---- image (premultiplied alpha floats)

class Img:
    def __init__(self, w, h, color=(0, 0, 0), alpha=1.0):
        self.w, self.h = w, h
        n = w * h
        self.r = [color[0] * alpha] * n
        self.g = [color[1] * alpha] * n
        self.b = [color[2] * alpha] * n
        self.a = [alpha] * n

    def over(self, i, c, a):
        """Paints colour c with alpha a over pixel i."""
        if a <= 0.0:
            return
        if a > 1.0:
            a = 1.0
        k = 1.0 - a
        self.r[i] = c[0] * a + self.r[i] * k
        self.g[i] = c[1] * a + self.g[i] * k
        self.b[i] = c[2] * a + self.b[i] * k
        self.a[i] = a + self.a[i] * k

    def write(self, name):
        raw = bytearray()
        for y in range(self.h):
            raw.append(0)
            for x in range(self.w):
                i = y * self.w + x
                a = self.a[i]
                if a <= 0.0:
                    raw += b"\x00\x00\x00\x00"
                    continue
                raw += bytes((int(clamp(self.r[i] / a) * 255 + 0.5), int(clamp(self.g[i] / a) * 255 + 0.5),
                              int(clamp(self.b[i] / a) * 255 + 0.5), int(clamp(a) * 255 + 0.5)))

        def chunk(kind, data):
            return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

        png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
               + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))
        with open(os.path.join(OUT, name), "wb") as f:
            f.write(png)
        print(f"{name}: {self.w}x{self.h}")

    def half(self):
        """Box-filtered copy at half size."""
        out = Img(self.w // 2, self.h // 2)
        for y in range(out.h):
            for x in range(out.w):
                i0 = (2 * y) * self.w + 2 * x
                i1 = i0 + self.w
                j = y * out.w + x
                for ch_in, ch_out in ((self.r, out.r), (self.g, out.g), (self.b, out.b), (self.a, out.a)):
                    ch_out[j] = (ch_in[i0] + ch_in[i0 + 1] + ch_in[i1] + ch_in[i1 + 1]) * 0.25
        return out

    def round_corners(self, radius):
        for y in range(self.h):
            for x in range(self.w):
                cx = min(max(x + 0.5, radius), self.w - radius)
                cy = min(max(y + 0.5, radius), self.h - radius)
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) - radius
                k = clamp(0.5 - d)
                if k < 1.0:
                    i = y * self.w + x
                    self.r[i] *= k
                    self.g[i] *= k
                    self.b[i] *= k
                    self.a[i] *= k


# ---- noise (smoke, aura flicker)

def _hash(ix, iy, seed):
    n = (ix * 374761393 + iy * 668265263 + seed * 982451653) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def vnoise(x, y, seed):
    ix, iy = math.floor(x), math.floor(y)
    fx, fy = x - ix, y - iy
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = _hash(ix, iy, seed), _hash(ix + 1, iy, seed)
    c, d = _hash(ix, iy + 1, seed), _hash(ix + 1, iy + 1, seed)
    return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy


def fbm(x, y, seed, octaves=3):
    v, amp, f, total = 0.0, 0.5, 1.0, 0.0
    for o in range(octaves):
        v += amp * vnoise(x * f, y * f, seed + o * 17)
        total += amp
        amp *= 0.5
        f *= 2.0
    return v / total


# ---- polygon rasteriser (non-zero winding, anti-aliased)

def raster(img, contours, color_at, alpha=1.0, ss=5):
    """Fills polygons (lists of (x, y) pixel points); color_at(x, y) gives the colour."""
    edges = []
    ymin, ymax = 1e9, -1e9
    for c in contours:
        for k in range(len(c)):
            x0, y0 = c[k]
            x1, y1 = c[(k + 1) % len(c)]
            if y0 != y1:
                edges.append((x0, y0, x1, y1))
            ymin, ymax = min(ymin, y0), max(ymax, y0)
    if not edges:
        return
    for py in range(max(0, int(ymin)), min(img.h, int(ymax) + 2)):
        cover = {}
        for s in range(ss):
            sy = py + (s + 0.5) / ss
            xs = []
            for x0, y0, x1, y1 in edges:
                if (y0 <= sy < y1) or (y1 <= sy < y0):
                    xs.append((x0 + (sy - y0) * (x1 - x0) / (y1 - y0), 1 if y1 > y0 else -1))
            xs.sort()
            wind = 0
            for k in range(len(xs) - 1):
                wind += xs[k][1]
                if wind == 0:
                    continue
                a, b = xs[k][0], xs[k + 1][0]
                if b <= a:
                    continue
                ia, ib = int(math.floor(a)), int(math.floor(b))
                if ia == ib:
                    cover[ia] = cover.get(ia, 0.0) + (b - a) / ss
                else:
                    cover[ia] = cover.get(ia, 0.0) + (ia + 1 - a) / ss
                    for px in range(ia + 1, ib):
                        cover[px] = cover.get(px, 0.0) + 1.0 / ss
                    cover[ib] = cover.get(ib, 0.0) + (b - ib) / ss
        for px, cov in cover.items():
            if 0 <= px < img.w and cov > 0.0:
                img.over(py * img.w + px, color_at(px + 0.5, py + 0.5), min(cov, 1.0) * alpha)


def mask_of(w, h, contours, ss=5):
    """Coverage mask (list of floats) of polygons, for glows."""
    m = Img(w, h, (0, 0, 0), 0.0)
    raster(m, contours, lambda x, y: (1, 1, 1), 1.0, ss)
    return m.a


def blur(mask, w, h, radius, passes=3):
    """Box blur, repeated: close to a Gaussian."""
    cur = mask[:]
    for _ in range(passes):
        tmp = [0.0] * (w * h)
        for y in range(h):
            row = y * w
            acc = 0.0
            for x in range(-radius, radius + 1):
                acc += cur[row + min(max(x, 0), w - 1)]
            for x in range(w):
                tmp[row + x] = acc / (2 * radius + 1)
                acc += cur[row + min(x + radius + 1, w - 1)] - cur[row + max(x - radius, 0)]
        out = [0.0] * (w * h)
        for x in range(w):
            acc = 0.0
            for y in range(-radius, radius + 1):
                acc += tmp[min(max(y, 0), h - 1) * w + x]
            for y in range(h):
                out[y * w + x] = acc / (2 * radius + 1)
                acc += tmp[min(y + radius + 1, h - 1) * w + x] - tmp[max(y - radius, 0) * w + x]
        cur = out
    return cur


# ---- TrueType reader (just enough for Latin capitals)

class Font:
    def __init__(self, path):
        with open(path, "rb") as f:
            self.d = f.read()
        num = struct.unpack(">H", self.d[4:6])[0]
        self.t = {}
        for k in range(num):
            tag, _, off, length = struct.unpack(">4sIII", self.d[12 + 16 * k:28 + 16 * k])
            self.t[tag.decode("latin-1")] = off
        head = self.t["head"]
        self.upem = struct.unpack(">H", self.d[head + 18:head + 20])[0]
        self.long_loca = struct.unpack(">h", self.d[head + 50:head + 52])[0] == 1
        self.n_hmetrics = struct.unpack(">H", self.d[self.t["hhea"] + 34:self.t["hhea"] + 36])[0]
        self.cmap = self._cmap()

    def _u16(self, o):
        return struct.unpack(">H", self.d[o:o + 2])[0]

    def _cmap(self):
        base = self.t["cmap"]
        n = self._u16(base + 2)
        sub = None
        for k in range(n):
            pid, eid, off = struct.unpack(">HHI", self.d[base + 4 + 8 * k:base + 12 + 8 * k])
            if (pid, eid) in ((3, 1), (0, 3)) and self._u16(base + off) == 4:
                sub = base + off
                break
        seg = self._u16(sub + 6) // 2
        ends = sub + 14
        starts = ends + 2 * seg + 2
        deltas = starts + 2 * seg
        ranges = deltas + 2 * seg
        table = {}
        for s in range(seg):
            end, start = self._u16(ends + 2 * s), self._u16(starts + 2 * s)
            delta = struct.unpack(">h", self.d[deltas + 2 * s:deltas + 2 * s + 2])[0]
            ro = self._u16(ranges + 2 * s)
            for c in range(start, min(end, 0x2FF) + 1):
                if ro == 0:
                    g = (c + delta) & 0xFFFF
                else:
                    g = self._u16(ranges + 2 * s + ro + 2 * (c - start))
                    g = (g + delta) & 0xFFFF if g else 0
                table[c] = g
        return table

    def advance(self, g):
        hm = self.t["hmtx"]
        k = min(g, self.n_hmetrics - 1)
        return self._u16(hm + 4 * k)

    def _loca(self, g):
        lo = self.t["loca"]
        if self.long_loca:
            return struct.unpack(">II", self.d[lo + 4 * g:lo + 4 * g + 8])
        a, b = struct.unpack(">HH", self.d[lo + 2 * g:lo + 2 * g + 4])
        return a * 2, b * 2

    def contours(self, g, dx=0, dy=0):
        start, end = self._loca(g)
        if start == end:
            return []
        o = self.t["glyf"] + start
        nc = struct.unpack(">h", self.d[o:o + 2])[0]
        if nc < 0:  # composite: offset parts only
            out, p = [], o + 10
            while True:
                flags, sub = struct.unpack(">HH", self.d[p:p + 4])
                p += 4
                if flags & 1:
                    ax, ay = struct.unpack(">hh", self.d[p:p + 4])
                    p += 4
                else:
                    ax, ay = struct.unpack(">bb", self.d[p:p + 2])
                    p += 2
                p += 2 if flags & 0x08 else 4 if flags & 0x40 else 8 if flags & 0x80 else 0
                out += self.contours(sub, dx + ax, dy + ay)
                if not flags & 0x20:
                    return out
        ends = [self._u16(o + 10 + 2 * k) for k in range(nc)]
        p = o + 10 + 2 * nc
        p += 2 + self._u16(p)
        n = ends[-1] + 1
        flags = []
        while len(flags) < n:
            f = self.d[p]
            p += 1
            flags.append(f)
            if f & 8:
                r = self.d[p]
                p += 1
                flags += [f] * r
        xs, ys, v = [], [], 0
        for f in flags:
            if f & 2:
                v += self.d[p] if f & 16 else -self.d[p]
                p += 1
            elif not f & 16:
                v += struct.unpack(">h", self.d[p:p + 2])[0]
                p += 2
            xs.append(v)
        v = 0
        for f in flags:
            if f & 4:
                v += self.d[p] if f & 32 else -self.d[p]
                p += 1
            elif not f & 32:
                v += struct.unpack(">h", self.d[p:p + 2])[0]
                p += 2
            ys.append(v)
        out, s = [], 0
        for e in ends:
            pts = [(xs[k] + dx, ys[k] + dy, flags[k] & 1) for k in range(s, e + 1)]
            s = e + 1
            out.append(_flatten(pts))
        return out


def _flatten(pts):
    """TrueType contour (quadratic curves) to a polyline."""
    if not any(p[2] for p in pts):
        a, b = pts[0], pts[1]
        pts = [((a[0] + b[0]) / 2, (a[1] + b[1]) / 2, 1)] + pts
    k = next(i for i, p in enumerate(pts) if p[2])
    pts = pts[k:] + pts[:k]
    out = [(pts[0][0], pts[0][1])]
    n = len(pts)
    i = 1
    cur = pts[0]
    while i <= n:
        p = pts[i % n]
        if p[2]:
            out.append((p[0], p[1]))
            cur = p
            i += 1
            continue
        nxt = pts[(i + 1) % n]
        end = nxt if nxt[2] else ((p[0] + nxt[0]) / 2, (p[1] + nxt[1]) / 2, 1)
        for s in range(1, 7):
            t = s / 6
            out.append(((1 - t) ** 2 * cur[0] + 2 * (1 - t) * t * p[0] + t * t * end[0],
                        (1 - t) ** 2 * cur[1] + 2 * (1 - t) * t * p[1] + t * t * end[1]))
        cur = end
        i += 1 if not nxt[2] else 2
    return out


def text_contours(font, text, x, baseline, size, spacing=0.0):
    """Polygons for a line of text, and its width."""
    scale = size / font.upem
    out, pen = [], x
    for ch in text:
        g = font.cmap.get(ord(ch), 0)
        for c in font.contours(g):
            out.append([(pen + px * scale, baseline - py * scale) for px, py in c])
        pen += font.advance(g) * scale + spacing * size
    return out, pen - x - spacing * size


# ---- the TAZ logotype: sharp, angular letters (unit height 1)

LETTERS = {
    "T": (0.92, [[(0.0, 0.0), (0.92, 0.0), (0.86, 0.20), (0.06, 0.20)],
                 [(0.33, 0.20), (0.59, 0.20), (0.59, 1.0), (0.33, 1.0)]]),
    "A": (1.0, [[(0.42, 0.0), (0.58, 0.0), (1.0, 1.0), (0.75, 1.0), (0.67, 0.79), (0.33, 0.79), (0.25, 1.0), (0.0, 1.0)],
                [(0.5, 0.30), (0.39, 0.60), (0.61, 0.60)]]),
    "Z": (0.88, [[(0.04, 0.0), (0.88, 0.0), (0.88, 0.17), (0.33, 0.81), (0.88, 0.81), (0.84, 1.0), (0.0, 1.0),
                  (0.0, 0.83), (0.55, 0.19), (0.0, 0.19)]]),
}


def logo_contours(text, x, y, height, gap=0.13):
    out, pen = [], x
    for ch in text:
        width, polys = LETTERS[ch]
        for poly in polys:
            out.append([(pen + px * height, y + py * height) for px, py in poly])
        pen += (width + gap) * height
    return out, pen - x - gap * height


# ---- the head

RED_HOT = (1.0, 0.13, 0.07)
RED_DEEP = (0.42, 0.0, 0.01)


def eye_sdf(x, y):
    """Left eye in head units: an ellipse cut by a slanted brow line (inner corner lower: angry)."""
    cx, cy, rx, ry = -0.37, 0.07, 0.29, 0.16
    d_ell = (math.hypot((x - cx) / rx, (y - cy) / ry) - 1.0) * ry
    ax, ay, bx, by = -0.70, -0.03, -0.05, 0.145
    vx, vy = bx - ax, by - ay
    ln = math.hypot(vx, vy)
    d_line = (x - ax) * (vy / ln) + (y - ay) * (-vx / ln)
    return max(d_ell, d_line)


def pupil_sdf(x, y):
    return (math.hypot((x + 0.33) / 0.028, (y - 0.135) / 0.072) - 1.0) * 0.028


def draw_head(img, hx, hy, R, letters=True, aura=True, tight=False):
    w, h = img.w, img.h
    pix = 1.0 / R
    ext = 1.02 if not aura else 1.4 if tight else 1.75
    x0, x1 = max(0, int(hx - ext * R)), min(w, int(hx + ext * R) + 1)
    y0, y1 = max(0, int(hy - ext * R)), min(h, int(hy + ext * R) + 1)
    # aura and head
    for py in range(y0, y1):
        for px in range(x0, x1):
            i = py * w + px
            x, y = (px + 0.5 - hx) / R, (py + 0.5 - hy) / R
            r = math.hypot(x, y)
            if aura and r > 0.97:
                d = r - 1.0
                ang = math.atan2(y, x)
                flick = 0.45 + 1.1 * fbm(math.cos(ang) * 2.4 + 5.0, math.sin(ang) * 2.4 - d * 3.0, 21)
                if tight:
                    a = 0.95 * math.exp(-max(d, 0) / 0.07) * flick
                    img.over(i, lerp(RED_HOT, (0.8, 0.02, 0.02), clamp(d / 0.25)), clamp(a))
                else:
                    a = (0.85 * math.exp(-max(d, 0) / 0.09) + 0.40 * math.exp(-max(d, 0) / 0.30)) * flick
                    img.over(i, lerp(RED_HOT, RED_DEEP, clamp(d / 0.45)), clamp(a) * 0.92)
            cov = clamp(0.5 - (r - 1.0) / pix)
            if cov > 0.0:
                t = clamp(math.hypot(x + 0.38, y + 0.48) / 1.45)
                base = lerp((0.115, 0.105, 0.11), (0.012, 0.008, 0.009), t ** 0.8)
                e = 1.0 - r
                facing = max(0.0, (x * 0.5 + y * 0.866) / max(r, 1e-6))
                rim = max(0.0, 1.0 - e / 0.075) ** 2 * (0.18 + 0.82 * facing)
                img.over(i, (base[0] + rim * 0.62, base[1] + rim * 0.03, base[2] + rim * 0.03), cov)
    # TAZ engraved on the forehead: a red-lit lower edge under dark letters
    if letters:
        lh = 0.205 * R
        _, width = logo_contours("TAZ", 0, 0, lh)
        lx, ly = hx - width / 2, hy - 0.60 * R
        edge, _ = logo_contours("TAZ", lx, ly + 0.014 * R, lh)
        raster(img, edge, lambda x, y: (0.40, 0.04, 0.04), 0.9)
        body, _ = logo_contours("TAZ", lx, ly, lh)
        raster(img, body, lambda x, y: (0.055, 0.008, 0.01), 1.0)
    # eyes: glow, then the eye, then the slit pupil
    ey0, ey1 = max(0, int(hy - 0.45 * R)), min(h, int(hy + 0.65 * R) + 1)
    ex0, ex1 = max(0, int(hx - 1.05 * R)), min(w, int(hx + 1.05 * R) + 1)
    for py in range(ey0, ey1):
        for px in range(ex0, ex1):
            i = py * w + px
            x, y = (px + 0.5 - hx) / R, (py + 0.5 - hy) / R
            xm = x if x < 0 else -x
            d = eye_sdf(xm, y)
            if d > 0:
                g = 0.95 * math.exp(-d / 0.03) + 0.45 * math.exp(-d / 0.11)
                img.over(i, RED_HOT, clamp(g) * 0.85)
            cov = clamp(0.5 - d / pix)
            if cov > 0.0:
                t = clamp(-d / 0.075)
                col = lerp((0.78, 0.0, 0.02), RED_HOT, clamp(t * 1.6)) if t < 0.62 else lerp(RED_HOT, (1.0, 0.82, 0.74), (t - 0.62) / 0.38)
                img.over(i, col, cov)
                pc = clamp(0.5 - pupil_sdf(xm, y) / pix) * cov
                if pc > 0.0:
                    img.over(i, (0.10, 0.0, 0.0), pc * 0.92)


def red_backdrop(img, cx, cy, spread, smoke=True):
    w, h = img.w, img.h
    for py in range(h):
        for px in range(w):
            x, y = (px + 0.5) / w, (py + 0.5) / h
            t = clamp(math.hypot((x - cx) * w / h, y - cy) / spread)
            col = lerp((0.32, 0.015, 0.03), (0.025, 0.0, 0.002), t ** 0.9)
            if smoke:
                n = fbm(x * 3.2 * w / h, y * 3.2, 7)
                col = (col[0] * (0.75 + 0.5 * n), col[1], col[2] * (0.8 + 0.4 * n))
            i = py * w + px
            img.r[i], img.g[i], img.b[i], img.a[i] = col[0], col[1], col[2], 1.0


def embers(img, count, seed, area):
    rnd = random.Random(seed)
    x0, y0, x1, y1 = area
    for _ in range(count):
        ex, ey = rnd.uniform(x0, x1), rnd.uniform(y0, y1)
        size = rnd.uniform(0.6, 2.2) * img.h / 640
        hot = rnd.random()
        col = lerp((1.0, 0.25, 0.08), (1.0, 0.65, 0.35), hot)
        rr = int(size * 5) + 2
        for py in range(max(0, int(ey - rr)), min(img.h, int(ey + rr) + 1)):
            for px in range(max(0, int(ex - rr)), min(img.w, int(ex + rr) + 1)):
                d = math.hypot(px + 0.5 - ex, py + 0.5 - ey)
                a = clamp(1.0 - (d - size) ) if d < size + 1 else 0.0
                glow = 0.5 * math.exp(-d / (size * 2.2))
                img.over(py * img.w + px, col, clamp(max(a, glow)) * 0.85)


# ---- outputs

def avatar(size):
    img = Img(size, size)
    red_backdrop(img, 0.5, 0.5, 0.78)
    draw_head(img, size * 0.5, size * 0.53, size * 0.33)
    return img


def mark(size):
    img = Img(size, size, (0, 0, 0), 0.0)
    draw_head(img, size * 0.5, size * 0.5, size * 0.34, tight=True)
    return img


def banner(w, h, tagline="HYPIXEL SKYBLOCK  ·  DUNGEONS", big=True):
    img = Img(w, h)
    R = h * (0.27 if big else 0.33)
    th = h * (0.25 if big else 0.31)
    _, width = logo_contours("TAZ", 0, 0, th)
    gap = R * 0.5
    left = (w - (2 * R + gap + width)) / 2
    hx, hy = left + R, h * 0.5
    # dark red fog, brighter low and behind the head
    for py in range(h):
        for px in range(w):
            x, y = (px + 0.5) / w, (py + 0.5) / h
            n = fbm(x * 4.0 * w / h, y * 4.0, 3)
            glow = math.exp(-((x - hx / w) * w / h) ** 2 / 0.35 - (y - 0.55) ** 2 / 0.35)
            low = clamp((y - 0.35) / 0.65)
            k = 0.05 + 0.20 * glow + 0.13 * low * n + 0.05 * n
            vign = 1.0 - 0.55 * clamp(math.hypot(x - 0.5, (y - 0.5) * 1.2) / 0.75) ** 2
            i = py * w + px
            img.r[i], img.g[i], img.b[i] = k * 1.0 * vign, k * 0.02 * vign, k * 0.06 * vign
    embers(img, int(w * h / 6000), 5, (0, h * 0.25, w, h))
    draw_head(img, hx, hy, R)
    # TAZ wordmark with a red glow; the tagline sits under it
    font = Font(FONT) if tagline else None
    size = h * (0.05 if big else 0.07)
    if font:
        _, tw = text_contours(font, tagline, 0, 0, size, spacing=0.12)
        size = min(h * 0.07, size * width / tw)
    block = th + (size * 2.2 if font else 0)
    tx, ty = hx + R + gap, hy - block / 2
    letters, _ = logo_contours("TAZ", tx, ty, th)
    pad = int(th * 0.5)
    bx0, by0 = max(0, int(tx - pad)), max(0, int(ty - pad))
    bw, bh = min(w, int(tx + width + pad)) - bx0, min(h, int(ty + th + pad)) - by0
    local = [[(px - bx0, py - by0) for px, py in c] for c in letters]
    glow = blur(mask_of(bw, bh, local), bw, bh, max(2, int(th * 0.09)))
    for yy in range(bh):
        for xx in range(bw):
            g = glow[yy * bw + xx]
            if g > 0.002:
                img.over((by0 + yy) * w + bx0 + xx, RED_HOT, clamp(g * 1.6) * 0.9)
    raster(img, letters, lambda x, y: lerp((1.0, 0.97, 0.96), (1.0, 0.18, 0.12), clamp((y - ty) / th) ** 1.3))
    if font:
        _, tw = text_contours(font, tagline, 0, 0, size, spacing=0.12)
        tc, _ = text_contours(font, tagline, tx + (width - tw) / 2, ty + th + size * 2.2, size, spacing=0.12)
        raster(img, tc, lambda x, y: (0.80, 0.30, 0.30))
    return img


def main():
    big = avatar(1024)
    big.write("taz-avatar-1024.png")
    a512 = big.half()
    a512.write("taz-avatar-512.png")
    logo = avatar(1024)
    logo.round_corners(1024 * 0.18)
    logo.write("taz-logo-1024.png")
    l512 = logo.half()
    l512.write("taz-logo-512.png")
    l256 = l512.half()
    l256.write("taz-logo-256.png")
    l256.half().write("taz-logo-128.png")
    m = mark(1024).half()
    m.write("taz-mark-transparent-512.png")
    banner(1280, 640).write("taz-github-social-1280x640.png")
    banner(960, 540).write("taz-discord-banner-960x540.png")
    banner(600, 240, tagline="DUNGEONS  ·  SKYBLOCK", big=False).write("taz-profile-banner-600x240.png")


if __name__ == "__main__":
    main()
