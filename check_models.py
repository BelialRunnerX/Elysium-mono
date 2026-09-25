#!/usr/bin/env python3
"""
Does every model, blockstate and texture reference land on a file?

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

The kinetics sail shipped a blockstate whose variants the block does not have,
and the flora door an item model over a texture nobody generated; both were
found by placing blocks in a running game, which is the slow way.
"""
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

shipped = set()          # "ns:models/block/x.json", "ns:textures/item/y.png"
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
                shipped.add(f"{namespace}:{path.relative_to(root).as_posix()}")

vanilla = set()
if os.path.isfile(VANILLA):
    with zipfile.ZipFile(VANILLA) as jar:
        for name in jar.namelist():
            if name.startswith("assets/") and name.count("/") >= 2 and not name.endswith("/"):
                _, namespace, rest = name.split("/", 2)
                vanilla.add(f"{namespace}:{rest}")
else:
    fail(f"no vanilla assets at {VANILLA}, so references into minecraft: cannot be followed "
         f"(check_loadtest.py explains how the jar is made)")

known = ours | {"minecraft"}


def exists(key):
    return key in shipped or key in vanilla


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
    namespace, rest = key.split(":", 1)
    path = HERE / module / "src" / "main" / "resources" / "assets" / namespace / rest
    try:
        with open(path, encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, ValueError) as error:
        fail(f"{module}: {key} is not JSON the game can read: {error}")
        return None


# ---------------------------------------------------------------------------
# Blockstates
# ---------------------------------------------------------------------------

blockstates = 0
models = 0
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
            for entry in entries:
                if "model" in entry:
                    follow("model", entry["model"], where)
                else:
                    fail(f"{where} has a variant with no model")

        # ------------------------------------------------------------------
        # Models
        # ------------------------------------------------------------------
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
print()

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("every model, blockstate and texture reference resolves")
