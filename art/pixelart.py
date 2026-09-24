"""
Vanilla-idiom sprites, authored as shade maps.

<h2>Why the sprites are written down rather than drawn in code</h2>

Everything else in this art package builds a sprite out of rectangles and set
operations, which is the right tool for a shape with a rule behind it — an ore's
scatter, a rune's sigil, a block's tiling. It is the wrong tool for a sword,
because a sword has no rule: it is a silhouette somebody decided on, and
expressing it as unions of rectangles means nobody can see it in the source.
Every adjustment to the old tool sprites was made blind, rendered, squinted at,
and made blind again.

A shade map is the sprite, visible in the file:

    "................",
    ".........000000.",
    "........05555520",

Each character is one pixel and says how *lit* that pixel is, never what colour
it is. Colour arrives at render time from the material's ramp, which is the
whole point — one map, twenty-six materials, and "recoloured" is a dictionary
lookup rather than a separate drawing.

<h2>The legend</h2>

    .           transparent
    0           the metal's outline (its own darkest tone, not black)
    1 .. 5      the metal, darkest to brightest highlight
    k           the handle's outline
    a .. e      the handle, darkest to brightest
    g           an element accent, dim
    G           an element accent, core

Two separate ramps, because that is the single largest thing that makes a
Minecraft tool look like a Minecraft tool: the handle is wood and stays wood
whatever the head is made of. A sprite drawn in one material end to end reads as
a casting of a tool rather than a tool, and it also destroys the recolour — with
nothing constant to compare against, a copper head and an iron head are just two
greys.

<h2>What these are not</h2>

They are not vanilla's pixels. They are shapes drawn here, following the
conventions vanilla uses — handle on the diagonal from the lower left, head in
the upper right, hard two-pixel forms, the light from the top left — so that a
player reads them as belonging to the same game. The proportions were chosen
against vanilla's, the pixels were not taken from it.
"""

# --------------------------------------------------------------------------
# The handle
# --------------------------------------------------------------------------
#
# One ramp for every tool in the mod, deliberately. See the note above about
# what a constant reference does for the recolour.
WOOD = ["#2a1c10", "#4a341c", "#63462a", "#7d5c39", "#99754c", "#b28f63"]


LEGEND_METAL = {"0": 0, "1": 1, "2": 2, "3": 3, "4": 4, "5": 5}
LEGEND_WOOD = {"k": 0, "a": 1, "b": 2, "c": 3, "d": 4, "e": 5}
LEGEND_GLOW = {"g": 1, "G": 4}

SIZE = 16


# ==========================================================================
# The maps
# ==========================================================================

MAPS = {

    # ----------------------------------------------------------------------
    # Materials
    # ----------------------------------------------------------------------

    # A cast bar seen from slightly above: a bright top face, a mid front face,
    # and a dark underside. The old one had every edge rounded and one flat
    # tone, which is why twenty-six materials all read as the same pebble.
    "ingot": (
        "................",
        "................",
        "......000000....",
        "....0055554400..",
        "...055555555440.",
        "..05444444444430",
        ".043333333333320",
        ".043333333333320",
        ".03222222222210.",
        "..032222222210..",
        "...0211111110...",
        "....00000000....",
        "................",
        "................",
        "................",
        "................",
    ),

    # Not a bar. Voidglass is not cast, it is cleaved, so it keeps an angular
    # sliver rather than being made to match the metals.
    "shard": (
        "................",
        "............00..",
        "...........0550.",
        "..........05540.",
        ".........055430.",
        "........0554320.",
        ".......0554320..",
        "......0554320...",
        ".....0554320....",
        "....0554320.....",
        "...0554320......",
        "..055320........",
        "..05320.........",
        "..0320..........",
        "..000...........",
        "................",
    ),

    # ----------------------------------------------------------------------
    # Armour
    # ----------------------------------------------------------------------

    "helmet": (
        "................",
        "................",
        ".....000000.....",
        "....05555540....",
        "...0555555540...",
        "..055555555540..",
        "..054444444430..",
        "..054444444430..",
        "..054444444430..",
        "..043300003320..",
        "..043300003320..",
        "..043333333320..",
        "..042222222220..",
        "...0000000000...",
        "................",
        "................",
    ),

    "chestplate": (
        "................",
        "................",
        "..000......000..",
        ".05540....05540.",
        ".05555000055540.",
        ".05444444444430.",
        ".05444422444430.",
        "..054442244430..",
        "..043332233320..",
        "..043332233320..",
        "..043332233320..",
        "...0433333320...",
        "....00000000....",
        "................",
        "................",
        "................",
    ),

    "leggings": (
        "................",
        "................",
        "...0000000000...",
        "..055555555540..",
        "..055555555540..",
        "..022222222220..",
        "..054430054430..",
        "..054430054430..",
        "..043320043320..",
        "..043320043320..",
        "..043320043320..",
        "..043320043320..",
        "...0000..0000...",
        "................",
        "................",
        "................",
    ),

    "boots": (
        "................",
        "................",
        "................",
        "................",
        "................",
        "...000....000...",
        "..05540..05540..",
        "..05540..05540..",
        ".05444300544430.",
        ".05444300544430.",
        ".043333204333320",
        ".043333204333320",
        "..000000.000000.",
        "................",
        "................",
        "................",
    ),

    # ----------------------------------------------------------------------
    # Tools
    # ----------------------------------------------------------------------

    "hammer": (
        "................",
        ".........000000.",
        "........05555520",
        "........05444410",
        "........05444410",
        "........05444410",
        "........0544420.",
        ".......kd54420..",
        "......kdb0000...",
        ".....kdbk.......",
        "....kdbk........",
        "...kdbk.........",
        "..kdbk..........",
        ".kdbk...........",
        "..kk............",
        "................",
    ),

    "broadaxe": (
        "..........000...",
        ".........0432000",
        "........04333345",
        "........04333345",
        "........04333345",
        "........04333345",
        "........04333200",
        ".......kdb4320..",
        "......kdbk000...",
        ".....kdbk.......",
        "....kdbk........",
        "...kdbk.........",
        "..kdbk..........",
        ".kdbk...........",
        "..kk............",
        "................",
    ),

    "scythe": (
        "..000...........",
        ".055400.........",
        ".05555400.......",
        "..055555400.....",
        "...005444430....",
        ".....00544430...",
        ".......005430...",
        ".........0dbk...",
        "........kdbk....",
        ".......kdbk.....",
        "......kdbk......",
        ".....kdbk.......",
        "....kdbk........",
        "...kdbk.........",
        "..kdbk..........",
        "...kk...........",
    ),

    "spear": (
        "............000.",
        "...........05250",
        "..........054520",
        ".........0544420",
        ".........054420.",
        ".........05420..",
        "........kdb00...",
        ".......kdbk.....",
        "......kdbk......",
        ".....kdbk.......",
        "....kdbk........",
        "...kdbk.........",
        ".0kdbk..........",
        "03dbk...........",
        "03bk............",
        ".0k.............",
    ),

    # ----------------------------------------------------------------------
    # The plain shapes
    # ----------------------------------------------------------------------
    #
    # Sword, pickaxe, axe, shovel and hoe — what every material makes first.
    # The sword map is down with the weapons, because it is one.
    #
    # Each is drawn as a head sitting on the top of the haft rather than beside
    # it. The first versions had the haft stop two rows short, which leaves the
    # head floating: at sixteen pixels a one-row gap does not read as a gap, it
    # reads as a badly drawn tool. check() now refuses a map in more than one
    # piece.

    "pickaxe": (
        "......0000000...",
        "....00555555500.",
        "...0544444444420",
        "...0520005442520",
        "....00..0544200.",
        ".........0db0...",
        "........kdbk....",
        ".......kdbk.....",
        "......kdbk......",
        ".....kdbk.......",
        "....kdbk........",
        "...kdbk.........",
        "..kdbk..........",
        ".kdbk...........",
        "..kk............",
        "................",
    ),
    "axe": (
        ".........04320..",
        ".........043340.",
        ".........0433350",
        ".........0433335",
        ".........0433350",
        ".........043340.",
        "........kdb000..",
        ".......kdbk.....",
        "......kdbk......",
        ".....kdbk.......",
        "....kdbk........",
        "...kdbk.........",
        "..kdbk..........",
        ".kdbk...........",
        "..kk............",
        "................",
    ),
    "shovel": (
        "..........0540..",
        ".........054420.",
        ".........054420.",
        ".........054420.",
        ".........054420.",
        ".........kd520..",
        "........kdb00...",
        ".......kdbk.....",
        "......kdbk......",
        ".....kdbk.......",
        "....kdbk........",
        "...kdbk.........",
        "..kdbk..........",
        ".kdbk...........",
        "..kk............",
        "................",
    ),
    "hoe": (
        ".........0000000",
        "........05555552",
        "........05444442",
        ".........0520000",
        ".........0520...",
        ".........kdbk...",
        "........kdbk....",
        ".......kdbk.....",
        "......kdbk......",
        ".....kdbk.......",
        "....kdbk........",
        "...kdbk.........",
        "..kdbk..........",
        ".kdbk...........",
        "..kk............",
        "................",
    ),

    # ----------------------------------------------------------------------
    # Weapons
    # ----------------------------------------------------------------------

    "sword": (
        ".............00.",
        "............0450",
        "............0530",
        "...........0530.",
        "..........0530..",
        ".........0530...",
        "........0530....",
        ".......0530.....",
        "..00..0530......",
        ".04400530.......",
        "..044530........",
        ".kcb440.........",
        ".kbc0440........",
        "0bck.00.........",
        "3320............",
        "000.............",
    ),

    "maul": (
        "........0000000.",
        ".......055555520",
        "......0533333332",
        "......0533333332",
        "......0533333332",
        "......0533333332",
        ".......053333320",
        "........0533200.",
        ".......kdb000...",
        "......kdbk......",
        ".....kdbk.......",
        "....kdbk........",
        "...kdbk.........",
        "..kdbk..........",
        ".kdbk...........",
        "..kk............",
    ),

    "lance": (
        "...........0540.",
        "..........055550",
        ".........0555540",
        "........0544430.",
        "........054430..",
        ".........0530...",
        "........kdb0....",
        ".......kdbk.....",
        "......kdbk......",
        ".....kdbk.......",
        "....kdbk........",
        "...kdbk.........",
        "..kdbk..........",
        ".kdbk...........",
        "kdbk............",
        ".kk.............",
    ),
}


# ==========================================================================
# Element accents
# ==========================================================================
#
# Where the element's colour goes on each shape, and nowhere else.
#
# Kept out of the maps on purpose. A map is about *form* and is the same for
# every material; an accent is about which element a particular item is, varies
# per item, and is the one thing on the sprite allowed to be fully saturated.
# Mixing them into the same grid would mean editing twelve silhouettes to move
# one highlight.
#
# Two or three pixels each. The old sprites spent a third of their area on glow
# and lost their silhouette to it — at sixteen pixels the surrounding metal is
# what sells a light, not the size of the light.
ACCENTS = {
    "ingot":      [(8, 6)],
    "shard":      [(7, 7), (8, 6)],

    # One pixel each, and never a symmetrical pair. Two lit pixels side by side
    # on a helmet are read as eyes before they are read as anything else, and
    # once a helmet has a face nothing else about the sprite gets looked at. A
    # single off-centre stud reads as a fitting, which is what it is.
    "helmet":     [(8, 7)],
    "chestplate": [(8, 7)],
    "leggings":   [(8, 4)],
    "boots":      [(4, 7)],

    "pickaxe":    [(11, 2)],
    "axe":        [(12, 4)],
    "shovel":     [(11, 3)],
    "hoe":        [(13, 2)],

    "hammer":     [(11, 3)],
    "broadaxe":   [(11, 3)],
    "scythe":     [(3, 2)],
    "spear":      [(12, 3)],

    # Weapons keep two, because on a blade the accent runs along the fuller and
    # reads as a lit edge rather than as a dot.
    "sword":      [(9, 6), (10, 5)],
    "maul":       [(10, 3), (11, 3)],
    "lance":      [(11, 2), (11, 3)],
}


# ==========================================================================
# Rendering
# ==========================================================================

# How many separate lumps a sprite is legitimately drawn in. Default is one.
PIECES = {
    # A pair of boots is a pair. Nothing joins them and nothing should.
    "boots": 2,
}


def connected_pieces(rows):
    """
    How many separate lumps the sprite's *body* is drawn in.

    Outlines are excluded, and that is the entire point of the function. The
    first version counted them, and it passed the exact bug it was written to
    catch: a tool head that stops two rows above its haft still has the head's
    outline touching the haft's outline, so by that measure it was one piece —
    while on screen there is a visible dark break with the metal on one side and
    the wood on the other. Ask about the body and the break is two pieces.

    Diagonal neighbours count as touching, because at this resolution a
    staircase edge reads as a continuous line even though no two of its pixels
    share a side.
    """
    ignored = set(".") | {"0", "k"}
    filled = {(x, y) for y in range(SIZE) for x in range(SIZE)
              if rows[y][x] not in ignored}
    seen = set()
    pieces = 0
    for start in filled:
        if start in seen:
            continue
        pieces += 1
        seen.add(start)
        queue = [start]
        while queue:
            (x, y) = queue.pop()
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    point = (x + dx, y + dy)
                    if point in filled and point not in seen:
                        seen.add(point)
                        queue.append(point)
    return pieces


def check():
    """
    Every map is sixteen rows of sixteen known characters.

    Called at import, because a map that is fifteen characters wide does not
    fail — it silently shifts every pixel below it by one, which looks like bad
    drawing rather than a typo and is very hard to find by eye.
    """
    known = set(".") | set(LEGEND_METAL) | set(LEGEND_WOOD) | set(LEGEND_GLOW)
    for name, rows in MAPS.items():
        if len(rows) != SIZE:
            raise ValueError("%s has %d rows, expected %d" % (name, len(rows), SIZE))
        for y, row in enumerate(rows):
            if len(row) != SIZE:
                raise ValueError("%s row %d is %d characters, expected %d"
                                 % (name, y, len(row), SIZE))
            bad = set(row) - known
            if bad:
                raise ValueError("%s row %d uses unknown characters %s"
                                 % (name, y, sorted(bad)))
        if name not in ACCENTS:
            raise ValueError("%s has no element accent" % name)

        # One piece, not two.
        #
        # Every tool here is a head sitting on a haft, and the easy mistake is
        # to stop the haft a row short of the head. At sixteen pixels a one-row
        # gap does not read as a gap — it reads as a badly drawn tool, and it is
        # very hard to see in a map full of dots. Four of these shipped that way
        # in their first draft.
        pieces = connected_pieces(rows)
        expected = PIECES.get(name, 1)
        if pieces != expected:
            raise ValueError("%s is drawn in %d separate pieces, expected %d — "
                             "something is floating. If it is meant to be, say "
                             "so in PIECES" % (name, pieces, expected))
    for name, points in ACCENTS.items():
        if name not in MAPS:
            raise ValueError("accent for %s, which is not a map" % name)
        for (x, y) in points:
            if MAPS[name][y][x] == ".":
                raise ValueError("%s accent at (%d, %d) is on a transparent "
                                 "pixel, so it floats beside the sprite"
                                 % (name, x, y))


check()


def render(name, metal_ramp, glow_ramp=None, wood_ramp=WOOD):
    """
    One map plus one material, as a Canvas.

    `metal_ramp` and `glow_ramp` are six-entry and five-entry ramps in the
    shapes style.METAL and style.GLOW already use, so this drops into the
    existing call sites unchanged.
    """
    from canvas import Canvas

    canvas = Canvas(SIZE, SIZE)
    rows = MAPS[name]
    for y in range(SIZE):
        for x in range(SIZE):
            character = rows[y][x]
            if character == ".":
                continue
            if character in LEGEND_METAL:
                canvas.put(x, y, metal_ramp[LEGEND_METAL[character]])
            elif character in LEGEND_WOOD:
                canvas.put(x, y, wood_ramp[LEGEND_WOOD[character]])
            elif glow_ramp is not None:
                canvas.put(x, y, glow_ramp[LEGEND_GLOW[character]])

    if glow_ramp is not None:
        accent(canvas, name, glow_ramp)
    return canvas


def accent(canvas, name, glow_ramp):
    """
    The element's two or three pixels, with one dim ring around them.

    The ring is confined to pixels that are already part of the sprite, so a
    light never grows the silhouette — which is the failure that made the old
    sprites read as blobs with a shape somewhere inside.
    """
    core = set(ACCENTS[name])
    ring = set()
    for (x, y) in core:
        for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            point = (x + dx, y + dy)
            if point not in core and canvas.filled(*point):
                ring.add(point)
    for (x, y) in ring:
        canvas.put(x, y, glow_ramp[1])
    for (x, y) in core:
        canvas.put(x, y, glow_ramp[4])
