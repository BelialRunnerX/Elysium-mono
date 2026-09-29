#!/usr/bin/env python3
"""
No two keys in the pack, or in the mods beside it, share a default.

    python3 check_keys.py

A KeyMapping's default is one number in one file, and the game does nothing
about two mappings claiming the same key: both fire. The space module's
Launch key was G, and G is Curios' inventory key, so a pilot pressing Launch
also opened the trinket screen over the countdown — found by hand at the
launch pad, 28 Sep 2026, when the stage's G opened Curios and no rocket
moved. This walks every KeyMapping in every module, resolves its default
(a literal, or a constant declared in the same file), and holds the set to
itself and to the defaults of the jars the pack ships beside: Curios, JEI,
and vanilla's own.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
COMMENT = re.compile(r"//[^\n]*|/\*.*?\*/", re.S)

# GLFW key codes, by the letter or name a player reads.
GLFW = {**{chr(c): c for c in range(65, 91)}, **{str(d): 48 + d for d in range(10)},
        "LEFT_ALT": 342, "RIGHT_ALT": 346, "LEFT_SHIFT": 340, "LEFT_CONTROL": 341, "TAB": 258, "SPACE": 32}
NAMES = {v: k for k, v in GLFW.items()}

# What the jars beside the pack bind by default (no modifier). Vanilla's letters
# are the ones a player has under their fingers; a mod key on them is a fight.
OTHERS = {
    71: "Curios: open inventory (G)",
    82: "JEI: show recipe (R)",
    85: "JEI: show uses (U)",
    65: "JEI: bookmark (A) / vanilla: strafe left",
    87: "vanilla: forward (W)", 83: "vanilla: back (S)", 68: "vanilla: strafe right (D)",
    69: "vanilla: inventory (E)", 81: "vanilla: drop (Q)", 84: "vanilla: chat (T)",
    70: "vanilla: swap hands (F)", 76: "vanilla: advancements (L)", 67: "vanilla: crouch... no, vanilla uses shift; C is free",
    32: "vanilla: jump (space)", 258: "vanilla: player list (tab)",
}
OTHERS.pop(67)      # C is free in vanilla; the line above was a note, not a claim

MAPPING = re.compile(r'new KeyMapping\(\s*"([^"]+)"\s*,\s*InputConstants\.Type\.(\w+)\s*,\s*([^,]+?)\s*,')
CONST = re.compile(r"static final int (\w+) = (\d+);")


def read(path):
    return COMMENT.sub(" ", open(path, encoding="utf-8").read())


def main():
    problems = []
    keys = {}       # code -> (mapping name, file)
    count = 0
    for module in sorted(os.listdir(ROOT)):
        src = os.path.join(ROOT, module, "src/main/java")
        if not module.startswith("elysium-") or not os.path.isdir(src):
            continue
        for dirpath, _, filenames in os.walk(src):
            for filename in filenames:
                if not filename.endswith(".java"):
                    continue
                path = os.path.join(dirpath, filename)
                text = read(path)
                if "new KeyMapping(" not in text:
                    continue
                consts = {name: int(value) for name, value in CONST.findall(text)}
                rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
                for name, kind, default in MAPPING.findall(text):
                    count += 1
                    if kind != "KEYSYM":
                        continue
                    default = default.strip()
                    code = consts.get(default.split(".")[-1]) if re.fullmatch(r"[\w.]+", default) else None
                    if code is None:
                        try:
                            code = int(default)
                        except ValueError:
                            problems.append(f"{rel}: the default of {name} is {default}, which this check cannot read; "
                                            f"declare it as a static final int in the same file")
                            continue
                    if code in keys:
                        problems.append(f"{rel}: {name} defaults to {NAMES.get(code, code)}, the same key as "
                                        f"{keys[code][0]} ({keys[code][1]}); both would fire")
                    keys[code] = (name, rel)
                    if code in OTHERS:
                        problems.append(f"{rel}: {name} defaults to {NAMES.get(code, code)}, which is {OTHERS[code]}; "
                                        f"a player pressing it gets both")
    if count == 0:
        problems.append("no KeyMapping found in any module, which cannot be right: the sheet, the launch and the jet have keys")
    if problems:
        print(f"{len(problems)} PROBLEM(S):")
        for problem in problems:
            print("  - " + problem)
        return 1
    print(f"keys               : {count} key mapping(s) across the modules, no two on one default and none on a key "
          f"Curios, JEI or vanilla already binds")
    return 0


if __name__ == "__main__":
    sys.exit(main())
