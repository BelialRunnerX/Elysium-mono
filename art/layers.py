"""
Armour layer sheets, painted against the real humanoid UV map.

A 64x32 armour sheet is not a free canvas — it is six unwrapped box faces per
body part, at fixed coordinates. Filling it with an even texture (which is what
this project shipped before) produces armour that reads as a patterned blanket,
because nothing lines up with the shape underneath.

  layer_1 renders  helmet (head box) · chestplate (body + arm boxes)
                   · boots (lower leg box)
  layer_2 renders  leggings (leg box + waist of the body box)

Faces below are (x0, y0, x1, y1) inclusive.
"""
from canvas import Canvas, rect
from style import METAL, GLOW

HEAD = {
    "top":    (8, 0, 15, 7),
    "bottom": (16, 0, 23, 7),
    "right":  (0, 8, 7, 15),
    "front":  (8, 8, 15, 15),
    "left":   (16, 8, 23, 15),
    "back":   (24, 8, 31, 15),
}

BODY = {
    "top":    (20, 16, 27, 19),
    "bottom": (28, 16, 35, 19),
    "right":  (16, 20, 19, 31),
    "front":  (20, 20, 27, 31),
    "left":   (28, 20, 31, 31),
    "back":   (32, 20, 39, 31),
}

ARM = {
    "top":    (44, 16, 47, 19),
    "bottom": (48, 16, 51, 19),
    "right":  (40, 20, 43, 31),
    "front":  (44, 20, 47, 31),
    "left":   (48, 20, 51, 31),
    "back":   (52, 20, 55, 31),
}

LEG = {
    "top":    (4, 16, 7, 19),
    "bottom": (8, 16, 11, 19),
    "right":  (0, 20, 3, 31),
    "front":  (4, 20, 7, 31),
    "left":   (8, 20, 11, 31),
    "back":   (12, 20, 15, 31),
}

SIDES = ("right", "front", "left", "back")


def face(c, box, name, ramp, y_from=None, y_to=None, base=3):
    """Paint one box face with a top-lit vertical ramp and darkened edges."""
    x0, y0, x1, y1 = box[name]
    top = y0 if y_from is None else y_from
    bottom = y1 if y_to is None else y_to
    span = max(1, bottom - top)
    for y in range(top, bottom + 1):
        depth = (y - top) / span
        level = base + 1 - int(depth * 2.2 + 0.5)
        level = max(1, min(5, level))
        for x in range(x0, x1 + 1):
            shade = level
            if x == x0 or x == x1:
                shade = max(1, shade - 1)
            if y == top:
                shade = min(5, shade + 1)
            if y == bottom:
                shade = max(1, shade - 1)
            c.put(x, y, ramp[shade])
    return (x0, top, x1, bottom)


def band(c, box, names, ramp, y, shade=1):
    """A horizontal seam running all the way around a box."""
    for name in names:
        x0, _, x1, _ = box[name]
        for x in range(x0, x1 + 1):
            c.put(x, y, ramp[shade])


def glow_band(c, box, names, ramp, y, step=2):
    """An energy line running around a box, brightest at regular intervals."""
    for name in names:
        x0, _, x1, _ = box[name]
        for x in range(x0, x1 + 1):
            c.put(x, y, ramp[1])
        for x in range(x0, x1 + 1, step):
            c.put(x, y, ramp[3])
            c.put(x, y - 1, ramp[0])


# ==========================================================================

def layer_one(metal, glow):
    """Helmet, chestplate and boots."""
    ramp = METAL[metal]
    lit = GLOW[glow]
    c = Canvas(64, 32)

    # ---- helmet ----------------------------------------------------------
    for name in SIDES:
        face(c, HEAD, name, ramp, base=3)
    face(c, HEAD, "top", ramp, base=4)
    x0, y0, x1, y1 = HEAD["bottom"]
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            c.put(x, y, ramp[1])

    # Brow band right around the helm, then a visor cut into the front.
    band(c, HEAD, SIDES, ramp, 10, shade=1)
    fx0, _, fx1, _ = HEAD["front"]
    for x in range(fx0 + 1, fx1):
        c.put(x, 11, ramp[1])
        c.put(x, 12, ramp[1])
    c.glow([(x, 12) for x in range(fx0 + 2, fx1 - 1)], lit,
           within=rect(fx0, 11, fx1, 13), halo=True)

    # Temple vents on both side faces.
    for name in ("right", "left"):
        sx0, _, sx1, _ = HEAD[name]
        c.glow([(sx0 + 2, 12), (sx0 + 5, 12)], lit,
               within=rect(sx0, 11, sx1, 13), halo=False)

    # Crest running front-to-back over the crown.
    tx0, ty0, tx1, ty1 = HEAD["top"]
    for y in range(ty0, ty1 + 1):
        c.put(tx0 + 3, y, ramp[5])
        c.put(tx0 + 4, y, ramp[1])

    # ---- chestplate ------------------------------------------------------
    for name in SIDES:
        face(c, BODY, name, ramp, base=3)
    face(c, BODY, "top", ramp, base=5)
    bx0, by0, bx1, by1 = BODY["bottom"]
    for y in range(by0, by1 + 1):
        for x in range(bx0, bx1 + 1):
            c.put(x, y, ramp[1])

    band(c, BODY, SIDES, ramp, 26, shade=1)
    band(c, BODY, SIDES, ramp, 30, shade=1)

    # Chest core on the front face, spine ridge on the back.
    fx0, _, fx1, _ = BODY["front"]
    c.glow([(fx0 + 3, 23), (fx0 + 4, 23), (fx0 + 3, 24), (fx0 + 4, 24)], lit,
           within=rect(fx0, 20, fx1, 31))
    kx0, _, kx1, _ = BODY["back"]
    for y in range(21, 30):
        c.put(kx0 + 3, y, ramp[5])
        c.put(kx0 + 4, y, ramp[1])

    # ---- arms: pauldron over vambrace ------------------------------------
    for name in SIDES:
        face(c, ARM, name, ramp, y_from=20, y_to=23, base=5)
        face(c, ARM, name, ramp, y_from=24, y_to=31, base=3)
    face(c, ARM, "top", ramp, base=5)
    ax0, ay0, ax1, ay1 = ARM["bottom"]
    for y in range(ay0, ay1 + 1):
        for x in range(ax0, ax1 + 1):
            c.put(x, y, ramp[1])
    band(c, ARM, SIDES, ramp, 24, shade=1)
    glow_band(c, ARM, SIDES, lit, 23, step=3)

    # ---- boots: the lower four rows of the leg box ------------------------
    for name in SIDES:
        face(c, LEG, name, ramp, y_from=28, y_to=31, base=4)
    sx0, sy0, sx1, sy1 = LEG["bottom"]
    for y in range(sy0, sy1 + 1):
        for x in range(sx0, sx1 + 1):
            c.put(x, y, ramp[1])
    band(c, LEG, SIDES, ramp, 28, shade=1)
    glow_band(c, LEG, SIDES, lit, 31, step=2)

    return c


def layer_two(metal, glow):
    """Leggings: the leg box, plus a belt around the waist of the body box."""
    ramp = METAL[metal]
    lit = GLOW[glow]
    c = Canvas(64, 32)

    # ---- belt ------------------------------------------------------------
    for name in SIDES:
        face(c, BODY, name, ramp, y_from=27, y_to=31, base=4)
    band(c, BODY, SIDES, ramp, 27, shade=1)
    fx0, _, fx1, _ = BODY["front"]
    c.glow([(fx0 + 3, 29), (fx0 + 4, 29)], lit, within=rect(fx0, 27, fx1, 31))

    # ---- legs ------------------------------------------------------------
    for name in SIDES:
        face(c, LEG, name, ramp, y_from=20, y_to=31, base=3)
    face(c, LEG, "top", ramp, base=5)

    band(c, LEG, SIDES, ramp, 20, shade=5)
    band(c, LEG, SIDES, ramp, 24, shade=1)   # thigh seam
    band(c, LEG, SIDES, ramp, 27, shade=1)   # knee plate top
    glow_band(c, LEG, SIDES, lit, 26, step=3)

    return c
