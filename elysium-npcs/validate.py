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
# The ledger each office brings to its trade screen
# ---------------------------------------------------------------------------
#
# Since the envoys grew a merchant screen, EnvoyKind decides not just WHICH
# shelf an office deals off but the whole spread of its offer list. That
# arithmetic is easy to get subtly wrong in a way nothing else would catch: a
# lowest tier above the reward tier produces an empty or inverted list, and a
# screen whose rows are not in price order is one a player cannot read.
#
# So the shape is re-derived here from the same numbers the enum declares, and
# tierForOffer's arithmetic is reimplemented rather than trusted - which is the
# point of a validator that does not share code with the thing it validates.

no_comments = re.sub(r"/\*.*?\*/", " ", kind_source, flags=re.S)
shapes = re.findall(
    r'^\s+[A-Z_]+\("(\w+)",\s*Meter\.\w+,\s*[\w.]+,\s*(\d+),\s*(\d+),\s*(\d+)',
    no_comments, re.M)

if len(shapes) != len(kinds):
    fail(f"read a trade shape for {len(shapes)} of {len(kinds)} envoys - the "
         f"EnvoyKind declaration shape has changed and the ledger checks below "
         f"are no longer checking anything")

MAX_TIER = 3
deals_at_bottom = False
total_offers = 0

for envoy, reward_tier, lowest_tier, offer_count in shapes:
    reward_tier, lowest_tier, offer_count = int(reward_tier), int(lowest_tier), int(offer_count)
    total_offers += offer_count

    if not 0 <= lowest_tier <= MAX_TIER or not 0 <= reward_tier <= MAX_TIER:
        fail(f"{envoy} deals at tiers {lowest_tier}..{reward_tier}, outside 0..{MAX_TIER}")
    if lowest_tier > reward_tier:
        fail(f"{envoy}'s lowest tier ({lowest_tier}) is above its reward tier "
             f"({reward_tier}) - its offer list would be inverted")
    if offer_count < 1:
        fail(f"{envoy} brings {offer_count} offers, so its screen is empty and "
             f"opening it is indistinguishable from a broken entity")
    if offer_count > 12:
        fail(f"{envoy} brings {offer_count} offers - past what the vanilla trade "
             f"screen shows without scrolling, so the tail is invisible")
    if lowest_tier == 0:
        deals_at_bottom = True

    # tierForOffer, reimplemented. Must stay inside the office's own range and
    # must never step backwards, or the list is not in price order.
    previous = -1
    for i in range(offer_count):
        if offer_count <= 1 or reward_tier <= lowest_tier:
            tier = reward_tier
        else:
            tier = lowest_tier + (i * (reward_tier - lowest_tier)) // (offer_count - 1)
        if not lowest_tier <= tier <= reward_tier:
            fail(f"{envoy} offer {i} lands at tier {tier}, outside its own "
                 f"{lowest_tier}..{reward_tier} range")
        if tier < previous:
            fail(f"{envoy} offer {i} is cheaper than the one before it - the "
                 f"screen is not in price order")
        previous = tier

if not deals_at_bottom:
    fail("no envoy deals at tier 0, so a player the Code has not noticed has "
         "nobody to trade with at all")

print(f"trade ledgers      : {total_offers} offers across {len(shapes)} envoys, "
      f"all in range and in price order")

# ---------------------------------------------------------------------------
# The library still ships no items
# ---------------------------------------------------------------------------
#
# Prices are a provider registry for the same reason rewards are. If the
# library ever names a modded item the whole arrangement collapses into the
# thing it exists to avoid - so the one place that is allowed to name an item
# (the vanilla emerald fallback) is checked to still be the only one.

prices_path = os.path.join(WORK, "..", "elysium-lib", "src", "main", "java",
                           "com", "elysium", "lib", "standing", "ElysiumPrices.java")
if not os.path.exists(prices_path):
    fail("elysium-lib has no ElysiumPrices - the trade screen has no way to "
         "name a price and every offer would be free")
else:
    prices_src = open(prices_path, encoding="utf-8").read()
    for forbidden in ("com.elysium.core", "com.elysium.mobs", "com.elysium.dungeons",
                      "com.elysium.trinkets", "com.elysium.npcs"):
        if forbidden in prices_src:
            fail(f"ElysiumPrices references {forbidden} - the library must not "
                 f"know what is built on top of it")
    print("price registry     : present, and names no mod's items")

# ---------------------------------------------------------------------------
# Court commissions (Fourth Edition 14.5 / 19.2)
# ---------------------------------------------------------------------------
#
# A commission is the "high-standing endgame sink" 19.2 names. Two things make
# it that rather than another errand: only top-shelf offices issue one, and
# there has to be at least one office that does. Both are easy to break by
# retuning a tier, and neither breaks loudly - the feature would simply become
# unreachable, or become available from the first envoy a new player meets.

issuers = [envoy for envoy, reward_tier, _lowest, _count in shapes if int(reward_tier) >= 3]
if not issuers:
    fail("no office issues court commissions, so 14.5's contracts can never be "
         "taken by anybody")
for envoy, reward_tier, _lowest, _count in shapes:
    if int(reward_tier) < 3 and envoy in issuers:
        fail(f"{envoy} issues commissions without dealing at the top tier")

commission_path = os.path.join(WORK, "..", "elysium-lib", "src", "main", "java",
                               "com", "elysium", "lib", "standing",
                               "ElysiumCommission.java")
if not os.path.exists(commission_path):
    fail("elysium-lib has no ElysiumCommission - the court can issue contracts "
         "that nothing persists")
else:
    commission_src = open(commission_path, encoding="utf-8").read()
    for forbidden in ("com.elysium.core", "com.elysium.mobs", "com.elysium.dungeons",
                      "com.elysium.trinkets", "com.elysium.npcs"):
        if forbidden in commission_src:
            fail(f"ElysiumCommission references {forbidden} - the library must "
                 f"not know what is built on top of it")
    # A commission that copied on death would make the deadline unfalsifiable:
    # the one failure state a player can actually hit is running out of time,
    # and dying is the ordinary way that happens.
    #
    # Checked against the CODE, not the file. The first version of this grepped
    # the whole source and fired on the Javadoc paragraph that explains why the
    # attachment deliberately does not copy on death - a validator that cannot
    # tell a call from a comment about the call will eventually be silenced by
    # somebody rather than fixed, and then it checks nothing.
    commission_code = re.sub(r"/\*.*?\*/", " ", commission_src, flags=re.S)
    commission_code = re.sub(r"//[^\n]*", " ", commission_code)
    if "copyOnDeath" in commission_code:
        fail("the commission attachment copies on death - see the note in "
             "ElysiumCommission about why it deliberately does not")

# Every commission line the court can say must exist in the lang file.
commission_keys = set(re.findall(r'"(elysiumnpcs\.commission\.[a-z_]+)"',
                                 open(os.path.join(JAVA, "entity/ImperialEnvoy.java"),
                                      encoding="utf-8").read()))
missing = sorted(k for k in commission_keys if k not in lang)
if missing:
    fail(f"commission lines with no lang entry: {missing} - they render as raw keys")

print(f"court commissions  : issued by {len(issuers)} top-shelf office(s), "
      f"{len(commission_keys)} lines all translated")

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
