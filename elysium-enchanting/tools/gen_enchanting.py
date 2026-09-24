#!/usr/bin/env python3
"""
Everything elysium-enchanting ships that is data rather than behaviour.

    python3 elysium-enchanting/tools/gen_enchanting.py

<h2>Why the shelves are a table here</h2>

A stat block is one fact written down twice: a file saying what the block is
worth, and a language entry nobody reads. What makes it worth generating is
that the *set* has to be balanced against itself, and a set of thirty files
cannot be read as a set. Here it is one table, sorted into the four things a
room can be built for, and a glance down it is the balance pass.

``validate.py`` reads the shipped files back and compares them against this
table, so editing a generated file by hand is caught rather than quietly
regenerated away on the next run.

<h2>What the numbers mean</h2>

  * ``eterna``        — power. The levels the table offers are made of it.
  * ``quanta``        — uncertainty, in both directions.
  * ``arcana``        — how rare the enchantments on the table may be.
  * ``rectification`` — the share of quanta's downside that is removed.
  * ``max_eterna``    — the cap this block raises the *room* to. Not added up:
    the room's cap is the best single cap in it, so the answer to every
    question is never one more bookshelf.

A player's own ``max_eterna`` attribute is the other half of the cap, and the
table holds whichever is smaller. That is why the ordinary shelves stop well
under the attribute's base of fifty and three blocks pass it: the first
half of the game is spent raising the room, and the second half is spent
earning the right to stand in it.
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
NS = "elysiumenchanting"

STATS = RES / f"data/{NS}/elysium_enchanting_stats"
RECIPES = RES / f"data/{NS}/recipe"
LOOT = RES / f"data/{NS}/loot_table/blocks"
TAGS = RES / "data/minecraft/tags/block"
BLOCKSTATES = RES / f"assets/{NS}/blockstates"
BLOCK_MODELS = RES / f"assets/{NS}/models/block"
ITEM_MODELS = RES / f"assets/{NS}/models/item"
TEXTURES = RES / f"assets/{NS}/textures/block"
ITEM_TEXTURES = RES / f"assets/{NS}/textures/item"
LANG = RES / f"assets/{NS}/lang/en_us.json"
VANILLA = MODULE / "tools/vanilla_enchantments"
ENCHANTMENTS = RES / "data/minecraft/enchantment"
OURS = RES / f"data/{NS}/enchantment"
OUR_TAGS = RES / f"data/{NS}/tags/enchantment"
ADVANCEMENTS_DIR = RES / f"data/{NS}/advancement"
ENCH_TAGS = RES / "data/minecraft/tags/enchantment"


# ===========================================================================
# The shelves
# ===========================================================================
#
# (block, eterna, quanta, arcana, rectification, max_eterna, why)
#
# Read down a column rather than across a row: each family answers one of the
# four questions well and the others barely at all, which is what makes a room
# a choice. A player who wants both builds a bigger room, and a player who
# wants both *now* has to decide which wall to give up.
SHELVES = [
    # -- paper and wood: power, cheaply, with a low ceiling -----------------
    #
    # The opening move, and deliberately still the opening move: a wall of
    # bookshelves is what a player already knows how to build, and taking that
    # away to teach a new system would be a worse first hour rather than a
    # better one. What changed is where it stops.
    ("minecraft:bookshelf", 2.0, 0.0, 0.0, 0.0, 15.0,
     "the wall everybody already builds"),
    ("minecraft:chiseled_bookshelf", 1.0, 0.75, 0.0, 0.0, 15.0,
     "a shelf you can take books out of, and it reads as unsettled"),
    ("minecraft:lectern", 1.5, 0.0, 0.5, 0.0, 18.0,
     "one book, open, which is worth more than six closed"),
    ("minecraft:cartography_table", 0.5, 0.0, 0.75, 0.0, 15.0,
     "somewhere to work out where the rare thing is"),
    ("minecraft:enchanting_table", 3.0, 0.0, 1.0, 0.0, 20.0,
     "the old table, which is a component rather than a competitor"),

    # -- the deep places: uncertainty ---------------------------------------
    #
    # Quanta is free power for anybody willing to be wrong sometimes, so
    # everything here is cheap and most of it is unpleasant to live next to.
    ("minecraft:magma_block", 0.0, 0.75, 0.0, 0.0, 0.0,
     "heat, and nothing about it is steady"),
    ("minecraft:crying_obsidian", 1.0, 1.0, 0.0, 0.0, 22.0,
     "something the world is doing rather than something you did"),
    ("minecraft:sculk_catalyst", 0.0, 2.0, 1.0, 0.0, 0.0,
     "the deep dark's own arithmetic, which nobody has checked"),
    ("minecraft:sculk_sensor", 0.0, 1.0, 0.0, 0.0, 0.0,
     "it listens, and a table that is listened to is not a private one"),
    ("minecraft:soul_lantern", 0.5, 0.5, 0.0, 0.0, 0.0,
     "light with something in it"),
    ("minecraft:respawn_anchor", 0.0, 1.5, 1.0, 0.0, 25.0,
     "a block that already does something the game will not explain"),

    # -- the crystal: rarity ------------------------------------------------
    #
    # Arcana never makes a rare enchantment certain — it makes it possible —
    # so a room built entirely for it is a room that offers strange things
    # weakly, and that is a real build rather than a mistake.
    ("minecraft:amethyst_block", 0.0, 0.0, 1.0, 0.0, 20.0,
     "the geode's plain wall"),
    ("minecraft:budding_amethyst", 0.0, 0.25, 2.5, 0.0, 28.0,
     "the one that is still doing it, and it cannot be moved twice"),
    ("minecraft:amethyst_cluster", 0.0, 0.0, 1.5, 0.0, 0.0,
     "grown rather than placed"),
    ("minecraft:end_rod", 0.5, 0.0, 1.0, 0.0, 0.0,
     "light from somewhere that is not here"),
    ("minecraft:chorus_flower", 0.0, 0.5, 1.5, 0.0, 0.0,
     "it grows towards nothing in particular"),
    ("minecraft:dragon_egg", 0.0, 1.0, 5.0, 0.0, 45.0,
     "there is one, and everybody knows whose it was"),

    # -- order: rectification, which is the hardest of the four -------------
    #
    # Nothing here is early and nothing here gives much. Full rectification
    # takes a room with several of these in it, which is the point: the reward
    # for it is keeping all of quanta's upside and none of its risk, and that
    # should be the last thing a player earns rather than the first.
    ("minecraft:bell", 0.0, 0.0, 0.0, 0.04, 0.0,
     "something that is the same note every time"),
    ("minecraft:sea_lantern", 1.0, 0.0, 0.0, 0.02, 0.0,
     "steady light, which is rarer than it sounds"),
    ("minecraft:conduit", 0.0, 1.0, 0.0, 0.08, 55.0,
     "it keeps its own weather"),
    ("minecraft:beacon", 1.0, 0.0, 0.0, 0.10, 60.0,
     "a straight line, held, for as long as you feed it"),
    ("minecraft:lodestone", 0.0, 0.0, 0.0, 0.06, 30.0,
     "the one block in the game whose whole job is not being wrong"),
    ("minecraft:calibrated_sculk_sensor", 0.0, 0.0, 0.5, 0.05, 0.0,
     "the same listening, told what to listen for"),

    # -- wealth: the ceiling ------------------------------------------------
    #
    # Mostly caps and very little else, so a rich room is a room that can hold
    # what a built room supplies rather than one that supplies it. A wall of
    # netherite with nothing else in it enchants nothing at all.
    ("minecraft:copper_block", 0.0, 0.0, 0.0, 0.0, 18.0, "the first one that is not stone"),
    ("minecraft:lapis_block", 1.0, 0.0, 0.5, 0.0, 22.0, "what the table already spends"),
    ("minecraft:gold_block", 0.0, 0.0, 1.0, 0.0, 26.0, "soft, and it has never minded magic"),
    ("minecraft:emerald_block", 0.0, 0.0, 1.5, 0.0, 30.0, "somebody else's idea of value"),
    ("minecraft:diamond_block", 1.0, 0.0, 0.0, 0.0, 35.0, "the honest one"),
    ("minecraft:netherite_block", 1.0, 0.0, 0.0, 0.05, 50.0, "it does not burn and it does not move"),
]

# The three blocks whose cap passes what a player starts able to hold. They are
# the reason the attribute is worth raising, and validate.py checks the list is
# not empty: a mod where the ceiling is never the player's is a mod with an
# attribute nobody can feel.
BEYOND_BASE = [row[0] for row in SHELVES if row[5] > 50.0]


# ===========================================================================
# The ladder (P10.6 and P10.7)
# ===========================================================================
#
# What a room is worth is only half of enchanting. The other half is what
# there is to spend it on, and vanilla's answer stops at Sharpness V — a
# ceiling reached in the first evening and never moved again. A table that can
# offer level fifty and an enchantment list whose top rung is level five is a
# table with nothing to sell.
#
# So the vanilla enchantments are re-laid rather than replaced. Each row here
# says how many rungs an enchantment should have; the *file* is vanilla's own,
# read out of tools/vanilla_enchantments/ and rewritten with three things
# changed and nothing else:
#
#   max_level  — from this table.
#   min_cost   — the same base, re-paced so the top rung sits at TOP_POWER.
#   max_cost   — the same base, widened when a step outgrew the band.
#
# The description, the effects, the slots, what it may go on, its weight and
# its anvil cost all come through untouched. Raising Sharpness to ten and
# quietly changing what Sharpness *does* would be a change nobody asked for.
#
# Nothing is listed that would be nonsense with a second rung: Silk Touch,
# Mending, Infinity, Channeling, Multishot and the three curses are all
# statements rather than amounts, and an enchantment whose level means nothing
# is a level nobody should be sold.

# The power a room built to the best cap in the game can offer. Deliberately
# under the fifty a player starts able to hold: the last rung of the ladder
# should be reachable by a completed room rather than only by one whose owner
# has raised their own ceiling as well.
TOP_POWER = 45

# What the band between a level's cheapest and dearest power must be worth at
# least, over the step between rungs. A band narrower than a step leaves power
# values that reach no level at all, which reads to a player as a table that
# sometimes offers nothing for no reason.
BAND_OVER_STEP = 5

# (enchantment, new max level, why)
LADDER = [
    # -- what a weapon does -------------------------------------------------
    ("sharpness", 10, "the ladder everything else is measured against"),
    ("smite", 10, "the same ladder, for one kind of thing"),
    ("bane_of_arthropods", 10, "and for another"),
    ("impaling", 8, "a trident is a weapon that already asks for a build"),
    ("density", 10, "the mace's own damage ladder, and it climbs with the fall"),
    ("breach", 6, "armour is a share, so going through it has to keep up"),
    ("knockback", 4, "four is as far as this goes before it is a catapult"),
    ("fire_aspect", 4, "longer, not hotter"),
    ("looting", 5, "two rungs past vanilla, because the drops are a share"),
    ("sweeping_edge", 5, "a crowd is what this is for and crowds got larger"),

    # -- what a bow does ----------------------------------------------------
    ("power", 10, "the bow's Sharpness, and it was always the shorter ladder"),
    ("punch", 4, "matched to Knockback so the two weapons answer alike"),
    ("piercing", 6, "a crossbow bolt through six is a build, not an accident"),
    ("quick_charge", 5, "the last rung is a crossbow that is simply ready"),
    ("loyalty", 5, "it comes back faster, which is all it has ever done"),
    ("riptide", 5, "further, in the rain, which is the whole joke"),

    # -- what a tool does ---------------------------------------------------
    ("efficiency", 10, "squared per level, so this is the one that runs away"),
    ("fortune", 5, "two more rungs, and a share rather than a multiplier"),
    ("unbreaking", 5, "durability is the one thing a long run always wants"),
    ("luck_of_the_sea", 5, "a fishing rod deserves a late game too"),
    ("lure", 5, "and something to do while it has one"),

    # -- what armour does ---------------------------------------------------
    ("protection", 8, "the share is capped by the game; the rungs are not"),
    ("fire_protection", 8, "matched, so no one kind of harm is the easy one"),
    ("blast_protection", 8, "matched"),
    ("projectile_protection", 8, "matched"),
    ("feather_falling", 8, "the fall is the one that kills a stocked player"),
    ("respiration", 5, "two more lungfuls"),
    ("thorns", 5, "answering back, and it costs durability to do it"),
    ("frost_walker", 4, "a wider path across the same water"),
    ("soul_speed", 5, "the Nether's own road, and it is still treasure"),
    ("swift_sneak", 5, "the deep dark asks for this and then asks for more"),
]

# Treasure that a room built for rarity can reach.
#
# Vanilla keeps these off the table entirely, which makes them a trade or a
# chest and nothing else. Here they are on the table and gated by arcana:
# every one has a weight of one or two, and EnchantingRules.available only
# lets a weight that low through to a room built hard for rarity. A player who
# wants Mending without trading has something to build.
FROM_THE_TABLE = ["mending", "frost_walker", "soul_speed", "swift_sneak", "wind_burst"]

# ===========================================================================
# The new enchantments (P10.8)
# ===========================================================================
#
# Twenty, and all of them data.
#
# In 1.21 an enchantment is a file: what it does is a list of effect
# components the game already knows how to run, so an enchantment that can be
# said in that vocabulary needs no Java at all. Everything below is said in
# it. That is not a shortcut — it is the difference between an enchantment
# another pack can copy, retune or turn off, and one that is welded into a jar.
#
# What it rules out is what shaped the list. There is no condition for "the
# wearer is nearly dead", because an entity predicate cannot read health; so
# the conditional ones are built on what it *can* read — stance, light, what
# you are standing in, what you are holding. Those turn out to be better
# enchantments anyway: a player can see the condition they are meeting.
#
# Each is one of a handful of shapes:
#
#   damage / damage_protection  a number, under a condition
#   post_attack                 something that happens to one of the two
#   attributes                  a number the player keeps
#   the specific ones           arrows, tridents, experience, drops, ground

def linear(base, per):
    return {"type": "minecraft:linear", "base": base, "per_level_above_first": per}


def add(value):
    return {"type": "minecraft:add", "value": value}


def multiply(factor):
    return {"type": "minecraft:multiply", "factor": factor}


def flags(entity, **which):
    return {"condition": "minecraft:entity_properties", "entity": entity,
            "predicate": {"flags": which}}


def direct():
    """The blow landed by hand, rather than by something the wielder threw."""
    return {"condition": "minecraft:damage_source_properties",
            "predicate": {"is_direct": True}}


def not_absolute():
    """Vanilla's own guard: nothing reduces the harm that is not meant to be reduced."""
    return {"condition": "minecraft:damage_source_properties",
            "predicate": {"tags": [{"expected": False,
                                    "id": "minecraft:bypasses_invulnerability"}]}}


def all_of(*terms):
    return {"condition": "minecraft:all_of", "terms": list(terms)}


def chance(base, per):
    return {"condition": "minecraft:random_chance",
            "chance": {"type": "minecraft:enchantment_level",
                       "amount": linear(base, per)}}


def mob_effect(effect, base, per, amplifier=0):
    return {"type": "minecraft:apply_mob_effect", "to_apply": effect,
            "min_duration": base, "max_duration": linear(base, per),
            "min_amplifier": amplifier, "max_amplifier": amplifier}


def attribute(name, attr, base, per, operation="add_value"):
    return {"attribute": attr, "amount": linear(base, per),
            "id": f"{NS}:enchantment.{name}", "operation": operation}


SWORD = "#minecraft:enchantable/sword"
WEAPON = "#minecraft:enchantable/sharp_weapon"
ARMOUR = "#minecraft:enchantable/armor"
MINING = "#minecraft:enchantable/mining"
BOW = "#minecraft:enchantable/bow"
CROSSBOW = "#minecraft:enchantable/crossbow"


def new(name, title, why, level, weight, anvil, first, slots, supported,
        effects, primary=None, exclusive=None):
    return dict(name=name, title=title, why=why, max_level=level, weight=weight,
                anvil_cost=anvil, first=first, slots=slots, supported=supported,
                effects=effects, primary=primary, exclusive=exclusive)


NEW_ENCHANTMENTS = [
    # -- a number, under a condition ---------------------------------------
    #
    # Both stances, and they are exclusive to each other: a weapon that is
    # better both standing still and running is a weapon that is just better.
    new("ambush", "Ambush",
        "more from a blow nobody saw coming",
        5, 5, 2, 5, ["mainhand"], WEAPON,
        {"minecraft:damage": [{"effect": add(linear(1.5, 1.5)),
                               "requirements": flags("attacker", is_sneaking=True)}]},
        primary=SWORD, exclusive=f"#{NS}:exclusive_set/stance"),
    new("charge", "Charge",
        "more from a blow with a run behind it",
        5, 5, 2, 5, ["mainhand"], WEAPON,
        {"minecraft:damage": [{"effect": add(linear(1.5, 1.5)),
                               "requirements": flags("attacker", is_sprinting=True)}]},
        primary=SWORD, exclusive=f"#{NS}:exclusive_set/stance"),
    new("bulwark", "Bulwark",
        "armour for a player who has decided to stand still",
        5, 3, 4, 5, ["armor"], ARMOUR,
        {"minecraft:damage_protection": [
            {"effect": add(linear(2.0, 2.0)),
             "requirements": all_of(flags("this", is_sneaking=True), not_absolute())}]}),
    new("nightward", "Nightward",
        "armour that is worth more where you cannot see",
        5, 3, 4, 8, ["armor"], ARMOUR,
        {"minecraft:damage_protection": [
            {"effect": add(linear(1.5, 1.5)),
             "requirements": all_of(
                 {"condition": "minecraft:location_check",
                  "predicate": {"light": {"light": {"max": 7}}}},
                 not_absolute())}]}),

    # -- something that happens to one of the two --------------------------
    new("sundering", "Sundering",
        "a blow that makes the next one land harder",
        4, 3, 4, 8, ["mainhand"], WEAPON,
        {"minecraft:post_attack": [
            {"affected": "victim", "enchanted": "attacker",
             "effect": mob_effect("minecraft:weakness", 3.0, 1.5),
             "requirements": direct()}]},
        primary=SWORD),
    new("venom", "Venom",
        "it keeps working after you have stopped",
        3, 2, 4, 8, ["mainhand"], WEAPON,
        {"minecraft:post_attack": [
            {"affected": "victim", "enchanted": "attacker",
             "effect": mob_effect("minecraft:poison", 2.0, 1.0),
             "requirements": direct()}]},
        primary=SWORD),
    new("siphon", "Siphon",
        "what you take, you keep",
        4, 2, 4, 12, ["mainhand"], WEAPON,
        {"minecraft:post_attack": [
            {"affected": "attacker", "enchanted": "attacker",
             "effect": mob_effect("minecraft:regeneration", 2.0, 1.0),
             "requirements": direct()}]},
        primary=SWORD),
    new("hoarfrost", "Hoarfrost",
        "nothing gets away twice",
        4, 3, 4, 10, ["mainhand"], WEAPON,
        {"minecraft:post_attack": [
            {"affected": "victim", "enchanted": "attacker",
             "effect": mob_effect("minecraft:slowness", 2.0, 1.0, amplifier=1),
             "requirements": direct()}]},
        primary=SWORD),
    new("kindling", "Kindling",
        "armour that objects",
        4, 2, 8, 12, ["armor"], "#minecraft:enchantable/chest_armor",
        {"minecraft:post_attack": [
            {"affected": "attacker", "enchanted": "victim",
             "effect": {"type": "minecraft:ignite", "duration": linear(3.0, 2.0)},
             "requirements": chance(0.20, 0.15)}]}),

    # -- a number the player keeps -----------------------------------------
    new("vitality", "Vitality",
        "more of you to lose",
        5, 2, 4, 15, ["armor"], ARMOUR,
        {"minecraft:attributes": [
            attribute("vitality", "minecraft:generic.max_health", 2.0, 2.0)]}),
    new("ironhide", "Ironhide",
        "armour that is harder rather than thicker",
        4, 2, 4, 10, ["armor"], ARMOUR,
        {"minecraft:attributes": [
            attribute("ironhide", "minecraft:generic.armor_toughness", 1.0, 1.0)]}),
    new("long_arm", "Long Arm",
        "the block at the back of the vein",
        3, 2, 4, 10, ["mainhand"], MINING,
        {"minecraft:attributes": [
            attribute("long_arm", "minecraft:player.block_interaction_range", 0.5, 0.5)]}),

    # -- arrows -------------------------------------------------------------
    new("volley", "Volley",
        "more arrows, and less idea where they are going",
        3, 2, 4, 12, ["mainhand"], BOW,
        {"minecraft:projectile_count": [{"effect": add(linear(1.0, 1.0))}],
         "minecraft:projectile_spread": [{"effect": add(linear(6.0, 4.0))}]},
        exclusive=f"#{NS}:exclusive_set/aim"),
    new("steady_aim", "Steady Aim",
        "one arrow, where you pointed it",
        3, 2, 4, 8, ["mainhand"], BOW,
        {"minecraft:projectile_spread": [{"effect": multiply(linear(0.75, -0.15))}]},
        exclusive=f"#{NS}:exclusive_set/aim"),
    new("thrift", "Thrift",
        "the arrow you sometimes still have",
        4, 2, 4, 20, ["mainhand"], BOW,
        {"minecraft:ammo_use": [{"effect": {"type": "minecraft:remove_binomial",
                                            "chance": linear(0.15, 0.15)}}]}),
    new("maelstrom", "Maelstrom",
        "further, harder, and still in the rain",
        4, 2, 4, 12, ["mainhand"], "#minecraft:enchantable/trident",
        # Not a list, and not wrapped in "effect": in the jar this
        # component is a bare EnchantmentValueEffect. Written as a list it is
        # valid JSON, loads fine, and fails when a world is created.
        {"minecraft:trident_spin_attack_strength": add(linear(1.5, 1.5))}),

    # -- what a kill or a block is worth ------------------------------------
    new("scholar", "Scholar",
        "the same fight, better understood",
        4, 2, 4, 15, ["mainhand"], WEAPON,
        {"minecraft:mob_experience": [{"effect": multiply(linear(1.25, 0.25))}]},
        primary=SWORD),
    new("prospector", "Prospector",
        "the same stone, better understood",
        4, 2, 4, 15, ["mainhand"], MINING,
        {"minecraft:block_experience": [{"effect": multiply(linear(1.5, 0.5))}]}),
    new("grave_robber", "Grave Robber",
        "what it was wearing, sometimes",
        4, 2, 4, 15, ["mainhand"], WEAPON,
        {"minecraft:equipment_drops": [
            {"enchanted": "attacker", "effect": add(linear(0.02, 0.02)),
             "requirements": {"condition": "minecraft:entity_properties",
                              "entity": "attacker",
                              "predicate": {"type": "minecraft:player"}}}]},
        primary=SWORD),

    # -- the ground ---------------------------------------------------------
    #
    # Frost Walker's shape pointed at the other liquid. Deliberately the same
    # shape: a player who has used one already knows what this does, and the
    # exclusive set is vanilla's own boots set, so it is a choice between them.
    new("emberwalk", "Emberwalk",
        "a crust over the lava, and it will not hold long",
        2, 2, 8, 10, ["feet"], "#minecraft:enchantable/foot_armor",
        {"minecraft:damage_immunity": [
            {"effect": {},
             "requirements": {"condition": "minecraft:damage_source_properties",
                              "predicate": {"tags": [
                                  {"expected": True, "id": "minecraft:burn_from_stepping"},
                                  {"expected": False,
                                   "id": "minecraft:bypasses_invulnerability"}]}}}],
         "minecraft:location_changed": [
             {"effect": {"type": "minecraft:replace_disk",
                         "block_state": {"type": "minecraft:simple_state_provider",
                                         "state": {"Name": "minecraft:magma_block"}},
                         "height": 1.0,
                         "offset": [0, -1, 0],
                         "predicate": {"type": "minecraft:all_of", "predicates": [
                             {"type": "minecraft:matching_block_tag",
                              "offset": [0, 1, 0], "tag": "minecraft:air"},
                             {"type": "minecraft:matching_blocks",
                              "blocks": "minecraft:lava"},
                             {"type": "minecraft:matching_fluids",
                              "fluids": "minecraft:lava"},
                             {"type": "minecraft:unobstructed"}]},
                         "radius": {"type": "minecraft:clamped", "max": 16.0, "min": 0.0,
                                    "value": linear(2.0, 1.0)},
                         "trigger_game_event": "minecraft:block_place"},
              "requirements": flags("this", is_on_ground=True)}]},
        exclusive="#minecraft:exclusive_set/boots"),
]

# Which of the new ones share a slot with each other, and so may not share a
# piece of gear. Anything not named here stacks with everything.
EXCLUSIVE_SETS = {
    "stance": ["ambush", "charge"],
    "aim": ["volley", "steady_aim"],
}


# ===========================================================================
# Writing
# ===========================================================================

def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(obj, handle, indent=2)
        handle.write("\n")


def short(block_id):
    return block_id.split(":", 1)[1]


def title(name):
    return " ".join(part.capitalize() for part in name.replace(":", "_").split("_"))


def all_shelves():
    """Every stat block this module ships: the vanilla table plus the made ones."""
    made = [(f"{NS}:{name}", e, q, a, r, m, why)
            for name, _metal, _glow, _seed, e, q, a, r, m, why, _key, _pattern
            in MADE_SHELVES]
    return SHELVES + made


def write_stats():
    """One file per shelf, named for the block so a pack can find and replace it."""
    if STATS.is_dir():
        for stale in STATS.glob("*.json"):
            stale.unlink()
    for block, eterna, quanta, arcana, rectification, max_eterna, why in all_shelves():
        entry = {"block": block}
        for key, value in (("eterna", eterna), ("quanta", quanta), ("arcana", arcana),
                           ("rectification", rectification), ("max_eterna", max_eterna)):
            if value:
                entry[key] = value
        # The reason the number is what it is, carried in the file rather than
        # only here: a pack author opening one of these to change it is owed
        # the sentence that set it.
        entry["_why"] = why
        write(STATS / f"{short(block)}.json", entry)
    return len(all_shelves())


# (block, metal, glow, the recipe's key, the recipe's pattern)
#
# Two benches, two rooms. The table is where a player gambles and the basin is
# where they do not, and the pair is the whole of what this module asks a
# player to decide.
BLOCKS = [
    ("arcane_table", "obsidian", "dimensional",
     {"d": "minecraft:diamond", "a": "minecraft:amethyst_block",
      "T": "minecraft:enchanting_table", "o": "minecraft:obsidian"},
     [" d ", "aTa", "ooo"]),
    ("infusion_basin", "obsidian", "aetherium",
     {"d": "minecraft:diamond", "a": "minecraft:amethyst_block",
      "T": "minecraft:cauldron", "o": "minecraft:obsidian"},
     ["a a", "dTd", "ooo"]),
    ("scriptorium", "stone", "kinetic",
     {"b": "minecraft:bookshelf", "l": "minecraft:lapis_block",
      "c": "minecraft:chest"},
     ["bbb", "lcl", "bbb"]),
    # Four of the small one, which is the same sentence its arithmetic makes:
    # four of a thing buy one of the thing above.
    ("grand_scriptorium", "voidsteel", "kinetic",
     {"s": f"{NS}:scriptorium", "g": "minecraft:gold_block",
      "d": "minecraft:diamond_block"},
     ["gsg", "sds", "gsg"]),
]


# The made shelves (P10.12).
#
# The vanilla stat blocks are what a player builds with for the first half of
# the game, and every one of them drags something else along with it. These are
# the made ones: each is a clean statement of one number, or of a pair nobody
# finds together in the world, and each costs having been somewhere.
#
# (name, metal, glow, seed, eterna, quanta, arcana, rectification, max_eterna,
#  why, recipe key, recipe pattern)
MADE_SHELVES = [
    ("hellshelf", "obsidian", "plasma", 3, 2.5, 1.5, 0.0, 0.0, 25.0,
     "it reads fast and it is not careful",
     {"n": "minecraft:netherrack", "r": "minecraft:blaze_rod", "B": "minecraft:bookshelf"},
     ["nrn", "rBr", "nrn"]),
    ("deepshelf", "stone", "neural", 11, 0.0, 3.0, 0.5, 0.0, 20.0,
     "everything it knows, it heard",
     {"s": "minecraft:deepslate", "d": "minecraft:sculk", "B": "minecraft:bookshelf"},
     ["sds", "dBd", "sds"]),
    ("sightshelf", "voidsteel", "voidglass", 7, 0.0, 0.0, 3.0, 0.0, 24.0,
     "it is looking at something further off than the room",
     {"a": "minecraft:amethyst_shard", "e": "minecraft:ender_eye",
      "B": "minecraft:bookshelf"},
     ["aea", "eBe", "aea"]),
    ("endshelf", "neutronium", "dimensional", 15, 0.0, 0.0, 2.0, 0.04, 35.0,
     "written somewhere that has no weather",
     {"p": "minecraft:purpur_block", "e": "minecraft:ender_pearl",
      "B": "minecraft:bookshelf"},
     ["pep", "eBe", "pep"]),
    ("keepers_shelf", "voidsteel", "kinetic", 19, 0.0, 0.0, 0.0, 0.12, 40.0,
     "somebody checks it, and it has never been wrong",
     {"r": "minecraft:redstone", "c": "minecraft:comparator", "B": "minecraft:bookshelf"},
     ["rcr", "cBc", "rcr"]),
    ("archive_column", "aetherium", "aetherium", 23, 3.0, 0.0, 0.0, 0.0, 40.0,
     "everything, in order, and none of it is interesting",
     {"d": "minecraft:diamond", "b": "minecraft:book", "B": "minecraft:bookshelf"},
     ["dbd", "bBb", "dbd"]),
]


def write_made_shelves():
    """The module's own shelves: a sprite each, and everything that places one."""
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for name, metal, glow, seed, *_rest in MADE_SHELVES:
        target = TEXTURES / f"{name}.png"
        if not target.exists():
            sprites.shelf(metal, glow, seed).save(target)
    for name, _metal, _glow, _seed, _e, _q, _a, _r, _m, _why, key, pattern in MADE_SHELVES:
        write(BLOCKSTATES / f"{name}.json",
              {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write(BLOCK_MODELS / f"{name}.json",
              {"parent": "minecraft:block/cube_all",
               "textures": {"all": f"{NS}:block/{name}"}})
        write(ITEM_MODELS / f"{name}.json", {"parent": f"{NS}:block/{name}"})
        write(LOOT / f"{name}.json",
              {"type": "minecraft:block",
               "pools": [{"rolls": 1, "bonus_rolls": 0,
                          "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                          "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        write(RECIPES / f"{name}.json",
              {"type": "minecraft:crafting_shaped",
               "category": "building",
               "pattern": pattern,
               "key": {letter: {"item": item} for letter, item in key.items()},
               "result": {"id": f"{NS}:{name}", "count": 1}})
    return len(MADE_SHELVES)


def write_block():
    """Each bench: a texture, the three files that draw it, and the two that place it."""
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for name, metal, glow, key, pattern in BLOCKS:
        target = TEXTURES / f"{name}.png"
        if not target.exists():
            # Obsidian for both, and different light: the table is a cold
            # dimensional blue and the basin the warmer aetherium, so a player
            # can tell from across a room which bench they are walking to.
            sprites.workstation(name, metal, glow).save(target)
        write(BLOCKSTATES / f"{name}.json",
              {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write(BLOCK_MODELS / f"{name}.json",
              {"parent": "minecraft:block/cube_all",
               "textures": {"all": f"{NS}:block/{name}"}})
        write(ITEM_MODELS / f"{name}.json", {"parent": f"{NS}:block/{name}"})
        write(LOOT / f"{name}.json",
              {"type": "minecraft:block",
               "pools": [{"rolls": 1, "bonus_rolls": 0,
                          "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                          "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        # Vanilla ingredients only. This module is independent of elysium-core
        # by design, and a recipe naming a block from a mod that is not
        # installed is a block nobody can craft.
        write(RECIPES / f"{name}.json",
              {"type": "minecraft:crafting_shaped",
               "category": "misc",
               "pattern": pattern,
               "key": {letter: {"item": item} for letter, item in key.items()},
               "result": {"id": f"{NS}:{name}", "count": 1}})
    # requiresCorrectToolForDrops in a block's properties is only half of the
    # statement: without these two tags it needs a tool the game never names,
    # which means no tool at all works.
    listed = [f"{NS}:{name}" for name, *_ in BLOCKS]
    write(TAGS / "mineable/pickaxe.json", {"replace": False, "values": listed})
    write(TAGS / "needs_iron_tool.json", {"replace": False, "values": listed})
    # The shelves are wood and break with an axe, like every bookshelf in the
    # game. They do not need a *correct* tool, so they are in no needs_ tag:
    # a shelf that dropped nothing to a player without iron would be a shelf
    # they could craft and not move.
    write(TAGS / "mineable/axe.json",
          {"replace": False, "values": [f"{NS}:{name}" for name, *_ in MADE_SHELVES]})



# The tomes (P10.11): one shape, four marks, four things a long world needs.
#
# (name, title, mark, metal, glow, the line the tooltip carries, recipe key,
#  recipe pattern)
TOMES = [
    ("scrapping_tome", "Tome of Scrapping", "scrapping", "obsidian", "plasma",
     "On an anvil: takes every enchantment off, so a table will look at it again",
     {"f": "minecraft:flint", "b": "minecraft:book"}, ["fbf"]),
    ("transcription_tome", "Tome of Transcription", "transcription", "voidsteel", "aetherium",
     "On an anvil: moves everything onto a book, and the item is spent doing it",
     {"i": "minecraft:ink_sac", "b": "minecraft:book"}, ["ibi"]),
    ("concentration_tome", "Tome of Concentration", "concentration", "obsidian", "voidglass",
     "On an anvil: narrows a mixed book down to its best line",
     {"a": "minecraft:amethyst_shard", "b": "minecraft:book"}, ["aba"]),
    ("patience_tome", "Tome of Patience", "patience", "voidsteel", "kinetic",
     "On an anvil: clears what an item's history is costing, once",
     {"c": "minecraft:clock", "b": "minecraft:book"}, ["cbc"]),
]


def write_tomes():
    """The four tomes: a sprite, a model, a recipe each."""
    ITEM_TEXTURES.mkdir(parents=True, exist_ok=True)
    for name, _title, mark, metal, glow, _why, key, pattern in TOMES:
        target = ITEM_TEXTURES / f"{name}.png"
        if not target.exists():
            sprites.tome(mark, metal, glow).save(target)
        write(ITEM_MODELS / f"{name}.json",
              {"parent": "minecraft:item/generated",
               "textures": {"layer0": f"{NS}:item/{name}"}})
        write(RECIPES / f"{name}.json",
              {"type": "minecraft:crafting_shaped",
               "category": "misc",
               "pattern": pattern,
               "key": {letter: {"item": item} for letter, item in key.items()},
               "result": {"id": f"{NS}:{name}", "count": 1}})
    return len(TOMES)


def write_new_enchantments():
    """
    The module's own enchantments, and the tags that make them findable.

    Same pacing as the ladder: an enchantment whose top rung needs power a
    completed room cannot offer is an enchantment with a level nobody stands
    on, whoever wrote it.
    """
    if OURS.is_dir():
        for stale in OURS.glob("*.json"):
            stale.unlink()
    for entry in NEW_ENCHANTMENTS:
        step = pace(entry["first"], entry["max_level"])
        body = {
            "anvil_cost": entry["anvil_cost"],
            "description": {"translate": f"enchantment.{NS}.{entry['name']}"},
            "effects": entry["effects"],
            "max_cost": {"base": entry["first"] + max(30, step + BAND_OVER_STEP),
                         "per_level_above_first": step},
            "max_level": entry["max_level"],
            "min_cost": {"base": entry["first"], "per_level_above_first": step},
            "slots": entry["slots"],
            "supported_items": entry["supported"],
            "weight": entry["weight"],
        }
        if entry["primary"]:
            body["primary_items"] = entry["primary"]
        if entry["exclusive"]:
            body["exclusive_set"] = entry["exclusive"]
        write(OURS / f"{entry['name']}.json", body)

    for name, members in EXCLUSIVE_SETS.items():
        write(OUR_TAGS / f"exclusive_set/{name}.json",
              {"replace": False, "values": [f"{NS}:{member}" for member in members]})

    ours = [f"{NS}:{entry['name']}" for entry in NEW_ENCHANTMENTS]
    treasure = [f"minecraft:{name}" for name in FROM_THE_TABLE]
    # Three tags, and they are three different questions: what a table may
    # offer, what a chest may hold, and what a villager may sell. An
    # enchantment in none of them exists only for somebody holding a command.
    write(ENCH_TAGS / "in_enchanting_table.json",
          {"replace": False, "values": treasure + ours})
    write(ENCH_TAGS / "on_random_loot.json", {"replace": False, "values": ours})
    write(ENCH_TAGS / "tradeable.json", {"replace": False, "values": ours})
    return len(NEW_ENCHANTMENTS)



# ===========================================================================
# Advancements (P12.12)
# ===========================================================================
#
# A tree rather than a list. What it is for is the order: a player who has
# never met this module cannot be told "build a room for rectification"
# usefully, but they can be shown, one rung at a time, that a table has four
# numbers and that different blocks answer different ones.
#
# Every one of them is a thing a player can hold or a thing they can do —
# nothing here fires on a statistic nobody can see.
#
# (name, parent, icon, frame, title, description, criteria)
ADVANCEMENT_ROOT_BACKGROUND = "minecraft:textures/block/bookshelf.png"


def has(item):
    """The criterion for holding something, which is most of this tree."""
    return {"trigger": "minecraft:inventory_changed",
            "conditions": {"items": [{"items": item}]}}


ADVANCEMENTS = [
    ("root", None, "minecraft:bookshelf", "task",
     "The Long Study",
     "Enchanting is a room you build, not a table you stand at",
     {"shelf": has("minecraft:bookshelf")}),
    ("arcane_table", "root", f"{NS}:arcane_table", "task",
     "Four Numbers",
     "Build an Arcane Table. It asks the room how much power it has, how "
     "uncertain it is, how strange it is, and how much of the uncertainty has "
     "been paid off",
     {"table": has(f"{NS}:arcane_table")}),
    ("enchanted", "arcane_table", "minecraft:enchanted_book", "task",
     "Bought, Not Found",
     "Enchant something",
     {"enchanted": {"trigger": "minecraft:enchanted_item"}}),
    ("deep_work", "enchanted", "minecraft:experience_bottle", "goal",
     "Deep Work",
     "Spend thirty levels on a single enchantment",
     {"expensive": {"trigger": "minecraft:enchanted_item",
                    "conditions": {"levels": {"min": 30}}}}),
    ("strange_room", "arcane_table", f"{NS}:sightshelf", "task",
     "Built for Strangeness",
     "Make a Sightshelf. Arcana does not make an enchantment stronger — it "
     "changes which ones are on the table at all",
     {"shelf": has(f"{NS}:sightshelf")}),
    ("steady_room", "arcane_table", f"{NS}:keepers_shelf", "task",
     "Built for Certainty",
     "Make a Keeper's Shelf. Rectification is the hardest of the four to "
     "raise, and it is what keeps the upside of an uncertain table",
     {"shelf": has(f"{NS}:keepers_shelf")}),
    ("infusion_basin", "enchanted", f"{NS}:infusion_basin", "task",
     "No Rolling",
     "Build an Infusion Basin. The book says what you get, the room says how "
     "high, and the price is the certainty",
     {"basin": has(f"{NS}:infusion_basin")}),
    ("scriptorium", "enchanted", f"{NS}:scriptorium", "task",
     "Somewhere to Put Them",
     "Build a Scriptorium, and the chest of books you were never going to use "
     "becomes a currency",
     {"shelf": has(f"{NS}:scriptorium")}),
    ("grand_scriptorium", "scriptorium", f"{NS}:grand_scriptorium", "goal",
     "The Great Library",
     "Build a Grand Scriptorium: sixty-four times the shelf, which is what "
     "makes the highest levels bankable",
     {"shelf": has(f"{NS}:grand_scriptorium")}),
    ("tome", "enchanted", f"{NS}:patience_tome", "task",
     "Too Expensive",
     "Make a Tome of Patience. An anvil that will not touch your best pickaxe "
     "at any price is a wall, and this is the way through it",
     {"tome": has(f"{NS}:patience_tome")}),
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
                 "requirements": [[key] for key in criteria]}
        if parent is not None:
            entry["parent"] = f"{NS}:{parent}"
        write(ADVANCEMENTS_DIR / f"{name}.json", entry)
    return len(ADVANCEMENTS)


def write_lang():
    """
    Every string this module shows, including the shelves' names.

    The shelf entries are not for the game — a vanilla block already has a name
    — they are for the in-game reference this module will grow, and for the
    validator, which fails on a shelf that ships without a sentence saying why
    it is worth what it is worth.
    """
    lang = {
        "itemGroup.elysiumenchanting": "Elysium Enchanting",
        "block.elysiumenchanting.arcane_table": "Arcane Table",
        "block.elysiumenchanting.infusion_basin": "Infusion Basin",
        "block.elysiumenchanting.scriptorium": "Scriptorium",
        "block.elysiumenchanting.grand_scriptorium": "Grand Scriptorium",
        "attribute.name.elysiumenchanting.max_eterna": "Maximum Eterna",

        "elysiumenchanting.stat.eterna": "Eterna %s",
        "elysiumenchanting.stat.quanta": "Quanta %s",
        "elysiumenchanting.stat.arcana": "Arcana %s",
        "elysiumenchanting.stat.rectification": "Rectification %s",

        "elysiumenchanting.offer.level": "Level %s · %s lapis",
        "elysiumenchanting.offer.and": "%s and %s more",
        "elysiumenchanting.offer.unknown": "Something",

        "elysiumenchanting.infusion.ceiling": "This room infuses to level %s",
        "elysiumenchanting.infusion.price": "%s levels \u00b7 %s lapis",
        "elysiumenchanting.infusion.nothing": "A book, and something to put it on",
        "elysiumenchanting.infusion.too_weak": "This room cannot reach that",
        "elysiumenchanting.infusion.too_rare": "This room is not strange enough",
        "elysiumenchanting.infusion.wrong_item": "Not on this",
        "elysiumenchanting.infusion.conflict": "It argues with what is already there",

        "elysiumenchanting.library.pages": "%s of %s pages",
        "elysiumenchanting.library.buy": "Level %s \u00b7 %s pages \u00b7 %s levels",
        "elysiumenchanting.library.empty": "Not enough filed for a copy",
        "elysiumenchanting.library.nothing": "Nothing filed here yet",
    }
    for block, _e, _q, _a, _r, _m, why in all_shelves():
        lang[f"elysiumenchanting.shelf.{short(block)}"] = why
    for name, _metal, _glow, _seed, _e, _q, _a, _r, _m, _why, _key, _pattern in MADE_SHELVES:
        lang[f"block.{NS}.{name}"] = title(name)
    for name, called, _mark, _metal, _glow, why, _key, _pattern in TOMES:
        lang[f"item.{NS}.{name}"] = called
        lang[f"{NS}.tooltip.{name}"] = why
    for name, _parent, _icon, _frame, called, description, _criteria in ADVANCEMENTS:
        lang[f"advancements.{NS}.{name}.title"] = called
        lang[f"advancements.{NS}.{name}.description"] = description
    for entry in NEW_ENCHANTMENTS:
        lang[f"enchantment.{NS}.{entry['name']}"] = entry["title"]
        # What it is for, in one line. Vanilla shows an enchantment's name and
        # nothing else; this module's own screens show the sentence, and a
        # sentence nobody wrote is an enchantment nobody can choose.
        lang[f"enchantment.{NS}.{entry['name']}.desc"] = entry["why"]
    write(LANG, dict(sorted(lang.items())))
    return len(lang)


def pace(min_base, max_level):
    """
    The power between one rung and the next.

    Chosen so the top rung lands on TOP_POWER: a ladder whose top is out of
    reach is a ladder with a rung nobody has ever stood on, and one whose top
    arrives early is a ladder that stops mattering halfway up.
    """
    if max_level <= 1:
        return 0
    return max(1, round((TOP_POWER - min_base) / (max_level - 1)))


def write_enchantments():
    """
    Vanilla's own files, re-paced.

    Read from tools/vanilla_enchantments/, three fields changed, written into
    data/minecraft/enchantment/ — which overrides the game's copy, because an
    enchantment's level ceiling lives in its file and there is no half of a
    file to ship.
    """
    if ENCHANTMENTS.is_dir():
        for stale in ENCHANTMENTS.glob("*.json"):
            stale.unlink()
    for name, max_level, _why in LADDER:
        source = VANILLA / f"{name}.json"
        if not source.is_file():
            raise SystemExit(f"no vanilla copy of {name} in {VANILLA}")
        entry = json.load(open(source, encoding="utf-8"))
        band = entry["max_cost"]["base"] - entry["min_cost"]["base"]
        step = pace(entry["min_cost"]["base"], max_level)
        entry["max_level"] = max_level
        entry["min_cost"] = {"base": entry["min_cost"]["base"],
                             "per_level_above_first": step}
        entry["max_cost"] = {"base": entry["min_cost"]["base"]
                                     + max(band, step + BAND_OVER_STEP),
                             "per_level_above_first": step}
        write(ENCHANTMENTS / f"{name}.json", entry)
    return len(LADDER)


def main():
    shelves = write_stats()
    write_block()
    made = write_made_shelves()
    rungs = write_enchantments()
    mine = write_new_enchantments()
    tomes = write_tomes()
    earned = write_advancements()
    keys = write_lang()
    print(f"enchanting: {shelves} stat block(s) ({made} of them made), {rungs} enchantment(s) re-paced, "
          f"{mine} new one(s), {tomes} tome(s), {earned} advancement(s), "
          f"{len(FROM_THE_TABLE)} treasure(s) a built room can reach, "
          f"{keys} language key(s), {len(BEYOND_BASE)} block(s) whose cap passes what "
          f"a player starts with")


if __name__ == "__main__":
    main()
