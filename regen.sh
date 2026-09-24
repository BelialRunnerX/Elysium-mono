#!/usr/bin/env bash
#
# Regenerate every derived resource, in the one order that works.
#
# Four generators write into the same trees and two of them clear directories
# the others fill, so the order is not a preference. It is written here rather
# than in a comment because a comment does not stop anybody running them the
# other way round and losing 196 files:
#
#   1. gen_data.py        clears data/ and assets/{models,blockstates,lang},
#                         then writes the hand-authored blocks, items, recipes,
#                         loot and the Silent Gear materials.
#   2. gen_material_gear.py  refills the 196 generated gear items - models,
#                         recipes, lang and sprites - skipping anything that
#                         already exists, so step 1's hand-written recipes win.
#   3. art/build.py       the named sprites and blocks, which step 2 then
#                         declines to overwrite.
#   4. gen_skilltree.py   the Pufferfish's Skills category, which adds to the
#                         language file rather than owning it and so has to
#                         run after anything that rewrites it.
#   5. gen_trinkets.py    and the two mods with their own tables.
#      gen_mobs.py / gen_npcs.py
#
# Everything here is derived. If a file changes and you did not edit a table,
# something is wrong with a generator rather than with the file.
set -euo pipefail
cd "$(dirname "$0")"

echo "== elysium-core: humanoid skins ==";  python3 elysium-core/tools/gen_humanoids.py
echo "== elysium-lib: gui ==";              python3 elysium-lib/tools/gen_gui.py
echo "== elysium-lib: hud ==";              python3 elysium-lib/tools/gen_hud.py
echo "== elysium-core: data ==";           python3 elysium-core/tools/gen_data.py
echo "== elysium-core: sprites ==";        python3 art/build.py
echo "== elysium-core: material gear ==";  python3 elysium-core/tools/gen_material_gear.py
# After gen_data.py, which clears data/, and after gen_material_gear.py, which
# rewrites the language file wholesale. The skill tree adds to that file rather
# than owning it, so anything that rewrites it has to have finished first.
echo "== elysium-core: skill tree ==";     python3 elysium-core/tools/gen_skilltree.py
echo "== elysium-trinkets ==";             python3 elysium-trinkets/tools/gen_trinkets.py
echo "== elysium-solarpunk ==";            python3 elysium-solarpunk/tools/gen_solarpunk.py
echo "== elysium-mobs ==";                 (cd elysium-mobs && python3 tools/gen_mobs.py)
echo "== elysium-npcs ==";                 (cd elysium-npcs && python3 tools/gen_npcs.py)
echo "== elysium-npcs: village ==";       python3 elysium-npcs/tools/gen_village.py
echo "== elysium-enchanting ==";          python3 elysium-enchanting/tools/gen_enchanting.py
echo "== elysium-spawners ==";            python3 elysium-spawners/tools/gen_spawners.py
echo "== elysium-enchanting: tower =="; (cd elysium-enchanting && python3 tools/gen_tower.py)
# Reads elysium-space's own world declarations, so it runs after nothing in
# particular but must run whenever those change.
echo "== elysium-colonies: charters =="; (cd elysium-colonies && python3 tools/gen_charters.py)
# Re-themes MineColonies's own packs by substituting the palette. Needs the
# MineColonies jar in /tmp/mods; skipped with a warning when it is not there.
echo "== elysium-space: waygate ==";     (cd elysium-space && python3 tools/gen_waygate.py)
echo "== elysium-colonies: styles ==";  (cd elysium-colonies && python3 tools/gen_styles.py || echo "  (styles skipped: no MineColonies jar)")
# The ore generator writes a Java file (data/OreCatalogue.java) as well as data,
# and the texture generator reads that file back. So they run in this order and
# the textures never run alone.
echo "== elysium-ores ==";               (cd elysium-ores && python3 tools/gen_ores.py)
echo "== elysium-ores: textures ==";     (cd elysium-ores && python3 tools/gen_textures.py)
echo "== elysium-flora ==";               (cd elysium-flora && python3 tools/gen_flora.py)
echo "== elysium-flora: textures ==";     (cd elysium-flora && python3 tools/gen_textures.py)
# The ages. Each one's generator writes every asset and data file in its module
# and nothing outside it, and each module's validator regenerates the whole set
# into a temporary tree and compares, so a file edited by hand fails the build
# rather than being silently undone the next time this script runs.
echo "== elysium-kinetics ==";           (cd elysium-kinetics && python3 tools/gen_kinetics.py)
echo "== elysium-industry ==";           (cd elysium-industry && python3 tools/gen_industry.py)
echo "== elysium-atomics ==";            (cd elysium-atomics && python3 tools/gen_atomics.py)
echo "== elysium-logistics ==";          (cd elysium-logistics && python3 tools/gen_logistics.py)
echo "== elysium-agronomy ==";           (cd elysium-agronomy && python3 tools/gen_agronomy.py)
echo "== elysium-agronomy ==";           (cd elysium-agronomy && python3 tools/gen_bees.py)
echo "== elysium-agronomy ==";           (cd elysium-agronomy && python3 tools/gen_fish.py)
echo "== elysium-agronomy ==";           (cd elysium-agronomy && python3 tools/gen_pots.py)
echo "== elysium-agronomy ==";           (cd elysium-agronomy && python3 tools/gen_veins.py)
echo "== elysium-agronomy ==";           (cd elysium-agronomy && python3 tools/gen_kitchen.py)
echo "== elysium-foundry ==";            (cd elysium-foundry && python3 tools/gen_foundry.py)
echo "== elysium-almanac ==";            (cd elysium-almanac && python3 tools/gen_almanac.py)
echo "== elysium-transit ==";            (cd elysium-transit && python3 tools/gen_transit.py)
echo "== elysium-arcana ==";             (cd elysium-arcana && python3 tools/gen_arcana.py)
echo "== elysium-arcana ==";             (cd elysium-arcana && python3 tools/gen_geodes.py)
echo "== elysium-arcana ==";             (cd elysium-arcana && python3 tools/gen_relics.py)
echo "== elysium-comforts ==";           (cd elysium-comforts && python3 tools/gen_comforts.py)
echo "== elysium-atelier ==";            (cd elysium-atelier && python3 tools/gen_atelier.py)
echo "== elysium-arsenal ==";            (cd elysium-arsenal && python3 tools/gen_arsenal.py)
echo "== elysium-bestiary ==";           (cd elysium-bestiary && python3 tools/gen_bestiary.py)
echo "== elysium-wilds ==";              (cd elysium-wilds && python3 tools/gen_wilds.py)
echo
echo "all generated resources rebuilt"
