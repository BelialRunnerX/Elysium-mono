#!/usr/bin/env python3
"""
The feature catalogue: everything the pack's own modules add, by name.

    python3 tools/catalogue.py            writes docs/FEATURES.md and docs/features.json
    python3 tools/catalogue.py --out DIR  writes the same two files into DIR instead
                                          (check_features.py compares those with docs/)

Dylan, 27 September 2026: *systematically catalog every feature from all the
mods that we're adding and then go through and validate everything one at a
time.* This is the catalogue. It is read off the tree, not written by hand,
so it cannot drift from what ships: blocks from the blockstates, items from
the item models, creatures, biomes, enchantments and effects from the
language file's keys, structures, dimensions, recipe kinds, advancements and
loot modifiers from the data folders, and the mechanics from the section
lines each module's validator prints as results (not its skip notices or the
detail of a failure). The JSON beside it is what the live
test reads to place every block, give every item and summon every creature
(tools/livetest/scenarios.py, "catalogue").

Each row carries how it is validated: `tree` when a validator reads it,
`live` when a scenario exercises it, `client` when only eyes can tell.
"""
import ast
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DOCS = os.path.join(ROOT, "docs")

LANG_KINDS = (("entity", "Creatures"), ("biome", "Biomes"), ("enchantment", "Enchantments"),
              ("effect", "Effects"), ("itemGroup", "Creative tabs"), ("attribute", "Attributes"))
DATA_KINDS = (("worldgen/structure", "Structures"), ("dimension", "Dimensions"),
              ("worldgen/configured_feature", "World features"), ("advancement", "Advancements"),
              ("loot_modifiers", "Loot modifiers"), ("damage_type", "Damage types"),
              ("worldgen/biome", "Biome definitions"), ("structure", "Structure templates"))
# A section line: "the name    : what it found", the name padded to the colon -
# by one space when it is 18 letters or more, and a name may carry a capital,
# an apostrophe or a comma ("the Vale", "maker's marks", "the pots, filled").
SECTION = re.compile(r"([a-z][^:]*?)\s+: (.*)")
# What a validator says when a section did not run: no prerequisite (a jar, the
# compiled classes, Pillow, the source folder), so nothing was checked. A skip
# notice is not a mechanic; listing one would claim a check that never happened.
NOT_RUN = re.compile(r"\b(?:not run|skipped|unchecked|not (?:checked|compared|cross-checked|looked up"
                     r"|measured|scanned|here|at|present))\b")


def modid_of(module):
    """The mod id and description, from the module's gradle.properties (the mods
    file is a template of those); the assets folder when the properties have none."""
    props = {}
    path = os.path.join(ROOT, module, "gradle.properties")
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            if "=" in line and not line.lstrip().startswith("#"):
                key, _eq, value = line.partition("=")
                props[key.strip()] = value.strip()
    modid = props.get("mod_id")
    if not modid:
        assets = os.path.join(ROOT, module, "src", "main", "resources", "assets")
        found = [d for d in os.listdir(assets)] if os.path.isdir(assets) else []
        modid = found[0] if found else None
    return modid, props.get("mod_description", ""), props.get("mod_name", module)


def names(directory, suffix=".json"):
    if not os.path.isdir(directory):
        return []
    return sorted(n[: -len(suffix)] for n in os.listdir(directory) if n.endswith(suffix))


def lang(module, modid):
    path = os.path.join(ROOT, module, "src", "main", "resources", "assets", modid, "lang", "en_us.json")
    if not os.path.exists(path):
        return {}
    return json.load(open(path, encoding="utf-8"))


def shown(node):
    """The text a print's first argument shows, each computed part an ellipsis:
    a string however it is written (literals side by side, an f-string, a + of
    them); None when it is not a string at all."""
    if isinstance(node, ast.Constant):
        return node.value if isinstance(node.value, str) else None
    if isinstance(node, ast.JoinedStr):
        return "".join(part.value if isinstance(part, ast.Constant) else "…" for part in node.values)
    if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add):
        left, right = shown(node.left), shown(node.right)
        if left is None and right is None:
            return None
        return (left if left is not None else "…") + (right if right is not None else "…")
    return None


def failing(stmt):
    """A statement that reports a failure whenever the block holding it runs."""
    if isinstance(stmt, ast.Raise):
        return True
    if not (isinstance(stmt, ast.Expr) and isinstance(stmt.value, ast.Call)):
        return False
    func = stmt.value.func
    return (isinstance(func, ast.Name) and func.id == "fail") or (
        isinstance(func, ast.Attribute) and func.attr == "exit"
        and isinstance(func.value, ast.Name) and func.value.id == "sys")


def sections(module):
    """The mechanics a module's validator executes: the section lines it prints,
    read with Python's own parser, so a line written over several pieces of
    string is read whole. A line is listed only when it is a result: not one
    in a block that reports a failure (it is the failure's detail), and not one
    that says the section did not run (NOT_RUN: a skip notice)."""
    path = os.path.join(ROOT, module, "validate.py")
    if not os.path.exists(path):
        return []
    tree = ast.parse(open(path, encoding="utf-8").read(), path)
    found = []
    for node in ast.walk(tree):
        for field in ("body", "orelse", "finalbody"):
            block = getattr(node, field, None)
            if not isinstance(block, list):
                continue
            # A failing block's prints are the failure's detail: that is a
            # branch (an if, an else, a handler). The file's own top level is
            # not one; a sys.exit() or fail() written there would otherwise
            # drop every top-level row of the module.
            if not isinstance(node, ast.Module) and any(failing(stmt) for stmt in block):
                continue
            for stmt in block:
                call = stmt.value if isinstance(stmt, ast.Expr) else None
                if not (isinstance(call, ast.Call) and isinstance(call.func, ast.Name)
                        and call.func.id == "print" and call.args):
                    continue
                # one print can write two section lines ("...\nconduit shape : ...")
                for at, line in enumerate((shown(call.args[0]) or "").split("\n")):
                    match = SECTION.match(line)
                    if match and not NOT_RUN.search(match.group(2)):
                        found.append((stmt.lineno, stmt.col_offset, at,
                                      match.group(1).strip(), match.group(2).strip(" ,;")))
    out = []
    for _line, _col, _at, head, blurb in sorted(found):
        if (head, blurb) not in out:
            out.append((head, blurb))
    return out


def catalogue():
    modules = {}
    for module in sorted(os.listdir(ROOT)):
        if not module.startswith("elysium-") or not os.path.isdir(os.path.join(ROOT, module, "src")):
            continue
        modid, desc, title = modid_of(module)
        if not modid:
            continue
        res = os.path.join(ROOT, module, "src", "main", "resources")
        words = lang(module, modid)
        entry = {"modid": modid, "name": title, "description": desc, "kinds": {}, "mechanics": sections(module)}
        blocks = names(os.path.join(res, "assets", modid, "blockstates"))
        items = names(os.path.join(res, "assets", modid, "models", "item"))
        entry["kinds"]["Blocks"] = [(b, words.get(f"block.{modid}.{b}", "")) for b in blocks]
        entry["kinds"]["Items"] = [(i, words.get(f"item.{modid}.{i}", words.get(f"block.{modid}.{i}", "")))
                                   for i in items if i not in blocks]
        # every item that exists: a block item, or an item with a name; the
        # rest of models/item are predicate models (a bow half drawn)
        overridden = set()                    # models an item switches to (a full crate, a drawn bow)
        for i in items:
            try:
                model = json.load(open(os.path.join(res, "assets", modid, "models", "item", i + ".json"), encoding="utf-8"))
            except (OSError, ValueError):
                continue
            for override in model.get("overrides", []):
                target = str(override.get("model", ""))
                if target.startswith(f"{modid}:item/"):
                    overridden.add(target.split("/", 1)[1])
        entry["item_models"] = [i for i in items
                                if (i in blocks or f"item.{modid}.{i}" in words) and i not in overridden]
        for prefix, title in LANG_KINDS:
            found = sorted((k.split(".", 2)[2], v) for k, v in words.items()
                           if k.startswith(f"{prefix}.{modid}.") and k.count(".") == 2)
            if found:
                entry["kinds"][title] = found
        for folder, title in DATA_KINDS:
            found = names(os.path.join(res, "data", modid, *folder.split("/")))
            if not found and folder == "structure":
                found = names(os.path.join(res, "data", modid, "structure"), ".nbt")
            if found:
                entry["kinds"][title] = [(f, "") for f in found]
        recipe_dir = os.path.join(res, "data", modid, "recipe")
        kinds = sorted(d for d in os.listdir(recipe_dir) if os.path.isdir(os.path.join(recipe_dir, d))) \
            if os.path.isdir(recipe_dir) else []
        if kinds:
            entry["kinds"]["Recipe kinds"] = [(k, f"{len(names(os.path.join(recipe_dir, k)))} recipes") for k in kinds]
        entry["recipes"] = sum(len(f) for _r, _d, f in os.walk(recipe_dir)) if os.path.isdir(recipe_dir) else 0
        modules[module] = entry
    return modules


def write(modules, docs=DOCS):
    lines = ["# The feature catalogue", "",
             "Everything the pack's own modules add, read off the tree by `tools/catalogue.py`.",
             "Regenerate it after any change; do not edit by hand. How each kind is validated:",
             "", "* **Blocks, items, creatures**: the `catalogue` live scenario places every block,",
             "  gives every item and summons every creature on the test server (CONVERSION.md §17);",
             "  the validators check every model, texture, name and recipe in the tree.",
             "* **Mechanics**: the lines each module's validator prints, one per rule it executes;",
             "  machines, runs and grids are also exercised live.",
             "* **Client-only** (screens, HUD, renderers): the client stages in PLAYTEST.md.", ""]
    total = {}
    for module, entry in modules.items():
        lines.append(f"## {entry['name']} — {module} (`{entry['modid']}`)")
        lines.append("")
        if entry["description"]:
            lines.append(entry["description"])
            lines.append("")
        counts = ", ".join(f"{len(rows)} {title.lower()}" for title, rows in entry["kinds"].items())
        lines.append(f"{counts}; {entry['recipes']} recipes; {len(entry['mechanics'])} validated mechanics.")
        lines.append("")
        for title, rows in entry["kinds"].items():
            total[title] = total.get(title, 0) + len(rows)
            lines.append(f"### {title} ({len(rows)})")
            lines.append("")
            lines.append("| id | name |")
            lines.append("|---|---|")
            for ident, name in rows:
                lines.append(f"| `{ident}` | {name} |")
            lines.append("")
        if entry["mechanics"]:
            lines.append(f"### Mechanics the validator executes ({len(entry['mechanics'])})")
            lines.append("")
            for head, blurb in entry["mechanics"]:
                lines.append(f"* **{head}**: {blurb}")
            lines.append("")
    summary = ["", "## Totals", ""] + [f"* {title}: {n}" for title, n in sorted(total.items())] + [""]
    lines[11:11] = summary
    with open(os.path.join(docs, "FEATURES.md"), "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines))
    with open(os.path.join(docs, "features.json"), "w", encoding="utf-8", newline="\n") as handle:
        json.dump(modules, handle, indent=1, ensure_ascii=False)
    return total


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    docs = DOCS
    if len(argv) == 2 and argv[0] == "--out":
        docs = argv[1]
        os.makedirs(docs, exist_ok=True)
    elif argv:
        print(__doc__)
        return 2
    modules = catalogue()
    total = write(modules, docs)
    print(f"catalogue          : {len(modules)} modules; " + ", ".join(f"{n} {t.lower()}" for t, n in sorted(total.items())))
    return 0


if __name__ == "__main__":
    sys.exit(main())
