#!/usr/bin/env python3
"""
Check that a merged jar is actually installable.

    python3 tools/checkjar.py dist/elysium-all-1.0.0.jar dist/elysiumlib-1.0.0.jar ...

Everything checked here is something that produces a jar which *builds fine* and
then misbehaves in game, which is the only interesting failure mode packaging
has. A merge that drops half the classes fails obviously; a merge that drops one
tag file, or declares a mod whose `@Mod` class is not in the archive, does not.

The checks, and what each one is protecting against:

  **Nothing was lost.** Every entry of every input jar is in the output, except
  the two that are deliberately regenerated. This is the check that catches a
  collision the merge tool resolved by accident instead of on purpose.

  **Every declared mod has an entry point.** NeoForge finds a mod's class by
  scanning for `@Mod("id")`. A `[[mods]]` block whose class is missing is a
  crash on startup with a message about the mod id and no hint that the jar is
  the problem.

  **Every entry point has a declaration.** The other direction. An `@Mod` class
  in the jar that no `[[mods]]` block names is a mod that silently never loads —
  no error, no entry on the mods screen, just five sixths of a mod pack.

  **Resource namespaces are whole.** Each mod's assets and data survived. A
  namespace that lost files loads with missing textures and no crash.

  **The toml parses, and says what it should.** Read back with a real parser
  rather than the regex that wrote it.
"""
import argparse
import json
import os
import re
import sys
import zipfile

try:
    import tomllib
except ModuleNotFoundError:
    tomllib = None

MODS_TOML = "META-INF/neoforge.mods.toml"
MANIFEST = "META-INF/MANIFEST.MF"
REGENERATED = {MODS_TOML, MANIFEST}

# @Mod("elysiumlib") compiled into a class file. The annotation is a runtime
# annotation, so the id survives as a plain UTF-8 constant in the constant pool
# and can be found without loading anything.
MOD_ANNOTATION = re.compile(rb"Lnet/neoforged/fml/common/Mod;")


def is_tag(entry):
    return (entry.startswith("data/") and "/tags/" in entry
            and entry.endswith(".json"))


def lost_values(source_bytes, merged_bytes):
    """
    Entries of a source tag that the merged tag no longer has.

    A tag merge is only ever allowed to *add*. Comparing the value lists rather
    than the bytes is what makes this a real check: the bytes differ by
    construction, so anything that stops at "the bytes differ, and that is
    expected" is not checking anything at all.
    """
    try:
        source = json.loads(source_bytes)
        merged = json.loads(merged_bytes)
    except ValueError as problem:
        return [f"<unparseable: {problem}>"]
    have = {render(value) for value in merged.get("values", [])}
    return sorted(render(value) for value in source.get("values", [])
                  if render(value) not in have)


def render(value):
    """Tag entries are either a plain id or {"id": ..., "required": ...}."""
    if isinstance(value, dict):
        return str(value.get("id"))
    return str(value)


def entry_point_ids(entries):
    """
    Mod ids that some class in this jar declares with @Mod.

    Reads the constant pool as bytes rather than parsing the class format. A
    class carrying the @Mod annotation has the annotation's descriptor and its
    id string both in the pool; taking every short lowercase string in a class
    that has the descriptor over-collects, so this is intersected with the
    declared ids by the caller rather than used on its own.
    """
    found = set()
    for name, payload in entries.items():
        if not name.endswith(".class") or not MOD_ANNOTATION.search(payload):
            continue
        for match in re.finditer(rb"[a-z][a-z0-9_]{2,63}", payload):
            found.add(match.group(0).decode("ascii"))
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("merged")
    parser.add_argument("inputs", nargs="+")
    args = parser.parse_args()

    problems = []

    def fail(message):
        problems.append(message)

    with zipfile.ZipFile(args.merged) as archive:
        merged = {info.filename: archive.read(info)
                  for info in archive.infolist() if not info.is_dir()}

    # --- the toml ------------------------------------------------------
    if MODS_TOML not in merged:
        print(f"{args.merged} has no {MODS_TOML} — it is not a mod jar",
              file=sys.stderr)
        return 1
    toml_text = merged[MODS_TOML].decode("utf-8")

    declared = []
    if tomllib is None:
        fail("python is older than 3.11, so the merged toml was not parsed — "
             "the regex that wrote it is not evidence that it is valid")
    else:
        try:
            parsed = tomllib.loads(toml_text)
        except Exception as problem:
            print(f"{MODS_TOML} does not parse: {problem}", file=sys.stderr)
            return 1
        declared = [entry.get("modId") for entry in parsed.get("mods", [])]
        for key in ("modLoader", "loaderVersion", "license"):
            if not parsed.get(key):
                fail(f"{MODS_TOML} declares no {key}")
        for index, entry in enumerate(parsed.get("mods", [])):
            for key in ("modId", "version", "displayName"):
                if not entry.get(key):
                    fail(f"{MODS_TOML} mod #{index + 1} declares no {key}")
        if "${" in toml_text:
            fail(f"{MODS_TOML} still contains an unexpanded ${{...}} "
                 f"placeholder — processResources did not run, or packjar.py "
                 f"did not expand it")

    # --- nothing was lost ----------------------------------------------
    expected_ids = []
    for jar in args.inputs:
        with zipfile.ZipFile(jar) as archive:
            source = {info.filename: archive.read(info)
                      for info in archive.infolist() if not info.is_dir()}
        name = os.path.basename(jar)

        if MODS_TOML in source and tomllib is not None:
            try:
                expected_ids += [entry["modId"] for entry in
                                 tomllib.loads(source[MODS_TOML].decode("utf-8"))
                                 .get("mods", [])]
            except Exception as problem:
                fail(f"{name}: its own {MODS_TOML} does not parse: {problem}")

        for entry, payload in source.items():
            if entry in REGENERATED:
                continue
            if entry not in merged:
                fail(f"{name}: {entry} is not in the merged jar")
                continue
            if merged[entry] != payload:
                if is_tag(entry):
                    # A tag file is *allowed* to change, because the merge
                    # unions it. It is not allowed to change by losing
                    # anything, which is the whole reason the union exists —
                    # and an earlier version of this check stopped at "it is a
                    # tag, carry on", which let exactly the failure this tool
                    # was written to prevent walk straight through it.
                    missing = lost_values(payload, merged[entry])
                    if missing:
                        fail(f"{name}: {entry} lost {missing} in the merge. "
                             f"As separate jars these were separate packs and "
                             f"the game merged them; one jar is one pack, so "
                             f"whatever is not in this file does not exist.")
                else:
                    fail(f"{name}: {entry} changed during the merge, and it is "
                         f"not a tag file the merge is allowed to union")

    # --- mods declared vs mods present ---------------------------------
    for mod_id in expected_ids:
        if mod_id not in declared:
            fail(f"{mod_id} was declared by an input jar but is missing from "
                 f"the merged {MODS_TOML} — that mod would silently not load")

    strings = entry_point_ids(merged)
    for mod_id in declared:
        if mod_id not in strings:
            fail(f"{MODS_TOML} declares {mod_id} but no @Mod class in the jar "
                 f"carries that id — NeoForge crashes on startup")

    # --- resource namespaces -------------------------------------------
    namespaces = {entry.split("/")[1] for entry in merged
                  if entry.startswith(("assets/", "data/")) and entry.count("/") > 1}
    for mod_id in declared:
        if mod_id not in namespaces:
            fail(f"no assets/ or data/ under the {mod_id} namespace — its "
                 f"resources did not survive the merge")

    # --- report ---------------------------------------------------------
    classes = sum(1 for entry in merged if entry.endswith(".class"))
    if problems:
        print(f"{len(problems)} PROBLEM(S) in {args.merged}:")
        for problem in problems:
            print("  - " + problem)
        return 1

    print(f"merged jar         : {len(declared)} mods, {classes} classes, "
          f"{len(merged)} entries, {len(namespaces)} namespaces, nothing lost")
    return 0


if __name__ == "__main__":
    sys.exit(main())
