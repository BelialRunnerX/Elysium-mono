#!/usr/bin/env python3
"""
The work board: who is converting what, and what nobody has taken yet.

    python3 tools/board.py list                  what there is, and who has it
    python3 tools/board.py claim <module> --as <name>
    python3 tools/board.py release <module> --as <name>
    python3 tools/board.py done <module> --as <name>
    python3 tools/board.py mine --as <name>

Why this exists rather than a list in a document: two agents reading a
document at the same time both see the same unit unclaimed and both take it,
and the first anybody knows about it is a merge with two rewrites of the same
file in it. A claim here is a file created with O_EXCL, which the operating
system will only let one of them win.

The board's units come from `docs/MANIFEST.md` -- the jars still to convert,
grouped by the module that will absorb them. It is never edited by hand: a
module with nothing left to absorb leaves the board by having its manifest
rows marked DONE, which is the same edit the work itself needs.
"""
import argparse
import collections
import errno
import json
import os
import re
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST = os.path.join(ROOT, "docs/MANIFEST.md")
SPLITS = os.path.join(ROOT, "docs/SPLITS.md")
CLAIMS = os.path.join(ROOT, "docs/claims")

#: What a module's owner is allowed to touch. Anything outside this is
#: somebody else's unit, and two agents editing one file is the thing the
#: board exists to prevent.
def owned_by(module):
    return [
        f"{module}/",
        "docs/MANIFEST.md",
        # The brief tells an agent to write its rows here as well, and a tool
        # that forbids what the brief instructs is a tool people learn to
        # ignore. A jar that turns out to be four mods in a trench coat is
        # discovered while converting it and nowhere else.
        "docs/SPLITS.md",
    ]


#: Places anybody may add a file but nobody may edit somebody else's.
#:
#: Negative tests are named after the feature rather than the module -- the
#: arsenal alone has negtest_ranged, negtest_forge and negtest_dig -- so
#: ownership here cannot be worked out from the name. A new file is yours; an
#: existing one is whoever wrote it.
ADD_ONLY = ["tools/negtest/"]

#: Written by the board itself, so it is never anybody's trespass.
THE_BOARD = ["docs/claims/"]

#: Shared ground. Changing any of it means claiming `shared` as well, because
#: a change here can break every other module at once -- which is exactly what
#: happened the day one validator's helper stopped stripping comments.
SHARED = ["elysium-lib/", "validate.py", "check_", "compile.sh", "tools/",
          "docs/"]


def units():
    """Every module with work left, and what that work is.

    Two sources, because a jar that spans four modules is four units of work
    and giving it one row means nobody can take any of it without standing in
    three other people's directories. The manifest marks such a jar SPLIT and
    docs/SPLITS.md carries its pieces, one row apiece, in the same shape.
    """
    left = collections.defaultdict(list)
    for where in (MANIFEST, SPLITS):
        if not os.path.isfile(where):
            continue
        for line in open(where, encoding="utf-8"):
            row = re.match(r"\|\s*`([^`]+)`\s*\|\s*([A-Z]+)\s*\|\s*([a-z-]+)\s*\|",
                           line)
            if not row:
                continue
            jar, state, module = row.groups()
            if module.startswith("elysium-") \
                    and state not in ("DONE", "KEEP", "DROP", "SPLIT"):
                left[module].append(jar)
    return dict(left)


def finished_module(module):
    """Whether this is a module that exists but has nothing left to convert.

    The board's units come from the rows still to convert, so a module leaves
    it the moment its last row is marked DONE. That is right for conversion
    and wrong for everything after it: a finished module still gets bugs
    found in it, numbers re-balanced, and -- the case that produced this
    function -- a whole roster re-themed. Under the old rule such a module
    could never be claimed again, so `ownership.py` called every edit to it a
    trespass, and the only ways forward were to ignore the tool or to fake a
    row in the manifest. Both are worse than letting somebody hold a finished
    module and say so.

    It is still a claim: two agents cannot revise the same finished module at
    once, which is the whole point of the board.
    """
    return os.path.isdir(os.path.join(ROOT, module)) \
        and module.startswith("elysium-")


def claim_path(module):
    return os.path.join(CLAIMS, module + ".json")


def held():
    """Who holds what, right now."""
    os.makedirs(CLAIMS, exist_ok=True)
    out = {}
    for name in sorted(os.listdir(CLAIMS)):
        if not name.endswith(".json"):
            continue
        try:
            out[name[:-5]] = json.load(open(os.path.join(CLAIMS, name),
                                            encoding="utf-8"))
        except (OSError, ValueError):
            out[name[:-5]] = {"who": "unreadable", "since": 0}
    return out


def claim(module, who):
    """Take a unit, or fail because somebody else has it.

    O_CREAT|O_EXCL is the whole mechanism: the operating system decides which
    of two simultaneous claims wins, and the loser is told rather than
    silently sharing the unit.
    """
    os.makedirs(CLAIMS, exist_ok=True)
    path = claim_path(module)
    try:
        handle = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o644)
    except OSError as trouble:
        if trouble.errno != errno.EEXIST:
            raise
        current = held().get(module, {})
        print(f"{module} is already held by {current.get('who', 'somebody')}"
              f" (since {stamp(current.get('since', 0))})")
        return 1
    with os.fdopen(handle, "w", encoding="utf-8") as out:
        json.dump({"who": who, "since": int(time.time())}, out)
    print(f"{module} is yours. You may edit:")
    for where in owned_by(module):
        print("  " + where)
    print("Anything under " + ", ".join(SHARED) + " needs the `shared` unit too.")
    return 0


def release(module, who, finished=False):
    path = claim_path(module)
    if not os.path.isfile(path):
        print(f"{module} is not held by anybody")
        return 1
    current = json.load(open(path, encoding="utf-8"))
    if current.get("who") != who:
        print(f"{module} is held by {current.get('who')}, not by {who}. "
              f"Releasing somebody else's unit is how two agents end up in one "
              f"file.")
        return 1
    os.remove(path)
    if finished:
        still = units().get(module, [])
        if still:
            print(f"{module} released, but {len(still)} jar(s) are still PLANNED "
                  f"in the manifest: {', '.join(still[:3])}"
                  + ("..." if len(still) > 3 else ""))
            print("Mark them DONE as part of the work, or the board will hand the "
                  "unit straight back out.")
            return 1
        print(f"{module} is finished and released.")
    else:
        print(f"{module} released.")
    return 0


def stamp(when):
    return time.strftime("%Y-%m-%d %H:%M", time.gmtime(when)) if when else "?"


def show():
    left = units()
    taken = held()
    wide = max([len(m) for m in list(left) + list(taken)] + [12])
    print(f"{'unit':{wide}}  {'jars':>4}  held by")
    for module in sorted(set(list(left) + list(taken)),
                         key=lambda m: -len(left.get(m, []))):
        who = taken.get(module, {}).get("who", "")
        print(f"{module:{wide}}  {len(left.get(module, [])):>4}  "
              f"{who or '-'}")
    print()
    print(f"{sum(len(v) for v in left.values())} jar(s) left across "
          f"{len(left)} unit(s); {len(taken)} unit(s) held.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("what", choices=["list", "claim", "release", "done",
                                         "mine"])
    parser.add_argument("module", nargs="?")
    parser.add_argument("--as", dest="who", default=os.environ.get("ELYSIUM_AGENT"))
    args = parser.parse_args()

    if args.what == "list":
        show()
        return 0
    if args.what == "mine":
        if not args.who:
            print("say who you are: --as <name>, or set ELYSIUM_AGENT")
            return 2
        for module, current in held().items():
            if current.get("who") == args.who:
                print(module)
        return 0
    if not args.module or not args.who:
        print("claim, release and done each need a module and --as <name>")
        return 2
    if args.what == "claim" and args.module != "shared" \
            and args.module not in units() and not finished_module(args.module):
        print(f"{args.module} is not a module and has nothing to convert, so "
              f"there is nothing to claim. `list` shows what there is.")
        return 1
    if args.what == "claim":
        return claim(args.module, args.who)
    return release(args.module, args.who, finished=args.what == "done")


if __name__ == "__main__":
    sys.exit(main())
