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
                    elif open(made_path, "rb").read() != open(kept, "rb").read():
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
    for kind, where in (("a model", f"assets/{NS}/models/block/{name}.json"),
                        ("an item model", f"assets/{NS}/models/item/{name}.json"),
                        ("a blockstate", f"assets/{NS}/blockstates/{name}.json")):
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

print(f"comforts in the world: {len(blocks)} block(s), facing four ways, blast-proof, "
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
if os.path.isfile(GAME_JAR) and len(THE_GAME_MAKES) < 2_000:
    fail(f"the game's jar is here and only {len(THE_GAME_MAKES)} item or block "
         f"name(s) could be read out of it, so every check in this file that "
         f"asks whether a thing exists is checking nothing at all")


print("-" * 70)

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
    kinds = [KIND_NAMES[one] for one in
             re.findall(r"[A-Z]+", _kinds.group(1) if _kinds else "")
             if one in KIND_NAMES]
    _colours = re.search(r"List<String> COLOURS = List\.of\((.*?)\);", _flat)
    colours = re.findall(r'"([a-z_]+)"', _colours.group(1) if _colours else "")
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
                ("a model", f"assets/{NS}/models/block/{name}.json"),
                ("an item model", f"assets/{NS}/models/item/{name}.json"),
                ("a blockstate", f"assets/{NS}/blockstates/{name}.json"),
                ("a texture", f"assets/{NS}/textures/block/{name}.png"),
                ("a loot table", f"data/{NS}/loot_table/blocks/{name}.json"),
                ("a recipe", f"data/{NS}/recipe/{name}.json")):
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
        if "IllegalStateException" not in source:
            fail(f"{where} does not count what it registered, so a roster that "
                 f"half registers is a roster that half exists")

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


if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all comforts checks passed")
