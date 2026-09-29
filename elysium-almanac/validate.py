#!/usr/bin/env python3
"""
Validate Elysium Almanac against its sources.

Run from anywhere:   python3 elysium-almanac/validate.py

A quest book is the one module in this pack whose failures are all invisible
to the person who wrote it. Nothing in it crashes. A step nobody can reach
looks perfect in the editor; two steps waiting on each other look like a
chapter that is not finished yet; a task naming an item no installed module
makes looks exactly like a task you have not got round to. All three are
things a player experiences as "this pack is broken" and the author
experiences as nothing at all.

So the book is a graph, and the graph is checked three times over: by the
harness, which plays the whole of it; by this file, which computes the same
properties from the same declarations with a second implementation that shares
no code with the first; and by the generator, which refuses to write words for
a quest that does not exist or leave a quest without words.

  1. INDEPENDENCE. The library, and not one module it describes.
  2. PURITY, and the harness run rather than read.
  3. THE GRAPH. Read out of the Java and checked again here.
  4. THE BOOK POINTS AT REAL THINGS.
  5. THE WORDS, and that they are generated.
  6. THE BOOK IN THE WORLD.
  7. THE SEAM.
"""
import json
import os
import sys as _sys
_sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "tools"))
import samefile  # noqa: E402
import importlib.util
import re
import subprocess
import sys
import tempfile
import zipfile

WORK = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(WORK)
RES = os.path.join(WORK, "src/main/resources")
SRC = os.path.join(WORK, "src/main/java/com/elysium/almanac")
PURE = os.path.join(SRC, "rules")
HARNESS = os.path.join(WORK, "tools/AlmanacHarness.java")
HARNESS_MAIN = "com.elysium.almanac.rules.AlmanacHarness"
GENERATOR = os.path.join(WORK, "tools/gen_almanac.py")

#: How long the graph is allowed to take. Playing a forty-step book takes
#: milliseconds; anything past this is a walk with no memory, which is the very
#: bug three of the checks below exist to find.
HARNESS_SECONDS = 60
NS = "elysiumalmanac"

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

    Naming the whole statement is the point. This project has now been bitten
    eleven times by a rule that looked for a token which survives the very edit
    it was meant to catch, because the token appears somewhere else in the same
    method — and a rule that has never failed is a rule nobody has seen work.
    
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
    print("This is not the elysium-almanac tree.")
    sys.exit(1)

print("=" * 70)
print("elysium-almanac — the book, and the proof a player can get to the end of it")
print("=" * 70)

java_files = []
for dirpath, _, filenames in os.walk(SRC):
    for name in filenames:
        if name.endswith(".java"):
            java_files.append(os.path.join(dirpath, name))

rules = read(PURE, "AlmanacRules.java")
plain = strip_comments(rules)
worded = strip_comments(rules, keep_strings=True)

# ---------------------------------------------------------------------------
# 1. Independence — which for this module is the whole design
# ---------------------------------------------------------------------------
#
# Every other module in the pack is allowed to depend downwards. The almanac is
# allowed to depend on nothing at all, because it is the one module that talks
# about all the others: a guide that imported the modules it describes could
# only describe the ones that happened to be installed, and would stop the game
# loading when one of them was not. It names things by string and looks them up
# at runtime. That is not a shortcut, it is the feature, so it is checked.

FOREIGN = re.compile(
    r"(?:ftbquests|dev\.ftb|com\.hollingsworth|vazkii\.patchouli|"
    r"net\.darkhax|questsadditions)", re.I)

for path in java_files:
    src = strip_comments(read(path))
    where = os.path.relpath(path, SRC)
    for hit in FOREIGN.findall(src):
        fail(f"{where} names {hit}; this module is a quest book of our own and must not "
             f"reach for one of the quest mods it replaces")
    for used in set(re.findall(r"com\.elysium\.(\w+)", src)):
        if used not in ("almanac", "lib"):
            fail(f"{where} names com.elysium.{used} in code rather than as a string; the "
                 f"book must survive that module not being installed, and a class "
                 f"reference does not")

toml = read(RES, "META-INF/neoforge.mods.toml")
declared = set(re.findall(r'modId="([^"]+)"', toml))
allowed = {"${mod_id}", "neoforge", "minecraft", "elysiumlib"}
for mod in declared - allowed:
    fail(f"the module declares a dependency on {mod}; a guide that required the modules "
         f"it describes could only ever describe a complete install")
for needed in ("neoforge", "minecraft", "elysiumlib"):
    if needed not in declared:
        fail(f"the module does not declare {needed}")

print(f"independence       : {len(java_files)} source file(s), and not one of the six "
      f"modules the book describes named anywhere but in a string")

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
            fail(f"rules/{name} reaches for {forbidden}; the rules are the JDK and "
                 f"nothing else, so a harness can play the whole book without a game")
    for used in re.findall(r"import\s+(com\.elysium\.[\w.]+);", without_package):
        fail(f"rules/{name} imports {used}; the graph is the JDK alone")

if not os.path.isfile(HARNESS):
    fail("there is no harness, so the book is checked by reading it, which is the one "
         "method that provably does not find either of the two failures")
else:
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
            fail("the graph does not compile against the JDK alone:\n"
                 + compiled.stderr.strip()[:1200])
        else:
            # With a clock on it, because the failure this module is most
            # likely to have is a walk that forgot where it had been, and that
            # does not come back with a wrong answer — it does not come back.
            try:
                ran = subprocess.run(
                    ["java", "-cp", os.path.join(tmp, "out"), HARNESS_MAIN],
                    capture_output=True, text=True, timeout=HARNESS_SECONDS)
            except subprocess.TimeoutExpired:
                ran = None
                fail(f"the harness did not finish within {HARNESS_SECONDS} seconds, so "
                     f"something in the graph can loop forever — which is a server that "
                     f"stops responding, not a wrong answer")
            if ran is None:
                pass
            elif ran.returncode != 0:
                fail("the almanac harness failed:\n" + harness_trouble(ran.stdout))
            else:
                print("the graph played   : " + ran.stdout.strip().splitlines()[-1])

# ---------------------------------------------------------------------------
# 3. The graph, read out of the Java and checked a second time here
# ---------------------------------------------------------------------------
#
# Deliberately a second implementation. The harness and the rules are the same
# code calling itself: a walker that has lost its memory agrees with a cycle
# check built on that walker, and both agree that everything is fine. The
# arithmetic below shares nothing with either of them.

QUEST = re.compile(r'quest\("([a-z_]+)",\s*"([a-z_]+)",\s*List\.of\(([^)]*)\)', re.S)
declared_quests = []
for qid, chapter, needs in QUEST.findall(worded):
    declared_quests.append((qid, chapter, re.findall(r'"([a-z_]+)"', needs)))

chapters = re.findall(r'new Chapter\("([a-z_]+)",\s*List\.of\(([^)]*)\)\)', worded)
chapter_after = dict((cid, re.findall(r'"([a-z_]+)"', after))
                     for cid, after in chapters)

if not declared_quests:
    fail("no quests could be read out of the rules, so nothing below is checking the "
         "book that ships")
if not chapters:
    fail("no chapters could be read out of the rules")

by_id = dict((qid, (chapter, needs)) for qid, chapter, needs in declared_quests)
if len(by_id) != len(declared_quests):
    seen, twice = set(), set()
    for qid, _, _ in declared_quests:
        (twice if qid in seen else seen).add(qid)
    fail(f"{sorted(twice)} is declared more than once; a quest with two definitions is "
         f"one the player finishes by doing whichever half came second")


def waits_on(start, target):
    """Second implementation, with its own memory."""
    seen, pending = set(), list(by_id.get(start, ("", []))[1])
    while pending:
        here = pending.pop()
        if here == target:
            return True
        if here in seen:
            continue
        seen.add(here)
        pending.extend(by_id.get(here, ("", []))[1])
    return False


own_prerequisite = sorted(q for q in by_id if waits_on(q, q))
if own_prerequisite:
    fail(f"{own_prerequisite} wait(s) on themselves, however far back; a player reads "
         f"that as a chapter that is not finished yet and stops looking")

dangling = sorted(set(need for _, (_, needs) in by_id.items() for need in needs)
                  - set(by_id))
if dangling:
    fail(f"{dangling} are named as prerequisites and are not quests, so everything "
         f"behind them is content nobody will ever be shown")

reached, moved = set(), True
while moved:
    moved = False
    for qid, (_, needs) in by_id.items():
        if qid not in reached and all(need in reached for need in needs):
            reached.add(qid)
            moved = True
stuck = sorted(set(by_id) - reached)
if stuck:
    fail(f"{stuck} can never be started by anybody; a quest the author can see and the "
         f"player cannot is the worse of the two failures, because it looks fine from "
         f"every angle except the one that matters")

firsts = [cid for cid, after in chapter_after.items() if not after]
if len(firsts) != 1:
    fail(f"the book has {len(firsts)} first chapter(s) ({sorted(firsts)}); a guide whose "
         f"opening page is ambiguous is not a direction")
for cid, after in chapter_after.items():
    for one in after:
        if one not in chapter_after:
            fail(f"chapter {cid} follows {one}, which is not a chapter")

# Depth first over the whole shape rather than up a single line, now that a
# chapter may follow two. A loop is still a loop and still means there is no
# order to read the book in.
def loops(cid, standing):
    if cid in standing:
        return True
    for one in chapter_after.get(cid, []):
        if loops(one, standing + (cid,)):
            return True
    return False


for cid in chapter_after:
    if loops(cid, ()):
        fail(f"the chapters loop at {cid}; there is no order to read them in")

# The book is read in the order it is written — ChapterScreen walks CHAPTERS
# and draws them — so a chapter listed before one it follows is one a player
# reaches first and cannot start. Not a loop, and not caught by looking for
# one: the shape is perfectly acyclic and still wrong.
listed = [cid for cid, _ in chapters]
for cid, after in chapter_after.items():
    for one in after:
        if one in listed and listed.index(one) > listed.index(cid):
            fail(f"chapter {cid} is listed before {one}, which it follows; the book is "
                 f"read in the order it is written, so a player reaches it before the "
                 f"chapter it was written to come after")

empty = [cid for cid in chapter_after
         if not any(chapter == cid for _, chapter, _ in declared_quests)]
if empty:
    fail(f"chapter(s) {empty} have no quests in them, so the book has a heading with "
         f"nothing under it")

for _, chapter, _ in declared_quests:
    if chapter not in chapter_after:
        fail(f"a quest is filed under chapter {chapter}, which is not a chapter, so it "
             f"is in the book and on no page of it")


def ancestry(cid):
    """Every chapter a player has, by the book's own account, already read."""
    seen, pending = set(), list(chapter_after.get(cid, []))
    while pending:
        here = pending.pop()
        if here in seen:
            continue
        seen.add(here)
        pending.extend(chapter_after.get(here, []))
    return seen


# A chapter is a promise about order. A quest in The Foundry may wait on one in
# The Ascent, because the foundry comes after the ascent; one in The Ascent
# waiting on the foundry would mean a chapter that opens before the chapter it
# was written to follow, which is a book that lies about its own shape.
for qid, (chapter, needs) in by_id.items():
    older = ancestry(chapter) | {chapter}
    for need in needs:
        if need in by_id and by_id[need][0] not in older:
            fail(f"{qid} is in {chapter} and waits on {need}, which is in "
                 f"{by_id[need][0]} — a chapter that comes later. The chapter order is "
                 f"a promise about what a player has already read")

depths = {}


def depth_of(qid, walking=()):
    if qid in depths:
        return depths[qid]
    if qid in walking:
        return 0
    needs = by_id.get(qid, ("", []))[1]
    found = 0 if not needs else max(depth_of(n, walking + (qid,)) + 1 for n in needs)
    depths[qid] = found
    return found


for qid in by_id:
    depth_of(qid)
if depths and max(depths.values()) < 4:
    fail(f"the longest chain in the book is {max(depths.values())} step(s); a guide to a "
         f"pack of four ages that can be finished four steps deep is not guiding "
         f"anybody through it")

# And the rules themselves, so that the arithmetic above is checking a book the
# game will agree with rather than one only this file believes in.
for needed in ("cycles", "waitsOn", "unreachable", "danglingNeeds", "depthOf",
               "openTo"):
    if needed not in plain:
        fail(f"there is no {needed}, so one of the properties the book rests on is not "
             f"stated in the module at all")

walker = squashed(body_of(plain, "public static boolean waitsOn("))
if "if (!seen.add(here)) { continue; }" not in walker:
    fail("the walk over the book has no memory, so asking whether a quest waits on "
         "itself in a book that contains a cycle is itself the endless loop")

cyc = squashed(body_of(plain, "public static Set<String> cycles()"))
if "if (waitsOn(quest.id(), quest.id())) {" not in cyc:
    fail("the cycle check does not ask each quest whether it waits on itself, so it is "
         "answering a different question and returning an empty set to it")

unreach = squashed(body_of(plain, "public static Set<String> unreachable()"))
for statement in ("Set<String> stuck = new HashSet<>(BY_ID.keySet());",
                  "stuck.removeAll(reached);"):
    if statement not in unreach:
        fail("the unreachable check does not take the quests it reached away from all "
             "of them, so it reports on something other than what is stranded")

deep = squashed(body_of(plain, "public static int depthOf("))
if "deepest = Math.max(deepest, depthOf(need) + 1);" not in deep:
    fail("depth is not the longest chain behind a quest, so a chapter sorted by it is "
         "not sorted into the order a player can do it in")

opened = squashed(body_of(plain, "public static List<Quest> openTo("))
if "if (done.contains(quest.id())) { continue; }" not in opened:
    fail("what is open to a player includes what they have already finished, so the "
         "book offers a player the step they just did, forever")

task_guard = squashed(body_of(plain, "public Task {"))
if "if (count < 1) {" not in task_guard:
    fail("a task may want none of something, which is a step that is finished before "
         "the player has read it")

quest_guard = squashed(body_of(plain, "public Quest {"))
if "if (tasks.isEmpty()) {" not in quest_guard:
    fail("a quest may ask for nothing, which is a step the player cannot fail to have "
         "done and therefore cannot be told about")

print(f"the graph          : {len(by_id)} quest(s) over {len(chapter_after)} chapter(s), "
      f"no cycle, nothing stranded, nothing dangling, longest chain "
      f"{max(depths.values()) if depths else 0} deep — checked again here, by other "
      f"arithmetic")

# ---------------------------------------------------------------------------
# 4. The book points at real things
# ---------------------------------------------------------------------------
#
# The other half of naming everything by string. A task naming an item nothing
# makes is indistinguishable, to a player, from a task they have not managed
# yet — they will look for it for an hour. The module cannot check this,
# because the module is not allowed to know the other modules exist. This file
# is, so it does it here.

TASK = re.compile(r'Task\.(have|place|visit|slay|craft|mine|use|eat|smelt)\((?:(\w+) \+ )?"([^"]+)"')
names = {}
for prefix, token, value in TASK.findall(worded):
    names.setdefault(prefix, []).append((token, value))

prefixes = dict(re.findall(r'private static final String (\w+) = "([a-z]+:)";', worded))

catalogue = {}
for entry in sorted(os.listdir(ROOT)):
    lang_path = os.path.join(ROOT, entry, "src/main/resources/assets")
    if not os.path.isdir(lang_path):
        continue
    for namespace in os.listdir(lang_path):
        one = os.path.join(lang_path, namespace, "lang/en_us.json")
        if os.path.isfile(one):
            try:
                catalogue[namespace] = json.load(open(one, encoding="utf-8"))
            except ValueError:
                fail(f"{entry}'s language file is not valid JSON")

# The generator, imported for its tables rather than parsed: the families are
# lists of ids and the worlds a table of names, and both are checked here
# against the tree.
gen = None
if os.path.isfile(GENERATOR):
    try:
        spec = importlib.util.spec_from_file_location("gen_almanac", GENERATOR)
        gen = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(gen)
    except Exception as trouble:  # noqa: BLE001 — a generator that will not import is reported below
        gen = None
        fail(f"the generator cannot be imported, so its families cannot be checked: {trouble}")
FAMILIES = dict(getattr(gen, "FAMILIES", {}) or {})
EXCUSED = list(getattr(gen, "EXCUSED", []) or [])

MC_JAR = os.environ.get("MC_JAR", "")
vanilla = set()
if MC_JAR and os.path.isfile(MC_JAR):
    with zipfile.ZipFile(MC_JAR) as jar:
        vanilla = set(jar.namelist())

#: What sort of language key each kind of task's id has to have. A quest
#: pointing at a mob nothing spawns is exactly as invisible as one pointing at
#: an item nothing makes, and the first version of this only checked the
#: items — so twelve tasks naming nine beasts went unchecked entirely.
SORTS = {"have": ("item", "block"), "place": ("block", "item"), "slay": ("entity",),
         "craft": ("item", "block"), "eat": ("item",), "smelt": ("item", "block"),
         "mine": ("block", "item"), "use": ("block", "item")}

#: Which registry a tag stands in for, per kind of task.
TAG_FOLDER = {"have": "item", "craft": "item", "eat": "item", "smelt": "item",
              "place": "block", "mine": "block", "use": "block", "slay": "entity_type"}


def shipped_data(namespace, relative):
    """Whether any module, or the base game, ships data/<namespace>/<relative>."""
    for entry in os.listdir(ROOT):
        if os.path.isfile(os.path.join(ROOT, entry, "src/main/resources/data", namespace,
                                       relative)):
            return True
    return namespace == "minecraft" and f"data/minecraft/{relative}" in vanilla


def catalogued(full, sorts):
    """Whether some module's language file names this id as one of these sorts."""
    namespace, path = full.split(":", 1)
    if namespace == "minecraft":
        return any(f"assets/minecraft/models/{sort}/{path}.json" in vanilla
                   for sort in sorts if sort in ("item", "block")) \
            or (("entity" in sorts) and f"assets/minecraft/textures/entity/{path}.png" in vanilla)
    if namespace not in catalogue:
        return False
    return any(f"{sort}.{namespace}.{path}" in catalogue[namespace] for sort in sorts)


asked, tagged, worlds, unknown = 0, 0, 0, []
for kind, entries in names.items():
    for token, value in entries:
        full = prefixes.get(token, "") + value if token else value
        bare = full[1:] if full.startswith("#") else full
        if ":" not in bare:
            fail(f"a task asks for {full!r}, which is not a namespaced id and therefore "
                 f"names nothing")
            continue
        namespace, path = bare.split(":", 1)
        if kind == "visit":
            worlds += 1
            if not shipped_data(namespace, f"dimension/{path}.json"):
                unknown.append(full + " (no module in this tree has a world of that name)")
            continue
        if full.startswith("#"):
            tagged += 1
            folder = TAG_FOLDER[kind]
            if namespace == NS:
                if path not in FAMILIES:
                    unknown.append(full + " (the book has no such family)")
            elif not shipped_data(namespace, f"tags/{folder}/{path}.json"):
                unknown.append(full + f" (no module ships a tag of that name for {folder}s)")
            continue
        asked += 1
        if namespace != "minecraft" and namespace not in catalogue:
            unknown.append(full + " (no such module in the tree)")
        elif not catalogued(full, SORTS[kind]):
            unknown.append(full + (" (nothing in the tree spawns it)"
                                   if kind == "slay" else ""))
if unknown:
    fail(f"{len(unknown)} task(s) ask for things no module in this tree makes, which a "
         f"player cannot tell apart from a thing they have not found yet: "
         + ", ".join(sorted(unknown)[:8]) + (" ..." if len(unknown) > 8 else ""))

# The families: every id in one is something in the tree, and every family is
# asked for by something, or it is a tag nobody reads.
family_trouble = []
family_members = 0
asked_families = set(value.split(":", 1)[1] for _, entries in names.items()
                     for token, value in entries
                     if value.startswith("#" + NS + ":"))
for family, members in sorted(FAMILIES.items()):
    if not re.fullmatch(r"[a-z_]+", family):
        family_trouble.append(f"{family!r} is not spelled like a tag path")
    if not members:
        family_trouble.append(f"{family} has nothing in it")
    for one in members:
        family_members += 1
        if ":" not in one or not catalogued(one, ("item", "block", "entity")):
            family_trouble.append(f"{family} lists {one}, which nothing in the tree makes")
    if family not in asked_families:
        family_trouble.append(f"{family} is a family no task asks for")
if family_trouble:
    fail(f"{len(family_trouble)} family problem(s): " + "; ".join(family_trouble[:6])
         + (" ..." if len(family_trouble) > 6 else ""))
for pattern, why in EXCUSED:
    try:
        re.compile(pattern)
    except re.error:
        fail(f"the excusal {pattern!r} is not a pattern")
    if len(why) < 12:
        fail(f"the excusal {pattern!r} gives no reason worth the name")

# The two halves of a counted quest, neither of which any other check looks at.
progress = squashed(strip_comments(read(SRC, "event/AlmanacProgress.java")))
if "case SLAY -> AlmanacAttachments.slain(player, task.what()) >= task.count();" not in progress:
    fail("a quest asking for three of something finishes on the first thing anybody "
         "kills, because nothing compares the tally against what the quest asked for")
if "AlmanacAttachments.slew(player, named);" not in progress:
    fail("nothing counts what a player kills, so a quest asking for three of something "
         "they have already killed two of needs three more and never says why")
if "case PLACE, BREAK, CRAFT, USE, EAT, SMELT -> AlmanacAttachments.count(player, task) " \
        ">= task.count();" not in progress:
    fail("the counted kinds never read their tally against what the quest asked for, so "
         "a step asking for forty of something finishes on the first")
if "AlmanacAttachments.bump(player, task, by);" not in progress:
    fail("nothing counts what a player places, breaks, crafts, uses, eats or smelts, so "
         "every counted step waits for ever")

tally = squashed(strip_comments(read(SRC, "registry/AlmanacAttachments.java")))
if "now.set(at + 1, Integer.toString(slain(player, what) + 1)); " \
        "player.setData(SLAIN.get(), List.copyOf(now));" not in tally:
    fail("a kill is counted and never stored, so the tally is whatever it was when the "
         "player logged in — an attachment changed in place is an attachment nothing "
         "syncs, which is the same mistake the finished list already documents")
if "now.set(at + 1, Integer.toString(countOf(was, key) + by)); " \
        "player.setData(TALLY.get(), List.copyOf(now));" not in tally:
    fail("a counted event is counted and never stored, so the tally is whatever it was "
         "when the player logged in")
if ".copyOnDeath()" not in tally:
    fail("the tally is lost on death, and losing your notes on the way to the Nether "
         "is not a difficulty setting")

print(f"what it asks for   : {asked} item(s), block(s) and beast(s), {tagged} tag(s) "
      f"({len(FAMILIES)} of the book's own families, {family_members} member(s)) and "
      f"{worlds} world(s) named across {len(set(prefixes.values()))} module(s), every one "
      f"of them made, shipped or spawned by something in this tree")

# ---------------------------------------------------------------------------
# 4b. The rewards, and the kinds
# ---------------------------------------------------------------------------
#
# Every step pays, by rule or by declaration, and the paying is done once, on
# the server, for a step that is done. And every kind the rules declare is a
# kind something answers — a task of a kind nothing answers is a step no player
# can ever finish, which is the module's founding failure in a new coat.

levelling = strip_comments(read(ROOT, "elysium-lib/src/main/java/com/elysium/lib/character",
                                "ElysiumLevelling.java"), True)
for ours, theirs in (("LEVEL_XP_BASE", "XP_BASE"), ("LEVEL_XP_PER_LEVEL", "XP_PER_LEVEL")):
    mine = re.search(rf"{ours}\s*=\s*(\d+);", worded)
    lib = re.search(rf"\b{theirs}\s*=\s*(\d+);", levelling)
    if not mine or not lib:
        fail(f"the level curve constant {ours} or the library's {theirs} cannot be read")
    elif mine.group(1) != lib.group(1):
        fail(f"the book's {ours} is {mine.group(1)} and the library's own curve says "
             f"{lib.group(1)}, so the harness adds the book up against a curve the game "
             f"does not use")

rewards_src = squashed(strip_comments(read(SRC, "event/AlmanacRewards.java")))
if "if (!AlmanacAttachments.has(player, questId)) { return WHY_NOT_DONE; }" not in rewards_src:
    fail("a reward pays for steps not finished; the book becomes a shop with nothing on the "
         "shelves but free things")
marked = rewards_src.find("if (!AlmanacAttachments.claim(player, questId)) { return WHY_ALREADY; }")
paid = rewards_src.find("ElysiumCharacter.addXp(player, reward.xp());")
if marked < 0:
    fail("a reward is never marked taken, so it is paid every time it is asked for")
if paid < 0:
    fail("a reward pays no experience through the library's one door, so the trinkets "
         "that scale experience do not see it")
if 0 <= paid < marked:
    fail("a reward is paid before it is marked taken, so two clicks in one tick pay twice")

network = squashed(strip_comments(read(SRC, "network/AlmanacNetwork.java")))
if "registrar.playToServer(AlmanacClaim.TYPE, AlmanacClaim.STREAM_CODEC, AlmanacNetwork::onClaim);" \
        not in network:
    fail("the claim is never registered as a payload, so the book's one button sends a "
         "message nobody is listening for")
if "bus = EventBusSubscriber.Bus.MOD" not in network:
    fail("the payload handlers are registered on the wrong bus, so they are never registered")
step_src = squashed(strip_comments(read(SRC, "client/StepScreen.java")))
if "PacketDistributor.sendToServer(new AlmanacNetwork.AlmanacClaim(quest.id()));" not in step_src:
    fail("the reward row sends nothing, so a reward can be looked at and never taken")

command = squashed(strip_comments(read(SRC, "command/AlmanacCommand.java"), True))
if 'Commands.literal("almanac")' not in command:
    fail("there is no /almanac command, so the tests have no way past a step")
for guarded in ("finish", "reset"):
    if f'Commands.literal("{guarded}").requires(source -> source.hasPermission(2))' not in command:
        fail(f"anybody can {guarded} their own almanac from the console, which is a book "
             f"anybody can finish without opening")

kinds = re.findall(r"^\s+([A-Z]+)[,;]", body_of(plain, "public enum Kind"), re.M)
if len(kinds) < 10:
    fail(f"only {len(kinds)} kinds could be read out of the rules")
for kind in kinds:
    if not re.search(rf"case [A-Z, ]*\b{kind}\b[A-Z, ]* ->", progress):
        fail(f"a task of kind {kind} is never answered in AlmanacProgress, so every step "
             f"asking for one waits for ever")
    if not re.search(rf"case [A-Z, ]*\b{kind}\b[A-Z, ]* ->", step_src):
        fail(f"a task of kind {kind} has no label on the step screen")
for event, why in (
        ("BlockEvent.BreakEvent", "breaking a block is never noticed, so every BREAK step "
                                  "is impossible"),
        ("PlayerInteractEvent.RightClickBlock", "using a block is never noticed"),
        ("PlayerEvent.ItemCraftedEvent", "crafting is never noticed"),
        ("PlayerEvent.ItemSmeltedEvent", "smelting is never noticed"),
        ("LivingEntityUseItemEvent.Finish", "eating is never noticed")):
    if event not in progress:
        fail(f"there is no handler for {event}, so {why}")

print(f"the rewards        : every step pays through the library's door, once, for a step "
      f"that is done; {len(kinds)} kinds of task, every one answered and labelled")

# ---------------------------------------------------------------------------
# 5. The words, and that they are generated
# ---------------------------------------------------------------------------

if not os.path.isfile(GENERATOR):
    fail("there is no generator, so the words are kept by hand and a quest can be added "
         "without one")
else:
    with tempfile.TemporaryDirectory() as tmp:
        environment = dict(os.environ, ELYSIUM_ALMANAC_OUT=tmp)
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
                     f"them: " + ", ".join(sorted(drifted)[:6])
                     + (" ..." if len(drifted) > 6 else ""))

generator = read(GENERATOR)


def table(name):
    """One top-level dict out of the generator, by name.

    Scoped rather than swept, because the generator holds two tables of the
    same shape — the chapters and the quests — and a rule that reads both at
    once reports every chapter as a quest nobody declared. Which is exactly
    what this one did on its first run.
    """
    start = generator.find(f"\n{name} = {{\n")
    if start < 0:
        return set()
    end = generator.find("\n}\n", start)
    return set(re.findall(r'^    "([a-z_]+)": \(', generator[start:end], re.M))


worded_quests = table("QUESTS")
worded_chapters = table("CHAPTERS")
if not worded_quests or not worded_chapters:
    fail("the generator's tables of words could not be read, so nothing below is "
         "comparing them with anything")
lang = {}
lang_path = os.path.join(RES, "assets", NS, "lang/en_us.json")
if os.path.isfile(lang_path):
    lang = json.load(open(lang_path, encoding="utf-8"))
else:
    fail("there is no language file, so every heading in the book is a raw key")

for qid in sorted(by_id):
    for key, what in ((f"{NS}.quest.{qid}", "a title"), (f"{NS}.why.{qid}", "a reason")):
        if key not in lang:
            fail(f"quest {qid} has no {what}; a step with no reason is a checklist entry, "
                 f"and a checklist is the thing this module exists instead of")
for cid in sorted(chapter_after):
    for key, what in ((f"{NS}.chapter.{cid}", "a title"),
                      (f"{NS}.why.chapter.{cid}", "a reason")):
        if key not in lang:
            fail(f"chapter {cid} has no {what}")

spare = sorted(worded_quests - set(by_id))
if spare:
    fail(f"{spare} have words in the generator and are not quests, so somebody wrote a "
         f"page for a step that was deleted")
spare_chapters = sorted(worded_chapters - set(chapter_after))
if spare_chapters:
    fail(f"{spare_chapters} have words in the generator and are not chapters")

# The generator reads the ids back out of the Java. If it ever stops doing
# that, the two halves drift apart silently and the check above becomes the
# check that a file agrees with itself.
reader = generator[generator.find("def ids("):]
reader = reader[:reader.find("\n\n\n")] if "\n\n\n" in reader else reader
if "AlmanacRules" not in read(GENERATOR)[:2000] or "re.findall" not in reader:
    fail("the generator does not read the quest ids out of the rules, so the words and "
         "the graph are two hand-kept lists and nothing notices when they disagree")

# A chapter's reason is a subtitle and a quest's reason is the guide itself, so
# they are held to different lengths. The quest one is the whole difference
# between this module and a to-do list: "craft a crusher" is a checklist entry,
# and a checklist is the thing a player who has just installed the pack already
# has and cannot read.
short = sorted(key for key, value in lang.items()
               if re.fullmatch(rf"{NS}\.why\.(?!chapter\.)[a-z_]+", key)
               and len(value) < 60)
if short:
    fail(f"{len(short)} quest reason(s) are under sixty characters ({short[:4]}); a line "
         f"that short can only restate the title, and restating the title is what the "
         f"recipe book already does")
thin = sorted(key for key, value in lang.items()
              if key.startswith(f"{NS}.why.chapter.") and len(value) < 20)
if thin:
    fail(f"{len(thin)} chapter subtitle(s) say almost nothing ({thin[:4]})")

for kind in kinds:
    if f"{NS}.task.{kind.lower()}" not in lang:
        fail(f"there is no line for a task of kind {kind}, so the step screen shows a key")
for key in (f"{NS}.reward", f"{NS}.reward.claim", f"{NS}.reward.claimed", f"{NS}.claimed"):
    if key not in lang:
        fail(f"there is no line {key}, so the reward row shows a key")
for _, value in names.get("visit", []):
    if f"{NS}.world.{value.replace(':', '.')}" not in lang and ":" in value:
        fail(f"the world {value} has no name in the book, so a VISIT step shows its id")

print(f"the words          : {len(lang)} key(s), a title and a reason for every quest "
      f"and every chapter, a line for every kind of task and every world, all of them "
      f"generated")

# ---------------------------------------------------------------------------
# 6. The book in the world
# ---------------------------------------------------------------------------

for where, what in (("assets/{}/textures/item/almanac.png", "a texture"),
                    ("assets/{}/models/item/almanac.json", "a model"),
                    ("data/{}/recipe/almanac.json", "a recipe")):
    if not os.path.isfile(os.path.join(RES, where.format(NS))):
        fail(f"the book has no {what}, so the one item this module adds cannot be "
             f"obtained or looked at")

recipe_path = os.path.join(RES, "data", NS, "recipe/almanac.json")
if os.path.isfile(recipe_path):
    recipe = json.load(open(recipe_path, encoding="utf-8"))
    ingredients = json.dumps(recipe.get("ingredients", []))
    if "minecraft:" not in ingredients or "elysium" in ingredients:
        fail("the book is not craftable out of the base game; a guide you need the pack "
             "to understand in order to craft is a guide for somebody who did not need "
             "it")

items_src = strip_comments(read(SRC, "registry/AlmanacItems.java"), True)
if 'ITEMS.register("almanac"' not in items_src:
    fail("the book is not registered as an item")

attach = read(SRC, "registry/AlmanacAttachments.java")
attach_plain = squashed(strip_comments(attach, True))

# Per attachment, not per file. There are two of them now — what a player has
# finished, and what they have killed — and a check that only asks whether the
# *file* mentions copyOnDeath passes happily while one of the two loses
# everything on death. Which is this module's own lesson from the arsenal,
# arriving a second time: the check was right about what it looked at.
ATTACHMENTS = {
    "DONE": "the finished list",
    "SLAIN": "the kill tally",
    "TALLY": "the event tally",
    "CLAIMED": "the paid list",
}
for field, called in sorted(ATTACHMENTS.items()):
    where = attach_plain.find(f"AttachmentType<List<String>>> {field} =")
    if where < 0:
        fail(f"there is no {field} attachment, so {called} is not kept at all")
        continue
    # Up to the next attachment, or the end.
    ends = [attach_plain.find(f"AttachmentType<List<String>>> {other} =")
            for other in ATTACHMENTS if other != field]
    after = [e for e in ends if e > where]
    block = attach_plain[where:min(after) if after else len(attach_plain)]
    for statement, why in (
            (".copyOnDeath()",
             "is lost when the player dies, which is not a difficulty setting"),
            (".sync(",
             "is never sent to the client, so the book a player opens is blank "
             "however much they have done"),
            (".serialize(Codec.STRING.listOf())",
             "is not written to disk, so it empties every time the server restarts")):
        if statement not in block:
            fail(f"{called} {why}")

finish_body = squashed(body_of(strip_comments(attach), "public static boolean finish("))
if "player.setData(DONE.get(), List.copyOf(now));" not in finish_body:
    fail("finishing a quest does not set the data back on the player, so the attachment "
         "is mutated in place and the sync never hears about it — the client's book "
         "stops updating and nothing looks wrong on the server")
if "was.add(" in finish_body:
    fail("finishing a quest adds to the list the player handed out rather than replacing "
         "it, which is the same bug wearing a different hat")

progress = read(SRC, "event/AlmanacProgress.java")
progress_plain = strip_comments(progress)
progress_squashed = squashed(progress_plain)
for event, why in (
        ("PlayerTickEvent.Post", "nothing ever notices that a player has an item"),
        ("BlockEvent.EntityPlaceEvent", "placing a block is never noticed, so every "
                                        "PLACE quest is impossible"),
        ("PlayerEvent.PlayerChangedDimensionEvent", "arriving somewhere is never "
                                                    "noticed, so the frontier cannot be "
                                                    "finished"),
        ("PlayerEvent.PlayerLoggedInEvent", "nobody is ever handed the book")):
    if event not in progress_plain:
        fail(f"there is no handler for {event}, so {why}")

if "if (player.level().isClientSide() || player.tickCount % LOOKS_EVERY != 0) { return; }" \
        not in progress_squashed:
    fail("the inventory sweep runs on the client, or runs every tick; one of those is a "
         "book that disagrees with the server and the other is forty inventory walks a "
         "second per player")

holds_body = squashed(body_of(progress_plain, "private static boolean holds("))
if "if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) { return false; }" \
        not in holds_body:
    fail("a task naming an item from a module that is not installed is not handled, so "
         "the book crashes on the one thing it was designed to survive")

given = re.search(r'GIVEN = "([^"]+)"', strip_comments(progress, True))
if not given:
    fail("there is no marker for having been handed the book, so every login hands out "
         "another one")
elif given.group(1) in by_id:
    fail(f"the marker {given.group(1)!r} is also a quest id, so being given the book "
         f"finishes a step of it")
elif re.fullmatch(r"[a-z_]+", given.group(1)):
    fail(f"the marker {given.group(1)!r} is spelled the way a quest id is, so the day "
         f"somebody adds a quest of that name the two silently become one")

screens = os.path.join(SRC, "client")
for name in ("ChapterScreen.java", "QuestScreen.java", "StepScreen.java"):
    body = strip_comments(read(screens, name))
    if "extends ElysiumChooser" not in body:
        fail(f"client/{name} does not extend the library's chooser, so the book looks "
             f"like a different mod from everything else in the pack")

item_body = squashed(strip_comments(read(SRC, "item/AlmanacItem.java")))
if "if (level.isClientSide() && FMLEnvironment.dist == Dist.CLIENT) {" not in item_body:
    fail("the screen is opened without checking the dist, so right-clicking the book on "
         "a dedicated server loads a class that is not there — a crash on the one "
         "machine nobody is watching")

print("the book           : craftable out of the base game, handed out once, kept "
      "through death, synced, and drawn on the library's own screens")

# ---------------------------------------------------------------------------
# 7. The seam
# ---------------------------------------------------------------------------

main_class = read(SRC, "ElysiumAlmanac.java")
if f'MODID = "{NS}"' not in main_class:
    fail("the module id is not what everything else in the tree spells it")
for registered in ("AlmanacAttachments.ATTACHMENTS.register",
                   "AlmanacItems.ITEMS.register"):
    if registered not in main_class:
        fail(f"{registered} is never called, so half the module is written and none of "
             f"it exists")

print("what it declares   : the game and the library, and not one thing it describes")

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all almanac checks passed")
