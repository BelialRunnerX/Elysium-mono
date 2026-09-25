#!/usr/bin/env python3
"""Generate the affix catalogue (SPEC_CLONE P4.2 and P5.4).

An affix is a named bundle of item effects rolled onto a finished item. The
effects are stated in the shared vocabulary — the same words a trait uses, read
by the same handlers — so this file writes data and no Java at all.

The table below is the whole catalogue: for each affix, where it sits in a name
(prefix or suffix), what kinds of gear it can land on, the world tier below
which it does not exist, how often it is drawn (weight), how high it rolls, and
what it does.

Two rules the table has to keep, both checked by the validator:

  * Only live effect kinds, and only ones the LIBRARY reads. A kind that only
    the armory acts on would work on modular gear and quietly do nothing on a
    vanilla sword, which is the bug this whole phase exists to end.
  * A tier is a gate, not a weight. An affix above the world's tier is absent
    rather than rare, so reaching a tier is felt as something new appearing.

Re-runnable: the output is a pure function of this table, and the validator
re-runs it into a scratch directory and compares byte for byte.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NS = "elysiumlib"
OUT = os.path.join(HERE, "src/main/resources/data", NS, "elysium_affixes")

MELEE = ["melee_weapon"]
MELEE_HYBRID = ["weapon", "not:ranged_weapon"]
RANGED = ["ranged_weapon"]
ANY_WEAPON = ["weapon"]
TOOL = ["harvest_tool"]
ARMOR = ["armor"]
SHIELD = ["shield"]
CURIO = ["curio"]


def attr(attribute, operation, slot, low, high, levels):
    return {"type": "attribute", "modifiers": [
        {"attribute": attribute, "operation": operation, "slot": slot,
         "min": low, "max": high, "levels": levels}]}


def attrs(*modifiers):
    return {"type": "attribute", "modifiers": list(modifiers)}


def one(attribute, operation, slot, low, high, levels):
    return {"attribute": attribute, "operation": operation, "slot": slot,
            "min": low, "max": high, "levels": levels}


def hits(effect, ticks_per_level):
    return {"type": "target_effect", "effect": effect, "ticks_per_level": ticks_per_level}


def worn(effect, levels, duration=60):
    return {"type": "wielder_effect",
            "targets": [{"effect": effect, "levels": levels, "duration": duration}]}


def slays(tag, per_level):
    return {"type": "extra_damage", "against": "tagged", "tag": tag, "per_level": per_level}


def slays_kind(against, per_level):
    return {"type": "extra_damage", "against": against, "per_level": per_level}


def turns_aside(tag, scale_per_level):
    return {"type": "negate_damage", "tag": tag, "scale_per_level": scale_per_level}


def clears(*effects):
    return {"type": "cancel_effects", "effects": list(effects)}


def digs(tag, per_level):
    return {"type": "mining_speed", "tag": tag, "per_level": per_level}


def spares(chance_per_level):
    return {"type": "durability", "chance_per_level": chance_per_level, "amount": -1}


def mends(interval, chance_per_level, amount_per_level=1):
    return {"type": "self_repair", "interval": interval,
            "chance_per_level": chance_per_level, "amount_per_level": amount_per_level}


def draws(range_, pull=0.06):
    return {"type": "item_magnet", "range": range_, "pull": pull}


def shakes_loose(ingredient, chance_per_level, multiplier=1.0):
    return {"type": "bonus_drops", "ingredient": ingredient,
            "chance_per_level": chance_per_level, "multiplier": multiplier}


def blast(radius_per_level=2.0, strength_per_level=0.4):
    return {"type": "wind_blast", "radius_per_level": radius_per_level,
            "strength_per_level": strength_per_level}


DAMAGE = "minecraft:generic.attack_damage"
SPEED_ATTACK = "minecraft:generic.attack_speed"
KNOCKBACK = "minecraft:generic.attack_knockback"
ARMOUR = "minecraft:generic.armor"
TOUGHNESS = "minecraft:generic.armor_toughness"
HEALTH = "minecraft:generic.max_health"
MOVE = "minecraft:generic.movement_speed"
KB_RESIST = "minecraft:generic.knockback_resistance"
LUCK = "minecraft:generic.luck"
REACH = "minecraft:player.block_interaction_range"
ADD = "add_value"
MULT = "add_multiplied_base"

# id, name, placement, kinds, min tier, weight, max level, effects
AFFIXES = [
    # ---- melee weapons -------------------------------------------------
    ("keen", "Keen", "prefix", MELEE, 0, 14, 4,
     [attr(DAMAGE, ADD, "mainhand", 0.5, 3.0, 4)]),
    ("brutal", "Brutal", "prefix", MELEE, 1, 10, 4,
     [attr(DAMAGE, MULT, "mainhand", 0.05, 0.25, 4)]),
    ("quick", "Quick", "prefix", MELEE, 0, 12, 3,
     [attr(SPEED_ATTACK, ADD, "mainhand", 0.1, 0.4, 3)]),
    ("heavy", "Heavy", "prefix", MELEE, 0, 10, 3,
     [attrs(one(KNOCKBACK, ADD, "mainhand", 0.25, 1.0, 3),
            one(SPEED_ATTACK, ADD, "mainhand", -0.1, -0.3, 3))]),
    ("cruel", "Cruel", "prefix", MELEE, 1, 8, 3,
     [hits("elysiumlib:bleeding", 40)]),
    ("vicious", "Vicious", "prefix", MELEE_HYBRID, 1, 8, 4,
     [attr("elysiumlib:crit_chance", ADD, "mainhand", 0.04, 0.2, 4)]),
    ("savage", "Savage", "prefix", MELEE, 2, 5, 4,
     [attr("elysiumlib:crit_damage", ADD, "mainhand", 0.1, 0.5, 4)]),
    ("long", "Long", "prefix", MELEE, 1, 7, 3,
     [attr(REACH, ADD, "mainhand", 0.25, 1.0, 3)]),
    ("gale", "Gale", "prefix", MELEE, 2, 4, 2, [blast()]),
    ("dire", "Dire", "prefix", MELEE, 3, 3, 3,
     [attr("elysiumlib:current_hp_damage", ADD, "mainhand", 0.02, 0.08, 3)]),
    ("of_burning", "of Burning", "suffix", MELEE_HYBRID, 0, 10, 3,
     [{"type": "target_effect", "effect": "minecraft:instant_damage", "ticks_per_level": 1}]),
    ("of_the_serpent", "of the Serpent", "suffix", MELEE, 0, 9, 3,
     [hits("minecraft:poison", 40)]),
    ("of_weakening", "of Weakening", "suffix", MELEE, 1, 7, 3,
     [hits("minecraft:weakness", 60)]),
    ("of_the_mire", "of the Mire", "suffix", MELEE, 1, 7, 3,
     [hits("minecraft:slowness", 50)]),
    ("of_sundering", "of Sundering", "suffix", MELEE, 2, 6, 3,
     [attr("elysiumlib:armor_pierce", ADD, "mainhand", 1.0, 5.0, 3)]),
    ("of_shredding", "of Shredding", "suffix", MELEE, 2, 6, 3,
     [attr("elysiumlib:armor_shred", ADD, "mainhand", 1.0, 4.0, 3)]),
    ("of_leeching", "of Leeching", "suffix", MELEE, 2, 5, 3,
     [attr("elysiumlib:life_steal", ADD, "mainhand", 0.02, 0.1, 3)]),
    ("of_the_hunt", "of the Hunt", "suffix", ANY_WEAPON, 0, 7, 4,
     [slays("minecraft:skeletons", 1.5)]),
    ("of_the_grave", "of the Grave", "suffix", ANY_WEAPON, 0, 7, 4,
     [slays("minecraft:undead", 1.5)]),
    ("of_the_deep", "of the Deep", "suffix", ANY_WEAPON, 1, 5, 4,
     [slays_kind("aquatic", 2.0)]),
    ("of_giants", "of Giants", "suffix", ANY_WEAPON, 2, 5, 4,
     [slays_kind("high_health", 2.0)]),
    ("of_embers", "of Embers", "suffix", ANY_WEAPON, 2, 4, 4,
     [slays_kind("fire_immune", 2.0)]),
    ("wrathful", "Wrathful", "prefix", MELEE, 3, 3, 3,
     [attrs(one("elysiumlib:crit_damage", ADD, "mainhand", 0.15, 0.45, 3),
            one("elysiumlib:life_steal", ADD, "mainhand", 0.02, 0.06, 3))]),

    # ---- ranged --------------------------------------------------------
    ("swift", "Swift", "prefix", RANGED, 0, 12, 4,
     [attr("elysiumlib:draw_speed", ADD, "mainhand", 0.1, 0.5, 4)]),
    ("true", "True", "prefix", RANGED, 0, 10, 3,
     [attr("elysiumlib:arrow_velocity", ADD, "mainhand", 0.1, 0.4, 3)]),
    ("piercing", "Piercing", "prefix", RANGED, 2, 6, 3,
     [attr("elysiumlib:armor_pierce", ADD, "mainhand", 1.0, 4.0, 3)]),
    ("hunters", "Hunter's", "prefix", RANGED, 1, 8, 3,
     [attr("elysiumlib:projectile_damage", ADD, "mainhand", 0.5, 2.0, 3)]),
    ("of_volley", "of Volley", "suffix", RANGED, 0, 8, 3,
     [attr("elysiumlib:arrow_damage", ADD, "mainhand", 0.5, 2.5, 3)]),
    ("of_the_falcon", "of the Falcon", "suffix", RANGED, 1, 6, 3,
     [attr("elysiumlib:crit_chance", ADD, "mainhand", 0.03, 0.15, 3)]),
    ("of_frost", "of Frost", "suffix", RANGED, 1, 6, 3,
     [attr("elysiumlib:cold_damage", ADD, "mainhand", 0.5, 2.0, 3)]),
    ("of_cinders", "of Cinders", "suffix", RANGED, 1, 6, 3,
     [attr("elysiumlib:fire_damage", ADD, "mainhand", 0.5, 2.0, 3)]),
    ("of_the_long_shot", "of the Long Shot", "suffix", RANGED, 2, 5, 3,
     [attr("elysiumlib:projectile_damage", MULT, "mainhand", 0.05, 0.25, 3)]),
    ("of_recovery", "of Recovery", "suffix", RANGED, 2, 5, 3,
     [mends(200, 0.2)]),
    ("of_the_siege", "of the Siege", "suffix", RANGED, 3, 4, 3,
     [attr("elysiumlib:current_hp_damage", ADD, "mainhand", 0.02, 0.06, 3)]),
    ("unerring", "Unerring", "prefix", RANGED, 3, 3, 3,
     [attrs(one("elysiumlib:crit_damage", ADD, "mainhand", 0.1, 0.4, 3),
            one("elysiumlib:arrow_velocity", ADD, "mainhand", 0.05, 0.2, 3))]),

    # ---- tools ---------------------------------------------------------
    ("tempered", "Tempered", "prefix", TOOL, 0, 14, 4, [spares(0.08)]),
    ("sturdy", "Sturdy", "prefix", TOOL, 0, 12, 3, [spares(0.05)]),
    ("industrious", "Industrious", "prefix", TOOL, 0, 12, 4,
     [attr("elysiumlib:mining_speed", ADD, "mainhand", 0.5, 2.5, 4)]),
    ("bountiful", "Bountiful", "prefix", TOOL, 2, 6, 3,
     [shakes_loose("c:ores", 0.1)]),
    ("patient", "Patient", "prefix", TOOL, 1, 8, 3, [mends(200, 0.25)]),
    ("far_reaching", "Far-Reaching", "prefix", TOOL, 1, 7, 3,
     [attr(REACH, ADD, "mainhand", 0.5, 1.5, 3)]),
    ("of_delving", "of Delving", "suffix", TOOL, 0, 11, 4,
     [digs("minecraft:mineable/pickaxe", 0.2)]),
    ("of_felling", "of Felling", "suffix", TOOL, 0, 10, 4,
     [digs("minecraft:mineable/axe", 0.2)]),
    ("of_tilling", "of Tilling", "suffix", TOOL, 0, 9, 4,
     [digs("minecraft:mineable/shovel", 0.2)]),
    ("of_gathering", "of Gathering", "suffix", TOOL, 0, 9, 3, [draws(3.0)]),
    ("of_the_prospector", "of the Prospector", "suffix", TOOL, 2, 5, 3,
     [shakes_loose("c:raw_materials", 0.12)]),
    ("of_the_vein", "of the Vein", "suffix", TOOL, 3, 4, 3,
     [shakes_loose("c:ores", 0.08, 2.0)]),
    ("of_mending", "of Mending", "suffix", TOOL + MELEE + ARMOR, 1, 5, 3, [mends(200, 0.25)]),
    ("of_the_surveyor", "of the Surveyor", "suffix", TOOL, 1, 7, 3,
     [worn("minecraft:night_vision", [0, 0, 0], 220)]),
    ("of_toil", "of Toil", "suffix", TOOL, 1, 8, 3,
     [worn("minecraft:haste", [0, 0, 1])]),
    ("of_the_anvil", "of the Anvil", "suffix", TOOL, 2, 5, 3, [spares(0.1)]),
    ("of_the_harvest", "of the Harvest", "suffix", TOOL, 1, 7, 3,
     [shakes_loose("c:crops", 0.15)]),

    # ---- armour --------------------------------------------------------
    ("stalwart", "Stalwart", "prefix", ARMOR, 0, 14, 4,
     [attr(ARMOUR, ADD, "any", 0.5, 3.0, 4)]),
    ("reinforced", "Reinforced", "prefix", ARMOR, 1, 10, 4,
     [attr(TOUGHNESS, ADD, "any", 0.25, 1.5, 4)]),
    ("nimble", "Nimble", "prefix", ARMOR, 1, 10, 3,
     [attr("elysiumlib:dodge_chance", ADD, "any", 0.02, 0.08, 3)]),
    ("hale", "Hale", "prefix", ARMOR, 0, 12, 4,
     [attr(HEALTH, ADD, "any", 1.0, 6.0, 4)]),
    ("rooted", "Rooted", "prefix", ARMOR, 1, 8, 3,
     [attr(KB_RESIST, ADD, "any", 0.05, 0.2, 3)]),
    ("fleet", "Fleet", "prefix", ARMOR, 1, 8, 3,
     [attr(MOVE, MULT, "any", 0.02, 0.08, 3)]),
    ("vital", "Vital", "prefix", ARMOR, 2, 6, 3,
     [attr("elysiumlib:healing_received", ADD, "any", 0.05, 0.25, 3)]),
    ("warded", "Warded", "prefix", ARMOR, 2, 6, 3,
     [attr("elysiumlib:prot_pierce", ADD, "any", 1.0, 4.0, 3)]),
    ("ghostly", "Ghostly", "prefix", ARMOR, 3, 4, 3,
     [attr("elysiumlib:ghost_health", ADD, "any", 1.0, 5.0, 3)]),
    ("of_the_titan", "of the Titan", "suffix", ARMOR, 1, 8, 4,
     [attr(HEALTH, ADD, "any", 2.0, 10.0, 4)]),
    ("of_warding", "of Warding", "suffix", ARMOR, 2, 6, 3,
     [turns_aside("minecraft:is_explosion", 0.15)]),
    ("of_the_hearth", "of the Hearth", "suffix", ARMOR, 2, 6, 3,
     [turns_aside("minecraft:is_fire", 0.15)]),
    ("of_the_fall", "of the Fall", "suffix", ARMOR, 1, 7, 3,
     [turns_aside("minecraft:fall", 0.2)]),
    ("of_the_quiver", "of the Quiver", "suffix", ARMOR, 2, 6, 3,
     [turns_aside("minecraft:is_projectile", 0.12)]),
    ("of_clarity", "of Clarity", "suffix", ARMOR, 1, 5, 2,
     [clears("minecraft:blindness", "minecraft:nausea")]),
    ("of_purity", "of Purity", "suffix", ARMOR, 2, 5, 2,
     [clears("minecraft:poison", "minecraft:wither")]),
    ("of_resolve", "of Resolve", "suffix", ARMOR, 2, 5, 2,
     [clears("minecraft:weakness", "minecraft:mining_fatigue")]),
    ("of_the_fox", "of the Fox", "suffix", ARMOR, 1, 7, 3,
     [worn("minecraft:speed", [0, 0, 1])]),
    ("of_the_bear", "of the Bear", "suffix", ARMOR, 2, 5, 3,
     [worn("minecraft:resistance", [0, 0, 0])]),
    ("of_the_owl", "of the Owl", "suffix", ARMOR, 2, 5, 3,
     [worn("minecraft:night_vision", [0, 0, 0], 220)]),
    ("of_the_tide", "of the Tide", "suffix", ARMOR, 2, 5, 3,
     [worn("minecraft:water_breathing", [0, 0, 0], 220)]),
    ("of_second_wind", "of Second Wind", "suffix", ARMOR, 3, 4, 3,
     [attr("elysiumlib:overheal", ADD, "any", 0.05, 0.2, 3)]),
    ("of_the_mountain", "of the Mountain", "suffix", ARMOR, 4, 2, 3,
     [attrs(one(ARMOUR, ADD, "any", 1.0, 4.0, 3),
            one(TOUGHNESS, ADD, "any", 0.5, 2.0, 3))]),

    # ---- shields -------------------------------------------------------
    ("bulwark", "Bulwark", "prefix", SHIELD, 0, 12, 3,
     [attr(KB_RESIST, ADD, "any", 0.05, 0.2, 3)]),
    ("banded", "Banded", "prefix", SHIELD, 0, 10, 3, [spares(0.1)]),
    ("towering", "Towering", "prefix", SHIELD, 1, 8, 3,
     [attr(ARMOUR, ADD, "any", 0.5, 2.0, 3)]),
    ("of_bracing", "of Bracing", "suffix", SHIELD, 0, 9, 3, [spares(0.1)]),
    ("of_the_wall", "of the Wall", "suffix", SHIELD, 1, 7, 3,
     [attr(TOUGHNESS, ADD, "any", 0.5, 2.0, 3)]),
    ("of_deflection", "of Deflection", "suffix", SHIELD, 2, 6, 3,
     [turns_aside("minecraft:is_projectile", 0.15)]),
    ("of_the_bastion", "of the Bastion", "suffix", SHIELD, 3, 4, 3,
     [turns_aside("minecraft:is_explosion", 0.2)]),
    ("of_repair", "of Repair", "suffix", SHIELD, 2, 5, 3, [mends(240, 0.2)]),

    # ---- curios --------------------------------------------------------
    ("gilded", "Gilded", "prefix", CURIO, 0, 10, 3,
     [attr(LUCK, ADD, "any", 0.5, 2.0, 3)]),
    ("scholars", "Scholar's", "prefix", CURIO, 1, 8, 3,
     [attr("elysiumlib:experience_gained", MULT, "any", 0.05, 0.25, 3)]),
    ("quickened", "Quickened", "prefix", CURIO, 2, 6, 3,
     [attr("elysiumlib:cooldown_reduction", ADD, "any", 0.03, 0.12, 3)]),
    ("charmed", "Charmed", "prefix", CURIO, 2, 6, 3,
     [attr("elysiumlib:dodge_chance", ADD, "any", 0.02, 0.06, 3)]),
    ("of_insight", "of Insight", "suffix", CURIO, 1, 7, 3,
     [attr("elysiumlib:experience_gained", MULT, "any", 0.05, 0.25, 3)]),
    ("of_vigour", "of Vigour", "suffix", CURIO, 0, 9, 3,
     [attr(HEALTH, ADD, "any", 1.0, 4.0, 3)]),
    ("of_the_magpie", "of the Magpie", "suffix", CURIO, 1, 7, 3, [draws(4.0)]),
    ("of_the_wanderer", "of the Wanderer", "suffix", CURIO, 1, 7, 3,
     [attr(MOVE, MULT, "any", 0.02, 0.06, 3)]),
    ("of_the_phoenix", "of the Phoenix", "suffix", CURIO, 3, 4, 3,
     [attr("elysiumlib:healing_received", ADD, "any", 0.1, 0.3, 3)]),
    ("of_fortune", "of Fortune", "suffix", CURIO, 3, 4, 3,
     [attr(LUCK, ADD, "any", 1.0, 3.5, 3)]),
    ("of_the_ascendant", "of the Ascendant", "suffix", CURIO, 4, 2, 3,
     [attrs(one(HEALTH, ADD, "any", 2.0, 8.0, 3),
            one(LUCK, ADD, "any", 1.0, 3.0, 3))]),
]


def merge_lang(entries, out):
    """Affix names go into the module's language file — but only when this is
    writing the tree it ships to. Run into a scratch directory, as the
    validator runs it to compare byte for byte, it must leave the shipped tree
    alone: writing the language file anyway would repair the very drift the
    comparison exists to notice."""
    if os.path.abspath(out) != os.path.abspath(OUT):
        return
    path = os.path.join(HERE, "src/main/resources/assets", NS, "lang/en_us.json")
    current = json.load(open(path, encoding="utf-8")) if os.path.isfile(path) else {}
    current.update(entries)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(dict(sorted(current.items())), f, indent=2, ensure_ascii=False)
        f.write("\n")


def main(out=OUT):
    os.makedirs(out, exist_ok=True)
    lang = {}
    seen = set()
    for aid, name, placement, on, min_tier, weight, max_level, effects in AFFIXES:
        if aid in seen:
            raise SystemExit("two affixes share the id " + aid)
        seen.add(aid)
        affix = {
            "type": placement,
            "on": on,
            "min_tier": min_tier,
            "weight": weight,
            "max_level": max_level,
            "effects": effects,
        }
        with open(os.path.join(out, aid + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(affix, f, indent=2)
            f.write("\n")
        lang[f"elysium.affix.{NS}.{aid}"] = name
    lang["elysium.affix.line"] = "%s"
    lang["elysium.affix.line.level"] = "%s %s"
    merge_lang(lang, out)
    prefixes = sum(1 for a in AFFIXES if a[2] == "prefix")
    gated = sum(1 for a in AFFIXES if a[4] > 0)
    print(f"wrote {len(AFFIXES)} affix(es); {prefixes} prefixes, "
          f"{len(AFFIXES) - prefixes} suffixes, {gated} gated behind a world tier")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else OUT)
