#!/usr/bin/env python3
"""
Retire jars Elysium has replaced, and keep the licensing ledger while doing it.

    python3 tools/retire.py --mods <folder> --list            what is clear to go, and why the rest is not
    python3 tools/retire.py --mods <folder> --dry-run [jar...] the ledger rows it would write, nothing moved
    python3 tools/retire.py --mods <folder> [jar...]           move them, write the rows
    python3 tools/retire.py --mods <folder> --record-superseded  ledger rows for what was retired before this tool
    python3 tools/retire.py --mods <folder> --all-but-ours [--list|--dry-run]
                                                         retire every jar that is neither Elysium's nor
                                                         something an Elysium jar (transitively) requires

With no jar names, every clear jar is taken. A jar is clear when all of these
hold, each read from the files rather than from memory:

  1. Its manifest row is DONE (tools/prune.py's list), so a module covers it.
  2. No jar that would remain declares it type="required" in its mods.toml.
  3. No hard tag entry in a remaining jar names its namespace (an entry marked
     required=false is fine; any other breaks the whole tag).

Retirement is a MOVE into mods/_superseded/, never a delete: a jar that turns
out to have been holding something up is one you want back in half a minute.

The licensing ledger, docs/LICENSING.md, gets one row per retired jar: mod id,
version, licence and authors as the jar declares them, the licence files it
carries, what replaced it, and the manifest's sentence on what was built (which
is where "ported" or "redrawn" says whether anything was derived from it). The
pack is private today; the day it is not, that ledger is the list of authors
whose terms have to be settled first, and it is far easier to write while the
jar is still in the folder than to reconstruct from a name in _superseded.
"""
import argparse
import datetime
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
LEDGER = os.path.join(ROOT, "docs", "LICENSING.md")
MANIFEST = os.path.join(ROOT, "docs", "MANIFEST.md")

HEADER = """# Licensing ledger

One row per jar retired from the pack, written by `tools/retire.py` at the
moment of retirement. Licence and authors are what the jar's own
`neoforge.mods.toml` declares; "carries" is the licence file inside the jar;
"built" is the manifest's sentence on what Elysium made in its place, which is
where a port or a redraw of somebody's art is recorded.

The pack is private. If it is ever published, every author in this table is
someone whose terms apply to whatever was derived from their work, and the
"All Rights Reserved" rows are the ones to settle first.

| Retired | Jar | Mod id | Version | Licence | Authors | Carries | Replaced by | Built |
|---|---|---|---|---|---|---|---|---|
"""


def toml_of(z):
    for name in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
        if name in z.namelist():
            return z.read(name).decode("utf-8", "replace")
    return ""


def field(text, key):
    m = re.search(r'^\s*' + key + r'\s*=\s*"([^"]*)"', text, re.M)
    return m.group(1).strip() if m else ""


def mods_blocks(text):
    """The [[mods]] blocks only: a modId in [[dependencies.x]] is a need, not a name."""
    return [b for b in re.split(r"\n(?=\s*\[\[)", text) if b.lstrip().startswith("[[mods]]")]


def describe(path):
    """What the jar says about itself."""
    with zipfile.ZipFile(path) as z:
        text = toml_of(z)
        ids, versions = [], []
        for block in mods_blocks(text):
            ids += re.findall(r'^\s*modId\s*=\s*"([^"]+)"', block, re.M)
            versions += re.findall(r'^\s*version\s*=\s*"([^"]+)"', block, re.M)
        version = next((v for v in versions if not v.startswith("$")), "")
        if not version:
            m = re.search(r'Implementation-Version:\s*(\S+)', z.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")) \
                if "META-INF/MANIFEST.MF" in z.namelist() else None
            version = m.group(1) if m else ""
        carries = sorted(n for n in z.namelist()
                         if re.search(r"(?i)(^|/)(licen[cs]e|copying|notice)", n) and n.count("/") <= 1)
    return {"ids": ids, "version": version, "license": field(text, "license") or "(none declared)",
            "authors": field(text, "authors"), "url": field(text, "displayURL"), "carries": carries}


def manifest_rows():
    rows = {}
    for line in open(MANIFEST, encoding="utf-8"):
        m = re.match(r"^\| `([^`]+\.jar)` \| (\w+) \| ([^|]*) \| (.*) \|\s*$", line)
        if m:
            rows[m.group(1)] = (m.group(2), m.group(3).strip(), m.group(4).strip())
    return rows


def prune_list():
    out = subprocess.run([sys.executable, os.path.join(HERE, "prune.py")],
                         capture_output=True, text=True, cwd=ROOT).stdout
    return set(out.split())


def keep_set(mods):
    """Elysium's own jars and everything they require, followed to the end.

    "Requires" is what a jar's mods.toml declares type="required"; an optional
    dependency is a jar the pack can lose. The game and the loader are not jars
    in the folder, so they fall out on their own.
    """
    installed = sorted(f for f in os.listdir(mods) if f.endswith(".jar"))
    provides, needs = {}, {}
    for f in installed:
        try:
            with zipfile.ZipFile(os.path.join(mods, f)) as z:
                text = toml_of(z)
        except zipfile.BadZipFile:
            continue
        for mid in describe(os.path.join(mods, f))["ids"]:
            provides[mid] = f
        wants = set()
        for block in re.split(r"\n(?=\s*\[\[)", text):
            if block.lstrip().startswith("[[dependencies"):
                mid, kind = field(block, "modId"), field(block, "type") or "required"
                if kind.lower() == "required":
                    wants.add(mid)
        needs[f] = wants
    keep = {f for f in installed if any(mid.startswith("elysium") for mid in describe(os.path.join(mods, f))["ids"])}
    grew = True
    while grew:
        grew = False
        for f in list(keep):
            for mid in needs.get(f, ()):
                g = provides.get(mid)
                if g and g not in keep:
                    keep.add(g)
                    grew = True
    return keep, needs, provides


def blockers(mods, candidates):
    """For each candidate: the remaining jars that require it, and the hard tag entries naming it."""
    cand_ids = {}
    for f in candidates:
        for mid in describe(os.path.join(mods, f))["ids"]:
            cand_ids[mid] = f
    required, tags = {}, {}
    remaining = [f for f in sorted(os.listdir(mods)) if f.endswith(".jar") and f not in candidates]
    for f in remaining:
        try:
            z = zipfile.ZipFile(os.path.join(mods, f))
        except zipfile.BadZipFile:
            continue
        text = toml_of(z)
        for block in re.split(r"\n(?=\s*\[\[)", text):
            if not block.lstrip().startswith("[[dependencies"):
                continue
            mid, kind = field(block, "modId"), field(block, "type") or "required"
            if mid in cand_ids and kind.lower() == "required":
                required.setdefault(cand_ids[mid], set()).add(f)
        for n in z.namelist():
            if n.startswith("data/") and "/tags/" in n and n.endswith(".json"):
                try:
                    values = json.loads(z.read(n)).get("values", [])
                except Exception:
                    continue
                for v in values:
                    vid = v.get("id", "") if isinstance(v, dict) else v
                    optional = isinstance(v, dict) and v.get("required", True) is False
                    ns = str(vid).lstrip("#").split(":", 1)[0]
                    if ns in cand_ids and not optional:
                        tags.setdefault(cand_ids[ns], set()).add(f + ":" + n)
    return required, tags


def ledger_row(mods, jar, rows, when):
    info = describe(os.path.join(mods, jar))
    status, module, built = rows.get(jar, ("", "", ""))
    if not module:
        # The manifest names one version of a mod; a later version retired
        # after an update is the same mod, so borrow its row by name stem.
        stem = re.split(r"[-_]v?\d", jar)[0]
        for name, row in rows.items():
            if name != jar and re.split(r"[-_]v?\d", name)[0] == stem:
                status, module, built = row
                break
    cell = lambda s: str(s).replace("|", "\\|")
    return "| {} | `{}` | {} | {} | {} | {} | {} | {} | {} |\n".format(
        when, cell(jar), cell(", ".join(info["ids"]) or "?"), cell(info["version"] or "?"),
        cell(info["license"]), cell(info["authors"] or "?"),
        cell(", ".join(info["carries"]) or "none"), cell(module or "?"), cell(built or "?"))


def write_rows(text_rows):
    if not os.path.isfile(LEDGER):
        with open(LEDGER, "w", encoding="utf-8", newline="\n") as f:
            f.write(HEADER)
    existing = open(LEDGER, encoding="utf-8").read()
    with open(LEDGER, "a", encoding="utf-8", newline="\n") as f:
        for row in text_rows:
            jar = re.search(r"\| `([^`]+)` \|", row).group(1)
            if f"| `{jar}` |" not in existing:
                f.write(row)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--mods", required=True, help="the instance's mods folder")
    p.add_argument("jars", nargs="*", help="jars to retire; none means every clear one")
    p.add_argument("--list", action="store_true", help="show what is clear and what blocks the rest")
    p.add_argument("--dry-run", action="store_true", help="print the ledger rows, move nothing")
    p.add_argument("--all-but-ours", action="store_true",
                   help="every jar that is neither Elysium's nor required, transitively, by an Elysium jar")
    p.add_argument("--record-superseded", action="store_true",
                   help="write ledger rows for jars already in mods/_superseded, nothing moved")
    a = p.parse_args()
    mods = a.mods
    when = datetime.date.today().isoformat()
    rows = manifest_rows()

    if a.record_superseded:
        # _superseded also holds the old copy of every mod that was merely
        # updated; a jar whose mod id is still installed is a version bump, not
        # a retirement, and has no business in the licensing ledger.
        folder = os.path.join(mods, "_superseded")
        still = set()
        for f in os.listdir(mods):
            if f.endswith(".jar"):
                try:
                    still.update(describe(os.path.join(mods, f))["ids"])
                except (zipfile.BadZipFile, KeyError):
                    pass
        jars = sorted(f for f in os.listdir(folder) if f.endswith(".jar") and not f.startswith("elysium"))
        retired = [j for j in jars if not set(describe(os.path.join(folder, j))["ids"]) & still]
        text_rows = [ledger_row(folder, j, rows, "before " + when) for j in retired]
        write_rows(text_rows)
        print(f"{len(text_rows)} row(s) recorded for jars retired before this tool; "
              f"{len(jars) - len(retired)} skipped as old versions of mods still installed")
        return 0

    installed = sorted(f for f in os.listdir(mods) if f.endswith(".jar"))
    if a.all_but_ours:
        keep, needs, provides = keep_set(mods)
        candidates = [f for f in installed if f not in keep]
        print(f"keeping {len(keep)}: " + ", ".join(sorted(keep)))
    else:
        listed = prune_list()
        candidates = [f for f in installed if f in listed]
    required, tags = blockers(mods, candidates)
    clear = [f for f in candidates if f not in required and f not in tags]

    if a.list or not a.jars and not clear:
        print(f"{len(installed)} installed, {len(candidates)} {'not ours and not needed' if a.all_but_ours else 'marked DONE'}, {len(clear)} clear:")
        for f in clear:
            print("  " + f)
        for f in candidates:
            if f in required:
                print(f"  BLOCKED {f}: required by {sorted(required[f])}")
            if f in tags:
                print(f"  BLOCKED {f}: {len(tags[f])} hard tag entr{'y' if len(tags[f]) == 1 else 'ies'}, e.g. {sorted(tags[f])[0]}")
        if a.list:
            return 0

    chosen = a.jars or clear
    refused = 0
    for jar in chosen:
        if jar not in installed:
            print(f"refused {jar}: not in {mods}"); refused += 1
        elif jar not in candidates:
            print(f"refused {jar}: " + ("Elysium keeps it" if a.all_but_ours else "its manifest row is not DONE")); refused += 1
        elif jar in required:
            print(f"refused {jar}: required by {sorted(required[jar])}"); refused += 1
        elif jar in tags:
            print(f"refused {jar}: hard tag entries name it, e.g. {sorted(tags[jar])[0]}"); refused += 1
    if refused:
        return 1

    text_rows = [ledger_row(mods, jar, rows, when) for jar in chosen]
    if a.dry_run:
        sys.stdout.write("".join(text_rows))
        print(f"\n{len(chosen)} jar(s) would move to _superseded; ledger rows above; nothing changed")
        return 0

    write_rows(text_rows)
    folder = os.path.join(mods, "_superseded")
    os.makedirs(folder, exist_ok=True)
    for jar in chosen:
        shutil.move(os.path.join(mods, jar), os.path.join(folder, jar))
        print(f"retired {jar}")
    print(f"{len(chosen)} jar(s) moved to {folder}; {len(chosen)} row(s) added to docs/LICENSING.md")
    return 0


if __name__ == "__main__":
    sys.exit(main())
