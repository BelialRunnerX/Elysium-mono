"""
Sprites for the seven trinket slots.

One shape per slot rather than one per trinket. Forty distinct silhouettes at
16x16 would be forty things a player cannot tell apart at inventory size; seven
silhouettes that say *where it goes*, coloured by the element that says *what it
does*, is a reading a player can do at a glance and without the tooltip.

That is the same division the rest of the set already uses — silhouette carries
the kind, the glow carries the element — applied to a category where the kind is
the slot.
"""
from canvas import Canvas, rect, union
from style import METAL, GLOW


# ==========================================================================
# Ring — a band seen at a slight angle, so it reads as a ring and not an O
# ==========================================================================

RING_OUTER = union(
    rect(6, 3, 9, 3),
    rect(4, 4, 11, 5),
    rect(3, 6, 12, 10),
    rect(4, 11, 11, 12),
    rect(6, 13, 9, 13),
)

RING_HOLE = union(
    rect(6, 5, 9, 5),
    rect(5, 6, 10, 10),
    rect(6, 11, 9, 11),
)

RING = RING_OUTER - RING_HOLE


def ring(metal, glow):
    """A plain band with a stone set at the top."""
    c = Canvas()
    c.plate(RING, METAL[metal])
    # The setting: two pixels at the crown, lit, so the eye starts there.
    c.glow([(7, 3), (8, 3), (7, 4), (8, 4)], GLOW[glow], within=RING)
    c.outline(METAL[metal][0])
    return c


# ==========================================================================
# Necklace — a chain that hangs, with the weight at the bottom
# ==========================================================================

NECK_CHAIN = union(
    rect(4, 2, 4, 4), rect(11, 2, 11, 4),
    rect(5, 5, 5, 6), rect(10, 5, 10, 6),
    rect(6, 7, 6, 7), rect(9, 7, 9, 7),
    rect(7, 8, 8, 8),
)

NECK_STONE = union(
    rect(6, 9, 9, 9),
    rect(5, 10, 10, 12),
    rect(6, 13, 9, 13),
    rect(7, 14, 8, 14),
)


def necklace(metal, glow):
    """A hung pendant. The chain is thin so the stone carries the sprite."""
    c = Canvas()
    ramp = METAL[metal]
    for (x, y) in NECK_CHAIN:
        c.put(x, y, ramp[3])
    c.plate(NECK_STONE, ramp)
    c.carve(union(rect(7, 10, 8, 12)), GLOW[glow], within=NECK_STONE)
    c.outline(ramp[0])
    return c


# ==========================================================================
# Belt — a strap across the sprite with a buckle at its centre
# ==========================================================================

BELT_STRAP = union(rect(1, 6, 14, 9))
BELT_BUCKLE = union(rect(6, 5, 10, 10))
BELT_TONGUE = union(rect(7, 7, 9, 8))


def belt(metal, glow):
    """Horizontal, which no other trinket is: unmistakable at a glance."""
    c = Canvas()
    ramp = METAL[metal]
    for (x, y) in BELT_STRAP - BELT_BUCKLE:
        c.put(x, y, ramp[2] if y in (6, 9) else ramp[3])
    c.plate(BELT_BUCKLE - BELT_TONGUE, ramp)
    c.glow([(x, y) for (x, y) in BELT_TONGUE], GLOW[glow], within=BELT_BUCKLE)
    c.outline(ramp[0])
    return c


# ==========================================================================
# Charm — a hung token, deliberately irregular so it is not a second necklace
# ==========================================================================

CHARM_CORD = union(rect(7, 1, 8, 3))

CHARM_BODY = union(
    rect(6, 4, 9, 4),
    rect(4, 5, 11, 7),
    rect(3, 8, 12, 10),
    rect(5, 11, 10, 12),
    rect(7, 13, 8, 13),
)


def charm(metal, glow):
    """A flat token on a cord, with a hole bored through it."""
    c = Canvas()
    ramp = METAL[metal]
    for (x, y) in CHARM_CORD:
        c.put(x, y, ramp[2])
    c.plate(CHARM_BODY, ramp)
    # Bored through, not painted on: the hole is the negative space that makes
    # a token read as a token rather than as a coin.
    for (x, y) in union(rect(7, 7, 8, 8)):
        c.put(x, y, (0, 0, 0, 0))
    c.glow([(6, 6), (9, 6), (6, 9), (9, 9)], GLOW[glow], within=CHARM_BODY)
    c.outline(ramp[0])
    return c


# ==========================================================================
# Hands — a gauntlet, fingers down
# ==========================================================================

HAND_CUFF = union(rect(4, 2, 11, 4))
HAND_PALM = union(rect(4, 5, 11, 10))
HAND_FINGERS = union(
    rect(4, 11, 5, 13),
    rect(7, 11, 8, 14),
    rect(10, 11, 11, 13),
)


def hands(metal, glow):
    """A gauntlet. Three fingers, because four at this size is a smear."""
    c = Canvas()
    ramp = METAL[metal]
    c.plate(union(HAND_CUFF, HAND_PALM, HAND_FINGERS), ramp)
    c.engrave([(x, 4) for x in range(4, 12)], ramp, shade=1)
    c.engrave([(x, 10) for x in range(4, 12)], ramp, shade=1)
    c.glow([(7, 7), (8, 7)], GLOW[glow], within=HAND_PALM)
    c.outline(ramp[0])
    return c


# ==========================================================================
# Back — a mantle, wide at the shoulders and tapering
# ==========================================================================

BACK_MANTLE = union(
    rect(3, 3, 12, 4),
    rect(2, 5, 13, 7),
    rect(3, 8, 12, 10),
    rect(4, 11, 11, 12),
    rect(6, 13, 9, 13),
)

BACK_CLASP = union(rect(7, 3, 8, 5))


def back(metal, glow):
    """A shoulder mantle, clasped at the throat."""
    c = Canvas()
    ramp = METAL[metal]
    c.plate(BACK_MANTLE - BACK_CLASP, ramp)
    # Folds, which is the only thing that keeps a wide shape from reading flat.
    for x in (5, 8, 11):
        c.engrave([(x, y) for y in range(6, 12)], ramp, shade=1)
    c.glow([(x, y) for (x, y) in BACK_CLASP], GLOW[glow], within=BACK_MANTLE)
    c.outline(ramp[0])
    return c


# ==========================================================================
# Head — a lens on a band, worn over one eye
# ==========================================================================

HEAD_BAND = union(rect(1, 6, 14, 8))
HEAD_RIM = union(
    rect(8, 3, 12, 3),
    rect(7, 4, 13, 4),
    rect(6, 5, 14, 10),
    rect(7, 11, 13, 11),
    rect(8, 12, 12, 12),
)
HEAD_GLASS = union(
    rect(9, 5, 11, 5),
    rect(8, 6, 12, 9),
    rect(9, 10, 11, 10),
)


def head(metal, glow):
    """A loupe on a headband — the shape every 'seeing' trinket wants."""
    c = Canvas()
    ramp = METAL[metal]
    for (x, y) in HEAD_BAND - HEAD_RIM:
        c.put(x, y, ramp[2] if y == 8 else ramp[3])
    c.plate(HEAD_RIM - HEAD_GLASS, ramp)
    for (x, y) in HEAD_GLASS:
        c.put(x, y, GLOW[glow][2 if (x + y) % 3 else 3])
    c.outline(ramp[0])
    return c


SHAPES = {
    "ring": ring,
    "necklace": necklace,
    "belt": belt,
    "charm": charm,
    "hands": hands,
    "back": back,
    "head": head,
}
