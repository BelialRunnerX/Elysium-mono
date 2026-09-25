#!/usr/bin/env python3
"""Generate elysium-armory's trait files from the Silent Gear inventory.

Source: docs/reference/silentgear_inventory.md, appendix T1 (all 75 traits with
their data effects) — read off the mod's own trait files. This turns the
appendix's compact notation into Elysium's trait format, which the game loads
from data/elysiumarmory/elysium_traits/*.json (SPEC_CLONE P4.3 and P4.5).

A trait whose behaviour was Java rather than data in the original is written out
with a `code` effect: the file still says what the trait is, and the behaviour
is in ArmoryTraitBehaviours (P4.6).

Re-runnable: the output is a pure function of the inventory.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(HERE, "tools"))
from gen_materials import rows                                   # noqa: E402  the same table reader

OUT = os.path.join(HERE, "src/main/resources/data/elysiumarmory/elysium_traits")
NS = "elysiumarmory"
NUMBER = r"-?\d+(?:\.\d+)?"


def ours(tag):
    """Silent Gear's own tags and ingredients, under our namespace."""
    return tag.replace("silentgear:", f"{NS}:")


def numbers(text):
    return [float(n) for n in re.findall(NUMBER, text)]


def condition(text):
    """The gear-condition column: 'on tool', '(on armor OR on curio)', ''."""
    text = text.strip()
    if not text:
        return []
    types = re.findall(r"on ([a-z_]+)", text)
    if " OR " in text:
        return ["any:" + ",".join("on:" + t for t in types)]
    return ["on:" + t for t in types]


def property_modifier(piece):
    """'propmod attack_speed +0.01xlvlxdmgRatio; harvest_speed +2.0xlvl'."""
    body = piece[len("propmod"):]
    modifiers = {}
    for term in body.split(";"):
        term = term.strip()
        if not term:
            continue
        match = re.match(r"([a-z_]+)\s*\+?(" + NUMBER + r")(.*)$", term.replace("+-", "-"))
        if not match:
            raise SystemExit("unreadable propmod: " + term)
        name, value, flags = match.groups()
        modifiers[name] = {"per_level": float(value),
                           "damage_ratio": "dmgRatio" in flags,
                           "of_value": "value" in flags}
    return {"type": "property", "modifiers": modifiers}


def wielder(piece):
    """'wielder potion [armor] resistance piece_count lvls[1, 1, 1, 2] dur38t'."""
    targets = []
    body = piece[len("wielder potion"):]
    for term in body.split(";"):
        term = term.strip()
        if not term:
            continue
        match = re.match(r"\[([a-z_/]+)\]\s+([a-z_]+)\s+([a-z_]+)\s+lvls\[([^\]]*)\]\s+dur(\d+)t", term)
        if not match:
            raise SystemExit("unreadable wielder effect: " + term)
        gear, effect, mode, levels, duration = match.groups()
        targets.append({"gear": gear, "effect": "minecraft:" + effect, "mode": mode,
                        "levels": [int(float(n)) for n in numbers(levels)], "duration": int(duration)})
    return {"type": "wielder_effect", "targets": targets}


def attribute(piece):
    """'attribute [all/any] generic.armor add_value [1.0, 2.0]; [armor/feet] ...'."""
    modifiers = []
    for term in piece[len("attribute"):].split(";"):
        term = term.strip()
        if not term:
            continue
        match = re.match(r"\[([a-z_/]+)\]\s+([a-z_.]+)\s+([a-z_]+)\s+\[([^\]]*)\]", term)
        if not match:
            raise SystemExit("unreadable attribute effect: " + term)
        slot, name, operation, values = match.groups()
        # Vanilla's own attributes keep their "generic."/"player." prefix in the
        # registry; the few without one are NeoForge's.
        registry = "minecraft:" + name if "." in name else "neoforge:" + name
        modifiers.append({"slot": slot.split("/")[-1], "attribute": registry,
                          "operation": operation, "values": numbers(values)})
    return {"type": "attribute", "modifiers": modifiers}


def parse(piece):
    piece = piece.strip()
    if piece.startswith("propmod"):
        return property_modifier(piece)
    if piece.startswith("wielder potion"):
        return wielder(piece)
    if piece.startswith("attribute"):
        return attribute(piece)
    if piece.startswith("extra dmg vs"):
        match = re.match(r"extra dmg vs ([a-z_]+)(?:\(([^)]*)\))?\s*\+?(" + NUMBER + r")", piece)
        kind, tag, value = match.groups()
        effect = {"type": "extra_damage", "against": kind, "per_level": float(value)}
        if tag:
            effect["tag"] = ours(tag)
        return effect
    if piece.startswith("durability:"):
        chance, amount = numbers(piece.replace("+-", "-"))[:2]
        return {"type": "durability", "chance_per_level": chance,
                "amount": int(amount) if "damage +" in piece else -int(abs(amount))}
    if piece.startswith("negate "):
        match = re.match(r"negate ([\w:]+) dmg (" + NUMBER + r")", piece)
        return {"type": "negate_damage", "tag": ours(match.group(1)), "scale_per_level": float(match.group(2))}
    if piece.startswith("clears "):
        return {"type": "cancel_effects", "effects": ["minecraft:" + piece[len("clears"):].strip()]}
    if piece.startswith("place "):
        match = re.match(r"place ([a-z_]+) on use, cost (\d+) dur, cooldown (\d+)t", piece)
        block, cost, cooldown = match.groups()
        return {"type": "block_placer", "block": ours("minecraft:" + block) if ":" not in block else block,
                "cost": int(cost), "cooldown": int(cooldown)}
    if piece.startswith("fill "):
        match = re.match(r"fill ([\w:]+)→([\w:]+) (\d)x\d+, (" + NUMBER + r") dur/block", piece)
        return {"type": "block_filler", "from": match.group(1), "to": match.group(2),
                "range": int(match.group(3)) // 2, "cost": float(match.group(4))}
    if piece.startswith("synergy"):
        match = re.match(r"synergy \+?(" + NUMBER + r")×lvl(?: if (" + NUMBER + r")<syn<(inf|" + NUMBER + r"))?",
                         piece.replace("+-", "-"))
        effect = {"type": "synergy", "per_level": float(match.group(1))}
        if match.group(2):
            effect["min"] = float(match.group(2))
            effect["max"] = 1.0E9 if match.group(3) == "inf" else float(match.group(3))
        return effect
    if piece.startswith("enchant "):
        enchantments = {}
        for term in piece[len("enchant"):].split(";"):
            match = re.match(r"\s*([a-z_]+) by lvl \[([^\]]*)\]", term)
            if match:
                enchantments["minecraft:" + match.group(1)] = [int(float(n)) for n in numbers(match.group(2))]
        return {"type": "enchantment", "enchantments": enchantments}
    if piece.startswith("bonus drops"):
        match = re.match(r"bonus drops ([\w:]+): chance (" + NUMBER + r")×lvl, mult (" + NUMBER + r")", piece)
        return {"type": "bonus_drops", "ingredient": ours(match.group(1)),
                "chance_per_level": float(match.group(2)), "multiplier": float(match.group(3))}
    if piece.startswith("mining speed"):
        match = re.match(r"mining speed ×\(1\+(" + NUMBER + r")×lvl\) on ([\w:]+)", piece)
        return {"type": "mining_speed", "per_level": float(match.group(1)), "tag": ours(match.group(2))}
    if piece.startswith("magnet"):
        match = re.match(r"magnet range (" + NUMBER + r")\(\+lvl\) pull (" + NUMBER + r")(?: items ([\w:]+))?",
                         piece)
        effect = {"type": "item_magnet", "range": float(match.group(1)), "pull": float(match.group(2))}
        if match.group(3):
            effect["items"] = ours(match.group(3))
        return effect
    if piece.startswith("self-repair"):
        interval, chance, amount = numbers(piece)[:3]
        return {"type": "self_repair", "interval": int(interval), "chance_per_level": chance,
                "amount_per_level": int(amount)}
    if piece.startswith("target "):
        match = re.match(r"target ([a-z_]+) L1:(\d+)t", piece)
        return {"type": "target_effect", "effect": "minecraft:" + match.group(1),
                "ticks_per_level": int(match.group(2))}
    if piece == "fireproof":
        return {"type": "fireproof"}
    if piece == "wind_blast":
        return {"type": "wind_blast"}
    if piece.startswith("food "):
        return {"type": "components", "components": {"minecraft:food": json.loads(piece[len("food"):].strip())}}
    if piece.startswith("(code"):
        return {"type": "code"}
    raise SystemExit("unreadable trait effect: " + piece)


def merge_lang(entries):
    """Trait names and descriptions go into the module's language file, beside
    everything else the art generator writes."""
    path = os.path.join(HERE, "src/main/resources/assets", NS, "lang/en_us.json")
    current = json.load(open(path, encoding="utf-8")) if os.path.isfile(path) else {}
    current.update(entries)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(dict(sorted(current.items())), f, indent=2, ensure_ascii=False)
        f.write("\n")


def main(out=OUT):
    written = 0
    coded = 0
    lang = {}
    for cells in rows("Appendix T1: all 75 traits"):
        tid, name, max_level, gear, effects, description = cells[1], cells[2], cells[3], cells[4], cells[5], cells[6]
        trait = {"name": name, "max_level": int(max_level), "description": description}
        conditions = condition(gear)
        if conditions:
            trait["conditions"] = conditions
        parsed = [parse(piece) for piece in effects.split("<br>") if piece.strip()]
        trait["effects"] = parsed
        coded += any(e["type"] == "code" for e in parsed)
        os.makedirs(out, exist_ok=True)
        with open(os.path.join(out, tid + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(trait, f, indent=2)
            f.write("\n")
        lang[f"{NS}.trait.{tid}"] = name
        lang[f"{NS}.trait.{tid}.desc"] = description
        written += 1
    if os.path.abspath(out) == os.path.abspath(OUT):
        # Run into a scratch directory — which is how the validator re-runs this
        # to compare byte for byte — the generator must leave the shipped tree
        # alone. Writing the language file anyway would repair the very drift
        # the check exists to notice.
        merge_lang(lang)
    print(f"wrote {written} trait(s); {coded} of them carry behaviour in code")


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else OUT))
