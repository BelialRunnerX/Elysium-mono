#!/usr/bin/env bash
# The crops behind the kitchen, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-agronomy
C=$M/src/main/java/com/elysium/agronomy/rules/CropRules.java
K=$M/src/main/java/com/elysium/agronomy/block/KitchenCropBlock.java
G=$M/tools/gen_kitchen.py
SNAP=/tmp/crops_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 400 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit is made."
  (cd $M && timeout 400 python3 validate.py 2>&1 | tail -5)
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-agronomy elysium-foundry)
restore() { rm -rf $ROOT/elysium-agronomy $ROOT/elysium-foundry && (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
gexpect() { (cd $M && python3 tools/gen_kitchen.py >/dev/null 2>&1); expect "$@"; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the one thing this file exists for: an ingredient from nowhere"
edit $C '            new Larder("stock", KitchenRules.POT,
                    List.of("bone", "onion", "carrot")),' ''
expect "an ingredient in the cupboard that nothing in the world puts there" "nothing in the world puts it there"

edit $C '            if (grown.contains(one) || made.contains(one)
                    || vanilla.contains(one)) {' '            if (true) {'
expect "a where-does-it-come-from check that cannot report anything" "is not reported, which is a cookbook for a cupboard nobody can fill"

edit $C '            new Larder("pasta", KitchenRules.BOARD, List.of("dough")),' '            new Larder("pasta", KitchenRules.BOARD, List.of("moonfruit")),'
expect "a larder item made out of something nothing gives" "the larder is made out of something nothing in the world gives"

echo "the frontier of time against yield"
edit $C '            new Crop("ginger", TILLED, 5, 3_600, 7, 1, "jungle"),' '            new Crop("ginger", TILLED, 5, 1_200, 7, 1, "jungle"),'
expect "a crop as good as another on every axis and better on one" "which is a reason never to plant"

edit $C '            new Crop("onion", TILLED, 4, 1_800, 1, 1, "plains"),' '            new Crop("onion", TILLED, 4, 1_800, 1, 4, "plains"),'
expect "a crop that feeds well enough raw to make cooking pointless" "a reason never to cook"

edit $C '                if (asGood && better) {' '                if (false) {'
expect "a domination check that cannot report anything" "is not reported as beating wheat"

edit $C '            if (one.nutrition() > 1) {' '            if (false) {'
expect "a raw-nutrition check that cannot report anything" "a crop that feeds six raw is not reported"

edit $C '                if (one == other || !one.ground().equals(other.ground())) {' '                if (one == other) {'
expect "a crop in shallow water compared against one in a field" "and then the rule demands that rice and barley be the same kind of thing"

echo "where the first seed comes from"
edit $C '            new Crop("garlic", TILLED, 4, 3_000, 3, 1, "forest"),' '            new Crop("garlic", TILLED, 4, 3_000, 3, 1, "plains"),'
edit $C '            new Crop("tomato", TILLED, 5, 3_000, 5, 1, "savanna"),' '            new Crop("tomato", TILLED, 5, 3_000, 5, 1, "plains"),'
edit $C '            new Crop("chili", TILLED, 6, 2_640, 6, 1, "desert"),' '            new Crop("chili", TILLED, 6, 2_640, 6, 1, "plains"),'
edit $C '            new Crop("ginger", TILLED, 5, 3_600, 7, 1, "jungle"),' '            new Crop("ginger", TILLED, 5, 3_600, 7, 1, "plains"),'
edit $C '            new Crop("rice", PADDY, 6, 2_200, 2, 1, "swamp"));' '            new Crop("rice", PADDY, 6, 2_200, 2, 1, "plains"));'
expect "every wild patch in one sort of place, so spawning decides the cookbook" "so where a player spawns decides most of what they can ever cook"

echo "the crop a player actually plants"
edit $K '        float ours = found == null ? 1.0F
                : (float) found.ticks() / CropRules.WHEAT.ticks();' '        float ours = 1.0F;'
expect "every crop growing at the game's own speed whatever the rules say" "so the frontier of time against yield that the harness proves is a table in a comment"

echo "the world the crops are actually in"
rm -f $M/src/main/resources/data/elysiumagronomy/neoforge/biome_modifier/wild_onion.json
expect "a wild patch nothing puts in a biome" "so it is generated nowhere at all"

rm -f $M/src/main/resources/data/elysiumagronomy/loot_table/blocks/cabbage_crop.json
expect "a crop that drops nothing when it is broken" "so a field of it is a field that cannot be harvested"

rm -f $M/src/main/resources/data/elysiumagronomy/larder/bacon.json
expect "a larder item with no recipe to put it on the shelf" "there is no recipe that puts it there"

rm -f $M/src/main/resources/assets/elysiumagronomy/models/block/chili_crop_stage3.json
expect "a crop with no model for one of its ages" "has no model for age"

edit $G '        write(os.path.join(DATA, "loot_table/blocks", crop["id"] + "_crop.json"), {
            "type": "minecraft:block",
            "pools": [
                {"rolls": 1, "entries": [{"type": "minecraft:item",
                                          "name": f"{NS}:{crop['"'"'id'"'"']}_seeds"}]},' '        write(os.path.join(DATA, "loot_table/blocks", crop["id"] + "_crop.json"), {
            "type": "minecraft:block",
            "pools": [
                {"rolls": 1, "entries": [{"type": "minecraft:item",
                                          "name": "minecraft:stick"}]},'
gexpect "a crop that eats its own seed when it is broken early" "so a crop broken early is a crop nobody plants twice"

edit $G '            "biomes": WILD_IN.get(crop["biomes"], "#minecraft:is_overworld"),' '            "biomes": "minecraft:plains",'
gexpect "a wild patch added to one biome rather than to a tag of them" "so a pack that adds a biome of that sort gets no patch"

edit $G '              {"variants": {
                  f"age={stage}": {
                      "model": f"{NS}:block/{crop['"'"'id'"'"']}_crop_stage{stage}"}
                  for stage in range(8)}})' '              {"variants": {
                  f"age={stage}": {
                      "model": f"{NS}:block/{crop['"'"'id'"'"']}_crop_stage{stage}"}
                  for stage in range(4)}})'
gexpect "a blockstate with fewer ages than the game's crop property has" "the ages with no model are the ones a creative player bone-meals it to"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all agronomy checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | tail -3 | head -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "crops: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
