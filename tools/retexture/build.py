"""
Reading a player's mods and writing them a resource pack.

<h2>What never happens here</h2>

Nothing of anybody else's is copied, stored, uploaded or redistributed. The
mods are read from the folder they are already installed in, on the machine
they are installed on, and what is written is a resource pack on that same
machine. Create's art is All Rights Reserved and so is most of the rest of
this list; the thing this repository ships is this program, and the art it
makes is made on the player's own disk out of files they already have.

<h2>What is written</h2>

A resource pack that mirrors each mod's texture paths exactly, so the game
overrides file for file and every model, every UV and every animation still
points at something the same size as what it pointed at before.
"""
import json
import os
import zipfile

from PIL import Image

import classify
import palette
import recolour

#: The mods the reskin is aimed at. A namespace outside this list is left alone
#: rather than guessed at: recolouring a mod nobody asked about is how a
#: resource pack ends up repainting somebody's minimap.
SCOPE = (
    "create", "createaddition", "createbigcannons", "createdeco",
    "createdieselgenerators", "create_new_age", "createutilities",
    "createrailwaysnavigator", "railways", "createchunkloading",
    "immersiveengineering", "mekanism", "mekanismgenerators", "mekanismtools",
    "mekanismadditions", "refinedstorage", "ae2", "appliedenergistics2",
    "industrialforegoing", "sophisticatedstorage", "sophisticatedbackpacks",
    "storagedrawers", "functionalstorage", "dankstorage", "thermal",
    "thermal_foundation", "powah", "modularrouters", "laserio", "pipez",
)

#: Never, whatever is asked for. Vanilla is not a tech mod, and repainting our
#: own art with a generated approximation of our own art is a way to lose it.
PROTECTED = ("minecraft", "neoforge", "forge", "elysium", "elysiumlib",
             "elysiumcore", "elysiumspace", "elysiummobs", "elysiumnpcs",
             "elysiumtrinkets", "elysiumsolarpunk", "elysiumdungeons",
             "elysiumarmory", "elysiumascendancy", "elysiumenchanting",
             "elysiumspawners", "elysiumores", "elysiumflora", "elysiumkinetics", "elysiumindustry",
             "elysiumatomics", "elysiumlogistics", "elysiumagronomy", "elysiumfoundry", "elysiumalmanac", "elysiumtransit", "elysiumarcana", "elysiumcomforts", "elysiumatelier", "elysiumarsenal", "elysiumbestiary", "elysiumwilds",
             "elysiumcolonies")

#: The resource pack format 1.21.1 reads.
PACK_FORMAT = 34

PACK_NAME = "Elysium Tech Reskin"


def namespace_of(path):
    pieces = path.split("/")
    return pieces[1] if len(pieces) > 2 and pieces[0] == "assets" else None


def overrides_from(path):
    """
    The hand corrections, as (pattern, class) pairs in order.

    A file rather than a table in the source, because the whole point of the
    overrides is that they are found by looking at the result and are a
    player's to add. JSON, so somebody can edit it without touching Python.
    """
    if not path or not os.path.isfile(path):
        return []
    loaded = json.load(open(path, encoding="utf-8"))
    return [(entry["match"], entry["class"]) for entry in loaded.get("overrides", [])]


class Report:
    """What was done, per mod, so coverage is a number rather than a feeling."""

    def __init__(self):
        self.mods = {}

    def note(self, mod, kind, how):
        seen = self.mods.setdefault(mod, {"textures": 0, "classes": {}, "how": {}})
        seen["textures"] += 1
        seen["classes"][kind] = seen["classes"].get(kind, 0) + 1
        seen["how"][how] = seen["how"].get(how, 0) + 1

    def skipped(self, mod, why):
        seen = self.mods.setdefault(mod, {"textures": 0, "classes": {}, "how": {}})
        seen.setdefault("skipped", {})
        seen["skipped"][why] = seen["skipped"].get(why, 0) + 1

    def as_json(self):
        return {"pack": PACK_NAME, "mods": self.mods,
                "textures": sum(m["textures"] for m in self.mods.values())}


def build(mods_dir, out_dir, only=None, overrides=None, limit=0, say=print):
    ramps = palette.load()
    surfaces = {name: [palette.rgb(step) for step in ramp]
                for name, ramp in ramps["surfaces"].items()}
    glows = {name: [palette.rgb(step) for step in ramp]
             for name, ramp in ramps["glows"].items()}
    corrections = overrides_from(overrides)
    wanted = set(only) if only else None

    report = Report()
    written = 0
    os.makedirs(out_dir, exist_ok=True)

    for jar_name in sorted(os.listdir(mods_dir)):
        if not jar_name.endswith(".jar"):
            continue
        jar_path = os.path.join(mods_dir, jar_name)
        try:
            jar = zipfile.ZipFile(jar_path)
        except zipfile.BadZipFile:
            continue
        with jar:
            names = jar.namelist()
            inside = {name for name in names}
            for name in names:
                mod = namespace_of(name)
                if mod is None or mod in PROTECTED:
                    continue
                if wanted is not None and mod not in wanted:
                    continue
                if wanted is None and mod not in SCOPE:
                    continue
                if not name.endswith(".png"):
                    continue
                if not classify.is_texture(name):
                    report.skipped(mod, "not a surface")
                    continue
                if limit and written >= limit:
                    continue
                try:
                    with jar.open(name) as handle:
                        image = Image.open(handle).convert("RGBA")
                except Exception:
                    report.skipped(mod, "unreadable")
                    continue
                kind, how = classify.classify(name, image, corrections)
                surface = surfaces.get(kind, surfaces["metal_pale"])
                glow = glows[palette.energy_for(mod)]
                made = recolour.recolour(image, surface, glow,
                                         always_energy=classify.emissive(name))
                target = os.path.join(out_dir, name)
                os.makedirs(os.path.dirname(target), exist_ok=True)
                made.save(target)
                # The animation's own file, copied byte for byte: its frame
                # order and timing are the author's drawing, not its colour.
                meta = name + ".mcmeta"
                if meta in inside:
                    with open(target + ".mcmeta", "wb") as out:
                        out.write(jar.read(meta))
                report.note(mod, kind, how)
                written += 1
                if written % 500 == 0:
                    say(f"  {written} textures")

    with open(os.path.join(out_dir, "pack.mcmeta"), "w", encoding="utf-8") as out:
        json.dump({"pack": {
            "pack_format": PACK_FORMAT,
            "description": "Elysium Tech Reskin — generated locally from the "
                           "mods installed in this instance. Recoloured, never "
                           "reshaped.",
        }}, out, indent=2)
        out.write("\n")
    return report, written
