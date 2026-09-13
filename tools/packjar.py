#!/usr/bin/env python3
"""
Package one module into a mod jar, without Gradle.

    python3 tools/packjar.py elysium-lib <classes-dir> dist/elysiumlib-1.0.0.jar

This exists because Gradle cannot reach the NeoForge repositories from every
environment this repository is worked in, and "you cannot produce a jar here"
would mean the merged jar could never be built or tested outside a full
NeoGradle setup.

It reproduces exactly two things Gradle's `jar` task does and nothing else:

  * the compiled classes go in, and
  * `src/main/resources` goes in, with `${...}` in neoforge.mods.toml expanded
    from gradle.properties the way `processResources` expands it.

It is deliberately not a replacement for the Gradle build. There is no
reobfuscation step, no dependency resolution and no data generation; it packages
what is already there. For the merged jar that is enough, because every input it
needs is either compiled source or a checked-in resource.
"""
import argparse
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import modprops

MODS_TOML = "META-INF/neoforge.mods.toml"


def pack(module_dir, classes_dir, out_path, quiet=False):
    properties = modprops.module_properties(module_dir)
    resources = os.path.join(module_dir, "src/main/resources")

    mod_id = properties.get("mod_id")
    version = properties.get("mod_version")
    if not mod_id or not version:
        print(f"{module_dir}/gradle.properties defines no mod_id/mod_version",
              file=sys.stderr)
        return 1

    os.makedirs(os.path.dirname(os.path.abspath(out_path)) or ".", exist_ok=True)
    entries = 0
    classes = 0

    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as jar:
        jar.writestr("META-INF/MANIFEST.MF",
                     "Manifest-Version: 1.0\n"
                     f"Implementation-Title: {properties.get('mod_name', mod_id)}\n"
                     f"Implementation-Version: {version}\n")
        entries += 1

        for dirpath, _, filenames in os.walk(resources):
            for filename in sorted(filenames):
                full = os.path.join(dirpath, filename)
                name = os.path.relpath(full, resources).replace(os.sep, "/")
                if name == MODS_TOML:
                    # The one file Gradle templates. An unexpanded placeholder
                    # here produces a mod whose id is literally "${mod_id}",
                    # which loads and is untraceable, so modprops raises rather
                    # than substituting an empty string.
                    jar.writestr(name, modprops.expand_file(full, properties))
                else:
                    jar.write(full, name)
                entries += 1

        for dirpath, _, filenames in os.walk(classes_dir):
            for filename in sorted(filenames):
                if not filename.endswith(".class"):
                    continue
                full = os.path.join(dirpath, filename)
                name = os.path.relpath(full, classes_dir).replace(os.sep, "/")
                jar.write(full, name)
                entries += 1
                classes += 1

    if classes == 0:
        print(f"{out_path}: no classes found under {classes_dir} — the module "
              f"was not compiled", file=sys.stderr)
        return 1

    if not quiet:
        print(f"  packed {mod_id:<16} {classes:>4} classes, {entries:>4} entries"
              f"  -> {out_path}")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("module", help="the module directory, e.g. elysium-lib")
    parser.add_argument("classes", help="the compiled classes directory")
    parser.add_argument("output", help="the jar to write")
    parser.add_argument("--quiet", action="store_true")
    args = parser.parse_args()
    return pack(args.module, args.classes, args.output, quiet=args.quiet)


if __name__ == "__main__":
    sys.exit(main())
