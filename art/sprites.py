"""Item and block sprites for the Voidforged set."""
import random

import pixelart
from canvas import Canvas, rect, union
from style import METAL, GLOW


def ingot(metal, glow):
    """A cast bar, recoloured for this material."""
    return pixelart.render("ingot", METAL[metal], GLOW[glow])


def shard(metal, glow):
    """Voidglass does not cast — it grows. An angular crystal instead of a bar."""
    return pixelart.render("shard", METAL[metal], GLOW[glow])


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


# ==========================================================================
# Gem dust, sigils and the case — SPEC_CLONE P7
#
# Runes are Elysium's gems, so everything here is drawn as something that
# happens *to* a rune rather than as a second family of stones. Dust is a
# tablet ground down; a sigil is a tablet with a seal instead of a sigil; the
# case is the box they are all kept in.
# ==========================================================================

# Four seals, drawn to be told apart at sixteen pixels rather than merely to
# differ. Each is what its sigil does, as a shape: an opening, an upward mark,
# a withdrawal, a turning.
SEALS = {
    # Socketing — an opening: a ring with a gap at the top, the socket itself.
    "socketing": [(6, 4), (9, 4), (5, 5), (10, 5), (4, 6), (11, 6),
                  (4, 7), (11, 7), (4, 8), (11, 8), (5, 9), (10, 9),
                  (6, 10), (7, 10), (8, 10), (9, 10)],
    # Enhancement — an upward mark over a bar: more of what is already there.
    "enhancement": [(7, 3), (8, 3), (6, 4), (9, 4), (5, 5), (10, 5),
                    (7, 5), (8, 5), (7, 6), (8, 6), (7, 7), (8, 7),
                    (5, 10), (6, 10), (7, 10), (8, 10), (9, 10), (10, 10)],
    # Withdrawal — a downward mark leaving a cup: something taken back out.
    "withdrawal": [(7, 3), (8, 3), (7, 4), (8, 4), (7, 5), (8, 5),
                   (5, 6), (10, 6), (6, 7), (9, 7), (7, 8), (8, 8),
                   (5, 10), (10, 10), (6, 11), (7, 11), (8, 11), (9, 11)],
    # Rebirth — a turning: an open circle with an arrowhead, the same thing
    # come round again.
    "rebirth": [(6, 4), (7, 4), (8, 4), (9, 4), (5, 5), (10, 5),
                (4, 6), (4, 7), (4, 8), (11, 6), (11, 7),
                (5, 9), (10, 9), (6, 10), (7, 10), (8, 10),
                (9, 3), (10, 4), (9, 5)],
}

# A pile of ground tablet: wide at the bottom, thinning upward, with two motes
# above it. Deliberately not a neat triangle — dust that reads as a pyramid
# reads as a block, and this sits in the same inventory as ingots.
DUST_PILE = [(4, 12), (5, 12), (6, 12), (7, 12), (8, 12), (9, 12), (10, 12), (11, 12),
             (5, 11), (6, 11), (7, 11), (8, 11), (9, 11), (10, 11),
             (6, 10), (7, 10), (8, 10), (9, 10),
             (7, 9), (8, 9)]
DUST_MOTES = [(5, 7), (10, 6), (7, 5)]

# The case: a lidded box, the lid a shade proud of the body so it reads as
# something that opens.
CASE_BODY = union(rect(3, 7, 12, 13))
CASE_LID = union(rect(2, 4, 13, 7))


def gem_dust(glow):
    """A rune ground down: what a tablet is worth once it is not one."""
    c = Canvas()
    c.plate(DUST_PILE, METAL["obsidian"])
    c.carve([(6, 11), (9, 11), (7, 10)], GLOW[glow], within=union(DUST_PILE))
    c.glow(DUST_MOTES, GLOW[glow])
    c.outline(METAL["obsidian"][0])
    return c


def sigil(seal, glow):
    """A tablet carrying a seal rather than a sigil: a use, not a bonus."""
    c = Canvas()
    c.plate(TABLET, METAL["voidsteel"])
    border = set()
    for x in range(4, 12):
        border |= {(x, 3), (x, 12)}
    for y in range(3, 13):
        border |= {(4, y), (11, y)}
    c.engrave(border, METAL["voidsteel"], shade=1)
    c.carve(SEALS[seal], GLOW[glow], within=TABLET)
    c.outline(METAL["voidsteel"][0])
    return c


def erasure(glow):
    """A page struck out of the ledger: pale paper, a line through the entry."""
    c = Canvas()
    page = union(rect(4, 2, 12, 14))
    c.plate(page, METAL["stone"])
    # Three faint lines of entry, then the strike-through in the glow colour.
    for y in (5, 8, 11):
        c.engrave({(x, y) for x in range(6, 11)}, METAL["stone"], shade=1)
    c.carve([(x, 8) for x in range(5, 12)], GLOW[glow], within=page)
    c.outline(METAL["stone"][0])
    return c


def gem_case(metal, glow):
    """The box the tablets are kept in."""
    c = Canvas()
    c.plate(CASE_BODY, METAL[metal])
    c.plate(CASE_LID, METAL[metal], top=5, bottom=3)
    c.engrave([(x, 7) for x in range(3, 13)], METAL[metal], shade=1)
    c.glow([(7, 9), (8, 9)], GLOW[glow])
    c.outline(METAL[metal][0])
    return c


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
# Spawner runes (SPEC_CLONE P11.6)
# ==========================================================================
#
# The same obsidian tablet as the socket runes, with its own set of marks. Each
# one has to be readable at sixteen pixels *and* tellable apart from the other
# ten in a row in an inventory, which rules out anything with fine detail: the
# marks below are all solid blocks, bars, rings and chevrons.
#
# The field inside the tablet's carved border is x 5..10, y 4..11 — six wide
# and eight tall. Nothing here goes outside it.

def _ring(x0, y0, x1, y1):
    """The outline of a box, which is a shape that survives being small."""
    points = set()
    for x in range(x0, x1 + 1):
        points |= {(x, y0), (x, y1)}
    for y in range(y0, y1 + 1):
        points |= {(x0, y), (x1, y)}
    return points


_EYE = ({(6, 6), (7, 6), (8, 6), (9, 6), (5, 7), (10, 7), (6, 8), (7, 8), (8, 8), (9, 8)}
        | {(7, 7), (8, 7)})
_STRIKE = {(5, 10), (6, 9), (7, 8), (8, 7), (9, 6), (10, 5)}

SPAWNER_SIGILS = {
    # Haste — two chevrons falling, which is the shape of a countdown.
    "haste": {(5, 4), (6, 5), (7, 6), (8, 6), (9, 5), (10, 4),
              (5, 7), (6, 8), (7, 9), (8, 9), (9, 8), (10, 7)},
    # Swarming — three of something where there was one.
    "swarming": {(5, 5), (6, 5), (5, 6), (6, 6),
                 (9, 5), (10, 5), (9, 6), (10, 6),
                 (7, 9), (8, 9), (7, 10), (8, 10)},
    # Crowding — a pen with something in it.
    "crowding": _ring(5, 5, 10, 10) | {(7, 7), (8, 7), (7, 8), (8, 8)},
    # Vigilance — an open eye, looking further.
    "vigilance": set(_EYE),
    # Scattering — a centre and four corners leaving it.
    "scattering": {(7, 7), (8, 7), (7, 8), (8, 8),
                   (5, 4), (6, 4), (5, 5), (9, 4), (10, 4), (10, 5),
                   (5, 10), (5, 11), (6, 11), (10, 10), (9, 11), (10, 11)},
    # Indifference — the same eye, struck through.
    "indifference": _EYE | _STRIKE,
    # Darkness — a blot: the only solid mark in the set, so it reads as the
    # absence of light rather than as a shape.
    "darkness": {(7, 5), (8, 5),
                 (6, 6), (7, 6), (8, 6), (9, 6),
                 (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7),
                 (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8),
                 (6, 9), (7, 9), (8, 9), (9, 9),
                 (7, 10), (8, 10)},
    # Tethering — a stake with two ties.
    "tethering": {(7, 4), (8, 4), (7, 5), (8, 5), (7, 6), (8, 6), (7, 7), (8, 7),
                  (7, 8), (8, 8), (7, 9), (8, 9), (7, 10), (8, 10), (7, 11), (8, 11),
                  (5, 6), (6, 6), (9, 6), (10, 6), (5, 9), (6, 9), (9, 9), (10, 9)},
    # Stillness — a bar, and nothing else at all.
    "stillness": {(5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7),
                  (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8)},
    # Silence — a mouth, struck through.
    "silence": {(5, 6), (6, 6), (7, 6), (5, 7), (6, 7), (7, 7),
                (5, 8), (6, 8), (7, 8), (5, 9), (6, 9), (7, 9)} | _STRIKE,
    # Obedience — a lever, waiting to be thrown.
    "obedience": {(7, 4), (8, 4), (7, 5), (8, 5), (7, 6), (8, 6), (7, 7), (8, 7),
                  (7, 8), (8, 8), (7, 9), (8, 9),
                  (5, 10), (6, 10), (7, 10), (8, 10), (9, 10), (10, 10),
                  (5, 11), (6, 11), (7, 11), (8, 11), (9, 11), (10, 11)},
}


def spawner_rune(sigil, glow):
    """One of the spawner runes: the socket rune's tablet, with its own mark."""
    c = Canvas()
    c.plate(TABLET, METAL["obsidian"])
    border = set()
    for x in range(4, 12):
        border |= {(x, 3), (x, 12)}
    for y in range(3, 13):
        border |= {(4, y), (11, y)}
    c.engrave(border, METAL["obsidian"], shade=1)
    c.carve(SPAWNER_SIGILS[sigil], GLOW[glow], within=TABLET)
    c.outline(METAL["obsidian"][0])
    return c


# ==========================================================================
# Convergence templates — the smithing plates that cross gear between systems
# ==========================================================================

# A smithing template's silhouette: a tall plate with the top two corners cut,
# so it reads as a template beside vanilla's at a glance rather than as another
# rune tablet. The notch is what a player recognises; the sigil is what tells
# the two apart.
TEMPLATE_PLATE = union(rect(4, 1, 11, 14)) - {
    (4, 1), (11, 1), (4, 14), (11, 14), (4, 2), (11, 2),
}

# Two marks, drawn to be distinguishable at sixteen pixels rather than merely
# different: one is two halves meeting, the other is a ring closing. A pair of
# similar glyphs in different colours is a pair a colourblind player cannot
# tell apart, and these are used in the same slot of the same screen.
CONVERGENCE_SIGILS = {
    # Silent Gear — two halves meeting at a seam, because that conversion is a
    # swap: the same gear, built the other mod's way.
    # Two solid halves that interlock across a stepped seam. The first version
    # was a scatter of pixels meant to suggest a join and read as noise — at
    # sixteen pixels a shape has to be solid to be a shape at all.
    "silent": [(6, 4), (7, 4), (6, 5), (7, 5), (8, 5),
               (6, 6), (7, 6), (6, 7), (7, 7),
               (8, 8), (9, 8), (8, 9), (9, 9),
               (7, 10), (8, 10), (9, 10), (8, 11), (9, 11)],
    # Apotheosis — a closed ring, because that conversion adds rather than
    # swaps: nothing leaves the piece.
    "affix": [(6, 4), (7, 4), (8, 4), (9, 4),
              (5, 5), (10, 5), (5, 6), (10, 6), (5, 7), (10, 7),
              (5, 8), (10, 8), (5, 9), (10, 9),
              (6, 10), (7, 10), (8, 10), (9, 10),
              (7, 7), (8, 7)],
}


def convergence_template(sigil, glow):
    """A smithing template carrying one of the two convergence marks."""
    c = Canvas()
    c.plate(TEMPLATE_PLATE, METAL["voidsteel"])
    # The recessed field the sigil sits in, one pixel inside the plate.
    field = set()
    for x in range(5, 11):
        field |= {(x, 3), (x, 12)}
    for y in range(3, 13):
        field |= {(5, y), (10, y)}
    c.engrave(field, METAL["voidsteel"], shade=1)
    c.carve(CONVERGENCE_SIGILS[sigil], GLOW[glow], within=TEMPLATE_PLATE)
    c.outline(METAL["voidsteel"][0])
    return c


def scanner(metal, glow):
    """
    The Field Scanner: a handheld lens with a lit aperture.

    A ring is the one silhouette that reads as "an instrument that looks at
    things" at sixteen pixels — a box with a dot on it is a box.
    """
    c = Canvas()
    body = union(rect(4, 5, 11, 12)) - {(4, 5), (11, 5), (4, 12), (11, 12)}
    grip = union(rect(6, 12, 9, 14))
    c.plate(body | grip, METAL[metal])
    # The aperture: a ring of housing with the lens inside it.
    ring = set()
    for x in range(6, 10):
        ring |= {(x, 7), (x, 10)}
    for y in range(7, 11):
        ring |= {(6, y), (9, y)}
    c.engrave(ring, METAL[metal], shade=1)
    c.glow({(7, 8), (8, 8), (7, 9), (8, 9)}, GLOW[glow], within=body)
    c.outline(METAL[metal][0])
    return c


def ration(metal, glow):
    """
    A field ration: a crimped foil pouch with a lit seal.

    The silhouette has to read as *food in a packet* at sixteen pixels, next to
    an inventory row of bread and steak, while still belonging to a set where
    everything else is plate armour and blades. A pouch does that and a bowl
    does not — a bowl reads as a cooked meal, and these are not cooked, they are
    issued.

    The seal is the only saturated thing on it, carrying the element the way
    every other sprite in the set does, so five rations side by side are told
    apart by colour alone. Which is the situation they are actually used in: a
    player picking the right one out of a hotbar while something is hurting
    them.
    """
    c = Canvas()
    pouch = union(rect(4, 4, 11, 13)) - {(4, 13), (11, 13)}
    crimp = union(rect(3, 2, 12, 3))
    c.plate(pouch | crimp, METAL[metal])
    # The crimp's teeth, and the fold below it. Engraved rather than drawn, so
    # the packet reads as pressed metal foil instead of a painted rectangle.
    c.engrave({(x, 2) for x in range(3, 13, 2)}, METAL[metal], shade=1)
    c.engrave({(x, 4) for x in range(4, 12)}, METAL[metal], shade=1)
    c.glow({(6, 8), (7, 8), (8, 8), (9, 8), (7, 7), (8, 9)},
           GLOW[glow], within=pouch)
    c.outline(METAL[metal][0])
    return c


def helmet(metal, glow):
    """A domed helm with an open face."""
    return pixelart.render("helmet", METAL[metal], GLOW[glow])


CHEST = union(
    rect(5, 3, 10, 12),                          # torso
    rect(1, 3, 4, 7), rect(11, 3, 14, 7),        # pauldrons
    rect(2, 8, 4, 9), rect(11, 8, 13, 9),        # upper arms
    rect(4, 11, 11, 12),                         # skirt flare
    rect(1, 2, 2, 2), rect(13, 2, 14, 2),        # pauldron spikes
) - rect(7, 3, 8, 3)                             # neck opening


def chestplate(metal, glow):
    """Shoulders, a neck opening and a centre seam."""
    return pixelart.render("chestplate", METAL[metal], GLOW[glow])


def leggings(metal, glow):
    """A belt and two legs."""
    return pixelart.render("leggings", METAL[metal], GLOW[glow])


def boots(metal, glow):
    """A pair, ankle above and foot forward."""
    return pixelart.render("boots", METAL[metal], GLOW[glow])


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

    # Gem Cutting Table — a facet: two chevrons meeting at a point, which is
    # what cutting a stone does to it. Not a saw and not a wheel, both of which
    # read as machinery at this size and this bench is a bench.
    "gem_cutting_table": ([(7, 3), (8, 3), (6, 4), (9, 4), (5, 5), (10, 5),
                           (4, 6), (11, 6), (4, 7), (11, 7),
                           (5, 8), (10, 8), (6, 9), (9, 9), (7, 10), (8, 10),
                           (6, 6), (9, 6), (6, 9), (9, 9)],
                          [(7, 6), (8, 6), (7, 7), (8, 7)]),

    # Augmenting Table — a seal pressed into a plate: a ring with a bar across
    # it, because what this bench does is stamp something onto a piece that
    # was already finished.
    "augmenting_table": ([(6, 3), (7, 3), (8, 3), (9, 3),
                          (5, 4), (10, 4), (4, 5), (11, 5),
                          (4, 6), (11, 6), (4, 7), (11, 7), (4, 8), (11, 8),
                          (5, 9), (10, 9), (6, 10), (7, 10), (8, 10), (9, 10),
                          (6, 12), (7, 12), (8, 12), (9, 12)],
                         [(6, 6), (7, 6), (8, 6), (9, 6)]),

    # Brutal Spawner — a barred cage with something behind it: four heavy
    # uprights and a shape that does not quite fit between them. Not a skull,
    # which at this size reads as decoration on half the blocks in the game.
    "brutal_spawner": ([(3, 1), (3, 2), (3, 3), (3, 4), (3, 5), (3, 6), (3, 7), (3, 8),
                        (3, 9), (3, 10), (3, 11), (3, 12), (3, 13), (3, 14),
                        (7, 1), (7, 2), (7, 3), (7, 4), (7, 5), (7, 6), (7, 7), (7, 8),
                        (7, 9), (7, 10), (7, 11), (7, 12), (7, 13), (7, 14),
                        (11, 1), (11, 2), (11, 3), (11, 4), (11, 5), (11, 6), (11, 7), (11, 8),
                        (11, 9), (11, 10), (11, 11), (11, 12), (11, 13), (11, 14),
                        (1, 1), (2, 1), (12, 1), (13, 1), (14, 1),
                        (1, 14), (2, 14), (12, 14), (13, 14), (14, 14)],
                       [(5, 6), (6, 6), (9, 6), (5, 9), (6, 9), (9, 9)]),

    # Swarm Spawner — the same cage, packed: many small shapes rather than one
    # large one, so the two are told apart by density at a glance.
    "swarm_spawner": ([(3, 1), (3, 3), (3, 5), (3, 7), (3, 9), (3, 11), (3, 13),
                       (7, 1), (7, 3), (7, 5), (7, 7), (7, 9), (7, 11), (7, 13),
                       (11, 1), (11, 3), (11, 5), (11, 7), (11, 9), (11, 11), (11, 13),
                       (5, 2), (9, 2), (5, 6), (9, 6), (5, 10), (9, 10), (5, 14), (9, 14),
                       (1, 4), (13, 4), (1, 8), (13, 8), (1, 12), (13, 12)],
                      [(5, 4), (9, 4), (5, 8), (9, 8), (5, 12), (9, 12)]),

    # Caged Invader — a sealed box with one seam across it, because what makes
    # this block interesting is that it is shut and a player has to open it.
    "caged_invader": ([(2, 2), (3, 2), (4, 2), (5, 2), (6, 2), (7, 2), (8, 2), (9, 2),
                       (10, 2), (11, 2), (12, 2), (13, 2),
                       (2, 13), (3, 13), (4, 13), (5, 13), (6, 13), (7, 13), (8, 13),
                       (9, 13), (10, 13), (11, 13), (12, 13), (13, 13),
                       (2, 3), (2, 4), (2, 5), (2, 6), (2, 7), (2, 8), (2, 9), (2, 10),
                       (2, 11), (2, 12),
                       (13, 3), (13, 4), (13, 5), (13, 6), (13, 7), (13, 8), (13, 9),
                       (13, 10), (13, 11), (13, 12),
                       (4, 7), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7), (11, 7),
                       (4, 8), (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (11, 8)],
                      [(7, 5), (8, 5), (7, 10), (8, 10)]),

    # Registry Beacon — a survey marker: a sealed ring with four bearings
    # running out to the block edge.
    #
    # Deliberately not an eye, an antenna or a flag, all of which were tried at
    # this size. An eye reads as surveillance, which is the *inspection*
    # beacon's job in §14.5 and would make the two indistinguishable the day
    # both exist. An antenna reads as a radio. A flag reads as ownership and
    # stops there. What this block does is file a piece of ground, so it is a
    # surveyor's mark — and the four bearings running off the tile are what
    # make a row of them read as a boundary rather than as four machines.
    # Imperial Archive — a ledger: stacked rules with a seal in the margin.
    #
    # The registry beacon is a mark on the ground; this is a record of it, so
    # the two are deliberately built from different primitives — one radiates,
    # one stacks. At 16 pixels that contrast is most of what lets a player tell
    # two dark metal cubes apart across a room.
    "imperial_archive": ([(4, 3), (5, 3), (6, 3), (7, 3), (8, 3), (9, 3),
                          (10, 3), (11, 3),
                          (4, 5), (5, 5), (6, 5), (7, 5), (8, 5), (9, 5),
                          (4, 7), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7),
                          (10, 7), (11, 7),
                          (4, 9), (5, 9), (6, 9), (7, 9),
                          (4, 11), (5, 11), (6, 11), (7, 11), (8, 11), (9, 11),
                          (10, 11),
                          (2, 3), (2, 5), (2, 7), (2, 9), (2, 11)],
                         [(11, 9), (12, 9), (11, 10), (12, 10)]),

    # Inspection Beacon — a single eye over a ruled baseline.
    #
    # An eye, which the Registry Beacon deliberately is not: that block files
    # ground, this one WATCHES it, and the two need to be told apart at a
    # glance in a world where both are dark metal cubes. The baseline under it
    # is the record being kept — the same ruled lines the archive uses, so the
    # three Imperial blocks read as one set of equipment.
    "inspection_beacon": ([(5, 5), (6, 4), (7, 4), (8, 4), (9, 4), (10, 5),
                           (11, 6), (11, 7),
                           (10, 8), (9, 9), (8, 9), (7, 9), (6, 9), (5, 8),
                           (4, 7), (4, 6),
                           (4, 12), (5, 12), (6, 12), (7, 12), (8, 12),
                           (9, 12), (10, 12), (11, 12)],
                          [(7, 6), (8, 6), (7, 7), (8, 7)]),

    "registry_beacon": ([(5, 4), (6, 4), (7, 4), (8, 4), (9, 4), (10, 4),
                         (5, 11), (6, 11), (7, 11), (8, 11), (9, 11), (10, 11),
                         (4, 5), (4, 6), (4, 7), (4, 8), (4, 9), (4, 10),
                         (11, 5), (11, 6), (11, 7), (11, 8), (11, 9), (11, 10),
                         (7, 1), (8, 1), (7, 2), (8, 2),
                         (7, 13), (8, 13), (7, 14), (8, 14),
                         (1, 7), (1, 8), (2, 7), (2, 8),
                         (13, 7), (13, 8), (14, 7), (14, 8)],
                        [(7, 7), (8, 7), (7, 8), (8, 8)]),

    # Arcane Table — four nodes feeding one core, because that is literally
    # what the block does: eterna, quanta, arcana and rectification are read
    # out of the room and meet in the middle. Deliberately not a book and not
    # a pentagram: the first is the vanilla table's own sprite and the second
    # is every wizard block in every mod.
    "arcane_table": ([(3, 3), (4, 3), (3, 4), (12, 3), (11, 3), (12, 4),
                      (3, 12), (4, 12), (3, 11), (12, 12), (11, 12), (12, 11),
                      (5, 5), (10, 5), (5, 10), (10, 10),
                      (6, 6), (9, 6), (6, 9), (9, 9),
                      (7, 4), (8, 4), (7, 11), (8, 11),
                      (4, 7), (4, 8), (11, 7), (11, 8)],
                     [(7, 7), (8, 7), (7, 8), (8, 8)]),

    # Elysium Spawner — a cage: four heavy uprights, two bands, and something
    # lit behind them. Close enough to vanilla's spawner that a player knows
    # what it is at a glance, and different enough that they can see it is not
    # the one they walked past in the dungeon.
    "owned_spawner": ([(2, 1), (2, 2), (2, 3), (2, 4), (2, 5), (2, 6), (2, 7), (2, 8),
                       (2, 9), (2, 10), (2, 11), (2, 12), (2, 13), (2, 14),
                       (6, 1), (6, 2), (6, 3), (6, 4), (6, 5), (6, 6), (6, 7), (6, 8),
                       (6, 9), (6, 10), (6, 11), (6, 12), (6, 13), (6, 14),
                       (9, 1), (9, 2), (9, 3), (9, 4), (9, 5), (9, 6), (9, 7), (9, 8),
                       (9, 9), (9, 10), (9, 11), (9, 12), (9, 13), (9, 14),
                       (13, 1), (13, 2), (13, 3), (13, 4), (13, 5), (13, 6), (13, 7),
                       (13, 8), (13, 9), (13, 10), (13, 11), (13, 12), (13, 13), (13, 14),
                       (1, 2), (3, 2), (4, 2), (5, 2), (7, 2), (8, 2), (10, 2), (11, 2),
                       (12, 2), (14, 2),
                       (1, 13), (3, 13), (4, 13), (5, 13), (7, 13), (8, 13), (10, 13),
                       (11, 13), (12, 13), (14, 13)],
                      [(7, 7), (8, 7), (7, 8), (8, 8)]),

    # Infusion Basin — a bowl seen from above: an open ring with a rim broken
    # at two sides, and a full core rather than a spark. The Arcane Table's
    # face is four things meeting; this is one thing held, which is the
    # difference between the two benches said in a picture.
    "infusion_basin": ([(6, 2), (7, 2), (8, 2), (9, 2),
                        (4, 3), (5, 3), (10, 3), (11, 3),
                        (3, 4), (12, 4), (3, 5), (12, 5),
                        (2, 6), (13, 6), (2, 9), (13, 9),
                        (3, 10), (12, 10), (3, 11), (12, 11),
                        (4, 12), (5, 12), (10, 12), (11, 12),
                        (6, 13), (7, 13), (8, 13), (9, 13),
                        (5, 5), (10, 5), (5, 10), (10, 10)],
                       [(6, 6), (7, 6), (8, 6), (9, 6),
                        (6, 7), (7, 7), (8, 7), (9, 7),
                        (6, 8), (7, 8), (8, 8), (9, 8),
                        (6, 9), (7, 9), (8, 9), (9, 9)]),

    # Scriptorium — shelves seen edge on: three runs of books with gaps where
    # one has been taken out. The gaps are the point; a solid bar reads as a
    # crate and this block is a thing you take from.
    "scriptorium": ([(2, 2), (3, 2), (5, 2), (6, 2), (8, 2), (9, 2), (11, 2), (13, 2),
                     (2, 3), (3, 3), (5, 3), (6, 3), (8, 3), (9, 3), (11, 3), (13, 3),
                     (2, 7), (4, 7), (5, 7), (7, 7), (8, 7), (10, 7), (11, 7), (13, 7),
                     (2, 8), (4, 8), (5, 8), (7, 8), (8, 8), (10, 8), (11, 8), (13, 8),
                     (2, 12), (3, 12), (5, 12), (7, 12), (8, 12), (10, 12), (12, 12), (13, 12),
                     (2, 13), (3, 13), (5, 13), (7, 13), (8, 13), (10, 13), (12, 13), (13, 13),
                     (1, 5), (14, 5), (1, 10), (14, 10)],
                    [(7, 5), (8, 5), (7, 10), (8, 10)]),

    # Grand Scriptorium — the same shelves, with a frame around them and no
    # gaps left. It holds sixty-four times as much, and the picture says so by
    # being the full one.
    "grand_scriptorium": ([(2, 2), (3, 2), (4, 2), (6, 2), (7, 2), (8, 2),
                          (9, 2), (11, 2), (12, 2), (13, 2), (2, 3), (3, 3),
                          (4, 3), (6, 3), (7, 3), (8, 3), (9, 3), (11, 3),
                          (12, 3), (13, 3), (2, 7), (3, 7), (4, 7), (6, 7),
                          (7, 7), (8, 7), (9, 7), (11, 7), (12, 7), (13, 7),
                          (2, 8), (3, 8), (4, 8), (6, 8), (7, 8), (8, 8),
                          (9, 8), (11, 8), (12, 8), (13, 8), (2, 12),
                          (3, 12), (4, 12), (6, 12), (7, 12), (8, 12),
                          (9, 12), (11, 12), (12, 12), (13, 12), (2, 13),
                          (3, 13), (4, 13), (6, 13), (7, 13), (8, 13),
                          (9, 13), (11, 13), (12, 13), (13, 13), (0, 0),
                          (1, 0), (14, 0), (15, 0), (0, 15), (1, 15),
                          (14, 15), (15, 15), (0, 5), (15, 5), (0, 10),
                          (15, 10), (5, 5), (10, 5), (5, 10), (10, 10)],
                          [(6, 5), (7, 5), (8, 5), (9, 5), (6, 10), (7, 10),
                           (8, 10), (9, 10)]),
}


def shelf(metal, glow, seed=3):
    """
    A shelf of books: two rows of spines of different widths in a frame.

    The widths are the whole trick. A row of equal bars reads as a grille, and
    six of these blocks sit side by side in a room a player built — so each
    one is seeded differently and the wall in front of them looks stacked
    rather than tiled.
    """
    ramp = METAL[metal]
    lit = GLOW[glow]
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.put(x, y, ramp[2])
    rng = random.Random(seed)
    for y in range(16):
        for x in range(16):
            if rng.random() < 0.18:
                c.put(x, y, ramp[1])

    # Two shelves, each a run of spines with a gap or two in it.
    for (top, bottom) in ((2, 6), (9, 13)):
        x = 2
        while x < 14:
            width = rng.choice((1, 1, 2, 2, 3))
            if x + width > 14:
                break
            if rng.random() < 0.18:
                x += width                      # a book somebody took out
                continue
            shade = rng.randint(1, 4)
            for bx in range(x, x + width):
                for by in range(top, bottom + 1):
                    c.put(bx, by, lit[shade])
            # A dark line down the near edge of each spine, so a run of them
            # reads as several books rather than one painted board.
            for by in range(top, bottom + 1):
                c.put(x, by, lit[0])
            x += width + 1

    for x in range(16):
        c.put(x, 7, ramp[4])
        c.put(x, 8, ramp[0])
        c.put(x, 0, ramp[5])
        c.put(x, 15, ramp[0])
    for y in range(16):
        c.put(0, y, ramp[4])
        c.put(15, y, ramp[0])
    return c


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


SWORD_GUARD = _crossbar(2, 8, 4)
# Grip and pommel continue down the blade's own axis, one pixel of core each,
# so the outline gives them three on screen — the same trick vanilla uses to
# keep a hilt legible at this size.
SWORD_GRIP = union(rect(3, 12, 4, 12), rect(2, 13, 3, 13))
SWORD_POMMEL = union(rect(1, 14, 2, 15))
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
    """A straight blade over a crossguard and a bound grip."""
    return pixelart.render("sword", METAL[metal], GLOW[glow])
LASH_BLADE = _axis(5, 10, 9, width=2)
LASH_SEGMENTS = {(7, 8), (10, 5), (13, 2)}
LASH_TIP = {(14, 1), (14, 0)}
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


def maul(metal, glow):
    """Most of the mass at the far end of the swing."""
    return pixelart.render("maul", METAL[metal], GLOW[glow])
def lance(metal, glow):
    """Nearly all reach, the weight in a long tapering point."""
    return pixelart.render("lance", METAL[metal], GLOW[glow])


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


def pickaxe(metal, glow):
    """A plain pickaxe: the head arcs over the haft, no 3x3 about it."""
    return pixelart.render("pickaxe", METAL[metal], GLOW[glow])


def axe(metal, glow):
    """One bit, on the right of the haft."""
    return pixelart.render("axe", METAL[metal], GLOW[glow])


def shovel(metal, glow):
    """A spade blade on the end of the haft."""
    return pixelart.render("shovel", METAL[metal], GLOW[glow])


def hoe(metal, glow):
    """A short blade turned across the haft."""
    return pixelart.render("hoe", METAL[metal], GLOW[glow])


def hammer(metal, glow):
    """A squared head with all the weight out past the haft."""
    return pixelart.render("hammer", METAL[metal], GLOW[glow])
def broadaxe(metal, glow):
    """A bearded blade, its cutting edge catching the light."""
    return pixelart.render("broadaxe", METAL[metal], GLOW[glow])


def scythe(metal, glow):
    """A long arc sweeping back over the snath."""
    return pixelart.render("scythe", METAL[metal], GLOW[glow])
def spear(metal, glow):
    """A leaf head on a shaft that runs corner to corner."""
    return pixelart.render("spear", METAL[metal], GLOW[glow])
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


# The tomes (SPEC_CLONE P10.11): the same board and spine as the codex, with a
# different mark cut into the cover. One shape, four marks, because they are
# four uses of one idea — a book you spend on an anvil — and four silhouettes
# would say they were four unrelated things.
TOME_MARKS = {
    # Scrapping: an arrow pointing down, which is where the enchantments go.
    # Not a cross — at sixteen pixels that reads as a medical symbol, which is
    # the one thing this book is not.
    "scrapping": union(rect(5, 4, 10, 5),
                       {(4, 7), (5, 7), (10, 7), (11, 7),
                        (5, 8), (6, 8), (9, 8), (10, 8),
                        (6, 9), (7, 9), (8, 9), (9, 9),
                        (7, 10), (8, 10)}),
    # Transcription: two short lines, one above the other, moving right — a
    # thing copied from one place to another.
    "transcription": union(rect(5, 5, 9, 6), rect(6, 9, 10, 10)),
    # Concentration: three lines narrowing to one.
    "concentration": union(rect(5, 4, 10, 5), rect(6, 7, 9, 8), rect(7, 10, 8, 11)),
    # Patience: an hourglass, which is the only thing the prior-work penalty
    # has ever been about.
    "patience": union(rect(5, 4, 10, 4), rect(6, 5, 9, 5), rect(7, 6, 8, 9),
                      rect(6, 10, 9, 10), rect(5, 11, 10, 11)),
}


def tome(mark, metal, glow):
    """One of the anvil tomes: the codex's board, with this tome's mark on it."""
    c = Canvas()
    c.plate(CODEX_COVER, METAL[metal])
    c.engrave(sorted(CODEX_SPINE), METAL[metal], shade=1)
    c.highlight(sorted(CODEX_PAGES), METAL[metal], shade=5)
    c.carve(TOME_MARKS[mark], GLOW[glow], within=CODEX_COVER)
    c.glow([(4, 7), (4, 8)], GLOW[glow], within=CODEX_SPINE, halo=False)
    c.outline(METAL[metal][0])
    return c


# ---------------------------------------------------------------------------
# Hazard ward effect icons
# ---------------------------------------------------------------------------
#
# Eighteen pixels, which is what vanilla draws a mob effect at; a sixteen-pixel
# icon is scaled and goes soft beside every vanilla one.
#
# An issued shield in aetherium, the same metal as the ration that grants the
# ward, with the hazard's glyph lit in the same element colour as that ration's
# seal. So the icon in the corner of the screen matches the packet the player
# just ate, and the glyph tells the five apart for anyone who cannot rely on
# the colour.

WARD_SIZE = 18

# The shield: flat shoulders, straight flanks, tapering to a point.
_WARD_SHIELD = [
    "..................",
    "..##############..",
    ".################.",
    ".################.",
    ".################.",
    ".################.",
    ".################.",
    ".################.",
    ".################.",
    ".################.",
    "..##############..",
    "..##############..",
    "...############...",
    "....##########....",
    ".....########.....",
    "......######......",
    ".......####.......",
    "........##........",
]

# Glyphs, drawn on the shield's face. '#' is lit.
WARD_GLYPHS = {
    # A flame: a tongue with a notch in its base.
    "flame": [
        "........#.........",
        ".......##.........",
        ".......###........",
        "......####........",
        "......#####.......",
        ".....######.......",
        ".....###.##.......",
        ".....##...#.......",
        "......#...........",
    ],
    # A snowflake: six arms from a centre.
    "snowflake": [
        "........#.........",
        "......#.#.#.......",
        ".......###........",
        "....#..###..#.....",
        ".....#######......",
        "....#..###..#.....",
        ".......###........",
        "......#.#.#.......",
        "........#.........",
    ],
    # A falling drop.
    "droplet": [
        "........#.........",
        "........#.........",
        ".......###........",
        ".......###........",
        "......#####.......",
        "......#####.......",
        "......#####.......",
        ".......###........",
        "..................",
    ],
    # The trefoil: a hub and three blades, one up and two below.
    "trefoil": [
        ".......####.......",
        "......######......",
        ".......####.......",
        "........##........",
        "..................",
        "...###......###...",
        "..####..##..####..",
        "...###......###...",
        "..................",
    ],
    # Weight bearing down: two heavy chevrons. Two pixels thick, because this
    # is the icon that was missing and it should be the clearest of the five.
    "chevrons": [
        "....##......##....",
        ".....##....##.....",
        "......##..##......",
        ".......####.......",
        "........##........",
        "....##......##....",
        ".....##....##.....",
        "......##..##......",
        ".......####.......",
        "........##........",
    ],
}

# Which glyph belongs to which ward.
WARD_ICONS = {
    "thermal_ward": "flame",
    "cryogenic_ward": "snowflake",
    "corrosive_ward": "droplet",
    "radiological_ward": "trefoil",
    "pressure_ward": "chevrons",
}


def _mask(rows, ox=0, oy=0):
    return {(x + ox, y + oy) for y, row in enumerate(rows)
            for x, ch in enumerate(row) if ch == "#"}


def ward_icon(glyph, glow, metal="aetherium"):
    """A hazard ward's status icon: an issued shield with the hazard lit on it."""
    c = Canvas(WARD_SIZE, WARD_SIZE)
    shield = _mask(_WARD_SHIELD)
    c.plate(shield, METAL[metal])
    # A rim engraved one pixel inside the edge, so the face reads as a plate
    # set into a frame rather than a flat sticker.
    rim = {(x, y) for (x, y) in shield
           if any((x + dx, y + dy) not in shield
                  for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    inner = shield - rim
    c.engrave({(x, y) for (x, y) in inner
               if any((x + dx, y + dy) in rim
                      for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))},
              METAL[metal], shade=1)
    # The glyph sits on the shield's face, centred.
    lit = _mask(WARD_GLYPHS[glyph], ox=0, oy=3) & inner
    # A dark engraved bed around the glyph, so it stands off the plate even
    # where the element colour is close to the metal's — dimensional blue on
    # aetherium teal, which is the one that needed it.
    bed = {(x + dx, y + dy) for (x, y) in lit
           for dx in (-1, 0, 1) for dy in (-1, 0, 1)} & inner
    c.engrave(bed - lit, METAL[metal], shade=0)
    c.glow(lit, GLOW[glow], within=inner, halo=False)
    c.outline(METAL[metal][0])
    return c
