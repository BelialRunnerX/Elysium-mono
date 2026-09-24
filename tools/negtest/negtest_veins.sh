#!/usr/bin/env bash
# Seven generators, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-agronomy
R=$M/src/main/java/com/elysium/agronomy/rules/AgronomyRules.java
SNAP=/tmp/veins_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-agronomy)
restore() { rm -rf $ROOT/elysium-agronomy && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 600 python3 validate.py 2>&1); }
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

echo "the reason a generator is not a mine"
edit $R 'new Vein("netherite_scrap", 2, 0.02F));' 'new Vein("netherite_scrap", 2, 0.90F));'
expect "a netherite generator worth most of a mine" "more of a mine than the line allows"

edit $R 'public static final float VEIN_AT_MOST = 0.35F;' 'public static final float VEIN_AT_MOST = 1.0F;'
expect "a line so generous nothing can cross it" "of a mine"

edit $R 'new Vein("diamond", 2, 0.06F),' 'new Vein("diamond", 2, 0.25F),'
expect "diamond coming out faster than gold" "slower as the material gets better"

edit $R 'new Vein("diamond", 2, 0.06F),' 'new Vein("diamond", 1, 0.06F),'
expect "a dearer material needing a cheaper generator" "ladder runs backwards"

edit $R 'new Vein("stone", 0, 1.00F),' 'new Vein("stone", 2, 1.00F),'
expect "the free generator gated behind power" "gated behind power"

echo "the seam with the world"
edit $M/src/main/java/com/elysium/agronomy/registry/AgronomyBlocks.java \
  'for (com.elysium.agronomy.rules.AgronomyRules.Vein vein
                : com.elysium.agronomy.rules.AgronomyRules.VEINS) {' \
  'for (com.elysium.agronomy.rules.AgronomyRules.Vein vein
                : com.elysium.agronomy.rules.AgronomyRules.VEINS.subList(0, 3)) {'
expect "a registry that stops walking the vein table" "no longer walks the whole vein table"

edit $M/src/main/java/com/elysium/agronomy/registry/AgronomyItems.java \
  'if (made.size() != com.elysium.agronomy.rules.AgronomyRules.VEINS.size()) {' \
  'if (false) {'
expect "a vein item registry that does not count what it built" "does not count what it built"

rm -f $M/src/main/resources/data/elysiumagronomy/recipe/emerald_generator.json
expect "a generator that cannot be made" "a generator needs are not there"

rm -f $M/src/main/resources/assets/elysiumagronomy/textures/block/gold_generator_side.png
expect "a generator with no texture" "a generator needs are not there"

edit $M/src/main/java/com/elysium/agronomy/block/RockGeneratorBlock.java \
  '"gold", Items.RAW_GOLD,' ''
expect "a vein with nothing said about what comes out of it" "nothing said about what comes out"

edit $M/src/main/java/com/elysium/agronomy/registry/AgronomyBlocks.java \
  'new RockGeneratorBlock(steel(), vein)' 'new RockGeneratorBlock(steel(), vein.tier())'
expect "seven generators that are the same block with different sides" "different sides"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all agronomy checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | grep -A1 PROBLEM | tail -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "veins: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
