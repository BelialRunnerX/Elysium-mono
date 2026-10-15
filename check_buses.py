#!/usr/bin/env python3
"""
Every listener is on the bus its event is actually fired on.

    python3 check_buses.py

<h2>The bug this exists for</h2>

NeoForge has two event buses. The mod bus carries the events fired while mods
are being built — registration, client setup, payload handlers — and the game
bus carries everything that happens afterwards. A listener on the wrong one is
not a subtle misbehaviour: the mod bus *rejects* an event that does not
implement {@code IModBusEvent}, throwing from the mod's constructor, and the
game does not reach the title screen.

None of that is visible to a compiler. Both buses take a {@code Consumer<T>},
so both calls type-check perfectly, and the difference is a marker interface
checked at runtime. We shipped a jar with

    modBus.addListener(ElysiumPotions::onBrewing);

in it, where {@code RegisterBrewingRecipesEvent} is a game-bus event, and the
first thing it did on a real machine was crash the whole load. Fifteen module
validators and a full compile had nothing to say about it, because neither of
them can read a jar.

This one can. It resolves every listener in the tree to the event class it
listens for, and asks the actual NeoForge jars on the classpath whether that
class implements IModBusEvent — the same question the game asks at startup,
asked before the build instead of after it.
"""
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "tools"))
import mcpath  # noqa: E402
CLASSPATH_FILE = os.path.join(mcpath.libs(), "cp.txt")
MARKER = "net.neoforged.fml.event.IModBusEvent"

problems = []


def fail(msg):
    problems.append(msg)


def strip_comments(text):
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"//[^\n]*", " ", text)
    # A fully qualified type wrapped across a line is still one name. Without
    # this the regex reads "net.neoforged.neoforge.event.entity.player" as the
    # event and asks the jars about a package.
    text = re.sub(r"\.\s*\n\s*", ".", text)
    return re.sub(r"\s*\n\s*\.", ".", text)


# ---------------------------------------------------------------------------
# Asking the jars
# ---------------------------------------------------------------------------

def stop(why):
    """
    No jars, no check, and that is a failure rather than a skip.

    From late September to 15 October this file split cp.txt on ":" itself,
    which on Windows turns "C:/..." into two entries that do not exist, and
    reported "0 listener(s) checked ... (277 not in the jars, skipped)" as a
    pass. Nothing was asked of any jar in all that time.
    """
    print("buses              : FAILED")
    print("  - " + why + ", so not one listener's bus can be checked")
    sys.exit(1)


_unusable = mcpath.problem(CLASSPATH_FILE)
if _unusable:
    stop(_unusable)

CLASSPATH = mcpath.join(mcpath.classpath(CLASSPATH_FILE))
_known = {}


def _javap(fqcn):
    """The first line of a class's declaration, or None if it is not there."""
    if fqcn in _known:
        return _known[fqcn]
    result = subprocess.run(["javap", "-cp", CLASSPATH, fqcn],
                            capture_output=True, text=True)
    line = None
    for text in result.stdout.splitlines():
        if " class " in text or " interface " in text:
            line = text
            break
    _known[fqcn] = line
    return line


DECLARED = re.compile(r"\b(?:class|interface)\s+([\w.$]+)")


def _prefetch(names):
    """
    Ask for many classes in one javap, and their superclasses after them.

    The same answers _javap gives one at a time; one JVM per class made this
    check take two and a half minutes once it was finally asking the jars
    anything, which is long enough for nobody to run it.
    """
    wanted = sorted({n for n in names if n and n not in _known})
    while wanted:
        for start in range(0, len(wanted), 100):
            chunk = wanted[start:start + 100]
            result = subprocess.run(["javap", "-cp", CLASSPATH] + chunk,
                                    capture_output=True, text=True)
            for text in result.stdout.splitlines():
                declared = DECLARED.search(text)
                if declared and declared.group(1) in chunk and declared.group(1) not in _known:
                    _known[declared.group(1)] = text
            for name in chunk:
                _known.setdefault(name, None)
        parents = set()
        for name in wanted:
            line = _known.get(name)
            parent = re.search(r"\bextends\s+([\w.$]+)", line) if line else None
            if parent and parent.group(1) != "java.lang.Object":
                parents.add(parent.group(1))
        wanted = sorted(parents - set(_known))


def on_mod_bus(fqcn, seen=()):
    """Whether this event class implements IModBusEvent, inherited or not."""
    if fqcn in seen:
        return False
    line = _javap(fqcn)
    if line is None:
        return None                       # not on the classpath; caller decides
    if MARKER in line:
        return True
    parent = re.search(r"\bextends\s+([\w.$]+)", line)
    if not parent or parent.group(1) == "java.lang.Object":
        return False
    return on_mod_bus(parent.group(1), seen + (fqcn,))


# The one class every answer below depends on. A classpath that has files on it
# and not this one (the wrong cp.txt, or the right one handed over in a form
# javap cannot read) answers "not in the jars" for every event in the tree.
if _javap(MARKER) is None:
    stop(f"javap cannot find {MARKER} on the classpath in {CLASSPATH_FILE}")


# ---------------------------------------------------------------------------
# Reading the tree
# ---------------------------------------------------------------------------

IMPORT = re.compile(r"^import\s+(?:static\s+)?([\w.]+);", re.M)
SUBSCRIBER = re.compile(r"@EventBusSubscriber\s*\(([^)]*)\)")
SUBSCRIBED = re.compile(r"@SubscribeEvent[^\n]*\n\s*(?:public\s+|private\s+|protected\s+|"
                        r"static\s+|final\s+)*void\s+\w+\s*\(\s*(?:final\s+)?([\w.]+)")


def annotating(body, position):
    """
    The @EventBusSubscriber that governs a method at this offset.

    Nearest preceding, not first in the file. One class here holds two
    subscriber classes, one on each bus — a key-mapping registration on the
    mod bus and a client tick on the game bus — which is perfectly correct and
    which the first version of this check read as a single file-wide bus,
    reporting the tick as a crash that was not there. A rule that cries wolf
    is a rule somebody switches off, and the file it was protecting goes with
    it.
    """
    governing = None
    for match in SUBSCRIBER.finditer(body):
        if match.start() < position:
            governing = match
        else:
            break
    return governing
BUS_FIELD = re.compile(r"\bIEventBus\s+(\w+)")
GAME_ALIAS = re.compile(r"\b(\w+)\s*=\s*NeoForge\.EVENT_BUS\b")
LISTENER = re.compile(r"\b([\w.]+)\.addListener\s*\(\s*(?:EventPriority\.\w+\s*,\s*)?"
                      r"(?:(?:final\s+)?\(\s*(?:final\s+)?([\w.]+)\s+\w+\s*\)|([\w.]+)::(\w+))")

files = {}
for module in sorted(os.listdir(HERE)):
    java = os.path.join(HERE, module, "src", "main", "java")
    if not (module.startswith("elysium-") and os.path.isdir(java)):
        continue
    for dirpath, _, filenames in os.walk(java):
        for name in filenames:
            if name.endswith(".java"):
                path = os.path.join(dirpath, name)
                files[name[:-5]] = (module, path,
                                    strip_comments(open(path, encoding="utf-8").read()))

if not files:
    print("no sources found — is this the Elysium tree?")
    sys.exit(1)


def resolve(name, body):
    """
    A type name as written to the name the JVM knows it by.

    Nested events are the whole reason this is more than a dictionary lookup.
    Most of NeoForge's game-bus events are inner classes — PlayerTickEvent.Post,
    BlockEvent.BreakEvent, LivingDamageEvent.Post — imported by their outer
    class and written with a dot. Matching the last segment of an import
    against "PlayerTickEvent.Post" finds nothing, and a resolver that returns
    nothing makes this file skip the listener silently. Seventeen of them, in
    the first version of this check: the most common event in the codebase,
    quietly unexamined, by a check written to stop exactly that.
    """
    if name[:1].islower() and "." in name:
        parts = name.split(".")
        depth = _package_depth(name)
        if depth >= len(parts):
            return None                   # all lower case: a package, not a type
        return ".".join(parts[:depth] + ["$".join(parts[depth:])])
    head, *nested = name.split(".")
    for imported in IMPORT.findall(body):
        if imported.rsplit(".", 1)[-1] == head:
            return "$".join([imported] + nested)
    return None


def _package_depth(name):
    """How many leading dotted segments of a written-out name are the package."""
    parts = name.split(".")
    for index, part in enumerate(parts):
        if part[:1].isupper():
            return index
    return len(parts)


def first_parameter(owner, method):
    """The declared parameter type of {@code owner.method}, wherever it lives."""
    if owner not in files:
        return None, None
    _, _, body = files[owner]
    match = re.search(r"\bvoid\s+" + re.escape(method) +
                      r"\s*\(\s*(?:final\s+)?([\w.]+)", body)
    return (match.group(1) if match else None), body


# ---------------------------------------------------------------------------
# Every listener, and the bus it was put on
# ---------------------------------------------------------------------------

listeners = []            # (module, file, event simple name, body, bus, where)

for name, (module, path, body) in sorted(files.items()):
    # 1. Annotated subscribers. The annotation says which bus; no argument
    #    means the game bus, which is NeoForge's own default.
    for match in SUBSCRIBED.finditer(body):
        subscriber = annotating(body, match.start())
        if subscriber is None:
            continue                      # registered by hand, if at all
        bus = "mod" if "Bus.MOD" in subscriber.group(1) else "game"
        listeners.append((module, name, match.group(1), body, bus, "@SubscribeEvent"))

    # 2. Direct registrations. The receiver decides: NeoForge.EVENT_BUS is the
    #    game bus, and anything the file declares as an IEventBus is the mod
    #    bus it was handed in its constructor — unless it was assigned from
    #    NeoForge.EVENT_BUS, which some mods do keep in a field.
    buses = set(BUS_FIELD.findall(body))
    aliases = set(GAME_ALIAS.findall(body))
    for receiver, lambda_type, owner, method in LISTENER.findall(body):
        if receiver.endswith("NeoForge.EVENT_BUS") or receiver == "EVENT_BUS":
            bus = "game"
        elif receiver in aliases:
            bus = "game"
        elif receiver in buses:
            bus = "mod"
        else:
            continue                      # not a bus: AddReloadListenerEvent and friends
        if lambda_type:
            listeners.append((module, name, lambda_type, body, bus, "addListener"))
            continue
        event, owner_body = first_parameter(owner if owner in files else name, method)
        if event is None:
            fail(f"{module}/{name}: cannot see what {owner}::{method} listens for, "
                 f"so nothing can check which bus it belongs on")
            continue
        listeners.append((module, name, event, owner_body, bus, "addListener"))

_prefetch(resolve(event, body) for _, _, event, body, _, _ in listeners)

checked = 0
unresolved = 0
missing = []
for module, name, event, body, bus, how in listeners:
    fqcn = resolve(event, body)
    if fqcn is None:
        unresolved += 1
        continue                          # our own type, or same-package: not a jar event
    mod_bus = on_mod_bus(fqcn)
    if mod_bus is None:
        unresolved += 1
        if fqcn.startswith(("net.neoforged.", "net.minecraft.")):
            # The game's own event, which the jars that answered for
            # IModBusEvent above cannot find: the resolver misread the name,
            # or the classpath is not the game's. Neither is a skip.
            missing.append(f"{module}/{name}: {event} resolves to {fqcn}, which "
                           f"is not on the classpath in {CLASSPATH_FILE}")
        continue                          # a compat jar that is not on this classpath
    checked += 1
    if mod_bus and bus == "game":
        fail(f"{module}/{name}: {event} is a mod-bus event ({how} put it on the "
             f"game bus), so it will never fire")
    if not mod_bus and bus == "mod":
        fail(f"{module}/{name}: {event} is a game-bus event and {how} put it on "
             f"the mod bus — NeoForge refuses that at construction and the game "
             f"crashes before the title screen")

for line in missing:
    fail(line)
if listeners and not checked:
    fail(f"{len(listeners)} listener(s) found and not one of them checked against "
         f"the jars in {CLASSPATH_FILE}: a check that asks nothing must not pass")

if problems:
    print("buses              : FAILED")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print(f"buses              : {checked} listener(s) checked against the NeoForge jars, "
      f"every one on the bus its event is fired on"
      + (f" ({unresolved} not in the jars, skipped)" if unresolved else ""))
