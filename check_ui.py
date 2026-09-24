#!/usr/bin/env python3
"""
One interface, across every module.

Dylan asked for "a unified gui theming carrying through Elysium space and
minecolonies apotheosis etc". The kit that does it — ElysiumPalette,
ElysiumUI, ElysiumButton, ElysiumChooser — has existed for a while, and most
screens use it. That is exactly the problem this file solves: a theme that
*most* screens follow is not a theme, it is a coincidence, and the way it
decays is always the same. Somebody writes a quick placeholder screen with
vanilla buttons and a hand-picked title colour, leaves a note in the javadoc
promising to retheme it later, and it ships.

Three screens in this project were in precisely that state, each with that
exact note. So the rule is mechanical from here: a screen is in the kit or the
build fails, and it stays that way through every module the conversion adds.

What is checked
---------------
1. Every screen draws through ElysiumUI, itself or through a base class in
   this tree. Nothing gets to draw its own chrome.
2. No vanilla widget chrome. Button, Checkbox and EditBox bring their own
   carved-stone textures and undo the palette in one line.
3. No GUI textures at all. The kit's premise is that everything is a fill on
   an integer boundary, so it is sharp at every GUI scale; one blit of a
   176x166 background texture and the screen is soft again.
4. No colour chosen by hand. Every colour comes from ElysiumPalette, which is
   generated from ui/palette.py, which measures its own contrast.
5. ElysiumPalette.java actually matches that generator — a hand-edit to the
   Java is a colour the previews never saw.
6. No English written into a screen. A screen is the most tempting place in a
   codebase to type a sentence instead of a translation key.
"""
import importlib.util
import os
import re
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
PALETTE_PY = os.path.join(ROOT, "elysium-lib", "ui", "palette.py")
PALETTE_JAVA = os.path.join(ROOT, "elysium-lib", "src", "main", "java", "com",
                            "elysium", "lib", "client", "ElysiumPalette.java")

problems = []


def fail(msg):
    problems.append(msg)


def strip_comments(text):
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"//[^\n]*", " ", text)


def modules():
    for name in sorted(os.listdir(ROOT)):
        path = os.path.join(ROOT, name, "src", "main", "java")
        if name.startswith("elysium-") and os.path.isdir(path):
            yield name, path


# ---------------------------------------------------------------------------
# Every java file in the tree, by the class it declares, so `extends` can be
# followed across modules.
# ---------------------------------------------------------------------------

sources = {}        # simple class name -> (module, path, source with comments gone)
surfaces = []       # the files that draw: anything under client/ or screen/

for module, java in modules():
    for dirpath, _, filenames in os.walk(java):
        for name in filenames:
            if not name.endswith(".java"):
                continue
            path = os.path.join(dirpath, name)
            body = strip_comments(open(path, encoding="utf-8").read())
            sources[name[:-5]] = (module, path, body)
            leaf = os.path.basename(dirpath)
            if leaf in ("client", "screen"):
                surfaces.append((module, path, body))

if not surfaces:
    print("no client sources found — is this the Elysium tree?")
    sys.exit(1)


def without_generics(text):
    """The same declaration with every <...> removed, nesting included."""
    out = []
    depth = 0
    for char in text:
        if char == "<":
            depth += 1
        elif char == ">":
            depth = max(0, depth - 1)
        elif depth == 0:
            out.append(char)
    return "".join(out)


def superclass(class_name):
    """
    The name of what a class in this tree extends, or None.

    The type parameters are cut out first, because
    {@code class Bench<T extends AbstractContainerMenu> extends ContainerScreen<T>}
    has two `extends` in it and the first one is not the answer. Reading it
    gave us a screen whose real base was never followed — which is to say a
    screen the whole file silently stopped checking, while still counting it.
    """
    if class_name not in sources:
        return None
    _, _, body = sources[class_name]
    start = re.search(r"\bclass\s+" + re.escape(class_name) + r"\b", body)
    if not start:
        return None
    header = body[start.start():]
    header = header[:header.find("{")] if "{" in header else header
    match = re.search(r"\bextends\s+(?:[\w.]*\.)?(\w+)", without_generics(header))
    return match.group(1) if match else None


def is_a_screen(class_name, seen=()):
    """
    Whether this class ends up being a Screen, however far up that is.

    Asked by walking the chain rather than by looking at the one word after
    `extends`, because the moment a base class exists the direct superclass is
    `ElysiumChooser` and a rule that matched on the name `...Screen` would stop
    seeing the very screens the base class was written for. A check that stops
    applying to the thing it was written about is worse than no check: it still
    prints a number, and the number goes up.
    """
    if class_name in seen:
        return False
    parent = superclass(class_name)
    if parent is None:
        return False
    if parent.endswith("Screen"):
        return True
    return is_a_screen(parent, seen + (class_name,))


def draws_in_the_kit(class_name, seen=()):
    """Whether this class or a base class in this tree calls the kit."""
    if class_name in seen or class_name not in sources:
        return False
    _, _, body = sources[class_name]
    if "ElysiumUI." in body:
        return True
    parent = superclass(class_name)
    if parent is None:
        return False
    return draws_in_the_kit(parent, seen + (class_name,))


# ---------------------------------------------------------------------------
# 1. Every screen is in the kit
# ---------------------------------------------------------------------------

screens = 0
for module, path, body in surfaces:
    name = os.path.basename(path)[:-5]
    if not re.search(r"\bclass\s+" + re.escape(name) + r"\b", body):
        continue
    if not is_a_screen(name):
        continue
    screens += 1
    if not draws_in_the_kit(name):
        fail(f"{module}: {name} is a screen that never reaches ElysiumUI, so it "
             f"draws chrome of its own and looks like a different mod")

# ---------------------------------------------------------------------------
# 2-3. No vanilla widgets, no textures
# ---------------------------------------------------------------------------

VANILLA_WIDGET = re.compile(r"\b(?:Button\.builder\(|new\s+Button\(|new\s+Checkbox\(|"
                            r"new\s+EditBox\(|Checkbox\.builder\()")
BLIT = re.compile(r"\.(?:blit|blitSprite|blitWithBorder)\(")

for module, path, body in surfaces:
    name = os.path.basename(path)
    if VANILLA_WIDGET.search(body):
        fail(f"{module}/{name}: a vanilla widget, which brings vanilla's button "
             f"texture with it — use ElysiumButton")
    if BLIT.search(body):
        fail(f"{module}/{name}: blits a texture, which is resampled at every GUI "
             f"scale above 1 — the kit draws, it does not blit")

# ---------------------------------------------------------------------------
# 3b. The chooser's filter, and the two ways a filter goes wrong
# ---------------------------------------------------------------------------
#
# A list that can be typed into has two failures that are invisible until
# somebody types something, which is to say invisible to whoever wrote it:
#
#   * the row you clicked is not the row you got, because the screen handed
#     back a position in the filtered page and the code behind it indexed its
#     own unfiltered list with it;
#   * the page index survives the filter, so narrowing to two rows while on
#     page nine shows an empty panel with "2 match(es)" written under it.
#
# Both live in ElysiumChooser rather than in any screen, so both are checked
# here once rather than in five validators that would each have to think of it.

CHOOSER = os.path.join(ROOT, "elysium-lib/src/main/java/com/elysium/lib/client/"
                             "ElysiumChooser.java")
if not os.path.isfile(CHOOSER):
    fail("ElysiumChooser is gone, and every chooser screen in the pack is built on it")
else:
    chooser = open(CHOOSER, encoding="utf-8").read()
    flat = " ".join(re.sub(r"//[^\n]*", " ", re.sub(r"/\*.*?\*/", " ", chooser,
                                                   flags=re.S)).split())
    if "int theirs = surviving.get(index);" not in flat:
        fail("ElysiumChooser: a pressed row no longer maps back through the filter, so "
             "a screen that is typed into acts on whatever row happens to sit at that "
             "position in its own unfiltered list")
    if "b -> chose(theirs)" not in flat:
        fail("ElysiumChooser: the index handed to chose() is not the one mapped back "
             "through the filter, so the mapping above is computed and thrown away")
    if "private void narrow(String now) { typed = now; first = 0;" not in flat:
        fail("ElysiumChooser: typing no longer returns to the first page, so narrowing "
             "a long list while paged through it shows an empty panel with a count "
             "under it saying otherwise")
    if "int head = ElysiumPalette.ROW + (statusShown() == null ? 0 : ElysiumPalette.ROW)" \
            not in flat:
        fail("ElysiumChooser: the panel is measured against a different line from the "
             "one drawn in it, so a chooser with no status of its own grows a row when "
             "typed into and draws it over its own border")

# ---------------------------------------------------------------------------
# 4. No colour chosen by hand
# ---------------------------------------------------------------------------
#
# A literal on a line that also masks or shifts is an opacity mask over a
# colour that came from somewhere else — a fluid tint, a dye — and not a
# colour anybody chose.

COLOUR = re.compile(r"0x[0-9A-Fa-f]{6,8}\b")
MASKING = re.compile(r"[&|]|<<|>>")

for module, path, body in surfaces:
    if os.path.basename(path) == "ElysiumPalette.java":
        continue
    for number, line in enumerate(body.splitlines(), start=1):
        if COLOUR.search(line) and not MASKING.search(line):
            fail(f"{module}/{os.path.basename(path)}:{number}: a colour written by "
                 f"hand — every colour belongs in ui/palette.py, where its contrast "
                 f"is measured")

# ---------------------------------------------------------------------------
# 5. The palette Java is what the generator produces
# ---------------------------------------------------------------------------

spec = importlib.util.spec_from_file_location("elysium_palette", PALETTE_PY)
palette = importlib.util.module_from_spec(spec)
spec.loader.exec_module(palette)

java = open(PALETTE_JAVA, encoding="utf-8").read()
declared = dict(re.findall(r"public static final int (\w+)\s*=\s*(-?0x[0-9A-Fa-f]+|-?\d+);", java))

for name, value in palette.PALETTE.items():
    if name not in declared:
        fail(f"palette: {name} is in ui/palette.py and not in ElysiumPalette.java — "
             f"the generator has not been re-run")
    elif int(declared[name], 0) != value:
        fail(f"palette: {name} is {declared[name]} in the Java and "
             f"{hex(value)} in the generator, so the previews were drawn from "
             f"different colours than the game uses")
for name, value in palette.METRICS.items():
    if name not in declared:
        fail(f"palette: metric {name} is missing from ElysiumPalette.java")
    elif int(declared[name], 0) != value:
        fail(f"palette: metric {name} disagrees with the generator")
for name in declared:
    if name not in palette.PALETTE and name not in palette.METRICS:
        fail(f"palette: {name} was added to ElysiumPalette.java by hand, so nothing "
             f"has ever measured it")

for name, surface, ratio, floor in palette.check_contrast():
    fail(f"palette: {name} on {surface} is {ratio}:1, below {floor} — that text "
         f"cannot be read on a dim monitor")

# ---------------------------------------------------------------------------
# 6. No English typed into a screen
# ---------------------------------------------------------------------------

# The literal's whole contents, not its first few characters. A screen is
# allowed "G" for a slot hint, "/ " in front of a number and " — " between two
# halves of a line; what it is not allowed is a word, and a word can start
# after a short one ("No other terminal yet" begins with two letters and a
# space, and an anchored pattern walks straight past it).
LITERAL = re.compile(r'Component\.literal\("((?:\\.|[^"\\])*)"')
WORD = re.compile(r"[A-Za-z]{3,}")

for module, path, body in surfaces:
    for text in LITERAL.findall(body):
        word = WORD.search(text)
        if word:
            fail(f"{module}/{os.path.basename(path)}: \"{text}\" is written into "
                 f"the screen rather than into the language file")
            break

# ---------------------------------------------------------------------------

if problems:
    print("one interface      : FAILED")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print(f"one interface      : {screens} screen(s) across {len(list(modules()))} module(s), "
      f"all in the kit; {len(palette.PALETTE)} colours and {len(palette.METRICS)} metrics "
      f"generated, measured and matching; no vanilla widgets, no blits, no hand-picked "
      f"colours, no English in a screen")
