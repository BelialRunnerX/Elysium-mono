#!/usr/bin/env bash
# Seventy-five bees, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-agronomy
R=$M/src/main/java/com/elysium/agronomy/rules/BeeRules.java
SNAP=/tmp/bees_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-agronomy elysium-foundry)
restore() { rm -rf $ROOT/elysium-agronomy $ROOT/elysium-foundry && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the four hundred and twenty-eight, which is what this module is about"
edit $R 'resource("osmium", "nickel", "silver"),' 'resource("actinium", "nickel", "silver"),'
expect "a bee for a metal nothing in the pack makes" "can never be obtained"

edit $R 'resource("osmium", "nickel", "silver"),' ''
expect "a material the foundry makes losing its bee" "have no bee"

echo "the roster"
edit $R 'resource("tin", "iron", "stone"),' 'resource("tin", "iron", "notabee"),'
expect "a bee bred from something that is not a bee" "not a bee"

edit $R 'resource("steel", "iron", "coal_dust"),' 'resource("steel", "iron", "stainless"),'
expect "a breeding cycle" "own ancestor"

python3 - <<'PYU'
import pathlib, sys
p = pathlib.Path("/tmp/Elysium-mono/elysium-agronomy/src/main/java/com/elysium/agronomy/rules/BeeRules.java")
s = p.read_text(encoding="utf-8")
a = 'product("sugar", "sugar", "meadow", "marsh"),'
b = 'product("clay", "clay_lump", "water", "marsh"),'
if a not in s or b not in s:
    sys.stderr.write("STALE ANCHOR in BeeRules :: sugar/clay\\n"); sys.exit(2)
s = s.replace(a, 'product("sugar", "sugar", "clay", "marsh"),', 1)
s = s.replace(b, 'product("clay", "clay_lump", "sugar", "marsh"),', 1)
p.write_text(s, encoding="utf-8")
PYU
expect "two bees that reach each other and nothing else" "registry and nowhere else"

edit $R 'product("sugar", "sugar", "meadow", "marsh"),' 'product("sugar", "honey", "meadow", "marsh"),'
expect "two bees making the same thing" "both produce"

edit $R 'place("dune", "desert", "sandgrain", "meadow", "water"),' 'place("dune", "desert", "sandgrain", "stone", "meadow"),'
expect "one pair of parents breeding two different bees" "can never be got on purpose"

edit $R 'resource("zinc", "copper", "stone"),' 'resource("zinc", "copper", "copper"),'
expect "a bee bred from two of the same bee" "not breeding"

edit $R 'place("canopy", "jungle", "sap", "forest", "marsh"),' 'place("canopy", "", "sap", "forest", "marsh"),'
expect "a place bee with nowhere to live" "nowhere to live"

echo "the economy"
edit $R 'public static final int FIRST_RESOURCE_TICKS = ApiaryRules.ticksForTheCeiling();' 'public static final int FIRST_RESOURCE_TICKS = 1_400;'
expect "a comb time chosen rather than solved against the ceiling" "BeeRules no longer takes its first resource comb from the ceiling"

edit $R 'public static final float HIVE_AT_MOST = 0.25F;' 'public static final float HIVE_AT_MOST = 0.9F;'
expect "a hive allowed as large a share of a mine as a farm" "runs itself while a farm does not"

echo "the world the bees are actually in"
rm -f $M/src/main/resources/assets/elysiumagronomy/models/item/iron_comb.json
expect "a comb with no model" "magenta cube"

rm -f $M/src/main/resources/assets/elysiumagronomy/textures/entity/bee/iron.png
expect "a bee with no texture" "magenta cube"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-agronomy/src/main/resources/assets/elysiumagronomy/lang/en_us.json")
d = json.loads(p.read_text(encoding="utf-8"))
d.pop("entity.elysiumagronomy.iron_bee", None)
p.write_text(json.dumps(d, indent=2) + "\n", encoding="utf-8")
PYX
expect "a bee with no name" "would show their id"

python3 - <<'PYX'
from PIL import Image
Image.new("RGBA", (16, 16)).save(
    "/tmp/Elysium-mono/elysium-agronomy/src/main/resources/assets/elysiumagronomy/textures/entity/bee/iron.png")
PYX
expect "an entity texture that is not the size the model bakes against" "32 by 32"

echo "the machinery that keeps the two halves together"
edit $M/src/main/java/com/elysium/agronomy/registry/BeeEntities.java \
  'if (made.size() != BeeRules.BEES.size()) {
            throw new IllegalStateException("the roster has " + BeeRules.BEES.size()' \
  'if (false) {
            throw new IllegalStateException("the roster has " + BeeRules.BEES.size()'
expect "a registry that does not count what it registered" "do not count what they built"

edit $M/src/main/java/com/elysium/agronomy/registry/BeeEntities.java \
  'for (BeeRules.Bee bee : BeeRules.BEES) {
            made.put(bee.id(), TYPES.register(bee.entity(),' \
  'for (BeeRules.Bee bee : BeeRules.BEES.subList(0, 3)) {
            made.put(bee.id(), TYPES.register(bee.entity(),'
expect "a registry that stops walking the roster" "no longer walks the whole roster"

edit $M/src/main/java/com/elysium/agronomy/client/BeeModel.java 'texOffs(0, 18)' 'texOffs(0, 24)'
expect "a model whose texture offsets no longer match the texture" "generator draws into"

edit $M/src/main/java/com/elysium/agronomy/client/BeeModel.java 'LayerDefinition.create(mesh, 32, 32)' 'LayerDefinition.create(mesh, 64, 64)'
expect "a model that changes its texture size without the generator" "mean something different"

edit $M/src/main/java/com/elysium/agronomy/registry/BeeItems.java 'hash = hash * 31 + id.charAt(index);' 'hash = hash * 33 + id.charAt(index);'
expect "the two colour functions drifting apart" "same colour"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-agronomy/src/main/resources/assets/elysiumagronomy/models/item/iron_comb.json")
p.write_text(json.dumps({"parent": "minecraft:item/handheld"}, indent=2) + "\n", encoding="utf-8")
PYX
expect "a generated file edited by hand" "edited by hand"

edit $M/tools/gen_bees.py 'def java_hash(text):' 'def not_the_java_hash(text):'
expect "a generator that stops computing the game's own hash" "same colour"

edit $M/src/main/java/com/elysium/agronomy/registry/BeeBlocks.java \
  'if (made.size() != BeeRules.homes().size()) {' 'if (false) {'
expect "a nest registry that does not count the places bees live" "do not count what they built"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all agronomy checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | tail -3 | head -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "bees: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
