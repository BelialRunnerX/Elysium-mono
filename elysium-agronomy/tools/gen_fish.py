#!/usr/bin/env python3
"""
Every file a hundred and five fish need and none of them is Java.

    python3 tools/gen_fish.py

The roster is read out of FishRules.java. A fish added to the rules gets a
texture, a model, a bucket, an egg and a name here without anybody remembering
to come and do it, and a fish removed loses all five the same way.

The entity textures are drawn into exactly the rectangles FishModel bakes
against: the body at (0, 0), eight by four by three, and the tail at (0, 8),
four by four by one. Those numbers are here and there, and validate.py checks
the two still agree — a model and a texture that disagree render as garbage
rather than as an error.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumagronomy"

OUT = os.environ.get("ELYSIUM_FISH_OUT", MOD)
ASSETS = os.path.join(OUT, "src/main/resources/assets", NS)
DATA = os.path.join(OUT, "src/main/resources/data", NS)

RULES = os.path.join(MOD, "src/main/java/com/elysium/agronomy/rules/FishRules.java")

try:
    from PIL import Image, ImageDraw
except ImportError:
    print("Pillow is needed to draw the sprites: pip install pillow")
    sys.exit(1)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(obj, handle, indent=2)
        handle.write("\n")


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


SOURCE = read(RULES)

FAMILIES = {}
for name, const, water, band, when in re.findall(
        r'public static final Family ([A-Z]+) = new Family\("([a-z_]+)", '
        r'([A-Z]+), (\d+), ([A-Z]+)\);', SOURCE):
    FAMILIES[name] = {"name": const, "water": water.lower(), "band": int(band),
                      "when": when.lower()}

FISH = []
for fid, family, rarity, length, nutrition in re.findall(
        r'fish\("([a-z_]+)", ([A-Z]+), (\d+), ([\d.]+)F, (\d+)\)', SOURCE):
    if family not in FAMILIES:
        print("a fish names a family that is not declared:", fid, family)
        sys.exit(2)
    FISH.append({"id": fid, "family": FAMILIES[family], "rarity": int(rarity),
                 "length": float(length), "nutrition": int(nutrition)})

RODS = re.search(r'String\[\] names = \{([^}]*)\}', SOURCE)
ROD_NAMES = ["reed_rod", "oak_rod", "iron_rod", "steel_rod", "starsteel_rod"]
ITEMS_JAVA = read(os.path.join(
    MOD, "src/main/java/com/elysium/agronomy/registry/FishItems.java"))
found_rods = re.search(r'String\[\] names = \{([^}]*)\}', ITEMS_JAVA)
if found_rods:
    ROD_NAMES = re.findall(r'"([a-z_]+)"', found_rods.group(1))

if not FISH:
    print("no fish were read out of FishRules.java — the roster's shape has changed")
    sys.exit(2)

# ---------------------------------------------------------------------------
# The tackle, read out of its own rules
# ---------------------------------------------------------------------------

TACKLE_SOURCE = read(os.path.join(
    MOD, "src/main/java/com/elysium/agronomy/rules/TackleRules.java"))

HOOKS = [{"id": one[0], "luck": int(one[1]), "catches": int(one[2]),
          "holds": one[3] == "true"}
         for one in re.findall(
             r'new Hook\("([a-z_]+)", (\d+), (\d+), (true|false)\)',
             TACKLE_SOURCE)]
BOBBERS = [{"id": one[0], "deeper": int(one[1]), "anytime": one[2] == "true",
            "sooner": float(one[3])}
           for one in re.findall(
               r'new Bobber\("([a-z_]+)", (\d+), (true|false), ([\d.]+)F\)',
               TACKLE_SOURCE)]
WORM_FARM = re.search(r'WORM_FARM = "([a-z_]+)"', TACKLE_SOURCE)
TACKLE_BOX = re.search(r'TACKLE_BOX = "([a-z_]+)"', TACKLE_SOURCE)
if not HOOKS or not BOBBERS or not WORM_FARM or not TACKLE_BOX:
    print(f"TackleRules no longer says what goes on a rod; read {len(HOOKS)} "
          f"hook(s) and {len(BOBBERS)} bobber(s)")
    sys.exit(2)
WORM_FARM = WORM_FARM.group(1)
TACKLE_BOX = TACKLE_BOX.group(1)
TACKLE_BLOCKS = [WORM_FARM, TACKLE_BOX]
MOUNTS = [one["id"] + "_mount" for one in FISH]

# ---------------------------------------------------------------------------
# The rods: what each rung is built from
# ---------------------------------------------------------------------------

#: What each rung of rod is built from. The first is the game's own rod shape
#: in sugar cane; every rung above is the rung below with four of this around
#: it, so a rod is improved rather than replaced, the way the hive housings
#: are. Keyed by name: a rung the registry adds and this table does not is a
#: generator that stops, not a rod nobody can make.
ROD_MATERIALS = {
    "reed_rod": {"item": "minecraft:sugar_cane"},
    "oak_rod": {"tag": "minecraft:oak_logs"},
    "iron_rod": {"item": "minecraft:iron_ingot"},
    "steel_rod": {"tag": "c:ingots/steel"},
    "starsteel_rod": {"tag": "c:ingots/starsteel"},
}
if [name for name in ROD_NAMES if name not in ROD_MATERIALS]:
    print("a rung of rod has nothing to be built from: "
          f"{[name for name in ROD_NAMES if name not in ROD_MATERIALS]}")
    sys.exit(2)

# ---------------------------------------------------------------------------
# The fillet knife's cuts, read out of KitchenRules
# ---------------------------------------------------------------------------

KITCHEN_SOURCE = read(os.path.join(
    MOD, "src/main/java/com/elysium/agronomy/rules/KitchenRules.java"))
FILLET_KNIFE = re.search(r'FILLET_KNIFE = "([a-z_]+)";', KITCHEN_SOURCE)
FILLET = re.search(r'String FILLET = "([a-z_]+)";', KITCHEN_SOURCE)
MOST_A_CUT_FEEDS = re.search(r"MOST_A_CUT_FEEDS = (\d+);", KITCHEN_SOURCE)
FILLET_WORTH = re.search(r'new Ingredient\("' + (FILLET.group(1) if FILLET else "")
                         + r'", (\d+),', KITCHEN_SOURCE)
if not (FILLET_KNIFE and FILLET and MOST_A_CUT_FEEDS and FILLET_WORTH):
    print("KitchenRules no longer says what a fillet knife takes off a fish")
    sys.exit(2)
FILLET_KNIFE = FILLET_KNIFE.group(1)
FILLET = FILLET.group(1)
MOST_A_CUT_FEEDS = int(MOST_A_CUT_FEEDS.group(1))
FILLET_WORTH = int(FILLET_WORTH.group(1))


def fillets_from(nutrition):
    """KitchenRules.filletsFrom: as many fillets as keep the cut inside the rule."""
    return nutrition * MOST_A_CUT_FEEDS // FILLET_WORTH


# ---------------------------------------------------------------------------
# The water the fish swim in without a rod, read out of WaterRules
# ---------------------------------------------------------------------------

WATER_SOURCE = read(os.path.join(
    MOD, "src/main/java/com/elysium/agronomy/rules/WaterRules.java"))

HAUNTS = {}
for const, biomes in re.findall(r'haunt\(FishRules\.([A-Z]+), "([^"]+)"\)',
                                WATER_SOURCE):
    if const not in FAMILIES:
        print("WaterRules gives a haunt to a family FishRules does not declare:",
              const)
        sys.exit(2)
    HAUNTS[FAMILIES[const]["name"]] = biomes


def water_number(name):
    found = re.search(r"public static final (?:int|double) " + name
                      + r" = ([\d.]+)D?;", WATER_SOURCE)
    if not found:
        print("WaterRules no longer says", name)
        sys.exit(2)
    return float(found.group(1))


SPAWN_DIVISOR = water_number("SPAWN_WEIGHT_DIVISOR")
SCHOOL_LEAST = int(water_number("SCHOOL_LEAST"))
SCHOOL_MOST = int(water_number("SCHOOL_MOST"))

#: The void is the one family nothing swims in on its own: WaterRules.spawnsWild.
WILD_FAMILIES = [family for family in FAMILIES.values()
                 if family["water"] != "void"]
if not HAUNTS or set(HAUNTS) != {family["name"] for family in WILD_FAMILIES}:
    print("the families with a haunt are not the families that swim on their own: "
          f"{sorted(HAUNTS)} against "
          f"{sorted(family['name'] for family in WILD_FAMILIES)}")
    sys.exit(2)


def spawn_weight(rarity):
    """WaterRules.spawnWeight: the cast weight, divided down, never nothing."""
    weight = 64.0
    for _ in range(1, rarity):
        weight /= 2.0
    # Math.round, which rounds a half up; Python's round() would not.
    return max(1, int(weight / SPAWN_DIVISOR + 0.5))


def spawns():
    """
    One add-spawns biome modifier per family that swims on its own.

    <p>The biomes are the family's haunt and the spawners are every fish in
    it at the weight its rarity gives, so the rarer fish is the rarer sight.
    Where in those biomes a fish may actually appear &mdash; the liquid, the
    depth, the hour &mdash; is FishEntities' spawn placement, which asks the
    same question a cast does.
    """
    written = 0
    for family in WILD_FAMILIES:
        members = [one for one in FISH if one["family"]["name"] == family["name"]]
        write(os.path.join(DATA, "neoforge/biome_modifier",
                           "fish_spawn_" + family["name"] + ".json"), {
            "type": "neoforge:add_spawns",
            "biomes": HAUNTS[family["name"]],
            "spawners": [{"type": f"{NS}:{one['id']}",
                          "weight": spawn_weight(one["rarity"]),
                          "minCount": SCHOOL_LEAST,
                          "maxCount": SCHOOL_MOST} for one in members],
        })
        written += 1
    return written


def java_hash(text):
    value = 0
    for char in text:
        value = (value * 31 + ord(char)) & 0xFFFFFFFF
    if value >= 0x80000000:
        value -= 0x100000000
    return abs(value)


def shade(name, base, spread):
    value = java_hash(name)
    span = 2 * spread + 1
    red = min(255, max(0, ((base >> 16) & 0xFF) + (value % span) - spread))
    green = min(255, max(0, ((base >> 8) & 0xFF) + ((value // 7) % span) - spread))
    blue = min(255, max(0, (base & 0xFF) + ((value // 53) % span) - spread))
    return red, green, blue


WATER_HUE = {"fresh": 0x6E9A72, "salt": 0x3E7C9A, "lava": 0xB4552A, "void": 0x6A4E8C}


def skin(one):
    return shade(one["id"], WATER_HUE[one["family"]["water"]], 34)


def belly(one):
    return tuple(min(255, channel + 52) for channel in skin(one))


def darker(colour, by=44):
    return tuple(max(0, channel - by) for channel in colour)


# ---------------------------------------------------------------------------
# Sprites
# ---------------------------------------------------------------------------

def entity_sprite(one):
    """Unwrapped the way FishModel asks for it."""
    body = skin(one)
    light = belly(one)
    edge = darker(body, 60)
    image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    # Body: width 8, height 4, depth 3, at (0, 0).
    w, h, d = 8, 4, 3
    draw.rectangle([d, 0, d + w - 1, d - 1], fill=darker(body, 20) + (255,))
    draw.rectangle([d + w, 0, d + 2 * w - 1, d - 1], fill=light + (255,))
    draw.rectangle([0, d, d - 1, d + h - 1], fill=body + (255,))
    draw.rectangle([d, d, d + w - 1, d + h - 1], fill=body + (255,))
    draw.rectangle([d + w, d, d + w + d - 1, d + h - 1], fill=body + (255,))
    draw.rectangle([d + w + d, d, d + w + d + w - 1, d + h - 1], fill=body + (255,))
    draw.line([(d, d + h - 1), (d + w - 1, d + h - 1)], fill=light + (255,))
    draw.line([(d + w + d, d + h - 1), (d + w + d + w - 1, d + h - 1)],
              fill=light + (255,))
    draw.point((d + 1, d + 1), fill=(16, 16, 20, 255))
    draw.point((d + w + d + w - 2, d + 1), fill=(16, 16, 20, 255))

    # Tail: width 4, height 4, depth 1, at (0, 8).
    tw, th, td = 4, 4, 1
    top = 8
    draw.rectangle([td, top, td + tw - 1, top + td - 1], fill=edge + (255,))
    draw.rectangle([td + tw, top, td + 2 * tw - 1, top + td - 1], fill=edge + (255,))
    draw.rectangle([0, top + td, 2 * (tw + td) - 1, top + td + th - 1],
                   fill=edge + (255,))
    return image


def item_sprite(one):
    """A fish, side on, in the water it comes out of."""
    body = skin(one)
    light = belly(one)
    edge = darker(body, 70)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.ellipse([3, 5, 12, 11], fill=body + (255,), outline=edge + (255,))
    draw.line([(4, 9), (11, 9)], fill=light + (255,))
    draw.polygon([(12, 8), (15, 5), (15, 11)], fill=edge + (255,))
    draw.polygon([(6, 5), (9, 3), (9, 5)], fill=edge + (255,))
    draw.point((5, 7), fill=(12, 12, 16, 255))
    return image


def bucket_sprite(one):
    """The game's bucket shape with the fish's own colour in it."""
    body = skin(one)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    steel = (168, 172, 178)
    draw.polygon([(3, 5), (12, 5), (11, 14), (4, 14)], fill=steel + (255,))
    draw.rectangle([3, 4, 12, 5], fill=(198, 202, 208, 255))
    draw.rectangle([5, 7, 10, 12], fill=body + (255,))
    draw.line([(5, 10), (10, 10)], fill=belly(one) + (255,))
    draw.point((6, 9), fill=(12, 12, 16, 255))
    return image


def rod_sprite(rung, name):
    shaft = shade(name, 0x9A7B4F, 30)
    line = (226, 230, 236)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.line([(2, 14), (12, 3)], fill=shaft + (255,), width=2)
    draw.line([(12, 3), (14, 9)], fill=line + (200,))
    for pip in range(rung + 1):
        draw.point((4 + pip, 12 - pip), fill=(240, 226, 150, 255))
    return image


def bait_sprite(family):
    core = shade(family, 0x8A6A4A, 34)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.ellipse([4, 6, 11, 12], fill=core + (255,), outline=darker(core) + (255,))
    draw.line([(7, 4), (7, 6)], fill=darker(core) + (255,))
    draw.line([(9, 4), (9, 6)], fill=darker(core) + (255,))
    return image


def tool_sprite(name):
    if name == "fillet_knife":
        blade, grip = (206, 210, 216), (96, 72, 48)
    else:
        blade, grip = (232, 226, 200), (120, 120, 128)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    if name == "fillet_knife":
        draw.polygon([(3, 12), (11, 4), (13, 6), (5, 14)], fill=blade + (255,))
        draw.line([(2, 13), (4, 15)], fill=grip + (255,), width=2)
    else:
        draw.rectangle([3, 7, 12, 9], fill=blade + (255,), outline=grip + (255,))
        for mark in range(4, 12, 2):
            draw.point((mark, 8), fill=grip + (255,))
    return image


def hook_sprite(hook):
    """
    A hook, drawn from what it does.

    <p>Warmer the more rod it is worth, barbed if it holds, forked if it
    brings up two. A player fitting one wants to know which it is without
    reading a tooltip.
    """
    from PIL import Image, ImageDraw
    warm = min(3, hook["luck"])
    metal = (150 + warm * 26, 146 + warm * 14, 140 - warm * 30)
    edge = darker(metal, 60)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.line([(8, 2), (8, 8)], fill=metal + (255,))
    draw.arc([4, 6, 12, 13], start=0, end=180, fill=metal + (255,))
    draw.point((4, 9), fill=edge + (255,))
    if hook["holds"]:
        draw.line([(10, 9), (12, 7)], fill=edge + (255,))
    if hook["catches"] > 1:
        draw.arc([2, 6, 10, 13], start=0, end=180, fill=metal + (255,))
    return image


def bobber_sprite(bobber):
    """A float: red over white, lit if it works at night, weighted if it sinks."""
    from PIL import Image, ImageDraw
    top = (206, 68, 62) if not bobber["anytime"] else (238, 206, 110)
    bottom = (226, 226, 222) if not bobber["deeper"] else (96, 96, 104)
    edge = darker(top, 60)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.ellipse([3, 3, 12, 12], fill=bottom + (255,), outline=edge + (255,))
    draw.pieslice([3, 3, 12, 12], start=180, end=360, fill=top + (255,),
                  outline=edge + (255,))
    draw.line([(8, 0), (8, 3)], fill=edge + (255,))
    if bobber["sooner"] > 0.0:
        draw.point((6, 6), fill=(255, 255, 255, 255))
        draw.point((10, 6), fill=(255, 255, 255, 255))
    return image


def mount_sprite(one):
    """A fish on a board, in the fish's own colours."""
    from PIL import Image, ImageDraw
    board = (128, 98, 62)
    grain = darker(board, 34)
    image = Image.new("RGBA", (16, 16), board + (255,))
    draw = ImageDraw.Draw(image)
    for row in range(0, 16, 4):
        draw.line([(0, row), (15, row)], fill=grain + (255,))
    body = skin(one)
    draw.ellipse([3, 6, 11, 10], fill=body + (255,), outline=darker(body) + (255,))
    draw.polygon([(11, 8), (14, 5), (14, 11)], fill=body + (255,))
    draw.point((5, 8), fill=(20, 20, 24, 255))
    return image


def station_sprite(name):
    """The worm farm and the tackle box, told apart by what is on the lid."""
    from PIL import Image, ImageDraw
    base = (118, 92, 58) if name == WORM_FARM else (88, 96, 110)
    edge = darker(base, 44)
    lid = lighter_colour(base, 40)
    image = Image.new("RGBA", (16, 16), base + (255,))
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], outline=edge + (255,))
    draw.rectangle([2, 2, 13, 7], fill=lid + (255,), outline=edge + (255,))
    if name == WORM_FARM:
        draw.arc([4, 9, 11, 14], start=200, end=340, fill=(196, 130, 130, 255))
    else:
        draw.line([(4, 11), (11, 11)], fill=edge + (255,))
        draw.rectangle([7, 9, 8, 13], fill=edge + (255,))
    return image


def lighter_colour(colour, by=34):
    return tuple(min(255, part + by) for part in colour)


def _art_path():
    """The shared art package at the repository root, wherever this script sits."""
    base = HERE
    for _ in range(6):
        if os.path.isfile(os.path.join(base, "art", "bodyplans.py")):
            return os.path.join(base, "art")
        base = os.path.dirname(base)
    sys.exit("cannot find the art package from " + HERE)


sys.path.insert(0, _art_path())
import bodyplans  # noqa: E402
import creature  # noqa: E402
from boxmodel import write_texture  # noqa: E402


# The fish's body plan (docs/POLISH.md P10): rings along a spine, fins, a tail
# that wags; emitted as FishModel with every species' sheet from the same rows.
FISH_PLAN = dict(seed=15, body_len=16, body_h=7, body_w=5, dorsal=4)
MODEL_JAVA = os.path.join(OUT, "src/main/java/com/elysium/agronomy/client/FishModel.java")


def fish_plan():
    return bodyplans.BUILDERS["fish"](FISH_PLAN)


def write_fish_model():
    plan = fish_plan()
    skin = creature.skin_for(plan.rows, "#3e6e7c")
    java, sheet = creature.model_java("com.elysium.agronomy.client", "FishModel", plan, skin, "tools/gen_fish.py",
                                      doc="Every species wears it, scaled to its length; the renderer picks the sheet.")
    os.makedirs(os.path.dirname(MODEL_JAVA), exist_ok=True)
    with open(MODEL_JAVA, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(java)
    return sheet


def write_fish_skin(one, path):
    """One species' sheet: the plan's rows in the species' water colour, its belly the accent."""
    plan = fish_plan()
    base = "#%02x%02x%02x" % skin(one)
    made = creature.skin_for(plan.rows, base)
    import pathlib
    sheet = creature.model_java("com.elysium.agronomy.client", "FishModel", plan, made, "tools/gen_fish.py")[1]
    write_texture(pathlib.Path(path), plan.rows, base, "#%02x%02x%02x" % belly(one), sheet=sheet, skin=made)


def save(image, *parts):
    path = os.path.join(*parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)


def draw_all():
    write_fish_model()
    for one in FISH:
        write_fish_skin(one, os.path.join(ASSETS, "textures/entity/fish", one["id"] + ".png"))
        save(item_sprite(one), ASSETS, "textures/item", one["id"] + ".png")
        save(bucket_sprite(one), ASSETS, "textures/item", one["id"] + "_bucket.png")
    for rung, name in enumerate(ROD_NAMES):
        save(rod_sprite(rung, name), ASSETS, "textures/item", name + ".png")
    for key, family in FAMILIES.items():
        save(bait_sprite(family["name"]), ASSETS, "textures/item",
             family["name"] + "_bait.png")
    for tool in ("fillet_knife", "measuring_line"):
        save(tool_sprite(tool), ASSETS, "textures/item", tool + ".png")
    for hook in HOOKS:
        save(hook_sprite(hook), ASSETS, "textures/item", hook["id"] + ".png")
    for bobber in BOBBERS:
        save(bobber_sprite(bobber), ASSETS, "textures/item", bobber["id"] + ".png")
    for one in FISH:
        save(mount_sprite(one), ASSETS, "textures/block",
             one["id"] + "_mount.png")
    for name in TACKLE_BLOCKS:
        save(station_sprite(name), ASSETS, "textures/block", name + ".png")


def flat(texture):
    return {"parent": "minecraft:item/generated",
            "textures": {"layer0": f"{NS}:item/{texture}"}}


def models():
    for one in FISH:
        write(os.path.join(ASSETS, "models/item", one["id"] + ".json"),
              flat(one["id"]))
        write(os.path.join(ASSETS, "models/item", one["id"] + "_bucket.json"),
              flat(one["id"] + "_bucket"))
        write(os.path.join(ASSETS, "models/item", one["id"] + "_spawn_egg.json"),
              {"parent": "minecraft:item/template_spawn_egg"})
    for name in ROD_NAMES:
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": "minecraft:item/handheld",
               "textures": {"layer0": f"{NS}:item/{name}"}})
    for family in FAMILIES.values():
        write(os.path.join(ASSETS, "models/item", family["name"] + "_bait.json"),
              flat(family["name"] + "_bait"))
    write(os.path.join(ASSETS, "models/item", "fillet_knife.json"),
          {"parent": "minecraft:item/handheld",
           "textures": {"layer0": f"{NS}:item/fillet_knife"}})
    write(os.path.join(ASSETS, "models/item", "measuring_line.json"),
          flat("measuring_line"))
    for piece in HOOKS + BOBBERS:
        write(os.path.join(ASSETS, "models/item", piece["id"] + ".json"),
              flat(piece["id"]))
    for name in MOUNTS + TACKLE_BLOCKS:
        write(os.path.join(ASSETS, "models/block", name + ".json"),
              {"parent": "minecraft:block/cube_all",
               "textures": {"all": f"{NS}:block/{name}"}})
        write(os.path.join(ASSETS, "blockstates", name + ".json"),
              {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": f"{NS}:block/{name}"})


def tackle_data():
    """
    Loot and recipes for everything around the water.

    <p>A mount is made of the fish and a plank: it is a trophy, so the fish
    goes into it and does not come back, and the recipe says so rather than a
    tooltip saying so.
    """
    data = os.path.join(OUT, "src/main/resources/data", NS)
    written = 0
    for name in MOUNTS + TACKLE_BLOCKS:
        write(os.path.join(data, "loot_table/blocks", name + ".json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })
        written += 1
    for one in FISH:
        write(os.path.join(data, "recipe", one["id"] + "_mount.json"), {
            "type": "minecraft:crafting_shaped",
            "category": "building",
            "pattern": ["PPP", "PFP", "PPP"],
            "key": {"P": {"tag": "minecraft:planks"},
                    "F": {"item": f"{NS}:{one['id']}"}},
            "result": {"id": f"{NS}:{one['id']}_mount", "count": 1},
        })
        written += 1
    write(os.path.join(data, "recipe", WORM_FARM + ".json"), {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["PPP", "PDP", "PPP"],
        "key": {"P": {"tag": "minecraft:planks"},
                "D": {"item": "minecraft:dirt"}},
        "result": {"id": f"{NS}:{WORM_FARM}", "count": 1},
    })
    write(os.path.join(data, "recipe", TACKLE_BOX + ".json"), {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["III", "PCP", "PPP"],
        "key": {"P": {"tag": "minecraft:planks"},
                "I": {"item": "minecraft:iron_nugget"},
                "C": {"item": "minecraft:chest"}},
        "result": {"id": f"{NS}:{TACKLE_BOX}", "count": 1},
    })
    written += 2
    for hook in HOOKS:
        metal = "minecraft:iron_nugget" if hook["luck"] < 2 \
            else "minecraft:gold_ingot"
        write(os.path.join(data, "recipe", hook["id"] + ".json"), {
            "type": "minecraft:crafting_shaped",
            "category": "equipment",
            "pattern": [" M ", "M M", " M "],
            "key": {"M": {"item": metal}},
            "result": {"id": f"{NS}:{hook['id']}", "count": 1},
        })
        written += 1
    for bobber in BOBBERS:
        heart = "minecraft:glowstone_dust" if bobber["anytime"] \
            else ("minecraft:lead" if bobber["deeper"] else "minecraft:string")
        write(os.path.join(data, "recipe", bobber["id"] + ".json"), {
            "type": "minecraft:crafting_shaped",
            "category": "equipment",
            "pattern": [" W ", "WHW", " W "],
            "key": {"W": {"item": "minecraft:white_wool"},
                    "H": {"item": heart}},
            "result": {"id": f"{NS}:{bobber['id']}", "count": 1},
        })
        written += 1

    # The rods. The game's own rod fishes water as the bottom rung, but only
    # ours casts a float that floats on lava and in the End's void, so a rod
    # nobody can make is two families nobody can catch.
    for rung, name in enumerate(ROD_NAMES):
        if rung == 0:
            recipe = {
                "type": "minecraft:crafting_shaped",
                "category": "equipment",
                "pattern": ["  C", " CS", "C S"],
                "key": {"C": ROD_MATERIALS[name], "S": {"item": "minecraft:string"}},
                "result": {"id": f"{NS}:{name}", "count": 1},
            }
        else:
            recipe = {
                "type": "minecraft:crafting_shaped",
                "category": "equipment",
                "pattern": [" M ", "MRM", " M "],
                "key": {"M": ROD_MATERIALS[name],
                        "R": {"item": f"{NS}:{ROD_NAMES[rung - 1]}"}},
                "result": {"id": f"{NS}:{name}", "count": 1},
            }
        write(os.path.join(data, "recipe", name + ".json"), recipe)
        written += 1

    # The fillet knife: a thin blade, so it is not an iron knife's ingot on
    # a stick. And the measuring line: string marked off in black.
    write(os.path.join(data, "recipe", "fillet_knife.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "pattern": ["  N", " N ", "S  "],
        "key": {"N": {"item": "minecraft:iron_nugget"}, "S": {"item": "minecraft:stick"}},
        "result": {"id": f"{NS}:fillet_knife", "count": 1},
    })
    write(os.path.join(data, "recipe", "measuring_line.json"), {
        "type": "minecraft:crafting_shapeless",
        "category": "equipment",
        "ingredients": [{"item": "minecraft:string"}, {"item": "minecraft:string"},
                        {"item": "minecraft:stick"}, {"item": "minecraft:black_dye"}],
        "result": {"id": f"{NS}:measuring_line", "count": 1},
    })
    written += 2

    # What the fillet knife cuts: every fish big enough to fillet, into as
    # many fillets as KitchenRules.filletsFrom gives. The cutting board reads
    # these beside the kitchen's own cuts, and asks for a cut by the knife's
    # name, so a kitchen knife never takes a fillet off one of these.
    for one in FISH:
        fillets = fillets_from(one["nutrition"])
        if fillets <= 0:
            continue
        write(os.path.join(data, "cutting", one["id"] + ".json"), {
            "input": f"{NS}:{one['id']}",
            "tool": FILLET_KNIFE,
            "yields": [{"item": f"{NS}:{FILLET}", "count": fillets}],
        })
        written += 1

    # A fish of a family that never swims on its own is never alive to be
    # scooped, so a caught one goes into a bucket at the table
    # (WaterRules.bucketedAtTable), and pouring it out is how an end fish
    # comes to be swimming. An empty bucket: a water bucket in a crafting grid
    # hands its bucket back, and the fish's bucket hands another back when it
    # is poured, which is two buckets for one.
    wild = {family["name"] for family in WILD_FAMILIES}
    for one in FISH:
        if one["family"]["name"] in wild:
            continue
        write(os.path.join(data, "recipe", one["id"] + "_bucket.json"), {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "ingredients": [{"item": f"{NS}:{one['id']}"}, {"item": "minecraft:bucket"}],
            "result": {"id": f"{NS}:{one['id']}_bucket", "count": 1},
        })
        written += 1
    return written


#: What a rod's tooltip and the worm farm say.
SAYINGS = {
    f"{NS}.rod.rung": "Rung %s of %s",
    f"{NS}.rod.hook": "Hook: %s",
    f"{NS}.rod.bobber": "Bobber: %s",
    f"{NS}.rod.bait": "Bait: %s x%s",
    f"{NS}.rod.bare": "Nothing fitted: sneak-use with a hook, bobber or bait in the "
                      "other hand",
    f"{NS}.worm_farm.fed": "The farm will make %s",
    f"{NS}.worm_farm.hungry": "Feed it a fish, and it makes that fish's bait",
    f"{NS}.worm_farm.working": "Making %s: %s of %s",
    f"{NS}.line.measured": "%s: %s cm long",
    f"{NS}.line.nothing": "Nothing to measure: hold a fish in the other hand, or use the "
                          "line on one in the water",
}


def a_name(text):
    return " ".join(word.capitalize() for word in text.split("_"))


def lang():
    words = {}
    for one in FISH:
        pretty = a_name(one["id"])
        words[f"entity.{NS}.{one['id']}"] = pretty
        words[f"item.{NS}.{one['id']}"] = pretty
        words[f"item.{NS}.{one['id']}_bucket"] = "Bucket of " + pretty
        words[f"item.{NS}.{one['id']}_spawn_egg"] = pretty + " Spawn Egg"
    for name in ROD_NAMES:
        words[f"item.{NS}.{name}"] = a_name(name)
    for family in FAMILIES.values():
        words[f"item.{NS}.{family['name']}_bait"] = a_name(family["name"]) + " Bait"
    words[f"item.{NS}.fillet_knife"] = "Fillet Knife"
    words[f"item.{NS}.measuring_line"] = "Measuring Line"
    for piece in HOOKS + BOBBERS:
        words[f"item.{NS}.{piece['id']}"] = a_name(piece["id"])
    for one in FISH:
        words[f"block.{NS}.{one['id']}_mount"] = a_name(one["id"]) + " Mount"
    for name in TACKLE_BLOCKS:
        words[f"block.{NS}.{name}"] = a_name(name)
    words.update(SAYINGS)

    # The base is the language file as it stands in the output tree if one is
    # there, and the committed one otherwise.
    #
    # Six generators write this file and each keeps the keys it does not own,
    # so they have to compose: the first starts from what is committed and
    # every one after it starts from what the one before wrote. Starting every
    # one of them from the committed file instead means the last to run puts
    # back whatever is in it — including a value somebody edited by hand,
    # which then matches itself and nothing reports it. That hid a hand-edited
    # line for as long as this file has had six generators writing it.
    committed = os.path.join(MOD, "src/main/resources/assets", NS,
                             "lang/en_us.json")
    standing = os.path.join(ASSETS, "lang/en_us.json")
    if os.path.exists(standing):
        committed = standing
    path = os.path.join(ASSETS, "lang/en_us.json")
    existing = json.loads(read(committed)) if os.path.exists(committed) else {}
    ours = {f"entity.{NS}.{one['id']}" for one in FISH}
    ours |= {f"item.{NS}.{one['id']}" for one in FISH}
    ours |= {f"item.{NS}.{one['id']}_bucket" for one in FISH}
    ours |= {f"item.{NS}.{one['id']}_spawn_egg" for one in FISH}
    ours |= {f"item.{NS}.{name}" for name in ROD_NAMES}
    ours |= {f"item.{NS}.{f['name']}_bait" for f in FAMILIES.values()}
    ours |= {f"item.{NS}.fillet_knife", f"item.{NS}.measuring_line"}
    ours |= {f"item.{NS}.{piece['id']}" for piece in HOOKS + BOBBERS}
    ours |= {f"block.{NS}.{one['id']}_mount" for one in FISH}
    ours |= {f"block.{NS}.{name}" for name in TACKLE_BLOCKS}
    ours |= set(SAYINGS)
    for key in ours:
        existing.pop(key, None)
    existing.update(words)
    write(path, dict(sorted(existing.items())))


def main():
    draw_all()
    models()
    files = tackle_data()
    files += spawns()
    lang()
    print(f"fish: {len(FISH)} species across {len(FAMILIES)} families, "
          f"{len(ROD_NAMES)} rod(s), {len(FAMILIES)} bait(s), "
          f"{len(FISH) * 3 + len(ROD_NAMES) + len(FAMILIES) + 2} texture(s); "
          f"the tackle is {len(HOOKS)} hook(s), {len(BOBBERS)} bobber(s), "
          f"{len(MOUNTS)} mount(s) and {len(TACKLE_BLOCKS)} station(s) in "
          f"{files} file(s)")


if __name__ == "__main__":
    main()
