#!/usr/bin/env python3
"""
A charter per world, and the list of what a settlement consumes.

The five Elysium worlds' hazards and air are not invented here: they are the
numbers elysium-space already declares in world/SpaceWorld.java, read out of
that file so the two cannot drift. What is decided here is the material half —
which families each world produces — and the reach, which is how far out each
one is.
"""
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
REPO = os.path.dirname(MOD)
NS = "elysiumcolonies"
DATA = os.path.join(MOD, "src/main/resources/data", NS)

SPACE = os.path.join(REPO, "elysium-space/src/main/java/com/elysium/space/world/SpaceWorld.java")

# ---------------------------------------------------------------------------
# What a settlement actually consumes.
#
# Kept apart from the charters on purpose: a world's charter is a fact about
# the world, and this is a fact about towns. A world that produces forty exotic
# materials a town never touches is not a rich world to settle, and the overlap
# between the two lists is the arithmetic that says so.
# ---------------------------------------------------------------------------
NEEDS = [
    "coal", "copper", "iron", "gold", "redstone", "lapis", "diamond", "emerald",
    "quartz", "tin", "zinc", "lead", "nickel", "aluminum", "silver", "steel",
    "lithium", "platinum",
]

# ---------------------------------------------------------------------------
# What each world produces, and how far out it is.
#
# The material lists are the families that actually generate there — Elysium's
# own ores plus whatever vanilla puts in that stone — so a charter is a claim
# about the ground rather than a flavour text.
# ---------------------------------------------------------------------------
WORLDS = {
    "minecraft:overworld": {
        "natives": ["coal", "copper", "iron", "gold", "redstone", "lapis",
                    "diamond", "emerald", "quartz", "tin", "zinc", "lead",
                    "nickel", "aluminum", "silver", "steel", "lithium"],
        "reach": 0,
    },
    "minecraft:the_nether": {
        "natives": ["quartz", "gold", "lead", "zinc", "nickel"],
        "reach": 1,
    },
    "minecraft:the_end": {
        "natives": ["platinum", "diamond", "emerald"],
        "reach": 2,
    },
    # Low orbit: a station, not a ground. Nothing is native to it at all, which
    # is the point of a station — everything it has, somebody carried up.
    "elysiumspace:high_orbit": {"natives": [], "reach": 3, "style": "High Orbit"},
    # The moon: iron and steel in the pale stone, and its own lunite.
    "elysiumspace:pale_moon": {
        "style": "Pale Reach",
        "natives": ["iron", "steel", "silver", "lead", "tin"],
        "reach": 2,
    },
    # Cinderveil: hot, and rich in exactly the things heat leaves behind.
    "elysiumspace:cinderveil": {
        "style": "Cinderhold",
        "natives": ["coal", "gold", "diamond", "copper", "iron", "zinc"],
        "reach": 4,
    },
    # Frostfall: iron and diamond in the frost stone, and little else.
    "elysiumspace:frostfall": {
        "style": "Frostfall",
        "natives": ["iron", "diamond", "tin", "nickel"],
        "reach": 5,
    },
    # Emerald Bastion: breathable, safe, and absurdly rich. The reward world.
    "elysiumspace:emerald_bastion": {
        "style": "Bastion",
        "natives": ["coal", "copper", "iron", "gold", "redstone", "lapis",
                    "diamond", "emerald"],
        "reach": 6,
    },
}


def space_worlds():
    """
    Hazard totals and air, read out of elysium-space rather than restated.

    Parses the SpaceWorld.ALL table. If the file is not there — a build of this
    module without the space module beside it — every world falls back to
    breathable and harmless, which is the correct answer when the worlds do not
    exist either.
    """
    if not os.path.isfile(SPACE):
        return {}
    text = open(SPACE, encoding="utf-8").read()
    out = {}
    for match in re.finditer(
            r'new SpaceWorld\(\s*"([a-z_]+)"\s*,\s*[0-9.]+[DdFf]?\s*,\s*Air\.(\w+)\s*,'
            r'\s*of\(([^)]*)\)', text, re.S):
        path, air, hazards = match.groups()
        total = sum(float(value) for value in re.findall(r'([0-9.]+)F', hazards))
        out["elysiumspace:" + path] = {
            "hazard": round(total, 3),
            "air": {"BREATHABLE": "breathable", "NONE": "none"}.get(air, "poor"),
        }
    return out


# ---------------------------------------------------------------------------
# What a settlement builds, in the order it wants it.
#
# The builder is first and it is not a matter of taste: a colony with no
# builder cannot raise anything at all, including the builder. After that the
# order is what keeps people alive and working — somewhere to live, somewhere
# to put things, something to eat — and only then the trades.
#
# The population figures are when a town is big enough to want the thing, not
# when it is able to staff it; MineColonies decides staffing.
# ---------------------------------------------------------------------------
PLAN = [
    ("minecolonies:builder", 0),
    ("minecolonies:residence", 0),   # MineColonies calls a home a residence
    ("minecolonies:warehouse", 3),
    ("minecolonies:deliveryman", 3),
    ("minecolonies:farmer", 4),
    ("minecolonies:cook", 5),
    ("minecolonies:lumberjack", 6),
    ("minecolonies:miner", 7),
    ("minecolonies:fisherman", 8),   # there is no forester; the fisherman feeds a town of that size
    ("minecolonies:guardtower", 9),
    ("minecolonies:sawmill", 10),
    ("minecolonies:stonemason", 12),
    ("minecolonies:blacksmith", 14),
    ("minecolonies:hospital", 16),
    ("minecolonies:library", 18),
    ("minecolonies:barracks", 20),
]


# ---------------------------------------------------------------------------
# What things are worth, before where and when.
#
# Per material family first, because a family covers every mod's version of a
# material at once and that is the whole point of having unified them. The
# per-item table is for things with no family at all.
#
# The numbers are relative to MarketRules.BASE_VALUE (100) and are meant to be
# read as "how much work is this". Raw stone is nearly free; a diamond is a
# day's mining; platinum is a day's mining on a world nobody lives on.
# ---------------------------------------------------------------------------
FAMILY_VALUES = {
    "coal": 40, "copper": 60, "iron": 90, "redstone": 70, "lapis": 80,
    "quartz": 70, "tin": 80, "zinc": 85, "lead": 95, "nickel": 110,
    "aluminum": 110, "lithium": 140, "silver": 150, "steel": 170,
    "gold": 180, "diamond": 320, "emerald": 300, "platinum": 380,
    "fluorite": 200, "certus_quartz": 160, "osmium": 210, "uranium": 260,
    "uraninite": 240, "crimson_iron": 230, "azure_silver": 280, "bort": 200,
    "cindrium": 300, "cryonite": 300, "frostite": 280, "lunite": 260,
    "rime_shard": 220,
}

ITEM_VALUES = {
    "minecraft:cobblestone": 4,
    "minecraft:dirt": 2,
    "minecraft:sand": 5,
    "minecraft:gravel": 5,
    "minecraft:oak_log": 20,
    "minecraft:oak_planks": 6,
    "minecraft:stick": 3,
    "minecraft:stone": 6,
    "minecraft:stone_bricks": 10,
    "minecraft:glass": 25,
    "minecraft:torch": 12,
    "minecraft:bread": 30,
    "minecraft:wheat": 15,
    "minecraft:string": 45,
    "minecraft:leather": 60,
    "minecraft:paper": 20,
    "minecraft:book": 90,
    "minecraft:clay_ball": 12,
    "minecraft:bricks": 40,
    "minecraft:charcoal": 40,
    "minecraft:ancient_debris": 900,
    "minecraft:netherite_ingot": 1600,
}


def write(path, body):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w", encoding="utf-8", newline="\n").write(json.dumps(body, indent=2) + "\n")


def main():
    read = space_worlds()
    written = 0
    for dimension, facts in WORLDS.items():
        hazard = read.get(dimension, {}).get("hazard", 0.0)
        air = read.get(dimension, {}).get("air", "breathable")
        name = dimension.split(":", 1)[1]
        write(os.path.join(DATA, f"charter/{name}.json"), {
            "dimension": dimension,
            "natives": facts["natives"],
            "hazard": hazard,
            "air": air,
            "reach": facts["reach"],
            "style": facts.get("style", ""),
            # Towns found themselves on every world with a charter of its
            # own except the two vanilla ones nobody lives on: the Nether
            # burns and the End is an island.
            "settles": dimension not in ("minecraft:the_nether", "minecraft:the_end"),
        })
        written += 1
    write(os.path.join(DATA, "charter_needs/settlement_needs.json"), {"needs": NEEDS})
    write(os.path.join(DATA, "charter_needs/settlement_plan.json"),
          {"wants": [{"building": b, "from": n} for b, n in PLAN]})
    write(os.path.join(DATA, "charter_needs/market_values.json"),
          {"families": FAMILY_VALUES, "items": ITEM_VALUES})
    missed = [d for d in WORLDS if d.startswith("elysiumspace:") and d not in read]
    print(f"charters          : {written}")
    print(f"settlement needs  : {len(NEEDS)} famil(ies)")
    print(f"settlement plan   : {len(PLAN)} building(s), builder first")
    print(f"market values     : {len(FAMILY_VALUES)} famil(ies), {len(ITEM_VALUES)} item(s)")
    print(f"hazards read      : {len(read)} world(s) from elysium-space"
          + (f"; MISSING {', '.join(missed)}" if missed else ""))


if __name__ == "__main__":
    main()
