#!/usr/bin/env python3
"""
Cross-check Elysium Mobs against itself.

Run from the repo root:   python3 validate.py

Two kinds of check. The ordinary resource audit — every variant has a texture,
every family has a model, renderer, attributes entry and lang key — and one
balance invariant that matters more than all of it:

    every sub-variant's three multipliers add up to the same total.

That is what keeps five sub-variants a choice of how a fight goes rather than a
ranking of which one is dangerous, and it is exactly the kind of promise that
decays silently as variants are tuned one at a time. It is checked here because
nothing else can check it.
"""
import json
import os
import re
import sys

WORK = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(WORK, "src/main/java/com/elysium/mobs")
RES = os.path.join(WORK, "src/main/resources/assets/elysiummobs")

problems = []


def fail(msg):
    problems.append(msg)


def read(rel):
    return open(os.path.join(SRC, rel), encoding="utf-8").read()


def exists(rel):
    return os.path.exists(os.path.join(RES, rel))


if not os.path.isdir(SRC):
    print("This is not the elysium-mobs tree: src/main/java/com/elysium/mobs is missing.")
    sys.exit(1)

# ---------------------------------------------------------------------------
# 1. Families and variants, read out of the Java
# ---------------------------------------------------------------------------
families_src = read("entity/ElysiumFamilies.java")
FAMILIES = re.findall(r'ResourceLocation (\w+)_ID = id\("(\w+)"\)', families_src)
family_names = [name for _const, name in FAMILIES]

variants_src = read("variant/ElysiumVariants.java")
variants = re.findall(
    r'variant\("(\w+)",\s*ElysiumFamilies\.(\w+)_ID,\s*'
    r'([\d.]+)F,\s*([\d.]+)F,\s*([\d.]+)F', variants_src)

CONST_TO_FAMILY = {const: name for const, name in FAMILIES}

if len(family_names) != 6:
    fail(f"expected 6 families, read {len(family_names)}: {family_names}")
if len(variants) != 30:
    fail(f"expected 30 sub-variants, read {len(variants)} - the mod promises "
         f"2 factions x 3 families x 5 variants")

# Five per family, no more and no fewer.
per_family = {}
for name, const, _h, _d, _s in variants:
    per_family.setdefault(CONST_TO_FAMILY.get(const, const), []).append(name)
for family in family_names:
    count = len(per_family.get(family, []))
    if count != 5:
        fail(f"family {family} has {count} sub-variants, not 5")

names = [v[0] for v in variants]
duplicates = sorted({n for n in names if names.count(n) > 1})
if duplicates:
    fail(f"duplicate variant ids: {duplicates} - the second registration would throw")

print(f"families            : {len(family_names)}")
print(f"sub-variants        : {len(variants)}")

# ---------------------------------------------------------------------------
# 2. The balance budget
# ---------------------------------------------------------------------------
BUDGET = 3.00
TOLERANCE = 0.001

over = []
for name, _const, health, damage, speed in variants:
    total = float(health) + float(damage) + float(speed)
    if abs(total - BUDGET) > TOLERANCE:
        over.append((name, round(total, 3)))
if over:
    fail(f"these sub-variants do not spend the same budget (should be {BUDGET:.2f}): "
         f"{over} - one variant being strictly better is how five choices become one")
else:
    print(f"balance budget      : all {len(variants)} spend {BUDGET:.2f}")

# Exactly one baseline per family, so there is always a plain option.
baselines = {}
for name, const, health, damage, speed in variants:
    if (float(health), float(damage), float(speed)) == (1.0, 1.0, 1.0):
        baselines.setdefault(CONST_TO_FAMILY.get(const, const), []).append(name)
for family in family_names:
    found = baselines.get(family, [])
    if len(found) != 1:
        fail(f"family {family} has {len(found)} baseline (1/1/1) variants, not 1: {found}")

# ---------------------------------------------------------------------------
# 3. Every variant has a texture; every texture belongs to a variant
# ---------------------------------------------------------------------------
for name, const, _h, _d, _s in variants:
    family = CONST_TO_FAMILY.get(const, const)
    rel = f"textures/entity/{family}/{name}.png"
    if not exists(rel):
        fail(f"missing texture: {rel} - run tools/gen_mobs.py")

texture_root = os.path.join(RES, "textures/entity")
orphans = []
known = {(CONST_TO_FAMILY.get(c, c), n) for n, c, _h, _d, _s in variants}
BOSS_TEXTURES = {("choir", "choir"), ("praetor", "praetor")}
if os.path.isdir(texture_root):
    for family in os.listdir(texture_root):
        for filename in os.listdir(os.path.join(texture_root, family)):
            pair = (family, filename[:-4])
            if pair not in known and pair not in BOSS_TEXTURES:
                orphans.append("%s/%s" % pair)
if orphans:
    fail(f"textures with no variant: {sorted(orphans)} - a leftover from a renamed variant")

# ---------------------------------------------------------------------------
# 4. Every registered entity type has a model, a renderer and attributes
# ---------------------------------------------------------------------------
main_src = read("ElysiumMobs.java")
types = re.findall(r'family\("(\w+)",', main_src)
if len(types) != 8:
    fail(f"expected 8 entity types (6 families + 2 bosses), read {len(types)}: {types}")

events_src = read("ElysiumMobEvents.java")
attribute_puts = len(re.findall(r"event\.put\(", events_src))
if attribute_puts != len(types):
    fail(f"{len(types)} entity types but {attribute_puts} attribute registrations - "
         f"a type with no attributes crashes the moment it is created, and only "
         f"when that family happens to spawn")

placements = len(re.findall(r"placement\(event,", events_src))
if placements != len(types):
    fail(f"{len(types)} entity types but {placements} spawn placements registered")

def camel(name):
    return "".join(part.capitalize() for part in name.split("_"))

for type_name in types:
    cls = camel(type_name)
    for kind, path in (("model", f"client/model/{cls}Model.java"),
                       ("renderer", f"client/{cls}Renderer.java")):
        if not os.path.exists(os.path.join(SRC, path)):
            fail(f"{type_name} has no {kind}: {path} - run tools/gen_mobs.py")

client_src = read("client/ElysiumMobsClient.java")
layers = len(re.findall(r"registerLayerDefinition\(", client_src))
renderers = len(re.findall(r"registerEntityRenderer\(", client_src))
if layers != len(types) or renderers != len(types):
    fail(f"{len(types)} entity types but {layers} layer definitions and {renderers} "
         f"renderers registered - a missing one is an invisible mob with no error")

print(f"entity types        : {len(types)}, all with model, renderer, attributes and placement")

# ---------------------------------------------------------------------------
# 5. Both factions are represented, and both have a boss
# ---------------------------------------------------------------------------
factions = set(re.findall(r"return ElysiumFaction\.(\w+);", families_src))
for side in ("UNSWORN", "EMPIRE"):
    if side not in factions:
        fail(f"no family is on the {side} side")

boss_src = read("boss/ElysiumBosses.java")
boss_factions = set(re.findall(r"return ElysiumFaction\.(\w+);", boss_src))
for side in ("UNSWORN", "EMPIRE"):
    if side not in boss_factions:
        fail(f"{side} has no boss - the mod promises one for each")

# Only the calls, not the method that defines them.
bestiary_bosses = len(re.findall(r'bossEntry\("', main_src))
if bestiary_bosses != 2:
    fail(f"{bestiary_bosses} bosses registered in the bestiary, expected 2 - a boss "
         f"the bestiary does not know about can never appear in a dungeon")

grunts = len(re.findall(r"ElysiumBestiary\.Role\.GRUNT", main_src))
if grunts < 2:
    fail("fewer than two GRUNT entries in the bestiary - dungeon filler rooms would "
         "be nearly empty, and the Choir would have nothing to summon")

print("factions            : both sides have families and a boss")

# ---------------------------------------------------------------------------
# 6. This mod does not name any other content mod
# ---------------------------------------------------------------------------
#
# The whole integration story is that mobs and dungeons reach each other only
# through the library. An import of com.elysium.dungeons would compile here
# (dungeons is not on the classpath, so it would not) but the equivalent
# mistakes that do compile are worth checking.
COMMENT = re.compile(r"//[^\n]*|/\*.*?\*/", re.S)
for dirpath, _, filenames in os.walk(SRC):
    for filename in filenames:
        if not filename.endswith(".java"):
            continue
        path = os.path.join(dirpath, filename)
        text = COMMENT.sub(" ", open(path, encoding="utf-8").read())
        rel = os.path.relpath(path, SRC)
        for hit in re.findall(r"com\.elysium\.(?!mobs\b|lib\b)\w+", text):
            fail(f"{rel}: refers to {hit} - mobs reaches other Elysium mods only "
                 f"through the library's bestiary")
        for hit in re.findall(r'isLoaded\("(\w+)"\)', text):
            fail(f"{rel}: checks for mod \"{hit}\" - the bestiary makes that unnecessary")

# ---------------------------------------------------------------------------
# 7. Lang and JSON
# ---------------------------------------------------------------------------
lang = json.load(open(os.path.join(RES, "lang/en_us.json"), encoding="utf-8"))
for name, _const, _h, _d, _s in variants:
    key = f"elysiummobs.variant.{name}"
    if key not in lang:
        fail(f"missing lang key: {key}")
for type_name in types:
    if f"entity.elysiummobs.{type_name}" not in lang:
        fail(f"missing lang key: entity.elysiummobs.{type_name}")
    if f"item.elysiummobs.{type_name}_spawn_egg" not in lang:
        fail(f"missing lang key: item.elysiummobs.{type_name}_spawn_egg")
    if not exists(f"models/item/{type_name}_spawn_egg.json"):
        fail(f"missing spawn egg model: {type_name}_spawn_egg")

abilities_src = read("variant/MobAbilities.java")
for key in set(re.findall(r'return "elysiummobs\.ability\.(\w+)";', abilities_src)):
    if f"elysiummobs.ability.{key}" not in lang:
        fail(f"missing lang key: elysiummobs.ability.{key}")

count = 0
for dirpath, _, filenames in os.walk(RES):
    for filename in filenames:
        if filename.endswith(".json"):
            count += 1
            try:
                json.load(open(os.path.join(dirpath, filename), encoding="utf-8"))
            except Exception as exc:
                fail(f"invalid JSON {os.path.join(dirpath, filename)}: {exc}")

print(f"lang keys           : {len(lang)}")
print(f"json files parsed   : {count}")
print()

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all mob checks passed")
