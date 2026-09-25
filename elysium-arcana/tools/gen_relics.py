#!/usr/bin/env python3
"""
Thirty relics: a sprite, a model, a recipe and a name apiece, and the words
the tooltip reads.

    python3 tools/gen_relics.py

The roster is read out of RelicRules.java and the abilities out of the
library's ElysiumAbilityRules.java, so an ability renamed in the library
renames the line in every tooltip that mentions it rather than leaving thirty
translation keys pointing at nothing.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
ROOT = os.path.dirname(MOD)
NS = "elysiumarcana"

OUT = os.environ.get("ELYSIUM_RELICS_OUT", MOD)
ASSETS = os.path.join(OUT, "src/main/resources/assets", NS)
DATA = os.path.join(OUT, "src/main/resources/data", NS)

RULES = os.path.join(MOD, "src/main/java/com/elysium/arcana/rules/RelicRules.java")
ABILITIES = os.path.join(
    ROOT, "elysium-lib/src/main/java/com/elysium/lib/ability/ElysiumAbilityRules.java")

try:
    from PIL import Image, ImageDraw
except ImportError:
    print("Pillow is needed to draw the sprites: pip install pillow")
    sys.exit(1)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(obj, handle, indent=2)
        handle.write("\n")


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


SOURCE = read(RULES)
ABILITY_SOURCE = read(ABILITIES)

RELICS = []
for rid, slot, rarity, abils in re.findall(
        r'relic\("([a-z_]+)", "([a-z]+)", (\d+), List\.of\(([^)]*)\)\)', SOURCE):
    RELICS.append({"id": rid, "slot": slot, "rarity": int(rarity),
                   "abilities": re.findall(r'"([a-z_]+)"', abils)})

ABILITY = {}
for aid, kind, trigger, cooldown, cost, power, scales, domain in re.findall(
        r'made\("([a-z_]+)", ([A-Z]+), "([a-z_]+)", (\d+), (\d+), ([\d.]+)F, '
        r'"([a-z]*)", "([a-z_]+)"\)', ABILITY_SOURCE):
    ABILITY[aid] = {"kind": kind.lower(), "trigger": trigger,
                    "cooldown": int(cooldown), "cost": int(cost),
                    "power": float(power), "scales": scales, "domain": domain}

SLOTS = re.findall(r'"([a-z]+)"', re.search(
    r'public static final List<String> SLOTS = List\.of\(([^;]*?)\);',
    SOURCE, re.S).group(1))

if not (RELICS and ABILITY and SLOTS):
    print("the relic sources no longer have the shape this reads")
    sys.exit(2)

missing = sorted({a for r in RELICS for a in r["abilities"]} - set(ABILITY))
if missing:
    print("relics name abilities the library does not have:", missing)
    sys.exit(2)


def java_hash(text):
    value = 0
    for char in text:
        value = (value * 31 + ord(char)) & 0xFFFFFFFF
    if value >= 0x80000000:
        value -= 0x100000000
    return abs(value)


#: A colour band per rarity, so a relic reads at a glance before its tooltip.
BAND = {1: 0x9AA0A8, 2: 0x6FB46A, 3: 0x5A86D8, 4: 0xB06AD8}

#: A shape per slot, so two relics of the same rarity are still told apart.
SHAPE = {"charm": "drop", "ring": "ring", "belt": "band", "cloak": "cloth",
         "amulet": "gem", "mask": "mask", "boots": "boot", "relic": "star"}


def shade(name, base, spread):
    value = java_hash(name)
    span = 2 * spread + 1
    red = min(255, max(0, ((base >> 16) & 0xFF) + (value % span) - spread))
    green = min(255, max(0, ((base >> 8) & 0xFF) + ((value // 7) % span) - spread))
    blue = min(255, max(0, (base & 0xFF) + ((value // 53) % span) - spread))
    return red, green, blue


def darker(colour, by=48):
    return tuple(max(0, channel - by) for channel in colour)


def lighter(colour, by=44):
    return tuple(min(255, channel + by) for channel in colour)


def sprite(relic):
    core = shade(relic["id"], BAND[relic["rarity"]], 28)
    edge = darker(core)
    glow = lighter(core, 60)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    shape = SHAPE[relic["slot"]]
    if shape == "ring":
        draw.ellipse([3, 3, 12, 12], outline=core + (255,), width=2)
        draw.point((7, 3), fill=glow + (255,))
    elif shape == "drop":
        draw.polygon([(8, 2), (12, 9), (8, 14), (4, 9)], fill=core + (255,),
                     outline=edge + (255,))
        draw.point((7, 7), fill=glow + (255,))
    elif shape == "band":
        draw.rectangle([2, 6, 13, 10], fill=core + (255,), outline=edge + (255,))
        draw.rectangle([6, 5, 9, 11], fill=glow + (255,), outline=edge + (255,))
    elif shape == "cloth":
        draw.polygon([(3, 3), (12, 3), (13, 13), (8, 10), (3, 13)],
                     fill=core + (255,), outline=edge + (255,))
    elif shape == "gem":
        draw.polygon([(8, 3), (12, 7), (8, 13), (4, 7)], fill=core + (255,),
                     outline=edge + (255,))
        draw.line([(8, 5), (8, 11)], fill=glow + (255,))
    elif shape == "mask":
        draw.ellipse([3, 3, 12, 13], fill=core + (255,), outline=edge + (255,))
        draw.rectangle([5, 6, 6, 8], fill=(18, 18, 22, 255))
        draw.rectangle([9, 6, 10, 8], fill=(18, 18, 22, 255))
    elif shape == "boot":
        draw.polygon([(5, 2), (9, 2), (9, 10), (13, 10), (13, 14), (5, 14)],
                     fill=core + (255,), outline=edge + (255,))
    else:
        draw.polygon([(8, 1), (10, 6), (15, 8), (10, 10), (8, 15), (6, 10),
                      (1, 8), (6, 6)], fill=core + (255,), outline=edge + (255,))
        draw.point((8, 8), fill=glow + (255,))
    return image


def a_name(text):
    return " ".join(word.capitalize() for word in text.split("_"))


#: What each ability reads as in a tooltip. Short, because a tooltip with four
#: lines of prose in it is a tooltip nobody reads.
SAYS = {
    "steadfast": "Harder to shift", "swiftfoot": "Quicker on your feet",
    "keen_edge": "Sharper", "deep_lungs": "Longer underwater",
    "sure_grip": "Steadier hands", "night_eyes": "Sees in the dark",
    "ironhide": "Tougher", "scholar": "Learns faster",
    "riposte": "Strikes back when struck", "thornmail": "Turns a blow back",
    "second_wind": "Catches you when you fall", "bloodscent": "Stronger after a kill",
    "soul_harvest": "Takes something from the dead", "cleave": "Cuts through",
    "sunder": "Breaks armour", "chill_touch": "Slows what it hits",
    "ember_brand": "Sets alight", "static_arc": "Arcs to what is near",
    "lifeline": "Shields you at the edge", "mender": "Knits wounds slowly",
    "gleaner": "Finds a little more", "ward_pulse": "Wards those beside you",
    "blink": "Step through space", "shockwave": "Knock everything back",
    "bulwark": "Stand and hold", "hunters_mark": "Mark your quarry",
    "veilstep": "Step out of sight", "gravewake": "Call up the dead",
    "stormcall": "Call down the storm", "sanctuary": "Make somewhere safe",
}


def main():
    for relic in RELICS:
        sprite(relic).save_path = None
        path = os.path.join(ASSETS, "textures/item", relic["id"] + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        sprite(relic).save(path)
        write(os.path.join(ASSETS, "models/item", relic["id"] + ".json"),
              {"parent": "minecraft:item/generated",
               "textures": {"layer0": f"{NS}:item/{relic['id']}"}})
        # A relic is found, not crafted. What it costs is the shards of the
        # material it is attuned to plus a gold frame, which is the one recipe
        # shape that works for all thirty without inventing thirty stories.
        shard = ["iron", "gold", "diamond", "emerald"][relic["rarity"] - 1]
        write(os.path.join(DATA, "recipe", relic["id"] + ".json"), {
            "type": "minecraft:crafting_shaped",
            "category": "equipment",
            "pattern": ["SGS", "G G", "SGS"],
            "key": {"S": {"item": f"{NS}:{shard}_shard"},
                    "G": {"item": "minecraft:gold_ingot"}},
            "result": {"id": f"{NS}:{relic['id']}", "count": 1},
        })

    words = {}
    for relic in RELICS:
        words[f"item.{NS}.{relic['id']}"] = a_name(relic["id"])
    for slot in SLOTS:
        words[f"{NS}.slot.{slot}"] = a_name(slot)
    for name, ability in sorted(ABILITY.items()):
        words[f"{NS}.ability.{name}"] = SAYS.get(name, a_name(name))
    words[f"{NS}.relic.slot"] = "Worn: %s"
    words[f"{NS}.relic.always"] = "  always"
    words[f"{NS}.relic.every"] = "  every %ss"

    committed = os.path.join(MOD, "src/main/resources/assets", NS, "lang/en_us.json")
    path = os.path.join(ASSETS, "lang/en_us.json")
    existing = json.loads(read(committed)) if os.path.exists(committed) else {}
    for key in set(words):
        existing.pop(key, None)
    # Anything this used to write for an ability that no longer exists goes.
    for key in [k for k in existing if k.startswith(f"{NS}.ability.")]:
        if key.split(".")[-1] not in ABILITY:
            existing.pop(key)
    existing.update(words)
    write(path, dict(sorted(existing.items())))

    print(f"relics: {len(RELICS)} across {len({r['slot'] for r in RELICS})} slot(s), "
          f"{len(ABILITY)} ability line(s), {len(RELICS)} sprite(s) and recipe(s)")


if __name__ == "__main__":
    main()
