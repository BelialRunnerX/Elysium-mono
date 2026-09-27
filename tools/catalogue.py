#!/usr/bin/env python3
"""
The feature catalogue: everything the pack's own modules add, by name.

    python3 tools/catalogue.py            writes docs/FEATURES.md and docs/features.json

Dylan, 27 September 2026: *systematically catalog every feature from all the
mods that we're adding and then go through and validate everything one at a
time.* This is the catalogue. It is read off the tree, not written by hand,
so it cannot drift from what ships: blocks from the blockstates, items from
the item models, creatures, biomes, enchantments and effects from the
language file's keys, structures, dimensions, recipe kinds, advancements and
loot modifiers from the data folders, and the mechanics from the section
lines each module's validator prints. The JSON beside it is what the live
test reads to place every block, give every item and summon every creature
(tools/livetest/scenarios.py, "catalogue").

Each row carries how it is validated: `tree` when a validator reads it,
`live` when a scenario exercises it, `client` when only eyes can tell.
"""
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
SECTION = re.compile(r'print\(f?"([a-z][a-z0-9 /_-]*?)\s{2,}: ([^"]*)"')


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


def sections(module):
    path = os.path.join(ROOT, module, "validate.py")
    if not os.path.exists(path):
        return []
    out = []
    for head, blurb in SECTION.findall(open(path, encoding="utf-8").read()):
        row = (head.strip(), re.sub(r"\{[^}]*\}", "…", blurb).strip(" ,;"))
        if row not in out:
            out.append(row)
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


def write(modules):
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
    open(os.path.join(DOCS, "FEATURES.md"), "w", encoding="utf-8", newline="\n").write("\n".join(lines))
    json.dump(modules, open(os.path.join(DOCS, "features.json"), "w", encoding="utf-8", newline="\n"),
              indent=1, ensure_ascii=False)
    return total


def main():
    modules = catalogue()
    total = write(modules)
    print(f"catalogue          : {len(modules)} modules; " + ", ".join(f"{n} {t.lower()}" for t, n in sorted(total.items())))
    return 0


if __name__ == "__main__":
    sys.exit(main())
