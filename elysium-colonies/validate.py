#!/usr/bin/env python3
"""
Validate Elysium Colonies against its sources.

Run from anywhere:   python3 elysium-colonies/validate.py

This is the repository's only add-on: every other module is Elysium's own
replacement for something, and this one sits on top of a mod it does not own
and cannot change. That inverts the usual independence rule and makes two new
ones matter more than anything else.

  1. ONE SEAM. Exactly one file may know MineColonies exists. An add-on is a
     bet on somebody else's API; the way to lose that bet cheaply is to make it
     in one place, so that when a method is renamed one file fails to compile
     instead of four.
  2. ADDITIVE. Nothing here may change how a colony works, what it builds, who
     lives in it or how a raid is decided. Three readings of a colony, and
     nothing written back.
  3. PURITY. rules/ is the JDK alone and its harness is run, so the three
     readings are executed rather than described.
  4. THE THREE SENTENCES. A floor that never falls, a floor that cannot be
     decayed away, and a promotion that is capped — each wired to the library
     through the one seam the library offers for it.
"""
import json
import math
import os
import re
import subprocess
import sys
import tempfile

WORK = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(WORK, "src/main/resources")
SRC = os.path.join(WORK, "src/main/java/com/elysium/colonies")
PURE = os.path.join(SRC, "rules")
SEAM = os.path.join(SRC, "colony/Colonies.java")
HARNESS = os.path.join(WORK, "tools/ColonyHarness.java")
HARNESS_MAIN = "com.elysium.colonies.rules.ColonyHarness"
NS = "elysiumcolonies"
HOST = "com.minecolonies"

problems = []


def fail(msg):
    problems.append(msg)


def strip_comments(text, keep_strings=False):
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"//[^\n]*", " ", text)
    if keep_strings:
        return text
    return re.sub(r'"(?:\\.|[^"\\])*"', '""', text)


def read(*parts):
    path = os.path.join(*parts)
    return open(path, encoding="utf-8").read() if os.path.isfile(path) else ""


def harness_trouble(output):
    """The lines of a harness run that say what went wrong.

    Copied from the other modules' validators. This one referred to it for as
    long as the harness had never failed, and the day a rule finally broke the
    harness the validator crashed on the name instead of reporting.
    """
    lines = [line for line in output.splitlines()
             if line.strip().startswith("FAIL:") or "failure(s)" in line]
    return chr(10).join(lines or output.strip().splitlines()[-12:])[:2000]


def body_of(src, signature):
    start = src.find(signature)
    if start < 0:
        return ""
    open_at = src.find("{", start)
    if open_at < 0:
        return ""
    depth = 0
    for i in range(open_at, len(src)):
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
            if depth == 0:
                return src[open_at + 1:i]
    return src[open_at + 1:]


def java_files():
    for root, _, names in os.walk(SRC):
        for name in names:
            if name.endswith(".java"):
                yield os.path.join(root, name)


# ---------------------------------------------------------------------------
# 1. One seam
# ---------------------------------------------------------------------------

if not os.path.isfile(SEAM):
    fail("colony/Colonies.java is gone; there is no seam, so every handler has to "
         "know MineColonies itself")

knowing = []
for path in java_files():
    src = strip_comments(read(path))
    if HOST in src:
        knowing.append(os.path.relpath(path, WORK))
expected = [os.path.relpath(SEAM, WORK)]
if sorted(knowing) != sorted(expected):
    fail(f"{len(knowing)} file(s) name {HOST} and exactly one may: "
         f"{', '.join(sorted(knowing)) or 'none'}. An add-on is a bet on somebody "
         f"else's API and it is made in one place")

seam = strip_comments(read(SEAM))
if "record Settlement(" not in seam:
    fail("the seam does not hand out a value of its own, so whatever it returns leaks "
         "the type it was supposed to contain")
if "IColony " in seam and "public" in seam:
    for signature in re.findall(r"public static [^\n(]*\(", seam):
        if "IColony" in signature:
            fail("the seam has a public method that speaks in MineColonies' own types, "
                 "which puts the bet back outside this file")

# The rest of the module must speak only in Settlements.
for path in java_files():
    if os.path.samefile(path, SEAM) if os.path.isfile(SEAM) else False:
        continue
    src = strip_comments(read(path))
    for leaked in ("IColony", "ICitizenManager", "AbstractEntityMinecolonies",
                   "IColonyManager"):
        if leaked in src:
            fail(f"{os.path.relpath(path, WORK)} names {leaked}; the rest of the module "
                 f"speaks in Settlements")
print("one seam           : exactly one file knows MineColonies, and it hands out a "
      "record")

# ---------------------------------------------------------------------------
# 2. Three writes, and no others
# ---------------------------------------------------------------------------
#
# This module used only to read a colony. It no longer can: a town that runs
# itself has to be able to put a building in the world, ask for one to be
# built or gone over, and settle the list of things it is waiting for. Those
# are the three writes, and they all go through the seam.
#
# What it still may not do is reach past those into the host's own state -
# editing citizens, reassigning jobs, setting colony fields, spawning raids.
# The difference is that the three writes are instructions the host already
# takes from a player, issued by the town instead; anything else would be this
# module deciding something MineColonies decided.

ALLOWED_WRITES = ("addWorkOrder", "requestRepair", "overruleRequest",
                  "addNewBuilding", "setPlacedBy", "setBlock", "extractItem",
                  "injectModifier", "setStructurePack", "setBlueprintPath",
                  "removeBlock", "addPlayer", "removePlayer",
                  # For a town that founded itself, the instructions a player
                  # gives with the build tool and the town hall's permissions
                  # screen: found a colony, complete a building whole as
                  # creative does, strike its order, and hand the colony on.
                  "createColony", "addToQueue",
                  "upgradeBuildingLevelToSchematicData", "removeWorkOrder", "setOwner")

for path in java_files():
    src = strip_comments(read(path))
    rel = os.path.relpath(path, WORK)
    for writing in ("setCitizenCount", "setColony(", "getBuildingManager().set",
                    "getRaiderManager().set", "removeCitizen", "spawnRaider",
                    "getEventManager().add", "setJob(", "setHomeBuilding(",
                    "setSaturation(", "finishResearch(", "deleteColony",
                    "setBuildingLevel(", "destroy()", "deconstruct()"):
        if writing in src:
            fail(f"{rel} calls {writing}; a town may file the orders a player could "
                 f"file, and may not reach past them into the host's own state")

def writes_into_host(src, writing):
    """
    Whether this file makes that write itself.

    A plain substring will not do for {@code setPlacedBy}: it is both the
    host call the seam makes on a hut and the name of a vanilla Block method
    that any block in this module may override — and must call {@code super}
    on. What the rule is about is a call on somebody else's object, so that is
    what is looked for, and {@code super.} is explicitly not it.
    """
    if writing == "setPlacedBy":
        return bool(re.search(r"(?<!super)\.setPlacedBy\(", src))
    return writing in src


# And every one of the writes it does make has to be in the seam, so there is
# one file to read when the host changes how any of them work.
for path in java_files():
    if os.path.isfile(SEAM) and os.path.samefile(path, SEAM):
        continue
    src = strip_comments(read(path))
    rel = os.path.relpath(path, WORK)
    for writing in ("addWorkOrder", "requestRepair", "overruleRequest",
                    "addNewBuilding", "setPlacedBy", "injectModifier",
                    "addPlayer(", "removePlayer(", "setStructurePack("):
        if writes_into_host(src, writing):
            fail(f"{rel} writes into the host directly ({writing}); every write goes "
                 f"through the seam or there is no seam")

# The one call the host is not null-safe about.
if "requestUpgrade" in strip_comments(read(SEAM)):
    fail("the seam calls requestUpgrade, which hands its Player argument straight to "
         "a message sender on three branches with no null check - so a town running "
         "itself crashes the first time an upgrade is refused. File the work order "
         "instead")
for path in java_files():
    _fp_src = strip_comments(read(path))
    if "FakePlayer" in _fp_src:
        # One fake player, the steward of a town that founded itself, and only
        # where the host demands a Player: founding the colony and completing
        # its orders. Anywhere else it is a thing other mods' handlers cope with.
        _allowed_fp = (body_of(_fp_src, "public static int settle")
                       + body_of(_fp_src, "public static boolean finish"))             if os.path.samefile(path, SEAM) else ""
        _conjured = "FakePlayerFactory.get("
        if _fp_src.count(_conjured) != _allowed_fp.count(_conjured) or not _allowed_fp                 or _fp_src.count(_conjured) == 0:
            fail(f"{os.path.relpath(path, WORK)} conjures a fake player outside the "
                 f"seam's settle and finish; the steward is the one fake player this "
                 f"module needs, and a fake player in a colony is a thing other mods' "
                 f"event handlers then have to cope with")

print("three writes       : an order, a repair, a delivery - all through the seam, and "
      "nothing reaches past them")

# ---------------------------------------------------------------------------
# 3. Purity, and the harness
# ---------------------------------------------------------------------------

for name in sorted(os.listdir(PURE)):
    if not name.endswith(".java"):
        continue
    src = strip_comments(read(PURE, name))
    without_package = re.sub(r"^\s*package [^;]+;", "", src, count=1, flags=re.M)
    for forbidden in ("net.minecraft", "net.neoforged", "com.mojang", "com.elysium",
                      HOST):
        if forbidden in without_package:
            fail(f"rules/{name} reaches for {forbidden}; the rules are the JDK and "
                 f"nothing else, so they can be executed by a harness")

with tempfile.TemporaryDirectory() as tmp:
    staged = os.path.join(tmp, "src")
    os.makedirs(staged)
    sources = [os.path.join(PURE, n) for n in os.listdir(PURE) if n.endswith(".java")]
    for source in sources + [HARNESS]:
        open(os.path.join(staged, os.path.basename(source)), "w",
             encoding="utf-8").write(read(source))
    compiled = subprocess.run(
        ["javac", "-nowarn", "-d", os.path.join(tmp, "out")]
        + [os.path.join(staged, n) for n in os.listdir(staged)],
        capture_output=True, text=True)
    if compiled.returncode != 0:
        fail("the rules do not compile against the JDK alone:\n"
             + compiled.stderr.strip()[:1200])
    else:
        ran = subprocess.run(["java", "-cp", os.path.join(tmp, "out"), HARNESS_MAIN],
                             capture_output=True, text=True)
        if ran.returncode != 0:
            fail("the colony harness failed:\n" + harness_trouble(ran.stdout))
        else:
            print("the arithmetic     : " + ran.stdout.strip().splitlines()[-1])

# ---------------------------------------------------------------------------
# 4. The three sentences
# ---------------------------------------------------------------------------

# A town is progress: a floor, written through the library's one writer.
progress = strip_comments(read(SRC, "event/ColonyProgress.java"))
tick = body_of(progress, "public static void onTick")
if "ColonyRules.worldFloor(" not in tick:
    fail("a colony's contribution to the world tier is decided somewhere other than "
         "the rules, so the harness proves nothing about it")
if "ElysiumTiers.lift(" not in tick:
    fail("the colony's tier is written by some route other than lift(), which is the "
         "one place that refuses to let a floor fall - so a town raided down to ten "
         "citizens while its owner was out would cost them their progress")
if "instanceof ServerPlayer" not in tick:
    fail("the colony tick runs on the client too, where a colony is a view object with "
         "different numbers on it")
if "INTERVAL" not in tick:
    fail("the colony is asked on every tick of every player, for an answer that changes "
         "when somebody moves into a hut")

# A town is conspicuous: a floor source, installed before any player exists.
standing = strip_comments(read(SRC, "event/ColonyStanding.java"))
if "ElysiumStanding.addFloorSource(" not in standing:
    fail("nothing registers the colony as a source of Suspicion floor, so a town of "
         "thirty can be quietly forgotten")
if "ColonyRules.noticeFloor(" not in standing:
    fail("how noticed a settlement makes its owner is decided outside the rules")
main = strip_comments(read(SRC, "ElysiumColonies.java"))
if "ColonyStanding.install()" not in main:
    fail("the standing source is written and never installed, so it is a class nobody "
         "calls")
if "SubscribeEvent" in standing:
    fail("the standing floor is pushed in by a listener rather than registered as a "
         "source; a listener has to guess when to push, and is wrong the first time a "
         "player joins with a town already built")

# A town is worth raiding properly: promoted, capped, and once.
raids = strip_comments(read(SRC, "event/ColonyRaids.java"))
raid_tick = body_of(raids, "public static void onTick")
if "ColonyRules.raiderTier(" not in raid_tick:
    fail("raiders are promoted at a tier nobody capped, so a capital eventually sends "
         "something that walks through a town of level-five guards")
if "ColonyRules.promotes(" not in raid_tick:
    fail("every raid is promoted, including a new player's first, so the two systems "
         "meet before there is anything to meet about")
if "ElysiumElites.decided(" not in raid_tick:
    fail("a raider is examined on every tick of its life rather than once")
if "ElysiumElites.promote(" not in raid_tick:
    fail("nothing actually promotes a raider")
for scaling in ("setBaseValue", "getAttribute", "setHealth", "MAX_HEALTH"):
    if scaling in raids:
        fail(f"the raid handler calls {scaling}; MineColonies already scales a raid "
             f"against the colony's defences, and multiplying one difficulty curve by "
             f"another produces a fight neither author would recognise")
if not re.search(r"onTick\(EntityTickEvent\.Post", raids):
    fail("raiders are promoted at the moment they appear, which is before MineColonies "
         "has told them which colony they belong to and before it has written their "
         "attributes")
print("the three sentences: a floor that never falls, a file that cannot be forgotten, "
      "and a capped raid")

# ---------------------------------------------------------------------------
# What it declares
# ---------------------------------------------------------------------------

toml = read(RES, "META-INF/neoforge.mods.toml")
if not toml:
    fail("neoforge.mods.toml is missing")
else:
    declared = dict(re.findall(
        r'modId\s*=\s*"([a-z0-9_]+)"[\s\S]{0,80}?type\s*=\s*"(\w+)"', toml))
    if declared.get("minecolonies") != "required":
        fail("MineColonies is not a required dependency. Every other module here is "
             "Elysium's own replacement for something; this one is an add-on, and an "
             "add-on whose host is absent is a jar that loads and does nothing")
    if declared.get("elysiumlib") != "required":
        fail("elysiumlib is not required, but all three sentences are written in its "
             "vocabulary")
    for sibling in ("elysium", "elysiumarmory", "elysiumspace", "elysiumores",
                    "apotheosis", "silentgear"):
        if sibling in declared:
            fail(f"{sibling} is declared; this module needs the engine and the host")

# ---------------------------------------------------------------------------
# 5. The charter: a world decides what a town is
# ---------------------------------------------------------------------------
#
# The charters are data because the list of worlds is not this module's to
# know. What the module IS responsible for is that the files it ships agree
# with the worlds they describe - a charter claiming Cinderveil is a meadow
# would be a lie the game then acts on.

CHARTER_DIR = os.path.join(RES, f"data/{NS}/charter")
NEEDS_FILE = os.path.join(RES, f"data/{NS}/charter_needs/settlement_needs.json")
SPACE_WORLD = os.path.join(os.path.dirname(WORK),
                           "elysium-space/src/main/java/com/elysium/space/world/SpaceWorld.java")

if not os.path.isdir(CHARTER_DIR):
    fail("no charters are shipped, so every world is a meadow and siting a town "
         "somewhere decides nothing")
    _charters = {}
else:
    _charters = {}
    for _name in sorted(os.listdir(CHARTER_DIR)):
        if not _name.endswith(".json"):
            continue
        _body = json.load(open(os.path.join(CHARTER_DIR, _name), encoding="utf-8"))
        _dimension = _body.get("dimension")
        if not _dimension:
            fail(f"charter/{_name} names no dimension, so nothing can find it")
            continue
        if _dimension in _charters:
            fail(f"two charters claim {_dimension}; one of them is silently ignored")
        _charters[_dimension] = _body
        if _body.get("hazard", 0) < 0:
            fail(f"charter/{_name} has a negative hazard")
        if _body.get("reach", 0) < 0:
            fail(f"charter/{_name} is a negative distance from home")
        if _body.get("air") not in ("breathable", "poor", "none"):
            fail(f"charter/{_name} declares air the game has no word for: "
                 f"{_body.get('air')}")

if not os.path.isfile(NEEDS_FILE):
    fail("nothing says what a settlement consumes, so every charter scores zero and "
         "a rich world is worth the same as a bare one")
    _needs = []
else:
    _needs = json.load(open(NEEDS_FILE, encoding="utf-8")).get("needs", [])
    if len(_needs) < 4:
        fail("a settlement is said to consume almost nothing, so the overlap that "
             "scores a world cannot distinguish between worlds")

# Every charter has to be able to score above zero against the needs, or it is
# a world nobody could ever settle well - which is a decision, and has to be a
# deliberate one rather than a typo in a material name.
_scored = 0
for _dimension, _body in _charters.items():
    _overlap = [_family for _family in _body.get("natives", []) if _family in _needs]
    if _overlap:
        _scored += 1
    for _family in _body.get("natives", []):
        if _family not in _needs and _family not in ("cindrium", "cryonite",
                                                     "frostite", "lunite",
                                                     "rime_shard", "uranium",
                                                     "osmium", "fluorite"):
            fail(f"{_dimension} claims to produce {_family}, which is neither "
                 f"something a settlement needs nor a known exotic - most likely a "
                 f"misspelling, and a misspelling here silently costs that world its "
                 f"charter")
if _charters and _scored == 0:
    fail("no world produces anything a settlement needs, so every charter is bare and "
         "the whole system reads as a flat penalty")

# The hazards and air are elysium-space's numbers. A charter that disagreed
# with the world it describes would have a town comfortable on Cinderveil.
if os.path.isfile(SPACE_WORLD):
    _space = strip_comments(read(SPACE_WORLD), keep_strings=True)
    for _match in re.finditer(
            r'new SpaceWorld\(\s*"([a-z_]+)"\s*,\s*[0-9.]+[DdFf]?\s*,\s*Air\.(\w+)\s*,'
            r'\s*of\(([^)]*)\)', _space, re.S):
        _path, _air, _hazards = _match.groups()
        _total = round(sum(float(v) for v in re.findall(r'([0-9.]+)F', _hazards)), 3)
        _wanted_air = {"BREATHABLE": "breathable", "NONE": "none"}.get(_air, "poor")
        _dimension = "elysiumspace:" + _path
        _body = _charters.get(_dimension)
        if _body is None:
            fail(f"elysium-space declares the world {_path} and no charter describes "
                 f"it, so a town there lives in a meadow")
            continue
        if abs(float(_body.get("hazard", 0)) - _total) > 0.001:
            fail(f"{_dimension}'s charter says hazard {_body.get('hazard')} and the "
                 f"world itself says {_total}; the two have drifted and the game acts "
                 f"on the charter")
        if _body.get("air") != _wanted_air:
            fail(f"{_dimension}'s charter says its air is {_body.get('air')} and the "
                 f"world itself says {_wanted_air}")
else:
    fail("elysium-space is not beside this module, so the charters cannot be checked "
         "against the worlds they describe")

print(f"the charter        : {len(_charters)} world(s), each agreeing with the world it "
      f"describes; a settlement consumes {len(_needs)}")

# ---------------------------------------------------------------------------
# 6. The daily reckoning
# ---------------------------------------------------------------------------

_life = strip_comments(read(SRC, "event/ColonyLife.java"))
_tick = body_of(_life, "public static void onTick")
if "ServerLevel" not in _tick:
    fail("the daily reckoning runs on the client as well, where a colony is a view "
         "object with different numbers on it")
if "INTERVAL" not in _tick:
    fail("the reckoning is attempted on every tick of every level rather than on a "
         "schedule")
_reckon = body_of(_life, "private static void reckon")
if "ledger.lastDay()" not in _reckon or "town.day()" not in _reckon:
    fail("a town is reckoned more than once a day, so development accrues at the tick "
         "rate rather than the day rate")
for _rule in ("CitizenStress.totalFor(", "CitizenRules.output(", "GrowthRules.perDay(",
              "GrowthRules.banked(", "CharterRules.workFactor("):
    if _rule not in _reckon:
        fail(f"the reckoning does not go through {_rule}, so the harness proves "
             f"nothing about what a day is worth")
if "CharterRules.weighted(" not in _reckon:
    fail("distance does not weigh on the charter, so a town on the far side of the "
         "system can import its way out of its world exactly as easily as one next "
         "door")
_raise = body_of(_life, "private static int raiseSomething")
if "GrowthRules.willRaise(" not in _raise:
    fail("a town raises buildings past the level it is allowed to, so its owner has "
         "nothing left to come home for")
if "GrowthRules.affords(" not in _raise:
    fail("a town orders upgrades it has not paid for")
if "GrowthRules.patient(" not in _reckon:
    fail("a town orders every upgrade it can afford at once, so a returning player "
         "meets a builder with a queue and an empty warehouse")
if "isPendingConstruction" in _life:
    fail("the reckoning inspects the host's building state itself rather than through "
         "the seam")

# ---------------------------------------------------------------------------
# 7. People, and where what is known about them lives
# ---------------------------------------------------------------------------

_memory = strip_comments(read(SRC, "citizen/ColonyMemory.java"))
if "extends SavedData" not in _memory:
    fail("what this module knows about a citizen is not level data. MineColonies "
         "rebuilds a citizen's body whenever the chunk loads, so an attachment on the "
         "body is a lifetime of practice thrown away by a chunk unload")
if "AttachmentType" in _memory:
    fail("citizen state is kept as an entity attachment, which does not survive the "
         "host rebuilding the entity")
_practised = body_of(_memory, "public Life practised")
if "Math.max(0, more)" not in _practised:
    fail("practice can be taken away, so changing trade costs a citizen their "
         "lifetime of work")

_stress = strip_comments(read(SRC, "citizen/CitizenStress.java"))
for _stressor in ("HOMELESS", "UNEMPLOYED", "INJURED", "AT_HOME", "THREATENED",
                  "CROWDED"):
    if _stressor not in _stress:
        fail(f"a citizen is never {_stressor.lower()}, so one of the twelve reasons "
             f"the library knows about can never apply in a colony")
if "ElysiumStress.total(" not in _stress:
    fail("a citizen's stress is added up by this module rather than by the library, so "
         "a homeless citizen and a homeless villager are unhappy by different amounts")
if "CharterRules.burden(" not in _stress:
    fail("the world a citizen stands on costs them nothing, so Cinderveil is a meadow")
if "CharterRules.airBurden(" not in _stress:
    fail("unbreathable air costs a citizen nothing")

# ---------------------------------------------------------------------------
# 8. The one thing written into the host
# ---------------------------------------------------------------------------

if "registerHappiness" not in main:
    fail("this module's reading of a citizen's life never reaches the colony's own "
         "happiness screen, so every number it computes is invisible")
if "CitizenRules.happiness(" not in main:
    fail("the happiness handed to the host is not the one the harness proves")
_happy = body_of(seam, "public static boolean ensureHappiness")
if "injectModifier(" not in _happy:
    fail("the happiness modifier is built and never installed")
if "getModifier(" not in _happy:
    fail("the modifier is injected again on every reckoning, so a colony accumulates "
         "one copy of it per day")
if "new DynamicHappinessSupplier(HAPPINESS_FUNCTION)" not in _happy:
    fail("the happiness modifier holds a lambda rather than the id of a registered "
         "function, so it cannot be written to disk and read back")

print("the reckoning      : once a day, on the server, through the rules, and it shows "
      "in the colony's own screen")

# ---------------------------------------------------------------------------
# 9. Autonomy: nothing here may wait for a player
# ---------------------------------------------------------------------------
#
# The point of the whole module. Every one of these is a way a town could
# quietly stop and wait for somebody to come and press something.

_life = strip_comments(read(SRC, "event/ColonyLife.java"))
for _waiting in ("getPlayerList().getPlayer(", "someoneOnline", "onBehalfOf"):
    if _waiting in body_of(_life, "public static boolean foundNear") \
            or _waiting in body_of(_life, "private static int raiseSomething"):
        fail(f"founding or raising a building waits on {_waiting}, so a town only "
             f"grows while somebody is logged in")
_found = body_of(_life, "public static boolean foundNear")
if "SiteRules." not in _found:
    fail("a town chooses where to build by arithmetic the harness does not see, so "
         "nothing stops it putting two buildings in one place")
if "Colonies.inside(" not in _found:
    fail("a town builds outside its own ground")
if "Colonies.clearOf(" not in _found:
    fail("a town builds on top of what it has already built")
if "levelEnough(" not in _found:
    fail("a town builds up a cliff face, so nobody can walk between the huts")
if "Charters.plan().next(" not in body_of(_life, "private static void reckon"):
    fail("a town founds buildings in an order nobody wrote down, so a pack cannot "
         "say what a settlement should be")

_caretake = body_of(_life, "private static void caretake")
if "Colonies.repair(" not in _caretake:
    fail("nothing ever goes over a damaged building, so one creeper can leave a town "
         "permanently short of a working hut and no player is coming to notice")
if "% CARETAKING" not in _caretake:
    fail("the caretaker's round runs on no schedule at all")

_exchange = strip_comments(read(SRC, "exchange/FrontierExchange.java"))
_settle = body_of(_exchange, "public static int settle")
if "Colonies.owed(" not in _settle:
    fail("nothing reads the list of things a town is waiting for, which is the one "
         "list that stops it dead")
if "Colonies.deliver(" not in _settle:
    fail("a town works out what it needs and never hands it over")
if "Colonies.take(" not in _settle or "MarketRules.purseFrom(" not in _settle:
    fail("the exchange gives a town things it has not paid for, which is a town with "
         "no reason to produce anything")
if "MarketRules.buys(" not in _settle \
        or "Market.priceOf(where, want.wanted(), town.day())" not in _settle:
    fail("the exchange trades at a flat rate rather than at the market's prices, so "
         "where a town is stops mattering the moment it starts trading")
if "ItemStack.isSameItem(offer.getKey(), want.wanted())" not in _settle:
    fail("the exchange will trade a thing for itself, so a town with a delivery "
         "problem grinds eight of an item into one of the same item")
if "sold >= ExchangeRules.MOST_SPENT_PER_DAY" not in _settle:
    fail("a day's trading is not capped in the code that trades, so the exchange is "
         "an economy rather than a lifeline")

_owed = body_of(seam, "public static List<Owed> owed")
if "getPlayerResolver()" not in _owed:
    fail("the outstanding list is read from somewhere other than the player resolver, "
         "which is the only place a request goes when everything else has failed")
_deliver = body_of(seam, "public static boolean deliver")
if "overruleRequest(" not in _deliver:
    fail("a delivery closes the request by some route other than the one the town "
         "hall's own button uses, so the host's bookkeeping and this module's "
         "disagree")

_found_seam = body_of(seam, "public static boolean found")
if "setPlacedBy(" not in _found_seam:
    fail("a hut is put in the world without telling the block it was placed, so the "
         "colony never registers the building and a player is left with a hut nothing "
         "owns")
if "blueprintFor(" not in _found_seam:
    fail("a hut is placed with no blueprint, so the builder can never raise it and a "
         "player has to find and break the block")
if "removeBlock" not in _found_seam:
    fail("a hut that failed to register is left standing, so a failed founding litters "
         "the town with blocks nothing owns")

_plan_file = os.path.join(RES, f"data/{NS}/charter_needs/settlement_plan.json")
if not os.path.isfile(_plan_file):
    fail("no settlement plan is shipped, so a town never founds anything and the "
         "autonomy stops at upgrades")
else:
    _plan = json.load(open(_plan_file, encoding="utf-8")).get("wants", [])
    if not _plan:
        fail("the settlement plan is empty")
    elif "builder" not in _plan[0].get("building", ""):
        fail("the first thing a town builds is not a builder's hut. A colony with no "
             "builder cannot raise anything at all, including the builder")
    _seen = set()
    for _want in _plan:
        _id = _want.get("building", "")
        if _id in _seen:
            fail(f"the plan asks for {_id} twice; the second is unreachable")
        _seen.add(_id)
        if _want.get("from", 0) < 0:
            fail(f"{_id} is wanted at a negative population")

# ---------------------------------------------------------------------------
# 9b. A stake, not a home
# ---------------------------------------------------------------------------
#
# A player may hold a stake in several settlements. Nothing in the host
# prevents it; what has to be prevented is this module quietly re-introducing
# the assumption that there is exactly one.

_member = body_of(seam, "public static List<Settlement> memberOf")
if "isColonyMember(" not in _member:
    fail("membership is read as ownership, so a player who joined a friend's town "
         "counts for nothing and a player with two towns has one")
for _single in ("event/ColonyProgress.java", "event/ColonyStanding.java"):
    _src = strip_comments(read(SRC, _single))
    if "Colonies.ownedBy(" in _src:
        fail(f"{_single} reads only the colony a player owns, so a second stake is "
             f"invisible to the world tier and to the Empire's files")
    if "Colonies.memberOf(" not in _src:
        fail(f"{_single} does not read a player's memberships at all")
_release = body_of(seam, "public static boolean release")
if "getOwner()" not in _release:
    fail("leaving does not refuse for the owner. The host treats an owner leaving as "
         "abandonment - it renames them and can open the colony up - and a one-word "
         "command must not make that decision for somebody")
if "setOwnerAbandoned" in seam:
    fail("the seam calls setOwnerAbandoned; joining and leaving must never touch who "
         "owns a settlement")
_floor_rules = strip_comments(read(PURE, "ColonyRules.java"))
if "Math.max(best, worldFloor(citizens))" not in _floor_rules:
    fail("several stakes are added up rather than compared, so ten hamlets of four "
         "beat one town of forty and the frontier fills with arithmetic farms")

# ---------------------------------------------------------------------------
# 9c. The grant, and the watchdog
# ---------------------------------------------------------------------------

_relief = strip_comments(read(SRC, "exchange/FrontierExchange.java"))
_grant = body_of(_relief, "public static int grant")
if not _grant:
    fail("there is no charter grant, so a new colony can never get the builder's hut "
         "without which it can build nothing - including the builder's hut")
if "ReliefRules.granting(" not in _grant:
    fail("the grant hands over an amount nobody decided")
if "Colonies.owed(" not in _grant:
    fail("the grant gives a town things it was not waiting for, which is a colony as "
         "a way of getting free materials")

_reckon3 = body_of(_life, "private static void reckon")
if "ReliefRules.reliefFor(" not in _reckon3:
    fail("nothing notices that a town has stopped, so every lock this module did not "
         "foresee is permanent")
if "ReliefRules.progressed(" not in _reckon3:
    fail("progress is judged by something other than the rule, so the harness proves "
         "nothing about when a town counts as stuck")
if "foundedOn(town.day())" not in _reckon3:
    fail("a settlement's founding is never recorded, so the grant either never starts "
         "or never ends")
if "Relief.GRANT" not in _reckon3 or "Relief.REPORT" not in _reckon3:
    fail("the escalation stops before its last steps, so a stuck town is never helped "
         "or never reported")
if "ReliefRules.alsoReport(" not in _reckon3:
    fail("a town is only reported once its grant is spent, and the grant runs for a "
         "hundred days - so a colony stuck on something free materials cannot fix "
         "sits silent for months, which is the exact failure the watchdog exists to "
         "prevent")
if "ReliefRules.dueToReport(" not in _reckon3:
    fail("a stuck town is reported every single day, which is a message that stops "
         "being read on the third")
if "report(level, town," not in _reckon3 \
        or not body_of(_life, "private static boolean report("):
    fail("nothing ever tells a player their colony has stopped, so going on quietly "
         "forever is how this ends")

# ---------------------------------------------------------------------------
# 9d. The market
# ---------------------------------------------------------------------------

_market = strip_comments(read(SRC, "market/Market.java"))
if "Charters.of(" not in _market:
    fail("prices ignore the charter, so where a thing is does not change what it is "
         "worth and the whole market is one number")
if "MarketRules.drifted(" not in _market:
    fail("prices never drift, so a route learned once is a route forever")
if "familyOf(stack)" not in body_of(_market, "public static int priceOf"):
    fail("the market prices items rather than materials, so every mod's lead is a "
         "different commodity and unifying the pack bought nothing")
_price = body_of(_market, "public static int priceOf")
if "MarketRules.priceOf(" not in _price:
    fail("a price is worked out somewhere other than the rules")
if "Charters.needs().contains(" not in _price:
    fail("demand is not read from what a settlement actually consumes, so a world is "
         "equally short of everything it lacks")
_seed = body_of(_market, "private static int seedFor")
if "dimension" not in _seed:
    fail("every world's prices drift together, which is one market wearing many hats")

_values = strip_comments(read(SRC, "market/MarketValues.java"))
if "families" not in _values or "items" not in _values:
    fail("the market has no table of what things are worth")
_values_file = os.path.join(RES, f"data/{NS}/charter_needs/market_values.json")
if not os.path.isfile(_values_file):
    fail("no market values are shipped, so everything is worth the same and carrying "
         "cargo between worlds is pointless")
else:
    _table = json.load(open(_values_file, encoding="utf-8"))
    _fams = _table.get("families", {})
    if len(_fams) < 10:
        fail("almost nothing has a value, so the market cannot tell iron from coal")
    for _name, _worth in _fams.items():
        if not isinstance(_worth, int) or _worth < 1:
            fail(f"{_name} is worth {_worth}, which is not a price")
    if _fams.get("coal", 0) >= _fams.get("diamond", 0):
        fail("coal is worth as much as diamond, so the values table says nothing")
    for _needed in ("iron", "coal", "gold", "diamond"):
        if _needed not in _fams:
            fail(f"{_needed} has no value, so the commonest thing a town trades is "
                 f"priced as an unknown")

# ---------------------------------------------------------------------------
# 9e. A style per world
# ---------------------------------------------------------------------------
#
# MineColonies decides what a building is made of by which structure pack the
# colony builds in. A pack is shipped in a mod jar at blueprints/<modid>/<name>/
# and is picked up automatically. These are re-themed from MineColonies' own
# packs by substituting the palette, which is why the geometry, the tile
# entities and the hut anchors are untouched — and why the check below is that
# they really are untouched.

STYLE_DIR = os.path.join(RES, "blueprints", NS)
_styles = {}
if not os.path.isdir(STYLE_DIR):
    fail("no styles are shipped, so a town on Cinderveil is built out of cobblestone "
         "somebody carried four worlds")
else:
    for _name in sorted(os.listdir(STYLE_DIR)):
        _root = os.path.join(STYLE_DIR, _name)
        if not os.path.isdir(_root):
            continue
        _meta = os.path.join(_root, "pack.json")
        if not os.path.isfile(_meta):
            fail(f"style {_name} has no pack.json, so Structurize will not see it")
            continue
        _body = json.load(open(_meta, encoding="utf-8"))
        for _key in ("name", "icon", "authors", "desc", "mods", "version",
                     "pack-format"):
            if _key not in _body:
                fail(f"style {_name}'s pack.json has no {_key}; the loader reads all "
                     f"seven unconditionally and throws without them")
        if _body.get("pack-format") != 1:
            fail(f"style {_name} declares pack-format {_body.get('pack-format')}; "
                 f"anything but 1 is rejected outright")
        if _body.get("name") != _name:
            fail(f"style {_name}'s pack.json calls it {_body.get('name')}; the pack is "
                 f"keyed by that name and a charter naming the folder finds nothing")
        _icon = _body.get("icon", "")
        if _icon and not os.path.isfile(os.path.join(_root, _icon)):
            fail(f"style {_name} names an icon it does not ship")
        _prints = [os.path.join(_base, _f)
                   for _base, _, _names in os.walk(_root)
                   for _f in _names if _f.endswith(".blueprint")]
        if len(_prints) < 50:
            fail(f"style {_name} has {len(_prints)} blueprint(s); a style a colony can "
                 f"actually build in needs most of the building types")
        _styles[_name] = (_body, _prints)

# Every style a charter names has to exist, and every style shipped should be
# named by something, or it is three quarters of a megabyte nobody can reach.
_named = set()
for _dimension, _body in _charters.items():
    _style = _body.get("style", "")
    if not _style:
        continue
    _named.add(_style)
    if _style not in _styles:
        fail(f"{_dimension}'s charter builds in the style {_style}, which is not "
             f"shipped, so its towns silently keep whatever pack they had")
for _shipped in _styles:
    if _shipped not in _named:
        fail(f"the style {_shipped} is shipped and no charter names it, so it is "
             f"weight in the jar nobody can reach")

# The blueprints themselves: re-themed, not rebuilt.
if _styles:
    sys.path.insert(0, os.path.join(WORK, "tools"))
    import gzip as _gzip
    import io as _io
    import nbt as _nbt

    def _read_blueprint(_path):
        _raw = open(_path, "rb").read()
        _data = _gzip.decompress(_raw) if _raw[:2] == b"\x1f\x8b" else _raw
        _stream = _io.BytesIO(_data)
        _kind = _stream.read(1)[0]
        _nbt._rs(_stream)
        return _nbt._read(_stream, _kind)

    _checked = 0
    _elysium = 0
    for _name, (_body, _prints) in _styles.items():
        # A sample rather than all seventeen hundred: reading them all would
        # make this validator take minutes, and a substitution that went wrong
        # went wrong everywhere. Spread evenly rather than taken from the
        # front, because the front of an alphabetical list is decorations —
        # a bamboo plant is made of bamboo whatever the style, and a sample of
        # those would prove nothing about the buildings.
        # Every file is opened and its header read. That is a few seconds for
        # seventeen hundred blueprints and it is not optional: the bug this
        # catches — appending to an empty NBT list without correcting its
        # element type, which truncates everything after it — hit exactly the
        # handful of bridges and rails that needed no mods, and a sample of
        # eight would have shipped it.
        _ordered = sorted(_prints)
        _step = max(1, len(_ordered) // 8)
        _deep = set(_ordered[::_step][:8])
        _here = 0
        for _path in _ordered:
            _checked += 1
            try:
                _tree = _read_blueprint(_path).value
            except Exception as _broken:
                fail(f"{os.path.relpath(_path, WORK)} is not readable as a blueprint: "
                     f"{_broken}")
                continue
            for _key in ("size_x", "size_y", "size_z", "palette", "blocks",
                         "tile_entities", "required_mods"):
                if _key not in _tree:
                    fail(f"{os.path.relpath(_path, WORK)} has no {_key}; the "
                         f"substitution has damaged the file rather than re-themed it")
                    break
            if "palette" not in _tree or _path not in _deep:
                continue
            _blocks = [_p.value["Name"].value for _p in _tree["palette"].value
                       if "Name" in _p.value]
            _mine = [_b for _b in _blocks if _b.startswith("elysium")]
            _elysium += len(_mine)
            _here += len(_mine)
            _needed = {_t.value for _t in _tree.get("required_mods").value} \
                if "required_mods" in _tree else set()
            for _block in _mine:
                _namespace = _block.split(":", 1)[0]
                if _namespace not in _needed:
                    fail(f"{os.path.relpath(_path, WORK)} places a {_namespace} block "
                         f"and does not require {_namespace}, so it loads into a world "
                         f"without that mod and builds air")
        if _here == 0:
            fail(f"nothing in a spread sample of the {_name} style contains a single "
                 f"Elysium block, so it is MineColonies' own pack under a different "
                 f"name")
    if _elysium == 0:
        fail("no shipped blueprint contains a single Elysium block, so the styles are "
             "MineColonies' own packs under a different name")
    print(f"the styles         : {len(_styles)} style(s), "
          f"{_checked} blueprints re-read and intact")

print(f"the market         : {len(_fams) if os.path.isfile(_values_file) else 0} "
      f"famil(ies) priced by where you are and what day it is")

# ---------------------------------------------------------------------------
# 9f. Towns that found themselves
# ---------------------------------------------------------------------------
#
# Dylan: "There should be colonies that self found and start in the world."
# The world places a survey stake at sites a structure set chooses; the stake
# founds a real colony, owned by a named steward who is nobody, run by the
# town; a director completes its orders whole and lays out the next building
# on a cadence; a player may claim one. Every number is SettleRules', the
# structure set reads them from there, and the seam's four new writes are each
# confined to the one method that exists for it.
NS = "elysiumcolonies"
DATA = os.path.join(RES, "data", NS)
ASSETS = os.path.join(RES, "assets", NS)
_settle_rules = strip_comments(read(PURE, "SettleRules.java"))
_settle_words = strip_comments(read(PURE, "SettleRules.java"), keep_strings=True)
_lang_now = json.load(open(os.path.join(ASSETS, "lang/en_us.json"), encoding="utf-8"))     if os.path.isfile(os.path.join(ASSETS, "lang/en_us.json")) else {}
json_files = {}
for _d, _, _fs in os.walk(RES):
    for _f in _fs:
        if _f.endswith(".json"):
            try:
                json_files[os.path.normpath(os.path.join(_d, _f))] = json.load(
                    open(os.path.join(_d, _f), encoding="utf-8"))
            except ValueError:
                pass


def _json_at(*parts):
    return json_files.get(os.path.normpath(os.path.join(*parts)))
_seam_src = strip_comments(read(SEAM))
_settlements = os.path.join(SRC, "settle/Settlements.java")
_seed_block = os.path.join(SRC, "settle/SettlementSeedBlock.java")
for _needed in (_settlements, _seed_block, os.path.join(SRC, "registry/SettleBlocks.java")):
    if not os.path.isfile(_needed):
        fail(f"{os.path.relpath(_needed, WORK)} is gone; towns no longer found themselves")


def _int_rule(name):
    m = re.search(r"public static final int " + name + r" = ([0-9_]+);", _settle_rules)
    return int(m.group(1).replace("_", "")) if m else None


_spacing, _separation, _salt = _int_rule("SPACING"), _int_rule("SEPARATION"), _int_rule("SALT")
if None in (_spacing, _separation, _salt):
    fail("SettleRules is missing SPACING, SEPARATION or SALT, so the structure set has "
         "no rule to agree with")
_set = _json_at(DATA, "worldgen/structure_set/settlements.json")
_structure = _json_at(DATA, "worldgen/structure/settlement.json")
_pool = _json_at(DATA, "worldgen/template_pool/settlement/seed.json")
_tag = _json_at(DATA, "tags/worldgen/biome/settles_in.json")
if not _set or not _structure or not _pool or not _tag:
    fail("the settlement's structure, structure set, template pool or biome tag is not "
         "shipped, so no stake is ever placed")
else:
    _placement = _set.get("placement", {})
    if (_placement.get("spacing"), _placement.get("separation"), _placement.get("salt")) \
            != (_spacing, _separation, _salt):
        fail(f"the structure set places sites at spacing {_placement.get('spacing')}, "
             f"separation {_placement.get('separation')}, salt {_placement.get('salt')}; "
             f"SettleRules says {_spacing}, {_separation}, {_salt}, and the harness proved "
             f"the rule, not the file")
    if _placement.get("exclusion_zone", {}).get("other_set") != "minecraft:villages":
        fail("a settlement may be placed on top of a village")
    if _structure.get("start_pool") != f"{NS}:settlement/seed" or _structure.get("size") != 1:
        fail("the settlement structure is not the one-piece jigsaw that places a stake")
    if _structure.get("biomes") != f"#{NS}:settles_in":
        fail("the settlement structure does not read the settles_in tag, so which worlds "
             "settle is decided somewhere other than the charters")
    _elements = [e.get("element", {}).get("location") for e in _pool.get("elements", [])]
    if _elements != [f"{NS}:settlement_seed"]:
        fail(f"the seed pool places {_elements} rather than the one stake template")
    _nbt_path = os.path.join(DATA, "structure/settlement_seed.nbt")
    if not os.path.isfile(_nbt_path):
        fail("the stake template settlement_seed.nbt is not shipped")
    else:
        sys.path.insert(0, os.path.join(WORK, "tools"))
        import nbt as _nbt_reader
        _loaded = _nbt_reader.load(_nbt_path)
        _root = _loaded[1] if isinstance(_loaded, tuple) else _loaded
        _names = [getattr(v, "value", v) for v in _nbt_reader.strings(_root)]
        _names = [str(v) for v in _names]
        if f"{NS}:settlement_seed" not in _names:
            fail("the stake template does not contain the survey stake block")
    # Which worlds settle is the charters' decision, and the tag must agree.
    _settling = [c["dimension"] for c in _charters.values() if c.get("settles")]
    if "minecraft:overworld" not in _settling or "elysiumspace:emerald_bastion" not in _settling:
        fail("the Overworld or the Emerald Bastion does not settle; the worlds people live "
             "on are the worlds towns found themselves on")
    for _no in ("minecraft:the_nether", "minecraft:the_end"):
        if _no in _settling:
            fail(f"{_no} settles; nobody founds a town there")
    _values = _tag.get("values", [])
    if len(_values) < 100:
        fail(f"the settles_in tag lists {len(_values)} biome(s); the overlay alone has 140 "
             f"surface biomes, so most of the world cannot be settled")
    if any(v.startswith("elysiumwilds:vale_") for v in _values):
        fail("the settles_in tag reaches into the Vale, whose charter does not settle")
    if not any(v.startswith("elysiumspace:emerald_bastion") for v in _values):
        fail("no Bastion biome is in settles_in, so the one world built to be settled is not")
    for _v in _values:
        if re.search(r"cavern|grotto|well$|vault$|hollow$|_caves$|space$|orbit", _v.split(":")[-1]):
            fail(f"{_v} is in settles_in: a town cannot stand in a cave or in orbit")

# The stake: registered, ticking at random, and founding through Settlements.
_seed_src = strip_comments(read(_seed_block))
if "randomTicks()" not in _seed_src or "randomTick(" not in _seed_src:
    fail("the survey stake does not tick at random, so it never founds anything")
if "Settlements.found(level, pos)" not in _seed_src:
    fail("the stake's tick does not found the settlement")
if "BlockEntity" in _seed_src:
    fail("the stake is a block entity, ticking every tick in every loaded chunk for a thing "
         "that acts once")

# The abandoned half (docs/ROADMAP.md §3, item 1): a ruin marker on RuinRules'
# own grid raises one building of the world's style pack as the world left it,
# through one seam call that reads a blueprint and writes nothing, and founds
# no colony. Every number the structure set uses is RuinRules', and the harness
# proves the rule; what is held here is that the files agree with it, that the
# ruin never becomes a town, and that its chest has a table to fill from.
_ruin_rules = strip_comments(read(PURE, "RuinRules.java")) if os.path.isfile(os.path.join(PURE, "RuinRules.java")) else ""
_ruin_words = strip_comments(read(PURE, "RuinRules.java"), keep_strings=True) if _ruin_rules else ""
_ruins_src = os.path.join(SRC, "settle/Ruins.java")
_ruin_block = os.path.join(SRC, "settle/RuinSeedBlock.java")
if not _ruin_rules or not os.path.isfile(_ruins_src) or not os.path.isfile(_ruin_block):
    fail("the ruins are gone (RuinRules, settle/Ruins or settle/RuinSeedBlock): natural colonies have "
         "only their living half")
else:
    def _ruin_int(name):
        m = re.search(r"public static final int " + name + r" = ([0-9_]+);", _ruin_rules)
        return int(m.group(1).replace("_", "")) if m else None
    _r_spacing, _r_separation, _r_salt = _ruin_int("SPACING"), _ruin_int("SEPARATION"), _ruin_int("SALT")
    _r_set = _json_at(DATA, "worldgen/structure_set/ruins.json")
    _r_structure = _json_at(DATA, "worldgen/structure/ruin.json")
    _r_pool = _json_at(DATA, "worldgen/template_pool/ruin/seed.json")
    if not _r_set or not _r_structure or not _r_pool:
        fail("the ruin's structure, structure set or template pool is not shipped, so no marker is ever placed")
    else:
        _rp = _r_set.get("placement", {})
        if (_rp.get("spacing"), _rp.get("separation"), _rp.get("salt")) != (_r_spacing, _r_separation, _r_salt):
            fail(f"the ruins structure set places sites at spacing {_rp.get('spacing')}, separation "
                 f"{_rp.get('separation')}, salt {_rp.get('salt')}; RuinRules says {_r_spacing}, {_r_separation}, "
                 f"{_r_salt}, and the harness proved the rule, not the file")
        if _rp.get("exclusion_zone", {}).get("other_set") != f"{NS}:settlements":
            fail("a ruin may be placed on a town's site")
        if _r_structure.get("start_pool") != f"{NS}:ruin/seed" or _r_structure.get("biomes") != f"#{NS}:settles_in":
            fail("the ruin structure is not the one-piece jigsaw on the settles_in tag")
        _r_elements = [e.get("element", {}).get("location") for e in _r_pool.get("elements", [])]
        if _r_elements != [f"{NS}:ruin_seed"]:
            fail(f"the ruin pool places {_r_elements} rather than the one marker template")
    if not os.path.isfile(os.path.join(DATA, "structure/ruin_seed.nbt")):
        fail("the marker template ruin_seed.nbt is not shipped")
    _ruin_block_src = strip_comments(read(_ruin_block))
    if "randomTicks()" not in _ruin_block_src or "Ruins.raise(level, pos)" not in _ruin_block_src:
        fail("the ruin marker does not tick at random into Ruins.raise, so it never raises anything")
    if "BlockEntity" in _ruin_block_src:
        fail("the ruin marker is a block entity, ticking every tick for a thing that acts once")
    _ruins_text = strip_comments(read(_ruins_src), keep_strings=True)
    for _forbidden, _why in (("Colonies.settle(", "founds a colony"), ("Colonies.found(", "founds a building for a colony"),
                             ("Colonies.finish(", "completes a work order"), ("ColonyMemory", "writes colony memory")):
        if _forbidden in _ruins_text:
            fail(f"a ruin {_why} ({_forbidden!r} in settle/Ruins.java); a ruin is what a town is not")
    for _needed, _why in (("Colonies.blueprintOf(", "reads its blueprint anywhere but through the seam"),
                          ("RuinRules.keep(", "places every block, so it is a building, not a ruin"),
                          ("RuinRules.keep(seed, at.getX(), row, at.getZ(), height,",
                           "decays by the height in the world rather than the row in the building, so every block reads as roof"),
                          ("RuinRules.weathered(", "leaves its stone unweathered"),
                          ("RuinRules.flimsy(", "keeps glass and cloth a ruin would have lost"),
                          ("RuinRules.hostBlock(", "keeps the host's own blocks, a hut in a ruin, or its blueprint placeholders"),
                          ("setBlockEntityLootTable(", "leaves a chest with nothing in it")):
        if _needed not in _ruins_text:
            fail(f"settle/Ruins.java {_why} ({_needed!r} is gone)")
    _loot_id = re.search(r'LOOT = "([^"]+)"', _ruin_words)
    if not _loot_id:
        fail("RuinRules names no loot table")
    else:
        _ns, _path = _loot_id.group(1).split(":", 1)
        if not os.path.isfile(os.path.join(RES, "data", _ns, "loot_table", _path + ".json")):
            fail(f"the ruin's loot table {_loot_id.group(1)} is not shipped, so its chest is empty")
    if f"block.{NS}.ruin_seed" not in _lang_now:
        fail("the ruin marker has no name in the language file")
    if not problems or not any("ruin" in p for p in problems):
        print(f"the ruins          : a marker on its own grid ({_r_spacing}/{_r_separation}), never on a town, raising "
              f"one building decayed by the rules through one seam read, founding nothing, one chest from "
              f"{_loot_id.group(1) if _loot_id else '?'}")
_blocks_src = strip_comments(read(SRC, "registry/SettleBlocks.java"), keep_strings=True)
if 'BLOCKS.register(\n            "settlement_seed"' not in _blocks_src \
        and 'BLOCKS.register("settlement_seed"' not in _blocks_src:
    fail("settlement_seed is not registered")
if "SettleBlocks.BLOCKS.register(modEventBus)" not in strip_comments(read(SRC, "ElysiumColonies.java"), keep_strings=True):
    fail("SettleBlocks is never registered on the mod bus, so the stake block does not exist")
if "ITEMS" in _blocks_src:
    fail("the survey stake has an item; a player cannot place a town's promise")
for _asset in ("blockstates/settlement_seed.json", "models/block/settlement_seed.json",
               "textures/block/settlement_seed.png"):
    if not os.path.isfile(os.path.join(ASSETS, _asset)):
        fail(f"the stake has no {_asset}")
_seed_model = _json_at(ASSETS, "models/block/settlement_seed.json") or {}
if _seed_model.get("render_type") != "minecraft:cutout":
    fail("the stake's model reads transparent pixels and names no render layer")

# Founding: the world's refusals, in Settlements, and the seam's fake owner.
_found = body_of(strip_comments(read(_settlements)), "public static boolean found")
for _guard, _why in (("charter.settles()", "a world whose charter does not settle gets towns"),
                     ("farEnoughFromSpawn", "a town may found itself on the world spawn"),
                     ("BlockPos ground = dryGroundNear(level, stake);", "a stake on a lake is wasted rather than looking around"),
                     ("Colonies.mayFoundAt", "the host's own distance between colonies is not asked"),
                     ("SettleRules.name(", "the town is not named by the rule the harness proves"),
                     ("Steward.TOWN", "a self-founded town waits for an owner who does not exist"),
                     ("Origin.settled(", "a self-founded town is not remembered as one"),
                     ("firstWants()", "a self-founded town starts as a stake with a roof")):
    if _guard not in _found:
        fail(f"Settlements.found lacks {_guard}: {_why}")
_dry = body_of(strip_comments(read(_settlements)), "static BlockPos dryGroundNear")
for _guard, _why in (("SiteRules.RINGS", "the search for dry ground is not the site rings the daily pass uses"),
                     ("level.getFluidState(ground.below()).isEmpty()", "a town may found itself on water"),
                     ("levelEnough(level, ground)", "a town may found itself on a cliff")):
    if _guard not in _dry:
        fail(f"dryGroundNear lacks {_guard}: {_why}")
_settle = body_of(_seam_src, "public static int settle")
if "Player steward = FakePlayerFactory.get(level, new GameProfile(stewardId, stewardName));" \
        not in _settle \
        or "colony = manager.createColony(level, where, steward, name, pack);" not in _settle:
    fail("the seam does not found a colony for a fake steward")
if "setPlacedBy(" not in _settle or "order(colony, hall, WorkOrderType.BUILD)" not in _settle:
    fail("the seam founds a colony without a town hall to build")
for _write, _method in (("createColony(", "public static int settle"),
                        ("addToQueue(", "public static boolean finish"),
                        ("upgradeBuildingLevelToSchematicData(", "public static boolean finish"),
                        ("removeWorkOrder(", "public static boolean finish"),
                        ("setOwner(", "public static boolean handOver")):
    _in = body_of(_seam_src, _method)
    if _write not in _in:
        fail(f"{_method} does not {_write}")
    if _seam_src.count(_write) != 1:
        fail(f"{_write} appears {_seam_src.count(_write)} time(s) in the seam; it belongs to "
             f"{_method} alone")
    for path in java_files():
        if not os.path.samefile(path, SEAM) and _write in strip_comments(read(path)):
            fail(f"{os.path.relpath(path, WORK)} calls {_write} itself; every write goes "
                 f"through the seam")
_finish = body_of(_seam_src, "public static boolean finish")
if "WorkOrderType.REMOVE" not in _finish:
    fail("a self-founded town may demolish a building by itself")
# The host's future-taking placement never queues anything (its handler answers
# hasBluePrint before the future is read), so the seam reads the blueprint and
# queues the build tool's own operation on it.
if "loadAndPlaceStructureWithRotation" in _seam_src:
    fail("the seam completes an order through the host's future placement, which never queues it")
_finish_raw = body_of(read(SEAM), "public static boolean finish")
for _lines, _why in (
        (["Blueprint blueprint = StructurePacks.getBlueprint(",
          "                    order.getStructurePack(), order.getStructurePath(), level.registryAccess());"],
         "finish does not read the order's blueprint itself"),
        (["CreativeBuildingStructureHandler handler = new CreativeBuildingStructureHandler(",
          "                    level, order.getLocation(), blueprint, order.getRotationMirror(), false);"],
         "finish does not build the creative handler on the blueprint it read"),
        (["Manager.addToQueue(new PlaceStructureOperation(new StructurePlacer(handler), hands));"],
         "finish does not queue the build tool's own placement"),
        (["if (blueprint == null) {",
          "                ElysiumColonies.LOGGER.warn(\"order {} of colony {} could not be completed: \"",
          "                        + \"no blueprint {} in pack {}\", orderId, colonyId,",
          "                        order.getStructurePath(), order.getStructurePack());",
          "                return false;",
          "            }"],
         "a missing blueprint is not reported")):
    if "\n".join(_lines) not in _finish_raw:
        fail(_why)
# The director: natural towns only, on the rule's schedule.
_direct = body_of(strip_comments(read(_settlements)), "public static void direct")
if "origin.natural()" not in _direct or "return" not in _direct.split("natural()")[1][:40]:
    fail("the director runs for towns a player made")
if "if (!SettleRules.finished(town.day(), origin.lastFinished(), order.targetLevel())) {" \
        not in _direct \
        or "if (waiting.isEmpty() && SettleRules.mayFound(town.day(), origin.lastFinished())) {" \
        not in _direct:
    fail("the director completes or founds on a schedule other than SettleRules'")
if "Settlements.direct(level, town)" not in strip_comments(read(SRC, "event/ColonyLife.java")):
    fail("the daily pass never calls the director, so a self-founded town never builds")
# Claiming: only a natural town, and it stops being one.
_claim = body_of(strip_comments(read(_settlements)), "public static boolean claim")
if "origin.natural()" not in _claim or "origin.claimed()" not in _claim \
        or "if (!Colonies.handOver(level, town.id(), player)) {" not in _claim:
    fail("claiming does not hand a natural town over and mark it a player's")
_command = strip_comments(read(SRC, "command/ColonyCommand.java"), keep_strings=True)
for _word in ('literal("claim")', 'literal("natural")'):
    if _word not in _command:
        fail(f"the colony command has no {_word}")
# Names: the plain and the styled, and every shipped style has its own naming.
for _style in sorted(os.listdir(os.path.join(RES, "blueprints", NS))):
    if os.path.isdir(os.path.join(RES, "blueprints", NS, _style)) \
            and _settle_words.count(f'"{_style}"') < 2:
        fail(f"the {_style} pack has no naming of its own in SettleRules, so its towns are "
             f"named like an English village")
for _key in ("block.elysiumcolonies.settlement_seed", "elysium.colony.claimed",
             "elysium.colony.claim.none", "elysium.colony.natural.header",
             "elysium.colony.natural.entry", "elysium.colony.natural.none"):
    if _key not in _lang_now:
        fail(f"{_key} has no words")
if "SettleRules." not in strip_comments(read(HARNESS)):
    fail("the harness never runs SettleRules")
# The shipped files are what the generator writes.
_gen_settle = os.path.join(WORK, "tools/gen_settlements.py")
if not os.path.isfile(_gen_settle):
    fail("tools/gen_settlements.py is gone, so the settlement's files are kept by hand")
else:
    with tempfile.TemporaryDirectory() as _tmp:
        _made = subprocess.run([sys.executable, _gen_settle], capture_output=True, text=True,
                               env=dict(os.environ, ELYSIUM_SETTLE_OUT=_tmp), cwd=WORK)
        if _made.returncode != 0:
            fail("tools/gen_settlements.py will not run:\n" + (_made.stdout + _made.stderr)[-800:])
        else:
            _fresh = os.path.join(_tmp, "src/main/resources")
            _drift = []
            for _d, _, _fs in os.walk(_fresh):
                for _f in _fs:
                    _new = os.path.join(_d, _f)
                    _old = os.path.join(RES, os.path.relpath(_new, _fresh))
                    if not os.path.isfile(_old):
                        _drift.append(os.path.relpath(_new, _fresh) + " (missing)")
                    elif _f.endswith(".png"):
                        from PIL import Image as _Img
                        if _Img.open(_new).convert("RGBA").tobytes() \
                                != _Img.open(_old).convert("RGBA").tobytes():
                            _drift.append(os.path.relpath(_new, _fresh))
                    elif open(_new, "rb").read() != open(_old, "rb").read():
                        _drift.append(os.path.relpath(_new, _fresh))
            if _drift:
                fail(f"{len(_drift)} settlement file(s) differ from what tools/gen_settlements.py "
                     f"writes: " + ", ".join(sorted(_drift)[:6]))
# Every building the plan names must be one the host has: its registry throws
# on a miss, and the first live town crashed the game on "minecolonies:home"
# (the host calls it a residence). The hut blockstates in the MineColonies jar
# are the ids, read rather than listed.
_mc_jar = next((os.path.join(d, f) for d in (os.environ.get("ELYSIUM_COMPAT_LIBS", "/tmp/mods"), "C:/tmp/mods")
                if os.path.isdir(d) for f in sorted(os.listdir(d)) if f.startswith("minecolonies-") and f.endswith(".jar")), None)
_plan_ids = [w["building"] for w in json_files.get(os.path.normpath(os.path.join(DATA, "charter_needs/settlement_plan.json")), {}).get("wants", [])]
if _mc_jar:
    # The ids are the host's own constants (HOME_ID = "residence", whose hut
    # block is called citizen), read with javap rather than guessed from names.
    _jp = subprocess.run(["javap", "-constants", "-cp", _mc_jar,
                          "com.minecolonies.api.colony.buildings.ModBuildings"],
                         capture_output=True, text=True)
    _huts = set(re.findall(r'String \w+_ID = "([a-z_]+)";', _jp.stdout))
    if not _huts:
        import zipfile as _zf
        with _zf.ZipFile(_mc_jar) as _z:
            _huts = {n.split("/blockhut", 1)[1][:-5] for n in _z.namelist()
                     if n.startswith("assets/minecolonies/blockstates/blockhut") and n.endswith(".json")}
    for _want in _plan_ids:
        if _want.split(":", 1)[1] not in _huts:
            fail(f"the settlement plan wants {_want}, which is not a building MineColonies has; "
                 f"the host's registry throws on it and the tick that founds it crashes the game")
    _plan_note = f"{len(_plan_ids)} plan building(s) all known to the host"
else:
    _plan_note = f"{len(_plan_ids)} plan building(s), host jar not found so not checked"
if "containsKey(kindId)" not in body_of(_seam_src, "public static boolean found(ServerLevel level, int colonyId, String kind"):
    fail("the seam asks the host's building registry for a kind without checking it exists; the "
         "registry throws on a miss")
print(f"settlement plan    : {_plan_note}")
print(f"settlements        : towns found themselves on {len(_settling) if _set else 0} world(s), "
      f"one site per {_spacing} chunks, in {len(_values) if _tag else 0} biome(s); a steward who is "
      f"nobody, a director that builds whole, and a claim that hands it on")

# ---------------------------------------------------------------------------
# 10. Whose map it is
# ---------------------------------------------------------------------------
#
# A town that sites its own buildings is the point of the autonomy, and it is
# also an intrusion on a player standing in that town with a plan for the north
# quarter. Placement follows attendance; everything else does not.

_reckon2 = body_of(_life, "private static void reckon")
if "StewardRules.maySite(" not in _reckon2:
    fail("a town sites buildings without asking whether its owner is tending it, so a "
         "player comes back to a hut where they were going to put the market")
if "ownerIsHere(" not in _reckon2:
    fail("nothing records that the owner was here, so attendance can never be "
         "established and the stewardship rule decides nothing")
_here = body_of(_life, "private static boolean ownerIsHere")
if "Colonies.inside(" not in _here:
    fail("attendance is 'the owner is logged in' rather than 'the owner is here', so a "
         "player mining on the other side of the world stops their colony developing")
if "seenOn(" not in _reckon2:
    fail("attendance is read and never written, so a visit is forgotten by the next day")

# The gate is on founding and on nothing else. Raising, repairing and trading
# change what is already standing where its owner put it.
_found_call = _reckon2[_reckon2.find("townDecides"):]
if "founded = foundNear(" not in _found_call:
    fail("the stewardship gate does not guard founding, which is the one thing it "
         "exists to guard")
for _unguarded, _what in (("raiseSomething(", "raising"), ("caretake(", "repairing"),
                          ("FrontierExchange.settle(", "trading")):
    _at = _reckon2.find(_unguarded)
    if _at < 0:
        fail(f"{_what} no longer happens at all")
    elif _at > _reckon2.find("townDecides") >= 0 and "townDecides" in \
            _reckon2[max(0, _at - 200):_at]:
        fail(f"{_what} is gated on who decides placement; it changes what is already "
             f"standing where its owner put it and should carry on either way")

_suggest = body_of(_life, "private static boolean suggest(")
if not _suggest:
    fail("a town that defers to its owner never tells them what it wanted, so the "
         "player simply never learns what it needs")
if "StewardRules.dueToSuggest(" not in _reckon2:
    fail("a deferring town nags its owner every single day, which is a message a "
         "player turns off")

_steward_rules = strip_comments(read(PURE, "StewardRules.java"))
if "case OWNER -> false" not in _steward_rules:
    fail("an owner who reserved placement can still be built around")
if "case TOWN -> true" not in _steward_rules:
    fail("an owner who delegated placement is still asked")
if "!everVisited(lastSeen)" not in _steward_rules:
    fail("a town founded and abandoned waits out an absence from a visit that never "
         "happened, so an unattended colony sits idle for its first three days")

_command = strip_comments(read(SRC, "command/ColonyCommand.java"))
if "withSteward(" not in _command:
    fail("there is no way for a player to say who decides placement, so the only "
         "control they have is whether they happen to be standing there")
# "mine" is the player's own town: the one underfoot when it is theirs, else the
# first they own. Both the stewardship and the report commands go through it.
_mine = body_of(_command, "private static Colonies.Settlement mine")
if "Colonies.Settlement town = mine(player);" not in body_of(_command, "private static int hand")         or "if (here != null && player.getUUID().equals(here.owner())) {" not in _mine         or "return Colonies.ownedBy(player);" not in _mine:
    fail("the stewardship command does not check whose colony it is, so a player can "
         "reach across the map and change who runs somebody else's town")

_ledger = body_of(strip_comments(read(SRC, "citizen/ColonyMemory.java")),
                  "public record Ledger")
if "optionalFieldOf(\"last_seen\"" not in strip_comments(
        read(SRC, "citizen/ColonyMemory.java"), keep_strings=True):
    fail("the ledger's new fields are not optional, so an existing world's colonies "
         "fail to load")

print("whose map it is    : placement follows attendance, everything else carries on, "
      "and the owner can say otherwise")

print(f"autonomy           : founds, raises, repairs and trades with nobody logged in; "
      f"{len(_plan) if os.path.isfile(_plan_file) else 0} building(s) planned")

# ---------------------------------------------------------------------------
# The spaceport
# ---------------------------------------------------------------------------
#
# A colony's way out of its own world. Three things have to hold or it is
# either furniture or a duplication bug: the structure must be real and
# breakable, every number must come from the pure rules, and nothing may leave
# one colony's stores without that colony being paid for it.

_elevator = strip_comments(read(SRC, "port/SpaceElevator.java"))
_port_blocks = strip_comments(read(SRC, "registry/PortBlocks.java"))
_ship = strip_comments(read(SRC, "port/Spaceport.java"))
_berths = strip_comments(read(SRC, "port/PortRegistry.java"))
_anchor_block = strip_comments(read(SRC, "port/PortAnchorBlock.java"))

PORT_BLOCKS = ("elevator_anchor", "elevator_tether", "elevator_counterweight",
               "elevator_pylon", "elevator_collar", "elevator_deck")

# Flattened, because where the line breaks fall in a registration is a
# formatting decision and a rule that depended on it would fail the next time
# somebody ran a formatter over the file.
_registered = re.sub(r"\s+", "",
                     strip_comments(read(SRC, "registry/PortBlocks.java"),
                                    keep_strings=True))
for _block in PORT_BLOCKS:
    # Both halves, named exactly. A block registered without its item is one
    # nobody can carry, and looking only for the name somewhere in the file
    # would be satisfied by whichever half survived.
    if f'BLOCKS.register("{_block}"' not in _registered:
        fail(f"{_block} is not registered as a block, so a spaceport cannot be built "
             f"out of it")
    if f'ITEMS.register("{_block}"' not in _registered:
        fail(f"{_block} has no item, so it can be placed by a command and by nothing "
             f"a player can hold")
    for _what, _where in (
            ("blockstate", f"assets/{NS}/blockstates/{_block}.json"),
            ("model", f"assets/{NS}/models/block/{_block}.json"),
            ("item model", f"assets/{NS}/models/item/{_block}.json"),
            ("texture", f"assets/{NS}/textures/block/{_block}.png"),
            ("drop", f"data/{NS}/loot_table/blocks/{_block}.json"),
            ("recipe", f"data/{NS}/recipe/{_block}.json")):
        if not os.path.isfile(os.path.join(RES, _where)):
            fail(f"{_block} has no {_what}; a block a player cannot make, see or keep "
                 f"is not a block")

# An add-on may be the only piece of Elysium somebody has installed, so the one
# structure it adds has to be buildable out of the base game.
for _block in PORT_BLOCKS:
    _recipe = os.path.join(RES, f"data/{NS}/recipe/{_block}.json")
    if os.path.isfile(_recipe):
        for _named in re.findall(r'"item"\s*:\s*"([^"]+)"',
                                 open(_recipe, encoding="utf-8").read()):
            # Vanilla, or something this module makes itself. Anything else
            # is a mod a player might not have, and a spaceport nobody can
            # build is a spaceport nobody builds.
            if not _named.startswith(("minecraft:", f"{NS}:")):
                fail(f"{_block}'s recipe needs {_named}; this module is an add-on to "
                     f"MineColonies and may be the only Elysium jar installed, so its "
                     f"one structure has to be buildable out of the base game")

# ---------------------------------------------------------------------------
# What mines them (rules/HarvestRules; the harvest audit of 29 Sep 2026)
# ---------------------------------------------------------------------------
# Every part of the elevator asks for the right tool (PortBlocks.metal() says
# so), and a block that asks for the right tool drops nothing unless some
# tool's mineable tag names it. Nothing named them: a colony that took its
# elevator down lost every block of it. So every registered block is in
# exactly one of this module's mineable tags, or named BY_HAND in the rules
# with its reason here; an exemption is read where the block is registered —
# through the properties helper it calls, not taken on trust; the tags are
# what the rules say, written by tools/gen_port.py; and a tag names only
# blocks this module registers, because one unknown id makes the game refuse
# the whole tag.
_harvest_src = strip_comments(read(PURE, "HarvestRules.java"), keep_strings=True)
if not _harvest_src:
    fail("rules/HarvestRules.java is missing, so nothing says what mines the elevator "
         "and the tags are written from nothing")


def _harvest_list(name):
    found = re.search(r"List<String> " + name + r" = List\.of\(([^;]*?)\);", _harvest_src, re.S)
    return re.findall(r'"([a-z_]+)"', found.group(1)) if found else []


HARVEST_TOOLS = ("pickaxe", "axe", "shovel", "hoe")
MINEABLE = {}
for _tool in HARVEST_TOOLS:
    _tag = os.path.join(RES, "data/minecraft/tags/block/mineable", _tool + ".json")
    if not os.path.isfile(_tag):
        continue
    _doc = json.load(open(_tag, encoding="utf-8"))
    if _doc.get("replace"):
        fail(f"mineable/{_tool} sets replace: true, which throws away every other mod's "
             f"entries in the game's own tag")
    for _one in _doc.get("values", []):
        _named = _one.get("id") if isinstance(_one, dict) else _one
        if not isinstance(_named, str):
            continue
        MINEABLE.setdefault(_named, set()).add(_tool)
        if _named.startswith(f"{NS}:") and not os.path.isfile(os.path.join(
                RES, f"assets/{NS}/blockstates", _named.split(":", 1)[1] + ".json")):
            fail(f"mineable/{_tool} names {_named}, which is not a block this module "
                 f"registers, so the game refuses the whole tag and every block in it "
                 f"is back to dropping nothing")

#: The blocks no tool is for, and why. HarvestRules.BY_HAND says which; a block
#: it names with no reason here is refused, and so is a reason for a block it
#: no longer names. Nothing, today: the stakes are wood and stone, so a tool
#: sets their speed even though neither asks for one.
MINE_EXEMPT = {}

#: What the game's blocks this module copies ask of a tool, read out of the
#: game's own jar. Nothing here copies one; a block that starts to is looked
#: up here or refused.
ASKS_FOR_A_TOOL = {}


def asks_for_a_tool(made, what):
    """Whether a block built like this asks for the right tool, and how."""
    if not made:
        fail(f"nothing here finds how {what} is built, so nothing shows whether it "
             f"asks for the right tool")
        return None, ""
    if ".requiresCorrectToolForDrops()" in made:
        return True, "it says so where it is registered"
    for copied in re.findall(r"\bBlocks\.([A-Z_]+)", made):
        if copied not in ASKS_FOR_A_TOOL:
            fail(f"{what} copies Blocks.{copied}, and nothing here knows whether that "
                 f"asks for the right tool: look it up in the game's jar and add it")
            return None, ""
        if ASKS_FOR_A_TOOL[copied]:
            return True, f"it copies Blocks.{copied}, which does"
    return False, ""


_registries = [strip_comments(read(SRC, "registry/PortBlocks.java"), keep_strings=True),
               strip_comments(read(SRC, "registry/SettleBlocks.java"), keep_strings=True)]
# A properties helper shared by several registrations (PortBlocks.metal) is
# where the tool is asked for, so a registration that calls one reads as the
# call and the helper's body together.
_property_helpers = {}
for _registry in _registries:
    for _helper in re.findall(r"private static BlockBehaviour\.Properties (\w+)\(", _registry):
        _property_helpers[_helper] = re.sub(r"\s+", " ", body_of(
            _registry, f"private static BlockBehaviour.Properties {_helper}("))


def _built_as(name):
    for _registry in _registries:
        _flat = re.sub(r"\s+", " ", _registry)
        _at = re.search(r'BLOCKS\.register\( ?"' + name + '"', _flat)
        if not _at:
            continue
        _made = _flat[_at.start():_flat.find(";", _at.start())]
        for _helper, _body in _property_helpers.items():
            if re.search(r"\b" + _helper + r"\(", _made):
                _made += " " + _body
        return _made
    return ""


_registered_blocks = re.findall(r'BLOCKS\.register\(\s*"([a-z_]+)"', "\n".join(_registries))
_blockstate_dir = os.path.join(RES, f"assets/{NS}/blockstates")
for _name in sorted(os.path.splitext(f)[0] for f in os.listdir(_blockstate_dir)
                    if f.endswith(".json")) if os.path.isdir(_blockstate_dir) else []:
    if _name not in _registered_blocks:
        fail(f"{_name} has a blockstate and no registration in PortBlocks or SettleBlocks, "
             f"so nothing here can say what mines it")
_by_hand = _harvest_list("BY_HAND")
for _name in _by_hand:
    if _name not in MINE_EXEMPT:
        fail(f"HarvestRules breaks {_name} by hand and this gives no reason, so it is left "
             f"out of every tool's tag on nobody's say-so")
for _name in MINE_EXEMPT:
    if _name not in _by_hand:
        fail(f"a reason is given for breaking {_name} by hand and HarvestRules no longer "
             f"does, so the exemption covers a block the rules put in a tool's tag")
_rule_tools = {}
for _tool in HARVEST_TOOLS:
    for _name in _harvest_list(_tool.upper()):
        _rule_tools.setdefault(_name, []).append(_tool)
for _name in sorted(set(_rule_tools) | set(_by_hand)):
    if _name not in _registered_blocks:
        fail(f"HarvestRules says what mines {_name}, and no such block is registered, so "
             f"the tag it is written into is refused whole")
_tool_blocks = []
_asking_untagged = []
_untagged = []
for _name in _registered_blocks:
    _asks, _how = asks_for_a_tool(_built_as(_name), f"the {_name}")
    _tags = MINEABLE.get(f"{NS}:{_name}", set())
    if _asks:
        _tool_blocks.append(_name)
    if len(_tags) > 1:
        fail(f"{_name} is in {len(_tags)} mineable tags ({', '.join(sorted(_tags))}), so "
             f"two tools are right and the rules can only have named one")
    if _name in MINE_EXEMPT:
        if _asks:
            fail(f"{_name} is exempt from the mineable tags ({MINE_EXEMPT[_name]}), but it "
                 f"asks for the right tool: {_how}. With no tag no tool is right, so it "
                 f"would drop nothing")
        if _tags:
            fail(f"{_name} is exempt from the mineable tags and in one all the same; take "
                 f"it off MINE_EXEMPT or out of the tag")
        continue
    if not _tags:
        (_asking_untagged if _asks else _untagged).append(_name)
    if sorted(_rule_tools.get(_name, [])) != sorted(_tags):
        fail(f"the rules put {_name} under {' and '.join(_rule_tools.get(_name, [])) or 'no tool'} "
             f"and the tags under {' and '.join(sorted(_tags)) or 'none'}; the tags are "
             f"written from the rules by tools/gen_port.py, so one of them was edited by "
             f"hand")
if _asking_untagged:
    fail(f"{len(_asking_untagged)} block(s) ask for the right tool and are in no tool's "
         f"tag, so no tool is ever right and they drop nothing however they are mined: "
         + ", ".join(_asking_untagged))
if _untagged:
    fail(f"{len(_untagged)} block(s) are in no mineable tag and not exempt, so every tool "
         f"breaks them at a fist's pace: " + ", ".join(_untagged))
# And each tag is the rules' list in the rules' order, which is what
# tools/gen_port.py writes; anything else was typed.
for _tool in HARVEST_TOOLS:
    _tag = os.path.join(RES, "data/minecraft/tags/block/mineable", _tool + ".json")
    _shipped = (json.load(open(_tag, encoding="utf-8")).get("values", [])
                if os.path.isfile(_tag) else [])
    _wanted = [f"{NS}:{name}" for name in _harvest_list(_tool.upper())]
    if _shipped != _wanted:
        fail(f"mineable/{_tool} is not what tools/gen_port.py writes from HarvestRules "
             f"({len(_shipped)} named, {len(_wanted)} in the rules)")
print(f"what mines them    : {len(_registered_blocks)} block(s), {len(_tool_blocks)} asking "
      f"for the right tool, every one in exactly one tool's tag or broken by hand with a "
      f"reason: " + ", ".join(f"{sum(1 for t in _rule_tools.values() if t == [tool])} by {tool}"
                              for tool in HARVEST_TOOLS
                              if any(t == [tool] for t in _rule_tools.values()))
      + f", {len(MINE_EXEMPT)} by hand")

# The structure is read out of the world, and every part of it is load-bearing.
_read = body_of(_elevator, "public static Reading read(")
if not _read:
    fail("nothing reads the elevator out of the world, so a spaceport is a claim "
         "rather than a building")
for _needed, _why in (
        ("ELEVATOR_TETHER", "the column is never walked, so a port with no tether "
                            "ships as well as one with two hundred blocks of it"),
        ("ELEVATOR_COUNTERWEIGHT", "nothing checks for a counterweight, so a bare "
                                   "mast is a spaceport"),
        ("PortRules.tall(", "the minimum height is not applied where the world is "
                            "read, so a three-block stub counts")):
    if _needed not in _read:
        fail(f"the elevator reading does not look at {_needed}: {_why}")
if "openAbove(" not in _read:
    fail("nothing checks the sky above the counterweight, so a space elevator inside "
         "a mountain is a very tall warehouse")
if "Heightmap.Types.MOTION_BLOCKING" not in body_of(_elevator,
                                                    "public static boolean openAbove("):
    fail("the sky is checked by walking upward rather than off the heightmap, which "
         "is as many block reads as there is sky, every day, per port")

# Every number the port uses comes from the pure rules, which are executed —
# and each is looked for in the method that has to make it, because a rule
# satisfied by the same call somewhere else in the file is no rule at all.
_run_body = body_of(_ship, "public static Run run(")
_freight_body = body_of(_ship, "private static Freight freight(")
for _needed, _where, _scope, _why in (
        ("PortRules.outbound(", "the day's run", _run_body,
         "how much a port can send is decided in the handler rather than in the "
         "rules, where it is tested"),
        ("PortRules.inbound(", "the day's run", _run_body,
         "how much a port can receive is decided in the handler"),
        ("PortRules.banked(", "the day's run", _run_body,
         "takings are banked without the treasury's ceiling, so a decade of exports "
         "buys a tech tree in an afternoon"),
        ("PortRules.spareFor(", "freight", _freight_body,
         "a port would give a sister colony everything it had to send that day"),
        ("PortRules.midPrice(", "freight", _freight_body,
         "freight is priced by the handler rather than at the mid price two ports "
         "agree on"),
        ("PortRules.freightBuys(", "freight", _freight_body,
         "a colony orders freight without asking what its treasury covers"),
        ("PortRules.freightCost(", "freight", _freight_body,
         "freight is taken without being priced")):
    if _needed not in _scope:
        fail(f"{_where} never calls {_needed}: {_why}")

# Export: never sell the thing the town is stopped on.
_sell = body_of(_ship, "private static Sale sell(")
if not _sell:
    fail("the port never exports anything, so a colony's surplus is still worth "
         "nothing to it")
if "waitingFor(waiting, offer.getKey())" not in _sell:
    fail("the port sells the town's surplus without checking the outstanding list, so "
         "a settlement stopped on sixty planks sells the planks its sawmill just made "
         "and buys them back dearer")
if "Colonies.surplusOf(level, town.id(), ExchangeRules.KEEP)" not in _sell:
    fail("the port decides for itself what a surplus is rather than using the same "
         "reserve the exchange does, so the two systems disagree about what a town "
         "can spare")

# Freight: crosses worlds, never trades with itself, and always pays.
_freight = _freight_body
if not _freight:
    fail("there is no freight between colonies, so a second spaceport does nothing "
         "the first one did not")
if "server.getLevel(" not in _freight:
    fail("freight never leaves the world it started in, which is the one thing a space "
         "elevator was for")
if "sameTown(other, town)" not in _freight:
    fail("a colony can ship to itself, which is a way of turning the market spread "
         "into free goods")
if "PortRules.freightCost(taken, mid)" not in _freight:
    fail("freight is paid for by what was ordered rather than by what actually left "
         "the seller's stores, so a short delivery still charges full price")
if "pay(theirs, other, cost)" not in _freight:
    fail("goods leave another colony's warehouse and nobody pays that colony, which is "
         "not freight, it is theft with extra steps")
if "Colonies.deliver(level, town.id(), want, shipping)" not in _freight \
        or _freight.find("Colonies.deliver(") > _freight.find("Colonies.take(theirs"):
    fail("freight takes from the seller before the buyer's request has accepted the "
         "goods, so a refused delivery destroys a player's materials")
_pay = body_of(_ship, "private static void pay(")
if "withTreasury(" not in _pay or "PortRules.banked(" not in _pay:
    fail("a freight sale is not banked in the seller's own treasury under its own "
         "ceiling, so either the seller is not paid or its treasury has no limit")

# Import: bounded by the treasury and priced like a player's.
_buy = body_of(_ship, "private static Purchase buy(")
if "MarketRules.buys(purse - spent, price)" not in _buy:
    fail("the port buys without subtracting what it has already spent today, so a "
         "treasury can be spent twice in one pass")
if "MarketRules.costOf(buying, price)" not in _buy:
    fail("what the port bought is never taken out of the treasury")
if "Market.priceOf(" not in _buy:
    fail("imports are priced at something other than this world's market, so where a "
         "colony stands stops mattering")

# A town with no port is untouched, which is most towns.
_run = _run_body
if "return idle(treasury);" not in _run:
    fail("a colony with no spaceport is not simply left alone, so the one feature "
         "nobody has built yet changes every settlement in the save")
if "verified(level, ports, town)" not in _run:
    fail("the port runs on its registry entry without ever re-reading the world, so "
         "cutting the tether does nothing")
_verified = body_of(_ship, "private static PortRegistry.Berth verified(")
if "hasChunkAt(" not in _verified:
    fail("the daily pass reads a three-hundred-block column whether or not anybody is "
         "near it, which is a chunk load per port per day forever")
if "ports.forget(town.dimension(), town.id())" not in _verified:
    fail("a port whose anchor is gone stays in the registry, so a dismantled spaceport "
         "goes on shipping")

# One registry, on the world every server has.
if "Level.OVERWORLD" not in body_of(_berths, "public static PortRegistry of("):
    fail("the port registry is kept per world, so the first freight run between two "
         "worlds finds nothing on the other side")
if "optionalFieldOf(\"treasury\"" not in strip_comments(
        read(SRC, "citizen/ColonyMemory.java"), keep_strings=True):
    fail("the treasury is not an optional ledger field, so every colony in an existing "
         "save fails to load the day this ships")

# The anchor: it registers what it reads, and forgets it when it is broken.
_remove = body_of(_anchor_block, "protected void onRemove(")
if "forgetAt(" not in _remove:
    fail("breaking the anchor leaves the port in the registry, so a spaceport survives "
         "being dismantled")
if "!state.is(replacement.getBlock())" not in _remove:
    fail("the anchor deregisters itself on any block update, so a port is lost when a "
         "neighbour changes")
if "ports.record(" not in body_of(_anchor_block, "public static void report("):
    fail("talking to an anchor does not record the port, and nothing else looks for "
         "one, so a built spaceport is never found")

# Every complaint the reading can make is answered in words.
_complaints = re.findall(r'return "([a-z]+)";',
                         strip_comments(read(SRC, "port/SpaceElevator.java"),
                                        keep_strings=True))
for _complaint in _complaints:
    if f'"elysium.colony.port.{_complaint}"' not in strip_comments(
            read(SRC, "port/PortAnchorBlock.java"), keep_strings=True):
        fail(f"the elevator can complain that it is {_complaint} and the anchor has no "
             f"message for it, so a player is told the generic answer and looks in the "
             f"wrong place")

# ---------------------------------------------------------------------------
# The flare
# ---------------------------------------------------------------------------
#
# A space elevator is a column somebody had to hold up, and the legs are what
# make it read as that. Three things have to agree about the curve they follow:
# the generator that draws the blueprint, the reading that decides whether a
# port is braced, and the rules that both of them are supposed to be using. Two
# of those agreeing is the bug.

_flare = strip_comments(read(SRC, "port/ElevatorBase.java"))
_gen_port = read(WORK, "tools/gen_spaceport.py")

if "PortRules.legRadius(" not in _flare and "PortRules.legX(" not in _flare:
    fail("the brace is read without asking the rules where a leg should be, so the "
         "shape the blueprint draws and the shape the game looks for can differ")
if "PortRules.legSamples()" not in _flare:
    fail("the brace is decided at heights the handler picked, not the ones the rules "
         "state and the harness checks")
if "hasChunkAt(" not in _flare:
    fail("reading the flare loads chunks around a port every time it is asked")
if "ELEVATOR_PYLON" not in _flare:
    fail("a brace can be made of anything, so four dirt blocks are a spaceport")

if "PortRules.java" not in _gen_port or "def rule(" not in _gen_port:
    fail("tools/gen_spaceport.py keeps its own copy of the leg curve instead of "
         "reading the rules, so the blueprint and the game can disagree about where "
         "a leg is")
for _copied in ("FOOT = 12", "WAIST = 3", "GATHER = 24", "BEND = 1.7"):
    if _copied in _gen_port:
        fail(f"tools/gen_spaceport.py hard-codes {_copied}; that number lives in "
             f"PortRules, where a harness executes it")

_reading = body_of(_elevator, "public static Reading read(")
if "ElevatorBase.legs(" not in _reading:
    fail("the elevator is read without looking at what is holding it up, so a braced "
         "port and a bare mast ship the same amount")
if "PortRules.throughput(tether, legs)" not in _elevator:
    fail("a reading's throughput ignores its own legs, so the flare is scenery")
if "PortRules.outbound(tether, legs)" not in _ship \
        or "PortRules.inbound(tether, legs)" not in _ship:
    fail("the day's shipping does not use the braced capacity, so the legs are "
         "counted, stored, reported and never spent")

# The blueprint itself, in every style, read back and measured against the rules.
_styles_here = sorted(name for name in os.listdir(os.path.join(RES, "blueprints", NS))
                      if os.path.isdir(os.path.join(RES, "blueprints", NS, name)))
_ports_found = 0
for _style in _styles_here:
    _path = os.path.join(RES, "blueprints", NS, _style, "infrastructure",
                         "spaceport.blueprint")
    if not os.path.isfile(_path):
        fail(f"the {_style} style has no spaceport blueprint, so a colony on that "
             f"world can build every hut it has and not the one building this module "
             f"added")
        continue
    _ports_found += 1
    try:
        _raw = open(_path, "rb").read()
        _data = __import__("gzip").decompress(_raw) if _raw[:2] == b"\x1f\x8b" else _raw
        _stream = __import__("io").BytesIO(_data)
        _kind = _stream.read(1)[0]
        _nbt_mod = __import__("nbt")
        _nbt_mod._rs(_stream)
        _tree = _nbt_mod._read(_stream, _kind).value
    except Exception as _broken:
        fail(f"the {_style} spaceport blueprint is unreadable: {_broken}")
        continue
    _sx = _tree["size_x"].value
    _sy = _tree["size_y"].value
    _sz = _tree["size_z"].value
    _palette = [_entry.value["Name"].value for _entry in _tree["palette"].value]
    _packed = _tree["blocks"].value
    _flat = []
    for _word in _packed:
        _word &= 0xFFFFFFFF
        _flat.append((_word >> 16) & 0xFFFF)
        _flat.append(_word & 0xFFFF)
    _flat = _flat[:_sx * _sy * _sz]

    def _at(x, y, z, _flat=_flat, _sx=_sx, _sz=_sz, _palette=_palette):
        if not (0 <= x < _sx and 0 <= z < _sz):
            return "out"
        return _palette[_flat[(y * _sz + z) * _sx + x]]

    _mid = _sx // 2
    if _at(_mid, 1, _mid) != f"{NS}:elevator_anchor":
        fail(f"the {_style} spaceport has no anchor at the middle of its deck, so "
             f"what a player raises is a sculpture")
    _column = [y for y in range(2, _sy)
               if _at(_mid, y, _mid) == f"{NS}:elevator_tether"]
    if len(_column) != _sy - 2:
        fail(f"the {_style} spaceport's shaft is broken inside its own blueprint: "
             f"{len(_column)} of {_sy - 2} blocks are tether")
    # And the legs stand on the curve the rules describe.
    _rules_src = read(PURE, "PortRules.java")
    _foot = int(re.search(r"FOOT = (\d+)", _rules_src).group(1))
    _waist = int(re.search(r"WAIST = (\d+)", _rules_src).group(1))
    _gather = int(re.search(r"GATHER = (\d+)", _rules_src).group(1))
    _bend = float(re.search(r"BEND = ([0-9.]+)F", _rules_src).group(1))
    _legs = int(re.search(r"LEGS = (\d+)", _rules_src).group(1))
    _tolerance = int(re.search(r"LEG_TOLERANCE = (\d+)", _rules_src).group(1))
    _samples = [max(1, _gather // 12), _gather // 4, _gather // 2,
                _gather * 3 // 4]
    for _sample in _samples:
        _radius = _waist + (_foot - _waist) * (((_gather - _sample) / _gather)
                                               ** _bend)
        _standing = 0
        for _leg in range(_legs):
            _angle = math.pi / 4.0 + _leg * (2.0 * math.pi / _legs)
            _lx = _mid + int(round(_radius * math.cos(_angle)))
            _lz = _mid + int(round(_radius * math.sin(_angle)))
            _near = False
            for _dx in range(-_tolerance, _tolerance + 1):
                for _dz in range(-_tolerance, _tolerance + 1):
                    if _at(_lx + _dx, _sample, _lz + _dz) == f"{NS}:elevator_pylon":
                        _near = True
            if _near:
                _standing += 1
        if _standing < _legs:
            fail(f"the {_style} spaceport blueprint has {_standing} of {_legs} legs "
                 f"where the rules put them at height {_sample}, so the building a "
                 f"player raises is not the building the game counts as braced")
    _needed = {_t.value for _t in _tree["required_mods"].value}
    for _block in set(_palette):
        _namespace = _block.split(":", 1)[0]
        if _namespace not in ("minecraft",) and _namespace not in _needed:
            fail(f"the {_style} spaceport places {_block} and does not require "
                 f"{_namespace}")
        if _namespace.startswith("elysium") and _namespace != NS:
            fail(f"the {_style} spaceport needs {_namespace}; this module is an "
                 f"add-on and may be the only Elysium jar installed, so its one "
                 f"building has to be raisable without the rest")

print(f"the flare          : {_ports_found} spaceport blueprint(s), legs on the curve "
      f"the rules state and the game looks for")

# The player's half of the port: the one place the market stops being a thing
# you can only read.
_sell = body_of(strip_comments(read(SRC, "command/ColonyCommand.java")),
                "private static int sell(")
if not _sell:
    fail("a player cannot sell anything through a spaceport, so the market is still "
         "a table of numbers nobody can act on")
if "Colonies.owns(" not in _sell or "memberOf(player, here)" not in _sell:
    fail("a player can sell into a settlement they have nothing to do with, so the "
         "command quietly gives cargo away to strangers")
if "berth.working()" not in _sell:
    fail("cargo is sold through a port that is not standing, so the elevator was "
         "never the point")
if "MarketRules.purseFrom(" not in _sell:
    fail("a player selling a crate is paid something other than what the market offers "
         "for it, so the same crate is worth two different numbers depending on who "
         "carried it")
if "PortRules.banked(" not in _sell:
    fail("a player sale ignores the treasury's ceiling, so one good route funds a "
         "colony forever and the port's height stops mattering")
if "setItemInHand(" not in _sell or "ItemStack.EMPTY" not in _sell:
    fail("the cargo is paid for and never taken, which is not a market, it is a "
         "duplication bug with a shopfront")

# The order in the day: the port, then the lifeline.
_reckon4 = body_of(_life, "private static void reckon")
if "Spaceport.run(level, town," not in _reckon4:
    fail("the spaceport never runs, so it is scenery")
if 0 <= _reckon4.find("FrontierExchange.settle(") < _reckon4.find("Spaceport.run("):
    fail("the emergency exchange is tried before the spaceport, so a town that built a "
         "two-hundred-block elevator still sells its furniture at a crisis spread "
         "every morning")
if "ledger.withTreasury(shipped.treasury())" not in _reckon4:
    fail("what the port banked is never written back, so a colony's treasury resets "
         "every day and freight can never be afforded")
if "GrowthRules.banked(ledger.treasury()" in _reckon4 \
        or "GrowthRules.spend(shipped" in _reckon4:
    fail("the treasury and the development bank are the same number, so a colony can "
         "buy its way to a taller building without anybody working")

print(f"the spaceport      : {len(PORT_BLOCKS)} blocks, one unbroken column, and "
      f"nothing leaves a warehouse unpaid for")

lang_path = os.path.join(RES, f"assets/{NS}/lang/en_us.json")
lang = json.load(open(lang_path, encoding="utf-8")) if os.path.isfile(lang_path) else {}
for path in java_files():
    raw = strip_comments(read(path), keep_strings=True)
    for key in re.findall(r'"(elysium\.colony\.[\w.]+)"', raw):
        if key.endswith("."):
            # A key built by appending a value. Which values exist is checked
            # below, against the enum that supplies them, which is stricter
            # than checking the prefix could be completed somehow.
            if not any(written.startswith(key) for written in lang):
                fail(f"{os.path.relpath(path, WORK)} builds keys under {key} and "
                     f"nothing translates any of them")
            continue
        if key not in lang:
            fail(f"{os.path.relpath(path, WORK)} shows {key} and nothing translates it, "
                 f"so a player reads a raw key")
# Every stewardship a player can ask for needs both its confirmation and its
# name, or the one nobody picked in testing reads as a raw key.
for _steward in re.findall(r'([A-Z]+)\("([a-z]+)"\)', read(PURE, "StewardRules.java")):
    for _shape in ("elysium.colony.steward.{}", "elysium.colony.steward.name.{}"):
        _key = _shape.format(_steward[1])
        if _key not in lang:
            fail(f"{_key} has no translation, so choosing that stewardship reads as a "
                 f"raw key")

print(f"what it declares   : the engine and the host, and {len(lang)} translated key(s)")
print()

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all colony checks passed")
