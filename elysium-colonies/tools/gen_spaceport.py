#!/usr/bin/env python3
"""
The spaceport's base: four legs, three collars, a deck, and the shaft.

<h2>Why a blueprint and not more blocks</h2>

Because a space elevator is not a column, it is a column somebody had to hold
up. The photograph this was built from is the whole argument: a slender shaft
disappearing into the sky, and underneath it four enormous legs splayed out to
the corners of a platform, tied together with rings. Everything that makes it
read as infrastructure is in that flare — it is the part that says the thing
above it is heavy.

<p>Nobody is going to place a hyperboloid by hand. So the geometry is computed
here, once, and written as a Structurize blueprint into every Elysium style, to
be raised with the build tool like any other building.

<h2>What is structural and what is scenery</h2>

The centre column is the elevator: the anchor at the bottom, tether above it,
and it is what {@code SpaceElevator} reads. The legs, the collars and the deck
are not required for a port to work — a bare column still ships — but a braced
one ships more, because the legs are what let it carry a load. So this file
draws a monument that is also worth building, and a player who would rather
have a mast keeps their mast.

    python3 elysium-colonies/tools/gen_spaceport.py [--preview]
"""
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumcolonies"
BLUEPRINTS = os.path.join(MOD, "src/main/resources/blueprints", NS)

# --------------------------------------------------------------------------
# The shape
# --------------------------------------------------------------------------
#
# Every number here is a decision about the silhouette, so each says what it
# is for rather than being a constant with a name.

#: How wide the platform is. Thirty-one, so there is a true centre column to
#: put the anchor in and fifteen blocks of deck either side of it.
SIZE = 31

#: And how tall the base is, above the deck. The shaft carries on past this;
#: this is only the part that flares, plus a length of bundle above it so the
#: legs are seen to stop rather than simply ending at the top of the file.
HEIGHT = 52

#: Where the rings that tie the legs together sit. Unevenly spaced, wider
#: apart as they go up, because that is what the photograph does and it is
#: what makes the eye read the flare as tapering rather than as stepped.
COLLARS = (12, 22, 31)

RULES = os.path.join(MOD, "src/main/java/com/elysium/colonies/rules/PortRules.java")


def rule(name, kind=int):
    """
    A number read out of the rules rather than repeated here.

    The leg curve has to be the same in three places — this generator, the
    reading that decides whether a port is braced, and the validator that
    checks the two agree — and three copies of a number is three chances for
    one of them to be edited alone. So there is one copy, in the file whose
    harness executes it, and the generator reads it.
    """
    pattern = (rf"public static final {'float' if kind is float else 'int'} "
               rf"{name} = ([0-9.]+)F?;")
    found = re.search(pattern, open(RULES, encoding="utf-8").read())
    if not found:
        raise SystemExit(f"PortRules no longer declares {name}; the generator and "
                         f"the rules have to agree about the shape of a leg")
    return kind(found.group(1))


#: Where the legs stand at the bottom, where they gather, how high, and how
#: sharply they bend — all four from the rules.
FOOT = float(rule("FOOT"))
WAIST = float(rule("WAIST"))
GATHER = rule("GATHER")
BEND = rule("BEND", float)
LEGS = rule("LEGS")

CENTRE = SIZE // 2

AIR = "minecraft:air"
ANCHOR = f"{NS}:elevator_anchor"
TETHER = f"{NS}:elevator_tether"
PYLON = f"{NS}:elevator_pylon"
COLLAR = f"{NS}:elevator_collar"
DECK = f"{NS}:elevator_deck"


def radius_at(height):
    """
    How far a leg's centre is from the shaft at this height above the deck.

    The same curve as PortRules.legRadius, from the same four numbers, read
    out of that file — so the building a player raises and the building the
    game counts as braced are the same building.
    """
    if height >= GATHER:
        return WAIST
    share = (GATHER - height) / float(GATHER)
    return WAIST + (FOOT - WAIST) * (share ** BEND)


def thickness_at(height):
    """
    How thick a leg is there, as a radius: 0 is one block, 1 is three, 2 is five.

    Heavy at the foot, because the load is, and then thin for most of its
    length. The first version was five blocks wide for two thirds of the way
    up and the four legs merged into a pyramid; what makes the silhouette work
    is that the legs are visibly separate things with sky between them.
    """
    if height >= GATHER:
        return 0
    share = (GATHER - height) / float(GATHER)
    if share >= 0.85:
        return 2
    return 1 if share >= 0.35 else 0


def leg_centres(height):
    """Where the four legs are at this height, on the diagonals."""
    radius = radius_at(height)
    out = []
    for leg in range(LEGS):
        angle = math.pi / 4.0 + leg * (2.0 * math.pi / LEGS)
        out.append((CENTRE + radius * math.cos(angle),
                    CENTRE + radius * math.sin(angle)))
    return out


def ring(height, radius, blocks, block):
    """A collar: the outline of a circle, one block thick."""
    placed = 0
    for x in range(SIZE):
        for z in range(SIZE):
            away = math.hypot(x - CENTRE, z - CENTRE)
            if radius - 0.6 <= away <= radius + 0.6:
                if blocks.get((x, height, z)) in (None, AIR):
                    blocks[(x, height, z)] = block
                    placed += 1
    return placed


def build(trim):
    """
    The whole base as {(x, y, z): block id}.

    @param trim the style's own stone, used for the deck's rim so that a port
                on Cinderveil is visibly a Cinderveil building
    """
    blocks = {}

    # The deck. A disc rather than a square: a square platform under a round
    # tower reads as a floor somebody forgot to finish.
    for x in range(SIZE):
        for z in range(SIZE):
            away = math.hypot(x - CENTRE, z - CENTRE)
            if away <= CENTRE - 0.5:
                blocks[(x, 0, z)] = DECK if away <= CENTRE - 2.5 else trim

    # The legs, and a flared foot where each one lands.
    #
    # Walked in quarter-block steps rather than layer by layer, because where
    # the curve is steepest a leg moves more than one block between one height
    # and the next, and a leg drawn a layer at a time comes out as a stack of
    # disconnected lumps. Sub-sampling fills the diagonal, which is the
    # difference between a leg and a staircase.
    for step in range(4, (GATHER + 1) * 4):
        height = step / 4.0
        thick = thickness_at(int(height))
        for centre_x, centre_z in leg_centres(height):
            for x in range(int(centre_x - thick), int(centre_x + thick) + 1):
                for z in range(int(centre_z - thick), int(centre_z + thick) + 1):
                    if not (0 <= x < SIZE and 0 <= z < SIZE):
                        continue
                    if math.hypot(x - centre_x, z - centre_z) > thick + 0.35:
                        continue
                    blocks[(x, int(height), z)] = PYLON
            if thick == 0:
                blocks[(int(round(centre_x)), int(height),
                        int(round(centre_z)))] = PYLON

    # The buttresses: the legs do not simply stop at the deck, they spread
    # into it, which is the difference between a tower standing on a floor and
    # a tower built out of one.
    for centre_x, centre_z in leg_centres(1):
        for x in range(int(centre_x) - 3, int(centre_x) + 4):
            for z in range(int(centre_z) - 3, int(centre_z) + 4):
                if not (0 <= x < SIZE and 0 <= z < SIZE):
                    continue
                if math.hypot(x - centre_x, z - centre_z) <= 3.2:
                    blocks[(x, 0, z)] = PYLON

    # The collars.
    for height in COLLARS:
        ring(height, radius_at(height) + 1.0, blocks, COLLAR)

    # The bundle above the waist: four thin tubes around the shaft, carried on
    # up past where the legs stop.
    for height in range(GATHER + 1, HEIGHT - 2):
        for centre_x, centre_z in leg_centres(GATHER):
            blocks[(int(round(centre_x)), height, int(round(centre_z)))] = PYLON

    # And the elevator itself, which is the only part of this that does
    # anything: the anchor on the deck and the tether straight up the middle.
    blocks[(CENTRE, 1, CENTRE)] = ANCHOR
    for height in range(2, HEIGHT):
        blocks[(CENTRE, height, CENTRE)] = TETHER
    return blocks


# --------------------------------------------------------------------------
# Writing it out
# --------------------------------------------------------------------------

#: The stone each style rims its deck with. Vanilla only, deliberately: this
#: module is an add-on and may be the only Elysium jar installed, and a
#: blueprint that cannot be placed without three other mods is a blueprint
#: nobody places.
TRIM = {
    "Cinderhold": "minecraft:polished_blackstone",
    "Frostfall": "minecraft:polished_deepslate",
    "Pale Reach": "minecraft:smooth_quartz",
    "High Orbit": "minecraft:iron_block",
    "Bastion": "minecraft:dark_prismarine",
}

#: What the blueprint needs to place. Structurize reads it, and a missing mod
#: is a hole in the building rather than an error, so it had better be short.
REQUIRED = ["structurize", "minecolonies", NS]

#: The data version blueprints in this pack were written at.
MC_VERSION = 3955


def as_blueprint(blocks, name):
    """The voxels as the NBT Structurize reads, in its own y, z, x order."""
    palette = [AIR]
    index = {AIR: 0}
    for block in blocks.values():
        if block not in index:
            index[block] = len(palette)
            palette.append(block)

    flat = []
    for y in range(HEIGHT):
        for z in range(SIZE):
            for x in range(SIZE):
                flat.append(index[blocks.get((x, y, z), AIR)])
    # Two shorts to an int, high half first: NBT has no short array and
    # Structurize packs them rather than wasting half the file.
    packed = []
    for position in range(0, len(flat), 2):
        high = flat[position]
        low = flat[position + 1] if position + 1 < len(flat) else 0
        packed.append(((high << 16) | low) - (1 << 32)
                      if ((high << 16) | low) >= (1 << 31)
                      else ((high << 16) | low))

    root = nbt.Tag(nbt.COMPOUND, {
        "size_x": nbt.Tag(nbt.SHORT, SIZE),
        "size_y": nbt.Tag(nbt.SHORT, HEIGHT),
        "size_z": nbt.Tag(nbt.SHORT, SIZE),
        "name": nbt.Tag(nbt.STRING, name),
        "version": nbt.Tag(nbt.BYTE, 1),
        "mcversion": nbt.Tag(nbt.INT, MC_VERSION),
        "palette": nbt.Tag(nbt.LIST, [
            nbt.Tag(nbt.COMPOUND, {"Name": nbt.Tag(nbt.STRING, block)})
            for block in palette], nbt.COMPOUND),
        "blocks": nbt.Tag(nbt.INT_ARRAY, packed),
        "tile_entities": nbt.Tag(nbt.LIST, [], nbt.COMPOUND),
        "entities": nbt.Tag(nbt.LIST, [], nbt.COMPOUND),
        # Written as strings even when the list is short: an empty list whose
        # element type is left as END is the bug that corrupted seventeen
        # blueprints the first time this pack was generated.
        "required_mods": nbt.Tag(nbt.LIST, [
            nbt.Tag(nbt.STRING, mod) for mod in REQUIRED], nbt.STRING),
        "architects": nbt.Tag(nbt.LIST, [nbt.Tag(nbt.STRING, "Elysium")],
                              nbt.STRING),
    })
    return root


def preview(blocks):
    """
    The silhouette, as text, so the shape can be judged without a game.

    A front elevation: for every column of the model, the nearest thing you
    would see standing square to it. Crude on purpose — what is being checked
    is the flare, and a flare is visible in ASCII.
    """
    marks = {DECK: "=", PYLON: "#", COLLAR: "o", TETHER: "|", ANCHOR: "A"}
    lines = []
    for y in range(HEIGHT - 1, -1, -1):
        row = ""
        for x in range(SIZE):
            seen = " "
            for z in range(SIZE):
                block = blocks.get((x, y, z))
                if block and block != AIR:
                    seen = marks.get(block, "+")
                    break
            row += seen
        lines.append(row)
    return "\n".join(lines)


def main():
    made = 0
    for style, trim in TRIM.items():
        blocks = build(trim)
        if "--preview" in sys.argv and made == 0:
            print(preview(blocks))
            print()
        folder = os.path.join(BLUEPRINTS, style, "infrastructure")
        os.makedirs(folder, exist_ok=True)
        path = os.path.join(folder, "spaceport.blueprint")
        nbt.save(path, "", as_blueprint(blocks, "spaceport.blueprint"))
        made += 1
    counts = {}
    for block in build(TRIM["Cinderhold"]).values():
        counts[block] = counts.get(block, 0) + 1
    print(f"spaceport : {made} style(s), {SIZE}x{HEIGHT}x{SIZE}, "
          f"{sum(counts.values())} blocks")
    for block, count in sorted(counts.items(), key=lambda pair: -pair[1]):
        print(f"  {count:5d}  {block}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
