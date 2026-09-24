#!/usr/bin/env python3
"""
Put somebody else's mod inside ours, the way NeoForge expects to find it.

    python3 tools/jarjar.py dist/elysium-all-1.0.0.jar /path/to/curios.jar [...]

Why this exists
---------------
elysium-trinkets needs Curios. That is a hard dependency — forty accessories
with nowhere to put them is not a degraded mod, it is a broken one — and it
means a player who downloads one Elysium jar and drops it in their mods folder
gets a load error rather than a game. NeoForge's answer to that is jar-in-jar:
the dependency travels inside the jar that needs it, the loader unpacks it, and
an externally installed copy of the same mod wins if it is newer.

What it writes
--------------
Three things, and the third is not optional in the legal sense.

1. The dependency's own jar, unmodified, at META-INF/jarjar/<its name>.
2. META-INF/jarjar/metadata.json, which is the index the loader reads. The
   version range is taken from what the mod is actually declared to need, so
   that a separately installed newer copy satisfies it and is preferred.
3. Its licence, its copyright notice and a pointer to its source, under
   licences/<modid>/, taken out of its own jar rather than written here. Curios
   is LGPL: shipping it without its licence text would be the one packaging
   mistake in this file that is not merely a bug.

Nothing of the dependency is modified, renamed, recompiled or recoloured. This
copies a file and writes an index.
"""
import json
import os
import re
import shutil
import sys
import tempfile
import zipfile

#: Where a mod's identity is declared, and the keys taken from it.
TOML = "META-INF/neoforge.mods.toml"

#: The licence files a mod may carry, in the order they are looked for. Taken
#: from the dependency's own jar: a licence retyped here would be a licence
#: that can drift from the one the author shipped.
LICENCE_FILES = ("LICENSE", "LICENSE.txt", "LICENCE", "COPYING", "COPYING.LESSER",
                 "NOTICE")

#: Maven coordinates per mod id. JarJar needs a group and an artifact to tell
#: two embedded jars apart; a mod id alone is not one, and guessing it from the
#: file name produces an identifier that changes when the file is renamed.
COORDINATES = {
    "curios": ("top.theillusivec4.curios", "curios-neoforge"),
}


def read_toml(jar):
    with zipfile.ZipFile(jar) as zf:
        if TOML not in zf.namelist():
            raise SystemExit(f"{jar} has no {TOML}; it is not a NeoForge mod")
        return zf.read(TOML).decode("utf-8")


def declared(text, key):
    found = re.search(rf'^\s*{key}\s*=\s*"([^"]+)"', text, re.M)
    return found.group(1) if found else None


def needed_range(mod_id, root):
    """
    What our own mods say they need of this dependency.

    Read from our tomls rather than chosen here, because the two numbers have
    to agree: a bundled jar outside the range its dependent declares is a jar
    the loader unpacks and then refuses anyway.
    """
    wanted = []
    for directory in sorted(os.listdir(root)):
        toml = os.path.join(root, directory, "src/main/resources", TOML)
        if not os.path.isfile(toml):
            continue
        text = open(toml, encoding="utf-8").read()
        for block in re.findall(r"\[\[dependencies\.[^\]]+\]\](.*?)(?=\[\[|\Z)",
                                text, re.S):
            if declared(block, "modId") != mod_id:
                continue
            found = declared(block, "versionRange")
            if found:
                wanted.append(found)
    return wanted


def bundle(merged, dependencies, root):
    entries = []
    staged = {}
    licences = {}

    for path in dependencies:
        text = read_toml(path)
        mod_id = declared(text, "modId")
        version = declared(text, "version")
        source = declared(text, "displayURL") or declared(text, "issueTrackerURL")
        licence = declared(text, "license")
        if not mod_id or not version:
            raise SystemExit(f"{path} does not declare a modId and a version")
        if mod_id not in COORDINATES:
            raise SystemExit(f"no maven coordinates known for {mod_id}; add them to "
                             f"COORDINATES with the group the mod publishes under")
        group, artifact = COORDINATES[mod_id]

        ranges = needed_range(mod_id, root)
        if not ranges:
            raise SystemExit(f"nothing in this repository declares a dependency on "
                             f"{mod_id}, so bundling it would ship a mod nobody asked "
                             f"for")
        if len(set(ranges)) > 1:
            raise SystemExit(f"two of our mods want different versions of {mod_id}: "
                             f"{', '.join(sorted(set(ranges)))}")
        inside = f"META-INF/jarjar/{os.path.basename(path)}"
        entries.append({
            "identifier": {"group": group, "artifact": artifact},
            "version": {"range": ranges[0], "artifactVersion": version},
            "path": inside,
            "isObfuscated": False,
        })
        staged[inside] = path

        with zipfile.ZipFile(path) as zf:
            held = {}
            for name in LICENCE_FILES:
                if name in zf.namelist():
                    held[name] = zf.read(name)
            if not held:
                raise SystemExit(f"{path} carries no licence file, so it cannot be "
                                 f"redistributed inside another jar")
            held["SOURCE.txt"] = (
                f"{mod_id} {version}\n"
                f"licence: {licence or 'see the files beside this one'}\n"
                f"source: {source or 'see the mod page'}\n\n"
                f"This jar is included unmodified inside Elysium so that the mods "
                f"that depend on it work out of the box. It is not Elysium's work "
                f"and is not covered by Elysium's licence. An installed copy of "
                f"the same mod takes precedence over this one.\n").encode("utf-8")
            licences[mod_id] = held

    listing = ("# Bundled inside this jar\n\n"
               "These are other people's mods, included unmodified because mods in\n"
               "this jar require them. Each keeps its own licence, which travels\n"
               "with it under licences/<mod>/ along with a pointer to its source.\n\n")
    for entry in entries:
        listing += (f"- {entry['identifier']['group']}:"
                    f"{entry['identifier']['artifact']} "
                    f"{entry['version']['artifactVersion']} "
                    f"(needed: {entry['version']['range']})\n")

    # Rewritten rather than edited in place: a zip cannot have an entry removed
    # and a jar that grew a second copy of its own metadata is a jar that loads
    # whichever one the reader happened to find first.
    handle, working = tempfile.mkstemp(suffix=".jar")
    os.close(handle)
    skip = set(staged) | {"META-INF/jarjar/metadata.json", "THIRD-PARTY.md"}
    skip |= {f"licences/{mod}/{name}" for mod, held in licences.items()
             for name in held}
    with zipfile.ZipFile(merged) as source, \
            zipfile.ZipFile(working, "w", zipfile.ZIP_DEFLATED) as out:
        for item in source.infolist():
            if item.filename in skip:
                continue
            out.writestr(item, source.read(item.filename))
        out.writestr("META-INF/jarjar/metadata.json",
                     json.dumps({"jars": entries}, indent=2) + "\n")
        out.writestr("THIRD-PARTY.md", listing)
        for mod, held in licences.items():
            for name, body in held.items():
                out.writestr(f"licences/{mod}/{name}", body)
        for inside, path in staged.items():
            # Stored rather than deflated: it is already a zip, compressing it
            # again costs time and saves nothing.
            out.write(path, inside, compress_type=zipfile.ZIP_STORED)
    shutil.move(working, merged)
    return entries


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    merged = sys.argv[1]
    dependencies = sys.argv[2:]
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    for path in [merged] + dependencies:
        if not os.path.isfile(path):
            print(f"no such jar: {path}")
            return 2
    entries = bundle(merged, dependencies, root)
    for entry in entries:
        print(f"  bundled {entry['identifier']['artifact']} "
              f"{entry['version']['artifactVersion']} for {entry['version']['range']}")
    print(f"  wrote META-INF/jarjar/metadata.json, THIRD-PARTY.md and "
          f"{len(entries)} licence folder(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
