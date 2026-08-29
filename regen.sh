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
#   4. gen_trinkets.py    and the two mods with their own tables.
#      gen_mobs.py / gen_npcs.py
#
# Everything here is derived. If a file changes and you did not edit a table,
# something is wrong with a generator rather than with the file.
set -euo pipefail
cd "$(dirname "$0")"

echo "== elysium-core: data ==";           python3 elysium-core/tools/gen_data.py
echo "== elysium-core: sprites ==";        python3 art/build.py
echo "== elysium-core: material gear ==";  python3 elysium-core/tools/gen_material_gear.py
echo "== elysium-trinkets ==";             python3 elysium-trinkets/tools/gen_trinkets.py
echo "== elysium-mobs ==";                 (cd elysium-mobs && python3 tools/gen_mobs.py)
echo "== elysium-npcs ==";                 (cd elysium-npcs && python3 tools/gen_npcs.py)
echo
echo "all generated resources rebuilt"
