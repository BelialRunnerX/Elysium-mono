"""
Declared sources for the crafts group: elysiumarmory, elysiumarcana,
elysiumenchanting, elysiumdungeons, elysiumores.

Only what Java hands out and the checker cannot read from data. Where the code
treats a family as one (every part through one recipe, every gear type through
another), the declarations are generated from the same data files the code
reads, so a part added to the data is declared without a line here.
"""
import json
import os

ARMORY = "elysium-armory/src/main/java/com/elysium/armory"
DUNGEONS = "elysium-dungeons/src/main/java/com/elysium/dungeons"
PARTS_DIR = "elysium-armory/src/main/resources/data/elysiumarmory/elysium_parts"
#: The checkout this file is in: tools/obtainable/crafts.py -> the repo root.
REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def _root(declared):
    root = getattr(declared, "__globals__", {}).get("ROOT")
    return root or os.environ.get("ELYSIUM_MONO") or REPO


def _parts(root):
    """Every part definition, as the armory reads it: id -> json."""
    out = {}
    folder = os.path.join(root, PARTS_DIR)
    for name in sorted(os.listdir(folder)):
        if name.endswith(".json"):
            with open(os.path.join(folder, name), encoding="utf-8") as handle:
                out[name[:-5]] = json.load(handle)
    return out


# One vanilla material per part type that the armory's own material files say
# fits it (elysium_materials/*.json: the material has properties for that part
# type, and its categories meet the part's): iron for heads, blades, plates and
# tips (iron is "metal", so the jewellery that wants metal takes it too), oak
# planks for a rod, string for a binding or a cord, leather for a grip, a lining
# and the elytra's cloth-or-sheet wings, gold for a coating, a diamond for a
# setting, a feather for fletching.
_MATERIAL_FOR = {
    "main": "minecraft:iron_ingot",
    "tip": "minecraft:iron_ingot",
    "rod": "minecraft:oak_planks",
    "binding": "minecraft:string",
    "cord": "minecraft:string",
    "grip": "minecraft:leather",
    "lining": "minecraft:leather",
    "coating": "minecraft:gold_ingot",
    "setting": "minecraft:diamond",
    "fletching": "minecraft:feather",
}


def _material(part):
    categories = set(part.get("crafting", {}).get("categories", []))
    if categories & {"cloth", "sheet"} and "metal" not in categories:
        return "minecraft:leather"
    return _MATERIAL_FOR[part["part_type"]]


# Vanilla gear the conversion recipe turns modular, by gear type: every shape in
# ArmoryConversionRules.SHAPES. Pickaxe, crossbow and shears were left out while
# the recipe matched by the order of a Map.ofEntries (a pickaxe converted on
# some starts only, shears never); since 29 September a shape is a whole word,
# the longest wins, and shears are iron.
_CONVERTS = {
    "sword": "minecraft:iron_sword", "pickaxe": "minecraft:iron_pickaxe", "axe": "minecraft:iron_axe",
    "shovel": "minecraft:iron_shovel", "hoe": "minecraft:iron_hoe", "helmet": "minecraft:iron_helmet",
    "chestplate": "minecraft:iron_chestplate", "leggings": "minecraft:iron_leggings",
    "boots": "minecraft:iron_boots", "shears": "minecraft:shears", "bow": "minecraft:bow",
    "crossbow": "minecraft:crossbow", "trident": "minecraft:trident", "shield": "minecraft:shield",
    "fishing_rod": "minecraft:fishing_rod", "elytra": "minecraft:elytra",
}


def declare(declared):
    root = _root(declared)

    # ------------------------------------------------------------------
    # elysiumarmory
    # ------------------------------------------------------------------
    declared("elysiumarmory:blueprint",
             ["elysiumarmory:gear_smithing_table", "elysiumarmory:blueprint_paper"],
             f"{ARMORY}/menu/ArmorySmithingMenu.java",
             "output.setItem(0, blueprint.of(parts.get(selected)));",
             "the Gear Smithing Table draws a blueprint of the chosen part on one blueprint paper")

    declared("elysiumarmory:sheet_metal",
             ["elysiumarmory:metal_press", "#c:ingots"],
             f"{ARMORY}/block/ArmoryMetalPressBlock.java",
             "new ItemStack(ArmoryItems.SHEET_METAL.get())",
             "hitting the Metal Press with any c:ingots item presses it into sheet metal")

    # The salvager takes any affixed piece apart (ArmorySalvageRules.salvageable,
    # 29 September); loot rolls affixes onto the gear it drops from world tier 1.
    declared("elysiumarmory:affix_essence",
             ["elysiumarmory:salvager", "minecraft:iron_sword"],
             f"{ARMORY}/craft/ArmorySalvage.java",
             "out.add(new ItemStack(ArmoryItems.AFFIX_ESSENCE.get(), here));",
             "the salvager takes any affixed piece apart for one essence an affix level; loot rolls affixes "
             "onto the gear it drops from world tier 1 (character level 10) up")

    parts = _parts(root)
    for name, part in parts.items():
        crafting = part.get("crafting", {})
        if part.get("type") == "misc_upgrade" or crafting.get("materials", 0) <= 0:
            continue    # the five upgrades have crafting recipes of their own
        material = _material(part)
        # A part's extras are charged one each (ArmoryPlanRules.extras): a
        # coating's glass bottle, a mace core's heavy core, a setting's bort.
        extras = list(crafting.get("extra", []))
        declared(f"elysiumarmory:{name}",
                 ["elysiumarmory:blueprint", material] + extras,
                 f"{ARMORY}/recipe/ArmoryCompoundPartRecipe.java",
                 "ItemStack out = ArmoryBuilder.part(found.part().id(), found.materials());",
                 f"compound_part: a {name} blueprint and {crafting['materials']} of any material with "
                 f"{part['part_type']} properties ({material} is one)"
                 + "".join(f" and one {extra}" for extra in extras)
                 + " in a crafting grid make the part")

    for name, part in parts.items():
        if part.get("part_type") != "main":
            continue
        declared(f"elysiumarmory:{part['gear_type']}",
                 [f"elysiumarmory:{name}"],
                 f"{ARMORY}/recipe/ArmoryGearAssemblyRecipe.java",
                 "ArmoryBuilder.gear(found.gearType(), found.parts())",
                 f"gear_assembly: the main part decides the gear, so a built {name} alone is a "
                 f"{part['gear_type']}; rod, grip and the rest are optional")

    for gear, vanilla in _CONVERTS.items():
        declared(f"elysiumarmory:{gear}", [vanilla],
                 f"{ARMORY}/recipe/ArmoryConversionRecipe.java",
                 "return ArmoryBuilder.gear(conversion.gearType(), parts);",
                 f"conversion: {vanilla} alone in a crafting grid comes back modular, of the same material")

    # ------------------------------------------------------------------
    # elysiumdungeons
    # ------------------------------------------------------------------
    declared("placed:elysiumdungeons:rift_portal",
             ["elysiumdungeons:rift_key", "elysiumdungeons:rift_frame"],
             f"{DUNGEONS}/item/RiftKeyItem.java",
             "RiftPortal.light(level, frame);",
             "striking a closed ring of rift frames (inside 2x3 to 21x21) with the rift key fills it with portal")

    declared("dimension:elysiumdungeons:dungeon",
             ["elysiumdungeons:rift_key", "elysiumdungeons:rift_frame"],
             f"{DUNGEONS}/level/DungeonTravel.java",
             "teleport(player, dungeonLevel, arrival);",
             "stepping into a lit rift portal allocates a dungeon instance, builds it and moves the player in")

    # The throne room asks the library's bestiary for a BOSS (DungeonRooms.spawnBoss);
    # elysium-mobs registers exactly two.
    declared("creature:elysiummobs:choir", ["dimension:elysiumdungeons:dungeon"],
             "elysium-mobs/src/main/java/com/elysium/mobs/ElysiumMobs.java",
             'bossEntry("choir", ElysiumFaction.UNSWORN, CHOIR);',
             "a dungeon's throne room spawns a bestiary BOSS, and the Choir is one of the two registered")
    declared("creature:elysiummobs:praetor", ["dimension:elysiumdungeons:dungeon"],
             "elysium-mobs/src/main/java/com/elysium/mobs/ElysiumMobs.java",
             'bossEntry("praetor", ElysiumFaction.EMPIRE, PRAETOR);',
             "a dungeon's throne room spawns a bestiary BOSS, and the Praetor is one of the two registered")
