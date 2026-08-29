#!/usr/bin/env python3
"""
Find registry objects resolved before the registries exist.

    python3 check_lifecycle.py

---------------------------------------------------------------------------
The rule
---------------------------------------------------------------------------

A mod registers its content from its constructor, because that is the only
point early enough for the registry events to pick it up. But a DeferredHolder
has no value during construction — the events have not fired — so calling
get() on one there throws:

    NullPointerException: Trying to access unbound value:
    ResourceKey[minecraft:block / elysium:neutronium_ore]

That is a *lifecycle* rule. It has no compile-time shape at all: the call is
perfectly typed, the stub harness compiles it happily, and a real Gradle build
compiles it happily too. It fails only when the game runs, in the constructor,
taking the whole mod down. Two separate launches of this project died this way
(the other was a config read, guarded in core/validate.py).

So it is checked here instead.

---------------------------------------------------------------------------
What counts as "during construction"
---------------------------------------------------------------------------

Anything reachable from the mod's constructor by ordinary calls. Not anything
inside a lambda: `() -> new ItemStack(FOO.get())` is a body that runs later, at
loot time or on a creative tab build, and is the correct way to defer. That
distinction is the entire subtlety, and it is why this cannot be a grep — the
same text is fine in one place and fatal three lines away.

So lambda bodies are blanked out before the search, and calls are followed
transitively from the constructor through the mod's own static methods.

---------------------------------------------------------------------------
Precision
---------------------------------------------------------------------------

Deliberately biased toward false positives: it reports, a human checks. A miss
costs a five-minute launch cycle and a crash report; a false alarm costs a
glance. Anything genuinely fine can be added to ALLOWED below with a reason.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent

# (package tail, main class). The directory is found below, because this file
# runs both from the multi-repo work tree (lib/, core/, ...) and from inside the
# monorepo (elysium-lib/, elysium-core/, ...). Hard-coding one layout means it
# silently checks nothing in the other, which is the worst outcome for a
# checker: a green line and no coverage.
MOD_SHAPES = [
    ("lib", "lib", "ElysiumLib"),
    ("core", "core", "Elysium"),
    ("dungeons", "dungeons", "ElysiumDungeons"),
    ("mobs", "mobs", "ElysiumMobs"),
    ("trinkets", "trinkets", "ElysiumTrinkets"),
    ("npcs", "npcs", "ElysiumNpcs"),
]


def locate():
    """Every mod directory present, under either layout."""
    found = []
    for short, package, main_class in MOD_SHAPES:
        for directory in (ROOT / short, ROOT / f"elysium-{short}"):
            source = directory / f"src/main/java/com/elysium/{package}"
            if source.is_dir():
                found.append((directory.name, source, main_class))
                break
    return found


MODS = locate()

# (mod, "Class.method", "field") pairs that are genuinely safe, with a reason.
ALLOWED = {}

HOLDER_TYPES = ("DeferredHolder", "DeferredRegister", "Supplier", "Holder")


def strip_anonymous_classes(source):
    """
    Blank out `new Something(...) { ... }` bodies.

    An anonymous class body is deferred code for exactly the same reason a
    lambda is: its methods run when something calls them, not when the object
    is constructed. Registering a dispatcher whose create() resolves a
    DeferredHolder is correct and normal — create() is called at spawn time.

    Missing this reported ElysiumContent's two dispatchers as crashes on the
    first tightening pass. They were the reason for this function.
    """
    out = list(source)
    for match in re.finditer(r"\bnew\s+[\w.]+(?:<[^;{]*?>)?\s*\([^;{]*?\)\s*\{", source):
        i = match.end() - 1
        depth, j = 0, i
        while j < len(source):
            if source[j] == "{":
                depth += 1
            elif source[j] == "}":
                depth -= 1
                if depth == 0:
                    j += 1
                    break
            j += 1
        for k in range(i, min(j, len(source))):
            if out[k] != "\n":
                out[k] = " "
    return "".join(out)


def strip_lambdas(source):
    """
    Blank out every lambda body, so what remains is only code that runs now.

    A lambda body is either a braced block or a single expression. Both are
    replaced with spaces rather than deleted, so byte offsets — and therefore
    reported line numbers — stay correct.
    """
    out = list(source)
    for arrow in [m.end() for m in re.finditer(r"->", source)]:
        i = arrow
        while i < len(source) and source[i] in " \t\n\r":
            i += 1
        if i >= len(source):
            continue

        if source[i] == "{":
            depth = 0
            j = i
            while j < len(source):
                if source[j] == "{":
                    depth += 1
                elif source[j] == "}":
                    depth -= 1
                    if depth == 0:
                        j += 1
                        break
                j += 1
        else:
            # A single-expression body, ending at the , or ) or ; that closes it.
            depth = 0
            j = i
            while j < len(source):
                c = source[j]
                if c in "([{":
                    depth += 1
                elif c in ")]}":
                    if depth == 0:
                        break
                    depth -= 1
                elif c in ",;" and depth == 0:
                    break
                j += 1

        for k in range(i, min(j, len(source))):
            if out[k] != "\n":
                out[k] = " "
    return "".join(out)


def deferred_only(source):
    """Everything that runs *now* — lambda and anonymous-class bodies removed."""
    return strip_lambdas(strip_anonymous_classes(source))


def holder_fields(sources):
    """Every static field whose declared type looks like a deferred registry object."""
    fields = set()
    pattern = re.compile(
        r"\b(?:public|private|protected)?\s*static\s+final\s+([A-Za-z_][\w.<>?,\s\[\]]*?)\s+"
        r"([A-Z][A-Z0-9_]*)\s*=")
    for text in sources.values():
        for declared_type, name in pattern.findall(text):
            if any(h in declared_type for h in HOLDER_TYPES):
                fields.add(name)
    return fields


def method_bodies(text):
    """Method name -> list of body texts, for methods declared in one file."""
    bodies = {}
    pattern = re.compile(
        r"\b(?:public|private|protected)\s+(?:static\s+)?[\w<>\[\],.?\s]+?\s+"
        r"(\w+)\s*\([^)]*\)\s*(?:throws [\w.,\s]+)?\{")
    for match in pattern.finditer(text):
        start = match.end() - 1
        depth = 0
        i = start
        while i < len(text):
            if text[i] == "{":
                depth += 1
            elif text[i] == "}":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        bodies.setdefault(match.group(1), []).append(text[start:i])
    return bodies


def line_of(text, index):
    return text.count("\n", 0, index) + 1


def check(mod_name, source_dir, main_class):
    if not source_dir.exists():
        return []

    sources = {}
    for path in source_dir.rglob("*.java"):
        sources[path] = path.read_text(encoding="utf-8")

    fields = holder_fields(sources)
    if not fields:
        return []

    # Method bodies keyed by (file stem, method name).
    #
    # Keyed by file as well as name, because keying by name alone follows any
    # method that happens to share one: the constructor calls register(), and a
    # name-only graph then walks into ImperialEnforcer.register() and every
    # other register() in the mod. That over-approximation reported fifteen
    # false positives on the first run — mob equipment and datagen code that
    # runs nowhere near construction.
    per_file = {}
    for path, text in sources.items():
        for name, bodies in method_bodies(deferred_only(text)).items():
            for body in bodies:
                per_file.setdefault((path.stem, name), []).append((path, body))

    # Start from the mod constructor, follow calls transitively.
    main_path = source_dir / f"{main_class}.java"
    if not main_path.exists():
        return []
    main_text = deferred_only(sources[main_path])

    ctor = re.search(re.escape(main_class) + r"\s*\(\s*IEventBus[^)]*\)\s*\{", main_text)
    if not ctor:
        return []
    start = ctor.end() - 1
    depth, i = 0, start
    while i < len(main_text):
        if main_text[i] == "{":
            depth += 1
        elif main_text[i] == "}":
            depth -= 1
            if depth == 0:
                break
        i += 1
    reachable_bodies = [(main_path, main_text[start:i])]

    # Resolve calls the way Java does: Foo.bar() looks in Foo, and a bare bar()
    # looks in the file it was written in.
    seen = set()
    frontier = [(main_path, main_text[start:i])]
    while frontier:
        here, body = frontier.pop()
        qualified = set(re.findall(r"\b([A-Z]\w*)\s*\.\s*(\w+)\s*\(", body))
        bare = set(re.findall(r"(?<![.\w])(\w+)\s*\(", body))
        targets = {(cls, method) for cls, method in qualified}
        targets |= {(here.stem, method) for method in bare}

        for key in targets:
            if key in seen or key not in per_file:
                continue
            seen.add(key)
            for path, called_body in per_file[key]:
                reachable_bodies.append((path, called_body))
                frontier.append((path, called_body))

    problems = []
    problems += check_static_initialisers(mod_name, sources, fields)
    problems += check_vanilla_registry_lookups(mod_name, sources, reachable_bodies)
    for path, body in reachable_bodies:
        for field in fields:
            for match in re.finditer(r"\b" + re.escape(field) + r"\s*\.\s*get\s*\(\s*\)", body):
                key = (mod_name, path.name, field)
                if key in ALLOWED:
                    continue
                # Report against the file, not the extracted body, so the line
                # number is one a person can actually open.
                whole = deferred_only(sources[path])
                where = whole.find(body[:80])
                line = line_of(sources[path], where + match.start()) if where >= 0 else 0
                problems.append(
                    f"{mod_name}: {path.name}:{line} resolves {field}.get() during mod "
                    f"construction, before the registries are populated")
    return sorted(set(problems))



def check_static_initialisers(mod_name, sources, fields):
    """
    A static field initialised from a registry object.

    Worse than the constructor case, because a static initialiser runs on class
    load, which can be earlier still and is far harder to trace back: the stack
    trace names whatever happened to touch the class first.

    Lambda and anonymous-class bodies are stripped first, so the common and
    correct `static final Supplier<X> FOO = () -> BAR.get();` is not reported.
    """
    problems = []
    for path, text in sources.items():
        body = deferred_only(text)
        # Field declarations at class scope: `static final Type NAME = ...;`
        for match in re.finditer(
                r"\bstatic\s+(?:final\s+)?[\w<>\[\].,?\s]+\s+(\w+)\s*=\s*([^;]+);", body):
            name, initialiser = match.group(1), match.group(2)
            for field in fields:
                if re.search(r"\b" + re.escape(field) + r"\s*\.\s*get\s*\(\s*\)", initialiser):
                    problems.append(
                        f"{mod_name}: {path.name}:{line_of(text, match.start())} static field "
                        f"{name} is initialised from {field}.get(), which runs on class load "
                        f"— before the registries exist")
    return sorted(set(problems))


def check_vanilla_registry_lookups(mod_name, sources, reachable):
    """
    BuiltInRegistries consulted during construction.

    Vanilla's registries are populated before mods construct, so reading an
    *item* from one is usually fine — but tags are not loaded until a world
    does, and `getTag(...)` during construction silently returns empty rather
    than throwing. That is worse than a crash: the gear quietly does not exist
    and nothing says why.

    This is why ElysiumGearMaterial.isAvailable() is a runtime question and not
    a load-time one; the check exists so it stays that way.
    """
    problems = []
    for path, body in reachable:
        for match in re.finditer(r"BuiltInRegistries\.\w+\s*\.\s*getTag\s*\(", body):
            problems.append(
                f"{mod_name}: {path.name} reads a tag during mod construction; tags are not "
                f"loaded until a world is, so this silently sees an empty tag")
    return sorted(set(problems))


def main():
    problems = []
    for mod_name, source_dir, main_class in MODS:
        problems += check(mod_name, source_dir, main_class)

    if not MODS:
        print("lifecycle: no mod source directories found — check has no coverage")
        return 1

    if problems:
        print(f"{len(problems)} PROBLEM(S):")
        for problem in problems:
            print("  - " + problem)
        print("\nA DeferredHolder has no value during mod construction. Pass the holder "
              "itself\n(it is already a Supplier) and resolve it lazily, or move the call "
              "into a\nlambda or a later event.")
        return 1

    print(f"lifecycle           : {len(MODS)} mod(s), nothing resolved during construction")
    return 0


if __name__ == "__main__":
    sys.exit(main())
