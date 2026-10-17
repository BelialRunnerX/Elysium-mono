"""
Runs: the assets every pipe, cable, wire, conduit, bus and channel shares.

A run block draws a core, an arm towards every face it touches, and a plate on
every face that touches a device (Dylan's cue that the connection is live).
The body is tinted white or the dye; the plate is tinted the run's own colour
or the dye. The numbers come off elysium-lib's RunRules.java, read here rather
than copied, so a plate cannot be one size in the rule and another on disk.

Every module that has a run calls write_all() from its generator and check()
from its validator, so six modules draw and check one thing.
"""
import json
import os
import pathlib
import re

from PIL import Image, ImageDraw

_HERE = pathlib.Path(__file__).resolve().parent
_ROOT = _HERE.parent
RULES = _ROOT / "elysium-lib/src/main/java/com/elysium/lib/run/RunRules.java"

SIDES = ("north", "east", "south", "west", "up", "down")
# how the north-facing arm and plate are turned for each side
TURNS = {"north": {}, "south": {"y": 180}, "west": {"y": 270}, "east": {"y": 90},
         "up": {"x": 270}, "down": {"x": 90}}
DYES = ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")


def rules():
    """The run numbers, off the Java."""
    text = RULES.read_text(encoding="utf-8")

    def const(name):
        m = re.search(r"int " + name + r" = (\d+);", text)
        if not m:
            raise SystemExit(f"RunRules.java no longer says {name}")
        return int(m.group(1))

    r = {k: const(k) for k in ("ARM_FROM", "ARM_TO", "PLATE", "PLATE_DEPTH", "BODY_TINT", "PLATE_TINT")}
    r["PLATE_FROM"] = (16 - r["PLATE"]) // 2
    r["PLATE_TO"] = r["PLATE_FROM"] + r["PLATE"]
    return r


def _faces(texture, tint, uv=None):
    face = {"texture": texture, "tintindex": tint}
    if uv:
        face = dict(face, uv=list(uv))
    return {side: dict(face) for side in SIDES}


def core_model(texture, particle=None):
    r = rules()
    a, b = r["ARM_FROM"], r["ARM_TO"]
    return {"parent": "block/block", "render_type": "minecraft:cutout",
            "textures": {"t": texture, "particle": particle or texture},
            "elements": [{"from": [a, a, a], "to": [b, b, b],
                          "faces": _faces("#t", r["BODY_TINT"], (a, a, b, b))}]}


def arm_model(texture, particle=None):
    """The arm towards north; the blockstate turns it for the other five."""
    r = rules()
    a, b = r["ARM_FROM"], r["ARM_TO"]
    return {"parent": "block/block", "render_type": "minecraft:cutout",
            "textures": {"t": texture, "particle": particle or texture},
            "elements": [{"from": [a, a, 0], "to": [b, b, a],
                          "faces": _faces("#t", r["BODY_TINT"], (a, 0, b, a))}]}


def plate_model(ns, name):
    """The plate against a device to the north; turned like the arm."""
    r = rules()
    f, t, d = r["PLATE_FROM"], r["PLATE_TO"], r["PLATE_DEPTH"]
    return {"parent": "block/block",
            "textures": {"p": f"{ns}:block/{name}_plate", "particle": f"{ns}:block/{name}_plate"},
            "elements": [{"from": [f, f, 0], "to": [t, t, d],
                          "faces": _faces("#p", r["PLATE_TINT"], (f, f, t, t))}]}


def inventory_model(ns, name):
    """A straight length, for the hand and the creative tab."""
    r = rules()
    a, b = r["ARM_FROM"], r["ARM_TO"]
    core = core_model(f"{ns}:block/{name}")
    return {"parent": "block/block", "render_type": "minecraft:cutout",
            "textures": core["textures"],
            "display": {"gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]},
                        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                                  "scale": [0.375, 0.375, 0.375]}},
            "elements": [{"from": [a, a, 0], "to": [b, b, 16],
                          "faces": _faces("#t", r["BODY_TINT"], (a, 0, b, 16))}]}


def blockstate(ns, name):
    parts = [{"apply": {"model": f"{ns}:block/{name}_core"}}]
    for side in SIDES:
        turn = TURNS[side]
        parts.append({"when": {"OR": [{side: "run"}, {side: "device"}]},
                      "apply": dict({"model": f"{ns}:block/{name}_arm"}, **turn)})
        parts.append({"when": {side: "device"},
                      "apply": dict({"model": f"{ns}:block/{name}_plate"}, **turn)})
    return {"multipart": parts}


def plate_sprite():
    """A flat, light plate the tint colours: a rim one pixel darker, a faint seam."""
    image = Image.new("RGBA", (16, 16), (216, 216, 216, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], outline=(168, 168, 168, 255))
    draw.rectangle([1, 1, 14, 14], outline=(236, 236, 236, 255))
    for x in range(3, 13, 3):
        draw.point((x, 8), fill=(200, 200, 200, 255))
    return image


def dye_recipes(ns, name):
    """Sixteen shapeless recipes: the run and a dye make the run wearing it."""
    out = {}
    for dye in DYES:
        out[f"{name}_dyed_{dye}"] = {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "group": f"{name}_dyed",
            "ingredients": [{"item": f"{ns}:{name}"}, {"item": f"minecraft:{dye}_dye"}],
            "result": {"id": f"{ns}:{name}", "count": 1,
                       "components": {"elysiumlib:run_dye": dye}},
        }
    return out


def write_all(ns, name, texture, assets, data, write):
    """
    Everything a run block needs on disk. `write(path, obj)` is the module's
    own JSON writer; the plate texture is saved directly.

    @param texture  the body's texture id, e.g. "elysiumspace:block/pipe"
    """
    write(os.path.join(assets, "models/block", name + "_core.json"), core_model(texture))
    write(os.path.join(assets, "models/block", name + "_arm.json"), arm_model(texture))
    write(os.path.join(assets, "models/block", name + "_plate.json"), plate_model(ns, name))
    write(os.path.join(assets, "models/block", name + "_inventory.json"), inventory_model(ns, name))
    # The block's own model name, for every check that asks "does this block
    # have a model": the same straight length the hand shows.
    write(os.path.join(assets, "models/block", name + ".json"), {"parent": f"{ns}:block/{name}_inventory"})
    write(os.path.join(assets, "blockstates", name + ".json"), blockstate(ns, name))
    write(os.path.join(assets, "models/item", name + ".json"), {"parent": f"{ns}:block/{name}_inventory"})
    os.makedirs(os.path.join(assets, "textures/block"), exist_ok=True)
    plate_sprite().save(os.path.join(assets, "textures/block", name + "_plate.png"))
    for rname, recipe in dye_recipes(ns, name).items():
        write(os.path.join(data, "recipe", rname + ".json"), recipe)


# ---------------------------------------------------------------------------
# The checks a module validator runs over one of its runs
# ---------------------------------------------------------------------------

LINK = _ROOT / "elysium-lib/src/main/java/com/elysium/lib/run/RunLink.java"


def links():
    """The values each side property takes, off RunLink.java (none, run, device)."""
    try:
        text = LINK.read_text(encoding="utf-8")
    except OSError:
        return ()
    return tuple(re.findall(r'^\s*[A-Z_]+\("([a-z_]+)"\)[,;]', text, re.M))


def _turned(box, x=0, y=0):
    """A model box as the blockstate turns it: x first, then y, in quarter turns."""
    points = [tuple(box[:3]), tuple(box[3:])]
    for _ in range((x // 90) % 4):
        points = [(px, pz, 16 - py) for px, py, pz in points]
    for _ in range((y // 90) % 4):
        points = [(16 - pz, py, px) for px, py, pz in points]
    lows = [min(a, b) for a, b in zip(*points)]
    highs = [max(a, b) for a, b in zip(*points)]
    return tuple(lows + highs)


def _bounds(model):
    """The box around every element of a model, or None for a model with none."""
    elements = model.get("elements", [])
    if not elements:
        return None
    lows = [min(e["from"][i] for e in elements) for i in range(3)]
    highs = [max(e["to"][i] for e in elements) for i in range(3)]
    return tuple(float(v) for v in lows + highs)


def declared_shapes(java):
    """
    The core and the arms a run's Java collides with: the two names its
    Runs.shape(state, CORE, ARMS) call passes, read back to their boxes, either
    a Map.of(Direction.X, box(...), ...) or one ARMS.put(Direction.X, box(...))
    per side. Returns (core box or None, {side: [boxes]}), or None when the
    block does not go through Runs.shape at all (reported on its own).
    """
    src = re.sub(r"/\*.*?\*/", " ", java, flags=re.S)
    src = re.sub(r"//[^\n]*", " ", src)
    call = re.search(r"Runs\.shape\(state, (\w+), (\w+)\)", src)
    if not call:
        return None
    core_name, arms_name = call.groups()

    def numbers(text):
        return tuple(float(n.strip().rstrip("DdFf")) for n in text.split(","))

    core = re.search(r"VoxelShape " + core_name + r" = (?:Block\.)?box\(([^)]*)\);", src)
    listed = re.search(r"\b" + arms_name + r" = Map\.of\((.*?)\);", src, re.S)
    entries = (listed.group(1) if listed else "") + " " + " ".join(
        re.findall(r"\b" + arms_name + r"\.put\((Direction\.[A-Z]+, (?:Block\.)?box\([^)]*\))\);", src))
    arms = {}
    for side, nums in re.findall(r"Direction\.([A-Z]+),\s*(?:Block\.)?box\(([^)]*)\)", entries):
        arms.setdefault(side.lower(), []).append(numbers(nums))
    return (numbers(core.group(1)) if core else None), arms


def _box(box):
    return "(" + ", ".join(f"{v:g}" for v in box) + ")"


def _conditions(when):
    """Every (property, value) a multipart case's when names, through OR and AND."""
    if isinstance(when, dict):
        for key, value in when.items():
            if key in ("OR", "AND") and isinstance(value, list):
                for inner in value:
                    yield from _conditions(inner)
            else:
                yield key, value


def _models(part):
    apply = part.get("apply", {})
    return [a.get("model") for a in apply] if isinstance(apply, list) else [apply.get("model")]


def check(ns, name, resources, java_sources):
    """
    Everything wrong with one run block: the blockstate must draw arms for
    both kinds of link and a plate for a device on every face, the plate and
    body must carry their tints, sixteen dye recipes must exist with the run
    dye component, and the block's Java must go through Runs for its links,
    its dye and its pick.

    @param resources     the module's src/main/resources
    @param java_sources  the text of the block's Java class(es), joined
    @return a list of problems, empty when clean
    """
    bad = []
    r = rules()
    assets = os.path.join(resources, "assets", ns)
    data = os.path.join(resources, "data", ns)
    state_path = os.path.join(assets, "blockstates", name + ".json")
    if not os.path.isfile(state_path):
        return [f"{name} has no blockstate"]
    state = json.load(open(state_path, encoding="utf-8"))
    parts = state.get("multipart")
    if not isinstance(parts, list):
        return [f"{name}'s blockstate is not multipart, so it cannot draw a plate per face"]
    for side in SIDES:
        arm = any(p.get("when") == {"OR": [{side: "run"}, {side: "device"}]}
                  and p.get("apply", {}).get("model") == f"{ns}:block/{name}_arm" for p in parts)
        plate = any(p.get("when") == {side: "device"}
                    and p.get("apply", {}).get("model") == f"{ns}:block/{name}_plate" for p in parts)
        if not arm:
            bad.append(f"{name} draws no arm towards {side} for a run and a device alike")
        if not plate:
            bad.append(f"{name} draws no plate towards a device to the {side}")
    for model, tint in ((name + "_core", r["BODY_TINT"]), (name + "_arm", r["BODY_TINT"]),
                        (name + "_plate", r["PLATE_TINT"])):
        path = os.path.join(assets, "models/block", model + ".json")
        if not os.path.isfile(path):
            bad.append(f"{name} has no {model} model")
            continue
        m = json.load(open(path, encoding="utf-8"))
        for element in m.get("elements", []):
            for face in element.get("faces", {}).values():
                if face.get("tintindex") != tint:
                    bad.append(f"{model} has a face without tint {tint}, so a dye would not colour it")
                    break
            if model.endswith("_plate"):
                if element["from"][:2] != [r["PLATE_FROM"], r["PLATE_FROM"]] \
                        or element["to"][:2] != [r["PLATE_TO"], r["PLATE_TO"]] \
                        or element["to"][2] != r["PLATE_DEPTH"]:
                    bad.append(f"{name}'s plate is not the {r['PLATE']}-pixel, "
                               f"{r['PLATE_DEPTH']}-deep square RunRules asks for")
    if not os.path.isfile(os.path.join(assets, "textures/block", name + "_plate.png")):
        bad.append(f"{name} has no plate texture")
    for dye in DYES:
        path = os.path.join(data, "recipe", f"{name}_dyed_{dye}.json")
        if not os.path.isfile(path):
            bad.append(f"{name} cannot be dyed {dye} at a crafting table")
            continue
        recipe = json.load(open(path, encoding="utf-8"))
        if recipe.get("result", {}).get("components", {}).get("elysiumlib:run_dye") != dye:
            bad.append(f"{name}'s {dye} recipe makes a run that does not wear the dye")
    for needed, why in (("Runs.define(builder);", "does not carry the six link properties"),
                        ("Runs.relink(", "does not read its links off the world by the rule"),
                        ("Runs.link(", "does not re-read a face when a neighbour changes"),
                        ("Runs.dye(", "cannot be dyed in place"),
                        ("Runs.clone(", "loses its dye when picked"),
                        ("Runs.placed(", "loses its dye when placed"),
                        ("Runs.shape(", "does not draw the arms it links"),
                        ("Runs.placedBlind(level, pos, state, this::device);", "placed by a command or a structure, links nothing"),
                        ("super(Runs.properties(properties));", "is not solid to the game, so water washes it away"),
                        ("public Runs.DeviceTest runDevice() {", "cannot be re-read by the library when its dye changes under it")):
        if needed not in java_sources:
            bad.append(f"{name}'s block {why} ({needed} is missing)")

    # The rules below were the solarpunk conduit's own until it became a run
    # (1d7d704d), when that validator handed its conduit to this check and
    # they were left behind; negtest_pipe.sh found them gone (17 October 2026).
    # They hold for every run, so they live here now.

    # Every state draws the core, and the cases name only what the block has.
    # A case naming a property the block has not got loads as the missing
    # model, and the log line for it scrolls past among the resource chatter.
    if not any("when" not in p and f"{ns}:block/{name}_core" in _models(p) for p in parts):
        bad.append(f"{name}'s multipart does not draw its core in every state; it wants a core and "
                   f"one arm per face, and a plate on each face against a device")
    values = links()
    if not values:
        bad.append(f"RunLink.java lists no links, so nothing can say which states {name} has")
    named = []
    for part in parts:
        for key, value in _conditions(part.get("when", {})):
            if (key, str(value)) in named:
                continue
            named.append((key, str(value)))
            if key not in SIDES or any(v not in values for v in str(value).split("|")):
                bad.append(f"{name}'s blockstate switches on {key}={value}, which the block has not got "
                           f"(Runs.define gives it {', '.join(SIDES)}, each {' or '.join(values)}); a case "
                           f"that names a property the block has not got renders as the missing-model cube")

    # The core draws all six faces: a side with no arm shows the core's face,
    # and a core without it is a hole.
    core_path = os.path.join(assets, "models/block", name + "_core.json")
    arm_path = os.path.join(assets, "models/block", name + "_arm.json")
    core_model_on_disk = json.load(open(core_path, encoding="utf-8")) if os.path.isfile(core_path) else None
    arm_model_on_disk = json.load(open(arm_path, encoding="utf-8")) if os.path.isfile(arm_path) else None
    for element in (core_model_on_disk or {}).get("elements", []):
        for side in SIDES:
            if side not in element.get("faces", {}):
                bad.append(f"{name}'s core is missing its {side} face, so a pipe with no arm on that side "
                           f"is see-through")

    # The run a player sees and the run a player bumps into are the same run.
    # The model is JSON the client draws and the VoxelShape is Java the server
    # collides against; nothing in Minecraft reconciles them, so a one-pixel
    # edit to either is a run a player can stand inside, or one that stops them
    # a pixel short of it, and neither throws. Each arm is worked out from the
    # one arm model on disk, turned the way the blockstate on disk turns it for
    # that side, so the check cannot agree with the Java by construction.
    shapes = declared_shapes(java_sources)
    if shapes is not None:
        core, arms = shapes
        if core is None or sorted(arms) != sorted(SIDES) or any(len(b) != 1 for b in arms.values()):
            bad.append(f"{name}'s block no longer declares a core and six arm shapes "
                       f"({sum(len(b) for b in arms.values())} arm(s) found); the {name} would collide "
                       f"as whatever is left")
        else:
            drawn_core = _bounds(core_model_on_disk) if core_model_on_disk else None
            if drawn_core is not None and core != drawn_core:
                bad.append(f"{name}'s collision core is {_box(core)} and its model's core is "
                           f"{_box(drawn_core)}: a player would stand inside the {name}, or stop short of it")
            drawn_arm = _bounds(arm_model_on_disk) if arm_model_on_disk else None
            for side in SIDES if drawn_arm is not None else ():
                turn = next((p.get("apply", {}) for p in parts
                             if p.get("when") == {"OR": [{side: "run"}, {side: "device"}]}
                             and isinstance(p.get("apply"), dict)), TURNS[side])
                want = _turned(drawn_arm, turn.get("x", 0), turn.get("y", 0))
                if arms[side][0] != want:
                    bad.append(f"{name}'s {side} arm collides at {_box(arms[side][0])} and the model draws "
                               f"it at {_box(want)}; the shape a player sees and the shape they walk into "
                               f"have drifted apart")
    return bad
