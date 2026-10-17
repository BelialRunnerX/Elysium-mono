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

_NUM = r"\s*(-?\d+(?:\.\d+)?)[dDfF]?\s*"
_BOX = r"(?:Block\.)?box\(" + ",".join([_NUM] * 6) + r"\)"


def _turned(box, turn):
    """A box turned the way a blockstate turns a model: x first, then y, about the middle."""
    corners = [(box[0], box[1], box[2]), (box[3], box[4], box[5])]
    for _ in range(turn.get("x", 0) // 90):
        # x=90 takes north (-z) to down (-y)
        corners = [(x, z, 16 - y) for x, y, z in corners]
    for _ in range(turn.get("y", 0) // 90):
        # y=90 takes north (-z) to east (+x)
        corners = [(16 - z, y, x) for x, y, z in corners]
    (ax, ay, az), (bx, by, bz) = corners
    return (min(ax, bx), min(ay, by), min(az, bz), max(ax, bx), max(ay, by), max(az, bz))


def collision_boxes(java_sources):
    """
    The core and the six arm boxes the block's Java collides with, as written:
    `CORE = box(...)` and a `Direction.X, box(...)` pair for every face, in a
    Map.of or a put. Returns (core, {side: box}); core is None when unwritten.
    """
    text = re.sub(r"/\*.*?\*/|//[^\n]*", " ", java_sources, flags=re.S)
    core = re.search(r"\bCORE\s*=\s*" + _BOX, text)
    arms = {}
    for m in re.finditer(r"Direction\.([A-Z]+)\s*,\s*" + _BOX, text):
        arms[m.group(1).lower()] = tuple(float(v) for v in m.groups()[1:])
    return (tuple(float(v) for v in core.groups()) if core else None), arms


def _drawn(path):
    """The one element of a run model, as (x1, y1, z1, x2, y2, z2), or None."""
    if not os.path.isfile(path):
        return None  # check() reports the missing model itself
    elements = json.load(open(path, encoding="utf-8")).get("elements", [])
    if len(elements) != 1:
        return ()
    one = elements[0]
    return tuple(float(v) for v in list(one["from"]) + list(one["to"]))


def shape_problems(ns, name, resources, java_sources):
    """
    The box a player walks into against the one they see, face by face.

    The Java's collision boxes and the models art/runs.py writes are two files
    that cannot see each other; a core or an arm that differs between them is a
    run that can be hit where it cannot be seen, or the other way round. The
    arm model points north and the blockstate turns it, so each face's
    collision box is compared with the north arm turned the way TURNS turns it.
    """
    bad = []
    core, arms = collision_boxes(java_sources)
    if core is None or set(arms) != set(SIDES):
        return [f"{name}'s block no longer declares a core and six arm shapes "
                f"(CORE = box(...) and a Direction.X, box(...) per face; found "
                f"{'a' if core else 'no'} core and {len(arms)} arm(s)), so nothing "
                f"compares the shape it collides with to the shape it draws"]
    assets = os.path.join(resources, "assets", ns, "models/block")
    drawn_core = _drawn(os.path.join(assets, name + "_core.json"))
    drawn_arm = _drawn(os.path.join(assets, name + "_arm.json"))
    for model, drawn in ((name + "_core", drawn_core), (name + "_arm", drawn_arm)):
        if drawn == ():
            bad.append(f"{model} is not the one box a run's {model.rsplit('_', 1)[1]} "
                       f"is, so its collision box cannot be compared with it")
    if drawn_core and drawn_core != core:
        bad.append(f"{name}'s model and its collision box are different sizes: the "
                   f"core collides at {core} and is drawn at {drawn_core}, so it can be "
                   f"hit where it cannot be seen or the other way round")
    if drawn_arm:
        for side in SIDES:
            want = _turned(drawn_arm, TURNS[side])
            if arms[side] != want:
                bad.append(f"{name}'s model and its collision box are different sizes: "
                           f"the {side} arm collides at {arms[side]} and is drawn at "
                           f"{want}, so it can be hit where it cannot be seen or the "
                           f"other way round")
    return bad


def check(ns, name, resources, java_sources):
    """
    Everything wrong with one run block: the blockstate must draw arms for
    both kinds of link and a plate for a device on every face, the plate and
    body must carry their tints, the core and every arm must collide where
    they are drawn, sixteen dye recipes must exist with the run dye
    component, and the block's Java must go through Runs for its links, its
    dye and its pick.

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
    bad.extend(shape_problems(ns, name, resources, java_sources))
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
    return bad
