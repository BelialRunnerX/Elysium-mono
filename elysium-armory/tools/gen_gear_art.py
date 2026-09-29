#!/usr/bin/env python3
"""Draw the modular gear and part sprites, and write their models and names.

Every sprite is two greyscale layers: the working metal, and the handle or
binding. Neither carries a colour — the game tints layer 0 with the material
the part is made of and layer 1 with the rod's, which is the whole point of
modular gear: one sprite, a hundred and thirty-two materials.

Shapes are composed from rectangles in a 16x16 grid rather than drawn pixel by
pixel, then shaded from the top left and outlined, the way the rest of the
Voidforged set is lit (see art/pixelart.py for that convention, which this
follows without borrowing its maps).

Re-runnable: the output is a pure function of the table below.
"""
import json
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(HERE, "src/main/resources/assets/elysiumarmory")
NS = "elysiumarmory"

# Greyscale ramps: outline, then dark to bright. Tinted at render time.
METAL = [(26, 26, 30), (74, 74, 82), (108, 108, 118), (140, 140, 150), (176, 176, 186), (214, 214, 224)]
WOOD = [(26, 20, 14), (78, 60, 40), (104, 80, 54), (130, 100, 68), (156, 122, 84), (182, 146, 104)]


def rect(x0, y0, x1, y1):
    return {(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)}


def diagonal(x0, y0, length, width=2, dx=1, dy=-1):
    """A band running from (x0, y0), the way vanilla's hafts run: a diagonal
    of single pixels, widened sideways rather than doubled."""
    out = set()
    for i in range(length):
        for w in range(width):
            out.add((x0 + i * dx + w, y0 + i * dy))
    return {(x, y) for x, y in out if 0 <= x < 16 and 0 <= y < 16}


def triangle(x0, y0, size, right=True):
    out = set()
    for i in range(size):
        for x in range(i + 1):
            out.add((x0 + (x if right else -x), y0 + i))
    return {(x, y) for x, y in out if 0 <= x < 16 and 0 <= y < 16}


HAFT = diagonal(2, 13, 11, width=2)                 # the standard handle, lower left to upper right
SHORT_HAFT = diagonal(4, 12, 7, width=2)

SHAPES = {
    # gear type / part id: (metal pixels, handle pixels)
    "sword_blade": (diagonal(6, 9, 7, width=2) | rect(4, 10, 8, 11), set()),
    "katana_blade": (diagonal(5, 10, 9, width=1) | rect(4, 11, 7, 11), set()),
    "machete_blade": (rect(8, 3, 11, 10) | rect(7, 8, 8, 11), set()),
    "spear_tip": (triangle(7, 2, 4) | rect(7, 6, 8, 8), set()),
    "mace_core": (rect(8, 2, 12, 6), set()),
    "dagger_blade": (diagonal(7, 8, 4, width=2), set()),
    "knife_blade": (diagonal(7, 8, 4, width=1) | rect(7, 8, 8, 9), set()),
    "pickaxe_head": (rect(4, 3, 12, 4) | rect(3, 4, 4, 5) | rect(12, 4, 13, 5), set()),
    "shovel_head": (rect(7, 2, 11, 6), set()),
    "axe_head": (rect(8, 2, 12, 7) | rect(7, 3, 8, 6), set()),
    "hoe_head": (rect(7, 2, 12, 3) | rect(11, 3, 12, 4), set()),
    "shear_blades": (diagonal(5, 9, 5, width=1) | diagonal(9, 9, 5, width=1, dx=-1), set()),
    "sickle_blade": (rect(6, 3, 12, 4) | rect(11, 4, 12, 7), set()),
    "mattock_head": (rect(4, 3, 8, 4) | rect(9, 3, 12, 6), set()),
    "paxel_head": (rect(4, 2, 12, 4) | rect(10, 4, 12, 7), set()),
    "hammer_head": (rect(6, 2, 13, 7), set()),
    "excavator_head": (rect(5, 2, 12, 7), set()),
    "saw_blade": (rect(6, 3, 13, 6) | {(x, 7) for x in range(6, 14, 2)}, set()),
    "prospector_hammer_head": (rect(7, 3, 12, 6), set()),
    "bow_limbs": (diagonal(4, 11, 9, width=1) | rect(3, 11, 4, 12) | rect(12, 2, 13, 3), set()),
    "crossbow_limbs": (rect(3, 5, 13, 6) | rect(3, 4, 4, 7) | rect(12, 4, 13, 7), set()),
    "slingshot_limbs": (rect(4, 3, 5, 8) | rect(10, 3, 11, 8) | rect(5, 8, 10, 9), set()),
    "trident_prongs": (rect(4, 2, 5, 6) | rect(7, 2, 8, 6) | rect(10, 2, 11, 6) | rect(4, 6, 11, 7), set()),
    "fishing_reel_and_hook": (rect(9, 3, 12, 6) | rect(11, 7, 12, 9), set()),
    "shield_plate": (rect(4, 2, 11, 10) | rect(5, 10, 10, 12), set()),
    "helmet_plates": (rect(4, 3, 11, 8) | rect(3, 5, 4, 9) | rect(11, 5, 12, 9), set()),
    "chestplate_plates": (rect(4, 3, 11, 12) | rect(2, 4, 4, 8) | rect(11, 4, 13, 8), set()),
    "legging_plates": (rect(4, 2, 11, 7) | rect(4, 7, 6, 13) | rect(9, 7, 11, 13), set()),
    "boot_plates": (rect(4, 6, 7, 12) | rect(8, 6, 11, 12) | rect(3, 11, 12, 13), set()),
    "elytra_wings": (triangle(6, 3, 6, right=False) | triangle(9, 3, 6), set()),
    "arrow_heads": (triangle(7, 3, 3) | rect(7, 6, 8, 8), set()),
    "ring_shank": (rect(5, 5, 10, 6) | rect(5, 9, 10, 10) | rect(4, 6, 5, 9) | rect(10, 6, 11, 9), set()),
    "bracelet_band": (rect(3, 6, 12, 7) | rect(3, 9, 12, 10) | rect(2, 7, 3, 9) | rect(12, 7, 13, 9), set()),
    "necklace_chain": ({(x, 5 + abs(7 - x) // 2) for x in range(3, 13)} | rect(7, 9, 8, 11), set()),
    # the parts that are not a head
    "rod": (set(), diagonal(3, 12, 10, width=2)),
    "tip": (triangle(6, 5, 4), set()),
    "binding": (set(), rect(4, 7, 11, 9)),
    "grip": (set(), rect(6, 4, 9, 11)),
    "lining": (set(), rect(4, 5, 11, 10)),
    "coating": (rect(5, 4, 10, 11), set()),
    "setting": (rect(6, 5, 9, 10) | rect(5, 6, 10, 9), set()),
    "cord": (set(), diagonal(4, 11, 8, width=1)),
    "fletching": (set(), triangle(6, 4, 5) | triangle(9, 4, 5)),
    "magnetic_upgrade": (rect(4, 4, 6, 11) | rect(9, 4, 11, 11) | rect(4, 4, 11, 6), set()),
    "red_card_upgrade": (rect(5, 3, 10, 12), set()),
    "road_maker_upgrade": (rect(3, 7, 12, 10), set()),
    "spoon_upgrade": (rect(6, 3, 9, 7) | rect(7, 7, 8, 12), set()),
    "wide_plate_upgrade": (rect(3, 5, 12, 10), set()),
}

# A piece of gear is its main part plus the parts it is held by.
GEAR = {
    "sword": ("sword_blade", HAFT), "katana": ("katana_blade", HAFT), "machete": ("machete_blade", SHORT_HAFT),
    "spear": ("spear_tip", diagonal(1, 14, 13, width=1)), "mace": ("mace_core", HAFT),
    "dagger": ("dagger_blade", SHORT_HAFT), "knife": ("knife_blade", SHORT_HAFT),
    "pickaxe": ("pickaxe_head", HAFT), "shovel": ("shovel_head", HAFT), "axe": ("axe_head", HAFT),
    "hoe": ("hoe_head", HAFT), "shears": ("shear_blades", SHORT_HAFT), "sickle": ("sickle_blade", HAFT),
    "mattock": ("mattock_head", HAFT), "paxel": ("paxel_head", HAFT), "hammer": ("hammer_head", HAFT),
    "excavator": ("excavator_head", HAFT), "saw": ("saw_blade", HAFT),
    "prospector_hammer": ("prospector_hammer_head", SHORT_HAFT),
    "bow": ("bow_limbs", diagonal(4, 11, 9, width=1, dx=0)), "crossbow": ("crossbow_limbs", rect(7, 6, 8, 13)),
    "slingshot": ("slingshot_limbs", rect(7, 9, 8, 13)), "trident": ("trident_prongs", HAFT),
    "fishing_rod": ("fishing_reel_and_hook", diagonal(2, 13, 10, width=1)),
    "shield": ("shield_plate", rect(7, 12, 8, 14)),
    "helmet": ("helmet_plates", set()), "chestplate": ("chestplate_plates", set()),
    "leggings": ("legging_plates", set()), "boots": ("boot_plates", set()),
    "elytra": ("elytra_wings", rect(7, 3, 8, 9)), "arrow": ("arrow_heads", diagonal(2, 13, 10, width=1)),
    "ring": ("ring_shank", set()), "bracelet": ("bracelet_band", set()), "necklace": ("necklace_chain", set()),
}

NAMES = {}


def shade(pixels, ramp):
    """Light from the top left, an outline all round: the set's own silhouette."""
    image = {}
    for x, y in pixels:
        top = (x, y - 1) not in pixels
        left = (x - 1, y) not in pixels
        bottom = (x, y + 1) not in pixels
        right = (x + 1, y) not in pixels
        if top or left:
            image[(x, y)] = ramp[5] if (top and left) else ramp[4]
        elif bottom or right:
            image[(x, y)] = ramp[2]
        else:
            image[(x, y)] = ramp[3]
    for x, y in list(pixels):
        for dx, dy in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            p = (x + dx, y + dy)
            if p not in pixels and 0 <= p[0] < 16 and 0 <= p[1] < 16:
                image.setdefault(p, ramp[0])
    return image


def draw(path, pixels, ramp):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = image.load()
    for (x, y), colour in shade(pixels, ramp).items():
        px[x, y] = colour + (255,)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)


def title(name):
    return " ".join(w.capitalize() for w in name.split("_"))


def model(name, layers):
    textures = {f"layer{i}": f"{NS}:item/{layer}" for i, layer in enumerate(layers)}
    return {"parent": "minecraft:item/generated", "textures": textures}


# The plans and the bench they are drawn at: flat sprites, not tinted.
PAPER = [(58, 54, 46), (126, 118, 100), (158, 150, 130), (190, 182, 162), (214, 208, 190), (236, 232, 218)]
INK = (40, 60, 110)
# The two ramps the P6 benches are drawn from: essence is the violet an affix
# comes back as, stone is the early anvil nobody has smelted anything for yet.
ESSENCE = [(30, 20, 44), (70, 46, 104), (98, 66, 142), (128, 92, 176), (162, 128, 202), (200, 176, 228)]
# The two P8 ramps: a forge that is hot, and a charger that is not.
FORGE = [(38, 16, 10), (96, 40, 20), (134, 62, 26), (170, 90, 34), (206, 128, 50), (236, 178, 90)]
STAR = [(16, 24, 44), (38, 58, 96), (58, 86, 134), (84, 118, 170), (124, 158, 202), (184, 210, 236)]
STONE = [(24, 24, 26), (66, 66, 70), (92, 92, 96), (118, 118, 122), (146, 146, 150), (178, 178, 182)]
#: The plan book's binding: oxblood leather, and a darker spine.
LEATHER = [(26, 14, 12), (58, 28, 24), (82, 40, 34), (104, 52, 44), (126, 66, 56), (150, 84, 72)]
SPINE = (36, 20, 16)


def plans(tex, models, lang):
    """Blueprint paper, a blueprint, a template, and the bench's own art."""
    sheet = rect(3, 2, 12, 13)
    draw(os.path.join(tex, "blueprint_paper.png"), sheet, PAPER)
    for name, lines in (("blueprint", [(5, 5), (5, 8), (5, 11)]), ("template", [(5, 6), (5, 10)])):
        image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        px = image.load()
        for (x, y), colour in shade(sheet, PAPER).items():
            px[x, y] = colour + (255,)
        for x0, y in lines:
            for x in range(x0, 11):
                px[x, y] = INK + (255,)
        image.save(os.path.join(tex, name + ".png"))
    # The mod kit: a small bundle of tools for taking a part back off.
    kit = rect(3, 6, 12, 11) | rect(5, 4, 6, 6) | rect(9, 3, 10, 6)
    draw(os.path.join(tex, "mod_kit.png"), kit, METAL)
    # Affix essence: a small bright mote, the thing an affix comes back as.
    mote = rect(6, 6, 9, 9) | rect(7, 4, 8, 11) | rect(4, 7, 11, 8)
    draw(os.path.join(tex, "affix_essence.png"), mote, ESSENCE)
    # Sheet metal: a flat pressed plate, wider than it is tall.
    plate = rect(2, 5, 13, 10)
    draw(os.path.join(tex, "sheet_metal.png"), plate, METAL)
    # The plan book: a bound cover with the same plans drawn inside it, so a
    # player reads "these are my blueprints" off the icon rather than the name.
    book = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = book.load()
    for (x, y), colour in shade(rect(2, 2, 13, 13), LEATHER).items():
        px[x, y] = colour + (255,)
    for (x, y), colour in shade(rect(4, 3, 12, 12), PAPER).items():
        px[x, y] = colour + (255,)
    for y in (5, 8, 11):
        for x in range(6, 11):
            px[x, y] = INK + (255,)
    for y in range(2, 14):                 # the spine
        px[3, y] = SPINE + (255,)
    book.save(os.path.join(tex, "plan_book.png"))

    for name in ("blueprint_paper", "blueprint", "template", "mod_kit",
                 "affix_essence", "sheet_metal", "plan_book"):
        json.dump(model(name, [name]), open(os.path.join(models, name + ".json"), "w", newline="\n"), indent=2)
        lang[f"item.{NS}.{name}"] = title(name)
    # The bench: a plated top on a dark frame, drawn as a 16x16 block face.
    top = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = top.load()
    for (x, y), colour in shade(rect(0, 0, 15, 15), METAL).items():
        px[x, y] = colour + (255,)
    for x in range(2, 14):
        px[x, 7] = METAL[1] + (255,)
        px[x, 8] = METAL[2] + (255,)
    top.save(os.path.join(tex.replace("item", "block"), "gear_smithing_table_top.png"))
    side = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = side.load()
    for (x, y), colour in shade(rect(0, 0, 15, 15), METAL).items():
        px[x, y] = colour + (255,)
    for y in range(9, 15):
        for x in range(2, 14):
            px[x, y] = WOOD[2] + (255,)
    side.save(os.path.join(tex.replace("item", "block"), "gear_smithing_table_side.png"))
    # The other benches. Each is the same plated form with its own face, so
    # they read as a set rather than as four unrelated blocks.
    block_dir = tex.replace("item", "block")
    for name, ramp, marks in (
            ("salvager", METAL, [(4, 5), (11, 5), (4, 10), (11, 10)]),
            ("reforging_table", ESSENCE, [(7, 4), (8, 4), (7, 11), (8, 11)]),
            ("metal_press", METAL, [(3, 6), (12, 6)]),
            ("stone_anvil", STONE, [(6, 7), (9, 7)]),
            ("alloy_forge", FORGE, [(5, 5), (10, 5), (7, 9), (8, 9)]),
            ("material_grader", METAL, [(4, 4), (11, 11), (4, 11), (11, 4), (7, 7)]),
            ("starlight_charger", STAR, [(7, 3), (3, 7), (12, 7), (7, 12)])):
        face = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        px = face.load()
        for (x, y), colour in shade(rect(0, 0, 15, 15), ramp).items():
            px[x, y] = colour + (255,)
        for x, y in marks:
            for dx in range(2):
                for dy in range(2):
                    px[x + dx, y + dy] = INK + (255,)
        face.save(os.path.join(block_dir, name + "_top.png"))
        flank = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        px = flank.load()
        for (x, y), colour in shade(rect(0, 0, 15, 15), ramp).items():
            px[x, y] = colour + (255,)
        for y in range(10, 15):
            for x in range(1, 15):
                px[x, y] = ramp[1] + (255,)
        flank.save(os.path.join(block_dir, name + "_side.png"))
    lang[f"block.{NS}.gear_smithing_table"] = "Gear Smithing Table"
    lang[f"block.{NS}.salvager"] = "Salvager"
    lang[f"block.{NS}.reforging_table"] = "Reforging Table"
    lang[f"block.{NS}.metal_press"] = "Metal Press"
    lang[f"block.{NS}.stone_anvil"] = "Stone Anvil"
    lang[f"block.{NS}.alloy_forge"] = "Alloy Forge"
    lang[f"block.{NS}.material_grader"] = "Material Grader"
    lang[f"block.{NS}.starlight_charger"] = "Starlight Charger"
    lang[f"{NS}.forge.nothing"] = "Nothing here would work better mixed"
    lang[f"{NS}.grader.nothing"] = "Nothing here can be worked up"
    lang[f"{NS}.grader.needs_gem"] = "Hold a gem in your other hand to pay for the work"
    lang[f"{NS}.grader.graded"] = "Worked up to grade %s"
    lang[f"{NS}.grader.crude"] = "The work went badly: %s"
    lang[f"{NS}.charger.nothing"] = "Nothing here would take the charge"
    lang[f"{NS}.marks.grade"] = "Grade %s"
    lang[f"{NS}.marks.starcharged"] = "Starcharged"
    lang[f"{NS}.marks.synergy"] = "Synergy %s"
    lang[f"item.{NS}.affix_essence"] = "Affix Essence"
    lang[f"item.{NS}.sheet_metal"] = "Sheet Metal"
    lang[f"{NS}.salvager.nothing"] = "Nothing here can be taken apart"
    lang[f"{NS}.salvager.button"] = "Salvage"
    lang[f"{NS}.reforge.cost"] = "Costs %s essence"
    lang[f"{NS}.reforge.target"] = "Rerolls to %s"
    lang[f"{NS}.reforge.no_essence"] = "Add essence to choose a rarity"
    lang[f"{NS}.press.only_ingots"] = "The press takes ingots"
    lang[f"{NS}.mod_kit.nothing"] = "Nothing on this can be taken off"
    lang[f"{NS}.tooltip.tier"] = "Mines: %s"
    for rung in ("wood", "bamboo", "netherwood", "vanilla_gold", "stone", "flint", "bone", "basalt",
                 "blackstone", "end_stone", "netherrack", "obsidian", "sandstone", "terracotta", "gold",
                 "copper", "amethyst", "lapis_lazuli", "iron", "bronze", "blaze_gold", "quartz", "emerald",
                 "high_carbon_steel", "diamond", "crimson_iron", "azure_silver", "dimerald", "netherite",
                 "crimson_steel", "azure_electrum", "tyrian_steel"):
        lang[f"{NS}.tier.{rung}"] = title(rung)
    lang[f"{NS}.blueprint.blank"] = "No shape chosen"
    lang[f"{NS}.blueprint.needs"] = "Takes %s material(s)"
    # A part's extra ingredient, which the grid charges one of (a coating's
    # glass bottle); and a found template before anyone has read it.
    lang[f"{NS}.blueprint.also"] = "Also takes one %s"
    lang[f"{NS}.template.unread"] = "Not yet read: carry it to see the plan"


def armour_layers(root, lang):
    """The worn-armour textures. Grey, and dyeable, so a piece is tinted by the
    material it was made of — the same colour its sprite is tinted with."""
    import random
    for layer, height in (("1", 32), ("2", 32)):
        image = Image.new("RGBA", (64, height), (0, 0, 0, 0))
        px = image.load()
        rng = random.Random(7 + int(layer))
        for x in range(64):
            for y in range(height):
                # A plate surface: a mid grey with a little tooth, so the tint
                # has something to sit on and the piece does not read as flat.
                tone = METAL[3 if rng.random() > 0.25 else 2]
                px[x, y] = tone + (255,)
        path = os.path.join(root, "assets", NS, "textures/models/armor", f"modular_layer_{layer}.png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        image.save(path)
    lang[f"{NS}.prospector.finds"] = "Finds: %s"
    lang[f"{NS}.prospector.nothing"] = "Nothing found"


def data(root, lang):
    """Blockstate, models, loot and recipes for the bench and the plans."""
    def write(path, obj):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        json.dump(obj, open(path, "w", newline="\n"), indent=2)

    assets = os.path.join(root, "assets", NS)
    data_dir = os.path.join(root, "data", NS)
    write(os.path.join(assets, "blockstates/gear_smithing_table.json"),
          {"variants": {"": {"model": f"{NS}:block/gear_smithing_table"}}})
    write(os.path.join(assets, "models/block/gear_smithing_table.json"),
          {"parent": "minecraft:block/cube_bottom_top",
           "textures": {"top": f"{NS}:block/gear_smithing_table_top",
                        "side": f"{NS}:block/gear_smithing_table_side",
                        "bottom": f"{NS}:block/gear_smithing_table_side"}})
    write(os.path.join(assets, "models/item/gear_smithing_table.json"),
          {"parent": f"{NS}:block/gear_smithing_table"})
    write(os.path.join(data_dir, "loot_table/blocks/gear_smithing_table.json"),
          {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
              {"type": "minecraft:item", "name": f"{NS}:gear_smithing_table"}]}]})
    # Blueprint paper: what every plan is drawn on.
    write(os.path.join(data_dir, "recipe/blueprint_paper.json"),
          {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["PP", "PC"],
           "key": {"P": {"item": "minecraft:paper"}, "C": {"item": "minecraft:clay_ball"}},
           "result": {"count": 3, "id": f"{NS}:blueprint_paper"}})
    write(os.path.join(data_dir, "recipe/gear_smithing_table.json"),
          {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["III", "PSP", "PSP"],
           "key": {"I": {"tag": "c:ingots/iron"}, "P": {"item": f"{NS}:blueprint_paper"},
                   "S": {"item": "minecraft:smooth_stone"}},
           "result": {"count": 1, "id": f"{NS}:gear_smithing_table"}})
    # The two special recipes: one for every part, one for every piece of gear.
    write(os.path.join(data_dir, "recipe/mod_kit.json"),
          {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": [" I ", "ISI", " I "],
           "key": {"I": {"tag": "c:ingots/iron"}, "S": {"item": "minecraft:stick"}},
           "result": {"count": 1, "id": f"{NS}:mod_kit"}})
    # The upgrade parts: bolted on afterwards, each from what it does.
    upgrades = {
        "magnetic_upgrade": ("minecraft:iron_ingot", "minecraft:lodestone"),
        "red_card_upgrade": ("minecraft:red_dye", "minecraft:paper"),
        "road_maker_upgrade": ("minecraft:gravel", "minecraft:iron_ingot"),
        "spoon_upgrade": ("minecraft:iron_ingot", "minecraft:bowl"),
        "wide_plate_upgrade": ("minecraft:iron_ingot", "minecraft:heavy_core"),
    }
    for name, (a, b) in upgrades.items():
        write(os.path.join(data_dir, "recipe", name + ".json"),
              {"type": "minecraft:crafting_shapeless", "category": "equipment",
               "ingredients": [{"item": a}, {"item": a}, {"item": b}],
               "result": {"count": 1, "id": f"{NS}:{name}"}})
    # The other three benches and the early anvil: blockstate, model, item
    # model, loot and a recipe apiece.
    benches = {
        "salvager": (["III", "IPI", "SSS"],
                     {"I": {"tag": "c:ingots/iron"}, "P": {"item": f"{NS}:mod_kit"},
                      "S": {"item": "minecraft:smooth_stone"}}),
        "reforging_table": (["IEI", "ISI", "SSS"],
                            {"I": {"tag": "c:ingots/iron"}, "E": {"item": "minecraft:amethyst_shard"},
                             "S": {"item": "minecraft:smooth_stone"}}),
        "metal_press": (["I I", "ISI", "SSS"],
                        {"I": {"tag": "c:ingots/iron"}, "S": {"item": "minecraft:smooth_stone"}}),
        "stone_anvil": ([" S ", "SSS", "SSS"],
                        {"S": {"item": "minecraft:cobblestone"}}),
        "alloy_forge": (["III", "IBI", "SSS"],
                        {"I": {"tag": "c:ingots/iron"}, "B": {"item": "minecraft:blast_furnace"},
                         "S": {"item": "minecraft:smooth_stone"}}),
        "material_grader": (["IGI", "ISI", "SSS"],
                            {"I": {"tag": "c:ingots/iron"}, "G": {"item": "minecraft:grindstone"},
                             "S": {"item": "minecraft:smooth_stone"}}),
        "starlight_charger": ([" A ", "AIA", "SSS"],
                              {"A": {"item": "minecraft:amethyst_shard"},
                               "I": {"tag": "c:ingots/iron"},
                               "S": {"item": "minecraft:smooth_stone"}}),
    }
    for name, (pattern, key) in benches.items():
        write(os.path.join(assets, f"blockstates/{name}.json"),
              {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write(os.path.join(assets, f"models/block/{name}.json"),
              {"parent": "minecraft:block/cube_bottom_top",
               "textures": {"top": f"{NS}:block/{name}_top",
                            "side": f"{NS}:block/{name}_side",
                            "bottom": f"{NS}:block/{name}_side"}})
        write(os.path.join(assets, f"models/item/{name}.json"), {"parent": f"{NS}:block/{name}"})
        write(os.path.join(data_dir, f"loot_table/blocks/{name}.json"),
              {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
                  {"type": "minecraft:item", "name": f"{NS}:{name}"}]}]})
        write(os.path.join(data_dir, "recipe", name + ".json"),
              {"type": "minecraft:crafting_shaped", "category": "misc",
               "pattern": pattern, "key": key,
               "result": {"count": 1, "id": f"{NS}:{name}"}})
    # Sheet metal is pressed, not crafted; essence is salvaged, not crafted.
    # Neither has a recipe on purpose, and the validator knows it.
    write(os.path.join(data_dir, "recipe/repair.json"),
          {"type": f"{NS}:repair", "category": "equipment"})
    write(os.path.join(data_dir, "recipe/paint.json"),
          {"type": f"{NS}:paint", "category": "equipment"})
    write(os.path.join(data_dir, "recipe/conversion.json"),
          {"type": f"{NS}:conversion", "category": "equipment"})
    write(os.path.join(data_dir, "recipe/compound_part.json"),
          {"type": f"{NS}:compound_part", "category": "equipment"})
    write(os.path.join(data_dir, "recipe/gear_assembly.json"),
          {"type": f"{NS}:gear_assembly", "category": "equipment"})


def main():
    tex = os.path.join(ASSETS, "textures/item")
    os.makedirs(os.path.join(ASSETS, "textures/block"), exist_ok=True)
    models = os.path.join(ASSETS, "models/item")
    lang = {"itemGroup.elysiumarmory": "Elysium Armory"}
    drawn = 0
    for part, (metal, wood) in SHAPES.items():
        layers = []
        if metal:
            draw(os.path.join(tex, part + ".png"), metal, METAL)
            layers.append(part)
        if wood:
            draw(os.path.join(tex, part + "_grip.png"), wood, WOOD)
            layers.append(part + "_grip")
        os.makedirs(models, exist_ok=True)
        json.dump(model(part, layers), open(os.path.join(models, part + ".json"), "w", newline="\n"), indent=2)
        lang[f"item.{NS}.{part}"] = title(part)
        drawn += 1
    for gear, (head, haft) in GEAR.items():
        metal, _ = SHAPES[head]
        layers = []
        if metal:
            draw(os.path.join(tex, gear + ".png"), metal, METAL)
            layers.append(gear)
        if haft:
            draw(os.path.join(tex, gear + "_grip.png"), haft, WOOD)
            layers.append(gear + "_grip")
        json.dump(model(gear, layers), open(os.path.join(models, gear + ".json"), "w", newline="\n"), indent=2)
        lang[f"item.{NS}.{gear}"] = title(gear)
        drawn += 1
    plans(tex, models, lang)
    data(os.path.join(HERE, "src/main/resources"), lang)
    armour_layers(os.path.join(HERE, "src/main/resources"), lang)
    os.makedirs(os.path.join(ASSETS, "lang"), exist_ok=True)
    # Merged, not replaced. The trait names come from gen_traits.py and are
    # written into the same file; a wholesale write here deleted all 150 of
    # them, which the validator caught and a player would have seen as raw keys
    # on every piece of gear in the game.
    path = os.path.join(ASSETS, "lang/en_us.json")
    current = json.load(open(path, encoding="utf-8")) if os.path.isfile(path) else {}
    current.update(lang)
    json.dump(dict(sorted(current.items())), open(path, "w", newline="\n"), indent=2)
    print(f"drew {drawn} sprites with models and names")


if __name__ == "__main__":
    sys.exit(main())
