#!/usr/bin/env bash
# The material chain, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-foundry
S=$M/src/main/java/com/elysium/foundry
R=$M/src/main/resources
F=$S/rules/FoundryRules.java
G=$M/tools/gen_foundry.py
SNAP=/tmp/foundry_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-foundry)
restore() { rm -rf $ROOT/elysium-foundry && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 300 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }
regen() { (cd $M && ELYSIUM_FOUNDRY_OUT=$M python3 tools/gen_foundry.py >/dev/null) \
  || { echo "GENERATOR BROKE"; exit 2; }; }

echo "cycles, which are duplication with a long fuse"
edit $F '            mined("tin", 0),' '            alloy("tin", 0, "bronze", UNIT),'
expect "a metal made out of the alloy it goes into" "made out of themselves"

edit $F '            if (!seen.add(here)) {
                continue;
            }' '            if (false) {
                continue;
            }'
expect "a chain walk that keeps no memory" "has no memory"

edit $F '            if (needs(material.name(), material.name())) {' '            if (false) {'
expect "a cycle check that asks nothing" "does not ask each material whether it needs itself"

echo "matter out of nothing"
edit $F '            alloy("bronze", 1, "copper", 3 * UNIT, "tin", UNIT),' '            alloy("bronze", 1, "copper", 1, "tin", 1),'
expect "an alloy that costs less than what goes into it" "creates matter"

edit $F '            alloy("stainless", 2, "steel", 3 * UNIT, "nickel", UNIT),' '            alloy("stainless", 2, "steel", 40 * UNIT, "nickel", 40 * UNIT),'
expect "an alloy so dear nobody will ever make it" "unreachable"

edit $F '        if (material == null || material.mined()) {
            return UNIT;
        }' '        if (material == null) {
            return UNIT;
        }'
expect "a deep cost that does not bottom out at the ground" "moving number"

edit $F '        return AtomicRules.ENRICHER_MULTIPLIER * UNIT;' '        return 4 * UNIT;'
expect "a fourth age that extends the ore ladder" "own ore multiplier rather than Age III"

echo "the tiers"
edit $F '            alloy("stainless", 2, "steel", 3 * UNIT, "nickel", UNIT),' '            alloy("stainless", 1, "steel", 3 * UNIT, "nickel", UNIT),'
expect "an alloy made of something on its own rung" "chain runs sideways or backwards"

edit $F '        return made != null && made.tier() <= Math.max(0, tier);' '        return made != null;'
expect "a casing made of anything at all" "can be skipped"

edit $F '            new Casing(1, "steel",' '            new Casing(1, "starsteel",'
expect "a first casing made of the last metal in the chain" "skips the rung under it"

edit $F '    public static final int UNIT = 9;' '    public static final int UNIT = 10;'
expect "a unit that is not nine" "is not nine"

echo "the two halves of the chain"
edit $G '    "invar": (1, {"iron": 2 * UNIT, "nickel": UNIT}, (178, 178, 164)),' '    "invar": (1, {"iron": 3 * UNIT, "nickel": UNIT}, (178, 178, 164)),'
regen
expect "a metal priced differently in the Java and the data" "depends on which file you read"

edit $G '    "stainless": (2, {"steel": 3 * UNIT, "nickel": UNIT}, (196, 202, 208)),' '    "stainless": (3, {"steel": 3 * UNIT, "nickel": UNIT}, (196, 202, 208)),'
regen
expect "a tier that differs between the Java and the generator" "in the rules and"

edit $G 'CASINGS = {1: "steel", 2: "invar", 3: "stainless", 4: "starsteel"}' 'CASINGS = {1: "steel", 2: "invar", 3: "stainless", 4: "void_steel"}'
regen
expect "casings that differ between the rules and the generator" "the casings differ"

edit $G 'SOMEBODY_ELSE = {"iron", "copper", "gold", "steel", "uranium", "redstone_dust"}' 'SOMEBODY_ELSE = {"iron", "copper", "gold", "uranium", "redstone_dust"}'
regen
expect "a second copy of somebody else's metal" "registers a second copy"

echo "the debt"
rm $R/data/c/tags/item/ingots/tin.json $R/data/c/tags/item/raw_materials/tin.json
expect "an ore family the pack generates and nothing supplies" "drop nothing once the mods"

rm -rf $R/data/c/tags/item/gems
expect "gems with no tag to be dropped through" "drop nothing once the mods"

edit $R/assets/elysiumfoundry/lang/en_us.json '"Tin Ingot"' '"Tin Ingot "'
expect "a generated file quietly edited by hand" "edited by hand"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all foundry checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "foundry: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
