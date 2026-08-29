"""Pixel-art primitives for the Voidforged set."""
from PIL import Image

TRANSPARENT = (0, 0, 0, 0)


def rgba(value):
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), 255)


def rect(x0, y0, x1, y1):
    """Inclusive rectangle as a point set."""
    return {(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)}


def union(*groups):
    out = set()
    for group in groups:
        out |= set(group)
    return out


class Canvas:
    def __init__(self, width=16, height=16):
        self.w = width
        self.h = height
        self.img = Image.new("RGBA", (width, height), TRANSPARENT)
        self.px = self.img.load()

    # -- low level ---------------------------------------------------------

    def inside(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def put(self, x, y, colour):
        if self.inside(x, y):
            self.px[x, y] = rgba(colour) if isinstance(colour, str) else colour

    def get(self, x, y):
        return self.px[x, y] if self.inside(x, y) else TRANSPARENT

    def filled(self, x, y):
        return self.inside(x, y) and self.px[x, y][3] != 0

    # -- plate shading -----------------------------------------------------

    def plate(self, shape, ramp, top=None, bottom=None):
        """
        Fill `shape` with a metal ramp lit from the top-left.

        The base tone comes from vertical position; edges then pick up a step
        of light where they face the source and lose one where they face away.
        That is what turns a flat silhouette into something that reads as
        bevelled plate rather than a coloured blob.
        """
        shape = set(shape)
        if not shape:
            return shape
        ys = [y for _, y in shape]
        top = min(ys) if top is None else top
        bottom = max(ys) if bottom is None else bottom
        span = max(1, bottom - top)

        for (x, y) in shape:
            depth = (y - top) / span
            level = 4 - int(depth * 2.6 + 0.5)          # 4 at the top, ~1 low down
            if (x, y - 1) not in shape:
                level += 1
            if (x - 1, y) not in shape:
                level += 1
            if (x, y + 1) not in shape:
                level -= 1
            if (x + 1, y) not in shape:
                level -= 1
            self.put(x, y, ramp[max(1, min(5, level))])
        return shape

    # -- outline -----------------------------------------------------------

    def outline(self, colour, diagonal=False):
        """Trace a one-pixel outline around everything currently drawn."""
        offsets = [(1, 0), (-1, 0), (0, 1), (0, -1)]
        if diagonal:
            offsets += [(1, 1), (1, -1), (-1, 1), (-1, -1)]
        edge = set()
        for y in range(self.h):
            for x in range(self.w):
                if not self.filled(x, y):
                    continue
                for dx, dy in offsets:
                    if self.inside(x + dx, y + dy) and not self.filled(x + dx, y + dy):
                        edge.add((x + dx, y + dy))
        for (x, y) in edge:
            self.put(x, y, colour)
        return edge

    # -- emissive ----------------------------------------------------------

    ORTHO = ((1, 0), (-1, 0), (0, 1), (0, -1))

    def glow(self, core, ramp, within=None, halo=True):
        """
        An emissive accent, drawn core-out.

        Bloom is deliberately tight: one orthogonal ring at the dimmest step.
        A wide halo at 16x16 swallows the sprite it is supposed to sit on —
        at this resolution the surrounding dark metal is what sells the light.
        """
        core = set(core)
        confine = set(within) if within is not None else None

        def allowed(point):
            if confine is not None:
                return point in confine
            return self.filled(*point)

        if halo:
            ring = set()
            for (x, y) in core:
                for dx, dy in self.ORTHO:
                    point = (x + dx, y + dy)
                    if point not in core and allowed(point):
                        ring.add(point)
            for (x, y) in ring:
                self.put(x, y, ramp[1])

        # Interior of the core burns white; its edge sits one step back, which
        # keeps even a two-pixel accent from reading as a flat blob.
        for (x, y) in core:
            neighbours = sum(1 for dx, dy in self.ORTHO if (x + dx, y + dy) in core)
            self.put(x, y, ramp[4] if neighbours >= 3 else ramp[3])

    def carve(self, points, ramp, within=None):
        """
        A sigil cut into a plate: a dark recess bitten out of the metal with a
        lit line running along the bottom of the cut. Reads as engraved rather
        than painted on.
        """
        points = set(points)
        confine = set(within) if within is not None else None

        recess = set()
        for (x, y) in points:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    point = (x + dx, y + dy)
                    if point in points:
                        continue
                    if confine is not None and point not in confine:
                        continue
                    if self.filled(*point):
                        recess.add(point)

        for (x, y) in recess:
            self.put(x, y, ramp[0])
        for (x, y) in points:
            neighbours = sum(1 for dx, dy in self.ORTHO if (x + dx, y + dy) in points)
            self.put(x, y, ramp[4] if neighbours >= 2 else ramp[3])

    # -- engraving ---------------------------------------------------------

    def engrave(self, points, ramp, shade=1):
        """Cut a darker line into a plate — panel seams, rivets, filigree."""
        for (x, y) in points:
            if self.filled(x, y):
                self.put(x, y, ramp[shade])

    def highlight(self, points, ramp, shade=5):
        for (x, y) in points:
            if self.filled(x, y):
                self.put(x, y, ramp[shade])

    # -- output ------------------------------------------------------------

    def paste(self, other, ox, oy):
        for y in range(other.h):
            for x in range(other.w):
                pixel = other.px[x, y]
                if pixel[3]:
                    self.put(x + ox, y + oy, pixel)

    def save(self, path):
        import os
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.img.save(path)

    def band(self, shape, ramp, light=5, mid=3, dark=1):
        """
        Shade a diagonal band by its position within each row: leftmost pixel
        catches the light, rightmost falls away.

        `plate()` decides shading from which neighbours are empty, which works
        for a blocky silhouette but flips on every pixel of a 45-degree
        staircase — the result reads as a dotted line rather than a blade.
        """
        rows = {}
        for (x, y) in shape:
            rows.setdefault(y, []).append(x)
        for y, xs in rows.items():
            xs.sort()
            for i, x in enumerate(xs):
                if len(xs) == 1:
                    level = mid
                elif i == 0:
                    level = light
                elif i == len(xs) - 1:
                    level = dark
                else:
                    level = mid
                self.put(x, y, ramp[level])
        return set(shape)
