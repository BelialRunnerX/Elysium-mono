#!/usr/bin/env python3
"""
A menu whose slot charges on take charges on a shift-click too.

    python3 check_quick_move.py

A result slot does its work in Slot.onTake: the bench's pay(), the
reforging table's take(chosen), the smithing table's paper. The game calls
onTake for a plain click by itself; for a shift-click it calls the menu's
own quickMoveStack and nothing else, and every vanilla quickMoveStack ends
by calling slot.onTake for what moved. A menu that overrides quickMoveStack
and forgets that line hands the result over for nothing and leaves the
inputs where they were — a duplication, silent, found by hand at the gem
cutting table on 28 Sep 2026 (the dust and the rune stayed, the cut rune
came out) and then at the augmenting and smithing tables.

So: every class in every module that both overrides quickMoveStack and
declares a slot with onTake has to call onTake inside quickMoveStack. A
module validator cannot see this because the contract is vanilla's, and the
same shape recurs in every module with a bench.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
COMMENT = re.compile(r"//[^\n]*|/\*.*?\*/", re.S)


def read(path):
    return COMMENT.sub(" ", open(path, encoding="utf-8").read())


def body_of(text, signature):
    """The text of one method from its signature to the matching brace."""
    start = text.find(signature)
    if start < 0:
        return ""
    depth = 0
    for i in range(text.find("{", start), len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return text[start:i + 1]
    return text[start:]


def main():
    problems = []
    menus = 0
    charging = 0
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
                if "ItemStack quickMoveStack(" not in text:
                    continue
                menus += 1
                if "public void onTake(" not in text:
                    continue
                charging += 1
                quick = body_of(text, "ItemStack quickMoveStack(")
                if ".onTake(player, " not in quick:
                    rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
                    problems.append(f"{rel}: a slot here charges in onTake, and quickMoveStack never calls it, so a "
                                    f"shift-click takes the result for nothing and leaves the inputs in place")
    if problems:
        print(f"{len(problems)} PROBLEM(S):")
        for problem in problems:
            print("  - " + problem)
        return 1
    print(f"quick move         : {menus} menu(s) with their own quickMoveStack, {charging} of them charging in a "
          f"slot's onTake, every one of those calling it on a shift-click")
    return 0


if __name__ == "__main__":
    sys.exit(main())
