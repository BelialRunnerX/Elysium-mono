#!/usr/bin/env python3
"""
Everything elysium-spawners ships that is data rather than behaviour.

    python3 elysium-spawners/tools/gen_spawners.py

<h2>Why the runes are a table here</h2>

A rune is one fact written down five times: an item registration, a sprite, a
model, a recipe, a modifier file and a pair of language entries. Miss the
modifier file and the rune is an item that does nothing when you click a
spawner with it — which no log reports and no crash reveals. It is found by a
player, holding it, in a cave.

`validate.py` reads the shipped files back and compares them against the table
below, and cross-checks them against the Java that registers the items, so a
rune that exists in one place and not the other is caught rather than shipped.
"""
import json
import os
import pathlib
import sys


def _art_path():
    here = pathlib.Path(__file__).resolve()
    for base in [here.parent] + list(here.parents):
        if (base / "art" / "style.py").is_file():
            return str(base / "art")
    raise SystemExit("cannot find the art package from " + str(here.parent))


sys.path.insert(0, _art_path())

import sprites                                              # noqa: E402

MODULE = pathlib.Path(__file__).resolve().parent.parent
RES = MODULE / "src/main/resources"
NS = "elysiumspawners"

MODIFIERS = RES / f"data/{NS}/elysium_spawner_modifiers"
ENCHANTMENTS = RES / f"data/{NS}/enchantment"
LOOT_MODIFIERS = RES / f"data/{NS}/loot_modifiers"
LOOT = RES / f"data/{NS}/loot_table"
RECIPES = RES / f"data/{NS}/recipe"
TAGS = RES / "data/minecraft/tags"
BLOCKSTATES = RES / f"assets/{NS}/blockstates"
BLOCK_MODELS = RES / f"assets/{NS}/models/block"
ITEM_MODELS = RES / f"assets/{NS}/models/item"
BLOCK_TEXTURES = RES / f"assets/{NS}/textures/block"
ITEM_TEXTURES = RES / f"assets/{NS}/textures/item"
LANG = RES / f"assets/{NS}/lang/en_us.json"
ADVANCEMENTS_DIR = RES / f"data/{NS}/advancement"


# ===========================================================================
# The runes
# ===========================================================================
#
# Eleven, and each one does exactly one thing. Five raise an axis and six
# switch on a behaviour; nothing here does two things, because a rune that made
# a spawner faster *and* quieter would be the rune everybody uses and ten
# nobody does.
#
# `max_steps` is a limit on how far one axis may be pushed, and it is only set
# where the axis has a reason to stop. Haste has one because past twelve steps
# the waiting is already inside the floor and a player would be spending runes
# on nothing; the amounts have none, because "more" is the whole point of them.
#
# (rune, title, stat or flag, max steps, sigil, glow, the ingredient, the line)
RUNES = [
    ("rune_of_haste", "Rune of Haste", "stat:delay", 12, "haste", "plasma",
     "minecraft:sugar",
     "Click a spawner: it waits less. Each one costs more than the last"),
    ("rune_of_swarming", "Rune of Swarming", "stat:count", 0, "swarming", "neural",
     "minecraft:slime_ball",
     "Click a spawner: it sends one more at a time"),
    ("rune_of_crowding", "Rune of Crowding", "stat:nearby", 0, "crowding", "aetherium",
     "minecraft:iron_bars",
     "Click a spawner: it tolerates more of its own standing around"),
    ("rune_of_vigilance", "Rune of Vigilance", "stat:range", 0, "vigilance", "voidglass",
     "minecraft:ender_eye",
     "Click a spawner: it notices a player further off"),
    ("rune_of_scattering", "Rune of Scattering", "stat:spread", 0, "scattering",
     "dimensional", "minecraft:gunpowder",
     "Click a spawner: it puts them further from itself"),

    ("rune_of_indifference", "Rune of Indifference", "flag:ignores_players", 0,
     "indifference", "void", "minecraft:ender_pearl",
     "Click a spawner: it stops caring whether anybody is watching"),
    ("rune_of_darkness", "Rune of Darkness", "flag:ignores_light", 0, "darkness", "void",
     "minecraft:sculk",
     "Click a spawner: it stops caring how bright it is"),
    ("rune_of_tethering", "Rune of Tethering", "flag:persistent", 0, "tethering", "kinetic",
     "minecraft:lead",
     "Click a spawner: what it sends stays, and never despawns"),
    ("rune_of_stillness", "Rune of Stillness", "flag:no_ai", 0, "stillness", "inert",
     "minecraft:cobweb",
     "Click a spawner: what it sends has no mind and does not move"),
    ("rune_of_silence", "Rune of Silence", "flag:silent", 0, "silence", "neural",
     "minecraft:white_wool",
     "Click a spawner: what it sends makes no sound"),
    ("rune_of_obedience", "Rune of Obedience", "flag:redstone", 0, "obedience", "plasma",
     "minecraft:redstone_torch",
     "Click a spawner: it waits for a redstone signal before it does anything"),
]

# The block, and why it has no recipe.
#
# A craftable spawner is a mob farm on day two, and every decision this module
# adds — which spawner to keep, where to put it, what to spend on it — only
# exists because there is a fixed number of them in the world. So it is not
# crafted: it is a dungeon spawner somebody mined with silk touch, and the
# validator knows to expect no recipe file for it.
BLOCK = "spawner"
NO_RECIPE = {BLOCK: "a craftable spawner is a mob farm on day two"}


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(obj, handle, indent=2)
        handle.write("\n")


def kind(spec):
    """('stat', 'delay') or ('flag', 'no_ai')."""
    head, _, tail = spec.partition(":")
    return head, tail


def write_block():
    BLOCK_TEXTURES.mkdir(parents=True, exist_ok=True)
    target = BLOCK_TEXTURES / f"{BLOCK}.png"
    if not target.exists():
        sprites.workstation("owned_spawner", "obsidian", "void").save(target)
    write(BLOCKSTATES / f"{BLOCK}.json",
          {"variants": {"": {"model": f"{NS}:block/{BLOCK}"}}})
    # cube_all_inner_faces, which is vanilla's own spawner model: the cage has
    # to be see-through from the inside or the creature turning in it is behind
    # a wall.
    write(BLOCK_MODELS / f"{BLOCK}.json",
          {"parent": "minecraft:block/cube_all_inner_faces",
           "textures": {"all": f"{NS}:block/{BLOCK}"}})
    write(ITEM_MODELS / f"{BLOCK}.json", {"parent": f"{NS}:block/{BLOCK}"})
    # Deliberately empty, and this is the one file in the repository that is.
    # The spawner's drop is assembled in code because it has to carry both what
    # is in the spawner and what has been done to it, and no loot function can
    # write a block entity's tag into the component that restores it. Two
    # mechanisms producing the same item would be two mechanisms that have to
    # agree.
    write(LOOT / f"blocks/{BLOCK}.json", {"type": "minecraft:block", "pools": []})
    write(TAGS / "block/mineable/pickaxe.json",
          {"replace": False, "values": [f"{NS}:{BLOCK}"]})
    write(TAGS / "block/needs_iron_tool.json",
          {"replace": False, "values": [f"{NS}:{BLOCK}"]})


def write_runes():
    ITEM_TEXTURES.mkdir(parents=True, exist_ok=True)
    for name, _title, spec, max_steps, sigil, glow, ingredient, _why in RUNES:
        target = ITEM_TEXTURES / f"{name}.png"
        if not target.exists():
            sprites.spawner_rune(sigil, glow).save(target)
        write(ITEM_MODELS / f"{name}.json",
              {"parent": "minecraft:item/generated",
               "textures": {"layer0": f"{NS}:item/{name}"}})
        # Two per craft: a spawner takes several of a rune to keep climbing,
        # and a one-for-one recipe would make the fourth step an afternoon.
        write(RECIPES / f"{name}.json",
              {"type": "minecraft:crafting_shaped",
               "category": "misc",
               "pattern": [" o ", "oIo", " o "],
               "key": {"o": {"item": "minecraft:obsidian"}, "I": {"item": ingredient}},
               "result": {"id": f"{NS}:{name}", "count": 2}})
        what, which = kind(spec)
        entry = {"item": f"{NS}:{name}"}
        entry[what] = which
        if max_steps:
            entry["max_steps"] = max_steps
        write(MODIFIERS / f"{name}.json", entry)
    return len(RUNES)


def write_capturing():
    """
    The one enchantment this module adds, and the one piece of Java that reads
    it.

    The enchantment itself is a file like every other enchantment in this
    repository — it can be retuned, moved to another item or turned off by a
    pack. What cannot be data is the effect: there is no effect component for
    "add an item to what this creature drops", so a global loot modifier reads
    the level and does it.
    """
    write(ENCHANTMENTS / "capturing.json",
          {"anvil_cost": 8,
           "description": {"translate": f"enchantment.{NS}.capturing"},
           # No effects: everything it does is in CapturingModifier, because
           # the game has no component for adding to a creature's drops.
           "max_cost": {"base": 65, "per_level_above_first": 9},
           "max_level": 3,
           "min_cost": {"base": 15, "per_level_above_first": 9},
           "slots": ["mainhand"],
           "supported_items": "#minecraft:enchantable/weapon",
           "primary_items": "#minecraft:enchantable/sword",
           "weight": 1})
    write(LOOT_MODIFIERS / "capturing.json", {"type": f"{NS}:capturing", "conditions": []})
    write(RES / "data/neoforge/loot_modifiers/global_loot_modifiers.json",
          {"replace": False, "entries": [f"{NS}:capturing"]})
    # Rare, so the Arcane Table only offers it to a room built for rarity; in
    # a chest and at a villager as well, because a weight of one at a table is
    # a long wait on its own.
    for tag in ("in_enchanting_table", "on_random_loot", "tradeable"):
        write(TAGS / f"enchantment/{tag}.json",
              {"replace": False, "values": [f"{NS}:capturing"]})


def write_unstable_loot():
    """
    What a spawner smashed without silk touch is worth.

    The parts, essentially: the iron of the cage, the redstone that was making
    it count, and — rarely — one of the runes that was holding the pattern
    together. Nothing here is better than picking the spawner up, which is the
    point: this is the consolation prize for the decision a player has already
    made.
    """
    write(LOOT / "unstable_spawner.json",
          {"type": "minecraft:chest",
           "pools": [
               {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4},
                "entries": [
                    {"type": "minecraft:item", "name": "minecraft:iron_nugget", "weight": 8,
                     "functions": [{"function": "minecraft:set_count",
                                    "count": {"type": "minecraft:uniform",
                                              "min": 2, "max": 6}}]},
                    {"type": "minecraft:item", "name": "minecraft:redstone", "weight": 6,
                     "functions": [{"function": "minecraft:set_count",
                                    "count": {"type": "minecraft:uniform",
                                              "min": 1, "max": 4}}]},
                    {"type": "minecraft:item", "name": "minecraft:iron_ingot", "weight": 3},
                    {"type": "minecraft:item", "name": "minecraft:obsidian", "weight": 2},
                    {"type": "minecraft:item", "name": "minecraft:experience_bottle",
                     "weight": 2}]},
               {"rolls": 1,
                "entries": [
                    {"type": "minecraft:empty", "weight": 12},
                    {"type": "minecraft:item", "name": f"{NS}:rune_of_haste", "weight": 1},
                    {"type": "minecraft:item", "name": f"{NS}:rune_of_swarming",
                     "weight": 1},
                    {"type": "minecraft:item", "name": f"{NS}:rune_of_obedience",
                     "weight": 1}]}]})



# ===========================================================================
# Advancements (P12.12)
# ===========================================================================
#
# Five, and every one of them is a thing a player does rather than a number
# they accumulate. The order is the teaching order: a rune exists, a spawner
# can be carried, a rune goes on a spawner, there are six behaviours and not
# one, and something can be caught.
ADVANCEMENT_ROOT_BACKGROUND = "minecraft:textures/block/spawner.png"


def has(item):
    return {"trigger": "minecraft:inventory_changed",
            "conditions": {"items": [{"items": item}]}}


ADVANCEMENTS = [
    ("root", None, f"{NS}:rune_of_haste", "task",
     "Caretaker",
     "A spawner is a thing to invest in rather than a thing to break",
     {"rune": has(f"{NS}:rune_of_haste")}),
    ("carried", "root", f"{NS}:spawner", "goal",
     "Portable Cage",
     "Mine a spawner with a silk touch pickaxe. It comes up holding exactly "
     "what it held",
     {"spawner": has(f"{NS}:spawner")}),
    ("tuned", "carried", f"{NS}:rune_of_swarming", "task",
     "One More Step",
     "Use a rune on a spawner. Each step costs one more than the last",
     {"used": {"trigger": "minecraft:item_used_on_block",
               "conditions": {"location": [
                   {"condition": "minecraft:location_check",
                    "predicate": {"block": {"blocks": f"{NS}:spawner"}}}]}}}),
    ("behaviours", "tuned", f"{NS}:rune_of_stillness", "challenge",
     "Six Ways to Ask",
     "Hold all six of the runes that change what a spawner sends, rather than "
     "how often it sends it",
     {"indifference": has(f"{NS}:rune_of_indifference"),
      "darkness": has(f"{NS}:rune_of_darkness"),
      "tethering": has(f"{NS}:rune_of_tethering"),
      "stillness": has(f"{NS}:rune_of_stillness"),
      "silence": has(f"{NS}:rune_of_silence"),
      "obedience": has(f"{NS}:rune_of_obedience")}),
    ("captured", "root", "minecraft:pig_spawn_egg", "challenge",
     "It Came With Me",
     "Kill something with Capturing and have it leave its own egg behind",
     {"egg": has("#minecraft:spawn_eggs")}),
]


def write_advancements():
    if ADVANCEMENTS_DIR.is_dir():
        for stale in ADVANCEMENTS_DIR.glob("*.json"):
            stale.unlink()
    for name, parent, icon, frame, _title, _description, criteria in ADVANCEMENTS:
        display = {"icon": {"id": icon, "count": 1},
                   "title": {"translate": f"advancements.{NS}.{name}.title"},
                   "description": {"translate": f"advancements.{NS}.{name}.description"},
                   "frame": frame,
                   "show_toast": True,
                   "announce_to_chat": parent is not None}
        if parent is None:
            display["background"] = ADVANCEMENT_ROOT_BACKGROUND
        entry = {"display": display,
                 "criteria": criteria,
                 # Every criterion required: a challenge that is satisfied by
                 # any one of six runes is not the challenge it says it is.
                 "requirements": [[key] for key in criteria]}
        if parent is not None:
            entry["parent"] = f"{NS}:{parent}"
        write(ADVANCEMENTS_DIR / f"{name}.json", entry)
    return len(ADVANCEMENTS)


def write_lang():
    lang = {
        "itemGroup.elysiumspawners": "Elysium Spawners",
        f"block.{NS}.spawner": "Spawner",
        f"enchantment.{NS}.capturing": "Capturing",
        f"enchantment.{NS}.capturing.desc":
            "Sometimes a creature you kill leaves its own egg behind",
        f"{NS}.tooltip.limit": "At most %s of these",
        f"{NS}.refused.cost": "Not enough runes for the next step",
        f"{NS}.refused.enough": "This spawner will take no more of that",
        f"{NS}.refused.already": "This spawner already does that",

        f"{NS}.command.none": "Look at a spawner first",
        f"{NS}.command.header": "This spawner",
        f"{NS}.command.axis": "%s: %s  (%s step(s); the next costs %s)",
        f"{NS}.command.behaviours": "Also: %s",
        f"{NS}.command.spent": "%s rune(s) have gone into it",
        f"{NS}.axis.delay": "Waits",
        f"{NS}.axis.count": "Sends",
        f"{NS}.axis.nearby": "Tolerates",
        f"{NS}.axis.range": "Watches",
        f"{NS}.axis.spread": "Scatters",
    }
    for name, _parent, _icon, _frame, called, description, _criteria in ADVANCEMENTS:
        lang[f"advancements.{NS}.{name}.title"] = called
        lang[f"advancements.{NS}.{name}.description"] = description
    for name, title, _spec, _max, _sigil, _glow, _ingredient, why in RUNES:
        lang[f"item.{NS}.{name}"] = title
        lang[f"{NS}.tooltip.{name}"] = why
    write(LANG, dict(sorted(lang.items())))
    return len(lang)


def main():
    write_block()
    runes = write_runes()
    write_capturing()
    write_unstable_loot()
    earned = write_advancements()
    keys = write_lang()
    print(f"spawners: 1 block (no recipe, by design), {runes} rune(s), "
          f"1 enchantment, {earned} advancement(s), {keys} language key(s)")


if __name__ == "__main__":
    main()
