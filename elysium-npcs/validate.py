#!/usr/bin/env python3
"""
Cross-check the court declared in Java against the shipped resources.

Run from the repo root:   python3 validate.py

The failure this exists to catch is the one a compiler cannot: an envoy that
registers, spawns, walks around and is a purple-and-black cube, or one whose
name renders as a raw translation key. Both are data problems in a mod whose
data is generated, and generated data goes wrong when somebody adds a row to one
table and not the other.
"""
import json
import os
import re
import sys

WORK = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(WORK, "src/main/resources/assets/elysiumnpcs")
JAVA = os.path.join(WORK, "src/main/java/com/elysium/npcs")

problems = []


def fail(msg):
    problems.append(msg)


def exists(rel):
    return os.path.exists(os.path.join(RES, rel))


# ---------------------------------------------------------------------------
# The five, read out of the enum that declares them
# ---------------------------------------------------------------------------

kind_source = open(os.path.join(JAVA, "entity/EnvoyKind.java"), encoding="utf-8").read()
kinds = re.findall(r'^\s+[A-Z_]+\("(\w+)",\s*Meter\.(\w+),\s*([\w.]+),\s*(\d+)',
                   kind_source, re.M)
if not kinds:
    print("read no envoys out of EnvoyKind.java - the declaration shape has changed")
    sys.exit(1)

names = [k[0] for k in kinds]
if len(set(names)) != len(names):
    fail(f"envoy id declared twice: {sorted({n for n in names if names.count(n) > 1})}")

lang_path = os.path.join(RES, "lang/en_us.json")
lang = json.load(open(lang_path, encoding="utf-8")) if os.path.exists(lang_path) else {}
if not lang:
    fail("no lang file - every name in the game would render as a translation key")

for envoy, meter, band, tier in kinds:
    if not exists(f"textures/entity/envoy/{envoy}.png"):
        fail(f"{envoy} has no skin at textures/entity/envoy/{envoy}.png - it renders "
             f"as a purple-and-black person")

    # The writ that summons them, and everything it needs to not be a cube.
    if not exists(f"models/item/{envoy}_summons.json"):
        fail(f"{envoy}'s writ has no item model")
    elif not exists(f"textures/item/{envoy}_summons.png"):
        fail(f"{envoy}'s writ has a model but no texture")

    for key in (f"entity.elysiumnpcs.envoy.{envoy}",
                f"elysiumnpcs.refusal.{envoy}",
                f"item.elysiumnpcs.{envoy}_summons"):
        if key not in lang:
            fail(f"{envoy}: no lang entry for {key} - the raw key shows in game")

print(f"envoys declared    : {len(kinds)} ({', '.join(names)})")

# ---------------------------------------------------------------------------
# Both halves of the court are reachable
# ---------------------------------------------------------------------------
#
# The scheduler picks the meter a player is further up and then only considers
# envoys who read that meter. A meter with nobody on it is a whole standing
# track that never produces a visitor - which looks exactly like the scheduler
# being broken, and would not be caught by anything else here.

for meter in ("FAVOR", "SUSPICION"):
    on_meter = [k for k in kinds if k[1] == meter]
    if not on_meter:
        fail(f"no envoy reads {meter}, so a player climbing that meter is never "
             f"visited by anybody")

print(f"standing coverage  : both meters have envoys")

# ---------------------------------------------------------------------------
# The model draws every accessory the enum can ask for
# ---------------------------------------------------------------------------
#
# EnvoyKind.Regalia is what a kind may wear; EnvoyModel is what can actually be
# drawn. A value in the first with no part in the second is regalia that
# silently does not appear - the kind is configured for it, nothing renders, and
# there is no error anywhere.

regalia = set(re.findall(r'public enum Regalia \{([^}]*)\}', kind_source)[0].replace(",", " ").split())
model_path = os.path.join(JAVA, "client/model/EnvoyModel.java")
if not os.path.exists(model_path):
    fail("EnvoyModel.java is missing - run tools/gen_npcs.py")
else:
    model = open(model_path, encoding="utf-8").read()
    for part in regalia:
        if f"Regalia.{part}" not in model:
            fail(f"EnvoyKind.Regalia.{part} exists but EnvoyModel never shows or hides "
                 f"a part for it, so a kind that wears it renders without it")
    print(f"regalia            : {len(regalia)} piece(s), all wired to a model part")

# ---------------------------------------------------------------------------
# No hard dependency on elysium-core
# ---------------------------------------------------------------------------
#
# The court pays out of ElysiumRewards precisely so that it works with whatever
# is installed. An import of elysium-core would turn its optional dependency
# into a required one, and the mods.toml would then be lying.

for dirpath, _, filenames in os.walk(JAVA):
    for filename in filenames:
        if not filename.endswith(".java"):
            continue
        text = open(os.path.join(dirpath, filename), encoding="utf-8").read()
        if "com.elysium.core" in text:
            fail(f"{filename} imports elysium-core. The court is supposed to work with "
                 f"whatever rewards are registered, and mods.toml declares core optional")

print(f"dependencies       : library only, core stays optional")

# ---------------------------------------------------------------------------
# All JSON parses
# ---------------------------------------------------------------------------

count = 0
for dirpath, _, filenames in os.walk(os.path.join(WORK, "src/main/resources")):
    for filename in filenames:
        if filename.endswith(".json"):
            count += 1
            try:
                json.load(open(os.path.join(dirpath, filename), encoding="utf-8"))
            except Exception as exc:
                fail(f"invalid JSON {filename}: {exc}")

print(f"json files parsed  : {count}")
print()

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  -", problem)
    sys.exit(1)

print("all court checks passed")
