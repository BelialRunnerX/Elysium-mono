#!/usr/bin/env python3
"""
Everything the waygate ships that is a file rather than a decision.

Two blocks, their models, their textures, their drops, their recipes and their
names. The textures are built rather than drawn, the same way the ore module's
are: a vanilla block is the surface and the ring's own colour is laid over it,
so a gate reads as cut stone with something alive in it rather than as a flat
recolour.
"""
import json
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumspace"
RES = os.path.join(MOD, "src/main/resources")
ASSETS = os.path.join(RES, "assets", NS)
DATA = os.path.join(RES, "data", NS)
BASE = os.path.join(HERE, "base")


def write(path, body):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if isinstance(body, (dict, list)):
        body = json.dumps(body, indent=2) + "\n"
    open(path, "w", encoding="utf-8", newline="\n").write(body)


def luminance(pixel):
    return (pixel[0] * 299 + pixel[1] * 587 + pixel[2] * 114) / 255000.0


def veined(source, colour, share, glow):
    """
    A stone with something running through it.

    The darkest pixels of the source become the vein — obsidian's darkest
    pixels are already its cracks, so the result follows the stone's own
    structure rather than being painted on top of it. Everything else is left
    alone, which is what keeps it looking like rock rather than like a recolour.

    <p>`share` is how much of the texture becomes vein, not an absolute
    brightness: obsidian is dark all over, so a fixed threshold either catches
    the whole block or none of it. Taking a percentile of the source's own
    luminance means the same number works on any stone.
    """
    out = source.copy()
    pixels = out.load()
    levels = sorted(luminance(pixels[x, y])
                    for y in range(out.height) for x in range(out.width)
                    if pixels[x, y][3] > 0)
    if not levels:
        return out
    threshold = levels[max(0, min(len(levels) - 1,
                                  int(len(levels) * share)))]
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            level = luminance((r, g, b))
            if level > threshold:
                continue
            # Darker source pixel, brighter vein: the deepest cracks glow most.
            strength = glow * (1.0 - level / max(1e-6, threshold))
            pixels[x, y] = (
                min(255, int(r + (colour[0] - r) * strength)),
                min(255, int(g + (colour[1] - g) * strength)),
                min(255, int(b + (colour[2] - b) * strength)),
                a,
            )
    return out


#: The frame's veins: a cold blue-green, the colour Elysium uses for anything
#: that is working rather than merely built.
FRAME_VEIN = (0x4C, 0xD8, 0xC0)

#: The anchor's: the same hue, brighter, because it is the part that is lit.
ANCHOR_VEIN = (0x9C, 0xF4, 0xE8)


def textures():
    source = Image.open(os.path.join(BASE, "obsidian.png")).convert("RGBA")
    made = 0
    for name, colour, threshold, glow in (
            # A quarter of the frame is vein; nearly half the anchor, because
            # the anchor is the part that is lit and should read as such from
            # across a room.
            ("waygate_frame", FRAME_VEIN, 0.25, 0.85),
            ("waygate_anchor", ANCHOR_VEIN, 0.45, 1.0)):
        out = os.path.join(ASSETS, "textures/block", name + ".png")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        veined(source, colour, threshold, glow).save(out)
        made += 1
    return made


def blocks():
    for name in ("waygate_frame", "waygate_anchor"):
        write(os.path.join(ASSETS, f"blockstates/{name}.json"),
              {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write(os.path.join(ASSETS, f"models/block/{name}.json"),
              {"parent": "minecraft:block/cube_all",
               "textures": {"all": f"{NS}:block/{name}"}})
        write(os.path.join(ASSETS, f"models/item/{name}.json"),
              {"parent": f"{NS}:block/{name}"})
        write(os.path.join(DATA, f"loot_table/blocks/{name}.json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })
    return 2


def recipes():
    # The frame is the whole cost of a gate, so it is priced like one: obsidian
    # for the body, steel for the structure, and cryonite — Frostfall's metal —
    # for the part that has to hold still while something goes through it.
    # Four at a time, because fifteen are needed and a recipe that took four
    # trips to the bench would be a recipe nobody uses twice.
    #
    # Everything named here is this module's own or vanilla's. The space module
    # is a world, not a dependant: a recipe reaching into elysium-core would
    # make the gates unbuildable in a pack that took the content module out.
    write(os.path.join(DATA, "recipe/waygate_frame.json"), {
        "type": "minecraft:crafting_shaped",
        "pattern": ["OCO", "CSC", "OCO"],
        "key": {
            "O": {"item": "minecraft:obsidian"},
            "C": {"item": f"{NS}:cryonite_ingot"},
            "S": {"item": f"{NS}:steel_ingot"},
        },
        "result": {"id": f"{NS}:waygate_frame", "count": 4},
    })
    # The anchor is one per gate and is the part that knows where every other
    # gate is, so it costs the moon's own metal and an eye that has already
    # been somewhere.
    write(os.path.join(DATA, "recipe/waygate_anchor.json"), {
        "type": "minecraft:crafting_shaped",
        "pattern": ["FLF", "LEL", "FLF"],
        "key": {
            "F": {"item": f"{NS}:waygate_frame"},
            "L": {"item": f"{NS}:lunite_ingot"},
            "E": {"item": "minecraft:ender_eye"},
        },
        "result": {"id": f"{NS}:waygate_anchor"},
    })
    return 2


def tags():
    for path, values in (
            ("minecraft/tags/block/mineable/pickaxe.json",
             [f"{NS}:waygate_frame", f"{NS}:waygate_anchor"]),
            ("minecraft/tags/block/needs_diamond_tool.json",
             [f"{NS}:waygate_frame", f"{NS}:waygate_anchor"])):
        full = os.path.join(RES, "data", path)
        body = {"replace": False, "values": []}
        if os.path.isfile(full):
            body = json.load(open(full, encoding="utf-8"))
        for value in values:
            if value not in body.get("values", []):
                body.setdefault("values", []).append(value)
        write(full, body)
    return 2


def language():
    path = os.path.join(ASSETS, "lang/en_us.json")
    lang = json.load(open(path, encoding="utf-8")) if os.path.isfile(path) else {}
    lang.update({
        f"block.{NS}.waygate_frame": "Waygate Frame",
        f"block.{NS}.waygate_anchor": "Waygate Anchor",
        "elysiumspace.waygate.title": "Waygate",
        "elysiumspace.waygate.count": "%s gates stand open",
        "elysiumspace.waygate.alone": "This is the only waygate. Build another one somewhere worth returning to.",
        "elysiumspace.waygate.unbuilt": "The ring is not finished.",
        "elysiumspace.waygate.missing": "The ring is %s blocks short of %s. The first gap is at %s, %s, %s.",
        "elysiumspace.waygate.blocked": "Something stands in the gate: %s blocks, the first at %s, %s, %s.",
        "elysiumspace.waygate.not_here": "You are no longer standing at a waygate.",
        "elysiumspace.waygate.gone": "That gate is no longer there.",
        "elysiumspace.waygate.no_world": "%s is not a world this server is running.",
        "elysiumspace.waygate.broken_there": "The ring at %s has been broken. It has been taken off the network.",
        "elysiumspace.waygate.arrived": "%s",
        "elysiumspace.waygate.named": "This gate is now %s.",
    })
    write(path, lang)
    return len(lang)


def main():
    print(f"waygate textures  : {textures()}")
    print(f"waygate blocks    : {blocks()} modelled and dropping")
    print(f"waygate recipes   : {recipes()}")
    print(f"waygate tags      : {tags()}")
    print(f"language keys     : {language()} in the space module")


if __name__ == "__main__":
    main()
