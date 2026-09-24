#!/usr/bin/env python3
"""
Is the mod we bundled actually loadable, and did we ship its licence?

    python3 tools/checkbundle.py dist/elysium-all-1.0.0.jar

Why this exists
---------------
Jar-in-jar is a claim about a zip file, so no source validator can see it, and
it fails silently in the most expensive way there is: the jar loads, the
bundled dependency does not, and the first anybody hears of it is a player
whose trinkets do not exist. The three ways it goes wrong are an index that
names a path the jar does not contain, a version range the bundled jar does not
itself satisfy, and a licence that was left behind.

A jar with nothing bundled passes. Bundling is opt-in, and "we did not bundle
anything today" is a fact rather than a fault.
"""
import io
import json
import re
import sys
import zipfile

INDEX = "META-INF/jarjar/metadata.json"
TOML = "META-INF/neoforge.mods.toml"

problems = []


def fail(message):
    problems.append(message)


def declared(text, key):
    found = re.search(rf'^\s*{key}\s*=\s*"([^"]+)"', text, re.M)
    return found.group(1) if found else None


def parts(version):
    """
    A version as something comparable.

    Numbers compare as numbers and anything after a + or - is dropped: Curios
    calls itself 9.5.1+1.21.1, where the tail is the Minecraft version and not
    part of its own ordering at all.
    """
    head = re.split(r"[+\-]", version, 1)[0]
    out = []
    for piece in head.split("."):
        out.append(int(piece) if piece.isdigit() else 0)
    return out


def satisfies(version, span):
    """
    Whether a version falls inside a Maven range.

    Only the forms this repository actually writes: [a,), [a,b), [a,b] and a
    bare version meaning "at least". Anything else is reported rather than
    guessed at, because a range silently read as "everything" is the same bug
    this file exists to catch.
    """
    span = span.strip()
    if not span:
        return False
    if span[0] not in "[(":
        return parts(version) >= parts(span)
    if "," not in span:
        fail(f"version range {span} is a shape this checker does not read")
        return True
    low, high = span[1:-1].split(",", 1)
    if low and parts(version) < parts(low):
        return False
    if low and span[0] == "(" and parts(version) == parts(low):
        return False
    if high:
        if parts(version) > parts(high):
            return False
        if span[-1] == ")" and parts(version) == parts(high):
            return False
    return True


def check(path):
    with zipfile.ZipFile(path) as jar:
        names = set(jar.namelist())
        if INDEX not in names:
            embedded = [n for n in names if n.startswith("META-INF/jarjar/")
                        and n.endswith(".jar")]
            if embedded:
                fail(f"{len(embedded)} jar(s) are inside META-INF/jarjar/ and nothing "
                     f"indexes them, so the loader unpacks none of them")
            print(f"bundled jars       : none in {path}")
            return
        index = json.loads(jar.read(INDEX))
        entries = index.get("jars", [])
        if not entries:
            fail("the jar-in-jar index is empty; either bundle something or leave the "
                 "index out, because an empty index is a thing a reader has to "
                 "explain")
        # Everything under jarjar/ is either the index or something the index
        # names. A jar in there that nothing names is dead weight a player
        # downloads.
        indexed = {entry.get("path") for entry in entries}
        for name in names:
            if name.startswith("META-INF/jarjar/") and name.endswith(".jar") \
                    and name not in indexed:
                fail(f"{name} is carried inside the jar and the index does not name "
                     f"it, so it is downloaded and never loaded")

        for entry in entries:
            inside = entry.get("path")
            version = entry.get("version", {}).get("artifactVersion")
            span = entry.get("version", {}).get("range")
            artifact = entry.get("identifier", {}).get("artifact")
            group = entry.get("identifier", {}).get("group")
            if not (inside and version and span and artifact and group):
                fail(f"an index entry is missing part of its identity: {entry}")
                continue
            if inside not in names:
                fail(f"the index names {inside} and the jar does not contain it, so "
                     f"the loader looks for a file that is not there")
                continue
            if not satisfies(version, span):
                fail(f"{artifact} {version} does not satisfy its own declared range "
                     f"{span}, so the loader unpacks it and then refuses it")

            body = io.BytesIO(jar.read(inside))
            with zipfile.ZipFile(body) as inner:
                if TOML not in inner.namelist():
                    fail(f"{inside} has no {TOML}; whatever it is, NeoForge will not "
                         f"load it as a mod")
                    continue
                text = inner.read(TOML).decode("utf-8")
                mod_id = declared(text, "modId")
                stated = declared(text, "version")
                if stated != version:
                    fail(f"the index says {artifact} is {version} and the jar itself "
                         f"says {stated}; the loader believes the jar and the range "
                         f"was written against the other number")
                # The licence travels with it, and it came out of its own jar.
                folder = f"licences/{mod_id}/"
                carried = [n for n in names if n.startswith(folder)]
                if not carried:
                    fail(f"{mod_id} is redistributed inside this jar with no licence "
                         f"text, which is the one packaging mistake here that is not "
                         f"merely a bug")
                elif f"{folder}SOURCE.txt" not in names:
                    fail(f"{mod_id}'s licence is shipped with no pointer to its "
                         f"source or its authors")
                else:
                    for name in inner.namelist():
                        if name in ("LICENSE", "COPYING", "COPYING.LESSER") \
                                and folder + name not in names:
                            fail(f"{mod_id} carries {name} and this jar does not, so "
                                 f"part of its licence was left behind")
                if "THIRD-PARTY.md" not in names:
                    fail("nothing in the jar says which of it is somebody else's work")
                else:
                    listed = jar.read("THIRD-PARTY.md").decode("utf-8")
                    if artifact not in listed:
                        fail(f"{artifact} is bundled and THIRD-PARTY.md does not "
                             f"mention it")
        print(f"bundled jars       : {len(entries)} indexed, each with its licence and "
              f"a version range it satisfies")


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    for path in sys.argv[1:]:
        check(path)
    if problems:
        print(f"{len(problems)} PROBLEM(S):")
        for problem in problems:
            print("  - " + problem)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
