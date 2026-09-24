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
        r' = "(\w+)";', MODEL_SOURCE):
    PART_NAME[name] = value
if len(PART_NAME) != 6:
    sys.exit("the six parts are not six any more")

SHEET = int(re.search(r"public static final int SHEET = (\d+);", MODEL_SOURCE).group(1))

SLOTS = {}
for name, x, y, wide, tall in re.findall(
        r"(HEAD|BODY|ARM_LEFT|ARM_RIGHT|LEG_LEFT|LEG_RIGHT), new int\[\]\{"
        r"(\d+), (\d+), (\d+), (\d+)\}", MODEL_SOURCE):
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

STAPLE = {
    "prowler": "minecraft:string",
    "brute": "minecraft:bone",
    "wight": "minecraft:rotten_flesh",
}

ESSENCE = {
    "prowler": "minecraft:spider_eye",
    "brute": "minecraft:leather",
    "wight": "minecraft:glowstone_dust",
}

RARE = {
    "prowler": ["minecraft:iron_nugget", "minecraft:iron_ingot", "minecraft:diamond"],
    "brute": ["minecraft:flint", "minecraft:gold_ingot", "minecraft:emerald"],
    "wight": ["minecraft:coal", "minecraft:amethyst_shard", "minecraft:echo_shard"],
}


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

    write(os.path.join(ASSETS, "lang/en_us.json"), dict(sorted(lang.items())))

    # One modifier per family rather than one per beast, so a player meeting
    # the dark meets a family of things rather than nine unrelated strangers,
    # and the weights stay comparable inside the group the game rolls.
    for family in sorted(SHAPES):
        spawners = []
        for beast in BEASTS:
            if beast["family"] != family:
                continue
            most = 4 - min(3, beast["tier"])
            spawners.append({
                "type": "%s:%s" % (NS, beast["id"]),
                "weight": beast["weight"],
                "minCount": 1,
                "maxCount": max(1, most),
            })
        write(os.path.join(DATA, "neoforge/biome_modifier",
                           "add_%s_spawns.json" % family),
              {
                  "type": "neoforge:add_spawns",
                  "biomes": "#minecraft:is_overworld",
                  "spawners": spawners,
              })

    print("elysium-bestiary: %d beast(s), %d sheet(s), %d loot table(s), "
          "%d spawn rule(s)" % (len(BEASTS), len(BEASTS), len(BEASTS), len(SHAPES)))


if __name__ == "__main__":
    main()
