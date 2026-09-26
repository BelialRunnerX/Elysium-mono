#!/usr/bin/env python3
"""
Every sprite, model and data file elysium-bestiary ships.

    python3 tools/gen_bestiary.py

Nothing in here decides anything. The nine beasts, their three shapes, the
corners of the sheet each part is drawn in and the reward each one gives are
all read back out of the Java, because a generator that has its own opinion
about any of those is a generator that drifts away from the game.

There is one exception and it is deliberate: working the reward out from the
threat is written twice, once in BeastRules.derive and once in earned() below.
The numbers it uses are read from the Java, but the shape of the arithmetic is
not. validate.py runs the harness, which prints the table it derived, and
compares it against this one line by line — so the two copies are checked
against each other on every build rather than trusted.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumbestiary"

OUT = os.environ.get("ELYSIUM_BESTIARY_OUT", MOD)
ASSETS = os.path.join(OUT, "src/main/resources/assets", NS)
DATA = os.path.join(OUT, "src/main/resources/data", NS)

RULES = os.path.join(MOD, "src/main/java/com/elysium/bestiary/rules/BeastRules.java")
MODEL = os.path.join(MOD, "src/main/java/com/elysium/bestiary/client/BeastModel.java")
SHARED = os.path.join(MOD, "src/main/java/com/elysium/bestiary/client/CreatureModel.java")
FAUNA_MODEL = os.path.join(MOD,
                           "src/main/java/com/elysium/bestiary/client/FaunaModel.java")
FAUNA_RULES = os.path.join(MOD,
                           "src/main/java/com/elysium/bestiary/rules/FaunaRules.java")
BEHAVIOURS = os.path.join(
    MOD, "src/main/java/com/elysium/bestiary/rules/BehaviourRules.java")

try:
    from PIL import Image, ImageDraw
except ImportError:
    print("Pillow is needed to draw the sprites: pip install pillow")
    sys.exit(1)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(obj, handle, indent=2)
        handle.write("\n")


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


# ---------------------------------------------------------------------------
# What the Java says
# ---------------------------------------------------------------------------

RULES_SOURCE = read(RULES)
MODEL_SOURCE = read(MODEL)
SHARED_SOURCE = read(SHARED)
FAUNA_MODEL_SOURCE = read(FAUNA_MODEL)
FAUNA_RULES_SOURCE = read(FAUNA_RULES)
BEHAVIOUR_SOURCE = read(BEHAVIOURS)

FAMILY = {}
for name, value in re.findall(
        r'public static final String (PROWLER|BRUTE|WIGHT) = "(\w+)";', RULES_SOURCE):
    FAMILY[name] = value
if len(FAMILY) != 3:
    sys.exit("the three families are not three families any more")


def number(source, name, suffix="D"):
    found = re.search(
        r"public static final (?:double|int|float) " + name
        + r" = ([-\d.]+)" + suffix + "?;", source)
    if not found:
        sys.exit("cannot find " + name + " in the Java")
    return float(found.group(1))


WORTH_OF_A_DROP = int(number(RULES_SOURCE, "WORTH_OF_A_DROP", ""))
BASE_RATE = number(RULES_SOURCE, "BASE_RATE")
TILT_PER_TIER = number(RULES_SOURCE, "TILT_PER_TIER")
DROP_SHARE = number(RULES_SOURCE, "DROP_SHARE")
LEAST_DROPS = int(number(RULES_SOURCE, "LEAST_DROPS", ""))
LEAST_EXPERIENCE = int(number(RULES_SOURCE, "LEAST_EXPERIENCE", ""))
LEAST_WORTH = LEAST_EXPERIENCE + LEAST_DROPS * WORTH_OF_A_DROP

BEAST_ROW = re.compile(
    r'new Beast\("(\w+)", (PROWLER|BRUTE|WIGHT), (\d+), ([\d.]+)D, ([\d.]+)D, '
    r'([\d.]+)D, ([\d.]+)D, (\d+), ([\d.]+)F, ([\d.]+)F\)')

# Scoped to the one list, because the zombie a few lines below it is written
# the same way and is not part of the bestiary. Reading it as a tenth beast is
# how the pack ends up shipping a spawn egg for a vanilla mob.
TABLE = re.search(r"public static final List<Beast> BEASTS = List\.of\((.*?)\);",
                  RULES_SOURCE, re.S)
if not TABLE:
    sys.exit("cannot find the bestiary in the Java")

BEASTS = []

for row in BEAST_ROW.finditer(TABLE.group(1)):
    BEASTS.append({
        "id": row.group(1),
        "family": FAMILY[row.group(2)],
        "tier": int(row.group(3)),
        "health": float(row.group(4)),
        "damage": float(row.group(5)),
        "speed": float(row.group(6)),
        "armour": float(row.group(7)),
        "weight": int(row.group(8)),
        "width": float(row.group(9)),
        "height": float(row.group(10)),
    })
if len(BEASTS) < 9:
    sys.exit("read %d beasts out of the Java, which is fewer than the nine it has"
             % len(BEASTS))

DECLARED = len(re.findall(r"new Beast\(", TABLE.group(1)))
if len(BEASTS) != DECLARED:
    sys.exit("read %d beasts but the Java declares %d, so the pattern above has "
             "stopped matching one of them" % (len(BEASTS), DECLARED))


# The two things an ability puts in the world, read out of SummonRules for the
# same reason as everything else here: how big one is and how long it stands
# are rules, and what it looks like is art.
SUMMON_SOURCE = read(os.path.join(
    MOD, "src/main/java/com/elysium/bestiary/rules/SummonRules.java"))
SUMMONS = [
    {"id": one[0], "lasts": int(one[1])}
    for one in re.findall(
        r'new Summon\((\w+), \w+\.\w+, ([\d_]+),', re.sub(r"\s+", " ", SUMMON_SOURCE))
]
# The table names its rows by constant -- BOLT, SHADE -- and one of those
# constants is the library's own, so the name a texture has to be filed under
# is not in this file at all. It is the lower-cased constant either way, which
# is checked below rather than assumed.
SUMMON_IDS = [one["id"].lower() for one in SUMMONS]
if len(SUMMON_IDS) != 2 or sorted(SUMMON_IDS) != ["bolt", "shade"]:
    print("SummonRules reads as %s rather than a bolt and a shade" % SUMMON_IDS)
    sys.exit(2)


# The eight myths, read out of MythRules for the same reason as everything
# else: which shape each wears and what beats it are rules, and what colour it
# is, is art.
MYTH_SOURCE = read(os.path.join(
    MOD, "src/main/java/com/elysium/bestiary/rules/MythRules.java"))
MYTHS = [
    {"id": one[0], "bane": one[1], "answer": one[2], "tier": int(one[3]),
     "weight": int(one[7]), "flees": one[8] == "true", "form": one[9]}
    for one in re.findall(
        r'new Myth\("([a-z_]+)", (\w+), "([\w:]+)", (\d+), ([\d.]+)D, ([\d.]+)D,'
        r' ([\d.]+)D, (\d+), (true|false), [\w.]*?(\w+)\)',
        re.sub(r"\s+", " ", MYTH_SOURCE))
]
if len(MYTHS) != 8:
    print("MythRules reads as %d myth(s) rather than eight" % len(MYTHS))
    sys.exit(2)

#: What each way past armour looks like, as a pair of shades. Art, not a rule.
BANE_SHADES = {
    "PETRIFY": ((122, 126, 118), (60, 64, 58)),
    "DRAIN": ((126, 42, 56), (52, 18, 26)),
    "BREATH": ((198, 106, 38), (92, 38, 14)),
    "PLATING": ((168, 122, 60), (78, 56, 26)),
    "NUMBERS": ((96, 110, 74), (44, 52, 34)),
    "THIEVERY": ((84, 94, 118), (38, 44, 58)),
    "HEIGHT": ((214, 212, 204), (140, 146, 158)),
    "SPORES": ((150, 96, 152), (70, 42, 74)),
}
_unshaded = sorted({one["bane"] for one in MYTHS} - set(BANE_SHADES))
if _unshaded:
    print("no shades for %s" % _unshaded)
    sys.exit(2)


#: One sentence per way past armour, because "this thing is dangerous" with no
#: sentence is a creature a player learns by dying to it twice.
BANE_WORDS = {
    "PETRIFY": "Meeting its eye slows you to a standstill. Do not look at it.",
    "DRAIN": "What it takes from your health, it keeps. Eating does not give it back.",
    "BREATH": "It burns through whatever you are holding up.",
    "PLATING": "Nothing but the right metal marks it.",
    "NUMBERS": "There is never one of them, and each is braver for the others.",
    "THIEVERY": "It takes what is in your hand and runs. Kill it to get it back.",
    "HEIGHT": "It goes where you cannot follow.",
    "SPORES": "What it leaves behind is still there when you stop to breathe.",
}
_unworded = sorted(set(BANE_SHADES) - set(BANE_WORDS))
if _unworded:
    print("no sentence for %s" % _unworded)
    sys.exit(2)

#: What each leaves behind, read out of the rules' own table rather than
#: decided here -- the rules hold every one of these to being a name something
#: can look up, and a second list would be a second place for it to be wrong.
_leavings = re.search(r"List<String> LEAVINGS = List\.of\((.*?)\);",
                      re.sub(r"\s+", " ", MYTH_SOURCE))
LEAVINGS = re.findall(r'"([\w:]+)"', _leavings.group(1)) if _leavings else []
if len(LEAVINGS) < len(MYTHS):
    print("MythRules names %d leaving(s) for %d myth(s)"
          % (len(LEAVINGS), len(MYTHS)))
    sys.exit(2)
MYTH_LEAVINGS = {one["id"]: LEAVINGS[at % len(LEAVINGS)]
                 for at, one in enumerate(MYTHS)}


# The eleven companions, read out of CompanionRules: which block's job each
# does and what keeping one costs are rules, and what colour it is, is art.
COMPANION_SOURCE = read(os.path.join(
    MOD, "src/main/java/com/elysium/bestiary/rules/CompanionRules.java"))
COMPANIONS = [
    {"id": one[0], "gift": one[1], "rivals": one[2], "tamed": one[3] == "true",
     "upkeep": int(one[4]), "weight": int(one[7])}
    for one in re.findall(
        r'new Companion\("([a-z_]+)", (\w+), "([\w:]+)", (true|false), (\d+),'
        r' ([\d.]+)D, (\d+), (\d+)\)', re.sub(r"\s+", " ", COMPANION_SOURCE))
]
if len(COMPANIONS) != 11:
    print("CompanionRules reads as %d companion(s) rather than eleven"
          % len(COMPANIONS))
    sys.exit(2)

#: One sentence per thing a companion does, because "this creature is useful"
#: with no sentence is a creature a player keeps by accident and never uses.
GIFT_WORDS = {
    "SHOWS_DARK": "It points at ground dark enough for something to stand on.",
    "REACH": "Stand near one and you can reach further.",
    "HOLDS": "Give it something and it will hold it where it stands.",
    "PRESSES": "Tell it what to press and it presses it.",
    "BLOOMS": "Flowers come up where it has been.",
    "DIGS": "Stand near one and the ground gives way faster.",
    "HIDES": "Find it three times and it pays out.",
    "CHILLS": "What is fighting you slows down near it.",
    "DOUBLES": "There appear to be several of you.",
    "BITES": "It goes for whatever is going for you.",
    "BURNS": "What stands too close to it catches.",
}

#: And what each looks like. Art, not a rule.
GIFT_SHADES = {
    "SHOWS_DARK": ((226, 214, 128), (96, 88, 44)),
    "REACH": ((198, 122, 96), (94, 52, 38)),
    "HOLDS": ((132, 128, 120), (62, 60, 56)),
    "PRESSES": ((188, 122, 78), (86, 52, 30)),
    "BLOOMS": ((222, 196, 208), (120, 86, 104)),
    "DIGS": ((134, 108, 84), (62, 48, 36)),
    "HIDES": ((150, 130, 96), (70, 58, 40)),
    "CHILLS": ((172, 206, 226), (78, 104, 126)),
    "DOUBLES": ((146, 132, 178), (66, 58, 88)),
    "BITES": ((120, 104, 88), (56, 46, 38)),
    "BURNS": ((216, 132, 60), (104, 50, 20)),
}
_unshaded = sorted({one["gift"] for one in COMPANIONS} - set(GIFT_SHADES))
_unworded = sorted({one["gift"] for one in COMPANIONS} - set(GIFT_WORDS))
if _unshaded or _unworded:
    print("no shades for %s and no sentence for %s" % (_unshaded, _unworded))
    sys.exit(2)


def companion_skin(one):
    """A companion, on the fauna's own 64x32 sheet.

    Two shades from what it does rather than from its name, so that two
    companions doing the same job would look alike -- which the rules forbid,
    and which this would make obvious if they ever stopped forbidding it.
    """
    light, dark = GIFT_SHADES[one["gift"]]
    solid_light = light + (255,)
    solid_dark = dark + (255,)
    image = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 63, 31], fill=solid_light)
    seed = sum(ord(letter) for letter in one["id"])
    for y in range(32):
        for x in range(64):
            if (x * 9 + y * 5 + seed) % 13 < 3:
                draw.point((x, y), fill=solid_dark)
    # A collar on the ones that have to be earned, so that which of them is
    # yours reads across a room.
    if one["tamed"]:
        draw.rectangle([0, 6, 23, 7], fill=(214, 168, 72, 255))
    draw.rectangle([3, 2, 5, 4], fill=(240, 240, 232, 255))
    draw.rectangle([9, 2, 11, 4], fill=(240, 240, 232, 255))
    return image


def myth_skin(myth):
    """A myth, on the same 64x64 sheet every creature in this module uses.

    Two shades from its bane rather than from its name, so that two creatures
    dangerous in the same way would look alike -- which the rules forbid, and
    which this would make obvious if they ever stopped forbidding it.
    """
    light, dark = BANE_SHADES[myth["bane"]]
    solid_light = light + (255,)
    solid_dark = dark + (255,)
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 63, 63], fill=solid_light)
    seed = sum(ord(letter) for letter in myth["id"])
    for y in range(64):
        for x in range(64):
            if (x * 13 + y * 7 + seed) % 11 < 3:
                draw.point((x, y), fill=solid_dark)
    # A band across the eyes, so which way it faces reads at a distance.
    draw.rectangle([0, 8, 31, 12], fill=solid_dark)
    draw.rectangle([4, 9, 7, 11], fill=(236, 236, 228, 255))
    draw.rectangle([12, 9, 15, 11], fill=(236, 236, 228, 255))
    return image


def summon_skin(name):
    """A shade, or the bolt it throws.

    Both are drawn rather than reused, because both are translucent and both
    have to read as "not a mob you can farm" at a glance. The shade is a wash
    of the wight family's colours with nothing solid in it; the bolt is a
    bright line on a dark core.
    """
    if name == "shade":
        image = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        draw = ImageDraw.Draw(image)
        for y in range(32):
            for x in range(64):
                # Vertical banding, fading out downwards: a shape that is
                # there at the top and gone at the bottom.
                mix = (x * 7 + y * 13) % 23
                fade = max(0, 200 - y * 5)
                if mix < 9:
                    draw.point((x, y), fill=(122, 148, 176, fade))
                elif mix < 15:
                    draw.point((x, y), fill=(74, 96, 126, fade))
                else:
                    draw.point((x, y), fill=(44, 58, 82, max(0, fade - 40)))
        # Two eyes, the one solid thing on it.
        for box in ((2, 2, 3, 3), (6, 2, 7, 3)):
            draw.rectangle(box, fill=(214, 232, 255, 255))
        return image
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 4], fill=(28, 24, 44, 255))
    draw.rectangle([0, 5, 15, 7], fill=(150, 196, 255, 255))
    draw.rectangle([0, 8, 15, 10], fill=(96, 132, 210, 255))
    draw.rectangle([0, 11, 15, 15], fill=(28, 24, 44, 255))
    return image


def threat(beast):
    return (beast["health"] * (1.0 + beast["damage"])
            * (1.0 + beast["armour"] / 10.0) * beast["speed"] / 0.25)


def earned():
    """
    The second copy of BeastRules.derive. validate.py proves it agrees.
    """
    order = sorted(BEASTS, key=threat)
    found = {}
    below = 0
    for beast in order:
        rate = BASE_RATE * (1.0 + TILT_PER_TIER * (beast["tier"] - 1))
        worth = max(max(round_half_up(threat(beast) * rate), below + 1), LEAST_WORTH)
        below = worth
        affordable = (worth - LEAST_EXPERIENCE) // WORTH_OF_A_DROP
        wanted = int(worth * DROP_SHARE / WORTH_OF_A_DROP)
        drops = max(LEAST_DROPS, min(affordable, wanted))
        found[beast["id"]] = (worth - drops * WORTH_OF_A_DROP, drops)
    return found


def round_half_up(value):
    """Java's Math.round, which is not Python's round."""
    import math
    return int(math.floor(value + 0.5))


REWARDS = earned()

PART_NAME = {}
for name, value in re.findall(
        r'public static final String (HEAD|BODY|ARM_LEFT|ARM_RIGHT|LEG_LEFT|LEG_RIGHT)'
        r' = "(\w+)";', SHARED_SOURCE):
    PART_NAME[name] = value
if len(PART_NAME) != 6:
    sys.exit("the six parts are not six any more")

SHEET = int(re.search(r"public static final int SHEET = (\d+);",
                      SHARED_SOURCE).group(1))

SLOTS = {}
for name, x, y, wide, tall in re.findall(
        r"(HEAD|BODY|ARM_LEFT|ARM_RIGHT|LEG_LEFT|LEG_RIGHT), new int\[\]\{"
        r"(\d+), (\d+), (\d+), (\d+)\}", SHARED_SOURCE):
    SLOTS[PART_NAME[name]] = (int(x), int(y), int(wide), int(tall))
if len(SLOTS) != 6:
    sys.exit("read %d slots off the sheet rather than six" % len(SLOTS))

PART_ROW = re.compile(
    r"new Part\((HEAD|BODY|ARM_LEFT|ARM_RIGHT|LEG_LEFT|LEG_RIGHT), "
    r"([-\d.]+)F, ([-\d.]+)F, ([-\d.]+)F, ([-\d.]+)F, ([-\d.]+)F, ([-\d.]+)F, "
    r"(\d+), (\d+), (\d+)\)")

SHAPES = {}
for block in re.finditer(
        r'made\.put\(BeastRules\.(PROWLER|BRUTE|WIGHT), List\.of\((.*?)\)\);',
        MODEL_SOURCE, re.S):
    parts = []
    for row in PART_ROW.finditer(block.group(2)):
        parts.append({
            "name": PART_NAME[row.group(1)],
            "pivot": (float(row.group(2)), float(row.group(3)), float(row.group(4))),
            "box": (float(row.group(5)), float(row.group(6)), float(row.group(7))),
            "size": (int(row.group(8)), int(row.group(9)), int(row.group(10))),
        })
    SHAPES[FAMILY[block.group(1)]] = parts
if len(SHAPES) != 3 or any(len(parts) != 6 for parts in SHAPES.values()):
    sys.exit("read %s shapes off the model rather than three of six parts each"
             % {name: len(parts) for name, parts in SHAPES.items()})

for family, parts in SHAPES.items():
    for part in parts:
        wide, tall, deep = part["size"]
        slot = SLOTS[part["name"]]
        if 2 * deep + 2 * wide > slot[2] or deep + tall > slot[3]:
            sys.exit("a %s's %s unwraps to %dx%d and its corner of the sheet is %dx%d"
                     % (family, part["name"], 2 * deep + 2 * wide, deep + tall,
                        slot[2], slot[3]))

# ---------------------------------------------------------------------------
# The palette
# ---------------------------------------------------------------------------
#
# One ramp per family, darkened or lit by tier, so that a player reads the
# family before the tier and the tier before the name. Four shades each: the
# deep one for shadow under a box, the two body shades, and the lit edge.

RAMPS = {
    "prowler": [(38, 26, 46), (66, 44, 78), (96, 68, 112), (140, 108, 158)],
    "brute": [(40, 30, 20), (74, 54, 34), (108, 80, 52), (150, 116, 76)],
    "wight": [(22, 34, 38), (42, 62, 70), (66, 92, 102), (104, 138, 150)],
}

EYES = {"prowler": (232, 176, 84), "brute": (226, 108, 64), "wight": (140, 226, 226)}

CLEAR = (0, 0, 0, 0)


def lit(colour, by):
    return tuple(min(255, max(0, int(channel + by))) for channel in colour) + (255,)


def shade(family, tier, step):
    """One of a family's four shades, pushed by how far up the ladder it is."""
    return lit(RAMPS[family][step], (tier - 2) * 14)


def grain(image, box, family, tier, seed):
    """
    A face: two shades scattered over a base, the same on every machine.

    A fixed hash rather than random, because the sheet has to come out
    byte-for-byte the same or the generated-file check fails for everybody
    except whoever last ran this.
    """
    x0, y0, wide, tall = box
    draw = ImageDraw.Draw(image)
    draw.rectangle([x0, y0, x0 + wide - 1, y0 + tall - 1], fill=shade(family, tier, 1))
    for y in range(tall):
        for x in range(wide):
            mix = (x * 41 + y * 97 + seed * 23) % 256
            if mix < 44:
                draw.point((x0 + x, y0 + y), fill=shade(family, tier, 0))
            elif mix < 92:
                draw.point((x0 + x, y0 + y), fill=shade(family, tier, 2))
    draw.line([(x0, y0), (x0 + wide - 1, y0)], fill=shade(family, tier, 3))


def unwrap(image, part, family, tier):
    """
    One box, laid out the way the game reads a cube: top and bottom along the
    top row, then right, front, left and back along the second.
    """
    wide, tall, deep = part["size"]
    x0, y0 = SLOTS[part["name"]][0], SLOTS[part["name"]][1]
    seed = sum(ord(letter) for letter in part["name"])
    faces = {
        "top": (x0 + deep, y0, wide, deep),
        "bottom": (x0 + deep + wide, y0, wide, deep),
        "right": (x0, y0 + deep, deep, tall),
        "front": (x0 + deep, y0 + deep, wide, tall),
        "left": (x0 + deep + wide, y0 + deep, deep, tall),
        "back": (x0 + deep + wide + deep, y0 + deep, wide, tall),
    }
    for index, (name, box) in enumerate(faces.items()):
        grain(image, box, family, tier, seed + index * 7)
    return faces


def face_of(image, faces, family, tier):
    """Eyes, on the front of the head and nowhere else."""
    x0, y0, wide, tall = faces["front"]
    draw = ImageDraw.Draw(image)
    colour = EYES[family] + (255,)
    left = x0 + max(1, wide // 4)
    right = x0 + wide - max(1, wide // 4) - 1
    row = y0 + max(1, tall // 3)
    for at in range(max(1, wide // 8) + 1):
        draw.point((left + at, row), fill=colour)
        draw.point((right - at, row), fill=colour)
    # A mouth on the brutes and a seam on the wights, so the three are three
    # things at a glance and not one thing in three colours.
    if family == "brute":
        draw.line([(x0 + 2, y0 + tall - 2), (x0 + wide - 3, y0 + tall - 2)],
                  fill=shade(family, tier, 0))
    if family == "wight":
        draw.line([(x0 + wide // 2, y0 + 1), (x0 + wide // 2, y0 + tall - 2)],
                  fill=lit(EYES[family], -70))


def skin(beast):
    image = Image.new("RGBA", (SHEET, SHEET), CLEAR)
    for part in SHAPES[beast["family"]]:
        faces = unwrap(image, part, beast["family"], beast["tier"])
        if part["name"] == PART_NAME["HEAD"]:
            face_of(image, faces, beast["family"], beast["tier"])
    return image


# ---------------------------------------------------------------------------
# What they leave behind
# ---------------------------------------------------------------------------
#
# A loot table whose expected haul is the number the bestiary says it is.
# Every pool is a uniform roll from nothing to some whole number, so a pool's
# expected yield is exactly half that number and three pools coming to twice
# the drop count come to the drop count. validate.py reads the written JSON
# back and works the expectation out again.

# What each family leaves, read out of BeastRules rather than decided here.
#
# These were three dicts in this file until the fauna arrived. A drop written
# in a generator is a drop no harness has ever seen, and the rule that says no
# animal you can keep in a pen leaves what something in the dark leaves cannot
# be written against a Python dict at all.
_LEAVINGS = re.search(r"Map<String, Leavings> LEFT_BY = Map\.of\((.*?)\);",
                      re.sub(r"\s+", " ", RULES_SOURCE))
if not _LEAVINGS:
    print("BeastRules no longer says what any family leaves")
    sys.exit(2)
STAPLE = {}
ESSENCE = {}
RARE = {}
for _family, _staple, _essence, _rare in re.findall(
        r'(\w+), new Leavings\("([a-z_:]+)", "([a-z_:]+)", List\.of\(([^)]*)\)\)',
        _LEAVINGS.group(1)):
    _key = _family.lower()
    STAPLE[_key] = _staple
    ESSENCE[_key] = _essence
    RARE[_key] = re.findall(r'"([a-z_:]+)"', _rare)
if sorted(STAPLE) != sorted(SHAPES):
    print("BeastRules names %s families of drops and %s families of beast"
          % (sorted(STAPLE), sorted(SHAPES)))
    sys.exit(2)


def pools(drops):
    """
    Split a drop count into three whole rolls that add back up to it.

    Twice the count, because a uniform from nothing to n averages n over two.
    The remainder always lands on the staple, so the sum is exact rather than
    nearly right.
    """
    twice = drops * 2
    if twice < 4:
        return [twice]
    rare = max(1, round(twice * 0.2))
    essence = max(1, round(twice * 0.3))
    staple = twice - rare - essence
    if staple < 1:
        return [twice]
    return [staple, essence, rare]


def pool(item, most, looting):
    functions = [{
        "function": "minecraft:set_count",
        "count": {"type": "minecraft:uniform", "min": 0.0, "max": float(most)},
    }]
    if looting:
        functions.append({
            "function": "minecraft:enchanted_count_increase",
            "count": {"type": "minecraft:uniform", "min": 0.0, "max": 1.0},
            "enchantment": "minecraft:looting",
        })
    return {
        "rolls": 1,
        "entries": [{"type": "minecraft:item", "name": item, "functions": functions}],
    }


def loot(beast):
    family = beast["family"]
    drops = REWARDS[beast["id"]][1]
    items = [STAPLE[family], ESSENCE[family],
             RARE[family][min(2, max(0, beast["tier"] - 1))]]
    made = []
    for index, most in enumerate(pools(drops)):
        made.append(pool(items[index], most, index == 0))
    return {"type": "minecraft:entity", "pools": made}


# ---------------------------------------------------------------------------
# Writing it all out
# ---------------------------------------------------------------------------

def titled(name):
    return " ".join(word.capitalize() for word in name.split("_"))


REASONS = {
    "shardling": "It comes apart when you hit it and the pieces keep coming.",
    "ripper": "Fast, and it has worked out that you are slower going uphill.",
    "stalker": "You will hear it stop following you. That is the bad part.",
    "husk_ox": "Too heavy to knock back and too slow to run from. Pick one.",
    "breaker": "It goes through the wall rather than round it, every time.",
    "siege_beast": "Three metres of it. Bring the door down and leave.",
    "mourner": "It is not chasing you. It is going the same way you are.",
    "keener": "The noise arrives first and the cold arrives with it.",
    "hollow_lord": "It waited. Whatever it was waiting for, it was not you.",
}



# Where each beast is, read out of HauntRules rather than decided here. A
# spawn worked out in a generator is a spawn no harness has ever seen.
HAUNT_SOURCE = open(os.path.join(
    MOD, "src/main/java/com/elysium/bestiary/rules/HauntRules.java"),
    encoding="utf-8").read()
_HAUNT = re.compile(
    r'new Haunt\("([a-z_]+)", "(#?[a-z_:]+)", (-?\w+), (-?\w+), (-?\w+), '
    r"(-?\w+), (\d+), (\d+)\)")
_WORDS = {"BEDROCK": -64, "SKY": 320, "PITCH_DARK": 0, "FULL_LIGHT": 15}


def _number(text):
    return _WORDS[text] if text in _WORDS else int(text)


_HAUNT_TABLE = re.search(r"List<Haunt> HAUNTS = List\.of\((.*?)\); ",
                         re.sub(r"\s+", " ", HAUNT_SOURCE))
if not _HAUNT_TABLE:
    print("HauntRules no longer has a table of haunts in it at all")
    sys.exit(2)
HAUNTS = [{"beast": one[0], "where": one[1], "darkest": _number(one[2]),
           "brightest": _number(one[3]), "lowest": _number(one[4]),
           "highest": _number(one[5]), "least": int(one[6]),
           "most": int(one[7])}
          for one in _HAUNT.findall(_HAUNT_TABLE.group(1))]
BY_ID = {beast["id"]: beast for beast in BEASTS}
if len(HAUNTS) != len(BEASTS):
    print("HauntRules has %d haunt(s) for %d beast(s)" % (len(HAUNTS), len(BEASTS)))
    sys.exit(2)


# ---------------------------------------------------------------------------
# The other half of the world
# ---------------------------------------------------------------------------
#
# Twelve animals, read out of FaunaRules and FaunaModel exactly as the nine
# beasts are read out of BeastRules and BeastModel. Nothing about an animal is
# decided here: not its wait, not its yield, not where it is. A generator that
# decides anything is a generator whose decisions no harness has ever seen.

_SQUASHED_FAUNA = re.sub(r"\s+", " ", FAUNA_RULES_SOURCE)
_KIND_NAME = dict(re.findall(
    r'public static final String (GRAZER|FORAGER|FOWL|SWIMMER) = "(\w+)";',
    _SQUASHED_FAUNA))
if len(_KIND_NAME) != 4:
    print("FaunaRules names %d kind(s) of animal rather than four" % len(_KIND_NAME))
    sys.exit(2)

_ANIMAL_TABLE = re.search(r"List<Animal> ANIMALS = List\.of\((.*?)\); ", _SQUASHED_FAUNA)
if not _ANIMAL_TABLE:
    print("FaunaRules no longer has a roster of animals in it at all")
    sys.exit(2)
ANIMALS = [{"id": one[0], "kind": _KIND_NAME[one[1]], "health": float(one[2]),
            "speed": float(one[3]), "width": float(one[4]), "height": float(one[5]),
            "eats": one[6], "gives": one[7], "yield": int(one[8]),
            "weight": int(one[9])}
           for one in re.findall(
               r'new Animal\("([a-z_]+)", (GRAZER|FORAGER|FOWL|SWIMMER), '
               r'([\d.]+)D, ([\d.]+)D, ([\d.]+)F, ([\d.]+)F, '
               r'"([a-z_:]+)", "([a-z_:]+)", (\d+), (\d+)\)',
               _ANIMAL_TABLE.group(1))]
if not ANIMALS:
    print("no animals could be read out of FaunaRules")
    sys.exit(2)

_FAUNA_HAUNT_TABLE = re.search(r"List<Haunt> THE_FAUNA = List\.of\((.*?)\); ",
                               re.sub(r"\s+", " ", HAUNT_SOURCE))
if not _FAUNA_HAUNT_TABLE:
    print("HauntRules no longer says where any animal is")
    sys.exit(2)
FAUNA_HAUNTS = [{"beast": one[0], "where": one[1], "darkest": _number(one[2]),
                 "brightest": _number(one[3]), "lowest": _number(one[4]),
                 "highest": _number(one[5]), "least": int(one[6]),
                 "most": int(one[7])}
                for one in _HAUNT.findall(_FAUNA_HAUNT_TABLE.group(1))]
BY_ANIMAL = {animal["id"]: animal for animal in ANIMALS}
if len(FAUNA_HAUNTS) != len(ANIMALS):
    print("HauntRules has %d haunt(s) for %d animal(s)"
          % (len(FAUNA_HAUNTS), len(ANIMALS)))
    sys.exit(2)

FAUNA_SHAPES = {}
for block in re.finditer(
        r'made\.put\(FaunaRules\.(GRAZER|FORAGER|FOWL|SWIMMER), List\.of\((.*?)\)\);',
        FAUNA_MODEL_SOURCE, re.S):
    parts = []
    for row in PART_ROW.finditer(block.group(2)):
        parts.append({
            "name": PART_NAME[row.group(1)],
            "pivot": (float(row.group(2)), float(row.group(3)), float(row.group(4))),
            "box": (float(row.group(5)), float(row.group(6)), float(row.group(7))),
            "size": (int(row.group(8)), int(row.group(9)), int(row.group(10))),
        })
    FAUNA_SHAPES[_KIND_NAME[block.group(1)]] = parts
if len(FAUNA_SHAPES) != 4 or any(len(parts) != 6 for parts in FAUNA_SHAPES.values()):
    print("read %s shapes off FaunaModel rather than four of six parts each"
          % {name: len(parts) for name, parts in FAUNA_SHAPES.items()})
    sys.exit(2)
for _kind, _parts in FAUNA_SHAPES.items():
    for _part in _parts:
        _wide, _tall, _deep = _part["size"]
        _slot = SLOTS[_part["name"]]
        if 2 * _deep + 2 * _wide > _slot[2] or _deep + _tall > _slot[3]:
            print("a %s's %s unwraps to %dx%d and its corner of the sheet is %dx%d"
                  % (_kind, _part["name"], 2 * _deep + 2 * _wide, _deep + _tall,
                     _slot[2], _slot[3]))
            sys.exit(2)

# One ramp per kind, so a field of animals reads as four groups before it
# reads as twelve names — the same claim the bestiary's palette makes about
# families, and the reason neither palette is per-creature.
FAUNA_RAMPS = {
    "grazer": [(46, 36, 22), (86, 68, 42), (124, 100, 64), (168, 142, 96)],
    "forager": [(32, 40, 24), (60, 74, 44), (92, 108, 68), (132, 150, 102)],
    "fowl": [(54, 48, 34), (98, 88, 62), (140, 128, 94), (186, 174, 134)],
    "swimmer": [(22, 42, 48), (40, 76, 86), (62, 110, 124), (96, 154, 170)],
}

FAUNA_EYES = {"grazer": (30, 22, 14), "forager": (24, 18, 12),
              "fowl": (36, 28, 18), "swimmer": (226, 226, 210)}


def fauna_shade(kind, step):
    return lit(FAUNA_RAMPS[kind][step], 0)


def fauna_grain(image, box, kind, seed):
    """The same fixed hash the bestiary uses, so the sheets are reproducible."""
    x0, y0, wide, tall = box
    draw = ImageDraw.Draw(image)
    draw.rectangle([x0, y0, x0 + wide - 1, y0 + tall - 1], fill=fauna_shade(kind, 1))
    for y in range(tall):
        for x in range(wide):
            mix = (x * 41 + y * 97 + seed * 23) % 256
            if mix < 44:
                draw.point((x0 + x, y0 + y), fill=fauna_shade(kind, 0))
            elif mix < 92:
                draw.point((x0 + x, y0 + y), fill=fauna_shade(kind, 2))
    draw.line([(x0, y0), (x0 + wide - 1, y0)], fill=fauna_shade(kind, 3))


def fauna_unwrap(image, part, kind):
    wide, tall, deep = part["size"]
    x0, y0 = SLOTS[part["name"]][0], SLOTS[part["name"]][1]
    seed = sum(ord(letter) for letter in part["name"])
    faces = {
        "top": (x0 + deep, y0, wide, deep),
        "bottom": (x0 + deep + wide, y0, wide, deep),
        "right": (x0, y0 + deep, deep, tall),
        "front": (x0 + deep, y0 + deep, wide, tall),
        "left": (x0 + deep + wide, y0 + deep, deep, tall),
        "back": (x0 + deep + wide + deep, y0 + deep, wide, tall),
    }
    for index, (name, box) in enumerate(faces.items()):
        fauna_grain(image, box, kind, seed + index * 7)
    return faces


def fauna_face(image, faces, kind):
    x0, y0, wide, tall = faces["front"]
    draw = ImageDraw.Draw(image)
    colour = FAUNA_EYES[kind] + (255,)
    left = x0 + max(1, wide // 4)
    right = x0 + wide - max(1, wide // 4) - 1
    row = y0 + max(1, tall // 3)
    draw.point((left, row), fill=colour)
    draw.point((right, row), fill=colour)
    if kind == "fowl":
        draw.line([(x0 + wide // 2, y0 + tall - 2), (x0 + wide // 2, y0 + tall - 1)],
                  fill=lit(FAUNA_RAMPS[kind][3], 40))
    if kind == "swimmer":
        draw.line([(x0, y0 + tall // 2), (x0 + wide - 1, y0 + tall // 2)],
                  fill=fauna_shade(kind, 3))


def fauna_skin(animal):
    image = Image.new("RGBA", (SHEET, SHEET), CLEAR)
    for part in FAUNA_SHAPES[animal["kind"]]:
        faces = fauna_unwrap(image, part, animal["kind"])
        if part["name"] == PART_NAME["HEAD"]:
            fauna_face(image, faces, animal["kind"])
    return image


def fauna_loot(animal):
    """
    One pool, and its expectation is exactly what the row says it leaves.

    A uniform roll from nothing to twice the yield averages the yield, which
    is the same arithmetic the bestiary's three pools use and the same reason:
    validate.py reads the written file back and works the expectation out
    again rather than believing this.
    """
    return {
        "type": "minecraft:entity",
        "pools": [pool(animal["gives"], animal["yield"] * 2, True)],
    }


FAUNA_REASONS = {
    "moorstag": "It watches you the whole way across the clearing and does not run.",
    "dunemare": "Nothing else out there is that colour on purpose.",
    "fellox": "It will move when it has finished, and not before.",
    "stonemole": "You hear the digging stop. It has heard you too.",
    "quillrat": "Picking one up is a decision you make exactly once.",
    "brackhog": "It got to the mushrooms first and it is not sorry.",
    "cragjay": "It has been following you for a mile, for no reason it will share.",
    "mirefowl": "Loud, and entirely unbothered about being loud.",
    "ashquail": "Three steps, stop, three steps. Never four.",
    "glasseel": "You see the shadow before you see the thing.",
    "silverfin": "A whole shoal turns at once, as if the water told them.",
    "shalecrab": "It has found somewhere to be and it is not moving.",
}


def titled_fauna(name):
    return " ".join(word.capitalize() for word in name.split("_"))


# What each beast does, read out of BehaviourRules. The words a player sees
# are here because they are words; which beast does which is not, because that
# is a rule and a rule written in a generator is a rule no harness has seen.
_VERB_NAME = dict(re.findall(
    r'public static final String (SPLIT|LEAP|STALK|ANCHOR|SUNDER|CHARGE|DRIFT'
    r'|CHILL|CALL) = "(\w+)";', BEHAVIOUR_SOURCE))
if len(_VERB_NAME) != 9:
    print("BehaviourRules names %d verb(s) rather than nine" % len(_VERB_NAME))
    sys.exit(2)
DOES = dict(re.findall(r'made\.put\("([a-z_]+)", (\w+)\);',
                       re.sub(r"\s+", " ", BEHAVIOUR_SOURCE)))
DOES = {beast: _VERB_NAME[verb] for beast, verb in DOES.items()
        if verb in _VERB_NAME}
if sorted(DOES) != sorted(beast["id"] for beast in BEASTS):
    print("BehaviourRules gives something to do to %s and the bestiary is %s"
          % (sorted(DOES), sorted(b["id"] for b in BEASTS)))
    sys.exit(2)

VERB_WORDS = {
    "split": "Comes apart when it dies, and the pieces keep coming.",
    "leap": "Jumps the gap you were counting on.",
    "stalk": "Stops dead when you look at it. Moves when you do not.",
    "anchor": "Will not be pushed, and pushes hard.",
    "sunder": "Goes through the wall rather than round it.",
    "charge": "Winds up, then crosses the room in one line.",
    "drift": "Walks to where you are going rather than where you are.",
    "chill": "What it touches gets slower and colder.",
    "call": "Calls up one more of its own family.",
}
if sorted(VERB_WORDS) != sorted(_VERB_NAME.values()):
    print("there are words for %s and verbs for %s"
          % (sorted(VERB_WORDS), sorted(_VERB_NAME.values())))
    sys.exit(2)


def main():
    entity_textures = os.path.join(ASSETS, "textures/entity")
    os.makedirs(entity_textures, exist_ok=True)

    lang = {
        "itemGroup.elysiumbestiary": "Elysium Bestiary",
    }

    for beast in BEASTS:
        skin(beast).save(os.path.join(entity_textures, beast["id"] + ".png"))

        write(os.path.join(ASSETS, "models/item", beast["id"] + "_spawn_egg.json"),
              {"parent": "minecraft:item/template_spawn_egg"})

        write(os.path.join(DATA, "loot_table/entities", beast["id"] + ".json"),
              loot(beast))

        lang["entity.%s.%s" % (NS, beast["id"])] = titled(beast["id"])
        lang["item.%s.%s_spawn_egg" % (NS, beast["id"])] = \
            titled(beast["id"]) + " Spawn Egg"
        lang["bestiary.%s.why.%s" % (NS, beast["id"])] = REASONS[beast["id"]]

    for family in sorted(SHAPES):
        lang["bestiary.%s.family.%s" % (NS, family)] = titled(family)

    for verb, words in sorted(VERB_WORDS.items()):
        lang["bestiary.%s.does.%s" % (NS, verb)] = words
    for beast_id, verb in sorted(DOES.items()):
        lang["bestiary.%s.mechanic.%s" % (NS, beast_id)] = titled(verb)

    # One modifier per beast rather than one per family.
    #
    # It used to be one per family, all of them in #minecraft:is_overworld,
    # with a group size worked out here as four minus the tier — three
    # decisions living in a generator, where no harness could reach them and
    # where "this mob is in the wrong place" had nowhere to be written down.
    # HauntRules holds them now, and this writes out what it says.
    for haunt in HAUNTS:
        write(os.path.join(DATA, "neoforge/biome_modifier",
                           "add_%s_spawns.json" % haunt["beast"]),
              {
                  "type": "neoforge:add_spawns",
                  "biomes": haunt["where"],
                  "spawners": [{
                      "type": "%s:%s" % (NS, haunt["beast"]),
                      "weight": BY_ID[haunt["beast"]]["weight"],
                      "minCount": haunt["least"],
                      "maxCount": haunt["most"],
                  }],
              })

    # The two the ability system puts in the world. Walked rather than listed,
    # because a summon with no skin is the black-and-magenta cube the renderer
    # falls back to, and both of these were registered and spawned for a while
    # with nothing drawing either.
    # The eight myths. Walked rather than listed, because a creature with no
    # skin renders as a black-and-magenta cube and is only ever found by the
    # player who meets that one.
    for myth in MYTHS:
        myth_skin(myth).save(os.path.join(entity_textures, myth["id"] + ".png"))

        write(os.path.join(ASSETS, "models/item", myth["id"] + "_spawn_egg.json"),
              {"parent": "minecraft:item/template_spawn_egg"})

        write(os.path.join(DATA, "loot_table/entities", myth["id"] + ".json"), {
            "type": "minecraft:entity",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item",
                             "name": MYTH_LEAVINGS[myth["id"]]}],
                "conditions": [{"condition": "minecraft:killed_by_player"}],
            }],
        })

        lang["entity.%s.%s" % (NS, myth["id"])] = titled(myth["id"])
        lang["item.%s.%s_spawn_egg" % (NS, myth["id"])] = \
            titled(myth["id"]) + " Spawn Egg"
        lang["bestiary.%s.bane.%s" % (NS, myth["id"])] = BANE_WORDS[myth["bane"]]
        lang["bestiary.%s.answer.%s" % (NS, myth["id"])] = myth["answer"]

        # And somewhere to appear. A myth registered with a spawn placement and
        # no biome modifier is a creature the game knows how to place and never
        # places -- which reads, from outside, exactly like a rare one.
        write(os.path.join(DATA, "neoforge/biome_modifier",
                           "add_%s_spawns.json" % myth["id"]),
              {
                  "type": "neoforge:add_spawns",
                  "biomes": "#minecraft:is_overworld",
                  "spawners": [{
                      "type": "%s:%s" % (NS, myth["id"]),
                      "weight": myth["weight"],
                      "minCount": 1,
                      "maxCount": 2 if myth["bane"] != "NUMBERS" else 4,
                  }],
              })

    # The eleven companions.
    for one in COMPANIONS:
        companion_skin(one).save(
            os.path.join(entity_textures, one["id"] + ".png"))

        write(os.path.join(ASSETS, "models/item", one["id"] + "_spawn_egg.json"),
              {"parent": "minecraft:item/template_spawn_egg"})

        write(os.path.join(DATA, "loot_table/entities", one["id"] + ".json"), {
            "type": "minecraft:entity",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": "minecraft:string"}],
                "conditions": [{"condition": "minecraft:killed_by_player"}],
            }],
        })

        write(os.path.join(DATA, "neoforge/biome_modifier",
                           "add_%s_spawns.json" % one["id"]),
              {
                  "type": "neoforge:add_spawns",
                  "biomes": "#minecraft:is_overworld",
                  "spawners": [{
                      "type": "%s:%s" % (NS, one["id"]),
                      "weight": one["weight"],
                      "minCount": 1,
                      "maxCount": 2,
                  }],
              })

        lang["entity.%s.%s" % (NS, one["id"])] = titled(one["id"])
        lang["item.%s.%s_spawn_egg" % (NS, one["id"])] = \
            titled(one["id"]) + " Spawn Egg"
        lang["bestiary.%s.gift.%s" % (NS, one["id"])] = GIFT_WORDS[one["gift"]]
        lang["bestiary.%s.rivals.%s" % (NS, one["id"])] = one["rivals"]

    for summon_id in SUMMON_IDS:
        summon_skin(summon_id).save(
            os.path.join(entity_textures, summon_id + ".png"))
        lang["entity.%s.%s" % (NS, summon_id)] = titled(summon_id)

    for animal in ANIMALS:
        fauna_skin(animal).save(os.path.join(entity_textures, animal["id"] + ".png"))

        write(os.path.join(ASSETS, "models/item", animal["id"] + "_spawn_egg.json"),
              {"parent": "minecraft:item/template_spawn_egg"})

        write(os.path.join(DATA, "loot_table/entities", animal["id"] + ".json"),
              fauna_loot(animal))

        lang["entity.%s.%s" % (NS, animal["id"])] = titled_fauna(animal["id"])
        lang["item.%s.%s_spawn_egg" % (NS, animal["id"])] = \
            titled_fauna(animal["id"]) + " Spawn Egg"
        lang["bestiary.%s.why.%s" % (NS, animal["id"])] = FAUNA_REASONS[animal["id"]]

    for kind in sorted(FAUNA_SHAPES):
        lang["bestiary.%s.kind.%s" % (NS, kind)] = titled_fauna(kind)
    lang["itemGroup.elysiumbestiary.fauna"] = "Elysium Fauna"

    write(os.path.join(ASSETS, "lang/en_us.json"), dict(sorted(lang.items())))

    for haunt in FAUNA_HAUNTS:
        write(os.path.join(DATA, "neoforge/biome_modifier",
                           "add_%s_spawns.json" % haunt["beast"]),
              {
                  "type": "neoforge:add_spawns",
                  "biomes": haunt["where"],
                  "spawners": [{
                      "type": "%s:%s" % (NS, haunt["beast"]),
                      "weight": BY_ANIMAL[haunt["beast"]]["weight"],
                      "minCount": haunt["least"],
                      "maxCount": haunt["most"],
                  }],
              })

    print("elysium-bestiary: %d beast(s) and %d animal(s), %d sheet(s), "
          "%d loot table(s), %d spawn rule(s)"
          % (len(BEASTS), len(ANIMALS), len(BEASTS) + len(ANIMALS),
             len(BEASTS) + len(ANIMALS), len(HAUNTS) + len(FAUNA_HAUNTS)))


if __name__ == "__main__":
    main()
