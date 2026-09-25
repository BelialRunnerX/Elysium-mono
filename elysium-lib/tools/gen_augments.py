#!/usr/bin/env python3
"""Generate the mob augments (SPEC_CLONE P9.3, P9.4).

An augment is one thing that can be wrong with a creature. The table below is
the whole catalogue: how often it is drawn, the world tier below which it does
not exist, what it multiplies, and what it is permanently under the effect of.

Two rules the table keeps, both checked by the validator:

  * Multipliers, not additions. A creature's base values vary by orders of
    magnitude between a chicken and a warden, so a flat bonus is nothing to one
    and everything to the other.
  * Every augment does something. An augment that multiplies by 1 and grants no
    effect is a word in a creature's name and nothing else.

Re-runnable: the output is a pure function of this table.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NS = "elysiumlib"
OUT = os.path.join(HERE, "src/main/resources/data", NS, "elysium_mob_augments")

SPEED = "minecraft:generic.movement_speed"
HEALTH = "minecraft:generic.max_health"
DAMAGE = "minecraft:generic.attack_damage"
ARMOUR = "minecraft:generic.armor"
KNOCKBACK = "minecraft:generic.attack_knockback"
RESIST = "minecraft:generic.knockback_resistance"
FOLLOW = "minecraft:generic.follow_range"

# id, name, weight, min world tier, attribute multipliers, permanent effects
AUGMENTS = [
    ("fleet", "Fleet", 12, 1, {SPEED: 1.35}, []),
    ("dogged", "Dogged", 10, 1, {FOLLOW: 2.0, SPEED: 1.1}, []),
    ("stout", "Stout", 12, 1, {HEALTH: 2.0}, []),
    ("hardened", "Hardened", 10, 1, {ARMOUR: 2.5}, []),
    ("cruel", "Cruel", 10, 1, {DAMAGE: 1.5}, []),
    ("heavy_handed", "Heavy-Handed", 8, 1, {KNOCKBACK: 3.0, DAMAGE: 1.2}, []),
    ("rooted", "Rooted", 8, 2, {RESIST: 4.0}, []),
    ("quickened", "Quickened", 8, 2, {SPEED: 1.2}, ["minecraft:speed"]),
    ("shielded", "Shielded", 7, 2, {}, ["minecraft:resistance"]),
    ("regenerating", "Regenerating", 6, 2, {}, ["minecraft:regeneration"]),
    ("fireproof", "Fireproof", 7, 2, {}, ["minecraft:fire_resistance"]),
    ("hidden", "Hidden", 4, 3, {}, ["minecraft:invisibility"]),
    ("towering", "Towering", 5, 3, {HEALTH: 3.0, KNOCKBACK: 2.0}, []),
    ("vicious", "Vicious", 5, 3, {DAMAGE: 2.0}, ["minecraft:strength"]),
    ("adamant", "Adamant", 4, 4, {ARMOUR: 4.0, HEALTH: 2.0}, ["minecraft:resistance"]),
    ("relentless", "Relentless", 3, 4, {SPEED: 1.5, FOLLOW: 3.0},
     ["minecraft:speed", "minecraft:regeneration"]),
]


def merge_lang(entries, out):
    """Augment names go into the language file, but only when this is writing
    the tree it ships to — the validator runs it into a scratch directory to
    compare byte for byte, and writing the language file from there would
    repair the very drift the comparison exists to notice."""
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
    for aid, name, weight, min_tier, attributes, effects in AUGMENTS:
        augment = {"weight": weight, "min_tier": min_tier}
        if attributes:
            augment["attributes"] = attributes
        if effects:
            augment["effects"] = [{"effect": effect} for effect in effects]
        with open(os.path.join(out, aid + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(augment, f, indent=2)
            f.write("\n")
        lang[f"elysium.augment.{NS}.{aid}"] = name
    lang["elysium.rank.elite"] = "Elite"
    lang["elysium.rank.invader"] = "Invader"
    merge_lang(lang, out)
    gated = sum(1 for a in AUGMENTS if a[3] > 1)
    print(f"wrote {len(AUGMENTS)} augment(s); {gated} gated past the first tier")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else OUT)
