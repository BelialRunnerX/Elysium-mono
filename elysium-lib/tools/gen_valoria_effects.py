#!/usr/bin/env python3
"""
Valoria's effects, attributes and enchantments in the library: the data files
and the words. SPLITS.md row "Valoria: effects".

    python3 tools/gen_valoria_effects.py

Writes the three enchantments and the tags they apply to, the two damage types
the Null field deals, the tag entries that make them bypass armour, and every
language key the feature shows a player. The attribute and effect names are
read out of the Java rather than typed again, so a row added to
ElysiumAttributeSpec or an effect added to ElysiumEffects is a name here and
never a raw key in the game.

The language file is shared with the rest of the library and kept by hand
there, so this generator reads the committed file as its base, adds or replaces
its own keys, and writes the whole file back sorted — which is also what lets
validate.py run it into a scratch directory and diff. LF on every platform: a
generator that wrote CRLF on Windows would make every file drift on Linux.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumlib"
OUT = os.environ.get("ELYSIUM_LIB_OUT", MOD)
RES = os.path.join(OUT, "src/main/resources")
BASE_RES = os.path.join(MOD, "src/main/resources")
SPEC = os.path.join(MOD, "src/main/java/com/elysium/lib/attribute/ElysiumAttributeSpec.java")
EFFECTS = os.path.join(MOD, "src/main/java/com/elysium/lib/combat/ElysiumEffects.java")


def write(rel, obj):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(obj, handle, indent=2, ensure_ascii=False)
        handle.write("\n")


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


# ---------------------------------------------------------------------------
# What the Java says
# ---------------------------------------------------------------------------

def valoria_attributes():
    """The rows Valoria brought, read from VALORIA_ROWS in the spec."""
    source = read(SPEC)
    table = re.search(r"VALORIA_ROWS = List\.of\((.*?)\);", source, re.S)
    if not table:
        sys.exit("cannot find VALORIA_ROWS in ElysiumAttributeSpec.java")
    rows = re.findall(r'"([a-z_]+)"', table.group(1))
    if not rows:
        sys.exit("VALORIA_ROWS is empty")
    return rows


def effects():
    source = read(EFFECTS)
    return re.findall(r'EFFECTS\.register\("([a-z_]+)"', source)


# ---------------------------------------------------------------------------
# The words
# ---------------------------------------------------------------------------

ATTRIBUTE_NAMES = {
    "dash_distance": "Dash Distance",
    "strike_radius": "Strike Radius",
    "summon_damage": "Summon Damage",
    "summon_count": "Summon Count",
    "summon_lifetime": "Summon Lifetime",
    "miss_chance": "Miss Chance",
    "element_resistance": "Elemental Resistance",
    "null_capacity": "Null Capacity",
    "null_resilience": "Null Resilience",
    "null_resistance": "Null Resistance",
    "null_damage": "Null Damage",
}
ELEMENT_WORDS = {"void": "Void", "plasma": "Plasma", "neural": "Neural",
                 "dimensional": "Dimensional", "kinetic": "Kinetic"}

EFFECT_NAMES = {
    "salve": "Salve",
    "stunned": "Stunned",
    "tipsy": "Tipsy",
    "exhaustion": "Exhaustion",
    "renewal": "Renewal",
    "foresight": "Foresight",
    "overload": "Overload",
    "null_ward": "Null Ward",
}

ENCHANTMENTS = {
    "bleeding": ("Bleeding", "Every hit opens a wound that keeps bleeding, through armour."),
    "accuracy": ("Accuracy", "Tightens the spread of an overcharged shot."),
    "explosive_charge": ("Explosive Charge", "A charged blast that also explodes."),
}

LANG = {
    "elysium.hazard.null": "Null",
    "elysium.hazard.null.cheap": "Leave the field, get under a filed claim, or eat a nullward ration.",
    "elysium.hazard.null.engineered": "Void-aligned armour, a sealed room, and the residue vessel.",
    "elysium.null.rising": "The field is in you. Past half, it hurts.",
    "elysium.null.speaks": "The field is speaking. Leave, or be reclaimed.",
    "elysium.null.extracted": "The Nullspire failsafe pulled you out. The gauge is clear.",
    "elysium.combat.missed": "Missed.",
    "death.attack.elysiumlib.null": "%1$s was saturated by the field",
    "death.attack.elysiumlib.null.player": "%1$s was saturated by the field while fighting %2$s",
    "death.attack.elysiumlib.null_collapse": "%1$s was reclaimed by the field",
    "death.attack.elysiumlib.null_collapse.player": "%1$s was reclaimed by the field while fighting %2$s",
}


def main():
    lang = dict(LANG)
    for row in valoria_attributes():
        name = ATTRIBUTE_NAMES.get(row)
        if name is None:
            element, _, kind = row.partition("_")
            if element in ELEMENT_WORDS and kind in ("damage", "resistance"):
                name = f"{ELEMENT_WORDS[element]} {kind.capitalize()}"
        if name is None:
            sys.exit(f"no display name for attribute {row}")
        lang[f"attribute.{NS}.{row}"] = name
    registered = effects()
    for effect, name in EFFECT_NAMES.items():
        if effect not in registered and effect != "null_ward":
            sys.exit(f"{effect} is named here and not registered in ElysiumEffects")
        lang[f"effect.{NS}.{effect}"] = name
    for enchantment, (name, desc) in ENCHANTMENTS.items():
        lang[f"enchantment.{NS}.{enchantment}"] = name
        lang[f"enchantment.{NS}.{enchantment}.desc"] = desc

    # --- enchantments: Valoria's own numbers -------------------------------
    cost = {"min_cost": {"base": 10, "per_level_above_first": 20},
            "max_cost": {"base": 61, "per_level_above_first": 10}, "anvil_cost": 8}
    write(f"data/{NS}/enchantment/bleeding.json", {
        "description": {"translate": f"enchantment.{NS}.bleeding"},
        "supported_items": "#minecraft:enchantable/sword",
        "weight": 1, "max_level": 3, **cost, "slots": ["mainhand"],
        "effects": {"minecraft:post_attack": [{
            "enchanted": "attacker", "affected": "victim",
            "effect": {"type": "minecraft:apply_mob_effect",
                       "to_apply": f"{NS}:bleeding",
                       "min_duration": 3.0, "max_duration": 3.0,
                       "min_amplifier": 0.0, "max_amplifier": 0.0},
            "requirements": {"condition": "minecraft:random_chance",
                             "chance": {"type": "minecraft:enchantment_level",
                                        "amount": {"type": "minecraft:linear",
                                                   "base": 0.1, "per_level_above_first": 0.1}}}}]}})
    write(f"data/{NS}/enchantment/accuracy.json", {
        "description": {"translate": f"enchantment.{NS}.accuracy"},
        "supported_items": f"#{NS}:enchantable/accuracy",
        "weight": 1, "max_level": 3, **cost, "slots": ["mainhand"]})
    write(f"data/{NS}/enchantment/explosive_charge.json", {
        "description": {"translate": f"enchantment.{NS}.explosive_charge"},
        "supported_items": f"#{NS}:enchantable/charge",
        "weight": 2, "max_level": 1,
        "min_cost": {"base": 11, "per_level_above_first": 10},
        "max_cost": {"base": 16, "per_level_above_first": 10},
        "anvil_cost": 4, "slots": ["mainhand"],
        "exclusive_set": ["minecraft:fire_aspect"]})
    for tag in ("accuracy", "charge"):
        write(f"data/{NS}/tags/item/enchantable/{tag}.json", {"replace": False, "values": []})
    write("data/minecraft/tags/enchantment/in_enchanting_table.json",
          {"replace": False, "values": [f"{NS}:bleeding", f"{NS}:accuracy", f"{NS}:explosive_charge"]})
    write("data/minecraft/tags/enchantment/non_treasure.json",
          {"replace": False, "values": [f"{NS}:bleeding", f"{NS}:accuracy", f"{NS}:explosive_charge"]})

    # --- the field's damage ------------------------------------------------
    write(f"data/{NS}/damage_type/null.json", {
        "message_id": f"{NS}.null", "exhaustion": 0.0, "scaling": "never"})
    write(f"data/{NS}/damage_type/null_collapse.json", {
        "message_id": f"{NS}.null_collapse", "exhaustion": 0.0, "scaling": "never"})
    for tag in ("bypasses_armor", "bypasses_effects", "bypasses_enchantments"):
        path = os.path.join(BASE_RES, f"data/minecraft/tags/damage_type/{tag}.json")
        values = json.load(open(path, encoding="utf-8"))["values"] if os.path.isfile(path) else []
        for wanted in (f"{NS}:null", f"{NS}:null_collapse"):
            if wanted not in values:
                values.append(wanted)
        write(f"data/minecraft/tags/damage_type/{tag}.json", {"replace": False, "values": values})
    path = os.path.join(BASE_RES, f"data/{NS}/tags/damage_type/is_non_physical.json")
    values = json.load(open(path, encoding="utf-8"))["values"] if os.path.isfile(path) else []
    for wanted in (f"{NS}:null", f"{NS}:null_collapse"):
        if wanted not in values:
            values.append(wanted)
    write(f"data/{NS}/tags/damage_type/is_non_physical.json", {"replace": False, "values": values})

    # --- the language file, on top of the committed one ---------------------
    base_path = os.path.join(BASE_RES, f"assets/{NS}/lang/en_us.json")
    base = json.load(open(base_path, encoding="utf-8")) if os.path.isfile(base_path) else {}
    base.update(lang)
    write(f"assets/{NS}/lang/en_us.json", dict(sorted(base.items())))
    print(f"elysium-lib (valoria effects): {len(valoria_attributes())} attributes, "
          f"{len(EFFECT_NAMES)} effects, {len(ENCHANTMENTS)} enchantments, 2 damage types, {len(lang)} words")


if __name__ == "__main__":
    main()
