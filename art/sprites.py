"""Item and block sprites for the Voidforged set."""
import random

from canvas import Canvas, rect, union
from style import METAL, GLOW


# ==========================================================================
# Materials
# ==========================================================================

# Sized against vanilla's own ingot rather than by eye.
#
# Vanilla's is sixteen pixels across at its widest and twelve tall, with a long
# flat run through the middle and hard 45-degree ends. Ours was thirteen across
# and eight tall with every edge rounded, which is the difference between "cast
# bar" and "pebble" - and it was the most-seen sprite in the mod, drawn once per
# material for twenty-six of them.
#
# Every shape here is drawn a pixel lean because outline() grows the silhouette
# by one in each direction, so these numbers are two smaller than what a player
# sees.
INGOT = union(
    rect(7, 3, 12, 3),
    rect(5, 4, 13, 4),
    rect(3, 5, 14, 5),
    rect(1, 6, 14, 6),
    rect(1, 7, 14, 7),
    rect(1, 8, 13, 8),
    rect(2, 9, 12, 9),
    rect(4, 10, 10, 10),
)


# The three rows the light catches: a bar is a box seen from slightly above, and
# the top face has to be its own value or the whole thing reads as a lozenge.
INGOT_TOP = union(rect(7, 3, 12, 3), rect(5, 4, 13, 4), rect(3, 5, 14, 5))
INGOT_BODY = INGOT - INGOT_TOP


def ingot(metal, glow):
    """
    A cast bar. The top face is painted as its own plate a full two steps
    lighter than the body — a smooth gradient across the whole silhouette
    reads as a pebble, a hard value break reads as a folded edge.
    """
    ramp = METAL[metal]
    c = Canvas()
    for (x, y) in INGOT_BODY:
        c.put(x, y, ramp[3] if y <= 8 else ramp[2])
    for (x, y) in INGOT_TOP:
        c.put(x, y, ramp[5] if y == 3 else ramp[4])
    # Fold line where the top face meets the body, then the lit underside.
    c.engrave([(x, 6) for x in range(2, 14)], ramp, shade=1)
    c.engrave([(x, 9) for x in range(3, 12)], ramp, shade=1)
    c.glow([(9, 3), (10, 3)], GLOW[glow], within=INGOT_TOP)
    c.outline(ramp[0])
    return c


SHARD = union(
    rect(7, 2, 8, 2),
    rect(6, 3, 9, 3),
    rect(5, 4, 10, 5),
    rect(4, 6, 11, 9),
    rect(5, 10, 10, 11),
    rect(6, 12, 9, 12),
    rect(7, 13, 8, 13),
)


def shard(metal, glow):
    """Voidglass does not cast — it grows. An angular crystal instead of a bar."""
    c = Canvas()
    c.plate(SHARD, METAL[metal])
    # Facet lines running out from the core.
    c.engrave([(6, 5), (7, 4), (8, 4), (9, 5), (6, 10), (9, 10)], METAL[metal], shade=1)
    c.glow([(7, 7), (8, 7), (7, 8), (8, 8)], GLOW[glow], within=SHARD)
    c.outline(METAL[metal][0])
    return c


# ==========================================================================
# Reforge catalyst — a caged core
# ==========================================================================

def _diamond(radius, cx=7.5, cy=7.5):
    return {(x, y) for y in range(16) for x in range(16)
            if abs(x - cx) + abs(y - cy) <= radius}


def catalyst(glow):
    c = Canvas()
    frame = _diamond(6.0) - _diamond(3.0)
    spikes = union(
        rect(7, 0, 8, 1), rect(7, 14, 8, 15),
        rect(0, 7, 1, 8), rect(14, 7, 15, 8),
    )
    housing = frame | spikes
    c.plate(housing, METAL["voidsteel"])
    c.engrave([(7, 2), (8, 2), (7, 13), (8, 13), (2, 7), (2, 8), (13, 7), (13, 8)],
              METAL["voidsteel"], shade=1)
    core = _diamond(2.5)
    c.glow(core, GLOW[glow], within=core | frame)
    c.outline(METAL["voidsteel"][0])
    return c


# ==========================================================================
# Runes — notched obsidian tablets with a carved sigil
# ==========================================================================

TABLET = union(
    rect(3, 2, 12, 13),
) - {(3, 2), (12, 2), (3, 13), (12, 13)}

TABLET_INNER = rect(5, 4, 10, 11)

SIGILS = {
    # Voidward — a warding shield
    "voidward": [(5, 4), (6, 4), (7, 4), (8, 4), (9, 4), (10, 4),
                 (5, 5), (10, 5), (5, 6), (10, 6), (5, 7), (10, 7),
                 (6, 8), (9, 8), (7, 9), (8, 9), (7, 10), (8, 10)],
    # Plasmaforge — a struck bolt
    "plasmaforge": [(9, 4), (8, 5), (7, 6), (8, 6), (9, 6),
                    (6, 7), (7, 7), (8, 7), (7, 8), (6, 9), (7, 9), (6, 10)],
    # Neuralspike — a synapse
    "neuralspike": [(7, 4), (8, 4), (7, 5), (8, 5),
                    (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6),
                    (7, 7), (8, 7), (6, 8), (9, 8), (5, 9), (10, 9),
                    (7, 10), (8, 10), (7, 11), (8, 11)],
    # Dimensionalshift — a rift
    "dimensionalshift": [(7, 4), (8, 4), (6, 5), (9, 5), (5, 6), (10, 6),
                         (5, 7), (10, 7), (5, 8), (10, 8), (6, 9), (9, 9),
                         (7, 10), (8, 10)],
    # Kineticsurge — stacked chevrons
    "kineticsurge": [(5, 5), (6, 6), (7, 7), (8, 7), (9, 6), (10, 5),
                     (5, 8), (6, 9), (7, 10), (8, 10), (9, 9), (10, 8)],
}


def rune(sigil, glow):
    c = Canvas()
    c.plate(TABLET, METAL["obsidian"])
    # Carved border, one pixel in from the edge.
    border = set()
    for x in range(4, 12):
        border |= {(x, 3), (x, 12)}
    for y in range(3, 13):
        border |= {(4, y), (11, y)}
    c.engrave(border, METAL["obsidian"], shade=1)
    c.carve(SIGILS[sigil], GLOW[glow], within=TABLET)
    c.outline(METAL["obsidian"][0])
    return c


# ==========================================================================
# Armour icons
# ==========================================================================

HELM = union(
    rect(6, 2, 9, 2),
    rect(5, 3, 10, 3),
    rect(4, 4, 11, 5),
    rect(3, 6, 12, 11),
    rect(4, 12, 11, 12),
    rect(3, 12, 4, 13), rect(11, 12, 12, 13),   # cheek guards
    rect(7, 0, 8, 1),                            # crest spike
)

HELM_VISOR = union(rect(4, 8, 6, 8), rect(9, 8, 11, 8))


def helmet(metal, glow):
    c = Canvas()
    c.plate(HELM, METAL[metal])
    c.engrave([(7, 4), (8, 4), (7, 5), (8, 5), (7, 6), (8, 6)], METAL[metal], shade=1)
    c.engrave([(x, 10) for x in range(4, 12)], METAL[metal], shade=1)
    c.glow(HELM_VISOR, GLOW[glow], within=HELM)
    c.outline(METAL[metal][0])
    return c


CHEST = union(
    rect(5, 3, 10, 12),                          # torso
    rect(1, 3, 4, 7), rect(11, 3, 14, 7),        # pauldrons
    rect(2, 8, 4, 9), rect(11, 8, 13, 9),        # upper arms
    rect(4, 11, 11, 12),                         # skirt flare
    rect(1, 2, 2, 2), rect(13, 2, 14, 2),        # pauldron spikes
) - rect(7, 3, 8, 3)                             # neck opening


def chestplate(metal, glow):
    c = Canvas()
    c.plate(CHEST, METAL[metal])
    c.engrave([(4, y) for y in range(4, 11)] + [(11, y) for y in range(4, 11)],
              METAL[metal], shade=1)
    c.engrave([(x, 10) for x in range(5, 11)], METAL[metal], shade=1)
    c.highlight([(2, 3), (3, 3), (12, 3), (13, 3)], METAL[metal], shade=5)
    c.glow([(7, 6), (8, 6), (7, 7), (8, 7)], GLOW[glow], within=CHEST)
    c.outline(METAL[metal][0])
    return c


LEGS = union(
    rect(3, 2, 12, 4),                           # belt
    rect(3, 5, 6, 13), rect(9, 5, 12, 13),       # legs
    rect(2, 2, 2, 3), rect(13, 2, 13, 3),        # hip plates
)


def leggings(metal, glow):
    c = Canvas()
    c.plate(LEGS, METAL[metal])
    c.engrave([(x, 4) for x in range(3, 13)], METAL[metal], shade=1)
    c.engrave([(3, y) for y in (10, 11)] + [(12, y) for y in (10, 11)], METAL[metal], shade=1)
    c.glow([(3, 7), (3, 8), (12, 7), (12, 8)], GLOW[glow], within=LEGS)
    c.outline(METAL[metal][0])
    return c


BOOTS = union(
    rect(2, 4, 5, 9), rect(10, 4, 13, 9),        # shins
    rect(1, 10, 6, 13), rect(9, 10, 14, 13),     # feet, with a gap between them
    rect(2, 3, 3, 3), rect(12, 3, 13, 3),        # knee lip
)


def boots(metal, glow):
    c = Canvas()
    c.plate(BOOTS, METAL[metal])
    c.engrave([(x, 10) for x in range(1, 7)] + [(x, 10) for x in range(9, 15)],
              METAL[metal], shade=1)
    c.engrave([(x, 13) for x in range(1, 7)] + [(x, 13) for x in range(9, 15)],
              METAL[metal], shade=1)
    c.glow([(2, 7), (2, 8), (13, 7), (13, 8)], GLOW[glow], within=BOOTS)
    c.outline(METAL[metal][0])
    return c


CROWN = union(
    rect(2, 9, 13, 12),                          # band
    rect(6, 4, 9, 8), rect(7, 2, 8, 3),          # centre spire and its tip
    rect(2, 5, 3, 8), rect(12, 5, 13, 8),        # side spires
)


def crown(metal, glow):
    c = Canvas()
    c.plate(CROWN, METAL[metal])
    c.engrave([(x, 12) for x in range(3, 13)], METAL[metal], shade=1)
    c.highlight([(x, 9) for x in range(3, 13)], METAL[metal], shade=5)
    c.glow([(7, 10), (8, 10), (7, 11), (8, 11)], GLOW[glow], within=CROWN)
    c.glow([(7, 2), (8, 2)], GLOW[glow], within=CROWN, halo=False)
    c.outline(METAL[metal][0])
    return c


# ==========================================================================
# Blocks
# ==========================================================================

def _clustered_stone(seed, ramp):
    """
    Per-pixel noise reads as static. One smoothing pass turns it into the
    clustered blotches that vanilla stone actually has.
    """
    rng = random.Random(seed)
    grid = [[rng.randrange(1, 6) for _ in range(16)] for _ in range(16)]
    smoothed = [[0] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            window = [grid[(y + dy) % 16][(x + dx) % 16]
                      for dy in (-1, 0, 1) for dx in (-1, 0, 1)]
            window.sort()
            smoothed[y][x] = window[4]
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.put(x, y, ramp[smoothed[y][x]])
    return c


# Measured against vanilla's own ore blocks, which cover about a third of the
# face in four or five irregular clumps with a few loose specks between them.
# These covered thirteen percent in six neat little rectangles, which reads as
# studs bolted to a wall rather than as metal in rock.
#
# Irregular on purpose: no cluster here is a rectangle, and each has at least
# one pixel that steps out of line. A rectangle is the one shape stone never
# makes, and six of them in a grid is what made the old ore read as a pattern.
ORE_CLUSTERS = [
    [(1, 2), (2, 2), (3, 2), (1, 3), (2, 3), (3, 3), (4, 3),
     (2, 4), (3, 4), (4, 4), (3, 5)],
    [(9, 1), (10, 1), (11, 1), (12, 1), (10, 2), (11, 2), (12, 2), (13, 2),
     (11, 3), (12, 3)],
    [(6, 6), (7, 6), (8, 6), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7),
     (6, 8), (7, 8), (8, 8), (7, 9)],
    [(0, 9), (1, 9), (2, 9), (0, 10), (1, 10), (2, 10), (3, 10),
     (1, 11), (2, 11)],
    [(11, 8), (12, 8), (13, 8), (10, 9), (11, 9), (12, 9), (13, 9), (14, 9),
     (11, 10), (12, 10), (13, 10)],
    [(5, 12), (6, 12), (7, 12), (8, 12), (6, 13), (7, 13), (8, 13), (9, 13),
     (7, 14)],
]

# Loose specks, each on its own. Vanilla has these and they are most of what
# stops the clumps reading as placed rather than found.
ORE_SPECKS = [(6, 0), (14, 5), (0, 6), (9, 4), (4, 15), (13, 13), (2, 7)]


def ore(glow, seed):
    """Crystal veins bedded into stone, each with a lit core."""
    c = _clustered_stone(seed, METAL["stone"])
    ramp = GLOW[glow]
    everything = rect(0, 0, 15, 15)
    for cluster in ORE_CLUSTERS:
        # A dark rim first, so the crystal sits *in* the rock rather than on it.
        rim = set()
        for (x, y) in cluster:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    if (x + dx, y + dy) not in cluster:
                        rim.add((x + dx, y + dy))
        for (x, y) in rim:
            c.put(x, y, METAL["stone"][0])
        for (x, y) in cluster:
            c.put(x, y, ramp[2])
        core = cluster[:2]
        c.glow(core, ramp, within=set(cluster), halo=False)
    for (x, y) in ORE_SPECKS:
        c.put(x, y, METAL["stone"][0])
        c.put(x, y, ramp[2])
    return c


def storage_block(metal, glow, seed=4):
    """Four riveted plates with an energy seam running between them."""
    ramp = METAL[metal]
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.put(x, y, ramp[4] if y % 8 < 3 else ramp[3])
    rng = random.Random(seed)
    for y in range(16):
        for x in range(16):
            if rng.random() < 0.12:
                c.put(x, y, ramp[2])

    seam = union(rect(7, 0, 8, 15), rect(0, 7, 15, 8))
    for (x, y) in seam:
        c.put(x, y, METAL[metal][1])
    c.glow([(7, 7), (8, 7), (7, 8), (8, 8)], GLOW[glow], within=seam, halo=True)

    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13),
                   (5, 5), (10, 5), (5, 10), (10, 10)]:
        c.put(x, y, METAL[metal][5])
        c.put(x, y + 1, METAL[metal][1])

    for x in range(16):
        c.put(x, 0, METAL[metal][5])
        c.put(x, 15, METAL[metal][0])
    for y in range(16):
        c.put(0, y, METAL[metal][4])
        c.put(15, y, METAL[metal][0])
    return c


WORKSTATION_INLAY = {
    # Rift Frame - a squared arch, so a wall of them reads as one structure
    # rather than as a repeating tile. The inlay meets the block edge on every
    # side on purpose: that is what makes the seams disappear when several are
    # placed together, which is the whole point of a frame block.
    "rift_frame": ([(0, 4), (1, 4), (2, 4), (13, 4), (14, 4), (15, 4),
                    (4, 0), (4, 1), (4, 2), (4, 13), (4, 14), (4, 15),
                    (11, 0), (11, 1), (11, 2), (11, 13), (11, 14), (11, 15),
                    (0, 11), (1, 11), (2, 11), (13, 11), (14, 11), (15, 11),
                    (4, 4), (11, 4), (4, 11), (11, 11)],
                   [(7, 7), (8, 7), (7, 8), (8, 8)]),

    # Reforge Table — an anvil face ringed by containment
    "reforge_table": ([(5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5),
                       (5, 10), (6, 10), (7, 10), (8, 10), (9, 10), (10, 10),
                       (5, 6), (5, 7), (5, 8), (5, 9),
                       (10, 6), (10, 7), (10, 8), (10, 9)],
                      [(7, 7), (8, 7), (7, 8), (8, 8)]),
    # Rune Socket Table — an open socket ring
    "rune_socket_table": ([(6, 4), (7, 4), (8, 4), (9, 4),
                           (5, 5), (10, 5), (4, 6), (11, 6),
                           (4, 7), (11, 7), (4, 8), (11, 8),
                           (5, 9), (10, 9), (6, 10), (7, 10), (8, 10), (9, 10),
                           (6, 11), (9, 11)],
                          [(7, 7), (8, 7), (7, 8), (8, 8)]),
    # Ascension Forge — an upward chevron over a crucible
    # Ascension Forge — three stacked chevrons. Anything more literal than
    # "up" turned into a bug shape at this size.
    "ascension_forge": ([(7, 2), (8, 2), (6, 3), (9, 3), (5, 4), (10, 4),
                         (7, 6), (8, 6), (6, 7), (9, 7), (5, 8), (10, 8),
                         (7, 10), (8, 10), (6, 11), (9, 11), (5, 12), (10, 12)],
                        [(7, 2), (8, 2)]),
}


def workstation(name, metal, glow, seed=9):
    """
    Note the flat base rather than a shaded plate: a block texture tiles, and a
    vertical gradient across 16 pixels produces a hard band at every seam once
    two of them sit side by side.
    """
    ramp = METAL[metal]
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.put(x, y, ramp[3])
    rng = random.Random(seed)
    for y in range(16):
        for x in range(16):
            roll = rng.random()
            if roll < 0.16:
                c.put(x, y, ramp[2])
            elif roll < 0.24:
                c.put(x, y, ramp[4])

    inlay, core = WORKSTATION_INLAY[name]
    c.carve(inlay, GLOW[glow], within=rect(0, 0, 15, 15))
    c.glow(core, GLOW[glow], within=rect(0, 0, 15, 15))

    # Bevelled rim so the block reads as a worked surface.
    for x in range(16):
        c.put(x, 0, METAL[metal][5])
        c.put(x, 15, METAL[metal][0])
    for y in range(16):
        c.put(0, y, METAL[metal][4])
        c.put(15, y, METAL[metal][0])
    c.put(0, 0, METAL[metal][5])
    c.put(15, 15, METAL[metal][0])
    return c


# ==========================================================================
# Weapons
#
# Minecraft item sprites read on the bottom-left → top-right diagonal, so the
# blades are built from a 45-degree axis rather than an upright silhouette.
# ==========================================================================

def _axis(x0, y0, length, width=2, dx=1, dy=-1):
    """
    A band of pixels along a 45-degree diagonal, `width` pixels thick.

    Thickness runs along x, not along the diagonal: offsetting diagonally
    leaves each step touching only at its corners, and a corner-connected run
    reads as a dotted line — and the outline pass then floods the gaps.
    """
    points = set()
    for i in range(length):
        bx, by = x0 + dx * i, y0 + dy * i
        for w in range(width):
            points.add((bx + w, by))
    return {(x, y) for (x, y) in points if 0 <= x < 16 and 0 <= y < 16}


def _crossbar(x0, y0, length, thickness=2):
    """
    A band running perpendicular to a blade — direction (+1, +1) against the
    blade's (+1, -1).

    A guard is read by crossing the blade and sticking out past it on both
    sides. Two stubs either side of the blade with a gap where the blade passes
    read as two beads, not as a bar: the eye needs the run to be continuous to
    call it one object. This draws through the blade and is painted after it,
    which is also how the blade ends up looking like it is in front.
    """
    points = set()
    for i in range(length):
        for t in range(thickness):
            points.add((x0 + i, y0 + i + t))
    return {(x, y) for (x, y) in points if 0 <= x < 16 and 0 <= y < 16}


# Measured against iron_sword, which fills 33% of its 16x16 and puts ten rows
# into the blade. The old shape gave the blade eight rows and spent the rest on
# a hilt that outlined into one brick; at inventory size that is a stick with a
# lump, which is what "blobby" meant.
SWORD_BLADE = _axis(5, 10, 9, width=3)
SWORD_TIP = {(14, 1), (15, 1), (14, 0)}
SWORD_GUARD = _crossbar(2, 8, 4)
# Grip and pommel continue down the blade's own axis, one pixel of core each,
# so the outline gives them three on screen — the same trick vanilla uses to
# keep a hilt legible at this size.
SWORD_GRIP = union(rect(3, 12, 4, 12), rect(2, 13, 3, 13))
SWORD_POMMEL = union(rect(1, 14, 2, 15))
SWORD = SWORD_BLADE | SWORD_TIP | SWORD_GUARD | SWORD_GRIP | SWORD_POMMEL

# The fuller runs down the middle of the blade rather than along its lit edge.
SWORD_FULLER = _axis(7, 9, 4, width=1)


def _hilt(c, metal, guard, grip, pommel):
    """
    Guard, grip and pommel, in that order and each its own value.

    Vanilla separates the three by material — steel guard, wood grip, steel
    pommel — and gets three readable parts for free. A single-hue ramp has to
    do it with value instead, so the guard alternates two dark steps (which
    reads as a wound or faceted bar), the grip is plated darker still, and only
    the pommel is bright.
    """
    ramp = METAL[metal]
    for (x, y) in sorted(guard):
        c.put(x, y, ramp[3 if (x + y) % 2 else 2])
    c.plate(grip, ramp)
    c.engrave(sorted(grip), ramp, shade=2)
    c.plate(pommel, ramp)
    c.highlight(sorted(pommel), ramp, shade=5)


def sword(metal, glow):
    """The baseline elemental weapon: a straight blade with a lit fuller."""
    c = Canvas()
    # light=5, mid=4, dark=2 rather than 5/3/1. Across a three-pixel core the
    # old values ran bright to almost black, and a blade that is half shadow
    # reads as a dark stick however well it is shaped.
    c.band(SWORD_BLADE | SWORD_TIP, METAL[metal], light=5, mid=4, dark=2)
    _hilt(c, metal, SWORD_GUARD, SWORD_GRIP, SWORD_POMMEL)
    # No halo: on a three-pixel core the bleed reaches both edges and the
    # blade stops reading as metal with a groove in it.
    c.carve(SWORD_FULLER, GLOW[glow], within=SWORD_BLADE)
    c.outline(METAL[metal][0])
    return c


# Two pixels of core against the sword's three, and cut into segments: the lash
# is the fast weapon and has to read lighter than the sword beside it in the
# same inventory row.
LASH_BLADE = _axis(5, 10, 9, width=2)
LASH_SEGMENTS = {(7, 8), (10, 5), (13, 2)}
LASH_TIP = {(14, 1), (14, 0)}
LASH = (LASH_BLADE | LASH_TIP | SWORD_GUARD | SWORD_GRIP | SWORD_POMMEL)


def lash(metal, glow):
    """Neural Lash: a segmented blade, thinner and faster than a sword."""
    c = Canvas()
    c.band(LASH_BLADE | LASH_TIP, METAL[metal], light=5, mid=4, dark=2)
    _hilt(c, metal, SWORD_GUARD, SWORD_GRIP, SWORD_POMMEL)
    # The joints between segments, lit rather than merely dark: what makes this
    # a lash and not a thin sword is that the blade is visibly made of pieces.
    c.carve(sorted(LASH_SEGMENTS), GLOW[glow], within=LASH_BLADE)
    c.outline(METAL[metal][0])
    return c


MAUL_HAFT = _axis(1, 14, 10, width=2)
MAUL_HEAD = union(rect(9, 2, 14, 6), rect(10, 1, 13, 1))
MAUL = MAUL_HAFT | MAUL_HEAD | union(rect(0, 14, 1, 15))


def maul(metal, glow):
    """Kinetic Maul: most of the mass at the far end of the swing."""
    c = Canvas()
    c.band(MAUL_HAFT | union(rect(0, 14, 1, 15)), METAL[metal])
    c.plate(MAUL_HEAD, METAL[metal])
    # Striking face on the far side, bound by two bands.
    c.highlight([(14, y) for y in range(2, 7)], METAL[metal], shade=5)
    c.engrave([(10, y) for y in range(1, 7)] + [(13, y) for y in range(1, 7)],
              METAL[metal], shade=1)
    c.glow([(11, 3), (12, 3), (11, 4), (12, 4)], GLOW[glow], within=MAUL_HEAD)
    c.outline(METAL[metal][0])
    return c


# Rows, not diagonal bands. A head built by stepping a run perpendicular to the
# blade touches the next step only at its corners, so the outline pass floods
# straight through it and the head comes out as a checkerboard — the same trap
# _axis() documents, reached from the other direction.
#
# The old head was three overlapping rectangles that plate() rounded off. At
# inventory size that is a spoon, which is exactly what it looked like. This is
# a long narrow spike with a fluted collar: one attack per turn and the highest
# damage on the board should read as reach, not as a bowl on a stick.
LANCE_SHAFT = _axis(0, 15, 9, width=2)
LANCE_HEAD = union(rect(15, 0, 15, 0), rect(14, 1, 15, 1), rect(13, 2, 14, 2),
                   rect(12, 3, 13, 3), rect(11, 4, 12, 4), rect(10, 5, 11, 5))
LANCE_COLLAR = union(rect(9, 6, 11, 6), rect(8, 7, 10, 7))
LANCE = LANCE_SHAFT | LANCE_HEAD | LANCE_COLLAR


def lance(metal, glow):
    """
    Singularity Lance: nearly all reach, with the weight in a long tapering
    point rather than a bulb.
    """
    c = Canvas()
    c.band(LANCE_SHAFT, METAL[metal], light=5, mid=4, dark=2)
    c.band(LANCE_HEAD, METAL[metal], light=5, mid=4, dark=2)
    # Fluting on the collar: alternating steps read as a wound ferrule and
    # separate the head from the shaft, which is the join a polearm needs.
    for (x, y) in sorted(LANCE_COLLAR):
        c.put(x, y, METAL[metal][3 if (x + y) % 2 else 2])
    c.highlight([(15, 0), (15, 1)], METAL[metal], shade=5)
    c.carve([(13, 2), (12, 3), (11, 4)], GLOW[glow], within=LANCE_HEAD)
    c.glow([(4, 11)], GLOW[glow], within=LANCE_SHAFT, halo=False)
    c.outline(METAL[metal][0])
    return c


def _slope(x0, y0, length, thickness=1):
    """
    A shallower run than 45 degrees: two pixels along x for every one up.

    Everything else in this file is built on the same 45-degree diagonal, which
    is right for a blade and wrong for a gun — drawn on that axis the rifle was
    a stick with two lumps on it, indistinguishable from the spear at inventory
    size. The bend between a shallow barrel and a steep stock is most of what
    says "firearm" here.
    """
    points = set()
    for i in range(length):
        x, y = x0 + i, y0 - (i // 2)
        for t in range(thickness):
            points.add((x, y + t))
    return {(x, y) for (x, y) in points if 0 <= x < 16 and 0 <= y < 16}


RIFLE_STOCK = union(rect(0, 12, 3, 14), rect(1, 15, 3, 15))
RIFLE_BODY = union(rect(3, 9, 8, 12))
RIFLE_BARREL = _slope(9, 8, 7, thickness=2)
RIFLE_MUZZLE = union(rect(15, 4, 15, 5))
RIFLE_SIGHT = union(rect(6, 8, 7, 8), rect(10, 7, 10, 7))
RIFLE_GRIP = union(rect(4, 13, 6, 15))
RIFLE_MAG = union(rect(7, 13, 9, 14))
RIFLE = (RIFLE_STOCK | RIFLE_BODY | RIFLE_BARREL | RIFLE_MUZZLE
         | RIFLE_SIGHT | RIFLE_GRIP | RIFLE_MAG)


def rifle(metal, glow):
    """
    Neural Cascade Rifle. Six readable parts — stock, receiver, magazine, grip,
    barrel, muzzle — because a gun at 16x16 is recognised by its parts count
    and its bend, not by its outline.
    """
    c = Canvas()
    c.plate(RIFLE_STOCK, METAL[metal])
    c.plate(RIFLE_BODY, METAL[metal])
    c.band(RIFLE_BARREL | RIFLE_MUZZLE, METAL[metal], light=5, mid=4, dark=2)
    c.plate(RIFLE_GRIP, METAL[metal])
    c.plate(RIFLE_MAG, METAL[metal])
    for (x, y) in sorted(RIFLE_SIGHT):
        c.put(x, y, METAL[metal][4])
    c.engrave(sorted(RIFLE_GRIP) + sorted(RIFLE_MAG), METAL[metal], shade=1)
    c.engrave([(x, 12) for x in range(3, 9)], METAL[metal], shade=1)
    c.highlight([(x, 9) for x in range(4, 9)], METAL[metal], shade=5)
    # Charge cells in the receiver, emitter at the muzzle.
    c.glow([(5, 10), (6, 10)], GLOW[glow], within=RIFLE_BODY)
    c.glow([(15, 4), (15, 5)], GLOW[glow],
           within=RIFLE_MUZZLE | RIFLE_BARREL, halo=False)
    c.outline(METAL[metal][0])
    return c


# ==========================================================================
# Utility rune sigils — the archive's non-elemental runes
# ==========================================================================

SIGILS.update({
    # Stabilizer — a closed cross, the Imperial medical mark
    "stabilizer": [(7, 5), (8, 5), (7, 6), (8, 6),
                   (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7),
                   (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8),
                   (7, 9), (8, 9), (7, 10), (8, 10)],
    # Reflex — two offset chevrons, a step sideways
    "reflex": [(6, 4), (5, 5), (6, 6), (7, 7), (6, 8), (5, 9), (6, 10),
               (10, 5), (9, 6), (10, 7), (9, 8), (10, 9)],
    # Barrier — a shield boss
    "barrier": [(5, 4), (6, 4), (7, 4), (8, 4), (9, 4), (10, 4),
                (5, 5), (10, 5), (5, 6), (10, 6), (5, 7), (10, 7),
                (6, 8), (9, 8), (7, 9), (8, 9), (7, 10), (8, 10),
                (7, 6), (8, 6), (7, 7), (8, 7)],
    # Plasma Core — a contained sun
    "plasma_core": [(7, 4), (8, 4), (5, 5), (10, 5), (4, 7), (11, 7),
                    (5, 9), (10, 9), (7, 10), (8, 10),
                    (7, 6), (8, 6), (6, 7), (9, 7), (7, 8), (8, 8),
                    (6, 6), (9, 6), (6, 8), (9, 8)],
})


# ==========================================================================
# Voidweave Aegis — the archive's flagship chestplate
# ==========================================================================

AEGIS = union(
    CHEST,
    rect(3, 10, 12, 12),                         # layered fauld across the waist
    rect(5, 2, 6, 2), rect(9, 2, 10, 2),         # raised collar
)


def aegis(metal, glow):
    """
    Heavier than the standard chestplate: layered plate, a wider core, and a
    second glow at the collar so it reads as the higher-tier piece at a
    glance.
    """
    c = Canvas()
    c.plate(AEGIS, METAL[metal])
    c.engrave([(4, y) for y in range(4, 12)] + [(11, y) for y in range(4, 12)],
              METAL[metal], shade=1)
    c.engrave([(x, 9) for x in range(5, 11)], METAL[metal], shade=1)
    c.highlight([(2, 3), (3, 3), (12, 3), (13, 3)], METAL[metal], shade=5)
    c.glow([(7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8)], GLOW[glow], within=AEGIS)
    c.glow([(6, 4), (9, 4)], GLOW[glow], within=AEGIS, halo=False)
    c.outline(METAL[metal][0])
    return c


# ==========================================================================
# Area tools
#
# Four shapes, three materials each. They share one haft so the line reads as
# a set, and are told apart entirely by the head — which is the part a player
# actually sees at inventory size.
# ==========================================================================

# One pixel of core, not two.
#
# outline() adds a pixel all round, so a two-wide core is a four-wide handle on
# screen. Every vanilla tool has a three-wide handle stepping one pixel a row,
# and four is most of what made these read as heavy and soft next to them.
# Measured off iron_pickaxe rather than judged by eye.
TOOL_HAFT = _axis(2, 13, 8, width=1)
TOOL_POMMEL = union(rect(2, 14, 2, 14))
TOOL_GRIP = TOOL_HAFT | TOOL_POMMEL


# Was a ten-by-seven brick on screen - wider than a vanilla axe head is tall,
# and filling a third of the sprite on its own. Now eight by six, with the haft
# socket cut in so the head sits *on* the handle rather than beside it.
HAMMER_HEAD = union(
    rect(9, 2, 14, 7),          # a straight-sided block: flat top, flat face
    rect(8, 4, 8, 5),           # socket, where the haft passes through
)
HAMMER = TOOL_GRIP | HAMMER_HEAD


def hammer(metal, glow):
    """
    A brick of a head with a striking face at each end. All the weight is out
    past the haft, which is what a 3x3 swing should look like.
    """
    c = Canvas()
    c.band(TOOL_GRIP, METAL[metal])
    c.plate(HAMMER_HEAD, METAL[metal])
    # Both faces bright, the bindings that hold them dark: reads as forged
    # rather than cast.
    c.highlight([(14, y) for y in range(2, 8)], METAL[metal], shade=5)
    c.highlight([(9, y) for y in range(2, 8)], METAL[metal], shade=4)
    c.engrave([(10, y) for y in range(2, 8)] + [(13, y) for y in range(2, 8)],
              METAL[metal], shade=1)
    # halo=False: on a head this size the bleed covers the whole block.
    c.glow([(12, 4), (12, 5)], GLOW[glow], within=HAMMER_HEAD, halo=False)
    c.outline(METAL[metal][0])
    return c


# The outline pass grows every silhouette by a pixel in each direction, so
# these shapes are drawn a pixel lean and gaps are never left at 1px — see the
# art rules in TEXTURES.md.

# An axe bit, built the way vanilla builds one: a convex cutting arc on the
# outside and a back that slopes in to meet the haft. The old shape was a flat
# slab with a straight back, which is a wedge rather than an axe, and it ran
# the full width of the sprite.
BROADAXE_BIT = union(
    rect(6, 1, 12, 1),          # flat top, which is what says "wedge"
    rect(5, 2, 12, 2),
    rect(4, 3, 12, 3),
    rect(4, 4, 11, 4),
    rect(5, 5, 10, 5),
    rect(6, 6, 8, 6),
)
BROADAXE_BEARD = union(rect(12, 4, 12, 5))
BROADAXE = TOOL_GRIP | BROADAXE_BIT | BROADAXE_BEARD


def broadaxe(metal, glow):
    """
    A wedge sitting on top of the haft: flat along the back, edge along the
    left, underside sloping into the handle. Asymmetry is what stops it
    reading as a leaf.
    """
    c = Canvas()
    c.band(TOOL_GRIP, METAL[metal])
    c.plate(BROADAXE_BIT | BROADAXE_BEARD, METAL[metal])
    # The edge, which is the left arc: lit hardest where it is thinnest.
    # The cutting edge is the left arc; light it hardest where it is thinnest.
    c.highlight([(4, 3), (4, 4), (5, 2), (5, 5), (6, 1), (6, 6)],
                METAL[metal], shade=5)
    c.engrave([(11, y) for y in range(2, 5)], METAL[metal], shade=1)
    c.glow([(9, 3), (9, 4)], GLOW[glow], within=BROADAXE_BIT, halo=False)
    c.outline(METAL[metal][0])
    return c


SCYTHE_SNATH = _axis(2, 13, 9, width=1)
# Two rows of core through the body of the blade, tapering to one at the point,
# rather than one row everywhere. A single-pixel arc becomes three on screen
# once outlined, and three pixels of which two are outline is a wire — it read
# as a bent stick rather than as something with an edge. Each run still
# overlaps the next in x so the curve stays orthogonally connected and the
# outline cannot flood it.
SCYTHE_BLADE = union(
    rect(8, 5, 11, 5),          # rooted at the snath, at full thickness
    rect(7, 4, 11, 4),
    rect(4, 3, 9, 3),           # sweeping left
    rect(2, 2, 7, 2),
    rect(0, 3, 3, 3),           # and hooking down to the point
    rect(0, 4, 1, 4),
)
SCYTHE = SCYTHE_SNATH | TOOL_POMMEL | SCYTHE_BLADE


def scythe(metal, glow):
    """A long arc sweeping off the snath, thick at the root and hooked at the
    point."""
    c = Canvas()
    c.band(SCYTHE_SNATH | TOOL_POMMEL, METAL[metal], light=5, mid=4, dark=2)
    c.plate(SCYTHE_BLADE, METAL[metal])
    # The cutting edge is the outer arc; the back of the blade is cut in, which
    # is what stops two rows of core reading as a slab.
    c.highlight([(2, 2), (3, 2), (4, 2), (0, 3), (1, 3), (0, 4)],
                METAL[metal], shade=5)
    c.engrave([(x, 5) for x in range(8, 12)] + [(9, 4), (10, 4)],
              METAL[metal], shade=1)
    c.glow([(10, 5)], GLOW[glow], within=SCYTHE_BLADE, halo=False)
    c.outline(METAL[metal][0])
    return c


# A one-pixel shaft, so the head reads as more than shaft.
SPEAR_SHAFT = _axis(1, 14, 10, width=1)
SPEAR_HEAD = union(
    rect(14, 0, 15, 0),
    rect(13, 1, 15, 1),
    rect(12, 2, 14, 2),
    rect(11, 3, 13, 3),
    rect(11, 4, 12, 4),
)
# Wings, as on a boar spear.
#
# A narrow head on a thin shaft is a stick with a bump, and widening the head
# until it stops being one turns it into a spoon — both were tried. What
# actually separates a polearm from a stick at 16x16 is a second silhouette
# crossing the first, and the wings are that at four pixels' cost. They also
# tell the player which end is which, which the old sprite did not.
SPEAR_WINGS = _crossbar(8, 4, 5, thickness=1)
SPEAR = SPEAR_SHAFT | TOOL_POMMEL | SPEAR_HEAD | SPEAR_WINGS


def spear(metal, glow):
    """A leaf head on a thin shaft, with wings at the socket."""
    c = Canvas()
    c.band(SPEAR_SHAFT | TOOL_POMMEL, METAL[metal], light=5, mid=4, dark=2)
    c.plate(SPEAR_HEAD, METAL[metal])
    for (x, y) in sorted(SPEAR_WINGS):
        c.put(x, y, METAL[metal][3 if (x + y) % 2 else 2])
    c.highlight([(14, 0), (15, 0), (15, 1)], METAL[metal], shade=5)
    c.carve([(13, 2), (12, 3)], GLOW[glow], within=SPEAR_HEAD)
    c.outline(METAL[metal][0])
    return c


# The material each tool variant is drawn in: metal ramp, then glow ramp.
TOOL_MATERIALS = {
    "voidglass": ("obsidian", "void"),
    "aetherium": ("aetherium", "dimensional"),
    "neutronium": ("neutronium", "kinetic"),
}

TOOL_SHAPES = {
    "hammer": hammer,
    "broadaxe": broadaxe,
    "scythe": scythe,
    "spear": spear,
}


# ==========================================================================
# The Imperial Codex
# ==========================================================================

CODEX_COVER = union(
    rect(3, 2, 12, 13),
)
CODEX_SPINE = union(rect(3, 2, 4, 13))
CODEX_PAGES = union(rect(11, 3, 12, 12))
# The Code's own mark: a serif I, which is what the Empire stamps on anything
# it considers filed. A cross read as a medical symbol, which is the one thing
# this book is not.
CODEX_SIGIL = union(
    rect(6, 5, 9, 5),
    rect(7, 6, 8, 9),
    rect(6, 10, 9, 10),
)


def codex(metal, glow):
    """
    A slab of a book: heavy board, a lit spine, and the Code's mark cut into
    the cover rather than printed on it. Reads as a document at inventory size
    because the page block on the fore-edge is the only bright thing on it.
    """
    c = Canvas()
    c.plate(CODEX_COVER, METAL[metal])
    c.engrave(sorted(CODEX_SPINE), METAL[metal], shade=1)
    c.highlight(sorted(CODEX_PAGES), METAL[metal], shade=5)
    c.carve(CODEX_SIGIL, GLOW[glow], within=CODEX_COVER)
    c.glow([(4, 7), (4, 8)], GLOW[glow], within=CODEX_SPINE, halo=False)
    c.outline(METAL[metal][0])
    return c
