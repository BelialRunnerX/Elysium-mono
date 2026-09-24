#!/usr/bin/env bash
# Two hundred and ten geodes, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-arcana
R=$M/src/main/java/com/elysium/arcana/rules/GeodeRules.java
SNAP=/tmp/geodes_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-arcana elysium-foundry)
restore() { rm -rf $ROOT/elysium-arcana $ROOT/elysium-foundry && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 500 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | grep -A1 PROBLEM | tail -1 | cut -c1-140)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the twenty-nine materials that are not here"
edit $R '            "rime_shard", "uraninite");' '            "rime_shard", "uraninite", "allthemodium");'
expect "a geode for a metal nothing in the pack mines" "nobody can reach"

edit $R '            "aluminum", "osmium", "platinum", "lithium", "crimson_iron",' '            "aluminum", "osmium", "platinum", "lithium", "steel",'
expect "a geode for something a machine makes" "a machine makes rather than one the ground"

edit $R '            "emerald", "ancient_debris", "glowstone", "obsidian");' '            "emerald", "ancient_debris", "glowstone", "iron");'
expect "a material listed as both mined and the game's own" "both mined here and the game"

echo "the growth chain"
edit $R '    public static final List<String> CHAIN = List.of(SMALL, MEDIUM, LARGE, CLUSTER);' '    public static final List<String> CHAIN = List.of(SMALL, LARGE, CLUSTER);'
expect "a chain with a step taken out of it" "will not run"

edit $R '            List.of(BLOCK, BUDDING, SMALL, MEDIUM, LARGE, CLUSTER, GLASS);' '            List.of(BLOCK, SMALL, MEDIUM, LARGE, CLUSTER, GLASS);'
expect "the budding block dropped from the catalogue" "growth chain with a hole"

echo "the seam with the world"
edit $M/src/main/java/com/elysium/arcana/registry/GeodeBlocks.java \
  'for (String material : GeodeRules.materials()) {' 'for (String material : GeodeRules.materials().subList(0, 4)) {'
expect "a registry that stops walking the material list" "no longer walks the material list"

edit $M/src/main/java/com/elysium/arcana/registry/GeodeBlocks.java \
  '() -> new BuddingGeodeBlock(solid(), chain)' '() -> new net.minecraft.world.level.block.Block(solid())'
expect "budding blocks that are not built with a chain" "stays a bare wall"

edit $M/src/main/java/com/elysium/arcana/registry/GeodeItems.java \
  'if (made.size() != GeodeRules.items().size()) {' 'if (false) {'
expect "an item registry that does not count what it built" "does not count what it registered"

echo "the world the geodes are actually in"
rm -f $M/src/main/resources/assets/elysiumarcana/textures/block/budding_iron.png
expect "a budding block with no texture" "a geode needs are not there"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-arcana/src/main/resources/assets/elysiumarcana/blockstates/iron_cluster.json")
d = json.loads(p.read_text(encoding="utf-8"))
p.write_text(json.dumps({"variants": {"facing=up": d["variants"]["facing=up"]}}, indent=2) + "\n", encoding="utf-8")
PYX
expect "a crystal that draws nothing when it grows sideways" "facing"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-arcana/src/main/resources/assets/elysiumarcana/lang/en_us.json")
d = json.loads(p.read_text(encoding="utf-8"))
d.pop("item.elysiumarcana.uranium_spyglass", None)
p.write_text(json.dumps(d, indent=2) + "\n", encoding="utf-8")
PYX
expect "a spyglass with no name" "a geode needs are not there"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all arcana checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | grep -A1 PROBLEM | tail -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "geodes: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
