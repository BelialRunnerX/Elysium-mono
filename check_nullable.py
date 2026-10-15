#!/usr/bin/env python3
"""
Nothing is called straight off something the game says may be null.

    python3 check_nullable.py

<h2>The bug this exists for</h2>

    BlockEntity entity = event.getSpawner().left().orElse(null);

``FinalizeSpawnEvent.getSpawner()`` is annotated ``@Nullable`` in the NeoForge
jar, and it is null for every mob the world spawns by itself — which is almost
all of them, and a few hundred of them during chunk generation. So a new world
stopped at 51% with a NullPointerException nobody could see coming from the
source, because the source looks exactly like the correct version.

Java has no opinion about this: the annotation is advisory and javac says
nothing. But the jar carries it, and the jar is right here.

<h2>The rule</h2>

For every event class this repository subscribes to, read its ``@Nullable``
no-argument methods out of the jar, and flag any **direct chain** off one of
them in a file that handles that event: ``event.getSpawner().left()``.

Assigning it to a local first is not flagged, which is the fix and is also the
only way to write the null check. So the rule is narrow, its fix is obvious,
and it cannot argue with anybody about whether a value is *really* null — the
jar already said.
"""
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "tools"))
import mcpath  # noqa: E402
CLASSPATH_FILE = os.path.join(mcpath.libs(), "cp.txt")

problems = []


def fail(msg):
    problems.append(msg)


def strip_comments(text):
    """
    Comments out, newlines kept.

    Kept because this file reports line numbers, and a strip that collapses a
    block comment to one space renumbers everything after it — the first run of
    this check pointed at a line eleven lines from the problem, which is worse
    than pointing at nothing.
    """
    def blank(match):
        return "\n" * match.group(0).count("\n")
    text = re.sub(r"/\*.*?\*/", blank, text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def stop(why):
    """
    No jars, no check, and that is a failure rather than a skip.

    From late September to 15 October this file split nothing and handed the
    POSIX cp.txt to javap whole, which on Windows finds no class at all, and
    it reported "against 0 event class(es) ... nothing chains off one" as a
    pass. Not one getter was read from any jar in all that time.
    """
    print("nullable           : FAILED")
    print("  - " + why + ", so no event's @Nullable getters can be read")
    sys.exit(1)


_unusable = mcpath.problem(CLASSPATH_FILE)
if _unusable:
    stop(_unusable)

CLASSPATH = mcpath.join(mcpath.classpath(CLASSPATH_FILE))

IMPORT = re.compile(r"^import\s+(?:static\s+)?([\w.]+);", re.M)
SUBSCRIBED = re.compile(r"@SubscribeEvent[^\n]*\n\s*(?:public\s+|private\s+|protected\s+|"
                        r"static\s+|final\s+)*void\s+\w+\s*\(\s*(?:final\s+)?([\w.]+)")
LAMBDA = re.compile(r"\.addListener\s*\(\s*(?:EventPriority\.\w+\s*,\s*)?"
                    r"\(\s*(?:final\s+)?([\w.]+)\s+\w+\s*\)")
METHOD_REF = re.compile(r"\.addListener\s*\(\s*(?:EventPriority\.\w+\s*,\s*)?([\w.]+)::(\w+)")


def resolve(name, body):
    """A type name as written to the name the JVM knows it by."""
    if name[:1].islower() and "." in name:
        parts = name.split(".")
        depth = next((i for i, part in enumerate(parts) if part[:1].isupper()), len(parts))
        if depth >= len(parts):
            return None
        return ".".join(parts[:depth] + ["$".join(parts[depth:])])
    head, *nested = name.split(".")
    for imported in IMPORT.findall(body):
        if imported.rsplit(".", 1)[-1] == head:
            return "$".join([imported] + nested)
    return None


_nullable = {}
#: fqcn -> javap -v's text for it, or None when the classpath does not have it.
_sections = {}
DECLARED = re.compile(r"\b(?:class|interface)\s+([\w.$]+)")


def _javap_v(names):
    """
    javap -v for many classes in one JVM, split back into one text per class.

    One JVM per class was most of this check's run time once it was finally
    reading a jar; the answers are the same either way.
    """
    wanted = sorted({n for n in names if n and n not in _sections})
    for start in range(0, len(wanted), 100):
        chunk = wanted[start:start + 100]
        result = subprocess.run(["javap", "-v", "-cp", CLASSPATH] + chunk,
                                capture_output=True, text=True)
        blocks, block = [], []
        for line in result.stdout.splitlines():
            if line.startswith("Classfile ") and block:
                blocks.append(block)
                block = []
            block.append(line)
        if block:
            blocks.append(block)
        for block in blocks:
            for line in block:
                if line[:1].isspace() or line.startswith(("Classfile ", "Compiled from")):
                    continue
                declared = DECLARED.search(line)
                if declared and declared.group(1) in chunk:
                    _sections.setdefault(declared.group(1), "\n".join(block))
                break
        for name in chunk:
            _sections.setdefault(name, None)


def _parent(text):
    parent = re.search(r"\bextends\s+([\w.$]+)", text) if text else None
    return parent.group(1) if parent else None


def prefetch(names):
    """Every class named, then every superclass of those, a level at a time."""
    wanted = {n for n in names if n}
    while wanted:
        _javap_v(wanted)
        wanted = {p for p in (_parent(_sections.get(n)) for n in wanted)
                  if p and p != "java.lang.Object" and p not in _sections}


def on_classpath(fqcn):
    _javap_v([fqcn])
    return _sections.get(fqcn) is not None


def nullable_getters(fqcn):
    """The no-argument methods this class declares as @Nullable, by name."""
    if fqcn in _nullable:
        return _nullable[fqcn]
    _javap_v([fqcn])
    stdout = _sections.get(fqcn) or ""
    found = set()
    current = None
    for line in stdout.splitlines():
        stripped = line.strip()
        signature = re.match(r"(?:public|protected)\s+[\w.$<>\[\], ?]+\s+(\w+)\(\);", stripped)
        if signature:
            current = signature.group(1)
            continue
        if stripped.startswith(("public ", "protected ", "private ")) and "(" in stripped:
            current = None                 # a method that takes arguments
            continue
        if current and stripped == "org.jetbrains.annotations.Nullable":
            found.add(current)
    # Superclasses carry most of an event's shape; walk one level up, which is
    # where getEntity() and friends live.
    parent = _parent(stdout)
    if parent and parent not in ("java.lang.Object", fqcn):
        found |= nullable_getters(parent)
    _nullable[fqcn] = found
    return found


files = []
for module in sorted(os.listdir(HERE)):
    java = os.path.join(HERE, module, "src", "main", "java")
    if not (module.startswith("elysium-") and os.path.isdir(java)):
        continue
    for dirpath, _, filenames in os.walk(java):
        for name in filenames:
            if name.endswith(".java"):
                path = os.path.join(dirpath, name)
                files.append((module, path,
                              strip_comments(open(path, encoding="utf-8").read())))

if not files:
    print("no sources found — is this the Elysium tree?")
    sys.exit(1)

by_name = {os.path.basename(path)[:-5]: body for _, path, body in files}

def body_after(text, brace_at):
    depth = 0
    for index in range(brace_at, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return brace_at, text[brace_at:index + 1]
    return brace_at, text[brace_at:]


#: Any method that takes an event, with the name it calls its parameter. The
#: handler is not the only place that matters — the call that actually threw
#: was in a private helper the handler passed the event to.
TAKES = re.compile(r"\b(?P<type>[\w.]*[A-Z][\w.$]*)\s+(?P<name>\w+)\s*[,)]")

handled = []
for module, path, body in files:
    events = set(SUBSCRIBED.findall(body)) | set(LAMBDA.findall(body))
    for owner, method in METHOD_REF.findall(body):
        source = by_name.get(owner, body)
        found = re.search(r"\bvoid\s+" + re.escape(method) + r"\s*\(\s*(?:final\s+)?([\w.]+)",
                          source)
        if found:
            events.add(found.group(1))
    if events:
        handled.append((module, path, body, events))

asked = {fqcn for _, _, body, events in handled for fqcn in
         (resolve(event, body) for event in events) if fqcn}
prefetch(asked)
#: The game's own event classes this tree handles, and which of them the jars had.
games = {fqcn for fqcn in asked if fqcn.startswith(("net.neoforged.", "net.minecraft."))}
read = {fqcn for fqcn in games if on_classpath(fqcn)}

checked = 0
classes = set()
for module, path, body, events in handled:
    # Each event's nullable getters are checked only against parameters
    # declared as THAT event. Pooling the names across a file and matching
    # them anywhere in it flags LivingDeathEvent.getEntity() — which is not
    # nullable — because some other handler in the same file takes an event
    # where it is. That is the failure mode that gets a check switched off.
    risky = {}
    for event in events:
        fqcn = resolve(event, body)
        if fqcn is None:
            continue
        got = nullable_getters(fqcn)
        if got:
            classes.add(fqcn)
            risky[event] = got

    for event, getters in risky.items():
        simple = event.split(".")[-1]
        pattern = re.compile(r"\b" + re.escape(event).replace(r"\.", r"\.")
                             + r"\s+(\w+)\s*[,)]")
        for declaration in pattern.finditer(body):
            name = declaration.group(1)
            opened = body.find("{", declaration.end())
            if opened < 0:
                continue
            start, scope = body_after(body, opened)
            for getter in sorted(getters):
                for match in re.finditer(
                        r"\b" + re.escape(name) + r"\." + re.escape(getter) + r"\(\)\s*\.",
                        scope):
                    checked += 1
                    line = body.count("\n", 0, start + match.start()) + 1
                    fail(f"{os.path.relpath(path, HERE)}:{line}: calls straight off "
                         f"{name}.{getter}() on a {simple}, which the jar declares "
                         f"@Nullable — put it in a local and check it, or the first "
                         f"time the game passes null the world stops where it is")

# What was actually asked, made a condition of passing. "Against 0 event
# class(es)" was this check's whole output for three weeks, and it passed.
for fqcn in sorted(games - read):
    fail(f"the tree handles {fqcn} and javap cannot find it on the classpath in "
         f"{CLASSPATH_FILE}: the name was misread or the classpath is not the game's")
if games and read and not classes:
    fail(f"{len(read)} of the game's event classes read from the jars and not one "
         f"@Nullable getter among them, when FinalizeSpawnEvent.getSpawner() alone "
         f"is one: the annotations are not being read, so nothing here was checked")

if problems:
    print("nullable           : FAILED")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print(f"nullable           : {len(files)} source file(s) against {len(classes)} event "
      f"class(es) the jars mark as returning null, and nothing chains off one")
