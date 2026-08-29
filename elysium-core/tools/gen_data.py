#!/usr/bin/env python3
"""Rebuild Elysium's data pack + asset JSON for Minecraft 1.21.1."""
import json
import os
import pathlib
import shutil

def _locate(marker, mod_dir):
    """
    Find a mod's resources under either repo layout.

    The multi-repo work tree has this script at the root with the mod in
    `core/`; the monorepo has it in `elysium-core/tools/` with the mod one
    level up. check_lifecycle.py already handles both for the same reason: a
    script that only knows one layout silently generates nothing in the other,
    and a generator that quietly writes nowhere is the exact failure this file
    spent two sections of FIXES.md on.
    """
    here = pathlib.Path(__file__).resolve()
    for base in [here.parent] + list(here.parents):
        for candidate in (base / mod_dir / "src/main/resources", base / "src/main/resources"):
            if (candidate / marker).is_dir():
                return str(candidate)
    raise SystemExit("cannot find %s under either repo layout - looked from %s"
                     % (mod_dir, here.parent))


# The live tree.
#
# This pointed at work/ - a scratch checkout that stopped being the shipped one
# some time ago - so every run wrote files nobody would ever load while the real
# ones drifted. art/build.py had the same footgun and got the same fix. Derived
# from this file's own location so moving the repo does not silently re-break
# it.
ROOT = _locate("data/elysium", "core")
NS = "elysium"

BLOCKS = [
    "neutronium_ore",
    "neutronium_block",
    "aetherium_ore",
    "voidglass_ore",
    "reforge_table",
    "rune_socket_table",
    "ascension_forge",
]

ELEMENTAL_RUNES = [
    "voidward_rune",
    "plasmaforge_rune",
    "neuralspike_rune",
    "dimensionalshift_rune",
    "kineticsurge_rune",
]

UTILITY_RUNES = [
    "stabilizer_rune",
    "reflex_rune",
    "barrier_rune",
    "plasma_core_rune",
]

WEAPONS = [
    "voidcut_blade",
    "plasma_brand",
    "neural_lash",
    "rift_edge",
    "kinetic_maul",
    "singularity_lance",
    "neural_cascade_rifle",
]

TOOL_MATERIALS = ["voidglass", "aetherium", "neutronium"]
TOOL_SHAPES = ["hammer", "broadaxe", "scythe", "spear"]
TOOLS = [f"{m}_{s}" for s in TOOL_SHAPES for m in TOOL_MATERIALS]

# Rendered from a vanilla template rather than a flat sprite.
TEMPLATE_ITEMS = {
    "imperial_enforcer_spawn_egg": "minecraft:item/template_spawn_egg",
    "unsworn_raider_spawn_egg": "minecraft:item/template_spawn_egg",
}

SIMPLE_ITEMS = [
    "neutronium_ingot",
    "aetherium_ingot",
    "voidglass_ingot",
    "elysium_reforge",
    "imperial_codex",
] + ELEMENTAL_RUNES + UTILITY_RUNES + WEAPONS + TOOLS + [
    "elysium_helmet",
    "plasma_chestplate",
    "voidweave_aegis",
    "neural_leggings",
    "dimensional_boots",
    "emperor_crown",
    "neutronium_helmet",
    "neutronium_chestplate",
    "neutronium_leggings",
    "neutronium_boots",
    "imperial_enforcer_spawn_egg",
    "unsworn_raider_spawn_egg",
]

ORE_DROPS = {
    "neutronium_ore": "neutronium_ingot",
    "aetherium_ore": "aetherium_ingot",
    "voidglass_ore": "voidglass_ingot",
}

SELF_DROPS = [
    "neutronium_block",
    "reforge_table",
    "rune_socket_table",
    "ascension_forge",
]


def write(path, obj):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as handle:
        json.dump(obj, handle, indent=2)
        handle.write("\n")


# ---------------------------------------------------------------------------
# Start from a clean slate for the directories we own.
#
# "Own" is doing work in that sentence, and it is why this script could not
# simply be repointed at the live tree when the drift was first noticed.
# gen_material_gear.py also writes into data/elysium/recipe and
# assets/elysium/models/item, so wiping them here deletes 196 files it wrote.
#
# The answer is ordering rather than cleverness: this runs first and clears,
# then gen_material_gear.py runs and refills, and regen.sh is the one place that
# knows the order so nobody has to remember it. A cleverer clean - deleting only
# files this script is about to rewrite - would leave a renamed file behind
# forever, which is a worse failure than an ordering rule that is written down
# and enforced by a script.
# ---------------------------------------------------------------------------
for stale in ["data", "assets/elysium/models", "assets/elysium/blockstates", "assets/elysium/lang"]:
    shutil.rmtree(os.path.join(ROOT, stale), ignore_errors=True)

# ---------------------------------------------------------------------------
# Blockstates + block models + item models
# ---------------------------------------------------------------------------
for block in BLOCKS:
    write(f"assets/{NS}/blockstates/{block}.json",
          {"variants": {"": {"model": f"{NS}:block/{block}"}}})
    write(f"assets/{NS}/models/block/{block}.json",
          {"parent": "minecraft:block/cube_all",
           "textures": {"all": f"{NS}:block/{block}"}})
    write(f"assets/{NS}/models/item/{block}.json",
          {"parent": f"{NS}:block/{block}"})

for item in SIMPLE_ITEMS:
    if item in TEMPLATE_ITEMS:
        # A spawn egg takes its colours from the item, not from a texture.
        write(f"assets/{NS}/models/item/{item}.json", {"parent": TEMPLATE_ITEMS[item]})
        continue
    # Weapons use handheld, which is what tilts an item into the player's grip
    # instead of leaving it lying flat against the hand.
    parent = ("minecraft:item/handheld" if item in WEAPONS or item in TOOLS
              else "minecraft:item/generated")
    write(f"assets/{NS}/models/item/{item}.json",
          {"parent": parent,
           "textures": {"layer0": f"{NS}:item/{item}"}})

# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------
def title(name):
    return " ".join(part.capitalize() for part in name.split("_"))


lang = {"itemGroup.elysium": "Elysium"}
for block in BLOCKS:
    lang[f"block.{NS}.{block}"] = title(block)
for item in SIMPLE_ITEMS:
    lang[f"item.{NS}.{item}"] = title(item)

lang.update({
    # --- items, named as the Sleeping Empire archive names them ------------
    "item.elysium.elysium_helmet": "Elysium Helm",
    "item.elysium.plasma_chestplate": "Plasma Carapace",
    "item.elysium.voidweave_aegis": "Voidweave Aegis",
    "item.elysium.neural_leggings": "Neural Leggings",
    "item.elysium.dimensional_boots": "Dimensional Boots",
    "item.elysium.emperor_crown": "Emperor's Crown",
    "item.elysium.elysium_reforge": "Elysium Reforge Catalyst",

    "item.elysium.voidward_rune": "Voidward Rune",
    "item.elysium.plasmaforge_rune": "Plasmaforge Rune",
    "item.elysium.neuralspike_rune": "Neuralspike Rune",
    "item.elysium.dimensionalshift_rune": "Dimensionalshift Rune",
    "item.elysium.kineticsurge_rune": "Kineticsurge Rune",
    "item.elysium.stabilizer_rune": "Stabilizer Rune",
    "item.elysium.reflex_rune": "Reflex Rune",
    "item.elysium.barrier_rune": "Barrier Rune",
    "item.elysium.plasma_core_rune": "Plasma Core",

    "item.elysium.voidcut_blade": "Voidcut Blade",
    "item.elysium.plasma_brand": "Plasma Brand",
    "item.elysium.neural_lash": "Neural Lash",
    "item.elysium.rift_edge": "Rift Edge",
    "item.elysium.kinetic_maul": "Kinetic Maul",
    "item.elysium.singularity_lance": "Singularity Lance",
    "item.elysium.neural_cascade_rifle": "Neural Cascade Rifle",

    # --- the /elysium materials report -------------------------------------
    #
    # These five were hand-added to the shipped lang file and were not in this
    # table, so they survived only because this generator had been pointed at a
    # tree nobody shipped. The moment it was pointed at the live one they would
    # have been deleted and the command would have printed raw keys - which is
    # exactly the class of loss a "clean slate" step causes when the table it
    # rebuilds from is not actually complete.
    "elysium.command.materials.header": "Gear materials: %s of %s available",
    "elysium.command.materials.complete": "Every ingot type in this pack has Elysium gear.",
    "elysium.command.materials.uncovered": "%s ingot type(s) in this pack have no Elysium gear:",
    "elysium.command.materials.missing": "%s registered with no ingredient in this pack:",
    "elysium.command.materials.hint": "Add them under [materials] extra_materials in "
                                      "elysium-common.toml, then restart.",

    # --- elements ----------------------------------------------------------
    "elysium.element.void": "Void",
    "elysium.element.plasma": "Plasma",
    "elysium.element.neural": "Neural",
    "elysium.element.dimensional": "Dimensional",
    "elysium.element.kinetic": "Kinetic",
    "elysium.element.none": "Inert",

    # --- Imperial clearance, per the Code of Satisfaction -------------------
    "elysium.clearance.unranked": "Unranked",
    "elysium.clearance.petitioner": "Clearance: Petitioner",
    "elysium.clearance.sanctioned": "Clearance: Sanctioned",
    "elysium.clearance.codified": "Clearance: Codified",
    "elysium.clearance.imperial": "Clearance: Imperial",
    "elysium.clearance.sovereign": "Clearance: Elysomnion's Own",

    # --- tooltips ----------------------------------------------------------
    "elysium.gui.reforge": "Forge",
    "elysium.tooltip.rune_hint": "Socket at a Rune Socket Table",
    "elysium.tooltip.reforge_hint": "Consumed at a Reforge Table to reroll stats",
    "elysium.tooltip.advantage": "+%s%% against %s and %s",
    "elysium.tooltip.resists": "Answers %s and %s",
    "elysium.tooltip.runes": "Runes: %s/%s",
    "elysium.tooltip.aligned": "(aligned)",
    "elysium.tooltip.ability.hammer": "Breaks a 3x3 face",
    "elysium.tooltip.ability.broadaxe": "Fells the whole tree",
    "elysium.tooltip.ability.scythe": "Harvests a 3x3",
    "elysium.tooltip.ability.spear": "Digs a 3x3",
    "elysium.tooltip.reforged": "Reforged \u00b7 %s of %s charges left",

    # --- rune effects ------------------------------------------------------
    "elysium.rune.voidward.effect": "Resistance when badly hurt \u00b7 +2 toughness",
    "elysium.rune.plasmaforge.effect": "Strength while healthy \u00b7 +1.5 attack damage",
    "elysium.rune.neuralspike.effect": "Haste \u00b7 +15% attack speed",
    "elysium.rune.dimensionalshift.effect": "Slow Falling \u00b7 +8% movement speed",
    "elysium.rune.kineticsurge.effect": "Jump Boost \u00b7 +0.1 knockback resistance",
    "elysium.rune.stabilizer.effect": "Health regeneration",
    "elysium.rune.reflex.effect": "+5% chance to avoid a blow",
    "elysium.rune.barrier.effect": "Recharging shield",
    "elysium.rune.plasma_core.effect": "-12% damage from fire and blasts",

    # --- affixes -----------------------------------------------------------
    "elysium.affix.void": "Void Warding %s knockback resistance",
    "elysium.affix.plasma": "Plasma Surge %s attack damage",
    "elysium.affix.neural": "Neural Overclock %s attack speed",
    "elysium.affix.dimensional": "Dimensional Drift %s movement speed",
    "elysium.affix.kinetic": "Kinetic Force %s attack damage",
    "elysium.affix.rune_voidward": "Voidward %s toughness",
    "elysium.affix.rune_plasmaforge": "Plasmaforge %s attack damage",
    "elysium.affix.rune_neuralspike": "Neuralspike %s attack speed",
    "elysium.affix.rune_dimensionalshift": "Dimensionalshift %s movement speed",
    "elysium.affix.rune_kineticsurge": "Kineticsurge %s knockback resistance",

    "material.elysium.neutronium": "Neutronium",
    "material.elysium.aetherium": "Aetherium",

    # --- the Empire's regard ------------------------------------------------
    "entity.elysium.imperial_enforcer": "Imperial Enforcer",
    "item.elysium.imperial_enforcer_spawn_egg": "Imperial Enforcer Spawn Egg",
    "entity.elysium.unsworn_raider": "Unsworn Raider",
    "item.elysium.unsworn_raider_spawn_egg": "Unsworn Raider Spawn Egg",

    "elysium.favor.unknown": "The Empire does not know your name",
    "elysium.favor.recognised": "Favor: Recognised \u2014 the Unsworn are drawn to you",
    "elysium.favor.favoured": "Favor: Favoured",
    "elysium.favor.exalted": "Favor: Exalted",

    "elysium.suspicion.clear": "Your record is clean",
    "elysium.suspicion.noted": "Suspicion: Noted",
    "elysium.suspicion.marked": "Suspicion: Marked \u2014 enforcers dispatched",
    "elysium.suspicion.hunted": "Suspicion: Hunted",

    "elysium.standing.report": "Favor %s \u00b7 %s    Suspicion %s \u00b7 %s",
})


# ---------------------------------------------------------------------------
# Character system: stats, races, classes, passives
# ---------------------------------------------------------------------------
STATS = {
    "vitality": ("Vitality", "Passive health regeneration, and a little more of it to lose"),
    "fortitude": ("Fortitude", "Armour you have without wearing any"),
    "resilience": ("Resilience", "Cuts a share off everything that reaches you"),
    "strength": ("Strength", "Your base damage, before a weapon multiplies it"),
    "agility": ("Agility", "Movement speed"),
    "accuracy": ("Accuracy", "Chance to land a critical hit"),
    "reflexes": ("Reflexes", "Chance to avoid a blow outright"),
    "retribution": ("Retribution", "Sends a share of every blow back at its owner"),
    "intellect": ("Intellect", "Psionic potency: elemental advantage and rune strength"),
    "willpower": ("Willpower", "A shield that rebuilds itself"),
    "luck": ("Luck", "Chance of more out of everything you kill"),
    "presence": ("Presence", "How fast the Empire notices, and how well a reforge rolls"),
}

RACES = {
    "imperial": ("Imperial",
                 "Humanoid. The most common form across the Empire, and the only "
                 "people with no weakness worth naming."),
    "druun": ("Druun",
              "Reptilian, of the Druun Ascendancy. Militaristic and steeply "
              "hierarchical; enormously strong and slow to think."),
    "veylari": ("Veylari",
                "Avian, of the Veylari Concord. Isolationist and technologically "
                "advanced. Precise, clever, and barely armoured."),
    "korrath": ("Korrath",
                "Insectoid, of the Korrath Dominion. Hive-structured and rapidly "
                "expanding. Fast, evasive, and impossible to demoralise."),
    "lumari": ("Lumari",
               "Energy-based, of the Lumari Collective. Advanced, peaceful, and "
               "existing in forms that transcend traditional biology."),
    "unsworn": ("Unsworn",
                "Outside the Code entirely. Lucky, quick, and worth nothing at all "
                "to the Empire."),
}

CLASSES = {
    "medicae": ("Medicae", "Medical Regeneration, practised in the field. The only role that keeps anyone else standing."),
    "factor": ("Factor", "Imperial trade. Does not fight better - simply comes away with more."),
    "artificer": ("Artificer", "Fleet and infrastructure engineering. Keeps gear alive and reforges it better."),
    "enforcer": ("Enforcer", "The fleets' line soldier, and the Code's blunt instrument."),
    "psion": ("Psion", "The psionic elemental system, practised rather than merely carried."),
    "voidrunner": ("Voidrunner", "Cybernetic enhancement, spent entirely on arriving first."),
    "reclaimer": ("Reclaimer", "Singularity-forged neutronium comes from somewhere. This is who is down there getting it."),
    "warden": ("Warden", "Modelled on Sentinel: what stands in the way when nobody is left to give orders."),
    "marksman": ("Marksman", "The long shot, taken from cover, once."),
}

PASSIVES = {
    "sanctioned_answer": ("Sanctioned Answer",
                          "Harm done to an Imperial is answered. Reflects a share of every "
                          "blow, climbing with level toward 100% and never quite arriving: "
                          "9% at level 10, 33% at 50, 50% at 100, 80% at 400."),
    "cold_blood": ("Cold Blood", "Deals up to 60% more damage as your health falls."),
    "lightfeather": ("Lightfeather", "Ignores the first ten blocks of any fall, and two thirds of the rest."),
    "molt": ("Molt", "Regeneration triples after five seconds without being hurt."),
    "photonic": ("Photonic", "Double shield capacity. Takes a third less fire and blast damage, and 15% more of everything else."),
    "uncounted": ("Uncounted", "Suspicion sheds twice as fast. Favor comes half as easily."),
    "triage_field": ("Triage Field", "Heals every player within eight blocks, and yourself half again faster."),
    "profiteer": ("Profiteer", "A further 25% chance of a second drop from anything that pays out."),
    "field_repair": ("Field Repair", "Elysium gear wears a third slower. Reforges roll 50% higher."),
    "sanctioned_force": ("Sanctioned Force", "25% more damage to the Unsworn. Earns 40% less Suspicion."),
    "resonance": ("Resonance", "Psionic potency raised by half: wider elemental advantage, stronger runes."),
    "slipstream": ("Slipstream", "Halves fall damage."),
    "prospector": ("Prospector", "A one in four chance of a second ingot from any Elysium vein."),
    "bulwark": ("Bulwark", "Below half health your whole reflected share is laid over itself."),
    "called_shot": ("Called Shot", "A critical hit deals 125% extra damage instead of 50%."),
}

for _id, (_name, _desc) in STATS.items():
    lang[f"elysium.stat.{_id}"] = _name
    lang[f"elysium.stat.{_id}.desc"] = _desc

for _id, (_name, _desc) in RACES.items():
    lang[f"elysium.race.{_id}"] = _name
    lang[f"elysium.race.{_id}.desc"] = _desc

for _id, (_name, _desc) in CLASSES.items():
    lang[f"elysium.class.{_id}"] = _name
    lang[f"elysium.class.{_id}.desc"] = _desc

for _id, (_name, _desc) in PASSIVES.items():
    lang[f"elysium.passive.{_id}"] = _name
    lang[f"elysium.passive.{_id}.desc"] = _desc

lang.update({
    "item.elysium.imperial_codex": "Imperial Codex",
    "elysium.tooltip.codex": "Right-click to open your character sheet",
    "elysium.tooltip.requires_level": "Requires character level %s",
    "elysium.clearance.beyond": "Clearance: Beyond the Code",

    "elysium.screen.character": "Imperial Codex",
    "elysium.screen.choose": "Declare yourself. This is recorded once.",
    "elysium.screen.confirm": "Enter it in the record",
    "elysium.screen.level": "Level %s",
    "elysium.screen.points": "%s points to assign",

    "elysium.level.up": "Level %s \u00b7 %s points to assign",
    "elysium.character.chosen": "Recorded: %s, %s",
    "elysium.character.unchosen": "Unrecorded",
    "elysium.command.header": "%s \u00b7 %s \u00b7 Level %s",
    "elysium.command.respec": "Every assigned point returned \u2014 %s to spend",
})

os.makedirs(os.path.join(ROOT, f"assets/{NS}/lang"), exist_ok=True)
with open(os.path.join(ROOT, f"assets/{NS}/lang/en_us.json"), "w", encoding="utf-8") as handle:
    json.dump(lang, handle, indent=2, sort_keys=True)
    handle.write("\n")

# ---------------------------------------------------------------------------
# Loot tables  (1.21 folder is singular: loot_table, not loot_tables)
# ---------------------------------------------------------------------------
def ore_loot(block, drop):
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{
                "type": "minecraft:alternatives",
                "children": [
                    {
                        "type": "minecraft:item",
                        "name": f"{NS}:{block}",
                        "conditions": [{
                            "condition": "minecraft:match_tool",
                            "predicate": {
                                "predicates": {
                                    "minecraft:enchantments": [{
                                        "enchantments": "minecraft:silk_touch",
                                        "levels": {"min": 1}
                                    }]
                                }
                            }
                        }]
                    },
                    {
                        "type": "minecraft:item",
                        "name": f"{NS}:{drop}",
                        "functions": [
                            {
                                "function": "minecraft:apply_bonus",
                                "enchantment": "minecraft:fortune",
                                "formula": "minecraft:ore_drops"
                            },
                            {"function": "minecraft:explosion_decay"}
                        ]
                    }
                ]
            }]
        }]
    }


def self_loot(block):
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{block}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]
        }]
    }


# The enforcer's base table. Without a loot table at the path derived from the
# entity id, Minecraft logs a missing-table warning every time one dies; the
# Favor-scaled extras are added on top of this by ElysiumLootHandler.
write(f"data/{NS}/loot_table/entities/imperial_enforcer.json", {
    "type": "minecraft:entity",
    "pools": [
        {
            "rolls": 1,
            "entries": [{
                "type": "minecraft:item",
                "name": "minecraft:rotten_flesh",
                "functions": [
                    {"function": "minecraft:set_count",
                     "count": {"type": "minecraft:uniform", "min": 0.0, "max": 2.0}},
                    # Renamed in 1.21, and the new function will not assume
                    # which enchantment it scales with - naming it is required.
                    {"function": "minecraft:enchanted_count_increase",
                     "count": {"type": "minecraft:uniform", "min": 0.0, "max": 1.0},
                     "enchantment": "minecraft:looting"}
                ]
            }]
        },
        {
            "rolls": 1,
            "conditions": [{"condition": "minecraft:random_chance", "chance": 0.35}],
            "entries": [{"type": "minecraft:item", "name": f"{NS}:neutronium_ingot"}]
        }
    ]
})

write(f"data/{NS}/loot_table/entities/unsworn_raider.json", {
    "type": "minecraft:entity",
    "pools": [
        {
            "rolls": 1,
            "entries": [{
                "type": "minecraft:item",
                "name": "minecraft:rotten_flesh",
                "functions": [
                    {"function": "minecraft:set_count",
                     "count": {"type": "minecraft:uniform", "min": 0.0, "max": 2.0}},
                    # Renamed in 1.21, and the new function will not assume
                    # which enchantment it scales with - naming it is required.
                    {"function": "minecraft:enchanted_count_increase",
                     "count": {"type": "minecraft:uniform", "min": 0.0, "max": 1.0},
                     "enchantment": "minecraft:looting"}
                ]
            }]
        },
        {
            "rolls": 1,
            "conditions": [{"condition": "minecraft:random_chance", "chance": 0.35}],
            "entries": [{"type": "minecraft:item", "name": f"{NS}:voidglass_ingot"}]
        }
    ]
})

for block, drop in ORE_DROPS.items():
    write(f"data/{NS}/loot_table/blocks/{block}.json", ore_loot(block, drop))
for block in SELF_DROPS:
    write(f"data/{NS}/loot_table/blocks/{block}.json", self_loot(block))

# ---------------------------------------------------------------------------
# Recipes  (1.21 folder is singular: recipe. Result key is "id", not "item".)
#
# An Ingredient is an *object* — {"item": ...} or {"tag": ...} — or an array of
# them. It is never a bare id string. That was legal through 1.20 and this file
# assumed it still was, so every one of the 229 recipes written here failed to
# load with
#
#     Map entry '#' : Failed to parse either.
#     First: Not a json array: "elysium:neutronium_ingot";
#     Second: Not a JSON object: "elysium:neutronium_ingot"
#
# and the whole mod's crafting quietly did not exist. Callers below still pass
# plain strings, which read far better in a recipe table; ing() is the single
# place that turns one into the shape the codec wants, so the mistake cannot be
# made once per recipe again.
# ---------------------------------------------------------------------------
def ing(value):
    """A bare id (or a list of them) as an Ingredient object."""
    if isinstance(value, (list, tuple)):
        return [ing(each) for each in value]
    if not isinstance(value, str):
        return value          # already an object; leave it alone
    if value.startswith("#"):
        return {"tag": value[1:]}
    # The common-tag namespace: c:ingots/tin is a tag, and writing it as an
    # item would mean the recipe only works when nothing provides the tag.
    if value.split(":", 1)[0] == "c":
        return {"tag": value}
    return {"item": value}


def shaped(pattern, key, result, count=1, category="misc"):
    return {
        "type": "minecraft:crafting_shaped",
        "category": category,
        "pattern": pattern,
        "key": {symbol: ing(value) for symbol, value in key.items()},
        "result": {"id": result, "count": count},
    }


def shapeless(ingredients, result, count=1, category="misc"):
    return {
        "type": "minecraft:crafting_shapeless",
        "category": category,
        "ingredients": [ing(each) for each in ingredients],
        "result": {"id": result, "count": count},
    }


ING = f"{NS}:neutronium_ingot"
AET = f"{NS}:aetherium_ingot"
VOID = f"{NS}:voidglass_ingot"

write(f"data/{NS}/recipe/neutronium_block.json",
      shaped(["###", "###", "###"], {"#": ING}, f"{NS}:neutronium_block", category="building"))

write(f"data/{NS}/recipe/neutronium_ingot_from_block.json",
      shapeless([f"{NS}:neutronium_block"], ING, 9))

write(f"data/{NS}/recipe/neutronium_helmet.json",
      shaped(["###", "# #"], {"#": ING}, f"{NS}:neutronium_helmet", category="equipment"))
write(f"data/{NS}/recipe/neutronium_chestplate.json",
      shaped(["# #", "###", "###"], {"#": ING}, f"{NS}:neutronium_chestplate", category="equipment"))
write(f"data/{NS}/recipe/neutronium_leggings.json",
      shaped(["###", "# #", "# #"], {"#": ING}, f"{NS}:neutronium_leggings", category="equipment"))
write(f"data/{NS}/recipe/neutronium_boots.json",
      shaped(["# #", "# #"], {"#": ING}, f"{NS}:neutronium_boots", category="equipment"))

write(f"data/{NS}/recipe/elysium_reforge.json",
      shaped([" a ", "ava", " a "], {"a": AET, "v": VOID}, f"{NS}:elysium_reforge"))

write(f"data/{NS}/recipe/reforge_table.json",
      shaped(["###", "iai", "iii"],
             {"#": ING, "i": "minecraft:iron_block", "a": "minecraft:anvil"},
             f"{NS}:reforge_table", category="building"))

write(f"data/{NS}/recipe/rune_socket_table.json",
      shaped(["vvv", "i#i", "iii"],
             {"v": VOID, "#": ING, "i": "minecraft:obsidian"},
             f"{NS}:rune_socket_table", category="building"))

write(f"data/{NS}/recipe/ascension_forge.json",
      shaped(["aaa", "i#i", "iii"],
             {"a": AET, "#": ING, "i": "minecraft:obsidian"},
             f"{NS}:ascension_forge", category="building"))

# --- runes -----------------------------------------------------------------
# Runes had no recipes at all, so the whole socket system was creative-only.
# Four voidglass around a catalyst that suits the effect; every catalyst is
# distinct, so no two rune recipes collide.
RUNE_CATALYSTS = {
    "voidward_rune": "minecraft:ender_pearl",
    "plasmaforge_rune": "minecraft:blaze_rod",
    "neuralspike_rune": "minecraft:amethyst_shard",
    "dimensionalshift_rune": "minecraft:chorus_fruit",
    "kineticsurge_rune": "minecraft:slime_ball",
    "stabilizer_rune": "minecraft:glistering_melon_slice",
    "reflex_rune": "minecraft:rabbit_foot",
    "barrier_rune": "minecraft:shield",
    "plasma_core_rune": "minecraft:magma_cream",
}

for rune_name, catalyst in RUNE_CATALYSTS.items():
    write(f"data/{NS}/recipe/{rune_name}.json",
          shaped([" v ", "vcv", " v "], {"v": VOID, "c": catalyst}, f"{NS}:{rune_name}"))

# --- area tools -------------------------------------------------------------
# Deliberately plain: the material across the top, sticks down the middle.
# Every shape is a distinct pattern, and none of them collide with a vanilla
# recipe, so the three material variants are just the same shape in a
# different metal.
TOOL_INGOTS = {
    "voidglass": VOID,
    "aetherium": AET,
    "neutronium": ING,
}

TOOL_PATTERNS = {
    "hammer":   ["mmm", "msm", " s "],
    "broadaxe": ["mmm", "ms ", " s "],
    "scythe":   ["mmm", "  s", "  s"],
    "spear":    ["  m", " s ", " s "],
}

for shape, pattern in TOOL_PATTERNS.items():
    for material, ingot in TOOL_INGOTS.items():
        write(f"data/{NS}/recipe/{material}_{shape}.json",
              shaped(pattern,
                     {"m": ingot, "s": "minecraft:stick"},
                     f"{NS}:{material}_{shape}",
                     category="equipment"))

# --- weapons ---------------------------------------------------------------
# The matching rune is part of the recipe: an element is a commitment, not a
# free label, and it competes with socketing that rune into armour.
WEAPON_RECIPES = {
    "voidcut_blade": ([" v ", " r ", " n "], {"v": VOID, "r": f"{NS}:voidward_rune", "n": ING}),
    "plasma_brand": ([" v ", " r ", " n "], {"v": VOID, "r": f"{NS}:plasmaforge_rune", "n": ING}),
    "neural_lash": ([" v ", " r ", " n "], {"v": VOID, "r": f"{NS}:neuralspike_rune", "n": ING}),
    "rift_edge": ([" v ", " r ", " n "], {"v": VOID, "r": f"{NS}:dimensionalshift_rune", "n": ING}),
    "kinetic_maul": (["vv ", "vr ", " n "], {"v": VOID, "r": f"{NS}:kineticsurge_rune", "n": ING}),
    "singularity_lance": (["  a", " a ", "nr "],
                          {"a": AET, "n": ING, "r": f"{NS}:dimensionalshift_rune"}),
    "neural_cascade_rifle": (["aa ", "nr ", "n  "],
                             {"a": AET, "n": ING, "r": f"{NS}:neuralspike_rune"}),
}

for weapon_name, (pattern, keys) in WEAPON_RECIPES.items():
    write(f"data/{NS}/recipe/{weapon_name}.json",
          shaped(pattern, keys, f"{NS}:{weapon_name}", category="equipment"))

# --- elemental armour -------------------------------------------------------
# These five pieces were creative-only: no recipe, no drop. Which quietly made
# the whole defensive half of the counter matrix, reforging and ascension
# unreachable in survival, since all three operate on Elysium armour.
#
# Same bargain as the weapons: the matching rune is part of the recipe, so an
# affinity costs you the rune you could have socketed instead. Plasma and
# Neural have no material of their own, so their pieces are voidglass — the
# same alloy the elemental blades are cut from.
ARMOUR_RECIPES = {
    "elysium_helmet": (["vvv", "vrv"],
                       {"v": VOID, "r": f"{NS}:voidward_rune"}),
    "plasma_chestplate": (["v v", "vrv", "vvv"],
                          {"v": VOID, "r": f"{NS}:plasmaforge_rune"}),
    "neural_leggings": (["vvv", "vrv", "v v"],
                        {"v": VOID, "r": f"{NS}:neuralspike_rune"}),
    "dimensional_boots": (["a a", "ara"],
                          {"a": AET, "r": f"{NS}:dimensionalshift_rune"}),
}

for armour_name, (pattern, keys) in ARMOUR_RECIPES.items():
    write(f"data/{NS}/recipe/{armour_name}.json",
          shaped(pattern, keys, f"{NS}:{armour_name}", category="equipment"))

# The Emperor's Crown is deliberately not here. It is Elysomnion's own, and it
# comes off an Imperial Enforcer's body once Suspicion reaches Hunted — see
# ElysiumLootHandler.

# --- the archive's flagship chestplate --------------------------------------
write(f"data/{NS}/recipe/voidweave_aegis.json",
      shaped(["v v", "vnv", "vvv"],
             {"v": VOID, "n": f"{NS}:neutronium_block"},
             f"{NS}:voidweave_aegis", category="equipment"))

# ---------------------------------------------------------------------------
# Block tags  (1.21 folder is singular: tags/block)
# ---------------------------------------------------------------------------
write("data/minecraft/tags/block/mineable/pickaxe.json",
      {"replace": False, "values": [f"{NS}:{b}" for b in BLOCKS]})

write("data/minecraft/tags/block/needs_iron_tool.json",
      {"replace": False, "values": [f"{NS}:aetherium_ore", f"{NS}:voidglass_ore"]})

write("data/minecraft/tags/block/needs_diamond_tool.json",
      {"replace": False,
       "values": [f"{NS}:reforge_table", f"{NS}:rune_socket_table", f"{NS}:ascension_forge"]})

write("data/minecraft/tags/block/needs_netherite_tool.json",
      {"replace": False, "values": [f"{NS}:neutronium_ore", f"{NS}:neutronium_block"]})

# NeoForge reads this tag to decide what a netherite pickaxe can harvest.
write("data/neoforge/tags/block/needs_netherite_tool.json",
      {"replace": False, "values": [f"{NS}:neutronium_ore", f"{NS}:neutronium_block"]})

# ---------------------------------------------------------------------------
# Worldgen
# ---------------------------------------------------------------------------
ORE_SETTINGS = {
    # name: (size, count, min_y, max_y, replaceable tag)
    "neutronium_ore": (5, 3, -64, 16),
    "aetherium_ore": (8, 8, -32, 64),
    "voidglass_ore": (6, 5, -48, 32),
}

for name, (size, count, min_y, max_y) in ORE_SETTINGS.items():
    write(f"data/{NS}/worldgen/configured_feature/{name}.json", {
        "type": "minecraft:ore",
        "config": {
            "size": size,
            "discard_chance_on_air_exposure": 0.0,
            "targets": [
                {
                    "target": {
                        "predicate_type": "minecraft:tag_match",
                        "tag": "minecraft:stone_ore_replaceables"
                    },
                    "state": {"Name": f"{NS}:{name}"}
                },
                {
                    "target": {
                        "predicate_type": "minecraft:tag_match",
                        "tag": "minecraft:deepslate_ore_replaceables"
                    },
                    "state": {"Name": f"{NS}:{name}"}
                }
            ]
        }
    })

    # A placed feature POINTS AT a configured feature - it does not restate it.
    # The old files pasted the ore config in here verbatim and left out the
    # "feature" field entirely, so every one of them failed to parse on load.
    write(f"data/{NS}/worldgen/placed_feature/{name}.json", {
        "feature": f"{NS}:{name}",
        "placement": [
            {"type": "minecraft:count", "count": count},
            {"type": "minecraft:in_square"},
            {
                "type": "minecraft:height_range",
                "height": {
                    "type": "minecraft:uniform",
                    "min_inclusive": {"absolute": min_y},
                    "max_inclusive": {"absolute": max_y}
                }
            },
            {"type": "minecraft:biome"}
        ]
    })

    # Without a biome modifier nothing ever places the feature into a biome,
    # which is why none of these ores generated before.
    write(f"data/{NS}/neoforge/biome_modifier/add_{name}.json", {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": f"{NS}:{name}",
        "step": "underground_ores"
    })

# ---------------------------------------------------------------------------
# Silent Gear material definitions (datapack route, no code dependency)
#
# The 1.21 format is not the 1.16-1.20 format, and the mod shipped the older
# one: a flat "stats" block, "colors"/"tier"/"categories" at the root, an
# ingredient written as {"item": ...}, and the whole thing filed under
# data/silentgear/materials/. Silent Gear 1.21 reads
# data/<namespace>/silentgear_materials/ instead, so those files were never
# even opened - the integration had been quietly doing nothing.
#
# Rewritten against the wiki's own 1.21 example. Only fields that appear in
# that example are used: "mining_speed" is now "harvest_speed",
# "armor_toughness" is gone, armour is split per slot, and the mining tier is
# an object rather than an integer.
#
# The wiki example was not enough, and cost two launches to find out. The
# "crafting" block has five REQUIRED keys, and the example only shows three:
#
#     can_salvage  categories  gear_type_blacklist  ingredient  part_substitutes
#
# The two extra ones have no sensible default in the codec even though they
# have an obvious empty value, so a material without them throws exactly like a
# material with a malformed ingredient. This list is not from the wiki: it is
# every key present in all 132 of Silent Gear 4.2.1.1's own material files,
# read out of the mod jar. Where a document and the shipped artifact disagree,
# the artifact is the specification.
# ---------------------------------------------------------------------------
def silentgear_material(name, colour, ingredient, durability, armour, damage,
                        speed, harvest, enchantability, rarity, tier_name,
                        tier_hint, incorrect_tag):
    """One material, in the 1.21 shape."""
    return {
        "type": "silentgear:simple",
        "parent": "silentgear:empty",
        "crafting": {
            "can_salvage": True,
            # Silent Gear reads this through the vanilla Ingredient codec, so
            # it is an object like any other ingredient. Passing the bare
            # string here did not merely fail to load the material: Silent Gear
            # throws MaterialJsonException out of its reload listener, which
            # crashed the client the moment the player clicked Create New World.
            "categories": ["metal"],
            # Required, with no default. Empty means "usable in every gear
            # type", which is what these two materials want - but leaving the
            # key out is a hard parse failure, not the empty case.
            "gear_type_blacklist": [],
            "ingredient": ing(ingredient),
            # Required, same story. A substitute lets one material be crafted
            # from a different item for a specific part - iron rods for an iron
            # rod, say. Neither Elysium material has one.
            "part_substitutes": {},
        },
        "display": {
            "color": colour,
            "main_texture_type": "HIGH_CONTRAST",
            "name": {"translate": f"material.elysium.{name}"},
        },
        "properties": {
            "silentgear:main": {
                "durability": float(durability),
                "armor_durability": round(durability / 80.0, 1),
                # Armour is a total plus a per-slot split, as vanilla iron is.
                "armor": float(armour),
                "armor/helmet": round(armour * 0.15, 1),
                "armor/chestplate": round(armour * 0.40, 1),
                "armor/leggings": round(armour * 0.32, 1),
                "armor/boots": round(armour * 0.13, 1),
                "magic_armor": round(armour * 0.30, 1),
                "attack_damage": float(damage),
                "attack_speed": float(speed),
                "harvest_speed": float(harvest),
                "enchantment_value": float(enchantability),
                "rarity": float(rarity),
                "harvest_tier": {
                    "name": tier_name,
                    "level_hint": tier_hint,
                    "incorrect_blocks_for_tool": incorrect_tag,
                },
            }
        },
    }


write("data/elysium/silentgear_materials/neutronium.json", silentgear_material(
    "neutronium", "#FF4A4A4A", f"{NS}:neutronium_ingot",
    durability=2500, armour=22.0, damage=5.0, speed=0.0, harvest=12.0,
    enchantability=25, rarity=80,
    tier_name="netherite", tier_hint="4",
    incorrect_tag="minecraft:incorrect_for_netherite_tool"))

write("data/elysium/silentgear_materials/aetherium.json", silentgear_material(
    "aetherium", "#FF7FD4E8", f"{NS}:aetherium_ingot",
    durability=1800, armour=17.0, damage=3.5, speed=0.2, harvest=10.0,
    enchantability=22, rarity=60,
    tier_name="diamond", tier_hint="3",
    incorrect_tag="minecraft:incorrect_for_diamond_tool"))

print("data + asset JSON regenerated")
