#!/usr/bin/env python3
"""
Every file elysium-flora ships that is a table rather than a decision.

Blockstates, models, loot tables, recipes, tags, worldgen and the language
file for two crops, their wild forms, one tree's worth of wood and two things
you can stack. The decisions live in rules/FloraRules.java and are read back
here rather than restated.
"""
import json
import os
import re
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumflora"
RES = os.path.join(MOD, "src/main/resources")
DATA = os.path.join(RES, "data", NS)
ASSETS = os.path.join(RES, "assets", NS)
JAVA = os.path.join(MOD, "src/main/java/com/elysium/flora")

RULES = open(os.path.join(JAVA, "rules/FloraRules.java"), encoding="utf-8").read()


def constant(name):
    m = re.search(r"public static final int " + name + r" = ([^;]+);", RULES)
    if not m:
        raise SystemExit("FloraRules has no int constant " + name)
    text = m.group(1).strip()
    return int(text) if text.isdigit() else eval(text, {}, {
        "COAL_TICKS": 1600, "STAGES": 8})


STAGES = constant("STAGES")
MATURE = STAGES - 1
FIBRE_PER_STRING = constant("FIBRE_PER_STRING")
TUFT_PER_STRING = constant("TUFT_PER_STRING")
WILD_TRIES = constant("WILD_TRIES")
WILD_PATCH = constant("WILD_PATCH")

CROPS = [
    # (name, the item it gives, the biomes its wild form grows in)
    ("loomflax", "loomflax_fibre", FIBRE_PER_STRING,
     ["#minecraft:is_overworld"]),
    ("downbloom", "downbloom_tuft", TUFT_PER_STRING,
     ["#minecraft:is_overworld"]),
]

# The wood set: name -> the vanilla model parent family it follows.
WOOD = {
    "emberwood_log": "column",
    "emberwood_wood": "column_all",
    "stripped_emberwood_log": "column_stripped",
    "stripped_emberwood_wood": "column_all_stripped",
    "emberwood_planks": "cube",
    "emberwood_leaves": "cube",
    "emberwood_charcoal_block": "cube",
    "downbloom_bale": "bale",
    "emberwood_sapling": "cross",
    "emberwood_slab": "slab",
    "emberwood_stairs": "stairs",
    "emberwood_fence": "fence",
    "emberwood_fence_gate": "fence_gate",
    "emberwood_door": "door",
    "emberwood_trapdoor": "trapdoor",
    "emberwood_button": "button",
    "emberwood_pressure_plate": "pressure_plate",
}


def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if isinstance(content, (dict, list)):
        content = json.dumps(content, indent=2) + "\n"
    open(path, "w", encoding="utf-8").write(content)


def clean():
    for sub in ("blockstates", "models", "lang"):
        shutil.rmtree(os.path.join(ASSETS, sub), ignore_errors=True)
    for sub in ("worldgen", "loot_table", "recipe", "tags", "neoforge"):
        shutil.rmtree(os.path.join(DATA, sub), ignore_errors=True)
    shutil.rmtree(os.path.join(RES, "data", "minecraft"), ignore_errors=True)
    shutil.rmtree(os.path.join(RES, "data", "c"), ignore_errors=True)


def title(word):
    return " ".join(p.capitalize() for p in word.replace("_", " ").split())


# ---------------------------------------------------------------------------
def crops():
    for name, gives, per, biomes in CROPS:
        write(os.path.join(ASSETS, f"blockstates/{name}.json"), {
            "variants": {f"age={age}": {"model": f"{NS}:block/{name}_stage{age}"}
                         for age in range(STAGES)}
        })
        for age in range(STAGES):
            write(os.path.join(ASSETS, f"models/block/{name}_stage{age}.json"), {
                "parent": "minecraft:block/crop",
                "textures": {"crop": f"{NS}:block/{name}_stage{age}"}
            })
        write(os.path.join(ASSETS, f"blockstates/wild_{name}.json"),
              {"variants": {"": {"model": f"{NS}:block/wild_{name}"}}})
        write(os.path.join(ASSETS, f"models/block/wild_{name}.json"), {
            "parent": "minecraft:block/cross",
            "textures": {"cross": f"{NS}:block/wild_{name}"}
        })
        # The wild form's item is the block, drawn flat like a sapling's.
        write(os.path.join(ASSETS, f"models/item/wild_{name}.json"), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"{NS}:block/wild_{name}"}
        })
        for item in (f"{name}_seeds", gives):
            write(os.path.join(ASSETS, f"models/item/{item}.json"), {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": f"{NS}:item/{item}"}
            })


def simple_item(name):
    write(os.path.join(ASSETS, f"models/item/{name}.json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"{NS}:item/{name}"}
    })


def block_item(name, parent=None):
    write(os.path.join(ASSETS, f"models/item/{name}.json"),
          {"parent": parent or f"{NS}:block/{name}"})


def wood():
    def tex(name):
        return f"{NS}:block/{name}"

    for name, kind in WOOD.items():
        if kind == "column":
            write(os.path.join(ASSETS, f"models/block/{name}.json"), {
                "parent": "minecraft:block/cube_column",
                "textures": {"end": tex(name + "_top"), "side": tex(name)}})
            axis_states(name, name)
            block_item(name)
        elif kind == "column_stripped":
            write(os.path.join(ASSETS, f"models/block/{name}.json"), {
                "parent": "minecraft:block/cube_column",
                "textures": {"end": tex(name + "_top"), "side": tex(name)}})
            axis_states(name, name)
            block_item(name)
        elif kind in ("column_all", "column_all_stripped"):
            bark = name.replace("_wood", "_log")
            write(os.path.join(ASSETS, f"models/block/{name}.json"), {
                "parent": "minecraft:block/cube_column",
                "textures": {"end": tex(bark), "side": tex(bark)}})
            axis_states(name, name)
            block_item(name)
        elif kind == "bale":
            write(os.path.join(ASSETS, f"models/block/{name}.json"), {
                "parent": "minecraft:block/cube_column",
                "textures": {"end": tex(name + "_top"), "side": tex(name + "_side")}})
            axis_states(name, name)
            block_item(name)
        elif kind == "cube":
            write(os.path.join(ASSETS, f"models/block/{name}.json"), {
                "parent": "minecraft:block/cube_all",
                "textures": {"all": tex(name)}})
            write(os.path.join(ASSETS, f"blockstates/{name}.json"),
                  {"variants": {"": {"model": f"{NS}:block/{name}"}}})
            block_item(name)
        elif kind == "cross":
            write(os.path.join(ASSETS, f"models/block/{name}.json"), {
                "parent": "minecraft:block/cross",
                "textures": {"cross": tex(name)}})
            write(os.path.join(ASSETS, f"blockstates/{name}.json"),
                  {"variants": {"": {"model": f"{NS}:block/{name}"}}})
            simple_item(name)
            write(os.path.join(ASSETS, f"models/item/{name}.json"), {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": tex(name)}})
        elif kind == "slab":
            planks = tex("emberwood_planks")
            write(os.path.join(ASSETS, f"models/block/{name}.json"), {
                "parent": "minecraft:block/slab",
                "textures": {"bottom": planks, "top": planks, "side": planks}})
            write(os.path.join(ASSETS, f"models/block/{name}_top.json"), {
                "parent": "minecraft:block/slab_top",
                "textures": {"bottom": planks, "top": planks, "side": planks}})
            write(os.path.join(ASSETS, f"blockstates/{name}.json"), {"variants": {
                "type=bottom": {"model": f"{NS}:block/{name}"},
                "type=top": {"model": f"{NS}:block/{name}_top"},
                "type=double": {"model": f"{NS}:block/emberwood_planks"}}})
            block_item(name)
        elif kind == "stairs":
            planks = tex("emberwood_planks")
            textures = {"bottom": planks, "top": planks, "side": planks}
            for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"),
                                   ("_outer", "outer_stairs")):
                write(os.path.join(ASSETS, f"models/block/{name}{suffix}.json"),
                      {"parent": f"minecraft:block/{parent}", "textures": textures})
            write(os.path.join(ASSETS, f"blockstates/{name}.json"),
                  {"variants": stair_variants(name)})
            block_item(name)
        elif kind == "fence":
            planks = tex("emberwood_planks")
            for suffix, parent in (("_post", "fence_post"), ("_side", "fence_side"),
                                   ("_inventory", "fence_inventory")):
                write(os.path.join(ASSETS, f"models/block/{name}{suffix}.json"),
                      {"parent": f"minecraft:block/{parent}",
                       "textures": {"texture": planks}})
            write(os.path.join(ASSETS, f"blockstates/{name}.json"),
                  {"multipart": fence_multipart(name)})
            block_item(name, f"{NS}:block/{name}_inventory")
        elif kind == "fence_gate":
            planks = tex("emberwood_planks")
            for suffix, parent in (("", "template_fence_gate"),
                                   ("_open", "template_fence_gate_open"),
                                   ("_wall", "template_fence_gate_wall"),
                                   ("_wall_open", "template_fence_gate_wall_open")):
                write(os.path.join(ASSETS, f"models/block/{name}{suffix}.json"),
                      {"parent": f"minecraft:block/{parent}",
                       "textures": {"texture": planks}})
            write(os.path.join(ASSETS, f"blockstates/{name}.json"),
                  {"variants": gate_variants(name)})
            block_item(name)
        elif kind == "door":
            write(os.path.join(ASSETS, f"blockstates/{name}.json"),
                  {"variants": door_variants(name)})
            for suffix in ("_bottom_left", "_bottom_left_open", "_bottom_right",
                           "_bottom_right_open", "_top_left", "_top_left_open",
                           "_top_right", "_top_right_open"):
                write(os.path.join(ASSETS, f"models/block/{name}{suffix}.json"), {
                    "parent": f"minecraft:block/door{suffix}",
                    "textures": {"top": tex(name + "_top"),
                                 "bottom": tex(name + "_bottom")}})
            simple_item(name)
        elif kind == "trapdoor":
            textures = {"texture": tex(name)}
            for suffix, parent in (("_bottom", "template_orientable_trapdoor_bottom"),
                                   ("_top", "template_orientable_trapdoor_top"),
                                   ("_open", "template_orientable_trapdoor_open")):
                write(os.path.join(ASSETS, f"models/block/{name}{suffix}.json"),
                      {"parent": f"minecraft:block/{parent}", "textures": textures})
            write(os.path.join(ASSETS, f"blockstates/{name}.json"),
                  {"variants": trapdoor_variants(name)})
            block_item(name, f"{NS}:block/{name}_bottom")
        elif kind == "button":
            planks = tex("emberwood_planks")
            for suffix, parent in (("", "button"), ("_pressed", "button_pressed"),
                                   ("_inventory", "button_inventory")):
                write(os.path.join(ASSETS, f"models/block/{name}{suffix}.json"),
                      {"parent": f"minecraft:block/{parent}",
                       "textures": {"texture": planks}})
            write(os.path.join(ASSETS, f"blockstates/{name}.json"),
                  {"variants": button_variants(name)})
            block_item(name, f"{NS}:block/{name}_inventory")
        elif kind == "pressure_plate":
            planks = tex("emberwood_planks")
            for suffix, parent in (("", "pressure_plate_up"),
                                   ("_down", "pressure_plate_down")):
                write(os.path.join(ASSETS, f"models/block/{name}{suffix}.json"),
                      {"parent": f"minecraft:block/{parent}",
                       "textures": {"texture": planks}})
            write(os.path.join(ASSETS, f"blockstates/{name}.json"), {"variants": {
                "powered=false": {"model": f"{NS}:block/{name}"},
                "powered=true": {"model": f"{NS}:block/{name}_down"}}})
            block_item(name)


def axis_states(name, model):
    write(os.path.join(ASSETS, f"blockstates/{name}.json"), {"variants": {
        "axis=y": {"model": f"{NS}:block/{model}"},
        "axis=z": {"model": f"{NS}:block/{model}", "x": 90},
        "axis=x": {"model": f"{NS}:block/{model}", "x": 90, "y": 90}}})


def stair_variants(name):
    out = {}
    for facing, y in (("north", 270), ("east", 0), ("south", 90), ("west", 180)):
        for half in ("bottom", "top"):
            for shape, suffix in (("straight", ""), ("inner_left", "_inner"),
                                  ("inner_right", "_inner"), ("outer_left", "_outer"),
                                  ("outer_right", "_outer")):
                key = f"facing={facing},half={half},shape={shape}"
                model = {"model": f"{NS}:block/{name}{suffix}"}
                turn = y
                if shape.endswith("_left"):
                    turn = (y + 270) % 360
                if turn:
                    model["y"] = turn
                if half == "top":
                    model["x"] = 180
                    model["uvlock"] = True
                elif shape != "straight":
                    model["uvlock"] = True
                out[key] = model
    return out


def fence_multipart(name):
    parts = [{"apply": {"model": f"{NS}:block/{name}_post"}}]
    for side, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        apply = {"model": f"{NS}:block/{name}_side", "uvlock": True}
        if y:
            apply["y"] = y
        parts.append({"when": {side: "true"}, "apply": apply})
    return parts


def gate_variants(name):
    out = {}
    for facing, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
        for in_wall in ("false", "true"):
            for open_state in ("false", "true"):
                suffix = "_wall" if in_wall == "true" else ""
                if open_state == "true":
                    suffix += "_open"
                model = {"model": f"{NS}:block/{name}{suffix}", "uvlock": True}
                if y:
                    model["y"] = y
                out[f"facing={facing},in_wall={in_wall},open={open_state}"] = model
    return out


def door_variants(name):
    out = {}
    for facing, y in (("east", 0), ("south", 90), ("west", 180), ("north", 270)):
        for half in ("lower", "upper"):
            for hinge in ("left", "right"):
                for open_state in ("false", "true"):
                    part = "bottom" if half == "lower" else "top"
                    suffix = f"_{part}_{hinge}" + ("_open" if open_state == "true" else "")
                    turn = y
                    if open_state == "true":
                        turn = (y + (90 if hinge == "left" else 270)) % 360
                    model = {"model": f"{NS}:block/{name}{suffix}"}
                    if turn:
                        model["y"] = turn
                    out[f"facing={facing},half={half},hinge={hinge},open={open_state}"] = model
    return out


def trapdoor_variants(name):
    out = {}
    for facing, y in (("north", 0), ("south", 180), ("east", 90), ("west", 270)):
        for half in ("bottom", "top"):
            for open_state in ("false", "true"):
                if open_state == "true":
                    model = {"model": f"{NS}:block/{name}_open"}
                    turn = y
                    if half == "top":
                        model["x"] = 180
                        turn = (y + 180) % 360
                else:
                    model = {"model": f"{NS}:block/{name}_{half}"}
                    turn = y
                if turn:
                    model["y"] = turn
                out[f"facing={facing},half={half},open={open_state}"] = model
    return out


def button_variants(name):
    out = {}
    for face, x in (("floor", 0), ("wall", 90), ("ceiling", 180)):
        for facing, y in (("north", 0), ("south", 180), ("west", 270), ("east", 90)):
            for powered in ("false", "true"):
                suffix = "_pressed" if powered == "true" else ""
                model = {"model": f"{NS}:block/{name}{suffix}"}
                turn = y
                if face == "floor":
                    turn = y
                elif face == "ceiling":
                    model["x"] = 180
                    turn = (y + 180) % 360
                else:
                    model["x"] = 90
                    model["uvlock"] = True
                if turn:
                    model["y"] = turn
                out[f"face={face},facing={facing},powered={powered}"] = model
    return out


# ---------------------------------------------------------------------------
def loot():
    """
    A table per block that has one.

    The crops and the wild plants have none on purpose: what they give depends
    on who broke them, which a table cannot say, so their block answers
    instead and their properties declare the absence.
    """
    def drops_self(name):
        return {"type": "minecraft:block", "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]}

    for name in WOOD:
        if name == "emberwood_leaves":
            continue
        if name == "emberwood_slab":
            write(os.path.join(DATA, f"loot_table/blocks/{name}.json"), {
                "type": "minecraft:block", "pools": [{
                    "rolls": 1,
                    "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}",
                                 "functions": [{
                                     "function": "minecraft:set_count",
                                     "count": 2,
                                     "conditions": [{
                                         "condition": "minecraft:block_state_property",
                                         "block": f"{NS}:{name}",
                                         "properties": {"type": "double"}}]},
                                     {"function": "minecraft:explosion_decay"}]}]}]})
            continue
        if name == "emberwood_door":
            write(os.path.join(DATA, f"loot_table/blocks/{name}.json"), {
                "type": "minecraft:block", "pools": [{
                    "rolls": 1,
                    "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                    "conditions": [
                        {"condition": "minecraft:block_state_property",
                         "block": f"{NS}:{name}",
                         "properties": {"half": "lower"}},
                        {"condition": "minecraft:survives_explosion"}]}]})
            continue
        write(os.path.join(DATA, f"loot_table/blocks/{name}.json"), drops_self(name))

    # Leaves: a sapling sometimes, sticks sometimes, and the leaves themselves
    # with shears. Vanilla's numbers, because a player already knows them.
    write(os.path.join(DATA, "loot_table/blocks/emberwood_leaves.json"), {
        "type": "minecraft:block",
        "pools": [
            {"rolls": 1, "entries": [{
                "type": "minecraft:alternatives",
                "children": [
                    {"type": "minecraft:item", "name": f"{NS}:emberwood_leaves",
                     "conditions": [{"condition": "minecraft:any_of", "terms": [
                         {"condition": "minecraft:match_tool",
                          "predicate": {"items": "minecraft:shears"}},
                         {"condition": "minecraft:match_tool",
                          "predicate": {"predicates": {"minecraft:enchantments": [
                              {"enchantments": "minecraft:silk_touch",
                               "levels": {"min": 1}}]}}}]}]},
                    {"type": "minecraft:item", "name": f"{NS}:emberwood_sapling",
                     "conditions": [{"condition": "minecraft:survives_explosion"},
                                    {"condition": "minecraft:table_bonus",
                                     "enchantment": "minecraft:fortune",
                                     "chances": [0.05, 0.0625, 0.083333336, 0.1]}]}]}]},
            {"rolls": 1,
             "conditions": [{"condition": "minecraft:inverted", "term": {
                 "condition": "minecraft:any_of", "terms": [
                     {"condition": "minecraft:match_tool",
                      "predicate": {"items": "minecraft:shears"}},
                     {"condition": "minecraft:match_tool",
                      "predicate": {"predicates": {"minecraft:enchantments": [
                          {"enchantments": "minecraft:silk_touch",
                           "levels": {"min": 1}}]}}}]}}],
             "entries": [{"type": "minecraft:item", "name": "minecraft:stick",
                          "functions": [{"function": "minecraft:set_count",
                                         "count": {"type": "minecraft:uniform",
                                                   "min": 1, "max": 2}},
                                        {"function": "minecraft:explosion_decay"}],
                          "conditions": [{"condition": "minecraft:table_bonus",
                                          "enchantment": "minecraft:fortune",
                                          "chances": [0.02, 0.022222223, 0.025,
                                                      0.033333335, 0.1]}]}]}]})


# ---------------------------------------------------------------------------
def recipes():
    def shapeless(name, ingredients, result, count=1):
        write(os.path.join(DATA, f"recipe/{name}.json"), {
            "type": "minecraft:crafting_shapeless",
            "ingredients": ingredients,
            "result": {"id": result, "count": count}})

    def shaped(name, pattern, key, result, count=1):
        write(os.path.join(DATA, f"recipe/{name}.json"), {
            "type": "minecraft:crafting_shaped",
            "pattern": pattern, "key": key,
            "result": {"id": result, "count": count}})

    # Fibre into string. The whole reason the crops exist.
    shapeless("string_from_loomflax",
              [{"item": f"{NS}:loomflax_fibre"}] * FIBRE_PER_STRING,
              "minecraft:string")
    shapeless("string_from_downbloom",
              [{"item": f"{NS}:downbloom_tuft"}] * TUFT_PER_STRING,
              "minecraft:string")

    shaped("downbloom_bale", ["###", "###", "###"],
           {"#": {"item": f"{NS}:downbloom_tuft"}}, f"{NS}:downbloom_bale")
    shapeless("downbloom_tuft_from_bale", [{"item": f"{NS}:downbloom_bale"}],
              f"{NS}:downbloom_tuft", 9)

    shaped("emberwood_charcoal_block", ["###", "###", "###"],
           {"#": {"item": f"{NS}:emberwood_charcoal"}}, f"{NS}:emberwood_charcoal_block")
    shapeless("emberwood_charcoal_from_block",
              [{"item": f"{NS}:emberwood_charcoal_block"}],
              f"{NS}:emberwood_charcoal", 9)

    write(os.path.join(DATA, "recipe/emberwood_charcoal.json"), {
        "type": "minecraft:smelting",
        "ingredient": {"tag": f"{NS}:emberwood_logs"},
        "result": {"id": f"{NS}:emberwood_charcoal"},
        "experience": 0.15,
        "cookingtime": 200})

    shapeless("emberwood_planks", [{"tag": f"{NS}:emberwood_logs"}],
              f"{NS}:emberwood_planks", 4)
    shaped("emberwood_wood", ["##", "##"],
           {"#": {"item": f"{NS}:emberwood_log"}}, f"{NS}:emberwood_wood", 3)
    shaped("stripped_emberwood_wood", ["##", "##"],
           {"#": {"item": f"{NS}:stripped_emberwood_log"}},
           f"{NS}:stripped_emberwood_wood", 3)

    planks = {"#": {"item": f"{NS}:emberwood_planks"}}
    shaped("emberwood_slab", ["###"], planks, f"{NS}:emberwood_slab", 6)
    shaped("emberwood_stairs", ["#  ", "## ", "###"], planks,
           f"{NS}:emberwood_stairs", 4)
    shaped("emberwood_fence", ["#/#", "#/#"],
           {"#": {"item": f"{NS}:emberwood_planks"}, "/": {"item": "minecraft:stick"}},
           f"{NS}:emberwood_fence", 3)
    shaped("emberwood_fence_gate", ["/#/", "/#/"],
           {"#": {"item": f"{NS}:emberwood_planks"}, "/": {"item": "minecraft:stick"}},
           f"{NS}:emberwood_fence_gate")
    shaped("emberwood_door", ["##", "##", "##"], planks, f"{NS}:emberwood_door", 3)
    shaped("emberwood_trapdoor", ["###", "###"], planks, f"{NS}:emberwood_trapdoor", 2)
    shapeless("emberwood_button", [{"item": f"{NS}:emberwood_planks"}],
              f"{NS}:emberwood_button")
    shaped("emberwood_pressure_plate", ["##"], planks,
           f"{NS}:emberwood_pressure_plate")


# ---------------------------------------------------------------------------
def tags():
    def block_tag(path, values, namespace="minecraft"):
        write(os.path.join(RES, "data", namespace, f"tags/block/{path}.json"),
              {"replace": False, "values": values})

    def item_tag(path, values, namespace="minecraft"):
        write(os.path.join(RES, "data", namespace, f"tags/item/{path}.json"),
              {"replace": False, "values": values})

    logs = [f"{NS}:emberwood_log", f"{NS}:emberwood_wood",
            f"{NS}:stripped_emberwood_log", f"{NS}:stripped_emberwood_wood"]
    write(os.path.join(DATA, "tags/block/emberwood_logs.json"),
          {"replace": False, "values": logs})
    write(os.path.join(DATA, "tags/item/emberwood_logs.json"),
          {"replace": False, "values": logs})

    wooden = [f"{NS}:emberwood_planks", f"{NS}:emberwood_slab",
              f"{NS}:emberwood_stairs", f"{NS}:emberwood_fence",
              f"{NS}:emberwood_fence_gate", f"{NS}:emberwood_door",
              f"{NS}:emberwood_trapdoor", f"{NS}:emberwood_button",
              f"{NS}:emberwood_pressure_plate"] + logs

    block_tag("logs_that_burn", logs)
    block_tag("planks", [f"{NS}:emberwood_planks"])
    block_tag("leaves", [f"{NS}:emberwood_leaves"])
    block_tag("saplings", [f"{NS}:emberwood_sapling"])
    block_tag("wooden_slabs", [f"{NS}:emberwood_slab"])
    block_tag("wooden_stairs", [f"{NS}:emberwood_stairs"])
    block_tag("wooden_fences", [f"{NS}:emberwood_fence"])
    block_tag("fence_gates", [f"{NS}:emberwood_fence_gate"])
    block_tag("wooden_doors", [f"{NS}:emberwood_door"])
    block_tag("wooden_trapdoors", [f"{NS}:emberwood_trapdoor"])
    block_tag("wooden_buttons", [f"{NS}:emberwood_button"])
    block_tag("wooden_pressure_plates", [f"{NS}:emberwood_pressure_plate"])
    block_tag("crops", [f"{NS}:loomflax", f"{NS}:downbloom"])
    block_tag("mineable/axe", wooden)
    block_tag("mineable/hoe", [f"{NS}:emberwood_leaves", f"{NS}:downbloom_bale"])
    block_tag("mineable/pickaxe", [f"{NS}:emberwood_charcoal_block"])
    block_tag("sword_efficient", [f"{NS}:loomflax", f"{NS}:downbloom",
                                  f"{NS}:wild_loomflax", f"{NS}:wild_downbloom"])
    block_tag("flower_pots", [])

    item_tag("logs_that_burn", logs)
    item_tag("planks", [f"{NS}:emberwood_planks"])
    item_tag("leaves", [f"{NS}:emberwood_leaves"])
    item_tag("saplings", [f"{NS}:emberwood_sapling"])
    item_tag("wooden_slabs", [f"{NS}:emberwood_slab"])
    item_tag("wooden_stairs", [f"{NS}:emberwood_stairs"])
    item_tag("wooden_fences", [f"{NS}:emberwood_fence"])
    item_tag("fence_gates", [f"{NS}:emberwood_fence_gate"])
    item_tag("wooden_doors", [f"{NS}:emberwood_door"])
    item_tag("wooden_trapdoors", [f"{NS}:emberwood_trapdoor"])
    item_tag("wooden_buttons", [f"{NS}:emberwood_button"])
    item_tag("wooden_pressure_plates", [f"{NS}:emberwood_pressure_plate"])

    # The common vocabulary, so other mods' machines find these.
    write(os.path.join(RES, "data/c/tags/item/seeds.json"),
          {"replace": False, "values": [f"{NS}:loomflax_seeds", f"{NS}:downbloom_seeds"]})
    write(os.path.join(RES, "data/c/tags/item/crops.json"),
          {"replace": False, "values": [f"{NS}:loomflax_fibre", f"{NS}:downbloom_tuft"]})
    write(os.path.join(RES, "data/c/tags/block/villager_farmlands.json"),
          {"replace": False, "values": []})


# ---------------------------------------------------------------------------
def worldgen():
    write(os.path.join(DATA, "worldgen/configured_feature/emberwood.json"), {
        "type": "minecraft:tree",
        "config": {
            "decorators": [],
            "dirt_provider": {"type": "minecraft:simple_state_provider",
                              "state": {"Name": "minecraft:netherrack"}},
            "foliage_placer": {"type": "minecraft:blob_foliage_placer",
                               "radius": 2, "offset": 0, "height": 3},
            "foliage_provider": {"type": "minecraft:simple_state_provider",
                                 "state": {"Name": f"{NS}:emberwood_leaves",
                                           "Properties": {"distance": "7",
                                                          "persistent": "false",
                                                          "waterlogged": "false"}}},
            "force_dirt": False,
            "ignore_vines": True,
            "minimum_size": {"type": "minecraft:two_layers_feature_size",
                             "limit": 1, "lower_size": 0, "upper_size": 1},
            "trunk_placer": {"type": "minecraft:straight_trunk_placer",
                             "base_height": 5, "height_rand_a": 2, "height_rand_b": 2},
            "trunk_provider": {"type": "minecraft:simple_state_provider",
                               "state": {"Name": f"{NS}:emberwood_log",
                                         "Properties": {"axis": "y"}}}}})

    write(os.path.join(DATA, "worldgen/placed_feature/emberwood.json"), {
        "feature": f"{NS}:emberwood",
        "placement": [
            {"type": "minecraft:count", "count": 1},
            {"type": "minecraft:rarity_filter", "chance": 6},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range",
             "height": {"type": "minecraft:uniform",
                        "min_inclusive": {"absolute": 32},
                        "max_inclusive": {"absolute": 100}}},
            {"type": "minecraft:environment_scan",
             "direction_of_search": "down", "max_steps": 12,
             "target_condition": {"type": "minecraft:solid"},
             "allowed_search_condition": {"type": "minecraft:matching_blocks",
                                          "blocks": "minecraft:air"}},
            {"type": "minecraft:block_predicate_filter",
             "predicate": {"type": "minecraft:matching_blocks",
                           "blocks": "minecraft:air"}},
            {"type": "minecraft:biome"}]})

    for name, gives, per, biomes in CROPS:
        write(os.path.join(DATA, f"worldgen/configured_feature/wild_{name}.json"), {
            "type": "minecraft:random_patch",
            "config": {
                "tries": WILD_PATCH,
                "xz_spread": 7,
                "y_spread": 3,
                "feature": {"feature": {
                    "type": "minecraft:simple_block",
                    "config": {"to_place": {
                        "type": "minecraft:simple_state_provider",
                        "state": {"Name": f"{NS}:wild_{name}"}}}},
                    "placement": [{"type": "minecraft:block_predicate_filter",
                                   "predicate": {"type": "minecraft:matching_blocks",
                                                 "blocks": "minecraft:air"}}]}}})
        write(os.path.join(DATA, f"worldgen/placed_feature/wild_{name}.json"), {
            "feature": f"{NS}:wild_{name}",
            "placement": [
                {"type": "minecraft:rarity_filter", "chance": 12},
                {"type": "minecraft:count", "count": WILD_TRIES},
                {"type": "minecraft:in_square"},
                {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
                {"type": "minecraft:biome"}]})

    write(os.path.join(DATA, "neoforge/biome_modifier/wild_crops.json"), {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": [f"{NS}:wild_loomflax", f"{NS}:wild_downbloom"],
        "step": "vegetal_decoration"})

    write(os.path.join(DATA, "neoforge/biome_modifier/emberwood.json"), {
        "type": "neoforge:add_features",
        "biomes": ["#minecraft:is_nether",
                   "elysiumspace:cinderveil_desert",
                   "elysiumspace:cinderveil_infernal_barrens"],
        "features": [f"{NS}:emberwood"],
        "step": "vegetal_decoration"})


# ---------------------------------------------------------------------------
def language():
    lang = {}
    for name, gives, per, biomes in CROPS:
        lang[f"block.{NS}.{name}"] = title(name)
        lang[f"block.{NS}.wild_{name}"] = "Wild " + title(name)
        lang[f"item.{NS}.{name}_seeds"] = title(name) + " Seeds"
        lang[f"item.{NS}.{gives}"] = title(gives)
    for name in WOOD:
        lang[f"block.{NS}.{name}"] = title(name)
    lang[f"item.{NS}.emberwood_charcoal"] = "Emberwood Charcoal"
    lang[f"item.{NS}.emberwood_door"] = "Emberwood Door"
    lang[f"itemGroup.{NS}.flora"] = "Elysium Flora"
    write(os.path.join(ASSETS, "lang/en_us.json"), lang)
    return len(lang)


def main():
    clean()
    crops()
    wood()
    for item in ("emberwood_charcoal",):
        simple_item(item)
    loot()
    recipes()
    tags()
    worldgen()
    keys = language()
    print(f"crops             : {len(CROPS)} with wild forms")
    print(f"wood set          : {len(WOOD)} block(s)")
    print(f"language keys     : {keys}")


if __name__ == "__main__":
    main()
