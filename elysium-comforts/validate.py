#!/usr/bin/env python3
"""
Validate Elysium Comforts against its sources.

Run from anywhere:   python3 elysium-comforts/validate.py

The module's one claim is that **nothing a player owned is ever lost**, and
that is a counting problem rather than a sentiment. Every gravestone mod that
has ever eaten somebody's backpack did it the same way: by deciding in advance
how many slots a grave has. It is never reported as a bug, because the player
assumes they dropped it.

  1. INDEPENDENCE. The library, and nothing it buries.
  2. PURITY, and the harness run rather than read.
  3. THE COUNTING. A grave built around what it is handed.
  4. WHERE IT GOES, and who may open it.
  5. THE THREE WAYS OUT, all of which have to work.
  6. COMFORTS IN THE WORLD.
  7. THE SEAM.
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
SRC = os.path.join(WORK, "src/main/java/com/elysium/comforts")
PURE = os.path.join(SRC, "rules")
BELOW = []
HARNESS = os.path.join(WORK, "tools/ComfortHarness.java")
HARNESS_MAIN = "com.elysium.comforts.rules.ComfortHarness"
GENERATOR = os.path.join(WORK, "tools/gen_comforts.py")

#: How long the arithmetic is allowed to take. Two million checks run in a few
#: seconds; anything past this is a loop.
HARNESS_SECONDS = 120
NS = "elysiumcomforts"

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
    newest feature is the broken one — the failures fell off the end and the
    validator reported a wall of successful headings instead.
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
    print("This is not the elysium-comforts tree.")
    sys.exit(1)

print("=" * 70)
print("elysium-comforts — the small mercies, and the proof that nothing you owned is "
      "ever lost")
print("=" * 70)

# ---------------------------------------------------------------------------
# 1. Independence
# ---------------------------------------------------------------------------

FOREIGN = re.compile(
    r"(?:de\.maxhenkel|com\.stereowalker|noobanidus|lootr|gravestone)"
    r"|\b(?:comforts|gravestone|lootr|easy_villagers|polymorph)\s*:", re.I)
UPWARD = re.compile(r"com\.elysium\.(arsenal|bestiary|atelier|wilds|colonies)")

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
    for used in set(re.findall(r"com\.elysium\.(\w+)", src)):
        if used not in ("comforts", "lib"):
            fail(f"{where} names com.elysium.{used}; a grave holds whatever a player "
                 f"was carrying, whoever made it, so this module must never have to "
                 f"know which mods added the items in it")

toml = read(RES, "META-INF/neoforge.mods.toml")
declared = set(re.findall(r'modId="([^"]+)"', toml))
allowed = {"${mod_id}", "neoforge", "minecraft", "elysiumlib"}
for mod in declared - allowed:
    fail(f"the module declares a dependency on {mod}; it buries whatever is installed "
         f"and must not require any of it")
for needed in ("neoforge", "minecraft", "elysiumlib"):
    if needed not in declared:
        fail(f"the module does not declare {needed}")

print(f"independence       : {len(java_files)} source file(s), nothing it replaces "
      f"named, and not one of the modules whose items it buries")

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
            fail(f"rules/{name} reaches for {forbidden}; the rules are the JDK alone, "
                 f"so a harness can bury a million inventories without a game")
    for used in re.findall(r"import\s+(com\.elysium\.[\w.]+);", without_package):
        fail(f"rules/{name} imports {used}; the rules are the JDK alone")

TRADER_HARNESS = os.path.join(WORK, "tools/TraderHarness.java")
TRADER_MAIN = "com.elysium.comforts.rules.TraderHarness"

for where in (HARNESS, TRADER_HARNESS):
    if not os.path.isfile(where):
        fail(f"{os.path.basename(where)} is gone, so what it proved is checked by "
             f"reading, and both of the things it proves are counting problems")

if os.path.isfile(HARNESS) and os.path.isfile(TRADER_HARNESS):
    with tempfile.TemporaryDirectory() as tmp:
        staged = os.path.join(tmp, "src")
        os.makedirs(staged)
        sources = [os.path.join(PURE, n) for n in os.listdir(PURE) if n.endswith(".java")]
        for source in sources + [HARNESS, TRADER_HARNESS]:
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
            for main, label in ((HARNESS_MAIN, "the arithmetic     "),
                                (TRADER_MAIN, "the ledger         ")):
                try:
                    ran = subprocess.run(
                        ["java", "-cp", os.path.join(tmp, "out"), main],
                        capture_output=True, text=True, timeout=HARNESS_SECONDS)
                except subprocess.TimeoutExpired:
                    fail(f"{main} did not finish within {HARNESS_SECONDS} seconds, so "
                         f"something in the rules can loop forever — which is a server "
                         f"that stops responding, not a wrong number")
                    continue
                if ran.returncode != 0:
                    fail(f"{main} failed:\n" + harness_trouble(ran.stdout))
                    continue
                print(label + ": " + ran.stdout.strip().splitlines()[-1])

rules = read(PURE, "ComfortRules.java")
plain = strip_comments(rules)
squished = squashed(plain)


def constant(name, kind=r"(?:float|int|double)"):
    m = re.search(r"public static final " + kind + r"\s+" + name + r"\s*=\s*([^;]+);",
                  plain, re.S)
    return " ".join(m.group(1).split()) if m else None






# ---------------------------------------------------------------------------
# 3. The counting
# ---------------------------------------------------------------------------
#
# The whole module. A grave is built around what it is handed; every gravestone
# mod that has eaten a backpack was built to a number decided in advance.

sizing = squashed(body_of(plain, "public static int slotsFor("))
if "Math.min(MOST_SLOTS, Math.max(0, stacks))" not in sizing:
    fail("a grave is not sized to the stacks it was handed, so an inventory with "
         "anything unusual in it loses the remainder — which is how every gravestone "
         "mod that ever ate somebody's backpack did it")

spilling = squashed(body_of(plain, "public static int spilled("))
if "return Math.max(0, stacks - slotsFor(stacks));" not in spilling:
    fail("what would not fit is not worked out against the size a grave actually gets, "
         "so the one number that proves nothing is lost is measuring something else")

cap = constant("MOST_SLOTS", "int")
try:
    cap_value = int((cap or "0").replace("_", ""))
except ValueError:
    cap_value = 0
    fail("the slot cap is not a number this file can read")
if cap_value <= 41 * 4:
    fail(f"a grave holds at most {cap_value}, which is smaller than an inventory with "
         f"backpacks and curios in it; being wrong upwards costs a few empty slots and "
         f"being wrong downwards costs somebody their evening")

filling = squashed(body_of(strip_comments(read(SRC, "block/GraveBlock.java")),
                           "public void fill("))
if "int room = ComfortRules.slotsFor(carried.size());" not in filling:
    fail("the grave in the world is not sized by the rule the harness proves, so "
         "fourteen million checks are about a different grave from the one that ships")
if "held.add(carried.get(at).copy());" not in filling:
    fail("a grave keeps the player's own stack objects rather than copies of them, so "
         "anything that clears the drop list afterwards empties the grave too")

print(f"the counting       : a grave sized to what it is handed, up to {cap_value}, and "
      f"nothing ever spilled")

# ---------------------------------------------------------------------------
# 4. Where it goes, and who may open it
# ---------------------------------------------------------------------------

height = squashed(body_of(plain, "public static int graveHeight("))
if "int atLeast = Math.min(lowest + 1, highest);" not in height:
    fail("a grave may sit on the floor of the world, which is bedrock, so it is a grave "
         "nobody can break and nothing can drop out of")
if "return Math.max(atLeast, Math.min(highest, died));" not in height:
    fail("a grave is not clamped into the world; falling out of the bottom of it is the "
         "commonest way to die with a full inventory, and the grave would be in the void")

opening = squashed(body_of(plain, "public static boolean mayOpen("))
if "return owner || operator || age >= PRIVATE_FOR;" not in opening:
    fail("who may open a grave is not the owner, an operator, or anybody once it is old "
         "— and every other answer is either losing everything to whoever was standing "
         "nearby or an obstacle nobody can ever clear")

private_for = constant("PRIVATE_FOR", "int")
if private_for is None or "20 * 60" not in private_for:
    fail("how long a grave is private is not written in ticks a reader can convert in "
         "their head")

counting_down = squashed(body_of(plain, "public static long publicIn("))
if "return Math.max(0L, PRIVATE_FOR - Math.max(0L, age));" not in counting_down:
    fail("the countdown can go negative or disagree with the lock beside it")

kept = squashed(body_of(plain, "public static int experienceKept("))
if "return (int) Math.floor(had * XP_KEPT);" not in kept:
    fail("experience is not rounded down, so dying is the one operation in this pack "
         "that rounds in the player's favour")
share = constant("XP_KEPT")
try:
    share_value = float((share or "1").rstrip("Ff"))
except ValueError:
    share_value = 1.0
    fail("the share of experience kept is not a number this file can read")
if share_value >= 1.0:
    fail("a grave keeps all your experience, so dying costs nothing at all and the "
         "whole of the game's risk goes with it")
if share_value <= 0.25:
    fail(f"a grave keeps {share_value} of your experience, which is a punishment rather "
         f"than a mercy")

keeper = strip_comments(read(SRC, "event/GraveKeeper.java"))
resting = squashed(body_of(keeper, "public static BlockPos restingPlace("))
# Both of these name the whole statement rather than the call inside it. A rule
# that looks for "ComfortRules.graveHeight(" goes on passing when somebody
# writes `int y = died.getY(); if (false) y = ComfortRules.graveHeight(...)` —
# which is exactly what its own negative test did the first time it ran.
if ("int y = ComfortRules.graveHeight(died.getY(), level.getMinBuildHeight(), "
        "level.getMaxBuildHeight() - 1);") not in resting:
    fail("where a grave goes in the world is not the height the rules clamp to, so the "
         "proof that it is never in the void is about a different function")
if ("for (Direction face : Direction.values()) { BlockPos next = start.relative(face, "
        "reach); if (level.isInWorldBounds(next) && replaceable(level, next)) { return "
        "next; } }") not in resting:
    fail("a grave does not look for somewhere it can sit, so dying inside a wall puts "
         "one inside it")

print(f"where and whose    : clamped into the world, never in bedrock, yours for "
      f"{int(private_for.split('*')[-1]) if private_for else '?'} minutes, and "
      f"{round(share_value * 100)}% of your levels kept")

# ---------------------------------------------------------------------------
# 5. The three ways out
# ---------------------------------------------------------------------------
#
# Opened, broken, or shoved by a piston. All three have to end with the player
# able to get their things; the third by refusing to happen at all.

grave = strip_comments(read(SRC, "block/GraveBlock.java"))
grave_squashed = squashed(grave)

using = squashed(body_of(grave, "protected InteractionResult useWithoutItem("))
if "if (!ComfortRules.mayOpen(owner, operator, age)) {" not in using:
    fail("who may open a grave is decided in the block rather than in the rule the "
         "harness proves, so the two can disagree")
if "grave.giveBackTo(player);" not in using:
    fail("opening a grave does not give anything back")
if using.find("grave.giveBackTo(player);") > using.find("level.removeBlock(pos, false);"):
    fail("the grave is removed before its contents are handed over, so anything that "
         "fails in between loses the lot")

removing = squashed(body_of(grave, "protected void onRemove("))
if "Containers.dropContents(level, pos, grave.spill());" not in removing:
    fail("breaking a grave destroys what is in it, which is the one thing this module "
         "exists to prevent")
if "grave.scatterExperience(level, pos);" not in removing:
    fail("breaking a grave destroys the experience in it")

if "public PushReaction getPistonPushReaction(BlockState state) { return PushReaction.BLOCK; }" \
        not in grave_squashed:
    fail("a piston can move a grave, and a block pushed into a wall is destroyed along "
         "with everything inside it")

giving = squashed(body_of(grave, "public void giveBackTo("))
if "if (!player.getInventory().add(stack)) { player.drop(stack, false); }" not in giving:
    fail("a stack that will not fit in the player's inventory is silently dropped from "
         "the grave rather than onto the floor, so a full inventory loses the rest")
if "held.clear();" not in giving:
    fail("the grave keeps what it just handed over, so opening it twice is a "
         "duplication bug")
if giving.find("held.clear();") < giving.find("player.getInventory().add(stack)"):
    fail("the grave is emptied before it hands anything over")

loot = read(RES, "data", NS, "loot_table/blocks/grave.json")
if loot and json.loads(loot).get("pools"):
    fail("a grave drops itself when broken, so a player can stockpile empty graves — "
         "and an empty grave in a chest looks exactly like somebody's belongings")

print("the three ways out : opened, broken, or refusing to be pushed; and the grave is "
      "emptied only after its contents are somewhere else")

# ---------------------------------------------------------------------------
# 5b. Catching the drops
# ---------------------------------------------------------------------------

dropping = squashed(body_of(keeper, "public static void onDrops("))
if "for (ItemEntity dropped : event.getDrops()) {" not in dropping:
    fail("the grave is filled from the player's inventory rather than from what would "
         "have dropped, so every mod that adds a slot this module has not heard of is "
         "quietly lost")
if "event.setCanceled(true);" not in dropping:
    fail("the drops are put in a grave and dropped on the floor as well, which is a "
         "duplication bug wearing a mercy's clothes")
if "if (event.getDrops().isEmpty()) {" not in dropping:
    fail("a grave is placed even when there is nothing to bury, so keep-inventory "
         "leaves an empty block in the floor every time anybody dies")
if "grave.fill(player, carried, player.totalExperience, level.getGameTime());" not in dropping:
    fail("the grave is not filled with what was caught, or not stamped with when it was "
         "made, which is what the lock counts from")
if dropping.find("grave.fill(") > dropping.find("event.getDrops().clear();"):
    fail("the drops are cleared before the grave is filled, so a failure in between "
         "loses everything")

experience = squashed(body_of(keeper, "public static void onExperience("))
if "event.setDroppedExperience(0);" not in experience:
    fail("the grave keeps a share of the levels and the rest is dropped as well, which "
         "is paying twice out of one purse")
if "buried.remove(player.getUUID())" not in experience:
    fail("the record of who has just been buried is never cleared, so it grows for as "
         "long as the server runs")

print("catching the drops : whatever would have hit the floor, taken whole, so a mod "
      "this module has never heard of is buried like anything else")

# ---------------------------------------------------------------------------
# 5c. Loot that belongs to whoever opened it
# ---------------------------------------------------------------------------
#
# Three ways this goes wrong and none of them is ever reported as a bug: a
# reroll on reopening, two players sharing a roll, and a table of everyone who
# has ever opened a chest that grows for as long as the server runs.

seeding = squashed(body_of(plain, "public static long seedFor("))
if "return mix(mix(mix(container) ^ playerHigh) ^ playerLow);" not in seeding:
    fail("a player's roll is not a function of the container and the player, so either "
         "it has to be remembered — a table that grows for ever — or it is rerolled "
         "every time the chest is opened, which is a slot machine")

mixing = squashed(body_of(plain, "private static long mix("))
for step in ("long z = value + 0x9E3779B97F4A7C15L;",
             "z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;",
             "z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;",
             "return z ^ (z >>> 31);"):
    if step not in mixing:
        fail("the mixing is not SplitMix64's finaliser; a weaker mix means two players "
             "whose ids differ by one bit get two rolls that differ by almost nothing, "
             "and a server that hands out sequential ids hands out identical loot")

forgetting = squashed(body_of(plain, "public static boolean forgettable("))
if "return emptied;" not in forgetting:
    fail("when a player's copy may be forgotten is not simply whether it is empty; "
         "anything else either keeps every player for ever or throws away loot "
         "somebody is coming back for")

loot_keeper = strip_comments(read(SRC, "event/LootKeeper.java"))
opening = squashed(body_of(loot_keeper, "public static void onOpen("))
for statement, why in (
        ("if (key == null) { return; }",
         "an ordinary chest somebody filled by hand is treated as loot and handed out "
         "as copies, so a shared base chest shows every player a different thing"),
        ("event.setCanceled(true);",
         "the player is shown their own copy and the real container as well"),
        ("if (player.isSecondaryUseActive() && !player.getMainHandItem().isEmpty()) {",
         "placing a block against a loot chest opens it instead")):
    if statement not in opening:
        fail("opening: " + why)
if "event.getHand() != InteractionHand.MAIN_HAND" not in opening:
    fail("the off hand opens the container too, so every click opens it twice")

sharing = squashed(body_of(loot_keeper, "private static SimpleContainer shareFor("))
if ("long seed = ComfortRules.seedFor(seedOf(container, pos), "
        "id.getMostSignificantBits(), id.getLeastSignificantBits());") not in sharing:
    fail("the roll in the world is not the seed the harness proves, so six hundred "
         "thousand checks are about a different chest from the one that ships")
if "if (shares.contains(who)) {" not in sharing:
    fail("a player who has opened a chest before is given a fresh roll, which is a "
         "reroll and therefore a slot machine")
if "table.fill(copy, params, seed);" not in sharing:
    fail("the copy is not filled from the container's own loot table with this player's "
         "seed")
if "container.unpackLootTable" in squashed(loot_keeper) or "setLootTable(null)" in loot_keeper:
    fail("the original container is unpacked, so the first player to arrive turns it "
         "into an ordinary chest and the hundredth gets nothing — which is the race "
         "this exists to end")

which = squashed(body_of(loot_keeper, "private static long seedOf("))
if "return seed != 0L ? seed : pos.asLong();" not in which:
    fail("two containers in one structure with no loot seed between them get the same "
         "roll, so a room of four chests is a room of one chest four times")

saving = squashed(body_of(loot_keeper, "private static void save("))
if "if (ComfortRules.forgettable(empty)) { shares.remove(who); } else {" not in saving:
    fail("a player who has cleared their copy is kept for ever, so the one table this "
         "design does need never empties")
if "container.getData(ComfortAttachments.SHARES.get()).copy();" not in saving:
    fail("the stored shares are edited in place rather than replaced, so the block "
         "entity is never told it changed and a restart loses what everybody took")
if "container.setChanged();" not in saving:
    fail("the container is never marked changed, so what a player took is not saved")

print("loot that is yours : a roll derived rather than remembered, an original that is "
      "never unpacked, and a table that empties itself")

# ---------------------------------------------------------------------------
# 6. Comforts in the world
# ---------------------------------------------------------------------------

if not os.path.isfile(GENERATOR):
    fail("there is no generator, so every asset in the module is kept by hand")
else:
    with tempfile.TemporaryDirectory() as tmp:
        environment = dict(os.environ, ELYSIUM_COMFORTS_OUT=tmp)
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
                     f"writes: " + ", ".join(sorted(drifted)[:6]))

lang = {}
lang_path = os.path.join(RES, "assets", NS, "lang/en_us.json")
if os.path.isfile(lang_path):
    lang = json.load(open(lang_path, encoding="utf-8"))
else:
    fail("there is no language file, so the block is called by its id")

blocks = sorted(re.findall(r'BLOCKS\.register\("([a-z_]+)"',
                           strip_comments(read(SRC, "registry/ComfortBlocks.java"), True)))
items = squashed(strip_comments(read(SRC, "registry/ComfortItems.java"), True))
for name in blocks:
    if f'registerSimpleBlockItem(ComfortBlocks.{name.upper()})' not in items:
        fail(f"{name} has no item, so it exists only to a command")
    for kind, where in (("model", f"assets/{NS}/models/block/{name}.json"),
                        ("item model", f"assets/{NS}/models/item/{name}.json"),
                        ("blockstate", f"assets/{NS}/blockstates/{name}.json")):
        if not os.path.isfile(os.path.join(RES, where)):
            fail(f"{name} has no {kind}, so it is a purple cube")
    if f"block.{NS}.{name}" not in lang:
        fail(f"{name} has no name in the language file")

state = read(RES, "assets", NS, "blockstates/grave.json")
for facing in ("north", "east", "south", "west"):
    if f"facing={facing}" not in state:
        fail(f"the grave has no model for facing={facing}, so it is invisible from one "
             f"side")

# The model's box and the block's collision box have to be the same, or a grave
# is a thing you can see through and cannot touch — or worse, touch and not see.
model = read(RES, "assets", NS, "models/block/grave.json")
box = re.search(r'"from":\s*\[\s*2,\s*0,\s*4\s*\].*?"to":\s*\[\s*14,\s*14,\s*12\s*\]',
                model, re.S)
shape = re.search(r"Block\.box\(2\.0D, 0\.0D, 4\.0D, 14\.0D, 14\.0D, 12\.0D\)", grave)
if not box or not shape:
    fail("the grave's model and its collision box are different sizes, so it is a block "
         "you can see through and cannot walk past, or the other way about")

strength = squashed(strip_comments(read(SRC, "registry/ComfortBlocks.java")))
if ".strength(1.0F, 3_600_000.0F)" not in strength:
    fail("a grave is not blast-proof, so a creeper at the spot where somebody died "
         "takes everything they were carrying with it")

# A headstone is not a full cube, and the game lets water flow into any block
# that is not solid. Without forceSolidOn a grave made where a player drowned was
# washed away within seconds and spilled everything to the sea floor (the
# first-join stage, 27 September 2026). Solid by declaration, whatever its shape.
grave_registration = strength[strength.find("GRAVE = BLOCKS.register("):]
grave_registration = grave_registration[:grave_registration.find(";") + 1]
if ".forceSolidOn()" not in grave_registration:
    fail("the grave is not declared solid, so water flows into its headstone shape and "
         "washes it away within seconds, spilling everything a drowned player carried "
         "to the sea floor")

print(f"comforts in the world: {len(blocks)} block(s), facing four ways, blast-proof, solid against water, "
      f"modelled and named")

# ---------------------------------------------------------------------------
# 7. The seam
# ---------------------------------------------------------------------------

main_class = read(SRC, "ElysiumComforts.java")
if f'MODID = "{NS}"' not in main_class:
    fail("the module id is not what everything else in the tree spells it")
for registered in ("ComfortAttachments.ATTACHMENTS.register", "ComfortBlocks.BLOCKS.register",
                   "ComfortBlockEntities.TYPES.register", "ComfortItems.ITEMS.register",
                   "ComfortComponents.TYPES.register"):
    if registered not in main_class:
        fail(f"{registered} is never called, so part of the module is written and none "
             f"of it exists")

# ---------------------------------------------------------------------------
# A villager in your pocket, and the clock that must travel with it
# ---------------------------------------------------------------------------
#
# A villager is not the mob standing there, it is a ledger, and a ledger copied
# field by field is a ledger with a field missing — which nobody notices for
# three weeks and then calls a server rollback. And the restock clock inside it
# is the exploit: the natural way to write "put the villager back" gives a
# freshly placed mob no history, so the game thinks a day has passed and
# restocks it on the spot.

trader_rules = read(PURE, "TraderRules.java")
trader_plain = strip_comments(trader_rules)

allowed = squashed(body_of(trader_plain,
                           "public static int restocksAllowed(Trader trader, long now)"))
if "long since = now - trader.restockedAt();" not in allowed:
    fail("what a villager has earned is no longer worked out from when it last "
         "restocked, so whatever it is worked out from does not travel with the "
         "villager — which is the exploit")
if "if (since < 0L) { return 0; }" not in allowed:
    fail("a clock that has gone backwards — a world restored from a backup — earns a "
         "villager a negative number of restocks, and a negative number of restocks is "
         "every restock")

restocking = squashed(body_of(trader_plain,
                              "public static Trader restocked(Trader trader, long now)"))
if "if (!mayRestock(trader, now)) { return trader; }" not in restocking:
    fail("a villager restocks when asked rather than when it is owed one, so asking "
         "repeatedly is the whole exploit with no item involved at all")
if "fresh, now, trader.restocks() + 1);" not in restocking:
    fail("a restock does not move the clock forward and count itself, so the next one "
         "is free")

trip = squashed(body_of(trader_plain, "public static boolean survivesTheTrip("))
if "return Objects.equals(trader, unpacked(packed(trader)));" not in trip:
    fail("whether a villager survives being written down is no longer the whole "
         "villager against the whole villager, so a field can go missing and pass")

pocket = squashed(body_of(trader_plain, "public static Trader pocketed(Trader trader)"))
if "return trader;" not in pocket:
    fail("picking a villager up changes it. It exists as a function precisely so that "
         "an edit which helpfully refreshes something here fails rather than ships")

item = strip_comments(read(SRC, "item/TraderItem.java"))
picking = squashed(body_of(item, "public InteractionResult interactLivingEntity("))
if "villager.saveWithoutId(written);" not in picking:
    fail("a villager is no longer picked up by keeping its own saved form whole; a "
         "ledger copied field by field is a ledger with a field missing")
placing = squashed(body_of(item, "public InteractionResult useOn(UseOnContext context)"))
if "villager.load(written);" not in placing:
    fail("a villager is no longer put back from exactly what was written down")
if re.search(r"\b(LAST_RESTOCK|RESTOCKS_TODAY)\b\s*[,)]", squashed(
        body_of(item, "public InteractionResult useOn(UseOnContext context)"))):
    fail("putting a villager down touches its restock clock. That is one villager, two "
         "hands and infinite emeralds, and it is never reported as an exploit — only "
         "as trading feeling a bit easy in this pack")

print("a villager carried : its whole ledger kept, and a clock that goes on being the "
      "clock while it is in your pocket")

print("what it declares   : the game and the library, and nothing it buries")

# ---------------------------------------------------------------------------
# Somewhere to sleep that is not a bed
# ---------------------------------------------------------------------------
#
# A bed moves your spawn, and that is what makes exploring expensive: every
# player carries one and never places one. Two kinds of rest divide the clock
# between them and neither touches where you wake when you die. Everything
# below is a way that could quietly stop being true.

# ---------------------------------------------------------------------------
# What the game itself makes
# ---------------------------------------------------------------------------
#
# This module names sixteen of the game's dyes by hand. A seventeenth invented
# one is a block with a recipe that can never fire, and from outside it looks
# exactly like a block nobody has bothered to craft — so the names are looked
# up in the game's own language file, which lists every item and block it
# ships.

GAME_JAR = os.path.join(os.environ.get("ELYSIUM_MC_LIBS", "/tmp/mc"), "vanilla.jar")


def the_game_makes():
    if not os.path.isfile(GAME_JAR):
        return set()
    import zipfile
    with zipfile.ZipFile(GAME_JAR) as jar:
        named = json.loads(jar.read("assets/minecraft/lang/en_us.json"))
    return {"minecraft:" + key.split(".", 2)[2] for key in named
            if key.startswith(("item.minecraft.", "block.minecraft."))}


THE_GAME_MAKES = the_game_makes()
if not os.path.isfile(GAME_JAR):
    # A failure, not an empty set: until 15 October no jar here left the set
    # empty, every check below that reads it stood aside, and the module passed.
    fail(f"the game's own item and block names cannot be read: there is no vanilla.jar "
         f"at {GAME_JAR}, so the dyes, wools and items this module names are checked "
         f"against nothing (set ELYSIUM_MC_LIBS to the folder holding the 1.21.1 client "
         f"jar as vanilla.jar, or run python3 tools/setup_env.py)")
elif len(THE_GAME_MAKES) < 2_000:
    fail(f"the game's jar is here and only {len(THE_GAME_MAKES)} item or block "
         f"name(s) could be read out of it, so every check in this file that "
         f"asks whether a thing exists is checking nothing at all")


print("-" * 70)

kinds = []
colours = []
rope = ""
refusals = {}
REST = strip_comments(read(SRC, "rules/RestRules.java"), keep_strings=True)
REST_BLOCK = strip_comments(read(SRC, "block/RestBlock.java"), keep_strings=True)
KEEPER = strip_comments(read(SRC, "event/RestKeeper.java"), keep_strings=True)
if not REST:
    fail("nothing says how to rest anywhere but a bed, so the pack is back to "
         "one bed carried everywhere and never placed")
else:
    _flat = re.sub(r"\s+", " ", REST)
    KIND_NAMES = dict(re.findall(
        r'public static final String (BAG|HAMMOCK|ROPE) = "(\w+)";', REST))
    _kinds = re.search(r"List<String> KINDS = List\.of\((.*?)\);", _flat)
    kinds[:] = [KIND_NAMES[one] for one in
                re.findall(r"[A-Z]+", _kinds.group(1) if _kinds else "")
                if one in KIND_NAMES]
    _colours = re.search(r"List<String> COLOURS = List\.of\((.*?)\);", _flat)
    colours[:] = re.findall(r'"([a-z_]+)"',
                            _colours.group(1) if _colours else "")
    rope = KIND_NAMES.get("ROPE", "")
    refusals = dict(re.findall(
        r'public static final String (OCCUPIED|TOO_FAR|NO_ROOM|NO_ROPE|NOT_NOW'
        r'|UNSAFE) = "(\w+)";', REST))

    if len(kinds) != 2:
        fail(f"there {'is' if len(kinds) == 1 else 'are'} {len(kinds)} kind(s) "
             f"of rest and the clock is divided between two, so either an hour "
             f"of the day has nothing that works in it or one thing works all "
             f"day — which deletes the night")
    if len(colours) != 16:
        fail(f"there are {len(colours)} colour(s) of rest and the game has "
             f"sixteen dyes, so at least one dye has nothing to make with it")
    if not rope:
        fail("a hammock has nothing to hang from, so sixteen of the blocks can "
             "never be slept in")

    # Every colour is one the game really has. An invented one is a block
    # nobody can craft, and it looks exactly like a block nobody has crafted.
    if THE_GAME_MAKES:
        for colour in colours:
            if f"minecraft:{colour}_wool" not in THE_GAME_MAKES:
                fail(f"{colour} is not one of the game's own dyes, so the "
                     f"{colour} bag and hammock are blocks with no recipe that "
                     f"can ever fire")

    rests = [f"{colour}_{kind}" for kind in kinds for colour in colours]
    # Everything the rules ask for exists, all the way out to the world.
    for name in rests + ([rope] if rope else []):
        for kind, where in (
                ("model", f"assets/{NS}/models/block/{name}.json"),
                ("item model", f"assets/{NS}/models/item/{name}.json"),
                ("blockstate", f"assets/{NS}/blockstates/{name}.json"),
                ("texture", f"assets/{NS}/textures/block/{name}.png"),
                ("loot table", f"data/{NS}/loot_table/blocks/{name}.json"),
                ("recipe", f"data/{NS}/recipe/{name}.json")):
            if not os.path.isfile(os.path.join(RES, where)):
                fail(f"{name} has no {kind}, so it is a block a player can see "
                     f"in a creative tab and never use")
        if f"block.{NS}.{name}" not in lang:
            fail(f"{name} has no name, so it reads as its own translation key")

    # And every one of them comes back when it is broken. The module's whole
    # theme: a bag that vanishes when a creeper clips the floor under it is
    # the same failure as a grave with too few slots, only smaller.
    for name in rests + ([rope] if rope else []):
        table = read(RES, "data", NS, "loot_table/blocks", name + ".json")
        if not table:
            continue
        if f'"{NS}:{name}"' not in table:
            fail(f"{name} does not give itself back when it is broken, so a "
                 f"creeper at the foot of your camp costs you the camp")

    # Every shaped recipe this module writes uses every symbol it declares.
    #
    # Found by check_loadtest.py rather than here, which is the wrong way
    # round: a key that defines a symbol its pattern never uses makes the game
    # refuse the whole recipe, log a line and carry on, so all sixteen
    # hammocks quietly did not exist and every other check in this file
    # passed. It is a property of any shaped recipe, so it is checked over all
    # of them rather than over the two that were wrong.
    for name in sorted(os.listdir(os.path.join(RES, "data", NS, "recipe"))
                       if os.path.isdir(os.path.join(RES, "data", NS, "recipe"))
                       else []):
        if not name.endswith(".json"):
            continue
        written = json.loads(read(RES, "data", NS, "recipe", name))
        if written.get("type") != "minecraft:crafting_shaped":
            continue
        used = {symbol for row in written.get("pattern", []) for symbol in row}
        for symbol in sorted(written.get("key", {})):
            if symbol not in used:
                fail(f"{name[:-5]} defines '{symbol}' in its key and never uses "
                     f"it in its pattern, so the game refuses the whole recipe, "
                     f"writes a line in the log and carries on — and the item "
                     f"is one nobody can ever craft")
        for symbol in sorted(used - set(written.get("key", {}))):
            if symbol != " ":
                fail(f"{name[:-5]} uses '{symbol}' in its pattern and its key "
                     f"does not say what that is")

    # And no two shaped recipes this module writes take the same things in
    # the same shape. The game cannot tell two such recipes apart, so one of
    # them wins and the other is an item nobody can ever craft — which is the
    # hammocks' bug again, one level up: it is about a pair rather than about
    # a key.
    _by_inputs = {}
    _recipes = os.path.join(RES, "data", NS, "recipe")
    for name in sorted(os.listdir(_recipes) if os.path.isdir(_recipes) else []):
        if not name.endswith(".json"):
            continue
        written = json.loads(read(RES, "data", NS, "recipe", name))
        if written.get("type") != "minecraft:crafting_shaped":
            continue
        fingerprint = json.dumps([written.get("pattern"), written.get("key")],
                                 sort_keys=True)
        already = _by_inputs.setdefault(fingerprint, name[:-5])
        if already != name[:-5]:
            fail(f"{name[:-5]} and {already} are made of the same things in the "
                 f"same shape, so the game cannot tell them apart: one of them "
                 f"wins and the other is an item nobody can ever craft")

    # The two kinds are two things to make, not one thing twice.
    if len(kinds) == 2 and colours:
        def inputs(of):
            written = read(RES, "data", NS, "recipe", of + ".json")
            if not written:
                return None
            made = json.loads(written)
            return json.dumps([made.get("pattern"), made.get("key"),
                               made.get("ingredients")], sort_keys=True)

        first = inputs(f"{colours[0]}_{kinds[0]}")
        second = inputs(f"{colours[0]}_{kinds[1]}")
        if first and first == second:
            fail("a bag and a hammock are made of exactly the same things in "
                 "the same shape, so one of the two kinds is a second name for "
                 "the other and the clock is divided between one thing")

    # Not one of the thirty-three registered by hand.
    for where, why in (("ComfortBlocks", "no place to sleep exists at all"),
                       ("ComfortItems", "none of them can be carried")):
        source = squashed(strip_comments(read(SRC, "registry", where + ".java")))
        if not source:
            fail(f"{where} is not here, so {why}")
            continue
        if "RestRules.KINDS" not in source and "ComfortBlocks.RESTS" not in source:
            fail(f"{where} names its blocks by hand rather than walking the "
                 f"rules, so the thirty-second is missing and the symptom is "
                 f"one dye that has nothing to make with it")
        if "RestRules.KINDS.size() * RestRules.COLOURS.size()" not in source \
                and "RestRules.roster().size()" not in source:
            fail(f"{where} does not count the places to sleep it registered, so "
                 f"a roster that half registers is a roster that half exists — "
                 f"and there is a second register in that file whose own count "
                 f"would go on passing for both")

    # Every refusal is reachable, and every one has a sentence of its own.
    _why = squashed(body_of(REST_BLOCK, "public String refusal("))
    if not _why:
        fail("nothing works out why a rest refuses you, so all six reasons are "
             "one silence and a player's only guess is that the pack is broken")
    for name, word in sorted(refusals.items()):
        if word == refusals.get("UNSAFE"):
            # The one the game itself decides; it is answered where the game
            # hands the problem back rather than in the check above.
            if f"RestRules.{name}" not in squashed(REST_BLOCK):
                fail(f"{word} is a reason the rules name and nothing can ever "
                     f"give, so it is a sentence nobody reads")
        elif f"RestRules.{name}" not in _why:
            fail(f"{word} is a reason the rules name and nothing can ever "
                 f"give, so it is a sentence nobody reads")
        if f"rest.{NS}.{word}" not in lang:
            fail(f"{word} has no words, so a player is refused and told a "
                 f"translation key")

    # The model and the collision box are the same four sixteenths. A thing
    # you can see through and cannot walk past is the commonest bug there is
    # in a decorative block, and it is never filed as one.
    if "public static final int LIES = 4;" not in REST_BLOCK:
        fail("a rest no longer says how tall it lies, so its model and its "
             "collision box have nothing to agree about")
    _model = read(RES, "assets", NS, "models/block",
                  f"{colours[0]}_{kinds[0]}.json" if colours and kinds else "")
    _lies = re.search(r"public static final int LIES = (\d+);", REST_BLOCK)
    _lies = int(_lies.group(1)) if _lies else 0
    if _model and not re.search(
            r'"to":\s*\[\s*16,\s*%d,\s*16\s*\]' % _lies, _model):
        fail(f"a rest is drawn a different height from the {_lies} sixteenths "
             f"it is, so it is a block you can see through and cannot walk "
             f"past — or the other way about, which is worse")

    # And the three things the game does about a bed that this must not do.
    if not KEEPER:
        fail("nothing keeps a rest from behaving like a bed, so it moves your "
             "spawn — which is the one thing this whole feature exists to "
             "stop")
    else:
        _spawn = squashed(body_of(KEEPER, "public static void onSetSpawn("))
        if "event.setCanceled(true);" not in _spawn:
            fail("a rest moves your spawn, so it is a bed with a different "
                 "model and three hundred blocks from home it costs you the "
                 "base you came from")
        if "restAt(player.level(), where) != null" not in _spawn:
            fail("every spawn-setting in the pack is cancelled rather than "
                 "only the ones at our own blocks, so no bed anywhere works")
        _slept = squashed(body_of(KEEPER, "public static void onSlept("))
        if "RestRules.skipsFrom(tick, kind)" not in _slept:
            fail("how far the world moves while you sleep is not the number "
                 "the rules worked out, so the hour you wake at and the hour "
                 "the harness swept are two different hours")
        _can = squashed(body_of(KEEPER, "public static void onCanSleep("))
        if "RestRules.usableAt(tick).equals(rest.kind())" not in _can:
            fail("what may be slept in at this hour is decided somewhere other "
                 "than the rules, so the partition the harness proved is not "
                 "the partition the game uses")

    # Every rule the file declares is added up.
    _sum = squashed(body_of(REST, "public static List<String> faults()"))
    _rules = set(re.findall(r"public static List<String> (faultsIn\w+)\(", REST))
    _added = set(re.findall(r"bad\.addAll\((faultsIn\w+)\([^;]*\)\);", _sum))
    if len(_rules) < 4:
        fail(f"only {len(_rules)} resting rule(s) are declared at all, so the "
             f"check below is checking nothing")
    for _rule in sorted(_rules - _added):
        fail(f"{_rule} is written, compiled and never added up by the "
             f"resting's faults(), so it answers the harness's controls and is "
             f"never once asked about what this module ships")

    print(f"somewhere to sleep : {len(rests) + (1 if rope else 0)} block(s) over "
          f"{len(kinds)} kind(s) and {len(colours)} colour(s), every one of them "
          f"craftable, breakable back into itself and named, six refusals each "
          f"with its own sentence, and not one of them moving your spawn")


# ---------------------------------------------------------------------------
# Something that makes a sound when the wind moves it
# ---------------------------------------------------------------------------
#
# An ambient block does not go wrong by being annoying on its own. It goes
# wrong because the player who likes it hangs eleven more, and every ambient
# block that has ever had to be muted got there the same way: each one rolled
# its own dice and nothing knew how many were rolling.

print("-" * 70)

CHIME = strip_comments(read(SRC, "rules/ChimeRules.java"), keep_strings=True)
CHIME_BLOCK = strip_comments(read(SRC, "block/ChimeBlock.java"), keep_strings=True)
if not CHIME:
    fail("nothing says how often a chime sounds, so a row of them is a wall of "
         "noise and the only thing a player can do about it is uninstall the "
         "pack")
else:
    _flat = re.sub(r"\s+", " ", CHIME)
    _materials = re.search(r"List<String> MATERIALS = List\.of\((.*?)\);", _flat)
    materials = re.findall(r'"([a-z_]+)"',
                           _materials.group(1) if _materials else "")
    made_of = dict(re.findall(r'made\.put\("([a-z_]+)", "([a-z_:]+)"\);', CHIME))
    chimes = [f"{colour}_{material}_chimes"
              for material in materials for colour in colours]

    if len(materials) < 2:
        fail(f"there {'is' if len(materials) == 1 else 'are'} {len(materials)} "
             f"material(s) of chime, and a chime that is the only note there is "
             f"has nothing to be a chord with")
    if sorted(made_of) != sorted(materials):
        fail(f"{len(materials)} material(s) of chime and {len(made_of)} of them "
             f"say what they are made of, so the rest have no recipe that can "
             f"ever fire")
    # Everything a chime is made of is a thing the game really has.
    if THE_GAME_MAKES:
        for material, of in sorted(made_of.items()):
            if of not in THE_GAME_MAKES:
                fail(f"a {material} chime is made of {of} and the game does not "
                     f"have one, so sixteen blocks have a recipe nothing can "
                     f"satisfy")

    for name in chimes:
        for kind, where in (
                ("model", f"assets/{NS}/models/block/{name}.json"),
                ("item model", f"assets/{NS}/models/item/{name}.json"),
                ("blockstate", f"assets/{NS}/blockstates/{name}.json"),
                ("texture", f"assets/{NS}/textures/block/{name}.png"),
                ("loot table", f"data/{NS}/loot_table/blocks/{name}.json"),
                ("recipe", f"data/{NS}/recipe/{name}.json")):
            if not os.path.isfile(os.path.join(RES, where)):
                fail(f"{name} has no {kind}, so it is a block a player can see "
                     f"in a creative tab and never use")
        if f"block.{NS}.{name}" not in lang:
            fail(f"{name} has no name, so it reads as its own translation key")

    # Not one of the ninety-six registered by hand.
    _blocks = squashed(strip_comments(read(SRC, "registry/ComfortBlocks.java")))
    if "ChimeRules.MATERIALS" not in _blocks:
        fail("the chimes are named by hand rather than walked from the rules, "
             "so one material in one colour is missing and the symptom is one "
             "dye that has nothing to make with it")
    if "ChimeRules.MATERIALS.size() * RestRules.COLOURS.size()" not in _blocks:
        fail("nothing counts the chimes that were registered, so a roster that "
             "half registers is a roster that half exists")

    # And the block asks the rules rather than deciding for itself.
    if not CHIME_BLOCK:
        fail("there is no chime block, so the arithmetic that keeps a room of "
             "them quiet is a file nothing reads")
    else:
        _tick = squashed(body_of(CHIME_BLOCK, "protected void tick("))
        for named, why in (
                ("int nearby = nearby(level, pos);",
                 "a chime does not count the company it keeps, so every one of "
                 "them rings on its own clock and a row of twelve is twelve "
                 "times the noise"),
                ("ChimeRules.chanceOnWaking(nearby, wind)",
                 "whether a chime sounds when it wakes is decided somewhere "
                 "other than the rules, so the ceiling the harness swept is "
                 "not the ceiling the game keeps"),
                ("level.scheduleTick(pos, this, ChimeRules.waitOf(nearby, wind));",
                 "a chime does not put itself back to sleep for as long as the "
                 "rules say, so either it stops ringing or it never stops"),
                ("ChimeRules.windAt(pos.getY(), level.canSeeSky(pos.above()))",
                 "how much wind a chime feels is not worked out from where it "
                 "is, so the one thing a player can do about a noisy chime — "
                 "put a roof over it — does nothing")):
            if named not in _tick:
                fail(why)
        if "ChimeRules.pitchOf(material)" not in squashed(CHIME_BLOCK):
            fail("every chime rings at the same note, which is the most tiring "
                 "noise a game can make")
        # The count is a slab rather than a cube. A cube of the same reach is
        # thirty-five thousand block lookups for the same answer, and it would
        # be done on the tick the room is meant to be cheapest.
        _near = squashed(body_of(CHIME_BLOCK, "public static int nearby("))
        if "pos.offset(-reach, -layers, -reach)" not in _near:
            fail("a chime counts its neighbours in a cube rather than in the "
                 "slab they actually hang in, which is thirty-five thousand "
                 "block lookups every time one of them rings")

    # Every rule the file declares is added up.
    _sum = squashed(body_of(CHIME, "public static List<String> faults()"))
    _rules = set(re.findall(r"public static List<String> (faultsIn\w+)\(", CHIME))
    _added = set(re.findall(r"bad\.addAll\((faultsIn\w+)\([^;]*\)\);", _sum))
    if len(_rules) < 4:
        fail(f"only {len(_rules)} chime rule(s) are declared at all, so the "
             f"check below is checking nothing")
    for _rule in sorted(_rules - _added):
        fail(f"{_rule} is written, compiled and never added up by the chimes' "
             f"faults(), so it answers the harness's controls and is never "
             f"once asked about what this module ships")

    print(f"the wind in them   : {len(chimes)} chime(s) over {len(materials)} "
          f"note(s), each made of something the game really has, each ringing "
          f"at a note of its own, and a room of them no louder than one of "
          f"them however many are in it")


# ---------------------------------------------------------------------------
# Eight blocks that do what a player would otherwise do
# ---------------------------------------------------------------------------
#
# What automating a villager buys is attention, not speed: a station goes on
# working while you are three thousand blocks away. The moment one of them is
# better value than a player standing there, it stops being a convenience and
# becomes a multiplier on emeralds, which is a multiplier on everything.

print("-" * 70)

STATION = strip_comments(read(SRC, "rules/StationRules.java"), keep_strings=True)
STATION_BLOCK = strip_comments(read(SRC, "block/StationBlock.java"),
                               keep_strings=True)
if not STATION:
    fail("nothing says what a station does or what it costs, so a block that "
         "automates a villager could hand back anything at all")
else:
    stations = re.findall(
        r'new Station\("([a-z_]+)", (\w+), (\d+), (\d+), ([\d_]+), (true|false)\)',
        re.sub(r"\s+", " ", STATION))
    jobs = dict(re.findall(
        r'public static final String (\w+) = "(\w+)";', STATION))
    if len(stations) < 2:
        fail(f"only {len(stations)} station(s) could be read out of the rules, "
             f"so everything below is checking an empty list and passing")

    # Everything the rules ask for exists, all the way out to the world.
    for row in stations:
        name = row[0]
        for kind, where in (
                ("model", f"assets/{NS}/models/block/{name}.json"),
                ("item model", f"assets/{NS}/models/item/{name}.json"),
                ("blockstate", f"assets/{NS}/blockstates/{name}.json"),
                ("texture", f"assets/{NS}/textures/block/{name}.png"),
                ("loot table", f"data/{NS}/loot_table/blocks/{name}.json"),
                ("recipe", f"data/{NS}/recipe/{name}.json")):
            if not os.path.isfile(os.path.join(RES, where)):
                fail(f"{name} has no {kind}, so it is a block a player can see "
                     f"in a creative tab and never use")
        if f"block.{NS}.{name}" not in lang:
            fail(f"{name} has no name, so it reads as its own translation key")
        table = read(RES, "data", NS, "loot_table/blocks", name + ".json")
        if table and f'"{NS}:{name}"' not in table:
            fail(f"{name} does not give itself back when it is broken, and it "
                 f"cost a player an emerald and nine planks")

    # Not one of the eight registered by hand, and counted.
    _blocks = squashed(strip_comments(read(SRC, "registry/ComfortBlocks.java")))
    if "for (StationRules.Station station : StationRules.STATIONS) {" not in _blocks:
        fail("the stations are named by hand rather than walked from the rules, "
             "so one of the eight is missing and the symptom is a job the rules "
             "name and no block does")
    if "made.size() != StationRules.STATIONS.size()" not in _blocks:
        fail("nothing counts the stations that were registered, so a roster "
             "that half registers is a roster that half exists")

    # The block asks the rules for the two numbers the feature turns on.
    if not STATION_BLOCK:
        fail("there is no station block, so the arithmetic that keeps a "
             "station from being a multiplier is a file nothing reads")
    else:
        _squashed_block = squashed(STATION_BLOCK)
        if "StationRules.station(id);" not in _squashed_block:
            fail("a station does not find its own row by the name it was "
                 "registered under, so whatever it is storing instead can be "
                 "stored wrong")
        if "level.scheduleTick(pos, this, station().ticks());" not in _squashed_block:
            fail("how long a station takes over a job is not the number the "
                 "rules worked out, so the pace the harness measured against a "
                 "player's own hands is not the pace the game keeps")
        for typed in re.findall(r"scheduleTick\(pos, this, (\d+)\)",
                                _squashed_block):
            fail(f"a station is scheduled {typed} ticks out by a number typed "
                 f"in the block, which is a second set of dials beside the "
                 f"rules'")

    # And a villager that goes in comes out. This module's own theme, applied
    # to something alive: a block that swallows a villager is a grave with too
    # few slots, except that what it ate had a name and a ledger.
    KEPT = strip_comments(read(SRC, "block/StationBlockEntity.java"),
                          keep_strings=True)
    if not KEPT:
        fail("nothing holds the villager a station is meant to be working, so "
             "all eight of them are blocks that do something to nothing")
    else:
        _kept = squashed(KEPT)
        if "CompoundTag written = from.get(ComfortComponents.TRADER.get());" \
                not in _kept:
            fail("a station keeps the villager somewhere other than the "
                 "component the crate keeps it in, so a villager carried in a "
                 "pocket and a villager in a station are two different "
                 "villagers and one of them has a fresh ledger")
        _take = squashed(body_of(KEPT, "public boolean take("))
        if "from.remove(ComfortComponents.TRADER.get());" not in _take:
            fail("a station takes a villager out of a crate and leaves it in "
                 "the crate as well, so there are two of it — which is how "
                 "every duplication bug that has ever shipped shipped")
        _back = squashed(body_of(KEPT, "public ItemStack handBack("))
        if "held = new CompoundTag();" not in _back:
            fail("a station hands a villager back and keeps it too, so clicking "
                 "one twice is a villager duplicator")
        if "back.set(ComfortComponents.TRADER.get(), held.copy());" not in _back:
            fail("a station hands back an empty crate, so whoever was in it is "
                 "gone and nothing says so")
        for named, why in (
                ("protected void loadAdditional(",
                 "a station never reads back who was in it, so the villager is "
                 "gone the first time the chunk unloads"),
                ("protected void saveAdditional(",
                 "a station never writes down who is in it, so the villager is "
                 "gone the first time the world is saved")):
            if named not in KEPT:
                fail(why)
        _block = squashed(STATION_BLOCK)
        if "!kept.empty()) { net.minecraft.world.Containers.dropContents(" \
                not in _block:
            fail("breaking a station keeps whoever was in it, so the one way a "
                 "player can get a villager out of a block they have built over "
                 "is to lose it")

    # Every rule the file declares is added up.
    _sum = squashed(body_of(STATION, "public static List<String> faults()"))
    _rules = set(re.findall(r"public static List<String> (faultsIn\w+)\(",
                            STATION))
    _added = set(re.findall(r"bad\.addAll\((faultsIn\w+)\([^;]*\)\);", _sum))
    if len(_rules) < 3:
        fail(f"only {len(_rules)} station rule(s) are declared at all, so the "
             f"check below is checking nothing")
    for _rule in sorted(_rules - _added):
        fail(f"{_rule} is written, compiled and never added up by the "
             f"stations' faults(), so it answers the harness's controls and is "
             f"never once asked about what this module ships")

    print(f"what it does for you: {len(stations)} station(s), each doing one "
          f"thing a player would otherwise do, none of them better value than "
          f"a pair of hands and none of them quicker, and every one of them "
          f"breaking back into itself")


# ---------------------------------------------------------------------------
# Mail: a thing you send that takes time to arrive and might not
# ---------------------------------------------------------------------------
#
# A mailbox that hands the parcel over at once is an ender chest with extra
# steps; one that takes a week is a block nobody uses twice. Everything
# interesting about post is in between, and this section is about proving the
# in-between is where the game actually puts it -- that the journey the rules
# worked out is the journey the block keeps, and that nothing along the way can
# quietly eat a parcel somebody paid for.

print("-" * 70)

POST = strip_comments(read(SRC, "rules/PostRules.java"), keep_strings=True)
MAILBOX = strip_comments(read(SRC, "block/MailboxBlock.java"), keep_strings=True)
BOX_KEEPER = strip_comments(read(SRC, "block/MailboxBlockEntity.java"),
                            keep_strings=True)
HOLE = strip_comments(read(SRC, "block/PigeonholeBlock.java"), keep_strings=True)
BIRD = strip_comments(read(SRC, "entity/PigeonEntity.java"), keep_strings=True)
MAIL_ITEM = strip_comments(read(SRC, "item/MailItem.java"), keep_strings=True)
if not POST:
    fail("nothing says how long a parcel takes or what it costs, so the post "
         "could hand anything over at any time and nothing would disagree")
else:
    _post = squashed(POST)
    _woods = re.search(r"List<String> WOODS = List\.of\((.*?)\);", _post)
    woods = re.findall(r'"([a-z_]+)"', _woods.group(1)) if _woods else []
    mail = re.findall(
        r'new Mail\((\w+), (\d+), (\d+), (true|false), (true|false), '
        r'(true|false)\)', _post)
    kinds = dict(re.findall(
        r'public static final String ([A-Z_]+) = "([a-z_]+)";', POST))
    birds = re.findall(
        r'new Bird\("([a-z_]+)", (\w+), ([^,]+), (\d+), (\d+)\)', _post)
    if len(woods) < 2:
        fail(f"only {len(woods)} wood(s) could be read out of the post, so every "
             f"pigeonhole check below is walking an empty list and passing")
    if len(mail) < 2:
        fail(f"only {len(mail)} kind(s) of mail could be read out of the post, "
             f"so every mail check below is walking an empty list and passing")
    if len(birds) < 1:
        fail(f"only {len(birds)} bird(s) could be read out of the post, so "
             f"nothing carries anything")

    # Every pigeonhole and the mailbox exist all the way out to the world.
    for name in [wood + "_pigeonhole" for wood in woods] + ["mailbox"]:
        for kind, where in (
                ("model", f"assets/{NS}/models/block/{name}.json"),
                ("item model", f"assets/{NS}/models/item/{name}.json"),
                ("blockstate", f"assets/{NS}/blockstates/{name}.json"),
                ("texture", f"assets/{NS}/textures/block/{name}.png"),
                ("loot table", f"data/{NS}/loot_table/blocks/{name}.json"),
                ("recipe", f"data/{NS}/recipe/{name}.json")):
            if not os.path.isfile(os.path.join(RES, where)):
                fail(f"{name} has no {kind}, so it is a block a player can see "
                     f"in a creative tab and never use")
        if f"block.{NS}.{name}" not in lang:
            fail(f"{name} has no name, so it reads as its own translation key")
        table = read(RES, "data", NS, "loot_table/blocks", name + ".json")
        if table and f'"{NS}:{name}"' not in table:
            fail(f"{name} does not give itself back when it is broken, and a "
                 f"mailbox costs a tag that costs three levels")

    # A pigeonhole's blockstate has to list every level of waste the block can
    # reach. One short is a block that renders as nothing at exactly the moment
    # it is worth scooping, which is the moment a player looks at it.
    _foulest = re.search(r"int FOULEST = (\d+);", POST)
    if not _foulest:
        fail("the post does not say how dirty a pigeonhole gets, so the "
             "blockstate cannot know how many levels to write")
    else:
        foulest = int(_foulest.group(1))
        for wood in woods:
            written = read(RES, "assets", NS, "blockstates",
                           wood + "_pigeonhole.json")
            for level in range(foulest + 1):
                if written and f"waste={level}" not in written:
                    fail(f"{wood}_pigeonhole has no model at waste level {level}, "
                         f"so it renders as nothing once a bird has been in it "
                         f"that many times")

    # Every kind of mail is an item with a sprite, a model and a name.
    for row in mail:
        name = kinds.get(row[0], row[0])
        for kind, where in (
                ("model", f"assets/{NS}/models/item/{name}.json"),
                ("texture", f"assets/{NS}/textures/item/{name}.png")):
            if not os.path.isfile(os.path.join(RES, where)):
                fail(f"{name} has no {kind}, so a player who crafts one is "
                     f"holding a missing-texture cube")
        if f"item.{NS}.{name}" not in lang:
            fail(f"{name} has no name, so it reads as its own translation key")

    # And every bird has a skin for every colouring it can roll. A bird that
    # rolls a variant nobody drew is a bird one player in four meets as a
    # black-and-magenta cube, and it is only ever found by that player.
    for row in birds:
        name, variants = row[0], int(row[4])
        for variant in range(variants):
            where = f"assets/{NS}/textures/entity/{name}_{variant}.png"
            if not os.path.isfile(os.path.join(RES, where)):
                fail(f"{name} can roll colouring {variant} and there is no skin "
                     f"for it, so that one renders as a missing texture")
        if f"entity.{NS}.{name}" not in lang:
            fail(f"{name} has no name, so it reads as its own translation key")
        if not os.path.isfile(os.path.join(
                RES, f"assets/{NS}/models/item/{name}_spawn_egg.json")):
            fail(f"{name} has no spawn egg model, so the one way to see one "
                 f"without waiting for a spawn is a missing-texture cube")

    # Nothing registered by hand, and everything counted.
    _blocks = squashed(strip_comments(read(SRC, "registry/ComfortBlocks.java")))
    if "for (String wood : PostRules.WOODS) {" not in _blocks:
        fail("the pigeonholes are named by hand rather than walked from the "
             "rules, so a wood the pack builds in has nothing to build the post "
             "out of and nothing says which")
    if "made.size() != PostRules.WOODS.size()" not in _blocks:
        fail("nothing counts the pigeonholes that were registered, so a roster "
             "that half registers is a roster that half exists")
    _items = squashed(strip_comments(read(SRC, "registry/ComfortItems.java")))
    if "for (com.elysium.comforts.rules.PostRules.Mail one" not in _items:
        fail("the mail is named by hand rather than walked from the rules, so a "
             "kind the rules price is a kind nothing registers")
    ENTITIES = strip_comments(read(SRC, "registry/ComfortEntities.java"))
    _entities = squashed(ENTITIES)
    # The register() body in particular. ComfortEntities walks the birds three
    # times -- to register, to give them attributes and to place their spawns --
    # so a check that only asks whether the file mentions the walk anywhere is a
    # check two of the three can answer for the third.
    _register = squashed(body_of(ENTITIES, "register() {"))
    if "for (PostRules.Bird bird : PostRules.BIRDS) {" not in _register:
        fail("the birds are named by hand rather than walked from the rules")
    if "made.size() != PostRules.BIRDS.size()" not in _entities:
        fail("nothing counts the birds that were registered, so a bird the rules "
             "describe is a bird nothing can spawn")
    _client = squashed(strip_comments(read(SRC, "client/ComfortsClient.java")))
    if "PostRules.BIRDS" not in _client:
        fail("the renderers are registered by hand rather than walked from the "
             "rules, and a renderer registered for one bird of two is a pack "
             "with a creature drawn as a black-and-magenta cube -- only ever "
             "found by the player who meets that one")
    _mod = squashed(strip_comments(read(SRC, "ElysiumComforts.java")))
    if "ComfortEntities.TYPES.register(modEventBus)" not in _mod:
        fail("the birds are never put on the mod event bus, so neither of them "
             "exists in the game at all and every check above is about files")

    # The block keeps the journey the rules worked out. Checked where the
    # arithmetic actually is -- the block entity -- rather than on the block,
    # which also mentions travelTicks in the sentence it shows the player and
    # would therefore pass this check while doing the sum itself.
    if not BOX_KEEPER:
        fail("nothing holds what has arrived at a mailbox, so everything posted "
             "is posted into the air")
    else:
        _keeper = squashed(BOX_KEEPER)
        _flight = squashed(body_of(BOX_KEEPER, "public boolean send("))
        if "PostRules.travelTicks(blocks)" not in _flight:
            fail("a mailbox does not ask the rules how long a parcel takes, so "
                 "the band the harness swept is not the band the game keeps")
        for typed in re.findall(r"arrives = now \+ (\d+)", _keeper):
            fail(f"a parcel is landed {typed} ticks out by a number typed into "
                 f"the block, which is a second set of dials beside the rules'")
        if "if (parcel.isEmpty() || expecting())" not in _flight:
            fail("a mailbox takes a second parcel while one is already in the "
                 "air, and every answer to what happens to the first is a way to "
                 "lose it")
        _land = squashed(body_of(BOX_KEEPER, "public boolean landIfDue("))
        if "arrives = now + PostRules.SOONEST;" not in _land:
            fail("a parcel that arrives at a full mailbox is not sent round "
                 "again, so it lands nowhere and a player has paid postage for "
                 "an item that no longer exists")
        _drops = squashed(body_of(BOX_KEEPER, "public NonNullList<ItemStack> drops("))
        if "if (!flying.isEmpty())" not in _drops:
            fail("breaking a mailbox keeps whatever was in the air towards it, "
                 "so a parcel somebody paid for can be made to vanish by "
                 "breaking the box it was coming to")
        for named, why in (
                ("protected void loadAdditional(",
                 "a mailbox never reads back what was in it, so everything "
                 "delivered is gone the first time the chunk unloads"),
                ("protected void saveAdditional(",
                 "a mailbox never writes down what is in it, so everything "
                 "delivered is gone the first time the world is saved")):
            if named not in BOX_KEEPER:
                fail(why)
        if "ContainerHelper.saveAllItems" not in _keeper:
            fail("a mailbox saves its contents some way other than the game's "
                 "own, so an item with components on it comes back as something "
                 "else")

    if not MAILBOX:
        fail("there is no mailbox, so the arithmetic that makes the post a post "
             "rather than a chest is a file nothing reads")
    else:
        _mailbox = squashed(MAILBOX)
        if "PostRules.UNKNOWN_DISTANCE" not in _mailbox:
            fail("the mailbox works out how far a parcel is going without asking "
                 "the rules what distance to assume when nobody knows, so the "
                 "one number the whole journey turns on is typed twice")
        for typed in re.findall(
                r"box\.send\(one, level\.getGameTime\(\), (\d+)\)", _mailbox):
            fail(f"a parcel is sent {typed} blocks by a number typed into the "
                 f"block, which is a second set of dials beside the rules'")
        _remove = squashed(body_of(MAILBOX, "protected void onRemove("))
        if "Containers.dropContents(level, pos, all);" not in _remove:
            fail("breaking a mailbox keeps what was in it, so the one way to "
                 "move a postbox is to lose the post")

    # Opening a parcel empties it before it hands anything over.
    if not MAIL_ITEM:
        fail("there is no mail item, so nothing a player holds is a parcel")
    else:
        _unpack = squashed(body_of(MAIL_ITEM, "public ItemStack unpack("))
        if "stack.remove(ComfortComponents.CONTENTS.get());" not in _unpack:
            fail("a parcel hands its contents over and keeps them as well, so "
                 "opening one twice is an item duplicator -- which is how every "
                 "duplication bug that has ever shipped shipped")
        _order = _unpack.find("stack.remove(ComfortComponents.CONTENTS.get());")
        _give = _unpack.find("player.getInventory().add(one)")
        if _order >= 0 and _give >= 0 and _order > _give:
            fail("a parcel hands its contents over before clearing itself, so a "
                 "crash in between leaves two of everything that was in it")
        if "sealed() && !addressedTo(stack, player)" not in squashed(MAIL_ITEM):
            fail("a sealed parcel opens in anybody's hands, so the seal is a "
                 "picture and the postage paid for it bought nothing")

    # The bird's two clocks are two clocks.
    if not BIRD:
        fail("nothing carries the post, so every parcel arrives by itself")
    else:
        _bird = squashed(BIRD)
        if "PostRules.bird(name)" not in _bird:
            fail("a bird does not find its own row by the name it was registered "
                 "under, so whatever it is storing instead can be stored wrong")
        _turn = squashed(body_of(BIRD, "private void turn()"))
        if "server.addFreshEntity(made);" not in _turn or "discard();" not in _turn:
            fail("a bird carried somewhere it does not belong does not become "
                 "the other bird, so the loop the rules hold shut is not the "
                 "loop the game walks")
        elif _turn.find("server.addFreshEntity(made);") > _turn.find("discard();"):
            fail("a bird is discarded before its replacement is placed, so a "
                 "crash in between is a bird gone for good")
        for named, why in (
                ("public void addAdditionalSaveData(",
                 "a bird never writes down how long it has been where it does "
                 "not belong, so carrying one below and logging out resets it"),
                ("public void readAdditionalSaveData(",
                 "a bird never reads back its own clocks, so a tired one comes "
                 "back rested and a pigeonhole never gets dirty")):
            if named not in BIRD:
                fail(why)

    # A pigeonhole only pays what the rules say, and only to a shovel.
    if not HOLE:
        fail("there is no pigeonhole, so there is nowhere for a bird to be")
    else:
        _hole = squashed(HOLE)
        if "PostRules.wasteAt(level_)" not in _hole:
            fail("a pigeonhole hands back an amount it works out itself rather "
                 "than the rules' own, which is a second set of dials beside "
                 "the rules'")
        for typed in re.findall(r"Items\.BONE_MEAL, (\d+)\)", _hole):
            fail(f"a pigeonhole hands back {typed} by a number typed into the "
                 f"block, so the climb the rules insist on is not the climb the "
                 f"game pays")

    # No two kinds of mail share a recipe shape. Two that did would be two
    # recipes the game resolves to whichever it loaded first, so one of them
    # silently does not exist -- which is exactly how sixteen hammocks were
    # refused at load for a season.
    shapes = {}
    for row in mail:
        name = kinds.get(row[0], row[0])
        written = read(RES, "data", NS, "recipe", name + ".json")
        if not written:
            continue
        made = json.loads(written)
        if made.get("type") != "minecraft:crafting_shaped":
            continue
        fingerprint = (tuple(made["pattern"]),
                       tuple(sorted((symbol, json.dumps(what, sort_keys=True))
                                    for symbol, what in made["key"].items())))
        if fingerprint in shapes:
            fail(f"{name} and {shapes[fingerprint]} are crafted the same way, so "
                 f"the game resolves both to whichever it loaded first and the "
                 f"other silently does not exist")
        shapes[fingerprint] = name
        # And every symbol the key defines is a symbol the pattern uses. One that
        # is not is not harmless: the game refuses the whole recipe with a line
        # in the log and carries on, so the item quietly cannot be made.
        used = {symbol for line in made["pattern"] for symbol in line
                if symbol != " "}
        for symbol in made["key"]:
            if symbol not in used:
                fail(f"{name}'s recipe declares the key '{symbol}' and its "
                     f"pattern never uses it, so the game refuses the whole "
                     f"recipe and the item cannot be crafted at all")
        for symbol in used:
            if symbol not in made["key"]:
                fail(f"{name}'s recipe uses '{symbol}' in its pattern and never "
                     f"says what it is, so the game refuses the recipe")

    # The way in (29 Sep 2026): every row of PostRules.WILD is a biome modifier
    # that spawns that bird there, as the rules say; and the loss the rules
    # price is rolled where a parcel lands, with a second road to lost mail.
    _wild = re.findall(r'new Wild\("([a-z_]+)", "([^"]+)", (\d+), (\d+), (\d+)\)',
                       re.sub(r"\s+", " ", POST))
    if not _wild:
        fail("no bird spawns anywhere, so the post's carriers can never be met")
    for _bird, _biomes, _weight, _least, _most in _wild:
        _written = read(RES, "data", NS, "neoforge/biome_modifier", _bird + "_spawns.json")
        _doc = json.loads(_written) if _written else {}
        _spawner = (_doc.get("spawners") or [{}])[0]
        if _doc.get("type") != "neoforge:add_spawns" or _doc.get("biomes") != _biomes \
                or _spawner.get("type") != f"{NS}:{_bird}" or _spawner.get("weight") != int(_weight) \
                or _spawner.get("minCount") != int(_least) or _spawner.get("maxCount") != int(_most):
            fail(f"the rules spawn {_bird} in {_biomes} at weight {_weight}, {_least} to {_most}, "
                 f"and no biome modifier says so, so the bird has no way into the world")
    _box = squashed(read(SRC, "block/MailboxBlockEntity.java"))
    if "PostRules.lostOn(level.random.nextInt(1000))" not in _box:
        fail("a parcel is never lost where it lands, so the loss the rules price is rolled "
             "nowhere and lost mail is an item nothing makes")
    if "PostRules.salvaged(one.getCount())" not in _box:
        fail("lost mail keeps an amount the box works out itself, not the rules' salvage")
    _global = read(RES, "data/neoforge/loot_modifiers/global_loot_modifiers.json")
    if f"{NS}:lost_mail_from_cartographer" not in _global:
        fail("the cartographer's lost mail is written and never listed, so the game never "
             "applies it")

    # Every rule the file declares is added up.
    _sum = squashed(body_of(POST, "public static List<String> faults()"))
    _rules = set(re.findall(r"public static List<String> (faultsIn\w+)\(", POST))
    _added = set(re.findall(r"bad\.addAll\((faultsIn\w+)\([^;]*\)\);", _sum))
    if len(_rules) < 8:
        fail(f"only {len(_rules)} post rule(s) are declared at all, so the check "
             f"below is checking nothing")
    for _rule in sorted(_rules - _added):
        fail(f"{_rule} is written, compiled and never added up by the post's "
             f"faults(), so it answers the harness's controls and is never once "
             f"asked about what this module ships")

    print(f"the post           : {len(woods)} pigeonhole(s), {len(mail)} kind(s) "
          f"of mail and {len(birds)} bird(s); a journey the block takes from the "
          f"rules rather than from a number typed into it, a parcel that is "
          f"never in two places and never in none, and a seal that only opens "
          f"in the hands it is addressed to")


# ---------------------------------------------------------------------------
# 8. WHAT LIGHTING A ROOM COSTS
# ---------------------------------------------------------------------------
#
# A decoration module is where a bestiary quietly dies. Thirty pretty light
# sources, each fine on its own, each brighter and cheaper than a torch, and
# somewhere else in the pack nine monsters that only spawn in the dark. Nobody
# decides to remove them; they are removed by the sum of thirty decisions that
# were each about how a room looks.
#
# So this section is mostly about one number. STILL_DARK is not a taste: it is
# the brightest light at which a majority of elysium-bestiary's own monsters
# still spawn, worked out here from that module's table rather than trusted,
# because the copy in LightRules is a copy and copies drift.

print("-" * 70)

LIGHT = strip_comments(read(SRC, "rules/LightRules.java"), keep_strings=True)
FITTING = strip_comments(read(SRC, "block/FittingBlock.java"), keep_strings=True)
LIGHT_BLOCKS = strip_comments(read(SRC, "registry/ComfortBlocks.java"),
                              keep_strings=True)
HAUNTS_AT = os.path.join(ROOT, "elysium-bestiary/src/main/java/com/elysium/"
                               "bestiary/rules/HauntRules.java")
if not LIGHT:
    fail("nothing says what a light fitting gives or costs, so a decoration "
         "block can be brighter than glowstone and cheaper than a torch and "
         "the only thing that reports it is a player wondering where the "
         "monsters went")
else:
    _light = squashed(LIGHT)
    fitting_kinds = dict(re.findall(
        r'public static final String (HELD|HUNG|FLOOR|HEARTH|UNDERFOOT|STRUNG)'
        r' = "(\w+)";', LIGHT))
    light_numbers = {name: int(value) for name, value in re.findall(
        r"public static final int (BRIGHTEST|STILL_DARK|GRID) = (\d+);", LIGHT)}
    for wanted in ("HELD", "HUNG", "FLOOR", "HEARTH", "UNDERFOOT", "STRUNG"):
        if wanted not in fitting_kinds:
            fail(f"LightRules no longer names a {wanted} fitting, and this "
                 f"section is written around the six kinds")
    for wanted in ("BRIGHTEST", "STILL_DARK", "GRID"):
        if wanted not in light_numbers:
            fail(f"LightRules no longer names {wanted}, so the number this "
                 f"module is held to is whatever anybody types")

    # ---- the table, as the rules build it --------------------------------
    def _light_piece(term):
        term = term.strip()
        if term.startswith('"'):
            return term[1:-1]
        if term == "RestRules.ROPE":
            return rope
        return fitting_kinds.get(term, "")

    def _light_number(term):
        term = term.strip()
        return int(term) if term.isdigit() else light_numbers.get(term, -1)

    fittings = []
    _body = body_of(LIGHT, "private static List<Fitting> fittings()")
    for _call in re.findall(r"new Fitting\(([^()]*)\)", _body):
        _args = [one.strip() for one in _call.split(",")]
        if len(_args) != 5:
            fail(f"a fitting is written with {len(_args)} field(s) and a "
                 f"Fitting has five")
            continue
        _kind = _light_piece(_args[1])
        _lit, _cost = _light_number(_args[2]), _light_number(_args[3])
        if _args[4] == "colour":
            for _colour in colours:
                fittings.append((_colour + "_" + _kind, _kind, _lit, _cost,
                                 _colour))
        else:
            fittings.append(("".join(_light_piece(one)
                                     for one in _args[0].split("+")),
                             _kind, _lit, _cost, ""))
    _already = re.search(r"List<String> ALREADY = List\.of\((.*?)\);", _light)
    already = ([_light_piece(one) for one in _already.group(1).split(",")]
               if _already else [])
    made_here = [one for one in fittings if one[0] not in already]
    if len(fittings) < 20:
        fail(f"only {len(fittings)} fitting(s) could be read out of LightRules, "
             f"so every check below is checking almost nothing")
    if not already:
        fail("LightRules names nothing this module already has, so nothing "
             "stops a second rope being registered beside the first")
    for one in already:
        if one not in [row[0] for row in fittings]:
            fail(f"{one} is named as a block this module already has and no "
                 f"fitting is it, so the roster and the registry disagree "
                 f"about what this module contains")

    # ---- the number the bestiary decides ---------------------------------
    if not os.path.isfile(HAUNTS_AT):
        fail("elysium-bestiary's haunts are not where this module expects "
             "them, so the one number holding a decoration roster to a "
             "bestiary is a number nothing checks")
    else:
        _haunts = strip_comments(open(HAUNTS_AT, encoding="utf-8").read(),
                                 keep_strings=True)
        _haunt_numbers = {name: int(value) for name, value in re.findall(
            r"public static final int (\w+) = (\d+);", _haunts)}
        _table = re.search(r"List<Haunt> HAUNTS = List\.of\((.*?)\);",
                           squashed(_haunts), re.S)
        bands = []
        if _table:
            for _row in re.findall(r"new Haunt\(([^()]*)\)", _table.group(1)):
                _parts = [one.strip() for one in _row.split(",")]
                if len(_parts) < 4:
                    continue
                _dark, _bright = _parts[2], _parts[3]
                _dark = (int(_dark) if _dark.isdigit()
                         else _haunt_numbers.get(_dark, -1))
                _bright = (int(_bright) if _bright.isdigit()
                           else _haunt_numbers.get(_bright, -1))
                if _dark >= 0 and _bright >= 0:
                    bands.append((_dark, _bright))
        if len(bands) < 5:
            fail(f"only {len(bands)} monster band(s) could be read out of "
                 f"elysium-bestiary, so the number this module copies from it "
                 f"is checked against nothing")
        else:
            # The last light level at which more than half of them still turn
            # up. Past it, a lit room is a room they are not in.
            worked_out = -1
            for _level in range(16):
                _in = sum(1 for _dark, _bright in bands
                          if _dark <= _level <= _bright)
                if _in * 2 > len(bands):
                    worked_out = _level
            if worked_out != light_numbers.get("STILL_DARK", -1):
                fail(f"this module keeps {light_numbers.get('STILL_DARK')} as "
                     f"the light a room can be and still have the bestiary in "
                     f"it, and elysium-bestiary's own {len(bands)} monsters say "
                     f"that number is {worked_out} — so either a band moved "
                     f"over there or this number was typed, and the pack now "
                     f"has decoration that empties a room without saying so")

    # ---- the rules are all asked -----------------------------------------
    _fsum = squashed(body_of(LIGHT, "public static List<String> faults()"))
    _rules = set(re.findall(r"public static List<String> (faultsIn\w+)\(", LIGHT))
    _added = set(re.findall(r"bad\.addAll\((faultsIn\w+)\([^;]*\)\);", _fsum))
    if len(_rules) < 9:
        fail(f"only {len(_rules)} lighting rule(s) are declared at all, so the "
             f"check below is checking nothing")
    for _rule in sorted(_rules - _added):
        fail(f"{_rule} is written, compiled and never added up by the "
             f"lighting's faults(), so it answers the harness's controls and "
             f"is never once asked about what this module ships")
    if re.search(r"\bif\s*\(", _fsum):
        fail("the lighting's faults() has a branch in it, so a rule can be "
             "added up only sometimes and the roster it ships can be the "
             "sometimes it is not")

    # ---- every fitting is a block, and none of them twice -----------------
    _reg = squashed(body_of(LIGHT_BLOCKS,
                            "private static java.util.Map<String, "
                            "DeferredBlock<FittingBlock>> fittings()"))
    if not _reg:
        fail("nothing registers the fittings, so a module full of rules about "
             "light ships no light")
    else:
        if "for (LightRules.Fitting one : LightRules.FITTINGS)" not in _reg:
            fail("the fittings are not registered by walking LightRules' own "
                 "roster, so the roster this module proves things about and "
                 "the blocks it ships are two lists that can differ")
        if "LightRules.ALREADY.contains(one.id())" not in _reg:
            fail("nothing skips what this module already has, so the rope a "
                 "hammock hangs from is registered a second time and the pack "
                 "ships two of it")
        _level = re.search(r"\.lightLevel\((.*?)\)\s*\.", _reg)
        if not _level:
            fail("no fitting is given a light level at all, so every one of "
                 "them is dark and the whole table is about nothing")
        elif ".light()" not in _level.group(1):
            fail("a fitting's light level is not read off its row, so the "
                 "number a player measures in a room and the number these "
                 "rules prove things about are two numbers that can differ")
        if re.search(r"lightLevel\([^)]*\b\d+\b", _reg):
            fail("a light level is typed into the registration, which is a "
                 "number no rule here can see")
    for one in already:
        _times = len(re.findall(r"BLOCKS\.register\(RestRules\.ROPE\b",
                                LIGHT_BLOCKS))
        if _times != 1:
            fail(f"{one} is registered {_times} time(s) and this module needs "
                 f"exactly one of it")

    # ---- what a player bumps into is what they see ------------------------
    _shapes = dict(re.findall(
        r"private static final VoxelShape (\w+) = box\(([^;]*)\);", FITTING))
    _switch = dict(re.findall(r"case LightRules\.(\w+) -> (\w+);", FITTING))
    boxes = {}
    for _const, _shape in _switch.items():
        if _shape not in _shapes or _const not in fitting_kinds:
            continue
        _numbers = [int(float(one.strip().rstrip("Dd")))
                    for one in _shapes[_shape].split(",")]
        if len(_numbers) == 6:
            boxes[fitting_kinds[_const]] = (_numbers[:3], _numbers[3:])
    if FITTING and not boxes:
        fail("FittingBlock no longer chooses its shape by the kind on its row, "
             "so what a player bumps into is the same for a doormat and a lamp")
    elif FITTING:
        # A lamp is a block of stone and is meant to be a whole cube. Every
        # other kind is something you put in a room, and a kind that has lost
        # its shape is a doormat you bark your shins on.
        for _kind in sorted({one[1] for one in made_here}
                            - {fitting_kinds.get("FLOOR", "")}):
            if _kind not in boxes:
                fail(f"nothing says what shape a {_kind} is, so it is a full "
                     f"cube a player cannot see the edges of and cannot walk "
                     f"past")
    if re.search(r"\b(light|Light)\s*=\s*\d+", FITTING):
        fail("FittingBlock has a light level written into it, so the block and "
             "its row can say different things about the same fitting")

    # ---- and every one of them is really in the jar -----------------------
    priced = 0
    for name, kind, lit, cost, colour in made_here:
        for what, where in (
                ("texture", f"assets/{NS}/textures/block/{name}.png"),
                ("model", f"assets/{NS}/models/block/{name}.json"),
                ("item model", f"assets/{NS}/models/item/{name}.json"),
                ("blockstate", f"assets/{NS}/blockstates/{name}.json"),
                ("loot table", f"data/{NS}/loot_table/blocks/{name}.json")):
            if not os.path.isfile(os.path.join(RES, where)):
                fail(f"{name} has no {what}, so it is a fitting a player "
                     f"cannot see, place or break back into itself")
        if f"block.{NS}.{name}" not in lang:
            fail(f"{name} has no name in the language file")
        _model_at = os.path.join(RES, f"assets/{NS}/models/block/{name}.json")
        if os.path.isfile(_model_at) and kind in boxes:
            _drawn = json.load(open(_model_at, encoding="utf-8"))
            _element = (_drawn.get("elements") or [{}])[0]
            _want = boxes[kind]
            if [_element.get("from"), _element.get("to")] != [list(_want[0]),
                                                              list(_want[1])]:
                fail(f"{name} is drawn from {_element.get('from')} to "
                     f"{_element.get('to')} and FittingBlock says you bump into "
                     f"{_want[0]} to {_want[1]}, so a player either walks "
                     f"through it or stops short of it")
        _recipe_at = os.path.join(RES, f"data/{NS}/recipe/{name}.json")
        if not os.path.isfile(_recipe_at):
            fail(f"{name} can be seen in the creative tab and nowhere else, "
                 f"because nothing says how to make one")
            continue
        _recipe = json.load(open(_recipe_at, encoding="utf-8"))
        _parts = _recipe.get("ingredients") or []
        if _recipe.get("type") != "minecraft:crafting_shapeless":
            fail(f"{name} is made by a {_recipe.get('type')} and this module "
                 f"prices its fittings by how many items they take, which only "
                 f"a shapeless recipe counts")
        elif len(_parts) != cost:
            fail(f"{name} costs {cost} in the rules and {len(_parts)} on disk, "
                 f"so every rule this module has about what lighting a room "
                 f"costs is about a price the game does not charge")
        elif cost > light_numbers.get("GRID", 9):
            fail(f"{name} costs {cost} and a crafting grid holds "
                 f"{light_numbers.get('GRID')}, so the recipe is dropped at "
                 f"load and the block cannot be made at all")
        else:
            priced += 1
        for _part in _parts:
            _item = _part.get("item", "")
            if THE_GAME_MAKES and _item not in THE_GAME_MAKES:
                fail(f"{name} is made out of {_item}, which the game does not "
                     f"make — so the recipe is dropped at load and the only "
                     f"symptom is a block nobody can craft")

    seen = set()
    dim = 0
    for name, kind, lit, cost, colour in fittings:
        key = kind + " (dyed)" if colour else name
        if key in seen:
            continue
        seen.add(key)
        if lit <= light_numbers.get("STILL_DARK", 7):
            dim += 1
    print(f"lighting a room    : {len(fittings)} fitting(s) in {len(seen)} "
          f"shape(s) over {len(fitting_kinds)} kind(s), {priced} of them priced "
          f"on disk at exactly what their row says, nothing over "
          f"{light_numbers.get('BRIGHTEST')} light, and {dim} of {len(seen)} "
          f"shape(s) at or under {light_numbers.get('STILL_DARK')} — which is "
          f"elysium-bestiary's own number for the brightest a room can be and "
          f"still have most of its monsters in it")


# The birds' body plan (docs/POLISH.md P10). PigeonModel is generated from
# art/bodyplans.py's winged plan with its sheets from the same rows: a part
# tree of sixty-plus boxes that flies, and every colouring's sheet the size
# the rows packed to.
_pigeon_model = strip_comments(read(SRC, "client/PigeonModel.java"))
_pigeon_boxes = _pigeon_model.count(".addBox(")
if "GENERATED by tools/gen_comforts.py" not in read(SRC, "client/PigeonModel.java"):
    fail("PigeonModel is written by hand again, so its sheet and its boxes can disagree")
if _pigeon_boxes < 60:
    fail(f"PigeonModel is {_pigeon_boxes} box(es); a bird patterned on the eight is sixty and more")
if "boolean flying = entity != null && !entity.onGround();" not in _pigeon_model:
    fail("PigeonModel no longer beats its wings off the ground, so a bird in the air walks")
_pigeon_sheet = re.search(r"public static final int SHEET = (\d+);", _pigeon_model)
_pigeon_sheet = int(_pigeon_sheet.group(1)) if _pigeon_sheet else 0
_bird_dir = os.path.join(RES, "assets", NS, "textures/entity")
if _pigeon_sheet and os.path.isdir(_bird_dir):
    try:
        from PIL import Image as _Image
    except ImportError:
        # A failure, not a pass: until 15 October no Pillow measured no sheet.
        _Image = None
        fail("the birds' sheets cannot be measured against the plan they were packed to: "
             "Pillow is not installed for this python (pip install Pillow)")
    if _Image is not None:
        for _name in sorted(os.listdir(_bird_dir)):
            if _name.endswith(".png"):
                with _Image.open(os.path.join(_bird_dir, _name)) as _img:
                    if _img.size != (_pigeon_sheet, _pigeon_sheet):
                        fail(f"{_name} is {_img.size[0]}x{_img.size[1]} and the bird's plan packed to "
                             f"{_pigeon_sheet}: the model reads pixels that are not there")
print(f"the birds          : one winged plan of {_pigeon_boxes} boxes that flies, every colouring on its sheet")
if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all comforts checks passed")
