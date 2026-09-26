#!/usr/bin/env python3
"""Generate elysium-space's assets and data.

Inputs: the Stellaris jar unpacked at STELLARIS (block art for the ported
blocks) and the Minecraft jar at MC_JAR (vanilla mangrove art, models and
worldgen, which Emgrove and the Emerald Bastion are built from). The worldgen
for the four Stellaris-based worlds is port_worldgen.py; this script does
everything else. Re-runnable; the output is a pure function of the inputs.
"""
import colorsys
import io
import json
import os
import shutil
import sys
import zipfile

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from reskin import reskin_file  # noqa: E402

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# ELYSIUM_SPACE_OUT points what the generator writes at another tree, so the
# validator can rebuild everything and compare; what it reads stays in the module.
RES = os.path.join(os.environ.get("ELYSIUM_SPACE_OUT", HERE), "src/main/resources")
ASSETS = os.path.join(RES, "assets/elysiumspace")
DATA = os.path.join(RES, "data/elysiumspace")
STELLARIS = os.environ.get("STELLARIS", "/tmp/ref/stellaris")
MC_JAR = os.environ.get("MC_JAR", "/tmp/mc/mapped-at.jar")
NS = "elysiumspace"

mc = zipfile.ZipFile(MC_JAR)


def vanilla(path):
    return mc.read(path)


def vjson(path):
    return json.loads(vanilla(path))


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def sub_all(obj, pairs):
    """Replace substrings in every string of a JSON value."""
    if isinstance(obj, dict):
        return {k: sub_all(v, pairs) for k, v in obj.items()}
    if isinstance(obj, list):
        return [sub_all(v, pairs) for v in obj]
    if isinstance(obj, str):
        for a, b in pairs:
            obj = obj.replace(a, b)
        return obj
    return obj


# ---------------------------------------------------------------------------
# Textures
# ---------------------------------------------------------------------------
TEX = os.path.join(ASSETS, "textures/block")
ITEM_TEX = os.path.join(ASSETS, "textures/item")

# Ported block art, kept as Stellaris drew it until the reskin (S8).
PORTED_ART = {
    "pale_regolith": "moon_sand", "pale_stone": "moon_stone",
    "frost_regolith": "mars_sand", "frost_stone": "mars_stone",
    "cinder_sand": "venus_sand", "cinder_stone": "venus_stone",
    "cinder_sandstone": "venus_sandstone",
    "pale_vines": "moon_vines", "pale_vines_lit": "moon_vines_lit",
    "pale_vines_plant": "moon_vines_plant", "pale_vines_plant_lit": "moon_vines_plant_lit",
}

# Emgrove: vanilla mangrove turned from red to green. The hue moves to the
# Emerald Bastion's green; saturation is eased so the planks read as wood and
# not as slime.
EMGROVE_HUE = 0.36          # 130 degrees, emerald green
MANGROVE_ART = {
    "emgrove_log": "mangrove_log", "emgrove_log_top": "mangrove_log_top",
    "stripped_emgrove_log": "stripped_mangrove_log",
    "stripped_emgrove_log_top": "stripped_mangrove_log_top",
    "emgrove_planks": "mangrove_planks",
    "emgrove_roots_side": "mangrove_roots_side", "emgrove_roots_top": "mangrove_roots_top",
    "emgrove_propagule": "mangrove_propagule",
    "emgrove_propagule_hanging": "mangrove_propagule_hanging",
}
LEAF_GREEN = (0x2E, 0x8B, 0x4A)   # baked in: the leaves have no biome tint here


def greened(img):
    img = img.convert("RGBA")
    out = Image.new("RGBA", img.size)
    px = img.load()
    po = out.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b, a = px[x, y]
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s > 0.12:                         # leave greys (bark cracks, ash) alone
                h, s = EMGROVE_HUE, s * 0.8
            nr, ng, nb = colorsys.hsv_to_rgb(h, s, v)
            po[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return out


def tinted(img, rgb):
    img = img.convert("RGBA")
    out = Image.new("RGBA", img.size)
    px, po = img.load(), out.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b, a = px[x, y]
            k = (r + g + b) / (3 * 255)
            po[x, y] = (round(rgb[0] * k), round(rgb[1] * k), round(rgb[2] * k), a)
    return out


def textures():
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(ITEM_TEX, exist_ok=True)
    # Stellaris's world blocks, reskinned into their world's Voidforged ramp (S8, tools/reskin.py);
    # the stone variants, building blocks and ores are all drawn from these afterwards.
    for ours, theirs in PORTED_ART.items():
        world = {"pale": "pale_moon", "frost": "frostfall", "cinder": "cinderveil"}[ours.split("_")[0]]
        reskin_file(os.path.join(STELLARIS, f"assets/stellaris/textures/block/{theirs}.png"),
                    os.path.join(TEX, ours + ".png"), world)
    for ours, theirs in MANGROVE_ART.items():
        img = Image.open(io.BytesIO(vanilla(f"assets/minecraft/textures/block/{theirs}.png")))
        greened(img).save(os.path.join(TEX, ours + ".png"))
    leaves = Image.open(io.BytesIO(vanilla("assets/minecraft/textures/block/mangrove_leaves.png")))
    tinted(leaves, LEAF_GREEN).save(os.path.join(TEX, "emgrove_leaves.png"))
    item = Image.open(io.BytesIO(vanilla("assets/minecraft/textures/item/mangrove_propagule.png")))
    greened(item).save(os.path.join(ITEM_TEX, "emgrove_propagule.png"))
    mud = Image.open(io.BytesIO(vanilla("assets/minecraft/textures/block/muddy_mangrove_roots_side.png")))
    greened(mud).save(os.path.join(TEX, "muddy_emgrove_roots_side.png"))
    mud_top = Image.open(io.BytesIO(vanilla("assets/minecraft/textures/block/muddy_mangrove_roots_top.png")))
    greened(mud_top).save(os.path.join(TEX, "muddy_emgrove_roots_top.png"))
    distributor()


HULL, HULL_DARK, TRIM, GLOW, STEEL = (0x1B, 0x22, 0x2B, 255), (0x12, 0x17, 0x1D, 255), \
    (0x2E, 0x8B, 0x4A, 255), (0x3F, 0xC8, 0x7A, 255), (0x5A, 0x66, 0x72, 255)


def rocket_art():
    """Placeholder rocket art in the Elysium palette (dark hull, emerald trim).
    The texture's regions follow RocketModel's texOffs: hull at (0,0) 48x48,
    nose (48,0) 32x18, tip (80,0) 16x8, engine (48,20) 40x14, fin (88,20) 14x20."""
    img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    px = img.load()

    def fill(x0, y0, w, h, colour):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                px[x, y] = colour
    fill(0, 0, 48, 48, HULL)
    for band in (14, 15, 40, 41):                      # emerald bands around the hull
        fill(0, band, 48, 1, TRIM)
    for x in range(12, 48, 12):                         # the four window ports
        fill(x - 9, 20, 3, 3, GLOW)
    fill(48, 0, 32, 18, TRIM)
    fill(80, 0, 16, 8, GLOW)
    fill(48, 20, 40, 14, HULL_DARK)
    fill(88, 20, 14, 20, TRIM)
    os.makedirs(os.path.join(ASSETS, "textures/entity"), exist_ok=True)
    img.save(os.path.join(ASSETS, "textures/entity/rocket.png"))

    icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    ip = icon.load()
    for y in range(2, 13):
        for x in range(6, 10):
            ip[x, y] = HULL
    for x in (7, 8):
        ip[x, 1] = TRIM
        ip[x, 6] = GLOW
    for y in (10, 11, 12, 13):
        ip[5, y] = TRIM
        ip[10, y] = TRIM
    for x in range(6, 10):
        ip[x, 13] = HULL_DARK
    ip[7, 14] = (0xFF, 0x9A, 0x3C, 255)
    ip[8, 14] = (0xFF, 0x9A, 0x3C, 255)
    icon.save(os.path.join(ITEM_TEX, "rocket.png"))

    can = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cp = can.load()
    for y in range(3, 15):
        for x in range(4, 12):
            cp[x, y] = STEEL if x in (4, 11) or y in (3, 14) else HULL
    for y in range(6, 12):
        for x in range(6, 10):
            cp[x, y] = GLOW
    for x in range(6, 10):
        cp[x, 2] = STEEL
    can.save(os.path.join(ITEM_TEX, "fuel_canister.png"))

    for name, has_mark in (("launch_pad_top", True), ("launch_pad_side", False)):
        pad = Image.new("RGBA", (16, 16), STEEL)
        pp = pad.load()
        for i in range(16):
            for e in (0, 15):
                pp[i, e] = HULL
                pp[e, i] = HULL
        if has_mark:
            for i in range(2, 14):
                pp[i, i] = TRIM
                pp[15 - i, i] = TRIM
        else:
            for x in range(1, 15):
                pp[x, 5] = TRIM
        pad.save(os.path.join(TEX, name + ".png"))


def suit_art():
    """The space suit in the house style: aetherium plating, emerald (neural)
    lights, from the Elysium art library so it matches the rest of the mod."""
    sys.path.insert(0, os.path.join(os.path.dirname(HERE), "art"))
    import layers as art_layers
    import sprites as art_sprites
    armor = os.path.join(ASSETS, "textures/models/armor")
    os.makedirs(armor, exist_ok=True)
    art_layers.layer_one("aetherium", "neural").save(os.path.join(armor, "space_suit_layer_1.png"))
    art_layers.layer_two("aetherium", "neural").save(os.path.join(armor, "space_suit_layer_2.png"))
    for piece in ("helmet", "chestplate", "leggings", "boots"):
        getattr(art_sprites, piece)("aetherium", "neural").save(
            os.path.join(ITEM_TEX, f"space_suit_{piece}.png"))
    art_sprites.chestplate("aetherium", "plasma").save(os.path.join(ITEM_TEX, "jet_suit.png"))
    can = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cp = can.load()
    for y in range(3, 15):
        for x in range(4, 12):
            cp[x, y] = STEEL if x in (4, 11) or y in (3, 14) else (0xE8, 0xF4, 0xFF, 255)
    for y in range(7, 11):
        for x in range(6, 10):
            cp[x, y] = (0x4F, 0xA8, 0xE0, 255)
    for x in range(6, 10):
        cp[x, 2] = STEEL
    can.save(os.path.join(ITEM_TEX, "oxygen_canister.png"))


MACHINE_ART = {
    # name: (metal, glow, inlay points, core points)
    "oxygen_distributor": ("aetherium", "neural",
        [(3, 3), (4, 3), (11, 3), (12, 3), (3, 12), (4, 12), (11, 12), (12, 12),
         (3, 4), (12, 4), (3, 11), (12, 11), (5, 7), (10, 7), (5, 8), (10, 8)],
        [(7, 7), (8, 7), (7, 8), (8, 8)]),
    "coal_generator": ("voidsteel", "plasma",
        [(4, 4), (5, 4), (6, 4), (9, 4), (10, 4), (11, 4), (4, 11), (5, 11), (6, 11),
         (9, 11), (10, 11), (11, 11), (4, 5), (11, 5), (4, 10), (11, 10)],
        [(7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8), (7, 9), (8, 9)]),
    "fuel_refinery": ("voidsteel", "kinetic",
        [(5, 2), (10, 2), (5, 3), (10, 3), (5, 4), (10, 4), (4, 5), (11, 5), (4, 10), (11, 10),
         (5, 11), (10, 11), (5, 12), (10, 12), (5, 13), (10, 13)],
        [(7, 7), (8, 7), (7, 8), (8, 8)]),
    "water_separator": ("aetherium", "dimensional",
        [(7, 2), (8, 2), (7, 3), (8, 3), (3, 7), (3, 8), (12, 7), (12, 8), (7, 12), (8, 12),
         (7, 13), (8, 13), (5, 5), (10, 5), (5, 10), (10, 10)],
        [(7, 7), (8, 7), (7, 8), (8, 8)]),
    "pumpjack": ("voidsteel", "kinetic",
        [(7, 1), (8, 1), (7, 2), (8, 2), (7, 3), (8, 3), (6, 4), (9, 4), (5, 5), (10, 5),
         (4, 12), (5, 12), (6, 12), (9, 12), (10, 12), (11, 12)],
        [(7, 8), (8, 8), (7, 9), (8, 9)]),
    "diesel_generator": ("voidsteel", "kinetic",
        [(4, 4), (5, 4), (6, 4), (9, 4), (10, 4), (11, 4), (4, 11), (5, 11), (6, 11),
         (9, 11), (10, 11), (11, 11), (4, 5), (11, 5), (4, 10), (11, 10)],
        [(7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8), (7, 9), (8, 9)]),
    "fluid_tank": ("aetherium", "aetherium",
        [(x, 2) for x in range(2, 14)] + [(x, 13) for x in range(2, 14)]
        + [(2, y) for y in range(3, 13)] + [(13, y) for y in range(3, 13)],
        [(7, 7), (8, 7), (7, 8), (8, 8)]),
    "solar_panel_side": ("aetherium", "aetherium",
        [(2, 12), (3, 12), (4, 12), (5, 12), (6, 12), (7, 12), (8, 12), (9, 12), (10, 12),
         (11, 12), (12, 12), (13, 12)],
        [(7, 13), (8, 13)]),
}


def machine_art():
    """Machine faces from the Elysium art library's workstation drawer, with
    inlays of our own, so the machines read as part of the same set."""
    sys.path.insert(0, os.path.join(os.path.dirname(HERE), "art"))
    import sprites as art_sprites
    for name, (metal, glow, inlay, core) in MACHINE_ART.items():
        art_sprites.WORKSTATION_INLAY[name] = (inlay, core)
        art_sprites.workstation(name, metal, glow).save(os.path.join(TEX, name + ".png"))
    # Solar panel top: dark cells in an emerald grid.
    top = Image.new("RGBA", (16, 16), (0x0B, 0x16, 0x24, 255))
    tp = top.load()
    for i in range(16):
        for j in (0, 5, 10, 15):
            tp[i, j] = TRIM
            tp[j, i] = TRIM
    for y in range(16):
        for x in range(16):
            if tp[x, y] != TRIM and (x + y) % 5 == 0:
                tp[x, y] = (0x1B, 0x3A, 0x5C, 255)
    top.save(os.path.join(TEX, "solar_panel_top.png"))


def bucket_art(name, fluid_rgb):
    """Our own bucket: steel pail, fluid showing at the rim."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    rows = [(5, 4, 11), (6, 4, 11), (7, 4, 11), (8, 4, 11), (9, 5, 10), (10, 5, 10), (11, 5, 10),
            (12, 5, 10), (13, 6, 9)]
    for y, x0, x1 in rows:
        for x in range(x0, x1 + 1):
            px[x, y] = STEEL if x in (x0, x1) or y == 13 else (0x3A, 0x44, 0x50, 255)
    for x in range(5, 11):
        px[x, 5] = fluid_rgb + (255,)
    for x in range(4, 12):
        px[x, 4] = STEEL
    for x in range(5, 11):
        px[x, 2] = (0x5A, 0x66, 0x72, 255)
    px[4, 3] = px[11, 3] = (0x5A, 0x66, 0x72, 255)
    img.save(os.path.join(ITEM_TEX, name + ".png"))


CONDUIT_ARMS = {
    "north": ([5, 5, 0], [11, 11, 5]), "south": ([5, 5, 11], [11, 11, 16]),
    "west": ([0, 5, 5], [5, 11, 11]), "east": ([11, 5, 5], [16, 11, 11]),
    "down": ([5, 0, 5], [11, 5, 11]), "up": ([5, 11, 5], [11, 16, 11]),
}


def conduit_models():
    """Cables and pipes: a 6-pixel core and an arm to each connected side."""
    faces = lambda tex: {f: {"texture": "#t", "uv": [5, 5, 11, 11]}
                         for f in ("north", "south", "east", "west", "up", "down")}
    for name in ("cable", "pipe"):
        tex = f"{NS}:block/{name}"
        write(os.path.join(BM, f"{name}_core.json"), {"textures": {"t": tex, "particle": tex},
              "elements": [{"from": [5, 5, 5], "to": [11, 11, 11], "faces": faces(tex)}]})
        parts = [{"apply": {"model": f"{NS}:block/{name}_core"}}]
        for side, (a, b) in CONDUIT_ARMS.items():
            write(os.path.join(BM, f"{name}_{side}.json"), {"textures": {"t": tex, "particle": tex},
                  "elements": [{"from": a, "to": b, "faces": faces(tex)}]})
            parts.append({"when": {side: "true"}, "apply": {"model": f"{NS}:block/{name}_{side}"}})
        write(os.path.join(BS, name + ".json"), {"multipart": parts})
        write(os.path.join(IM, name + ".json"), {"parent": f"{NS}:block/{name}_core"})
    sys.path.insert(0, os.path.join(os.path.dirname(HERE), "art"))
    import sprites as art_sprites
    art_sprites.WORKSTATION_INLAY["cable"] = ([(x, 7) for x in range(16)] + [(x, 8) for x in range(16)], [])
    art_sprites.WORKSTATION_INLAY["pipe"] = ([(7, y) for y in range(16)] + [(8, y) for y in range(16)], [])
    art_sprites.workstation("cable", "voidsteel", "plasma").save(os.path.join(TEX, "cable.png"))
    art_sprites.workstation("pipe", "aetherium", "dimensional").save(os.path.join(TEX, "pipe.png"))


# ---------------------------------------------------------------------------
# Space ores and metals (Stellaris's, renamed: desh->lunite, ostrum->frostite,
# tharsite->cryonite, corronium->cindrium, ice shard->rime shard; steel stays)
# ---------------------------------------------------------------------------
METALS = {  # name: (art metal, art glow, needs tool tier)
    "lunite": ("aetherium", "aetherium", "iron"),
    "frostite": ("aetherium", "dimensional", "diamond"),
    "cryonite": ("voidsteel", "voidglass", "diamond"),
    "cindrium": ("voidsteel", "plasma", "diamond"),
    "steel": ("stone", "inert", "stone"),
}
# ore: (host texture, drop item, count range, vanilla?, glow, Stellaris vein size, count, min y, max y, world)
ORES = {
    "lunite_ore": ("pale_stone", f"{NS}:raw_lunite", (1, 1), "aetherium", 9, 7, -64, 80, "pale_moon"),
    "pale_steel_ore": ("pale_stone", f"{NS}:raw_steel", (1, 1), "inert", 11, 10, -24, 56, "pale_moon"),
    "pale_iron_ore": ("pale_stone", "minecraft:raw_iron", (1, 1), "kinetic", 11, 10, -24, 56, "pale_moon"),
    "pale_rime_shard_ore": ("pale_stone", f"{NS}:rime_shard", (1, 3), "dimensional", 10, 8, -32, 32, "pale_moon"),
    "frostite_ore": ("frost_stone", f"{NS}:raw_frostite", (1, 1), "dimensional", 8, 6, -64, 80, "frostfall"),
    "cryonite_ore": ("frost_stone", f"{NS}:raw_cryonite", (1, 1), "voidglass", 6, 8, -32, 32, "frostfall"),
    "frost_iron_ore": ("frost_stone", "minecraft:raw_iron", (1, 1), "kinetic", 11, 8, -24, 56, "frostfall"),
    "frost_diamond_ore": ("frost_stone", "minecraft:diamond", (1, 1), "aetherium", 7, 7, -64, 80, "frostfall"),
    "frost_rime_shard_ore": ("frost_stone", f"{NS}:rime_shard", (1, 3), "dimensional", 10, 8, -32, 32, "frostfall"),
    "cindrium_ore": ("cinder_stone", f"{NS}:raw_cindrium", (1, 1), "plasma", 6, 1, -64, -10, "cinderveil"),
    "cinder_coal_ore": ("cinder_stone", "minecraft:coal", (1, 1), "inert", 17, 20, -64, 192, "cinderveil"),
    "cinder_gold_ore": ("cinder_stone", "minecraft:raw_gold", (1, 1), "kinetic", 10, 4, -64, 32, "cinderveil"),
    "cinder_diamond_ore": ("cinder_stone", "minecraft:diamond", (1, 1), "aetherium", 9, 7, -64, 80, "cinderveil"),
    "steel_ore": (None, f"{NS}:raw_steel", (1, 1), "inert", 9, 6, -24, 56, "overworld"),
    "deepslate_steel_ore": (None, f"{NS}:raw_steel", (1, 1), "inert", 7, 7, -64, 25, "overworld"),
}
ORE_TIER = {"lunite_ore": "iron", "pale_steel_ore": "stone", "pale_iron_ore": "stone", "pale_rime_shard_ore": "iron",
            "frostite_ore": "diamond", "cryonite_ore": "diamond", "frost_iron_ore": "stone",
            "frost_diamond_ore": "iron", "frost_rime_shard_ore": "iron", "cindrium_ore": "diamond",
            "cinder_coal_ore": "stone", "cinder_gold_ore": "iron", "cinder_diamond_ore": "iron",
            "steel_ore": "stone", "deepslate_steel_ore": "stone"}
WORLD_BIOMES = {
    "pale_moon": [f"{NS}:pale_moon_desert", f"{NS}:pale_moon_caves"],
    "frostfall": [f"{NS}:frostfall_desert", f"{NS}:frostfall_ice_spikes", f"{NS}:frostfall_rocky_plains"],
    "cinderveil": [f"{NS}:cinderveil_desert", f"{NS}:cinderveil_infernal_barrens"],
}
HOST_BLOCK = {"pale_stone": f"{NS}:pale_stone", "frost_stone": f"{NS}:frost_stone", "cinder_stone": f"{NS}:cinder_stone"}


def metal_art():
    sys.path.insert(0, os.path.join(os.path.dirname(HERE), "art"))
    import sprites as art_sprites
    import style as art_style
    for m, (metal, glow, _) in METALS.items():
        art_sprites.ingot(metal, glow).save(os.path.join(ITEM_TEX, f"{m}_ingot.png"))
        art_sprites.storage_block(metal, glow).save(os.path.join(TEX, f"{m}_block.png"))
        # Raw: the storage block's plates broken up; nugget: a small ingot corner.
        raw = Image.open(os.path.join(ITEM_TEX, f"{m}_ingot.png")).convert("RGBA")
        raw.rotate(45, resample=Image.NEAREST).resize((16, 16), Image.NEAREST).save(
            os.path.join(ITEM_TEX, f"raw_{m}.png"))
        blk = Image.open(os.path.join(TEX, f"{m}_block.png")).convert("RGBA")
        blk.transpose(Image.ROTATE_90).save(os.path.join(TEX, f"raw_{m}_block.png"))
        nug = raw.resize((8, 8), Image.NEAREST)
        canvas = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        canvas.paste(nug, (4, 4), nug)
        canvas.save(os.path.join(ITEM_TEX, f"{m}_nugget.png"))
    art_sprites.shard("aetherium", "dimensional").save(os.path.join(ITEM_TEX, "rime_shard.png"))
    # Ores: flecks of the ore's glow, bedded into the host world's stone.
    import random as _r
    for name, (host, _drop, _c, glow, *_rest) in ORES.items():
        if host is None:        # an Overworld ore: the same flecks, in vanilla's stone or deepslate
            stone = "deepslate" if name.startswith("deepslate") else "stone"
            img = Image.open(io.BytesIO(vanilla(f"assets/minecraft/textures/block/{stone}.png"))).convert("RGBA")
        else:
            img = Image.open(os.path.join(TEX, host + ".png")).convert("RGBA")
        px = img.load()
        ramp = [tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,) for h in art_style.GLOW[glow]]
        rng = _r.Random(name)
        for _ in range(5):
            cx, cy = rng.randint(2, 13), rng.randint(2, 13)
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1)):
                x, y = cx + dx, cy + dy
                if 0 <= x < 16 and 0 <= y < 16:
                    px[x, y] = ramp[-1] if (dx, dy) == (0, 0) else ramp[len(ramp) // 2]
        img.save(os.path.join(TEX, name + ".png"))


def ore_loot(name, drop, counts):
    fortune = {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
               "formula": "minecraft:ore_drops"}
    entry_drop = {"type": "minecraft:item", "name": drop, "functions": ([
        {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": counts[0], "max": counts[1]}}]
        if counts != (1, 1) else []) + [fortune, {"function": "minecraft:explosion_decay"}]}
    silk = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
    return {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [
        {"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"{NS}:{name}", "conditions": [silk]}, entry_drop]}]}]}


def materials():
    metal_art()
    lt = os.path.join(DATA, "loot_table/blocks")
    rd = os.path.join(DATA, "recipe")
    for name, (host, drop, counts, *_r) in ORES.items():
        cube(name)
        write(os.path.join(lt, name + ".json"), ore_loot(name, drop, counts))
    for m in METALS:
        for b in (f"{m}_block", f"raw_{m}_block"):
            cube(b)
            write(os.path.join(lt, b + ".json"), self_drop(b))
        for it in (f"raw_{m}", f"{m}_ingot", f"{m}_nugget"):
            write(os.path.join(IM, it + ".json"), {"parent": "minecraft:item/generated",
                  "textures": {"layer0": f"{NS}:item/{it}"}})
        # raw -> ingot by furnace and blast furnace; compaction both ways
        for kind, time in (("smelting", 200), ("blasting", 100)):
            write(os.path.join(rd, f"{m}_ingot_from_{kind}.json"), {"type": f"minecraft:{kind}",
                  "category": "misc", "ingredient": {"item": f"{NS}:raw_{m}"},
                  "result": {"id": f"{NS}:{m}_ingot"}, "experience": 0.7, "cookingtime": time})
        for small, big, n in ((f"{m}_nugget", f"{m}_ingot", 9), (f"{m}_ingot", f"{m}_block", 9),
                              (f"raw_{m}", f"raw_{m}_block", 9)):
            write(os.path.join(rd, f"{big}_from_{small}.json"), {"type": "minecraft:crafting_shaped",
                  "category": "misc", "pattern": ["###", "###", "###"], "key": {"#": {"item": f"{NS}:{small}"}},
                  "result": {"count": 1, "id": f"{NS}:{big}"}})
            write(os.path.join(rd, f"{small}_from_{big}.json"), {"type": "minecraft:crafting_shapeless",
                  "category": "misc", "ingredients": [{"item": f"{NS}:{big}"}],
                  "result": {"count": n, "id": f"{NS}:{small}"}})
        # c: tags so every mod's recipes take ours
        tag("c", "item", f"ingots/{m}", [f"{NS}:{m}_ingot"])
        tag("c", "item", f"nuggets/{m}", [f"{NS}:{m}_nugget"])
        tag("c", "item", f"raw_materials/{m}", [f"{NS}:raw_{m}"])
        tag("c", "item", f"storage_blocks/{m}", [f"{NS}:{m}_block"])
        tag("c", "block", f"storage_blocks/{m}", [f"{NS}:{m}_block"])
        tag("c", "item", f"storage_blocks/raw_{m}", [f"{NS}:raw_{m}_block"])
    write(os.path.join(IM, "rime_shard.json"), {"parent": "minecraft:item/generated",
          "textures": {"layer0": f"{NS}:item/rime_shard"}})
    tag("c", "item", "gems/rime", [f"{NS}:rime_shard"])
    by_metal = {}
    for name, (host, drop, *_r) in ORES.items():
        key = drop.split(":")[1].replace("raw_", "")
        by_metal.setdefault(key, []).append(f"{NS}:{name}")
    for key, blocks in by_metal.items():
        tag("c", "block", f"ores/{key}", blocks)
        tag("c", "item", f"ores/{key}", blocks)
    tag("c", "block", "ores", [f"{NS}:{n}" for n in ORES])
    tag("c", "item", "ores", [f"{NS}:{n}" for n in ORES])
    tag("minecraft", "block", "mineable/pickaxe", [f"{NS}:{n}" for n in ORES]
        + [f"{NS}:{m}_block" for m in METALS] + [f"{NS}:raw_{m}_block" for m in METALS])
    for tier in ("stone", "iron", "diamond"):
        blocks = [f"{NS}:{n}" for n, tr in ORE_TIER.items() if tr == tier] + [
            f"{NS}:{m}_block" for m, (_a, _b, tr) in METALS.items() if tr == tier] + [
            f"{NS}:raw_{m}_block" for m, (_a, _b, tr) in METALS.items() if tr == tier]
        if blocks:
            tag("minecraft", "block", f"needs_{tier}_tool", blocks)
    # Worldgen: Stellaris's vein sizes and counts, in each world's stone.
    for name, (host, _d, _c, _g, size, count, lo, hi, world) in ORES.items():
        if host is None:
            target = {"predicate_type": "minecraft:tag_match",
                      "tag": "minecraft:deepslate_ore_replaceables" if name.startswith("deepslate")
                      else "minecraft:stone_ore_replaceables"}
        else:
            target = {"predicate_type": "minecraft:block_match", "block": HOST_BLOCK[host]}
        write(os.path.join(WG, "configured_feature", name + ".json"), {"type": "minecraft:ore", "config": {
              "discard_chance_on_air_exposure": 0.0, "size": size,
              "targets": [{"state": {"Name": f"{NS}:{name}"}, "target": target}]}})
        write(os.path.join(WG, "placed_feature", name + ".json"), {"feature": f"{NS}:{name}", "placement": [
              {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
              {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid",
               "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}},
              {"type": "minecraft:biome"}]})
    bm = os.path.join(DATA, "neoforge/biome_modifier")
    for world, biomes in WORLD_BIOMES.items():
        write(os.path.join(bm, f"{world}_ores.json"), {"type": "neoforge:add_features", "biomes": biomes,
              "features": [f"{NS}:{n}" for n, v in ORES.items() if v[-1] == world], "step": "underground_ores"})
    write(os.path.join(bm, "overworld_steel_ore.json"), {"type": "neoforge:add_features",
          "biomes": "#minecraft:is_overworld", "features": [f"{NS}:steel_ore", f"{NS}:deepslate_steel_ore"],
          "step": "underground_ores"})
    for name in ORES:
        LANG[f"block.{NS}.{name}"] = " ".join(w.capitalize() for w in name.split("_"))
    for m in METALS:
        c = m.capitalize()
        LANG.update({f"block.{NS}.{m}_block": f"Block of {c}", f"block.{NS}.raw_{m}_block": f"Block of Raw {c}",
                     f"item.{NS}.raw_{m}": f"Raw {c}", f"item.{NS}.{m}_ingot": f"{c} Ingot",
                     f"item.{NS}.{m}_nugget": f"{c} Nugget"})
    LANG[f"item.{NS}.rime_shard"] = "Rime Shard"


# ---------------------------------------------------------------------------
# Building blocks for the ported structures
# ---------------------------------------------------------------------------
STONE_BASE = {"pale": "pale_stone", "frost": "frost_stone", "cinder": "cinder_stone"}
PLATING = {"iron": ("stone", "inert"), "steel": ("voidsteel", "inert"), "lunite": ("aetherium", "aetherium")}


def _shade(px, x, y, k):
    r, g, b, a = px[x, y]
    return (max(0, min(255, int(r * k))), max(0, min(255, int(g * k))), max(0, min(255, int(b * k))), a)


def stone_variants(world, base):
    src = Image.open(os.path.join(TEX, base + ".png")).convert("RGBA")
    # bricks: mortar lines every 4 rows, offset joints
    bricks = src.copy(); bp = bricks.load()
    for y in range(16):
        for x in range(16):
            if y % 4 == 3 or (x + (4 if (y // 4) % 2 else 0)) % 8 == 7:
                bp[x, y] = _shade(bp, x, y, 0.55)
    bricks.save(os.path.join(TEX, f"{world}_stone_bricks.png"))
    # polished: smoothed (average with neighbours) and a bevel
    pol = src.copy(); pp = pol.load(); sp = src.load()
    for y in range(16):
        for x in range(16):
            n = [sp[(x + dx) % 16, (y + dy) % 16] for dx in (-1, 0, 1) for dy in (-1, 0, 1)]
            pp[x, y] = tuple(sum(c[i] for c in n) // 9 for i in range(3)) + (255,)
    for i in range(16):
        pp[i, 0] = _shade(pp, i, 0, 1.2); pp[0, i] = _shade(pp, 0, i, 1.2)
        pp[i, 15] = _shade(pp, i, 15, 0.7); pp[15, i] = _shade(pp, 15, i, 0.7)
    pol.save(os.path.join(TEX, f"polished_{world}_stone.png"))
    # chiseled: polished with an inset square
    ch = pol.copy(); cp_ = ch.load()
    for i in range(4, 12):
        for e in (4, 11):
            cp_[i, e] = _shade(cp_, i, e, 0.6); cp_[e, i] = _shade(cp_, e, i, 0.6)
    ch.save(os.path.join(TEX, f"chiseled_{world}_stone_bricks.png"))
    # pillar: vertical fluting on the side, polished top
    side = pol.copy(); sd = side.load()
    for y in range(16):
        for x in (3, 7, 11):
            sd[x, y] = _shade(sd, x, y, 0.65)
    side.save(os.path.join(TEX, f"{world}_stone_pillar.png"))
    pol.save(os.path.join(TEX, f"{world}_stone_pillar_top.png"))
    # cobblestone: dark outlines around blobs
    cob = src.copy(); cb = cob.load()
    import random as _r
    rng = _r.Random(world)
    for _ in range(40):
        x, y = rng.randint(0, 15), rng.randint(0, 15)
        cb[x, y] = _shade(cb, x, y, 0.5)
    cob.save(os.path.join(TEX, f"{world}_cobblestone.png"))


def slab_and_stairs(name, full_model, tex_side, tex_top=None):
    tex_top = tex_top or tex_side
    t3 = {"bottom": f"{NS}:block/{tex_top}", "top": f"{NS}:block/{tex_top}", "side": f"{NS}:block/{tex_side}"}
    if name.endswith("_slab"):
        write(os.path.join(BM, name + ".json"), {"parent": "minecraft:block/slab", "textures": t3})
        write(os.path.join(BM, name + "_top.json"), {"parent": "minecraft:block/slab_top", "textures": t3})
        vs = vjson("assets/minecraft/blockstates/stone_brick_slab.json")
        vs = sub_all(vs, [("minecraft:block/stone_brick_slab_top", f"{NS}:block/{name}_top"),
                          ("minecraft:block/stone_brick_slab", f"{NS}:block/{name}"),
                          ("minecraft:block/stone_bricks", f"{NS}:block/{full_model}")])
        write(os.path.join(BS, name + ".json"), vs)
        loot = sub_all(vjson("data/minecraft/loot_table/blocks/stone_brick_slab.json"),
                       [("minecraft:stone_brick_slab", f"{NS}:{name}")])
        write(os.path.join(DATA, "loot_table/blocks", name + ".json"), loot)
    else:
        for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
            write(os.path.join(BM, name + suffix + ".json"), {"parent": f"minecraft:block/{parent}", "textures": t3})
        vs = vjson("assets/minecraft/blockstates/stone_brick_stairs.json")
        vs = sub_all(vs, [("minecraft:block/stone_brick_stairs", f"{NS}:block/{name}")])
        write(os.path.join(BS, name + ".json"), vs)
        write(os.path.join(DATA, "loot_table/blocks", name + ".json"), self_drop(name))
    write(os.path.join(IM, name + ".json"), {"parent": f"{NS}:block/{name}"})


def building_blocks():
    sys.path.insert(0, os.path.join(os.path.dirname(HERE), "art"))
    import sprites as art_sprites
    names = []
    for w, base in STONE_BASE.items():
        stone_variants(w, base)
        for full in (f"{w}_cobblestone", f"polished_{w}_stone", f"{w}_stone_bricks", f"chiseled_{w}_stone_bricks"):
            cube(full); names.append(full)
            write(os.path.join(DATA, "loot_table/blocks", full + ".json"), self_drop(full))
        for part, full in ((f"{w}_stone", base), (f"polished_{w}_stone", f"polished_{w}_stone"),
                           (f"{w}_stone_brick", f"{w}_stone_bricks")):
            for kind in ("_stairs", "_slab"):
                slab_and_stairs(part + kind, full, full); names.append(part + kind)
        pillar(f"{w}_stone_pillar", f"{w}_stone_pillar", f"{w}_stone_pillar_top"); names.append(f"{w}_stone_pillar")
        write(os.path.join(DATA, "loot_table/blocks", f"{w}_stone_pillar.json"), self_drop(f"{w}_stone_pillar"))
    # the pale moon's deep layer, and smooth cinder sandstone
    deep = Image.open(os.path.join(TEX, "pale_stone.png")).convert("RGBA")
    dp = deep.load()
    for y in range(16):
        for x in range(16):
            dp[x, y] = _shade(dp, x, y, 0.62)
    deep.save(os.path.join(TEX, "pale_deepslate.png"))
    ss = Image.open(os.path.join(TEX, "cinder_sandstone.png")).convert("RGBA")
    sp = ss.load(); sm = ss.copy(); smp = sm.load()
    for y in range(16):
        for x in range(16):
            n = [sp[(x + dx) % 16, (y + dy) % 16] for dx in (-1, 0, 1) for dy in (-1, 0, 1)]
            smp[x, y] = tuple(sum(c[i] for c in n) // 9 for i in range(3)) + (255,)
    sm.save(os.path.join(TEX, "smooth_cinder_sandstone.png"))
    for full in ("pale_deepslate", "smooth_cinder_sandstone"):
        cube(full); names.append(full)
        write(os.path.join(DATA, "loot_table/blocks", full + ".json"), self_drop(full))
    for kind in ("_stairs", "_slab"):
        slab_and_stairs("smooth_cinder_sandstone" + kind, "smooth_cinder_sandstone", "smooth_cinder_sandstone")
        names.append("smooth_cinder_sandstone" + kind)
    # metal plating, from the art library's plate drawer
    for m, (metal, glow) in PLATING.items():
        art_sprites.storage_block(metal, glow, seed=11).save(os.path.join(TEX, f"{m}_plating.png"))
        art_sprites.workstation("cable" if False else "rift_frame", metal, glow).save(
            os.path.join(TEX, f"{m}_pillar.png"))
        art_sprites.storage_block(metal, glow, seed=5).save(os.path.join(TEX, f"{m}_pillar_top.png"))
        cube(f"{m}_plating"); names.append(f"{m}_plating")
        write(os.path.join(DATA, "loot_table/blocks", f"{m}_plating.json"), self_drop(f"{m}_plating"))
        for kind in ("_stairs", "_slab"):
            slab_and_stairs(f"{m}_plating" + kind, f"{m}_plating", f"{m}_plating"); names.append(f"{m}_plating" + kind)
        pillar(f"{m}_pillar", f"{m}_pillar", f"{m}_pillar_top"); names.append(f"{m}_pillar")
        write(os.path.join(DATA, "loot_table/blocks", f"{m}_pillar.json"), self_drop(f"{m}_pillar"))
    tag("minecraft", "block", "mineable/pickaxe", [f"{NS}:{n}" for n in names])
    tag("minecraft", "block", "stairs", [f"{NS}:{n}" for n in names if n.endswith("_stairs")])
    tag("minecraft", "item", "stairs", [f"{NS}:{n}" for n in names if n.endswith("_stairs")])
    tag("minecraft", "block", "slabs", [f"{NS}:{n}" for n in names if n.endswith("_slab")])
    tag("minecraft", "item", "slabs", [f"{NS}:{n}" for n in names if n.endswith("_slab")])
    for n in names:
        words = n.split("_")
        LANG[f"block.{NS}.{n}"] = " ".join(w.capitalize() for w in words)
    # crafting: stonecutter from the base, like vanilla
    rd = os.path.join(DATA, "recipe")
    for w, base in STONE_BASE.items():
        for out, count in ((f"{w}_stone_bricks", 1), (f"polished_{w}_stone", 1), (f"chiseled_{w}_stone_bricks", 1),
                           (f"{w}_stone_stairs", 1), (f"{w}_stone_slab", 2), (f"{w}_stone_brick_stairs", 1),
                           (f"{w}_stone_brick_slab", 2), (f"polished_{w}_stone_stairs", 1),
                           (f"polished_{w}_stone_slab", 2), (f"{w}_stone_pillar", 1)):
            write(os.path.join(rd, f"{out}_stonecutting.json"), {"type": "minecraft:stonecutting",
                  "ingredient": {"item": f"{NS}:{base}"}, "result": {"count": count, "id": f"{NS}:{out}"}})
        write(os.path.join(rd, f"{base}_from_{w}_cobblestone.json"), {"type": "minecraft:smelting",
              "category": "blocks", "ingredient": {"item": f"{NS}:{w}_cobblestone"},
              "result": {"id": f"{NS}:{base}"}, "experience": 0.1, "cookingtime": 200})
    for m in PLATING:
        ingot = {"tag": f"c:ingots/{m}"}
        write(os.path.join(rd, f"{m}_plating.json"), {"type": "minecraft:crafting_shaped", "category": "building",
              "pattern": ["##", "##"], "key": {"#": ingot}, "result": {"count": 4, "id": f"{NS}:{m}_plating"}})
        for out, count in ((f"{m}_plating_stairs", 1), (f"{m}_plating_slab", 2), (f"{m}_pillar", 1)):
            write(os.path.join(rd, f"{out}_stonecutting.json"), {"type": "minecraft:stonecutting",
                  "ingredient": {"item": f"{NS}:{m}_plating"}, "result": {"count": count, "id": f"{NS}:{out}"}})


def part_art():
    """Rocket parts in the rocket's own colours."""
    def sprite(name, pixels):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        px = img.load()
        for (x, y), c in pixels.items():
            px[x, y] = c
        img.save(os.path.join(ITEM_TEX, name + ".png"))
    nose = {}
    for y in range(3, 14):
        half = max(0, (y - 3) // 2)
        for x in range(7 - half, 9 + half):
            nose[(x, y)] = TRIM if y < 6 else HULL
    for x in range(3, 13):
        nose[(x, 13)] = STEEL
    sprite("rocket_nose_cone", nose)
    fin = {}
    for y in range(3, 14):
        for x in range(4, 4 + (y - 2)):
            if x < 13:
                fin[(x, y)] = TRIM if x == 4 else HULL
    sprite("rocket_fin", fin)
    engine = {}
    for y in range(3, 9):
        for x in range(5, 11):
            engine[(x, y)] = HULL if x not in (5, 10) else STEEL
    for y in range(9, 14):
        w = y - 8
        for x in range(5 - w, 11 + w):
            if 0 <= x < 16:
                engine[(x, y)] = HULL_DARK if y < 13 else (0xFF, 0x9A, 0x3C, 255)
    sprite("rocket_engine", engine)
    motor = dict(engine)
    for y in range(4, 8):
        for x in range(6, 10):
            motor[(x, y)] = (0xB8, 0xA8, 0xFF, 255)
    sprite("hydrogen_motor", motor)
    can = {}
    for y in range(3, 15):
        for x in range(4, 12):
            can[(x, y)] = STEEL if x in (4, 11) or y in (3, 14) else (0xE8, 0xE4, 0xFF, 255)
    for y in range(7, 11):
        for x in range(6, 10):
            can[(x, y)] = (0x9A, 0x84, 0xE8, 255)
    for x in range(6, 10):
        can[(x, 2)] = STEEL
    sprite("hydrogen_canister", can)


def distributor():
    """A placeholder machine face in the Elysium palette until the reskin: a
    dark frame, a vent grille, and a green core that reads as 'air'."""
    frame, panel, vent, core = (0x1B, 0x22, 0x2B, 255), (0x2E, 0x3A, 0x47, 255), \
        (0x12, 0x17, 0x1D, 255), (0x3F, 0xC8, 0x7A, 255)
    for name, has_core in (("oxygen_distributor_side", False), ("oxygen_distributor_top", True)):
        img = Image.new("RGBA", (16, 16), panel)
        px = img.load()
        for i in range(16):
            for e in (0, 15):
                px[i, e] = frame
                px[e, i] = frame
        for y in range(3, 13, 2):
            for x in range(3, 13):
                px[x, y] = vent
        if has_core:
            for y in range(6, 10):
                for x in range(6, 10):
                    px[x, y] = core
        img.save(os.path.join(TEX, name + ".png"))
    rocket_art()
    suit_art()
    machine_art()
    part_art()
    bucket_art("oil_bucket", (0x1E, 0x17, 0x14))
    bucket_art("fuel_bucket", (0xE0, 0xB0, 0x40))
    bucket_art("diesel_bucket", (0x6B, 0x5A, 0x2E))
    tank = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    tk = tank.load()
    for y in range(2, 15):
        for x in range(3, 13):
            tk[x, y] = STEEL if x in (3, 12) or y in (2, 14) else HULL
    for y in range(5, 12):
        for x in range(5, 11):
            tk[x, y] = (0xE0, 0xB0, 0x40, 255) if y > 7 else HULL_DARK
    for x in range(3, 13):
        tk[x, 6] = TRIM
    tank.save(os.path.join(ITEM_TEX, "large_fuel_tank.png"))


# ---------------------------------------------------------------------------
# Models and blockstates
# ---------------------------------------------------------------------------
BS = os.path.join(ASSETS, "blockstates")
BM = os.path.join(ASSETS, "models/block")
IM = os.path.join(ASSETS, "models/item")


def cube(name, tex=None):
    write(os.path.join(BM, name + ".json"),
          {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{tex or name}"}})
    write(os.path.join(BS, name + ".json"), {"variants": {"": {"model": f"{NS}:block/{name}"}}})
    write(os.path.join(IM, name + ".json"), {"parent": f"{NS}:block/{name}"})


def pillar(name, side, end):
    write(os.path.join(BM, name + ".json"), {"parent": "minecraft:block/cube_column",
          "textures": {"side": f"{NS}:block/{side}", "end": f"{NS}:block/{end}"}})
    write(os.path.join(BM, name + "_horizontal.json"), {"parent": "minecraft:block/cube_column_horizontal",
          "textures": {"side": f"{NS}:block/{side}", "end": f"{NS}:block/{end}"}})
    m, h = f"{NS}:block/{name}", f"{NS}:block/{name}_horizontal"
    write(os.path.join(BS, name + ".json"), {"variants": {
        "axis=x": {"model": h, "x": 90, "y": 90}, "axis=y": {"model": m},
        "axis=z": {"model": h, "x": 90}}})
    write(os.path.join(IM, name + ".json"), {"parent": m})


# Exact ids only. A prefix rule ("minecraft:mangrove" -> ...) also renames
# vanilla's minecraft:mangrove_root_placer, a codec type that does not exist
# under our namespace, and the world would fail to load.
_MANGROVE_BLOCKS = ["mangrove_log", "mangrove_leaves", "mangrove_roots", "mangrove_propagule",
                    "muddy_mangrove_roots", "stripped_mangrove_log", "mangrove_wood",
                    "stripped_mangrove_wood", "mangrove_planks"]
def rename_mangrove(obj):
    """Exact-match rename of mangrove ids, and of model/texture paths."""
    if isinstance(obj, dict):
        return {k: rename_mangrove(v) for k, v in obj.items()}
    if isinstance(obj, list):
        return [rename_mangrove(v) for v in obj]
    if isinstance(obj, str):
        # Vanilla models write "block/x" with no namespace, which means
        # minecraft: — so bare paths are renamed too, or Emgrove renders red.
        for prefix in ("minecraft:block/", "minecraft:item/", "block/", "item/", "minecraft:"):
            if obj.startswith(prefix):
                rest = obj[len(prefix):]
                base = rest.split("_hanging_")[0]
                if rest in _MANGROVE_BLOCKS or base in _MANGROVE_BLOCKS or rest in (
                        "mangrove_roots_side", "mangrove_roots_top", "mangrove_log_top",
                        "stripped_mangrove_log_top", "mangrove_propagule_hanging",
                        "muddy_mangrove_roots_side", "muddy_mangrove_roots_top"):
                    kind = prefix.replace("minecraft:", "")
                    return f"{NS}:{kind}" + rest.replace("mangrove", "emgrove")
        return obj
    return obj


def from_vanilla(kind, vname, ours, render_type=None):
    data = rename_mangrove(vjson(f"assets/minecraft/{kind}/{vname}.json"))
    if render_type:
        # Vanilla's own blocks are cut out by a table the game keeps for them;
        # a copy under our name is not in it, and would draw its transparent
        # pixels black unless the model says which layer it wants.
        data = dict(data, render_type=render_type)
    folder = {"blockstates": BS, "models/block": BM, "models/item": IM}[kind]
    write(os.path.join(folder, ours + ".json"), data)


def models():
    for name in ("pale_regolith", "pale_stone", "frost_regolith", "frost_stone",
                 "cinder_sand", "cinder_stone", "cinder_sandstone"):
        cube(name)
    # Vines: a cross, lit when it carries berries, as Stellaris's moon vines.
    for part in ("pale_vines", "pale_vines_plant"):
        for lit in ("", "_lit"):
            write(os.path.join(BM, part + lit + ".json"), {"parent": "minecraft:block/cross",
                  "render_type": "minecraft:cutout", "textures": {"cross": f"{NS}:block/{part}{lit}"}})
        write(os.path.join(BS, part + ".json"), {"variants": {
            "berries=false": {"model": f"{NS}:block/{part}"},
            "berries=true": {"model": f"{NS}:block/{part}_lit"}}})
    write(os.path.join(IM, "pale_vines.json"), {"parent": "minecraft:item/generated",
          "textures": {"layer0": f"{NS}:block/pale_vines"}})

    pillar("emgrove_log", "emgrove_log", "emgrove_log_top")
    pillar("stripped_emgrove_log", "stripped_emgrove_log", "stripped_emgrove_log_top")
    pillar("emgrove_wood", "emgrove_log", "emgrove_log")
    pillar("stripped_emgrove_wood", "stripped_emgrove_log", "stripped_emgrove_log")
    pillar("muddy_emgrove_roots", "muddy_emgrove_roots_side", "muddy_emgrove_roots_top")
    cube("emgrove_planks")
    write(os.path.join(BM, "emgrove_leaves.json"), {"parent": "minecraft:block/cube_all",
          "render_type": "minecraft:cutout_mipped", "textures": {"all": f"{NS}:block/emgrove_leaves"}})
    write(os.path.join(BS, "emgrove_leaves.json"), {"variants": {"": {"model": f"{NS}:block/emgrove_leaves"}}})
    write(os.path.join(IM, "emgrove_leaves.json"), {"parent": f"{NS}:block/emgrove_leaves"})
    # Roots and the propagule keep vanilla's shapes and states, with our art.
    from_vanilla("blockstates", "mangrove_roots", "emgrove_roots")
    from_vanilla("models/block", "mangrove_roots", "emgrove_roots", "minecraft:cutout")
    write(os.path.join(IM, "emgrove_roots.json"), {"parent": f"{NS}:block/emgrove_roots"})
    from_vanilla("blockstates", "mangrove_propagule", "emgrove_propagule")
    from_vanilla("models/block", "mangrove_propagule", "emgrove_propagule", "minecraft:cutout")
    for i in range(5):
        from_vanilla("models/block", f"mangrove_propagule_hanging_{i}",
                     f"emgrove_propagule_hanging_{i}", "minecraft:cutout")
    write(os.path.join(IM, "emgrove_propagule.json"), {"parent": "minecraft:item/generated",
          "textures": {"layer0": f"{NS}:item/emgrove_propagule"}})

    for machine in ("oxygen_distributor", "coal_generator", "fuel_refinery", "water_separator", "pumpjack",
                    "diesel_generator", "fluid_tank"):
        cube(machine)
    write(os.path.join(BM, "solar_panel.json"), {"parent": "minecraft:block/cube_bottom_top",
          "textures": {"side": f"{NS}:block/solar_panel_side", "top": f"{NS}:block/solar_panel_top",
                       "bottom": f"{NS}:block/solar_panel_side"}})
    write(os.path.join(BS, "solar_panel.json"), {"variants": {"": {"model": f"{NS}:block/solar_panel"}}})
    write(os.path.join(IM, "solar_panel.json"), {"parent": f"{NS}:block/solar_panel"})
    conduit_models()
    for fluid in ("oil", "fuel", "diesel"):
        write(os.path.join(BM, fluid + ".json"), {"textures": {"particle": "minecraft:block/water_still"}})
        write(os.path.join(BS, fluid + ".json"), {"variants": {"": {"model": f"{NS}:block/{fluid}"}}})
    write(os.path.join(BM, "launch_pad.json"), {"parent": "minecraft:block/cube_bottom_top",
          "textures": {"side": f"{NS}:block/launch_pad_side", "top": f"{NS}:block/launch_pad_top",
                       "bottom": f"{NS}:block/launch_pad_side"}})
    write(os.path.join(BS, "launch_pad.json"), {"variants": {"": {"model": f"{NS}:block/launch_pad"}}})
    write(os.path.join(IM, "launch_pad.json"), {"parent": f"{NS}:block/launch_pad"})
    for item in ("rocket", "fuel_canister", "oxygen_canister", "oil_bucket", "fuel_bucket", "diesel_bucket",
                 "large_fuel_tank",
                 "rocket_nose_cone", "rocket_fin", "rocket_engine", "hydrogen_motor", "hydrogen_canister", "jet_suit",
                 "space_suit_helmet",
                 "space_suit_chestplate", "space_suit_leggings", "space_suit_boots"):
        write(os.path.join(IM, item + ".json"), {"parent": "minecraft:item/generated",
              "textures": {"layer0": f"{NS}:item/{item}"}})


# ---------------------------------------------------------------------------
# Loot, recipes, tags
# ---------------------------------------------------------------------------
def self_drop(name):
    return {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]}


SELF_DROPS = ["pale_regolith", "pale_stone", "frost_regolith", "frost_stone", "cinder_sand",
              "cinder_stone", "cinder_sandstone", "emgrove_log", "emgrove_wood",
              "stripped_emgrove_log", "stripped_emgrove_wood", "emgrove_planks", "emgrove_roots",
              "muddy_emgrove_roots", "oxygen_distributor", "launch_pad", "coal_generator", "solar_panel",
              "fuel_refinery", "water_separator", "cable", "pipe", "pumpjack", "diesel_generator"]


def loot():
    lt = os.path.join(DATA, "loot_table/blocks")
    for name in SELF_DROPS:
        write(os.path.join(lt, name + ".json"), self_drop(name))
    # The fluid tank keeps what it holds: the block entity's contents ride on the item.
    tank = self_drop("fluid_tank")
    tank["pools"][0]["entries"][0]["functions"] = [{"function": "minecraft:copy_components",
        "source": "block_entity", "include": [f"{NS}:tank_fluid"]}]
    write(os.path.join(lt, "fluid_tank.json"), tank)
    for vname, ours in (("mangrove_leaves", "emgrove_leaves"), ("mangrove_propagule", "emgrove_propagule"),
                        ("cave_vines", "pale_vines"), ("cave_vines_plant", "pale_vines_plant")):
        data = rename_mangrove(vjson(f"data/minecraft/loot_table/blocks/{vname}.json"))
        if "cave_vines" in vname:
            data = sub_all(data, [("minecraft:cave_vines_plant", f"{NS}:pale_vines_plant"),
                                  ("minecraft:cave_vines", f"{NS}:pale_vines")])
        write(os.path.join(lt, ours + ".json"), data)


def recipes():
    rd = os.path.join(DATA, "recipe")
    write(os.path.join(rd, "emgrove_planks.json"), {"type": "minecraft:crafting_shapeless",
          "category": "building", "group": "planks",
          "ingredients": [{"tag": f"{NS}:emgrove_logs"}],
          "result": {"count": 4, "id": f"{NS}:emgrove_planks"}})
    for wood, log in (("emgrove_wood", "emgrove_log"), ("stripped_emgrove_wood", "stripped_emgrove_log")):
        write(os.path.join(rd, wood + ".json"), {"type": "minecraft:crafting_shaped",
              "category": "building", "group": "bark", "pattern": ["##", "##"],
              "key": {"#": {"item": f"{NS}:{log}"}}, "result": {"count": 3, "id": f"{NS}:{wood}"}})
    # Machines: vanilla parts until the S8 reskin gives them Elysium components.
    I, B, R, G, F = ({"item": "minecraft:iron_ingot"}, {"item": "minecraft:iron_block"},
                     {"item": "minecraft:redstone"}, {"item": "minecraft:glass"}, {"item": "minecraft:furnace"})
    for name, pattern, key in (
            ("oxygen_distributor", ["IGI", "GRG", "IGI"], {"I": I, "G": G, "R": {"item": "minecraft:redstone_block"}}),
            ("coal_generator", ["III", "IFI", "IRI"], {"I": I, "F": F, "R": R}),
            ("solar_panel", ["GGG", "IDI", "IRI"], {"G": G, "I": I, "D": {"item": "minecraft:daylight_detector"}, "R": R}),
            ("fuel_refinery", ["IBI", "FUF", "IRI"], {"I": I, "B": {"item": "minecraft:bucket"}, "F": F,
                                                      "U": B, "R": R}),
            ("water_separator", ["IGI", "GCG", "IRI"], {"I": I, "G": G, "C": {"item": "minecraft:cauldron"}, "R": R}),
            ("large_fuel_tank", ["L L", "LBL", "LLL"], {"L": {"tag": "c:ingots/lunite"},
                                                        "B": {"item": "minecraft:bucket"}}),
            ("pumpjack", ["IPI", "IPI", "BRB"], {"I": I, "P": {"item": "minecraft:piston"}, "B": B, "R": R}),
            ("diesel_generator", ["III", "IFI", "IBI"], {"I": I, "F": F, "B": {"item": "minecraft:bucket"}}),
            ("fluid_tank", ["IGI", "G G", "IGI"], {"I": I, "G": G})):
        write(os.path.join(rd, name + ".json"), {"type": "minecraft:crafting_shaped", "category": "misc",
              "pattern": pattern, "key": key, "result": {"count": 1, "id": f"{NS}:{name}"}})
    # Placeholders until the rocket station and fuel refinery (S6).
    write(os.path.join(rd, "launch_pad.json"), {"type": "minecraft:crafting_shaped",
          "category": "misc", "pattern": ["III", "SSS"],
          "key": {"I": {"item": "minecraft:iron_ingot"}, "S": {"item": "minecraft:smooth_stone"}},
          "result": {"count": 4, "id": f"{NS}:launch_pad"}})
    for name, mid in (("cable", {"item": "minecraft:copper_ingot"}), ("pipe", {"item": "minecraft:glass"})):
        write(os.path.join(rd, name + ".json"), {"type": "minecraft:crafting_shaped", "category": "misc",
              "pattern": ["III", "MRM", "III"],
              "key": {"I": {"item": "minecraft:iron_nugget"}, "M": mid, "R": {"item": "minecraft:redstone"}},
              "result": {"count": 8, "id": f"{NS}:{name}"}})
    write(os.path.join(rd, "jet_suit.json"), {"type": "minecraft:crafting_shaped", "category": "equipment",
          "pattern": ["FCF", "EBE"],
          "key": {"F": {"item": f"{NS}:rocket_fin"}, "C": {"item": f"{NS}:space_suit_chestplate"},
                  "E": {"item": f"{NS}:rocket_engine"}, "B": {"item": "minecraft:blaze_rod"}},
          "result": {"count": 1, "id": f"{NS}:jet_suit"}})
    # Rocket parts, after Stellaris's recipes; iron stands in for steel until the ores phase.
    Ig, Ib, Rs = {"tag": "c:ingots/steel"}, {"tag": "c:storage_blocks/steel"}, {"item": "minecraft:redstone"}
    for name, pattern, key in (
            ("rocket_nose_cone", [" X ", " # ", "###"], {"#": Ig, "X": Rs}),
            ("rocket_fin", ["###", "# #"], {"#": Ig}),
            ("rocket_engine", ["RXR", "# #", "# #"], {"#": Ig, "R": Ib, "X": Rs}),
            ("hydrogen_motor", ["DDD", "EGG", "DDD"], {"D": {"item": "minecraft:diamond"},
                                                       "E": {"tag": "c:storage_blocks/lunite"},
                                                       "G": {"item": f"{NS}:rime_shard"}})):
        write(os.path.join(rd, name + ".json"), {"type": "minecraft:crafting_shaped", "category": "misc",
              "pattern": pattern, "key": key, "result": {"count": 1, "id": f"{NS}:{name}"}})
    # The rocket: nose over a hull of iron blocks, four fins and the engine
    # (Stellaris builds it at a Rocket Station; a crafting table stands in).
    write(os.path.join(rd, "rocket.json"), {"type": "minecraft:crafting_shaped", "category": "misc",
          "pattern": [" N ", "FBF", "FEF"],
          "key": {"N": {"item": f"{NS}:rocket_nose_cone"}, "F": {"item": f"{NS}:rocket_fin"},
                  "B": Ib, "E": {"item": f"{NS}:rocket_engine"}},
          "result": {"count": 1, "id": f"{NS}:rocket"}})
    # Fuel and oxygen canisters come from bottling at the refinery and separator.
    # Suit and air: placeholders until the S6 machines (a suit bench, oxygen loaders).
    I, G, W = {"item": "minecraft:iron_ingot"}, {"item": "minecraft:glass"}, {"tag": "minecraft:wool"}
    for piece, pattern, key in (
            ("helmet", ["WIW", "IGI"], {"W": W, "I": I, "G": G}),
            ("chestplate", ["I I", "WGW", "IWI"], {"W": W, "I": I, "G": G}),
            ("leggings", ["IWI", "W W", "I I"], {"W": W, "I": I}),
            ("boots", ["W W", "I I"], {"W": W, "I": I})):
        write(os.path.join(rd, f"space_suit_{piece}.json"), {"type": "minecraft:crafting_shaped",
              "category": "equipment", "pattern": pattern, "key": key,
              "result": {"count": 1, "id": f"{NS}:space_suit_{piece}"}})



def tag(ns, kind, name, values):
    write(os.path.join(RES, f"data/{ns}/tags/{kind}/{name}.json"), {"replace": False, "values": values})


def tags():
    e = lambda *n: [f"{NS}:{x}" for x in n]
    logs = e("emgrove_log", "emgrove_wood", "stripped_emgrove_log", "stripped_emgrove_wood")
    tag(NS, "block", "emgrove_logs", logs)
    tag(NS, "item", "emgrove_logs", logs)
    tag("minecraft", "block", "logs_that_burn", [f"#{NS}:emgrove_logs"])
    tag("minecraft", "item", "logs_that_burn", [f"#{NS}:emgrove_logs"])
    tag("minecraft", "block", "planks", e("emgrove_planks"))
    tag("minecraft", "item", "planks", e("emgrove_planks"))
    tag("minecraft", "block", "leaves", e("emgrove_leaves"))
    tag("minecraft", "item", "leaves", e("emgrove_leaves"))
    tag("minecraft", "block", "mineable/axe", logs + e("emgrove_planks", "emgrove_roots",
        "muddy_emgrove_roots"))
    tag("minecraft", "block", "mineable/hoe", e("emgrove_leaves"))
    tag("minecraft", "block", "mineable/shovel", e("pale_regolith", "frost_regolith", "cinder_sand",
        "muddy_emgrove_roots"))
    tag("minecraft", "block", "mineable/pickaxe", e("pale_stone", "frost_stone", "cinder_stone",
        "cinder_sandstone", "oxygen_distributor", "launch_pad", "coal_generator", "solar_panel",
        "fuel_refinery", "water_separator", "cable", "pipe", "pumpjack", "diesel_generator", "fluid_tank"))
    tag("minecraft", "block", "climbable", e("pale_vines", "pale_vines_plant"))
    # Emgrove grows through what mangroves grow through, and through itself.
    grow = ["minecraft:mud", "minecraft:moss_carpet", "minecraft:vine"] + e(
        "muddy_emgrove_roots", "emgrove_roots", "emgrove_leaves", "emgrove_log", "emgrove_propagule")
    tag(NS, "block", "emgrove_logs_can_grow_through", grow)
    tag(NS, "block", "emgrove_roots_can_grow_through", ["minecraft:mud", "minecraft:moss_carpet",
        "minecraft:vine", "minecraft:snow"] + e("muddy_emgrove_roots", "emgrove_roots", "emgrove_propagule"))


# ---------------------------------------------------------------------------
# Emgrove trees and the Emerald Bastion
# ---------------------------------------------------------------------------
WG = os.path.join(DATA, "worldgen")
TREE_PAIRS = [
    ("#minecraft:mangrove_logs_can_grow_through", f"#{NS}:emgrove_logs_can_grow_through"),
    ("#minecraft:mangrove_roots_can_grow_through", f"#{NS}:emgrove_roots_can_grow_through"),
]


def emgrove():
    for v, ours in (("mangrove", "emgrove"), ("tall_mangrove", "tall_emgrove")):
        data = sub_all(rename_mangrove(vjson(f"data/minecraft/worldgen/configured_feature/{v}.json")), TREE_PAIRS)
        data["config"]["dirt_provider"] = {"type": "minecraft:simple_state_provider",
                                           "state": {"Name": "minecraft:mud"}}
        write(os.path.join(WG, "configured_feature", ours + ".json"), data)
    # One tree in about three chunks on the plains: a grove world, not a jungle.
    write(os.path.join(WG, "configured_feature/emgrove_grove.json"), {
        "type": "minecraft:random_selector",
        "config": {"features": [{"chance": 0.15, "feature": f"{NS}:tall_emgrove"}],
                   "default": f"{NS}:emgrove"}})
    write(os.path.join(WG, "placed_feature/emgrove_grove.json"), {
        "feature": f"{NS}:emgrove_grove",
        "placement": [
            {"type": "minecraft:count", "count": {"type": "minecraft:weighted_list", "distribution": [
                {"weight": 7, "data": 0}, {"weight": 3, "data": 1}, {"weight": 1, "data": 2}]}},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 2},
            {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
            {"type": "minecraft:biome"},
            {"type": "minecraft:block_predicate_filter", "predicate": {
                "type": "minecraft:would_survive", "state": {"Name": f"{NS}:emgrove_propagule",
                "Properties": {"age": "0", "hanging": "false", "stage": "0", "waterlogged": "false"}}}}]})


ORE_BLOCKS = {  # deepslate ores only: the Bastion has no stone
    "coal": ("minecraft:deepslate_coal_ore", 17, 24, -64, 192),
    "iron": ("minecraft:deepslate_iron_ore", 9, 20, -64, 128),
    "copper": ("minecraft:deepslate_copper_ore", 10, 16, -32, 112),
    "gold": ("minecraft:deepslate_gold_ore", 9, 6, -64, 32),
    "redstone": ("minecraft:deepslate_redstone_ore", 8, 8, -64, 16),
    "lapis": ("minecraft:deepslate_lapis_ore", 7, 4, -64, 64),
    "diamond": ("minecraft:deepslate_diamond_ore", 8, 5, -64, 16),
    # The Bastion's reason to exist: emerald everywhere, veins of it, at every depth.
    "emerald": ("minecraft:deepslate_emerald_ore", 8, 40, -64, 256),
}


BLACKSTONE_ORES = {"emerald": "minecraft:deepslate_emerald_ore", "gold": "minecraft:gilded_blackstone"}


def ores():
    names = []
    for kind, (block, size, count, lo, hi) in ORE_BLOCKS.items():
        name = f"emerald_bastion_ore_{kind}"
        targets = [{"state": {"Name": block}, "target": {"predicate_type": "minecraft:tag_match",
                                                         "tag": "minecraft:deepslate_ore_replaceables"}}]
        if kind in BLACKSTONE_ORES:
            # The mountains are blackstone, not deepslate; without this they would be barren.
            targets.append({"state": {"Name": BLACKSTONE_ORES[kind]},
                            "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:blackstone"}})
        write(os.path.join(WG, "configured_feature", name + ".json"), {"type": "minecraft:ore",
              "config": {"discard_chance_on_air_exposure": 0.0, "size": size, "targets": targets}})
        write(os.path.join(WG, "placed_feature", name + ".json"), {"feature": f"{NS}:{name}",
              "placement": [{"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
                            {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid",
                             "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}},
                            {"type": "minecraft:biome"}]})
        names.append(f"{NS}:{name}")
    return names


def lakes():
    write(os.path.join(WG, "configured_feature/emerald_bastion_lake.json"), {"type": "minecraft:lake",
          "config": {"fluid": {"type": "minecraft:simple_state_provider",
                               "state": {"Name": "minecraft:water", "Properties": {"level": "0"}}},
                     "barrier": {"type": "minecraft:simple_state_provider",
                                 "state": {"Name": "minecraft:mud"}}}})
    write(os.path.join(WG, "placed_feature/emerald_bastion_lake.json"), {
        "feature": f"{NS}:emerald_bastion_lake",
        "placement": [{"type": "minecraft:rarity_filter", "chance": 6}, {"type": "minecraft:in_square"},
                      {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
                      {"type": "minecraft:biome"}]})
    return f"{NS}:emerald_bastion_lake"


def bastion_biomes(ore_features, lake):
    plains = vjson("data/minecraft/worldgen/biome/plains.json")
    base = {
        "has_precipitation": True, "temperature": 0.8, "downfall": 0.6,
        "effects": {"sky_color": 0x6FBF8E, "fog_color": 0x9FD8B2, "water_color": 0x1F7A5A,
                    "water_fog_color": 0x0B3326, "grass_color": 0x3C8C4E, "foliage_color": 0x2E8B4A,
                    "mood_sound": plains["effects"]["mood_sound"]},
        # A paradise world: animals, no monsters, whatever the light.
        "spawners": {"monster": [], "creature": [s for s in plains["spawners"]["creature"]],
                     "ambient": [], "water_creature": [], "water_ambient": [],
                     "underground_water_creature": [], "axolotls": [], "misc": []},
        "spawn_costs": {},
        "carvers": {"air": ["minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"]},
    }
    # Feature steps (11): lakes at 1, ores at 6, vegetation at 9.
    def features(trees):
        steps = [[] for _ in range(11)]
        steps[1] = [lake]
        steps[6] = list(ore_features)
        steps[9] = ([f"{NS}:emgrove_grove"] if trees else []) + [
            "minecraft:patch_grass_plain", "minecraft:flower_plains"]
        return steps
    for name, trees, extra in (("emerald_bastion_plains", True, {}),
                               ("emerald_bastion_highlands", False, {"temperature": 0.5}),
                               ("emerald_bastion_river", False, {})):
        b = json.loads(json.dumps(base))
        b.update(extra)
        b["features"] = features(trees)
        write(os.path.join(WG, "biome", name + ".json"), b)


BASTION_DF = ["continents", "offset", "factor", "jaggedness", "depth", "sloped_cheese"]

# How the Bastion reads the overworld's erosion: (overworld value, Bastion value).
# Erosion is what decides flat or mountainous. Vanilla's bands are peaks below
# -0.78, mountains to -0.375, hills to 0.05, plains 0.05..0.45, then shattered
# windswept ground (0.45..0.55) and sunken swamp above. Most of the overworld's
# range is folded into the plains band, and never past it; only its low tail
# (below about -0.45) becomes peaks, which the highlands biome sits on and
# paints black. So: a flat green world with ranges of volcanic rock.
BASTION_EROSION = [(-1.0, -0.95), (-0.55, -0.85), (-0.42, -0.4), (-0.3, 0.12), (0.2, 0.25), (1.0, 0.38)]


def bastion_terrain():
    """The overworld's terrain with the oceans taken out and the ground laid flat.

    Continentalness is what makes oceans: below about -0.19 the ground drops
    under sea level. The Bastion reads the overworld's continents squeezed into
    -0.1..0.6 (coast to inland), so there are no oceans. Valleys still fall to
    sea level, and those are its rivers and lakes. Erosion is remapped by
    BASTION_EROSION so the land is plains except for rare mountain ranges.
    Every density function that reads either is copied so it reads ours.
    """
    pairs = [(f"minecraft:overworld/{n}", f"{NS}:emerald_bastion/{n}") for n in BASTION_DF]
    pairs.append(("minecraft:overworld/erosion", f"{NS}:emerald_bastion/erosion"))
    for n in BASTION_DF[1:]:
        data = sub_all(vjson(f"data/minecraft/worldgen/density_function/overworld/{n}.json"), pairs)
        write(os.path.join(WG, "density_function/emerald_bastion", n + ".json"), data)
    write(os.path.join(WG, "density_function/emerald_bastion/continents.json"), {
        "type": "minecraft:flat_cache", "argument": {"type": "minecraft:add", "argument1": 0.25,
            "argument2": {"type": "minecraft:mul", "argument1": 0.35,
                          "argument2": "minecraft:overworld/continents"}}})
    write(os.path.join(WG, "density_function/emerald_bastion/erosion.json"), {
        "type": "minecraft:flat_cache", "argument": {"type": "minecraft:spline", "spline": {
            "coordinate": "minecraft:overworld/erosion",
            "points": [{"location": a, "value": b, "derivative": 0.0} for a, b in BASTION_EROSION]}}})
    ns = sub_all(vjson("data/minecraft/worldgen/noise_settings/overworld.json"), pairs)
    ns["default_block"] = {"Name": "minecraft:deepslate"}
    ns["ore_veins_enabled"] = False             # the veins are granite/tuff-filled; no stone here
    bedrock = ns["surface_rule"]["sequence"][0]
    moss, mud = {"type": "minecraft:block", "result_state": {"Name": "minecraft:moss_block"}}, \
        {"type": "minecraft:block", "result_state": {"Name": "minecraft:mud"}}
    blackstone = {"type": "minecraft:block", "result_state": {"Name": "minecraft:blackstone"}}
    basalt = {"type": "minecraft:block", "result_state": {"Name": "minecraft:basalt",
                                                          "Properties": {"axis": "y"}}}
    magma = {"type": "minecraft:block", "result_state": {"Name": "minecraft:magma_block"}}
    # The highlands are the mountains (erosion below -0.375, which BASTION_EROSION
    # keeps to the ranges), and they are volcanic: blackstone right through above
    # sea level, a skin of basalt in broad patches, and thin seams of magma
    # where the surface noise crosses zero, like cooling cracks.
    volcanic = {"type": "minecraft:condition",
                "if_true": {"type": "minecraft:biome", "biome_is": [f"{NS}:emerald_bastion_highlands"]},
                "then_run": {"type": "minecraft:sequence", "sequence": [
                    {"type": "minecraft:condition", "if_true": {"type": "minecraft:stone_depth",
                         "offset": 0, "add_surface_depth": False, "secondary_depth_range": 0,
                         "surface_type": "floor"},
                     "then_run": {"type": "minecraft:sequence", "sequence": [
                         {"type": "minecraft:condition", "if_true": {"type": "minecraft:noise_threshold",
                              "noise": "minecraft:surface", "min_threshold": -0.025, "max_threshold": 0.025},
                          "then_run": magma},
                         {"type": "minecraft:condition", "if_true": {"type": "minecraft:noise_threshold",
                              "noise": "minecraft:surface", "min_threshold": 0.3, "max_threshold": 1.0},
                          "then_run": basalt},
                         blackstone]}},
                    {"type": "minecraft:condition", "if_true": {"type": "minecraft:y_above",
                         "anchor": {"absolute": 60}, "surface_depth_multiplier": 0, "add_stone_depth": False},
                     "then_run": blackstone}]}}
    ns["surface_rule"] = {"type": "minecraft:sequence", "sequence": [
        bedrock,
        volcanic,
        {"type": "minecraft:condition", "if_true": {"type": "minecraft:above_preliminary_surface"},
         "then_run": {"type": "minecraft:sequence", "sequence": [
             # Cliffs stay bare dark rock, which is most of what the mountains are.
             {"type": "minecraft:condition", "if_true": {"type": "minecraft:steep"},
              "then_run": {"type": "minecraft:block", "result_state": {"Name": "minecraft:deepslate"}}},
             {"type": "minecraft:condition", "if_true": {"type": "minecraft:stone_depth", "offset": 0,
                  "add_surface_depth": False, "secondary_depth_range": 0, "surface_type": "floor"},
              "then_run": {"type": "minecraft:sequence", "sequence": [
                  {"type": "minecraft:condition", "if_true": {"type": "minecraft:water", "offset": -1,
                       "surface_depth_multiplier": 0, "add_stone_depth": False}, "then_run": moss},
                  mud]}},
             {"type": "minecraft:condition", "if_true": {"type": "minecraft:stone_depth", "offset": 0,
                  "add_surface_depth": True, "secondary_depth_range": 0, "surface_type": "floor"},
              "then_run": mud}]}}]}
    write(os.path.join(WG, "noise_settings/emerald_bastion.json"), ns)

    p = lambda t, h, c, e, w, d=0.0: {"temperature": t, "humidity": h, "continentalness": c,
                                      "erosion": e, "weirdness": w, "depth": d, "offset": 0.0}
    whole = [-1.0, 1.0]
    write(os.path.join(DATA, "dimension/emerald_bastion.json"), {
        "type": f"{NS}:emerald_bastion",
        "generator": {"type": "minecraft:noise", "settings": f"{NS}:emerald_bastion",
            "biome_source": {"type": "minecraft:multi_noise", "biomes": [
                # Plains either side of the river's weirdness band, not over it: a
                # biome whose box covers the river's ties with it, and ties go
                # to the first listed, so there would be no river biome at all.
                {"biome": f"{NS}:emerald_bastion_plains", "parameters": p(whole, whole, whole, [-0.2, 1.0], [-1.0, -0.05])},
                {"biome": f"{NS}:emerald_bastion_plains", "parameters": p(whole, whole, whole, [-0.2, 1.0], [0.05, 1.0])},
                {"biome": f"{NS}:emerald_bastion_highlands", "parameters": p(whole, whole, whole, [-1.0, -0.375], whole)},
                {"biome": f"{NS}:emerald_bastion_river", "parameters": p(whole, whole, whole, [-0.2, 1.0], [-0.05, 0.05])},
            ]}}})
    dt = vjson("data/minecraft/dimension_type/overworld.json")
    dt["has_raids"] = False
    write(os.path.join(DATA, "dimension_type/emerald_bastion.json"), dt)


# ---------------------------------------------------------------------------
# Lang
# ---------------------------------------------------------------------------
LANG = {
    "itemGroup.elysiumspace": "Elysium Space",
    "block.elysiumspace.pale_regolith": "Pale Regolith",
    "block.elysiumspace.pale_stone": "Pale Stone",
    "block.elysiumspace.pale_vines": "Pale Vines",
    "block.elysiumspace.pale_vines_plant": "Pale Vines",
    "block.elysiumspace.frost_regolith": "Frost Regolith",
    "block.elysiumspace.frost_stone": "Frost Stone",
    "block.elysiumspace.cinder_sand": "Cinder Sand",
    "block.elysiumspace.cinder_stone": "Cinder Stone",
    "block.elysiumspace.cinder_sandstone": "Cinder Sandstone",
    "block.elysiumspace.emgrove_log": "Emgrove Log",
    "block.elysiumspace.emgrove_wood": "Emgrove Wood",
    "block.elysiumspace.stripped_emgrove_log": "Stripped Emgrove Log",
    "block.elysiumspace.stripped_emgrove_wood": "Stripped Emgrove Wood",
    "block.elysiumspace.emgrove_planks": "Emgrove Planks",
    "block.elysiumspace.emgrove_leaves": "Emgrove Leaves",
    "block.elysiumspace.emgrove_roots": "Emgrove Roots",
    "block.elysiumspace.muddy_emgrove_roots": "Muddy Emgrove Roots",
    "block.elysiumspace.emgrove_propagule": "Emgrove Propagule",
    "block.elysiumspace.oxygen_distributor": "Oxygen Distributor",
    "block.elysiumspace.launch_pad": "Launch Pad",
    "item.elysiumspace.rocket": "Rocket",
    "item.elysiumspace.fuel_canister": "Fuel Canister",
    "entity.elysiumspace.rocket": "Rocket",
    "elysiumspace.rocket.fuel": "Fuel: %s / %s mB",
    "elysiumspace.rocket.no_fuel": "Not enough fuel to reach anywhere",
    "elysiumspace.rocket.how": "Press %s to launch",
    "key.elysiumspace.launch": "Launch rocket",
    "key.categories.elysiumspace": "Elysium Space",
    "elysiumspace.rocket.nowhere": "Rockets cannot launch from here",
    "elysiumspace.rocket.needs_pad": "A rocket must stand on a Launch Pad",
    "elysiumspace.destinations.title": "Choose a destination",
    "elysiumspace.destinations.burn": "Burns %s mB",
    "elysiumspace.destinations.nowhere": "Nowhere within reach. The tank is the limit, not the map.",
    # Space weather, named by the lib's ElysiumWeather under its own keys.
    "elysium.weather.ashfall": "Ashfall",
    "elysium.weather.firestorm": "A firestorm",
    "elysium.weather.ion_storm": "An ion storm",
    "elysium.weather.regolith_gust": "A regolith gust",
    "elysium.weather.solar_flare": "A solar flare",
    "elysium.weather.whiteout": "A whiteout",
    "elysiumspace.destinations.land": "Land here",
    "elysiumspace.destinations.fuel": "Fuel: %s / %s mB",
    "elysiumspace.destinations.overworld": "The Overworld",
    "item.elysiumspace.space_suit_helmet": "Space Suit Helmet",
    "item.elysiumspace.space_suit_chestplate": "Space Suit Chestplate",
    "item.elysiumspace.space_suit_leggings": "Space Suit Leggings",
    "item.elysiumspace.space_suit_boots": "Space Suit Boots",
    "item.elysiumspace.oxygen_canister": "Oxygen Canister",
    "elysiumspace.suit.air": "Air: %s / %s mB",
    "elysiumspace.suit.gauge": "Air: %s s",
    "death.attack.elysiumspace.asphyxiation": "%1$s ran out of air",
    "death.attack.elysiumspace.asphyxiation.player": "%1$s ran out of air fighting %2$s",
    "block.elysiumspace.coal_generator": "Coal Generator",
    "block.elysiumspace.solar_panel": "Solar Panel",
    "block.elysiumspace.fuel_refinery": "Fuel Refinery",
    "block.elysiumspace.water_separator": "Water Separator",
    "block.elysiumspace.oil": "Oil",
    "block.elysiumspace.fuel": "Rocket Fuel",
    "item.elysiumspace.oil_bucket": "Oil Bucket",
    "item.elysiumspace.fuel_bucket": "Rocket Fuel Bucket",
    "item.elysiumspace.large_fuel_tank": "Large Fuel Tank",
    "fluid_type.elysiumspace.oil": "Oil",
    "fluid_type.elysiumspace.fuel": "Rocket Fuel",
    "fluid_type.elysiumspace.oxygen": "Oxygen",
    "fluid_type.elysiumspace.hydrogen": "Hydrogen",
    "elysiumspace.machine.running": "running",
    "elysiumspace.machine.idle": "idle",
    "elysiumspace.machine.generator": "Energy %s / %s FE, %s",
    "elysiumspace.machine.refinery": "Oil %s, Fuel %s, Diesel %s mB, Energy %s / %s FE",
    "elysiumspace.machine.separator": "Water %s, Oxygen %s, Hydrogen %s mB, Energy %s / %s FE",
    "elysiumspace.machine.distributor": "Filling %s blocks, Oxygen %s mB, Energy %s / %s FE, %s",
    "elysiumspace.distributor.open": "the room is open or too large",
    "elysiumspace.distributor.no_air": "out of oxygen",
    "elysiumspace.distributor.no_power": "out of power",
    "item.elysiumspace.rocket_nose_cone": "Rocket Nose Cone",
    "item.elysiumspace.rocket_fin": "Rocket Fin",
    "item.elysiumspace.rocket_engine": "Rocket Engine",
    "item.elysiumspace.hydrogen_motor": "Hydrogen Motor",
    "item.elysiumspace.hydrogen_canister": "Hydrogen Canister",
    "elysiumspace.rocket.motor": "Motor fitted: burns %s",
    "elysiumspace.rocket.empty_first": "Empty the tank before changing the motor",
    "elysiumspace.fuel.hydrogen": "hydrogen",
    "block.elysiumspace.cable": "Cable",
    "block.elysiumspace.pipe": "Pipe",
    "elysiumspace.machine.cable": "Carrying %s / %s FE",
    "elysiumspace.machine.pipe": "Carrying %s, %s / %s mB",
    "elysiumspace.machine.empty": "nothing",
    "block.elysiumspace.pumpjack": "Pumpjack",
    "block.elysiumspace.diesel_generator": "Diesel Generator",
    "block.elysiumspace.fluid_tank": "Fluid Tank",
    "block.elysiumspace.diesel": "Diesel",
    "item.elysiumspace.diesel_bucket": "Diesel Bucket",
    "fluid_type.elysiumspace.diesel": "Diesel",
    "elysiumspace.machine.pumpjack": "Oil below %s mB, Tank %s mB, Energy %s / %s FE",
    "elysiumspace.machine.diesel": "Diesel %s mB, Energy %s / %s FE, %s",
    "item.elysiumspace.jet_suit": "Jet Suit",
    "elysiumspace.jet.fuel": "Jet fuel: %s / %s mB",
    "elysiumspace.jet.mode": "Jet mode: %s",
    "elysiumspace.jet.mode.off": "off",
    "elysiumspace.jet.mode.normal": "flight",
    "elysiumspace.jet.mode.hover": "hover",
    "key.elysiumspace.jet_mode": "Jet suit mode",
    "elysium.hazard.elysiumspace.vacuum": "Vacuum",
    "commands.elysiumspace.travel.unknown": "No world called %s",
    "commands.elysiumspace.travel.done": "Arrived at %s",
    "dimension.elysiumspace.high_orbit": "High Orbit",
    "dimension.elysiumspace.pale_moon": "The Pale Moon",
    "dimension.elysiumspace.cinderveil": "Cinderveil",
    "dimension.elysiumspace.frostfall": "Frostfall",
    "dimension.elysiumspace.emerald_bastion": "The Emerald Bastion",
    "biome.elysiumspace.high_orbit_space": "High Orbit",
    "biome.elysiumspace.pale_moon_desert": "Pale Moon Flats",
    "biome.elysiumspace.pale_moon_caves": "Pale Moon Hollows",
    "biome.elysiumspace.cinderveil_desert": "Cinderveil Dunes",
    "biome.elysiumspace.cinderveil_infernal_barrens": "Cinderveil Barrens",
    "biome.elysiumspace.frostfall_desert": "Frostfall Wastes",
    "biome.elysiumspace.frostfall_ice_spikes": "Frostfall Spires",
    "biome.elysiumspace.frostfall_rocky_plains": "Frostfall Scree",
    "biome.elysiumspace.emerald_bastion_plains": "Emerald Bastion Meadows",
    "biome.elysiumspace.emerald_bastion_highlands": "Emerald Bastion Highlands",
    "biome.elysiumspace.emerald_bastion_river": "Emerald Bastion River",
}


def oil_lakes():
    """Oil lakes, Stellaris's source of oil: tar-black pools rimmed with
    blackstone, on Cinderveil and Frostfall and in the Overworld's deserts."""
    write(os.path.join(WG, "configured_feature/oil_lake.json"), {"type": "minecraft:lake", "config": {
          "fluid": {"type": "minecraft:simple_state_provider",
                    "state": {"Name": f"{NS}:oil", "Properties": {"level": "0"}}},
          "barrier": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:blackstone"}}}})
    for name, chance in (("oil_lake_space", 12), ("oil_lake_overworld", 40)):
        write(os.path.join(WG, "placed_feature", name + ".json"), {"feature": f"{NS}:oil_lake",
              "placement": [{"type": "minecraft:rarity_filter", "chance": chance}, {"type": "minecraft:in_square"},
                            {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
                            {"type": "minecraft:biome"}]})
    bm = os.path.join(DATA, "neoforge/biome_modifier")
    write(os.path.join(bm, "oil_lakes_space.json"), {"type": "neoforge:add_features",
          "biomes": [f"{NS}:cinderveil_desert", f"{NS}:cinderveil_infernal_barrens", f"{NS}:frostfall_desert",
                     f"{NS}:frostfall_rocky_plains"],
          "features": f"{NS}:oil_lake_space", "step": "lakes"})
    write(os.path.join(bm, "oil_lakes_overworld.json"), {"type": "neoforge:add_features",
          "biomes": "#c:is_desert", "features": f"{NS}:oil_lake_overworld", "step": "lakes"})


def damage_types():
    write(os.path.join(DATA, "damage_type/asphyxiation.json"), {"message_id": f"{NS}.asphyxiation",
          "exhaustion": 0.0, "scaling": "never"})
    # Suffocation in a suit is the air running out, not something armour stops.
    tag("minecraft", "damage_type", "bypasses_armor", [f"{NS}:asphyxiation"])


MOB_NAMES = {"moonfolk": "Moonfolk", "hollow_moonfolk": "Hollow Moonfolk", "void_crawler": "Void Crawler",
             "frost_raptor": "Frost Raptor", "cinder_boar": "Cinder Boar", "cinderling": "Cinderling",
             "cinderling_brute": "Cinderling Brute"}


def drop(item, lo, hi, looting=True, weight=None):
    fns = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    if looting:
        fns.append({"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                    "count": {"type": "minecraft:uniform", "min": 0, "max": 1}})
    entry = {"type": "minecraft:item", "name": item, "functions": fns}
    if weight:
        entry["weight"] = weight
    return {"rolls": 1, "entries": [entry]}


def mobs():
    """Spawn eggs, names, drops and tags for the ported mobs (their art is port_mobs.py's).
    Drops are Elysium's: Stellaris's alien zombie dropped moon fruit, which is not ported;
    each mob now drops the metal or shard of its world."""
    for mob, name in MOB_NAMES.items():
        LANG[f"entity.{NS}.{mob}"] = name
        LANG[f"item.{NS}.{mob}_spawn_egg"] = name + " Spawn Egg"
        write(os.path.join(ASSETS, "models/item", mob + "_spawn_egg.json"),
              {"parent": "minecraft:item/template_spawn_egg"})
    LANG[f"entity.{NS}.rime_spit"] = "Rime Spit"
    LANG.update({f"{NS}.gui.energy": "Energy  %s / %s FE", f"{NS}.gui.tank": "%s  %s / %s mB",
                 f"{NS}.gui.empty": "Empty"})
    tables = {
        "hollow_moonfolk": [drop(f"{NS}:rime_shard", 0, 2), drop("minecraft:rotten_flesh", 0, 1)],
        "void_crawler": [drop(f"{NS}:lunite_nugget", 1, 3), drop("minecraft:string", 0, 2)],
        "frost_raptor": [drop(f"{NS}:frostite_nugget", 0, 2), drop("minecraft:bone", 0, 2)],
        "cinder_boar": [drop("minecraft:porkchop", 2, 4), drop("minecraft:leather", 0, 1),
                        drop(f"{NS}:cindrium_nugget", 0, 2)],
        "moonfolk": [], "cinderling": [], "cinderling_brute": [],
    }
    for mob, pools in tables.items():
        write(os.path.join(DATA, "loot_table/entities", mob + ".json"),
              {"type": "minecraft:entity", "pools": pools, "random_sequence": f"{NS}:entities/{mob}"})
    tag("minecraft", "entity_type", "undead", [f"{NS}:hollow_moonfolk"])
    tag("minecraft", "entity_type", "zombies", [f"{NS}:hollow_moonfolk"])
    tag("minecraft", "entity_type", "arthropod", [f"{NS}:void_crawler"])
    tag("minecraft", "entity_type", "freeze_immune_entity_types", [f"{NS}:frost_raptor", f"{NS}:hollow_moonfolk"])
    tag("minecraft", "entity_type", "impact_projectiles", [f"{NS}:rime_spit"])


STATIONS = (("small", 100, 100), ("medium", 150, 150), ("big", 200, 200))   # kept in step with StationRules


def stations():
    """Station blueprints: art, models, names, messages and recipes. The blueprint's
    ring count grows with the station (one, two, three)."""
    paper, line, edge = (0x2E, 0x5C, 0xA8, 255), (0xD8, 0xEC, 0xFF, 255), (0x1C, 0x3A, 0x70, 255)
    for rings, (size, steel, lunite) in enumerate(STATIONS, start=1):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        px = img.load()
        for y in range(2, 14):
            for x in range(2, 14):
                px[x, y] = edge if x in (2, 13) or y in (2, 13) else paper
        for r in range(rings):                      # concentric station rings
            lo, hi = 4 + r, 11 - r
            for i in range(lo, hi + 1):
                for x, y in ((i, lo), (i, hi), (lo, i), (hi, i)):
                    if r < rings:
                        px[x, y] = line
        px[7, 7] = px[8, 8] = px[7, 8] = px[8, 7] = (0xFF, 0xD2, 0x5A, 255)   # the pad
        name = f"{size}_station_blueprint"
        img.save(os.path.join(ITEM_TEX, name + ".png"))
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
        LANG[f"item.{NS}.{name}"] = f"{size.capitalize()} Station Blueprint"
        prev = {"item": "minecraft:paper"} if rings == 1 else {"item": f"{NS}:{STATIONS[rings - 2][0]}_station_blueprint"}
        write(os.path.join(DATA, "recipe", name + ".json"), {"type": "minecraft:crafting_shaped",
              "category": "misc", "pattern": ["SLS", "LPL", "SLS"],
              "key": {"S": {"tag": "c:ingots/steel"}, "L": {"tag": "c:ingots/lunite"}, "P": prev},
              "result": {"count": 1, "id": f"{NS}:{name}"}})
    LANG.update({
        f"{NS}.station.cost": "Builds from %s steel and %s lunite ingots, in High Orbit",
        f"{NS}.station.built": "Station built. Rockets to High Orbit now land here",
        f"{NS}.station.upgraded": "Station rebuilt bigger, in the same place",
        f"{NS}.station.not_in_orbit": "Stations can only be built in High Orbit",
        f"{NS}.station.already": "You already have a station this size or bigger",
        f"{NS}.station.missing_materials": "You need %s steel and %s lunite ingots",
        f"{NS}.station.no_template": "This station's plans are missing from the game data",
    })


def lang():
    write(os.path.join(ASSETS, "lang/en_us.json"), LANG)


def main():
    if not os.path.isdir(STELLARIS):
        sys.exit(f"no Stellaris data at {STELLARIS}")
    textures()
    models()
    loot()
    recipes()
    tags()
    emgrove()
    lake = lakes()
    ores_ = ores()
    bastion_biomes(ores_, lake)
    bastion_terrain()
    damage_types()
    oil_lakes()
    materials()
    building_blocks()
    mobs()
    stations()
    lang()
    print("generated elysium-space assets and data")


if __name__ == "__main__":
    main()
