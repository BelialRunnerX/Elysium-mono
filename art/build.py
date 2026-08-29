#!/usr/bin/env python3
"""Emit the full Voidforged texture set into the mod's resource tree."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image

import sprites
import layers
from style import METAL, GLOW

# The live tree. This pointed at work/ - a scratch checkout that stopped being
# the shipped one - so running it wrote textures nobody would ever see and the
# real ones drifted. Same footgun gen_data.py had; same fix.
def _locate():
    """
    The live texture tree, under either repo layout - see gen_data.py.

    Keyed on the module's *Java source*, not on whether a textures directory
    happens to be there. The previous version asked whether
    `<module>/…/assets/elysium` existed and took the first hit — and out()
    creates that directory with makedirs, so a single run against the wrong
    path made the wrong path permanently correct.

    In the monorepo that is exactly what happened. An orphan `core/` sat beside
    `elysium-core/` holding nothing but 54 textures: not in settings.gradle,
    not built, not shipped, and quietly collecting every named sprite on every
    regen while the ones in the real module went stale.

    Only the real module has ElysiumMaterials.java, and no generator ever
    writes a .java file, so this test cannot bootstrap itself into being wrong.
    elysium-core is tried first for the same reason.
    """
    here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    for module in ("elysium-core", "core"):
        marker = os.path.join(here, module, "src/main/java/com/elysium/core/"
                                            "item/ElysiumMaterials.java")
        if os.path.isfile(marker):
            return os.path.join(here, module,
                                "src/main/resources/assets/elysium/textures")
    raise SystemExit("cannot find elysium-core's texture tree from " + here)


ROOT = _locate()


def out(rel):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def main():
    # -- materials ---------------------------------------------------------
    sprites.ingot("neutronium", "inert").save(out("item/neutronium_ingot.png"))
    sprites.ingot("voidsteel", "aetherium").save(out("item/aetherium_ingot.png"))
    sprites.shard("obsidian", "voidglass").save(out("item/voidglass_ingot.png"))

    # -- the character sheet, in the hand ----------------------------------
    sprites.codex("voidsteel", "aetherium").save(out("item/imperial_codex.png"))

    # -- reforge catalyst --------------------------------------------------
    sprites.catalyst("aetherium").save(out("item/elysium_reforge.png"))

    # -- runes -------------------------------------------------------------
    for sigil, glow in [("voidward", "void"),
                        ("plasmaforge", "plasma"),
                        ("neuralspike", "neural"),
                        ("dimensionalshift", "dimensional"),
                        ("kineticsurge", "kinetic"),
                        # Utility runes from the equipment archive. They get
                        # aetherium rather than an element colour, so a glance
                        # at the inventory separates the two families.
                        ("stabilizer", "aetherium"),
                        ("reflex", "aetherium"),
                        ("barrier", "aetherium"),
                        ("plasma_core", "plasma")]:
        sprites.rune(sigil, glow).save(out(f"item/{sigil}_rune.png"))

    # -- weapons -----------------------------------------------------------
    sprites.sword("voidsteel", "void").save(out("item/voidcut_blade.png"))
    sprites.sword("voidsteel", "plasma").save(out("item/plasma_brand.png"))
    sprites.lash("voidsteel", "neural").save(out("item/neural_lash.png"))
    sprites.sword("voidsteel", "dimensional").save(out("item/rift_edge.png"))
    sprites.maul("neutronium", "kinetic").save(out("item/kinetic_maul.png"))
    sprites.lance("neutronium", "dimensional").save(out("item/singularity_lance.png"))
    sprites.rifle("neutronium", "neural").save(out("item/neural_cascade_rifle.png"))

    # -- area tools: four shapes x three materials -------------------------
    for material, (metal, glow) in sprites.TOOL_MATERIALS.items():
        for shape, draw_tool in sprites.TOOL_SHAPES.items():
            draw_tool(metal, glow).save(out(f"item/{material}_{shape}.png"))

    # -- Elysium armour: one element per piece -----------------------------
    sprites.helmet("voidsteel", "void").save(out("item/elysium_helmet.png"))
    sprites.chestplate("voidsteel", "plasma").save(out("item/plasma_chestplate.png"))
    sprites.leggings("voidsteel", "neural").save(out("item/neural_leggings.png"))
    sprites.boots("voidsteel", "dimensional").save(out("item/dimensional_boots.png"))
    sprites.crown("voidsteel", "void").save(out("item/emperor_crown.png"))
    sprites.aegis("voidsteel", "void").save(out("item/voidweave_aegis.png"))

    # -- Neutronium armour: inert, no elemental colour ---------------------
    sprites.helmet("neutronium", "inert").save(out("item/neutronium_helmet.png"))
    sprites.chestplate("neutronium", "inert").save(out("item/neutronium_chestplate.png"))
    sprites.leggings("neutronium", "inert").save(out("item/neutronium_leggings.png"))
    sprites.boots("neutronium", "inert").save(out("item/neutronium_boots.png"))

    # -- blocks ------------------------------------------------------------
    sprites.ore("inert", 3).save(out("block/neutronium_ore.png"))
    sprites.ore("aetherium", 17).save(out("block/aetherium_ore.png"))
    sprites.ore("voidglass", 29).save(out("block/voidglass_ore.png"))
    sprites.storage_block("neutronium", "inert").save(out("block/neutronium_block.png"))
    sprites.workstation("reforge_table", "voidsteel", "aetherium").save(
        out("block/reforge_table.png"))
    sprites.workstation("rune_socket_table", "obsidian", "voidglass").save(
        out("block/rune_socket_table.png"))
    sprites.workstation("ascension_forge", "voidsteel", "plasma").save(
        out("block/ascension_forge.png"))

    # -- armour layers -----------------------------------------------------
    layers.layer_one("voidsteel", "void").save(out("models/armor/elysium_layer_1.png"))
    layers.layer_two("voidsteel", "void").save(out("models/armor/elysium_layer_2.png"))
    layers.layer_one("neutronium", "inert").save(out("models/armor/neutronium_layer_1.png"))
    layers.layer_two("neutronium", "inert").save(out("models/armor/neutronium_layer_2.png"))

    # No GUI texture. The screens are drawn in code by ElysiumUI - a texture is
    # authored at one size and then scaled by the player's GUI scale, so at
    # scale 3 its one-pixel border is a three-pixel stripe, which is most of
    # what made the old screen look unlike the game's own. This used to emit
    # textures/gui/reforge_table.png, which nothing has referenced since that
    # change and which would have shipped in the jar as dead weight.

    print("voidforged texture set written")


if __name__ == "__main__":
    main()
