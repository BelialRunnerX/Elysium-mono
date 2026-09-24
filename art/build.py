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


def out_lib(rel):
    """
    The library's texture tree. The hazard wards are the library's effects —
    elysiumlib:pressure_ward, not elysium:pressure_ward — and the game looks
    for an effect's icon under the effect's own namespace. Written into
    elysium-core's tree they were a set of textures nothing ever read, and every
    ward showed the missing-texture square.
    """
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    # Keyed on the library's Java source, like _locate(): out() creates
    # directories, so testing for a directory would let one bad run make a
    # wrong path permanently "exist".
    marker = os.path.join(repo, "elysium-lib/src/main/java/com/elysium/lib/ElysiumLib.java")
    if not os.path.isfile(marker):
        raise SystemExit("cannot find elysium-lib from " + repo)
    lib = os.path.join(repo, "elysium-lib/src/main/resources/assets/elysiumlib/textures")
    path = os.path.join(lib, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def main():
    # -- materials ---------------------------------------------------------
    sprites.ingot("neutronium", "inert").save(out("item/neutronium_ingot.png"))
    sprites.ingot("voidsteel", "aetherium").save(out("item/aetherium_ingot.png"))
    sprites.shard("obsidian", "voidglass").save(out("item/voidglass_ingot.png"))

    # -- convergence templates --------------------------------------------
    sprites.convergence_template("silent", "aetherium").save(
        out("item/silent_convergence_template.png"))
    sprites.convergence_template("affix", "void").save(
        out("item/affix_convergence_template.png"))

    # -- the field scanner -------------------------------------------------
    sprites.scanner("aetherium", "aetherium").save(out("item/field_scanner.png"))

    # -- the character sheet, in the hand ----------------------------------
    sprites.codex("voidsteel", "aetherium").save(out("item/imperial_codex.png"))

    # -- reforge catalyst --------------------------------------------------
    sprites.catalyst("aetherium").save(out("item/elysium_reforge.png"))

    # -- field rations: one per hazard, coloured by the element that answers it
    #
    # The colour is the hazard's own element rather than anything about the
    # food, because that is the association the whole hazard layer is built on:
    # a player who has been sorting armour by element since their first blade
    # can pick the right ration out of a hotbar without reading a word.
    for ration, glow in [("thermal_ration", "plasma"),
                         ("cryogenic_ration", "kinetic"),
                         ("corrosive_ration", "neural"),
                         ("radiological_ration", "void"),
                         ("pressure_ration", "dimensional")]:
        sprites.ration("aetherium", glow).save(out(f"item/{ration}.png"))

    # -- hazard ward effect icons, in the library's namespace ---------------
    #
    # The same element colour as the ration that grants each one, so the icon
    # matches the packet. See sprites.ward_icon.
    ward_glow = {"thermal_ward": "plasma", "cryogenic_ward": "kinetic",
                 "corrosive_ward": "neural", "radiological_ward": "void",
                 "pressure_ward": "dimensional"}
    for ward, glyph in sprites.WARD_ICONS.items():
        sprites.ward_icon(glyph, ward_glow[ward]).save(
            out_lib(f"mob_effect/{ward}.png"))

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

    # -- gems, sigils and the case (SPEC_CLONE P7) -------------------------
    #
    # Runes are Elysium's gems, so these are things that happen to a rune
    # rather than a second family of stones. Dust takes aetherium, the colour
    # of the utility runes it is usually ground from; each sigil takes the
    # colour of what it does to a piece.
    sprites.gem_dust("aetherium").save(out("item/gem_dust.png"))
    for seal, glow in [("socketing", "void"),
                       ("enhancement", "plasma"),
                       ("withdrawal", "kinetic"),
                       ("rebirth", "dimensional")]:
        sprites.sigil(seal, glow).save(out(f"item/sigil_of_{seal}.png"))
    sprites.gem_case("voidsteel", "aetherium").save(out("item/gem_case.png"))

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
    # Kinetic gold rather than any of the arcane glows: the Empire's colour is
    # the one the Praetor and the court regalia already use, and a Registry
    # Beacon is a piece of Imperial paperwork, not a piece of Elysium's magic.
    # The two rogue spawners and the cage (SPEC_CLONE P9.5, P9.6). Red for the
    # one that sends a few things worth fearing, green for the one that sends
    # many; the cage takes the void colour, because what is in it is worse.
    sprites.workstation("brutal_spawner", "obsidian", "plasma").save(
        out("block/brutal_spawner.png"))
    sprites.workstation("swarm_spawner", "obsidian", "neural").save(
        out("block/swarm_spawner.png"))
    sprites.workstation("caged_invader", "voidsteel", "void").save(
        out("block/caged_invader.png"))
    sprites.workstation("gem_cutting_table", "obsidian", "aetherium").save(
        out("block/gem_cutting_table.png"))
    sprites.workstation("augmenting_table", "voidsteel", "void").save(
        out("block/augmenting_table.png"))
    sprites.workstation("registry_beacon", "voidsteel", "kinetic").save(
        out("block/registry_beacon.png"))
    # Obsidian and the same kinetic gold: the archive is the beacon's paperwork,
    # so it shares the Empire's colour and differs in its stone.
    sprites.workstation("imperial_archive", "obsidian", "kinetic").save(
        out("block/imperial_archive.png"))
    # Neutronium and a cold dimensional blue: Imperial equipment rather than
    # Imperial authority. The gold blocks are things the Empire decides with;
    # this is a thing it measures with.
    sprites.workstation("inspection_beacon", "neutronium", "dimensional").save(
        out("block/inspection_beacon.png"))

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
