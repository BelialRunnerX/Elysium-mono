#!/usr/bin/env python3
"""
Whether what you have changed is what you claimed.

    python3 tools/ownership.py --as <name>

Run it before you hand work back. It asks git what has changed and compares
that against the units you hold on the board.

Why it is worth a tool: "stay inside your module" is the sort of rule everyone
agrees with and nobody can check by eye once a change is forty files. The one
that bites is not somebody rewriting another module on purpose -- it is a
generator, a formatter or a careless `sed -i` reaching one directory further
than it meant to, and nothing saying so until two people's work will not merge.
"""
import argparse
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import board                                             # noqa: E402


def changed():
    """Every path git can see a change to, tracked or not."""
    ran = subprocess.run(["git", "status", "--porcelain", "--untracked-files=all"],
                         cwd=ROOT, capture_output=True, text=True)
    if ran.returncode != 0:
        print("git could not be asked what has changed:\n" + ran.stderr.strip())
        sys.exit(2)
    paths = []
    for line in ran.stdout.splitlines():
        if not line.strip():
            continue
        state, path = line[:2], line[3:].strip().strip('"')
        # A rename shows as "old -> new"; both ends count as touched.
        if " -> " in path:
            for part in path.split(" -> "):
                paths.append((state, part.strip()))
        else:
            paths.append((state, path))
    return sorted(set(paths))


def allowed(paths, mine):
    """Split what changed into what you may have changed and what you may not."""
    permitted, trespass = [], []
    prefixes = []
    for module in mine:
        if module == "shared":
            prefixes.extend(board.SHARED)
        else:
            prefixes.extend(board.owned_by(module))
    for state, path in paths:
        if any(path.startswith(prefix) for prefix in board.THE_BOARD):
            continue
        # Something you hold outright is yours whatever else covers it. The
        # add-only rule is for agents who hold a module and nothing more; it
        # used to be asked first, and then holding `shared` — which is what
        # the board tells you to claim before touching anything under
        # tools/ — still could not edit tools/negtest/README.md. A rule that
        # forbids what the board instructs is a rule people learn to work
        # around.
        if any(path.startswith(prefix) for prefix in prefixes):
            permitted.append(path)
            continue
        # A file you have just added under an add-only place is yours; one
        # that was already there is whoever wrote it, and editing it is the
        # trespass this is looking for.
        if any(path.startswith(prefix) for prefix in board.ADD_ONLY):
            (permitted if state.strip() == "??" else trespass).append(path)
            continue
        trespass.append(path)
    return permitted, trespass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--as", dest="who",
                        default=os.environ.get("ELYSIUM_AGENT"))
    args = parser.parse_args()
    if not args.who:
        print("say who you are: --as <name>, or set ELYSIUM_AGENT")
        return 2

    mine = [module for module, current in board.held().items()
            if current.get("who") == args.who]
    if not mine:
        print(f"{args.who} holds nothing on the board, so every change is "
              f"somebody else's. Claim a unit first: "
              f"python3 tools/board.py claim <module> --as {args.who}")
        return 1

    paths = changed()
    if not paths:
        print(f"{args.who} holds {', '.join(mine)} and has changed nothing yet.")
        return 0

    permitted, trespass = allowed(paths, mine)
    print(f"{args.who} holds: {', '.join(mine)}")
    print(f"changed: {len(paths)} path(s), {len(permitted)} inside those units")
    if trespass:
        print(f"{len(trespass)} path(s) outside anything {args.who} holds:")
        for path in trespass[:20]:
            print("  " + path)
        if len(trespass) > 20:
            print(f"  ...and {len(trespass) - 20} more")
        print()
        print("Either claim the unit that owns them, or put them back. Two agents "
              "in one file is a merge somebody has to unpick by hand.")
        return 1
    print("everything changed is inside what you hold")
    return 0


if __name__ == "__main__":
    sys.exit(main())
