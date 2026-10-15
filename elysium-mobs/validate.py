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
import pathlib
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

# Eight families, four a side, five variants each.
#
# The counts are asserted rather than derived because they are a promise the
# mod makes about itself: an even split between the factions, and every family
# offering the same number of choices. A seventh family on one side would be a
# balance change disguised as a content addition.
if len(family_names) != 8:
    fail(f"expected 8 families, read {len(family_names)}: {family_names}")
if len(variants) != 40:
    fail(f"expected 40 sub-variants, read {len(variants)} - the mod promises "
         f"2 factions x 4 families x 5 variants")

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
if len(types) != 10:
    fail(f"expected 10 entity types (8 families + 2 bosses), read {len(types)}: {types}")

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

# ---------------------------------------------------------------------------
# The model and its texture agree about the sheet size
# ---------------------------------------------------------------------------
#
# A sheet size appears twice: in the model's LayerDefinition and in the PNG. The
# generator measures it per family with smallest_sheet, so the two come from one
# number — but they are written by two different calls, and the first version of
# that change passed the size to the model and let the texture default to 64.
#
# The symptom is not a crash. It is every box on that family sampling the wrong
# part of the sheet: a mob wearing its own pauldron as a face. Nothing reports
# it and it is invisible until somebody looks at one.
try:
    from PIL import Image
except ImportError:
    Image = None

if Image is None:
    # A failure, not a status line: until 15 October this printed "unchecked"
    # and the module passed with not one sheet measured.
    fail(f"Pillow does not import in {sys.executable}, so not one texture's size "
         f"was read against its model's sheet (python3 -m pip install Pillow)")
else:
    checked = 0
    for type_name in types:
        cls = camel(type_name)
        model_path = os.path.join(SRC, f"client/model/{cls}Model.java")
        if not os.path.exists(model_path):
            continue
        declared = re.search(r"LayerDefinition\.create\(mesh, (\d+), (\d+)\)",
                             open(model_path, encoding="utf-8").read())
        if not declared:
            fail(f"{type_name}: no LayerDefinition.create(mesh, w, h) in its model")
            continue
        w, h = int(declared.group(1)), int(declared.group(2))
        if w != h:
            fail(f"{type_name}: model sheet is {w}x{h}, not square")
        folder = os.path.join(RES, "textures/entity", type_name)
        if not os.path.isdir(folder):
            continue
        for filename in sorted(os.listdir(folder)):
            if not filename.endswith(".png"):
                continue
            size = Image.open(os.path.join(folder, filename)).size
            checked += 1
            if size != (w, h):
                fail(f"{type_name}/{filename} is {size[0]}x{size[1]} but its "
                     f"model declares {w}x{h} — every box on this family would "
                     f"sample the wrong part of the sheet, with no error")
    print(f"model sheets        : {checked} textures, all matching their model")

print(f"entity types        : {len(types)}, all with model, renderer, attributes and placement")

# ---------------------------------------------------------------------------
# 5. Both factions are represented, and both have a boss
# ---------------------------------------------------------------------------
factions = set(re.findall(r"return ElysiumFaction\.(\w+);", families_src))
for side in ("UNSWORN", "EMPIRE"):
    if side not in factions:
        fail(f"no family is on the {side} side")

# And evenly. Counting the returns rather than the classes, because that is
# what actually decides which side a family fights for.
per_side = {side: len(re.findall(rf"return ElysiumFaction\.{side};", families_src))
            for side in ("UNSWORN", "EMPIRE")}
if per_side["UNSWORN"] != per_side["EMPIRE"]:
    fail(f"the factions are uneven: {per_side}. Both meters drive spawning, so "
         f"one side having more families means one loop quietly produces more "
         f"opposition than the other")

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

# ---------------------------------------------------------------------------
# Roaming bands do not break the two systems they touch
# ---------------------------------------------------------------------------
#
# The library sends small squads of whatever is registered in the bestiary, on
# its own clock, forever. That is fine on its own and breaks two things here,
# both silently, and both in a way that looks like an unrelated bug:
#
#   1. ElysiumMob refuses to despawn. Correct for a dungeon boss, catastrophic
#      for a mob that arrives every two minutes for the length of a session.
#   2. RegisterScheduler decides a wave is cleared by counting nearby Empire
#      mobs. An ambient Empire band standing in the field holds every later
#      wave forever — the player clears the wave, nothing comes, and the action
#      eventually times out with no explanation.
#
# Both are fixed by the same scoreboard tag, and both fixes are one expression
# that a later edit could quietly revert. So both are checked.
SQUAD_COMMENT = re.compile(r"//[^\n]*|/\*.*?\*/", re.S)

# The despawn rule, and the placements that opt out of it.
#
# Elysium mobs consent to the ordinary vanilla despawn rules, because they are
# in the overworld spawn tables: an immortal naturally-spawned mob means a
# session leaves a permanent trail of them across the map. Persistence is
# claimed by whatever placed the mob deliberately, and vanilla checks that
# BEFORE removeWhenFarAway is consulted.
#
# The two halves fail in opposite directions, which is why both are checked:
# lose the rule and the world fills up, lose a persistence call and a boss room
# is empty when the player walks in.
mob_code = SQUAD_COMMENT.sub(" ", read("entity/ElysiumMob.java"))
far_away = re.search(r"removeWhenFarAway\([^)]*\)\s*\{(.*?)\}", mob_code, re.S)
if not far_away:
    fail("ElysiumMob.removeWhenFarAway is missing")
elif "return true" not in far_away.group(1):
    fail("ElysiumMob.removeWhenFarAway no longer returns true. These families "
         "are in the overworld spawn tables, so refusing to despawn leaves a "
         "permanent trail of them everywhere a player has been")

# Every deliberate placement in the repository marks itself persistent.
DELIBERATE = {
    "elysium-mobs/src/main/java/com/elysium/mobs/register/RegisterScheduler.java":
        "register action waves",
    "elysium-mobs/src/main/java/com/elysium/mobs/boss/ElysiumBosses.java":
        "boss summons",
    "elysium-dungeons/src/main/java/com/elysium/dungeons/room/DungeonRooms.java":
        "dungeon room occupants",
    "elysium-lib/src/main/java/com/elysium/lib/event/ElysiumStandingHandler.java":
        "standing dispatches",
}
# ElysiumSquads is the one that deliberately does NOT: a roaming band is the
# thing that is allowed to be forgotten.
repo = os.path.dirname(WORK)
for rel, what in DELIBERATE.items():
    path = os.path.join(repo, rel)
    if not os.path.exists(path):
        continue
    body = SQUAD_COMMENT.sub(" ", open(path, encoding="utf-8").read())
    if "addFreshEntity" in body and "setPersistenceRequired" not in body:
        fail(f"{rel} places {what} without setPersistenceRequired — they now "
             f"despawn like natural spawns, so the player arrives to an empty "
             f"room")

squads_path = os.path.join(
    repo, "elysium-lib/src/main/java/com/elysium/lib/entity/ElysiumSquads.java")
if os.path.exists(squads_path):
    squads_body = SQUAD_COMMENT.sub(" ", open(squads_path, encoding="utf-8").read())
    if "setPersistenceRequired" in squads_body:
        fail("ElysiumSquads makes roaming bands persistent — a band arrives "
             "every couple of minutes forever and a permanent one is a leak "
             "with a clock on it")

sched_code = SQUAD_COMMENT.sub(" ", read("register/RegisterScheduler.java"))
survivors = re.search(r"private static int survivors\([^)]*\)\s*\{(.*?)\n    \}",
                      sched_code, re.S)
if not survivors:
    fail("RegisterScheduler.survivors is missing")
elif "isAmbient" not in survivors.group(1):
    fail("RegisterScheduler.survivors counts roaming bands as wave survivors — "
         "an unrelated band parks a register action forever")

# ---------------------------------------------------------------------------
# 14.3 is "Register Actions and base sieges" — the second half is the claim
# ---------------------------------------------------------------------------
#
# Waves used to spawn around the player wherever they stood, which made the
# ten-minute warning meaningless: walking into the wilderness took the siege
# with you and the base you were told to prepare was never involved. A siege has
# to have somewhere to be.
#
# Each of these is a way for that to quietly revert to following the player,
# with nothing failing and nothing logged.
if "claimOf" not in sched_code or "ElysiumClaims" not in sched_code:
    fail("RegisterScheduler no longer looks up the player's claim, so a "
         "register action targets nobody's base — §14.3 is titled 'Register "
         "Actions and base sieges' and the warning has nowhere to point")

for helper, why in (
    ("centreOf", "the centre a wave is placed around"),
    ("placement(level, centre", "placement working from a centre rather than "
                               "from the player"),
    ("survivors(level, centre", "wave clearance counted at the siege, not "
                                "around the player"),
):
    if helper not in sched_code:
        fail(f"RegisterScheduler lost {why} — waves would follow the player "
             f"again and the claim would stop mattering")

# The Noted patrol is the deliberate exception, and it is easy to "fix" by
# making it consistent with the sieges. §14.3 calls a patrol an "interruption",
# and one that arrived at a base the player is nowhere near interrupts nothing.
if "player.blockPosition(), band, 2" not in sched_code:
    fail("the Noted patrol no longer spawns around the player. §14.3 calls it "
         "an interruption; sieges go to the base, patrols find you")

# The failure row, for an action already running.
if "register.failed" not in sched_code:
    fail("nothing ends a running action whose claim has been struck — §14.3's "
         "failure row would leave waves arriving at a beacon that is gone")
if "isIn(level)" not in sched_code:
    fail("RegisterScheduler checks a claim without checking it is in this "
         "dimension. ElysiumClaims is per dimension, so an Overworld claim "
         "reads as destroyed from the Nether and stepping through a portal "
         "would end the siege as a failure")

if "addSuppressor" not in sched_code:
    fail("RegisterScheduler no longer suppresses roaming bands during an "
         "action; the ten-minute warning §14.3 asks for is unusable if bands "
         "keep arriving through it")

mods_code = SQUAD_COMMENT.sub(" ", read("ElysiumMobs.java"))
if "suppressWhileActive()" not in mods_code:
    fail("ElysiumMobs never calls RegisterScheduler.suppressWhileActive, so the "
         "suppressor is written and never registered")

print("roaming bands       : despawn consent tagged, wave clearance excludes "
      "them, suppressed mid-action")
print("base sieges         : actions target the largest claim, fail when its "
      "beacon is struck")
print()

# ---------------------------------------------------------------------------
# Register actions (Fourth Edition 14.3)
# ---------------------------------------------------------------------------
#
# 14.3 is a table, and a table is the easiest kind of specification to drift
# from: every number in it is a plain integer in a switch that compiles fine
# whatever it says. These checks re-read the numbers out of the source and
# compare them against the specification, so a retune has to be deliberate.

action_src = open(os.path.join(SRC, "register/RegisterAction.java"),
                  encoding="utf-8").read()
sched_src = open(os.path.join(SRC, "register/RegisterScheduler.java"),
                 encoding="utf-8").read()

# 14.3: Marked sends 3 waves, Hunted sends 5, and nothing below sends an action.
waves = dict(re.findall(r"case (\d+) -> (\d+);", action_src.split("wavesFor")[1]
                        .split("}")[0] + "}"))
for band, expected in (("3", "5"), ("2", "3")):
    if waves.get(band) != expected:
        fail(f"14.3 says band {band} sends {expected} waves; wavesFor says "
             f"{waves.get(band, 'nothing')}")

# 14.3: "announced ~10 minutes early".
warning = re.search(r"WARNING_TICKS = 20 \* 60 \* (\d+)", action_src)
if not warning:
    fail("WARNING_TICKS is no longer written as minutes, so the 14.3 "
         "announcement window cannot be checked")
elif warning.group(1) != "10":
    fail(f"14.3 announces an action ~10 minutes early; WARNING_TICKS is "
         f"{warning.group(1)} minutes")

# 14.3: clearing returns Suspicion "toward structural floor". "Structural" is
# 14.2's claim floor - what you have built - and NOT the bottom of the band that
# sent the action, which is what this used to check.
#
# The old check enforced a real bug, which is the argument for checking the
# property rather than the constant. Flooring a cleared Hunted action at 75
# leaves the player Hunted, so the next check schedules another five-wave
# action: the biggest fight in the mod paid its reward and immediately bought a
# rematch. So what is checked now is the property that made it wrong - clearing
# an action must be able to lower the band.
if "floorFor(int band)" in action_src:
    fail("RegisterAction still floors a cleared action by band. 14.3's "
         "'structural floor' is 14.2's claim floor; a band floor leaves a "
         "cleared Hunted action still Hunted and schedules the next one "
         "immediately")
elif "floorAfter" not in action_src:
    fail("RegisterAction has no floorAfter - nothing implements 14.3's "
         "'Suspicion returns toward structural floor'")
else:
    body = action_src.split("floorAfter")[1].split("}")[0]
    if "ElysiumStanding.floorFor" not in body:
        fail("floorAfter does not read the claim floor, so 14.2's permanent "
             "floor has no effect on clearing an action")
    if "NOTICE" not in body:
        fail("floorAfter has no lower bound - a player with no claim who "
             "clears a siege would be filed at zero, which switches off both "
             "standing loops")

# The promise made in RegisterScheduler.isEmpire's own comment: every Empire
# family is listed there. A family added later and forgotten would simply never
# count toward clearing a wave, and the action would stall with the player
# standing in a crowd of enemies.
families_src = open(os.path.join(SRC, "entity/ElysiumFamilies.java"),
                    encoding="utf-8").read()
empire_families = []
for chunk in families_src.split("public static class ")[1:]:
    name = re.match(r"(\w+)", chunk).group(1)
    body = chunk.split("public static class ")[0]
    if "ElysiumFaction.EMPIRE" in body:
        empire_families.append(name)
if not empire_families:
    fail("no Empire family found in ElysiumFamilies - the register action has "
         "nothing to send")
for name in empire_families:
    if f"ElysiumMobs.{name.upper()}.get()" not in sched_src:
        fail(f"{name} is an Empire family but is not counted by "
             f"RegisterScheduler.isEmpire - a wave containing one could never "
             f"be cleared")

register_keys = set(re.findall(r'"(elysiummobs\.register\.[a-z_]+)"', sched_src))
missing_register = sorted(k for k in register_keys if k not in lang)
if missing_register:
    fail(f"register action lines with no lang entry: {missing_register}")

print(f"register actions   : 14.3 table matches, {len(empire_families)} Empire "
      f"families all counted, {len(register_keys)} lines translated")

# ---------------------------------------------------------------------------
# The report, and it is the LAST thing in this file on purpose
# ---------------------------------------------------------------------------
#
# It used to sit in the middle. Every section appended after it — the whole of
# the register-action block — recorded failures into a list that had already
# been printed, and the file ended with an unconditional "all mob checks
# passed". Those checks could not fail. They ran, they found a real problem (a
# new Empire family missing from isEmpire), and the script exited 0 announcing
# success.
#
# That is the worst failure mode a validator has: not a missing check, but a
# check that looks present, prints a reassuring line, and is structurally
# incapable of reporting anything. The root validator now refuses any module
# whose file calls fail() after this block.
# ---------------------------------------------------------------------------
# Value structure: a mob has to be visible from across a room
# ---------------------------------------------------------------------------
#
# The bestiary shipped twice as a set of silhouettes, and neither time was it
# a bug anybody could see in a diff. The palette was three to four times darker
# than vanilla's entity textures the first time; the second time the mean was
# right and the *range* was a fifth of a cow's, which is the same thing — one
# flat value over a hundred parts is a shape with an outline and nothing
# inside it.
#
# Both are measurements, so both are checkable. The numbers are vanilla's own,
# taken from cow.png and iron_golem.png through this same arithmetic:
#
#     cow     mean  64.9   p5  20.6   p95 172.0   std 48.6
#     golem   mean 145.4   p5  88.2   p95 180.9   std 32.0
#
# The bands below are wide enough to hold both and to leave a family its own
# character; what they do not hold is a texture with no light in it, or one
# with no dark.

try:
    import numpy as _np
    from PIL import Image as _Image
except ImportError as _missing:
    # A failure, not a status line: until 15 October this printed "not
    # measured" and the module passed with not one texture's value read.
    fail(f"the mob value range cannot be measured: {_missing} in {sys.executable} "
         f"(it needs Pillow and numpy: python3 -m pip install Pillow numpy)")
else:
    MEAN_FLOOR, MEAN_CEILING = 55.0, 165.0
    SPREAD_FLOOR = 24.0
    HIGHLIGHT_FLOOR = 130.0

    _measured = []
    _flattest = None
    _darkest = None
    for _texture in sorted((pathlib.Path(RES) / "textures/entity").rglob("*.png")):
        _pixels = _np.array(_Image.open(_texture).convert("RGBA"), dtype=float)
        _opaque = _pixels[_pixels[..., 3] > 0][:, :3]
        if _opaque.size == 0:
            fail(f"{_texture.name} is entirely transparent")
            continue
        _lum = (0.2126 * _opaque[:, 0] + 0.7152 * _opaque[:, 1]
                + 0.0722 * _opaque[:, 2])
        _mean, _std = float(_lum.mean()), float(_lum.std())
        _p95 = float(_np.percentile(_lum, 95))
        _measured.append((_texture.name, _mean, _std, _p95))

        _where = f"{_texture.parent.name}/{_texture.name}"
        if _mean < MEAN_FLOOR:
            fail(f"{_where} averages {_mean:.1f} luminance against vanilla's "
                 f"65-145. It renders as a silhouette with a coloured dot on it")
        if _mean > MEAN_CEILING:
            fail(f"{_where} averages {_mean:.1f} luminance, brighter than any "
                 f"vanilla entity — it will glow against every world it is in")
        if _std < SPREAD_FLOOR:
            fail(f"{_where} strays only {_std:.1f} from its own mean against a "
                 f"cow's 48.6. One flat value over every part is a shape with "
                 f"an outline and nothing inside it, however bright the value")
        if _p95 < HIGHLIGHT_FLOOR:
            fail(f"{_where} has no highlight: its brightest twentieth reaches "
                 f"{_p95:.1f}. A texture whose lightest pixel is a midtone has "
                 f"no value structure and reads as a silhouette")

        if _flattest is None or _std < _flattest[1]:
            _flattest = (_where, _std)
        if _darkest is None or _mean < _darkest[1]:
            _darkest = (_where, _mean)

    if _measured and _flattest and _darkest:
        print(f"mob value range     : {len(_measured)} textures, flattest "
              f"{_flattest[0]} at std {_flattest[1]:.1f} (floor {SPREAD_FLOOR}), "
              f"darkest {_darkest[0]} at mean {_darkest[1]:.1f} "
              f"(floor {MEAN_FLOOR})")

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all mob checks passed")
