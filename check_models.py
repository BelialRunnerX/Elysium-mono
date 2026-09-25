#!/usr/bin/env python3
"""
Does every model, blockstate and texture reference land on a file, and does
every block that needs a render layer say which?

    python3 check_models.py

A blockstate names models, a model names a parent and textures, and the game
resolves all of it at resource reload — where a miss is one WARN line among
thousands ("missing model for variant", "Missing textures in model", "Unable
to load model") and a purple-and-black block or item in the hand. Nothing
fails, nothing is logged again, and the per-module validators check the
piece they know about (a block's own model, a crop's own texture) and not the
web between the pieces.

This is at the root rather than in each module's validate.py because the web
crosses modules — a blockstate here, a parent model there, a vanilla texture
under it — and because it is one rule applied identically twenty-nine times.
It reads every module's assets and the game's own (the assets-only copy of
the client jar that check_loadtest uses), and follows each reference:

  * blockstates: every `model` under `variants` and `multipart[].apply`;
  * models: `parent`, every `textures` value that is not a `#slot`, and every
    `overrides[].model`;
  * a reference into a namespace this repository does not ship and the game
    does not either (another mod's) is counted and not followed.

Then the render layer. A modded block's model draws on the solid layer unless
the model, or a parent of it, names a `render_type`: vanilla's crops, saplings
and glass are cut out by a table the game keeps for its own blocks, and ours
are not in it. So a block whose texture has one transparent pixel and no
render type shows that pixel black in the world — and looks right in the hand,
where items are always cut out, which is why loomflax reached a player before
anybody here saw it. Every model a blockstate reaches is followed to its
textures: a hole (alpha 0) wants `cutout` (leaves, `cutout_mipped`), a
see-through pixel (alpha between) wants `translucent`, and either without a
declared type is a failure that names the block.

The kinetics sail shipped a blockstate whose variants the block does not have,
and the flora door an item model over a texture nobody generated; both were
found by placing blocks in a running game, which is the slow way.
"""
import io
import json
import os
import pathlib
import sys
import zipfile

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "tools"))
import mcpath  # noqa: E402

# Beside cp.txt, as check_loadtest expects it — and failing that where the
# POSIX layout keeps it, because ELYSIUM_MC_LIBS on Windows points at a folder
# that holds only the re-spelt classpath.
VANILLA = next((path for path in (os.path.join(mcpath.libs(), "libs", "vanilla-assets.jar"),
                                  "/tmp/mc/libs/vanilla-assets.jar",
                                  "C:/tmp/mc/libs/vanilla-assets.jar")
                if os.path.isfile(path)),
               os.path.join(mcpath.libs(), "libs", "vanilla-assets.jar"))

problems = []
checks = 0


def fail(message):
    problems.append(message)


def check(condition, message):
    global checks
    checks += 1
    if not condition:
        fail(message)


# ---------------------------------------------------------------------------
# What is shipped
# ---------------------------------------------------------------------------

shipped = {}             # "ns:models/block/x.json" -> path on disk
ours = set()             # namespaces this repository ships assets under
modules = []
for module in sorted(HERE.glob("elysium-*")):
    assets = module / "src" / "main" / "resources" / "assets"
    if not assets.is_dir():
        continue
    modules.append(module.name)
    for namespace in sorted(p.name for p in assets.iterdir() if p.is_dir()):
        ours.add(namespace)
        root = assets / namespace
        for path in root.rglob("*"):
            if path.is_file():
                shipped[f"{namespace}:{path.relative_to(root).as_posix()}"] = path

vanilla = {}             # "ns:models/item/generated.json" -> name inside the jar
jar = None
if os.path.isfile(VANILLA):
    jar = zipfile.ZipFile(VANILLA)
    for name in jar.namelist():
        if name.startswith("assets/") and name.count("/") >= 2 and not name.endswith("/"):
            _, namespace, rest = name.split("/", 2)
            vanilla[f"{namespace}:{rest}"] = name
else:
    fail(f"no vanilla assets at {VANILLA}, so references into minecraft: cannot be followed "
         f"(check_loadtest.py explains how the jar is made)")

known = ours | {"minecraft"}


def exists(key):
    return key in shipped or key in vanilla


def read_bytes(key):
    if key in shipped:
        return shipped[key].read_bytes()
    if key in vanilla and jar is not None:
        return jar.read(vanilla[key])
    return None


def split(reference):
    if ":" in reference:
        return reference.split(":", 1)
    return "minecraft", reference


external = 0


def follow(kind, reference, where):
    """One reference. kind is 'model' or 'texture'."""
    global external
    namespace, path = split(reference)
    if namespace not in known:
        external += 1
        return
    if kind == "model":
        if path.startswith("builtin/"):
            return
        key = f"{namespace}:models/{path}.json"
        check(exists(key), f"{where} names model {reference} and no such model is shipped "
                           f"(the game draws the missing model there)")
    else:
        key = f"{namespace}:textures/{path}.png"
        check(exists(key), f"{where} names texture {reference} and no such texture is "
                           f"shipped (the game draws purple and black there)")


def load(module, key):
    try:
        with open(shipped[key], encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, ValueError, KeyError) as error:
        fail(f"{module}: {key} is not JSON the game can read: {error}")
        return None


# ---------------------------------------------------------------------------
# Blockstates and models
# ---------------------------------------------------------------------------

blockstates = 0
models = 0
reached = []             # (module, blockstate name, model reference) for the render pass
for module in modules:
    assets = HERE / module / "src" / "main" / "resources" / "assets"
    for namespace in sorted(p.name for p in assets.iterdir() if p.is_dir()):
        root = assets / namespace
        for path in sorted((root / "blockstates").glob("*.json")):
            blockstates += 1
            where = f"{module}: blockstates/{path.name}"
            state = load(module, f"{namespace}:blockstates/{path.name}")
            if state is None:
                continue
            entries = []
            for variant in state.get("variants", {}).values():
                entries.extend(variant if isinstance(variant, list) else [variant])
            for part in state.get("multipart", []):
                apply = part.get("apply", {})
                entries.extend(apply if isinstance(apply, list) else [apply])
            check(bool(entries), f"{where} has no variants and no multipart, so every "
                                 f"state of the block is the missing model")
            seen = set()
            for entry in entries:
                if "model" in entry:
                    follow("model", entry["model"], where)
                    if entry["model"] not in seen:
                        seen.add(entry["model"])
                        reached.append((module, path.name[:-5], entry["model"]))
                else:
                    fail(f"{where} has a variant with no model")

        for path in sorted((root / "models").rglob("*.json")):
            models += 1
            rel = path.relative_to(root).as_posix()
            where = f"{module}: {rel}"
            model = load(module, f"{namespace}:{rel}")
            if model is None:
                continue
            if "parent" in model:
                follow("model", model["parent"], where)
            for slot, texture in model.get("textures", {}).items():
                if isinstance(texture, str) and not texture.startswith("#"):
                    follow("texture", texture, f"{where} ({slot})")
            for override in model.get("overrides", []):
                if "model" in override:
                    follow("model", override["model"], where)

print(f"shipped            : {len(modules)} module(s), {len(shipped)} asset file(s) under "
      f"{len(ours)} namespace(s); {len(vanilla)} vanilla file(s) to fall back on")
print(f"followed           : {blockstates} blockstate(s), {models} model(s), {checks} "
      f"reference(s) each landing on a file; {external} into other mods, not followed")

# ---------------------------------------------------------------------------
# Render types
# ---------------------------------------------------------------------------

try:
    from PIL import Image
except ImportError:
    Image = None
    fail("Pillow is not installed, so no model can be checked against its textures for a "
         "render type")

_models = {}


def model_of(reference):
    """The parsed model a reference names, ours or the game's; None when neither has it."""
    namespace, path = split(reference)
    key = f"{namespace}:models/{path}.json"
    if key not in _models:
        data = read_bytes(key)
        try:
            _models[key] = None if data is None else json.loads(data.decode("utf-8"))
        except ValueError:
            _models[key] = None
    return _models[key]


def resolved(reference):
    """
    (render_type or None, {slot: texture}, elements) down the parent chain.

    Children win: the first render_type met, the first binding of a texture
    slot, and the nearest model that has elements at all — which is how the
    game reads a chain too.
    """
    render_type, textures, elements = None, {}, None
    seen = []
    while reference and reference not in seen and len(seen) < 16:
        seen.append(reference)
        model = model_of(reference)
        if model is None:
            break
        if "render_type" in model and render_type is None:
            render_type = model["render_type"]
        for slot, texture in model.get("textures", {}).items():
            textures.setdefault(slot, texture)
        if elements is None and "elements" in model:
            elements = model["elements"]
        reference = model.get("parent")
    return render_type, textures, elements or []


_images = {}


def image_of(reference):
    """The first frame of a texture as RGBA, or None."""
    namespace, path = split(reference)
    key = f"{namespace}:textures/{path}.png"
    if key not in _images:
        image = None
        data = read_bytes(key)
        if data is not None and Image is not None:
            try:
                with Image.open(io.BytesIO(data)) as opened:
                    image = opened.convert("RGBA")
                if image.height > image.width:
                    image = image.crop((0, 0, image.width, image.width))
            except Exception:
                image = None
        _images[key] = image
    return _images[key]


def default_uv(face, start, end):
    """Where a face reads its texture when the model does not say: vanilla's rule."""
    x0, y0, z0 = start
    x1, y1, z1 = end
    return {
        "down": (x0, 16 - z1, x1, 16 - z0),
        "up": (x0, z0, x1, z1),
        "north": (16 - x1, 16 - y1, 16 - x0, 16 - y0),
        "south": (x0, 16 - y1, x1, 16 - y0),
        "west": (z0, 16 - y1, z1, 16 - y0),
        "east": (16 - z1, 16 - y1, 16 - z0, 16 - y0),
    }[face]


def alpha_under(image, uv):
    """The alpha values a face draws: the texture pixels inside its uv rectangle."""
    scale = image.width / 16.0
    u0, v0, u1, v1 = uv
    left, right = sorted((u0, u1))
    top, bottom = sorted((v0, v1))
    box = (int(left * scale), int(top * scale),
           max(int(left * scale) + 1, int(-(-right * scale // 1))),
           max(int(top * scale) + 1, int(-(-bottom * scale // 1))))
    box = (max(0, box[0]), max(0, box[1]), min(image.width, box[2]), min(image.height, box[3]))
    if box[2] <= box[0] or box[3] <= box[1]:
        return set()
    return set(image.crop(box).getchannel("A").tobytes())


def drawn_alpha(textures, elements):
    """Every alpha value some face of the model puts on screen."""
    values = set()
    for element in elements:
        start = element.get("from", [0, 0, 0])
        end = element.get("to", [16, 16, 16])
        for face, spec in element.get("faces", {}).items():
            if face not in ("down", "up", "north", "south", "west", "east"):
                continue
            texture = spec.get("texture", "")
            hops = 0
            while isinstance(texture, str) and texture.startswith("#") and hops < 8:
                texture = textures.get(texture[1:])
                hops += 1
            if not isinstance(texture, str) or split(texture)[0] not in known:
                continue
            image = image_of(texture)
            if image is None:
                continue
            uv = spec.get("uv") or default_uv(face, start, end)
            values |= alpha_under(image, uv)
    return values


WANTED = {"holes": "cutout (cutout_mipped for leaves)", "glass": "translucent"}
layered = 0
opaque = 0
for module, block, reference in reached:
    namespace, _ = split(reference)
    if namespace not in known:
        continue
    render_type, textures, elements = resolved(reference)
    if render_type is not None:
        layered += 1
        continue
    values = drawn_alpha(textures, elements)
    if not values or values == {255}:
        opaque += 1
        continue
    kind = "glass" if any(0 < v < 255 for v in values) else "holes"
    fail(f"{module}: {block} draws {reference} on the solid layer — a face of it reads "
         f"{'see-through' if kind == 'glass' else 'transparent'} pixels and no model in the "
         f"chain names a render_type, so those pixels are black in the world; it wants "
         f"{WANTED[kind]}")

print(f"render layers      : {len(reached)} block model(s) reached from a blockstate; "
      f"{layered} declare a render_type, {opaque} draw only opaque pixels and need none")
print()

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("every model, blockstate and texture reference resolves, and every block that needs a "
      "render layer names one")
