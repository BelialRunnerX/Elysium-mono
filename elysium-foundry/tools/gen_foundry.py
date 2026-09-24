#!/usr/bin/env python3
"""
Every file the material chain needs that is not Java.

    python3 tools/gen_foundry.py

Twenty-two metals in up to seven shapes each, with a tag, a model, a texture, a
name and at least two recipes apiece. Nobody keeps that straight by hand; the
chain is one table here and in FoundryRules, the validator checks the two agree,
and it regenerates the whole set into a temporary tree and compares byte for
byte.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumfoundry"
IND = "elysiumindustry"

OUT = os.environ.get("ELYSIUM_FOUNDRY_OUT", MOD)
ASSETS = os.path.join(OUT, "src/main/resources/assets", NS)
DATA = os.path.join(OUT, "src/main/resources/data", NS)

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


# ---------------------------------------------------------------------------
# The chain, mirrored from FoundryRules
# ---------------------------------------------------------------------------

UNIT = 9

#: name -> (tier, {part: units}, base colour)
#: The tiers and the recipes must match FoundryRules exactly; the validator
#: compares the two and fails the build if they have drifted, because a metal
#: that costs one thing in the Java and another in the data is a metal whose
#: price depends on which file you read.
MATERIALS = {
    "iron": (0, {}, (198, 198, 198)),
    "copper": (0, {}, (192, 112, 68)),
    "gold": (0, {}, (232, 190, 70)),
    "tin": (0, {}, (198, 208, 216)),
    "zinc": (0, {}, (176, 186, 176)),
    "nickel": (0, {}, (206, 202, 158)),
    "silver": (0, {}, (214, 224, 232)),
    "lead": (0, {}, (110, 106, 136)),
    "aluminum": (0, {}, (208, 216, 220)),
    "osmium": (0, {}, (156, 178, 210)),
    "platinum": (0, {}, (182, 214, 222)),
    "lithium": (0, {}, (226, 222, 238)),
    "crimson_iron": (0, {}, (176, 74, 70)),
    "azure_silver": (0, {}, (132, 168, 214)),
    "uranium": (0, {}, (108, 150, 74)),
    "coal_dust": (0, {}, (48, 48, 52)),
    "redstone_dust": (0, {}, (186, 42, 42)),
    "bort": (0, {}, (72, 78, 86)),
    "certus_quartz": (0, {}, (196, 216, 232)),
    "fluorite": (0, {}, (150, 220, 190)),
    "rime_shard": (0, {}, (178, 226, 244)),
    "uraninite": (0, {}, (96, 126, 70)),
    "bronze": (1, {"copper": 3 * UNIT, "tin": UNIT}, (196, 132, 68)),
    "brass": (1, {"copper": 3 * UNIT, "zinc": UNIT}, (214, 178, 84)),
    "steel": (1, {"iron": UNIT, "coal_dust": UNIT}, (124, 128, 134)),
    "cast_iron": (1, {"iron": 4 * UNIT, "coal_dust": UNIT}, (148, 130, 118)),
    "electrum": (1, {"gold": UNIT, "silver": UNIT}, (236, 218, 132)),
    "invar": (1, {"iron": 2 * UNIT, "nickel": UNIT}, (178, 178, 164)),
    "constantan": (1, {"copper": UNIT, "nickel": UNIT}, (206, 156, 110)),
    "stainless": (2, {"steel": 3 * UNIT, "nickel": UNIT}, (196, 202, 208)),
    "signal_alloy": (2, {"electrum": UNIT, "redstone_dust": 2 * UNIT}, (214, 118, 104)),
    "hardened_bronze": (2, {"bronze": 2 * UNIT, "invar": UNIT}, (168, 118, 74)),
    "void_steel": (3, {"stainless": UNIT, "uranium": UNIT}, (84, 88, 112)),
    "starsteel": (3, {"stainless": 2 * UNIT, "platinum": UNIT}, (176, 206, 226)),
}

#: Materials another module already supplies, which this one must not supply a
#: second copy of. Mirrored from FoundryItems.SOMEBODY_ELSE_SUPPLIES.
SOMEBODY_ELSE = {"iron", "copper", "gold", "steel", "uranium", "redstone_dust"}

#: Materials that exist in exactly one shape, named after themselves. Coal dust
#: is a dust and nothing else; calling its dust form "coal_dust_dust" would be
#: the model leaking into the item names.
ONE_FORM = {"coal_dust": "dust", "bort": "gem", "certus_quartz": "gem",
            "fluorite": "gem", "rime_shard": "gem", "uraninite": "raw"}

#: What a material another module supplies is actually called, for the handful
#: of places a recipe needs a concrete item rather than a tag — a result, which
#: cannot be a tag.
CONCRETE = {
    "iron": "minecraft:iron_ingot",
    "copper": "minecraft:copper_ingot",
    "gold": "minecraft:gold_ingot",
    "steel": f"{IND}:steel_ingot",
    "uranium": "elysiumatomics:uranium_ingot",
    "redstone_dust": "minecraft:redstone",
}

MINED_FORMS = ["raw", "ingot", "nugget", "dust", "plate"]
ALLOY_FORMS = ["ingot", "nugget", "dust", "plate"]
WORKED_FORMS = ["rod", "gear"]

#: tier -> what its casing is made of. Mirrored from FoundryRules.CASINGS.
CASINGS = {1: "steel", 2: "invar", 3: "stainless", 4: "starsteel"}


def mined(name):
    return not MATERIALS[name][1]


def named(material, form):
    if material in ONE_FORM:
        return "raw_uraninite" if material == "uraninite" else material
    return ("raw_" + material) if form == "raw" else (material + "_" + form)


FOLDERS = {"ingot": "ingots", "nugget": "nuggets", "dust": "dusts",
           "plate": "plates", "raw": "raw_materials", "rod": "rods", "gear": "gears",
           "gem": "gems"}


def ours():
    """Every item this module makes, as (material, form)."""
    made = []
    for material in MATERIALS:
        if material in SOMEBODY_ELSE:
            continue
        if material in ONE_FORM:
            made.append((material, ONE_FORM[material]))
            continue
        for form in (MINED_FORMS if mined(material) else ALLOY_FORMS):
            made.append((material, form))
    for material in CASINGS.values():
        for form in WORKED_FORMS:
            if (material, form) not in made:
                made.append((material, form))
    return made


MADE = None


def refer(material, form):
    """How a recipe names this, as our item where we make one and a tag where
    somebody else does — because asking for a tag is what lets another module
    supply the same metal without either of them knowing about the other."""
    global MADE
    if MADE is None:
        MADE = set(ours())
    if (material, form) in MADE:
        return {"item": f"{NS}:{named(material, form)}"}
    if material in CONCRETE and form == "ingot":
        stem = "coal" if material == "coal_dust" else material
        return {"tag": f"c:ingots/{stem}"}
    if material == "redstone_dust":
        return {"item": "minecraft:redstone"}
    return {"tag": f"c:{FOLDERS[form]}/{material}"}


def concrete(material, form):
    """The one item a result has to be, since a result cannot be a tag."""
    global MADE
    if MADE is None:
        MADE = set(ours())
    if (material, form) in MADE:
        return f"{NS}:{named(material, form)}"
    return CONCRETE.get(material, f"{NS}:{named(material, form)}")


# ---------------------------------------------------------------------------
# The sprites
# ---------------------------------------------------------------------------
#
# One shape per form, tinted per material. A hundred and more hand-drawn metal
# items is a week nobody has, and every metal-and-shape mod that has ever
# existed drew them by tinting anyway.

CLEAR = (0, 0, 0, 0)


def shades(base):
    """A four-step ramp from one colour, dark to light."""
    def step(factor):
        return tuple(max(0, min(255, round(channel * factor))) for channel in base)
    return [step(0.45), step(0.72), step(1.0), step(1.3)]


def rgba(colour, alpha=255):
    return (colour[0], colour[1], colour[2], alpha)


def ingot(ramp):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.polygon([(3, 11), (5, 6), (11, 6), (13, 11), (13, 12), (3, 12)],
                 fill=rgba(ramp[2]), outline=rgba(ramp[0]))
    draw.line([(5, 7), (10, 7)], fill=rgba(ramp[3]))
    draw.line([(4, 11), (12, 11)], fill=rgba(ramp[1]))
    return image


def nugget(ramp):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.polygon([(6, 11), (5, 8), (8, 6), (11, 8), (10, 11)],
                 fill=rgba(ramp[2]), outline=rgba(ramp[0]))
    draw.point((8, 8), fill=rgba(ramp[3]))
    return image


def raw(ramp):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.polygon([(4, 12), (3, 7), (7, 3), (12, 6), (13, 10), (9, 13)],
                 fill=rgba((104, 96, 88)), outline=rgba((58, 54, 50)))
    for x, y in ((6, 7), (9, 6), (7, 10), (10, 10)):
        draw.point((x, y), fill=rgba(ramp[2]))
        draw.point((x + 1, y), fill=rgba(ramp[3]))
    return image


def dust(ramp):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    for x, y in ((6, 11), (8, 11), (10, 11), (7, 10), (9, 10), (8, 9),
                 (5, 12), (11, 12)):
        draw.point((x, y), fill=rgba(ramp[2]))
    for x, y in ((7, 11), (9, 11), (8, 10)):
        draw.point((x, y), fill=rgba(ramp[3]))
    draw.line([(4, 13), (12, 13)], fill=rgba(ramp[1]))
    return image


def plate(ramp):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 5, 13, 11], fill=rgba(ramp[2]), outline=rgba(ramp[0]))
    draw.line([(3, 6), (12, 6)], fill=rgba(ramp[3]))
    draw.line([(3, 10), (12, 10)], fill=rgba(ramp[1]))
    return image


def rod(ramp):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.polygon([(4, 12), (11, 4), (12, 5), (5, 13)], fill=rgba(ramp[2]),
                 outline=rgba(ramp[0]))
    draw.line([(5, 11), (11, 5)], fill=rgba(ramp[3]))
    return image


def gear(ramp):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.ellipse([3, 3, 12, 12], fill=rgba(ramp[2]), outline=rgba(ramp[0]))
    for x, y in ((7, 1), (7, 13), (1, 7), (13, 7)):
        draw.rectangle([x, y, x + 1, y + 1], fill=rgba(ramp[1]))
    draw.ellipse([6, 6, 9, 9], fill=rgba(ramp[0]))
    return image


def gem(ramp):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.polygon([(8, 2), (13, 7), (10, 14), (6, 14), (3, 7)], fill=rgba(ramp[2]),
                 outline=rgba(ramp[0]))
    draw.polygon([(8, 4), (11, 7), (8, 9), (5, 7)], fill=rgba(ramp[3]))
    return image


SHAPES = {"raw": raw, "ingot": ingot, "nugget": nugget, "dust": dust,
          "plate": plate, "rod": rod, "gear": gear, "gem": gem}


def casing_texture(tier, ramp):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], fill=rgba(ramp[1]))
    draw.rectangle([1, 1, 14, 14], fill=rgba(ramp[2]), outline=rgba(ramp[0]))
    for index in range(tier):
        draw.line([(3, 3 + index * 3), (12, 3 + index * 3)], fill=rgba(ramp[3]))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        draw.point((x, y), fill=rgba(ramp[0]))
    return image


def smelter_texture(top):
    ramp = shades((124, 128, 134))
    hot = shades((226, 118, 24))
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], fill=rgba(ramp[1]))
    draw.rectangle([1, 1, 14, 14], fill=rgba(ramp[2]), outline=rgba(ramp[0]))
    if top:
        draw.rectangle([4, 4, 7, 11], fill=rgba(hot[1]), outline=rgba(ramp[0]))
        draw.rectangle([8, 4, 11, 11], fill=rgba(hot[1]), outline=rgba(ramp[0]))
    else:
        draw.rectangle([4, 6, 11, 11], fill=rgba(hot[2]), outline=rgba(ramp[0]))
        draw.line([(5, 9), (10, 9)], fill=rgba(hot[3]))
    return image


def draw_all():
    items = os.path.join(ASSETS, "textures/item")
    blocks = os.path.join(ASSETS, "textures/block")
    os.makedirs(items, exist_ok=True)
    os.makedirs(blocks, exist_ok=True)
    drawn = 0
    for material, form in ours():
        ramp = shades(MATERIALS[material][2])
        SHAPES[form](ramp).save(os.path.join(items, named(material, form) + ".png"))
        drawn += 1
    for tier, material in CASINGS.items():
        casing_texture(tier, shades(MATERIALS[material][2])).save(
            os.path.join(blocks, f"tier_{tier}_casing.png"))
        drawn += 1
    smelter_texture(False).save(os.path.join(blocks, "alloy_smelter.png"))
    smelter_texture(True).save(os.path.join(blocks, "alloy_smelter_top.png"))
    return drawn + 2


# ---------------------------------------------------------------------------
# Models, blockstates, loot
# ---------------------------------------------------------------------------

BLOCKS = ["alloy_smelter"] + [f"tier_{tier}_casing" for tier in sorted(CASINGS)]


def cube(top, side):
    return {
        "parent": "block/block",
        "textures": {"top": f"{NS}:block/{top}", "side": f"{NS}:block/{side}",
                     "particle": f"{NS}:block/{side}"},
        "elements": [{
            "from": [0, 0, 0],
            "to": [16, 16, 16],
            "faces": {
                "down": {"texture": "#top", "cullface": "down"},
                "up": {"texture": "#top", "cullface": "up"},
                "north": {"texture": "#side", "cullface": "north"},
                "south": {"texture": "#side", "cullface": "south"},
                "west": {"texture": "#side", "cullface": "west"},
                "east": {"texture": "#side", "cullface": "east"},
            },
        }],
    }


def models_and_states():
    written = 0
    for material, form in ours():
        name = named(material, form)
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": "item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
        written += 1
    for name in BLOCKS:
        top = "alloy_smelter_top" if name == "alloy_smelter" else name
        write(os.path.join(ASSETS, "models/block", name + ".json"), cube(top, name))
        write(os.path.join(ASSETS, "blockstates", name + ".json"),
              {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": f"{NS}:block/{name}"})
        written += 1
    return written


# ---------------------------------------------------------------------------
# Recipes and tags
# ---------------------------------------------------------------------------

def drop_itself(name):
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    }


def shaped(pattern, key, result, count=1):
    return {"type": "minecraft:crafting_shaped", "category": "misc",
            "pattern": pattern, "key": key,
            "result": {"id": result, "count": count}}


def shapeless(ingredients, result, count=1):
    return {"type": "minecraft:crafting_shapeless", "category": "misc",
            "ingredients": ingredients, "result": {"id": result, "count": count}}


def cooked(kind, ingredient, result, ticks, experience=0.3):
    return {"type": f"minecraft:{kind}", "category": "misc",
            "ingredient": ingredient, "result": {"id": result},
            "experience": experience, "cookingtime": ticks}


def alloying(first, first_count, second, second_count, result, count, ticks):
    return {"type": f"{NS}:alloying",
            "first": first, "first_count": first_count,
            "second": second, "second_count": second_count,
            "result": {"id": result, "count": count}, "ticks": ticks}


def recipes():
    made = {}
    for material, form in ours():
        name = named(material, form)
        mine = f"{NS}:{name}"
        if material == "coal_dust":
            # Crushed on Age II's crusher out of the thing it is a dust of.
            made[name] = {"type": f"{IND}:crushing",
                          "input": {"item": "minecraft:coal"},
                          "result": {"id": mine, "count": 1}, "ticks": 80}
        elif material == "uraninite":
            # An ore of uranium by another name, so it smelts into Age III's.
            made[f"{name}_smelting"] = cooked(
                "smelting", {"item": mine}, "elysiumatomics:uranium_ingot", 200, 0.7)
            made[f"{name}_blasting"] = cooked(
                "blasting", {"item": mine}, "elysiumatomics:uranium_ingot", 100, 0.7)
        elif material in ONE_FORM:
            pass                              # a gem: the ground gives it whole
        elif form == "ingot":
            made[f"{name}_from_nuggets"] = shaped(
                ["NNN", "NNN", "NNN"], {"N": refer(material, "nugget")}, mine)
            made[f"{name}_from_dust"] = cooked(
                "smelting", refer(material, "dust"), mine, 200)
            made[f"{name}_from_dust_blasting"] = cooked(
                "blasting", refer(material, "dust"), mine, 100)
            if mined(material):
                made[f"{name}_from_raw"] = cooked(
                    "smelting", refer(material, "raw"), mine, 200)
                made[f"{name}_from_raw_blasting"] = cooked(
                    "blasting", refer(material, "raw"), mine, 100)
        elif form == "nugget":
            made[name] = shapeless([refer(material, "ingot")], mine, 9)
        elif form == "plate":
            # Rolled on Age II's mill, which is the machine that exists for it.
            made[name] = {"type": f"{IND}:rolling",
                          "input": refer(material, "ingot"),
                          "result": {"id": mine, "count": 1}, "ticks": 120}
        elif form == "dust":
            # Crushed on Age II's crusher, likewise.
            made[name] = {"type": f"{IND}:crushing",
                          "input": refer(material, "ingot"),
                          "result": {"id": mine, "count": 1}, "ticks": 100}
        elif form == "rod":
            made[name] = shaped([" I ", " I "], {"I": refer(material, "ingot")}, mine, 2)
        elif form == "gear":
            made[name] = shaped([" R ", "RPR", " R "], {
                "R": refer(material, "rod"), "P": refer(material, "plate")}, mine)

    # The alloys themselves, read straight off the chain.
    for material, (tier, parts, _) in MATERIALS.items():
        if not parts:
            continue
        (first, first_units), (second, second_units) = list(parts.items())
        made[f"{material}_alloy"] = alloying(
            refer(first, "dust" if first in ONE_FORM else "ingot"),
            first_units // UNIT,
            refer(second, "dust" if second in ONE_FORM
                  or second == "redstone_dust" else "ingot"),
            second_units // UNIT,
            concrete(material, "ingot"), 1, 100 + 60 * tier)

    # The casings, and the machine that makes the alloys for them.
    for tier, material in CASINGS.items():
        made[f"tier_{tier}_casing"] = shaped(["PPP", "PGP", "PPP"], {
            "P": refer(material, "plate"), "G": refer(material, "gear")},
            f"{NS}:tier_{tier}_casing", 2)
    made["alloy_smelter"] = shaped(["PPP", "PFP", "PBP"], {
        "P": {"tag": "c:plates/steel"}, "F": {"item": "minecraft:blast_furnace"},
        "B": {"item": f"{IND}:arc_furnace"}}, f"{NS}:alloy_smelter")
    return made


def data_files():
    written = 0
    for name in BLOCKS:
        write(os.path.join(DATA, "loot_table/blocks", name + ".json"), drop_itself(name))
        written += 1
    for name, recipe in recipes().items():
        write(os.path.join(DATA, "recipe", name + ".json"), recipe)
        written += 1

    # The tags. This is the debt the module is paying: elysium-ores generates
    # the ore and drops whatever fills these, and nothing installed fills them
    # once the mods being replaced are gone.
    common = os.path.join(OUT, "src/main/resources/data/c/tags/item")
    filled = {}
    for material, form in ours():
        stem = "coal" if material == "coal_dust" else material
        filled.setdefault(f"{FOLDERS[form]}/{stem}", []).append(
            f"{NS}:{named(material, form)}")
    for path, values in sorted(filled.items()):
        write(os.path.join(common, path + ".json"),
              {"replace": False, "values": values})
        written += 1

    tags = os.path.join(OUT, "src/main/resources/data/minecraft/tags/block")
    write(os.path.join(tags, "mineable/pickaxe.json"),
          {"replace": False, "values": [f"{NS}:{name}" for name in BLOCKS]})
    written += 1
    return written


# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------

FORM_NAMES = {"raw": "Raw %s", "ingot": "%s Ingot", "nugget": "%s Nugget",
              "dust": "%s Dust", "plate": "%s Plate", "rod": "%s Rod",
              "gear": "%s Gear"}

LINES = {
    "itemGroup.elysiumfoundry": "Elysium Foundry",
    "elysiumfoundry.smelter.dark": "No power reaching it.",
    "elysiumfoundry.smelter.waiting": "Running at %s, and it wants two things in it.",
    "elysiumfoundry.smelter.melting": "Melting, at %s.",
}


def pretty(material):
    return material.replace("_", " ").title()


def language():
    entries = dict(LINES)
    for material, form in ours():
        shown = (FORM_NAMES[form] % pretty(material)
                 if material not in ONE_FORM or material == "uraninite"
                 else pretty(material))
        entries[f"item.{NS}.{named(material, form)}"] = shown
    entries[f"block.{NS}.alloy_smelter"] = "Alloy Smelter"
    for tier, material in CASINGS.items():
        entries[f"block.{NS}.tier_{tier}_casing"] = f"{pretty(material)} Casing"
    write(os.path.join(ASSETS, "lang/en_us.json"), dict(sorted(entries.items())))
    return len(entries)


if __name__ == "__main__":
    sprites = draw_all()
    models = models_and_states()
    data = data_files()
    keys = language()
    print(f"foundry: {sprites} sprite(s), {models} thing(s) modelled and stated, "
          f"{data} data file(s), {keys} language key(s)")
