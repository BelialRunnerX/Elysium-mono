#!/usr/bin/env python3
"""
The two mod folders on the stick: the client's, and the server's.

    python3 tools/modfolders.py [--from <ELYSIUM_TEST_ROOT>/ElysiumServer/mods] [--to <folder>]

Dylan, 27 September 2026: *two mod folders - one is client side, one is only
what's necessary server side - both clearly named and on the usb stick.*

Reads every jar in the source mods folder (never `_superseded`), opens its
`META-INF/neoforge.mods.toml`, and sorts it:

* **Elysium-client-mods**: every jar. A client needs all of them.
* **Elysium-server-mods**: every jar whose mods are not all client-only. A mod
  is client-only when its `[[mods]]` entry (or every dependency it declares
  on the game) says `side="CLIENT"`; a jar that declares nothing is for both
  sides, as every one of ours and of MineColonies' is.

Each folder gets a `README.txt` naming the game and loader version, the date,
every jar and why it is there, so the stick explains itself. The folders are
rebuilt from scratch every time so they cannot hold a stale jar.

The source is the dedicated server under ELYSIUM_TEST_ROOT (default
C:/testingbuilds). The stick, E:, was lost in October 2026 (on 14 October the
test rigs were rebuilt on C:), so the folders now land in ELYSIUM_TEST_ROOT
unless --to names a stick.
"""
import argparse
import datetime
import os
import re
import shutil
import sys
import zipfile

TEST_ROOT = os.environ.get("ELYSIUM_TEST_ROOT", "C:/testingbuilds").replace("\\", "/").rstrip("/")
CLIENT_ONLY_MARK = re.compile(r'^\s*side\s*=\s*"CLIENT"', re.M)


def sides_of(jar):
    """(mod ids, client-only?) read off the jar's mods file."""
    with zipfile.ZipFile(jar) as z:
        try:
            text = z.read("META-INF/neoforge.mods.toml").decode("utf-8", "replace")
        except KeyError:
            return [], False
    # A [[mods]] block names a mod this jar carries; a [[dependencies.x]] block names one it wants.
    blocks = re.split(r"\n\s*\[\[", text)
    mod_blocks = [b for b in blocks if b.lstrip().startswith("mods]]")]
    own = []
    for block in mod_blocks:
        for m in re.findall(r'^\s*modId\s*=\s*"([a-z0-9_]+)"', block, re.M):
            if m not in own:
                own.append(m)
    client_only = bool(mod_blocks) and all(CLIENT_ONLY_MARK.search(b) for b in mod_blocks)
    return own, client_only


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--from", dest="source", default=TEST_ROOT + "/ElysiumServer/mods")
    parser.add_argument("--to", dest="target", default=TEST_ROOT)
    parser.add_argument("--loader", default="NeoForge 21.1.248 for Minecraft 1.21.1")
    args = parser.parse_args()

    jars = sorted(n for n in os.listdir(args.source) if n.endswith(".jar"))
    if not jars:
        print(f"no jars in {args.source}")
        return 1
    rows = []
    for name in jars:
        own, client_only = sides_of(os.path.join(args.source, name))
        rows.append((name, own, client_only))

    folders = {"Elysium-client-mods": rows, "Elysium-server-mods": [r for r in rows if not r[2]]}
    today = datetime.date.today().isoformat()
    for folder, chosen in folders.items():
        path = os.path.join(args.target, folder)
        if os.path.isdir(path):
            shutil.rmtree(path)
        os.makedirs(path)
        for name, _own, _client in chosen:
            shutil.copy2(os.path.join(args.source, name), os.path.join(path, name))
        side = "the client" if folder.startswith("Elysium-client") else "a dedicated server"
        lines = [f"Elysium — mods for {side}", f"{args.loader}; built {today} by tools/modfolders.py", "",
                 "Copy every jar in this folder into the instance's mods folder. Nothing else is needed.", ""]
        for name, own, client in chosen:
            what = "ours" if any(m.startswith("elysium") for m in own) else "a dependency of ours"
            lines.append(f"  {name}  ({', '.join(own) or 'no mods file'}; {what}{'; client only' if client else ''})")
        left_out = [r for r in rows if r not in chosen]
        if left_out:
            lines += ["", "Left out, client only:"] + [f"  {n}" for n, _o, _c in left_out]
        elif folder.startswith("Elysium-server"):
            lines += ["", "Every jar the client needs is needed here too: none of them is client only."]
        open(os.path.join(path, "README.txt"), "w", encoding="utf-8", newline="\r\n").write("\n".join(lines) + "\n")
        print(f"{folder:22s}: {len(chosen)} jar(s) -> {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
