#!/usr/bin/env python3
"""
Validate Elysium Transit against its sources.

Run from anywhere:   python3 elysium-transit/validate.py

A teleport network has exactly one thing that can be catastrophically wrong
and is invisible to whoever wrote it: an **arbitrage**. If going by way of
somewhere is ever cheaper than going direct, every long jump in the pack is
free to the first player who notices, and nothing about the code looks wrong.
The first draft of these rules had one. It was found by execution over a
million random triples, and everything below exists to keep it found.

  1. INDEPENDENCE. Vanilla, the library, and the grid it hangs off.
  2. PURITY, and the harness run rather than read.
  3. THE PRICE. Subadditive, saturating, and payable.
  4. THE JUMP. Decided by the server, every time.
  5. TRANSIT IN THE WORLD.
  6. THE SEAM.
"""
import json
import os
import sys as _sys
_sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "tools"))
import samefile  # noqa: E402
import re
import subprocess
import sys
import tempfile

WORK = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(WORK)
RES = os.path.join(WORK, "src/main/resources")
SRC = os.path.join(WORK, "src/main/java/com/elysium/transit")
PURE = os.path.join(SRC, "rules")
BELOW = [os.path.join(ROOT, "elysium-kinetics/src/main/java/com/elysium/kinetics/rules"),
         os.path.join(ROOT, "elysium-industry/src/main/java/com/elysium/industry/rules")]
HARNESS = os.path.join(WORK, "tools/TransitHarness.java")
HARNESS_MAIN = "com.elysium.transit.rules.TransitHarness"
GENERATOR = os.path.join(WORK, "tools/gen_transit.py")

#: How long the arithmetic is allowed to take. Two million checks run in a few
#: seconds; anything past this is a loop.
HARNESS_SECONDS = 120
NS = "elysiumtransit"

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


def squashed(text):
    """Every run of whitespace as one space, so a rule can name a whole statement.

    Naming the whole statement is the point: a rule that looks for a token
    which survives the very edit it was meant to catch is a rule that has never
    failed, and this project has been bitten by that a dozen times.
    
    Comments go first, and that is not cosmetic. Without it every rule that
    uses this could be defeated by putting // in front of the line it names:
    the statement is still there, character for character, and the rule goes
    on passing. A negative test found that in one validator by commenting out
    one line, and it was true of every validator that had this function.
    """
    return " ".join(strip_comments(text, True).split())


def harness_trouble(output):
    """The lines of a harness run that say what went wrong.

    A failing harness prints its sections as it goes and its failures among
    them, and this used to be handed the first sixteen hundred characters of
    that. Where the failing check was near the end — which it is whenever the
    newest feature is the broken one — the failures fell off the end entirely
    and the validator reported a wall of successful section headings. Two
    negative tests were written against phrases that were never going to be
    printed before anybody noticed.
    """
    lines = [line for line in output.splitlines()
             if line.strip().startswith("FAIL:") or "failure(s)" in line]
    return "\n".join(lines or output.strip().splitlines()[-12:])[:2000]


def body_of(text, signature):
    start = text.find(signature)
    if start < 0:
        return ""
    opened = text.find("{", start)
    if opened < 0:
        return ""
    depth = 0
    for index in range(opened, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[opened:index + 1]
    return text[opened:]


if not os.path.isdir(SRC):
    print("This is not the elysium-transit tree.")
    sys.exit(1)

print("=" * 70)
print("elysium-transit — getting there, and the proof the network cannot be "
      "arbitraged")
print("=" * 70)

# ---------------------------------------------------------------------------
# 1. Independence
# ---------------------------------------------------------------------------

FOREIGN = re.compile(
    r"(?:net\.blay09|com\.dannyandson|opekope2|de\.maxhenkel)"
    r"|\b(?:waystones|automobility|create|travelersbackpack)\s*:", re.I)
UPWARD = re.compile(r"com\.elysium\.(arsenal|bestiary|atelier|wilds|arcana|colonies)")

java_files = []
for dirpath, _, filenames in os.walk(SRC):
    for name in filenames:
        if name.endswith(".java"):
            java_files.append(os.path.join(dirpath, name))

for path in java_files:
    src = strip_comments(read(path))
    where = os.path.relpath(path, SRC)
    for hit in FOREIGN.findall(src):
        fail(f"{where} names {hit}; this module is a reimplementation of the mods it "
             f"replaces and must not reach for any of them")
    if UPWARD.search(src):
        fail(f"{where} reaches up into a later module; the dependencies run one way")

toml = read(RES, "META-INF/neoforge.mods.toml")
declared = set(re.findall(r'modId="([^"]+)"', toml))
allowed = {"${mod_id}", "neoforge", "minecraft", "elysiumlib", "elysiumindustry"}
for mod in declared - allowed:
    fail(f"the module declares a dependency on {mod}; it needs the game, the library "
         f"and the grid a waystone hangs off")
for needed in ("neoforge", "minecraft", "elysiumlib", "elysiumindustry"):
    if needed not in declared:
        fail(f"the module does not declare {needed}")

print(f"independence       : {len(java_files)} source file(s), nothing it replaces "
      f"named, and the grid it hangs off declared")

# ---------------------------------------------------------------------------
# 2. Purity, and the harness executed
# ---------------------------------------------------------------------------

for name in sorted(os.listdir(PURE)) if os.path.isdir(PURE) else []:
    if not name.endswith(".java"):
        continue
    body = read(PURE, name)
    without_package = re.sub(r"^\s*package [^;]+;", "", body, count=1, flags=re.M)
    for forbidden in ("net.minecraft", "net.neoforged", "com.mojang"):
        if forbidden in without_package:
            fail(f"rules/{name} reaches for {forbidden}; the rules are the JDK and the "
                 f"grid below, so a harness can price a million jumps without a game")
    for used in re.findall(r"import\s+(com\.elysium\.[\w.]+);", without_package):
        if not used.startswith("com.elysium.industry.rules."):
            fail(f"rules/{name} imports {used}; a rules class may reach only into other "
                 f"rules, which are pure like itself")

if not os.path.isfile(HARNESS):
    fail("there is no harness, so the one property this module rests on is checked by "
         "reading, which provably does not find it")
else:
    with tempfile.TemporaryDirectory() as tmp:
        staged = os.path.join(tmp, "src")
        os.makedirs(staged)
        sources = [os.path.join(PURE, n) for n in os.listdir(PURE) if n.endswith(".java")]
        for folder in BELOW:
            sources += [os.path.join(folder, n) for n in os.listdir(folder)
                        if n.endswith(".java")]
        for source in sources + [HARNESS]:
            open(os.path.join(staged, os.path.basename(source)), "w",
                 encoding="utf-8").write(read(source))
        compiled = subprocess.run(
            ["javac", "-nowarn", "-d", os.path.join(tmp, "out")]
            + [os.path.join(staged, n) for n in os.listdir(staged)],
            capture_output=True, text=True)
        if compiled.returncode != 0:
            fail("the rules do not compile against the JDK and the grid below alone:\n"
                 + compiled.stderr.strip()[:1200])
        else:
            try:
                ran = subprocess.run(
                    ["java", "-cp", os.path.join(tmp, "out"), HARNESS_MAIN],
                    capture_output=True, text=True, timeout=HARNESS_SECONDS)
            except subprocess.TimeoutExpired:
                ran = None
                fail(f"the harness did not finish within {HARNESS_SECONDS} seconds, so "
                     f"something in the rules can loop forever — which is a server that "
                     f"stops responding, not a wrong answer")
            if ran is None:
                pass
            elif ran.returncode != 0:
                fail("the transit harness failed:\n" + harness_trouble(ran.stdout))
            else:
                print("the arithmetic     : " + ran.stdout.strip().splitlines()[-1])

rules = read(PURE, "TransitRules.java")
plain = strip_comments(rules)
squished = squashed(plain)


def constant(name, kind=r"(?:float|int|double)"):
    m = re.search(r"public static final " + kind + r"\s+" + name + r"\s*=\s*([^;]+);",
                  plain, re.S)
    return " ".join(m.group(1).split()) if m else None


# ---------------------------------------------------------------------------
# 3. The price
# ---------------------------------------------------------------------------
#
# Three properties, and every one of them was a bug first.

per_jump = constant("PER_JUMP")
if per_jump is None or not re.match(r"^[1-9][\d_]*(\.\d+)?F?$", per_jump):
    fail(f"the fixed price of a jump reads {per_jump}, which is not a positive number; "
         f"without one, a chain of short hops ties with the long jump it replaces and "
         f"the network is free to anybody patient")

if "PER_BLOCK" in plain:
    fail("distance is still priced per block, which climbs without limit; a jump inside "
         "one world then costs more than leaving it and coming back, and any distance "
         "at all becomes a flat fee by way of another world")

shape = squashed(body_of(plain, "public static float forDistance("))
if "MOST_FOR_DISTANCE * far / (far + HALF_PRICE_AT)" not in shape:
    fail("the price of distance is not the saturating shape the subadditivity proof "
         "depends on; a price that is not concave can be arbitraged, and this is the "
         "one line that makes it concave")
if "double far = Math.max(0.0, blocks);" not in shape:
    fail("a negative distance is priced rather than floored, so a waypoint with a "
         "coordinate the wrong way round is paid for in reverse")

costing = squashed(body_of(plain, "public static float cost("))
if "if (!from.sameWorldAs(to)) { return PER_JUMP + BETWEEN_WORLDS; }" not in costing:
    fail("crossing between worlds is not a flat surcharge, so two worlds' coordinates "
         "are being subtracted from one another and the price means nothing")
if "return PER_JUMP + forDistance(distance(from, to));" not in costing:
    fail("a jump inside one world is not the fixed price plus the price of distance, so "
         "the shape the proof rests on is not the shape that is charged")

# The numbers themselves have to leave the flat surcharge dearer than any jump
# inside a world, or bouncing off another world is a cheaper way to cross this
# one — which is the exact bug the harness found.
numbers = {}
for name in ("PER_JUMP", "MOST_FOR_DISTANCE", "BETWEEN_WORLDS", "HALF_PRICE_AT",
             "TRICKLE"):
    raw = constant(name)
    try:
        numbers[name] = float((raw or "").replace("_", "").rstrip("Ff"))
    except ValueError:
        fail(f"{name} is not a number this file can read")
buffer_size = constant("BUFFER", "int")
try:
    numbers["BUFFER"] = float((buffer_size or "").replace("_", ""))
except ValueError:
    fail("BUFFER is not a number this file can read")

if len(numbers) == 6:
    most_in_a_world = numbers["PER_JUMP"] + numbers["MOST_FOR_DISTANCE"]
    bounce = 2.0 * (numbers["PER_JUMP"] + numbers["BETWEEN_WORLDS"])
    if bounce < most_in_a_world:
        fail(f"leaving the world and coming back costs {bounce:.0f} and the dearest "
             f"jump inside it costs {most_in_a_world:.0f}, so any distance at all can "
             f"be crossed for a flat fee by way of somewhere else — which is the "
             f"arbitrage this whole module is arranged to prevent")
    if numbers["BUFFER"] < max(most_in_a_world,
                               numbers["PER_JUMP"] + numbers["BETWEEN_WORLDS"]):
        fail("a waystone cannot hold enough for the dearest jump in the network, so "
             "there are places in it nobody can ever reach")
    if numbers["TRICKLE"] <= 0.0:
        fail("an unwired waystone never fills at all, so the early game has no short "
             "way home and the module starts at Age II")
    else:
        # Guarded rather than divided blindly: the rule above is the one that
        # reports a trickle of nothing, and a validator that dies of a
        # zero-divide on its way to saying so reports nothing at all. Which is
        # exactly what it did the first time it was tested.
        patience = (numbers["PER_JUMP"] + numbers["MOST_FOR_DISTANCE"]
                    * 256.0 / (256.0 + numbers["HALF_PRICE_AT"])) / numbers["TRICKLE"]
        if patience > 20 * 60 * 5:
            fail(f"an unwired waystone takes {patience / 20:.0f} seconds to afford the "
                 f"short way home, which is a chore rather than an incentive to wire it")

filling = squashed(body_of(plain, "public static float fillRate("))
if "return Math.max(TRICKLE, WAYSTONE.draw() * Math.min(1.0F, share));" not in filling:
    fail("the trickle is an alternative to the grid rather than a floor under it, so a "
         "waystone on a browned-out grid fills slower than one with no power at all — "
         "wiring something made it worse")

reaching = squashed(body_of(plain, "public static double reach("))
if "if (forDistance >= MOST_FOR_DISTANCE) { return Double.POSITIVE_INFINITY; }" \
        not in reaching:
    fail("what a waystone reaches is worked out by dividing by something that can be "
         "zero, so a full stone reports an impossible number rather than 'anywhere'")

ready = squashed(body_of(plain, "public static int readyIn("))
if "if (held >= want) { return 0; }" not in ready:
    fail("a waystone that can already pay still counts down, so the readout is of "
         "something other than what the player is waiting for")

machine = squashed(body_of(plain, "public static final IndustryRules.Machine WAYSTONE"))
if "share" in filling.split("return")[0] and "Math.min(1.0F, share)" not in filling:
    fail("the fill rate does not clamp the share, so a grid with more than it needs "
         "fills a waystone faster than full")

print(f"the price          : a fixed {per_jump} a jump, a price for distance that "
      f"saturates rather than climbing, and a flat surcharge dearer than any jump "
      f"inside one world")

# ---------------------------------------------------------------------------
# 4. The jump, decided by the server
# ---------------------------------------------------------------------------

travel = strip_comments(read(SRC, "travel/WaystoneTravel.java"))
jumping = squashed(body_of(travel, "public static Component jump("))
for statement, why in (
        ("if (player.blockPosition().distSqr(from) > WITHIN * WITHIN) {",
         "a packet can jump a player from a waystone they are nowhere near, which is a "
         "waystone anybody can use from anywhere"),
        ("TransitRules.Waypoint there = remembered(player, key);",
         "the destination comes out of the packet rather than out of what this player "
         "has bound, so a crafted packet reaches anywhere in the world"),
        ("if (!waystone.spend(price)) {",
         "the jump is not paid for, so the whole price is decoration")):
    if statement not in jumping:
        fail("the jump: " + why)
if "TransitBlocks.WAYSTONE.get()" not in jumping:
    fail("the block a player is jumping from is never checked to be a waystone")

remembering = squashed(body_of(travel, "private static TransitRules.Waypoint remembered("))
if "TransitAttachments.known(player)" not in remembering:
    fail("which places a player may jump to is not read off that player, so the "
         "destination list is not per player and the network is a map handed to "
         "somebody who has not explored")

if "TransitAttachments.forget(player, there);" not in jumping:
    fail("a waystone that has been mined stays in the list forever, so a player is "
         "offered a place that will refuse them every time they open the menu")

payloads = squashed(strip_comments(read(SRC, "network/TransitPayloads.java")))
if "record ChooseWaystone(BlockPos from, String key)" not in payloads:
    fail("what comes back from the menu is not just which row; a packet carrying a "
         "price or a name is a packet that can lie about one")
for forbidden in ("float price", "int price", "float cost"):
    if forbidden in payloads:
        fail(f"the packet from the client carries {forbidden}, so the client is being "
             f"trusted with the one number the whole module is about")

attachments = strip_comments(read(SRC, "registry/TransitAttachments.java"))
attach_squashed = squashed(attachments)
for statement, why in (
        (".copyOnDeath()",
         "every waystone a player has bound is lost when they die, which is a "
         "punishment aimed at the thing this module exists to remove"),
        (".sync(",
         "the list never reaches the client, so the menu is empty however far a player "
         "has walked"),
        (".serialize(Codec.STRING.listOf())",
         "the list is not written to disk, so the network empties on restart")):
    if statement not in attach_squashed:
        fail("what a player remembers: " + why)

learning = squashed(body_of(strip_comments(attachments), "public static boolean learn("))
if "player.setData(KNOWN.get(), List.copyOf(now));" not in learning:
    fail("binding a waystone mutates the list in place rather than replacing it, so "
         "the sync never hears about it and the client's menu silently stops growing")

print("the jump           : the distance, the block, the player's own list and the "
      "price all re-decided on the server, and the packet carries only which row")

# ---------------------------------------------------------------------------
# 5. Transit in the world
# ---------------------------------------------------------------------------

if not os.path.isfile(GENERATOR):
    fail("there is no generator, so every asset in the module is kept by hand")
else:
    with tempfile.TemporaryDirectory() as tmp:
        environment = dict(os.environ, ELYSIUM_TRANSIT_OUT=tmp)
        made = subprocess.run([sys.executable, GENERATOR], capture_output=True,
                              text=True, env=environment, cwd=WORK)
        if made.returncode != 0:
            fail("the generator will not run, so nothing here can be rebuilt:\n"
                 + (made.stdout + made.stderr).strip()[:800])
        else:
            fresh = os.path.join(tmp, "src/main/resources")
            drifted = []
            for dirpath, _, filenames in os.walk(fresh):
                for name in filenames:
                    made_path = os.path.join(dirpath, name)
                    kept = os.path.join(RES, os.path.relpath(made_path, fresh))
                    if not os.path.isfile(kept):
                        drifted.append(os.path.relpath(made_path, fresh) + " (missing)")
                    elif not samefile.same(made_path, kept):
                        drifted.append(os.path.relpath(made_path, fresh))
            if drifted:
                fail(f"{len(drifted)} generated file(s) differ from what the generator "
                     f"writes, so they were edited by hand and the next run will undo "
                     f"them: " + ", ".join(sorted(drifted)[:6]))

generator = read(GENERATOR)
if "random" in generator.lower() and "fixed hash rather than random" not in generator:
    fail("a sprite is drawn with randomness, so the generated-file check fails for "
         "everybody except whoever last ran it")

lang = {}
lang_path = os.path.join(RES, "assets", NS, "lang/en_us.json")
if os.path.isfile(lang_path):
    lang = json.load(open(lang_path, encoding="utf-8"))
else:
    fail("there is no language file, so every block is called by its id")

blocks = sorted(re.findall(r'BLOCKS\.register\("([a-z_]+)"',
                           strip_comments(read(SRC, "registry/TransitBlocks.java"), True)))
items = strip_comments(read(SRC, "registry/TransitItems.java"), True)
in_tab = re.search(r"ORDER\s*=\s*List\.of\(([^;]*)\);", items)
in_tab = in_tab.group(1) if in_tab else ""

for name in blocks:
    if f'registerSimpleBlockItem(TransitBlocks.{name.upper()})' not in squashed(items):
        fail(f"{name} has no item, so it exists only to a command")
    if name.upper() not in in_tab:
        fail(f"{name} is in no creative tab, so nobody in a creative world can find it")
    for kind, where in (("a model", f"assets/{NS}/models/block/{name}.json"),
                        ("an item model", f"assets/{NS}/models/item/{name}.json"),
                        ("a blockstate", f"assets/{NS}/blockstates/{name}.json"),
                        ("a texture", f"assets/{NS}/textures/block/{name}.png")):
        if not os.path.isfile(os.path.join(RES, where)):
            fail(f"{name} has no {kind}, so it is a purple cube")
    if not os.path.isfile(os.path.join(RES, "data", NS, "loot_table/blocks",
                                       name + ".json")):
        fail(f"{name} has no drop, so mining one destroys it")
    if not os.path.isfile(os.path.join(RES, "data", NS, "recipe", name + ".json")):
        fail(f"{name} has no recipe, so it is a creative-only block")
    if f"block.{NS}.{name}" not in lang:
        fail(f"{name} has no name in the language file")

mineable = read(RES, "data/minecraft/tags/block/mineable/pickaxe.json") \
    + read(RES, "data/minecraft/tags/block/mineable/shovel.json")
for name in blocks:
    if f"{NS}:{name}" not in mineable:
        fail(f"{name} is in no mineable tag, so it takes a fist and drops nothing "
             f"quickly")

# The waystone's own recipe has to be buildable by somebody who has a grid and
# nothing else: a waystone gated behind a later age is a waystone nobody has
# when they most want one.
recipe = read(RES, "data", NS, "recipe/waystone.json")
for later in ("elysiumatomics", "elysiumfoundry", "elysiumlogistics"):
    if later in recipe:
        fail(f"the waystone is made out of {later}, so the short way home arrives "
             f"several ages after the walking does")
if "elysiumindustry" not in recipe:
    fail("the waystone costs nothing from the age whose grid it runs on, so nothing "
         "about its recipe says it wants power")

# ---------------------------------------------------------------------------
# 5b. The vehicles
# ---------------------------------------------------------------------------
#
# Three rungs of one class. Everything here is about the two ways that goes
# wrong: a rung with no numbers behind it, and a vehicle that bills fuel for a
# journey it did not make.

entity = strip_comments(read(SRC, "entity/VehicleEntity.java"))
entity_squashed = squashed(entity)
entities = strip_comments(read(SRC, "registry/TransitEntities.java"), True)

rungs = re.findall(r'public static final Vehicle (\w+) = new Vehicle\("([a-z_]+)"',
                   strip_comments(rules, keep_strings=True))
if len(rungs) < 3:
    fail("there are fewer than three vehicles in the rules, so the ladder the roads "
         "exist for has no rungs on it")
for _, name in rungs:
    if f'vehicle(TransitRules.{name.upper()}' not in squashed(entities):
        fail(f"{name} is in the rules and is not registered as an entity, so it is a "
             f"row of numbers nobody can ride")
    if f'ITEMS.register("{name}"' not in squashed(strip_comments(items, True)):
        fail(f"{name} has no item, so it can only be summoned by a command")
    for kind, where in (("a model", f"assets/{NS}/models/item/{name}.json"),
                        ("an item sprite", f"assets/{NS}/textures/item/{name}.png"),
                        ("a skin", f"assets/{NS}/textures/entity/{name}.png")):
        if not os.path.isfile(os.path.join(RES, where)):
            fail(f"{name} has no {kind}")
    if not os.path.isfile(os.path.join(RES, "data", NS, "recipe", name + ".json")):
        fail(f"{name} has no recipe, so it is a creative-only vehicle")
    for key in (f"item.{NS}.{name}", f"entity.{NS}.{name}"):
        if key not in lang:
            fail(f"{name} has no {key} in the language file")

rung_lookup = squashed(body_of(strip_comments(entities),
                               "public static TransitRules.Vehicle rungOf("))
if "EntityType.getKey(type).getPath();" not in rung_lookup:
    fail("which rung an entity is does not come from its own registered name, so a "
         "type and its numbers can drift apart and a vehicle can be registered with no "
         "numbers behind it")

driving = squashed(body_of(entity, "private void drive("))
for statement, why in (
        ("target = TransitRules.speedOn(rung(), surface(), load());",
         "how fast a vehicle goes is not the rules' answer, so the road, the rung and "
         "the load are decided twice and the proofs cover neither"),
        ("float now = TransitRules.approach(was, target);",
         "a vehicle reaches its speed instantly, so it handles like a cursor and the "
         "acceleration in the rules is decoration"),
        ("float burnt = (float) (went * TransitRules.thirstOn(rung(), surface(), load()));",
         "fuel is billed on something other than the distance actually covered")):
    if statement not in driving:
        fail("driving: " + why)
if "if (went > 0.0D && driver != null) {" not in driving:
    fail("a vehicle held against a wall goes on burning fuel, which is the one way "
         "this could quietly empty somebody's tank while they watched it not move")
if "if (level().isClientSide) {" not in squashed(body_of(entity, "public void tick()")):
    fail("driving is decided on the client as well as the server, so two answers to "
         "one question and whichever arrives last wins")

fuelling = squashed(body_of(entity, "public InteractionResult interact("))
if "setFuel(TransitRules.fuelled(fuel(), TransitRules.fuelFrom(burns)));" not in fuelling:
    fail("refuelling does not go through the rules, so a tank can be filled past what "
         "it holds and the fuel over the top is destroyed")
if "if (burns > 0 && fuel() < TransitRules.TANK) {" not in fuelling:
    fail("a full vehicle accepts fuel anyway, so feeding one coal too many destroys it")

hurting = squashed(body_of(entity, "public boolean hurt("))
if "spill();" not in hurting:
    fail("breaking a vehicle destroys everything in it, which is the one place in this "
         "module where a player's items would vanish without them asking")

surfacing = squashed(body_of(entity, "public TransitRules.Surface surface("))
for named in ("TransitBlocks.PAVING", "TransitBlocks.GRAVEL_PATH"):
    if named not in surfacing:
        fail(f"{named} is laid on purpose and is not read as a surface, so laying it "
             f"does nothing at all")
if "return TransitRules.Surface.GROUND;" not in surfacing:
    fail("there is no default surface, so a block nobody listed drives like nothing")

renderer = strip_comments(read(SRC, "client/VehicleRenderer.java"))
if "EntityType.getKey(vehicle.getType()).getPath()" not in squashed(renderer):
    fail("every rung is drawn with one texture, so a hauler and a cart are the same "
         "thing across a field")

print(f"the vehicles       : {len(rungs)} rung(s) of one class, each registered, "
      f"modelled, painted, craftable and named; fuel billed on distance covered")

screen = strip_comments(read(SRC, "client/WaystoneScreen.java"))
if "extends ElysiumChooser" not in screen:
    fail("the menu does not use the library's chooser, so four ways of crossing a "
         "distance look like four different mods")
if "ElysiumPalette." not in screen:
    fail("the menu writes its own colours rather than taking the pack's")

print(f"transit in the world: {len(blocks)} block(s), each modelled, dropped, named and "
      f"craftable; a waystone anybody with a wire can build")

# ---------------------------------------------------------------------------
# 6. The seam
# ---------------------------------------------------------------------------

main_class = read(SRC, "ElysiumTransit.java")
if f'MODID = "{NS}"' not in main_class:
    fail("the module id is not what everything else in the tree spells it")
for registered in ("TransitAttachments.ATTACHMENTS.register", "TransitBlocks.BLOCKS.register",
                   "TransitBlockEntities.TYPES.register", "TransitEntities.TYPES.register",
                   "TransitItems.ITEMS.register"):
    if registered not in main_class:
        fail(f"{registered} is never called, so part of the module is written and none "
             f"of it exists")

print("what it declares   : the game, the library, and the grid a waystone hangs off")

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all transit checks passed")
