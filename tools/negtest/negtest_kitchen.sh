#!/usr/bin/env bash
# A kitchen, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-agronomy
R=$M/src/main/java/com/elysium/agronomy/rules/KitchenRules.java
SNAP=/tmp/kitchen_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-agronomy)
restore() { rm -rf $ROOT/elysium-agronomy && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 700 python3 validate.py 2>&1); }
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

echo "whether more than three meals are worth making"
edit $R '                    8, 0.7F, 420, 4, POT, "resistance"),' '                    8, 0.7F, 400, 4, POT, "comfort"),'
expect "two meals that are the same meal under two names" "same meal under two names"

edit $R '        public boolean dominates(Meal other) {
            if (!station.equals(other.station()) || !effect.equals(other.effect())) {
                return false;
            }' '        public boolean dominates(Meal other) {
            if (true) {
                return false;
            }'
expect "a domination check that can never fire" "answering no to everything"

edit $R '            boolean better = nutrition > other.nutrition()' '            boolean better = nutrition >= other.nutrition()'
expect "a meal that makes itself pointless" "makes itself pointless"

echo "free food, and food nobody cooks"
edit $R '                    8, 0.7F, 400, 4, POT, "comfort"),' '                    40, 0.7F, 400, 4, POT, "comfort"),'
expect "a meal worth far more than what went into it" "hunger stops existing"

edit $R '                    11, 0.8F, 700, 4, POT, "fire_resistance"),' '                    1, 0.8F, 700, 4, POT, "fire_resistance"),'
expect "a meal worth less than half its parts raw" "nobody cooks it"

echo "the seam with the game"
edit $R '                    9, 0.7F, 280, 1, SKILLET, "haste"),' '                    9, 0.7F, 280, 1, SKILLET, "levitation"),'
expect "a meal naming an effect the game has nothing behind" "silently do nothing"

edit $M/src/main/java/com/elysium/agronomy/registry/KitchenItems.java \
  'Map.entry("night_vision", MobEffects.NIGHT_VISION));' 'Map.entry("night_vision", MobEffects.NIGHT_VISION),
            Map.entry("levitation", MobEffects.LEVITATION));'
expect "an effect mapped to the game that no meal names" "no meal ever names"

edit $R '            new Ingredient("ginger", 1, 0.2F),' '            new Ingredient("saffron", 1, 0.2F),'
expect "an ingredient nothing uses, and a meal wanting one nothing makes" "neither this module nor the game makes"

edit $M/src/main/java/com/elysium/agronomy/registry/KitchenItems.java \
  'if (made.size() != KitchenRules.MEALS.size()) {' 'if (false) {'
expect "a kitchen registry that does not count what it built" "do not count what they built"

edit $M/src/main/java/com/elysium/agronomy/registry/KitchenBlocks.java \
  'KitchenRules.OVEN, "stone_oven");' '"nowhere", "stone_oven");'
expect "a station with no block to cook on" "no block is registered"

echo "the data a pack would add a meal with"
python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-agronomy/src/main/resources/data/elysiumagronomy/cooking/curry.json")
d = json.loads(p.read_text(encoding="utf-8"))
d["ticks"] = 12
p.write_text(json.dumps(d, indent=2) + "\n", encoding="utf-8")
PYX
expect "a meal's data file disagreeing with the rules" "something different from the rules"

python3 - <<'PYX'
import pathlib
pathlib.Path("/tmp/Elysium-mono/elysium-agronomy/src/main/resources/data/elysiumagronomy/cooking/bread_loaf.json").unlink()
PYX
expect "a meal with no data file" "disagree"

rm -f $M/src/main/resources/data/elysiumagronomy/cutting/milk_bucket.json
expect "a cut nothing can perform" "kitchen needs are not there"

edit $R '        public boolean dominates(Meal other) {
            if (!station.equals(other.station()) || !effect.equals(other.effect())) {
                return false;
            }' '        public boolean dominates(Meal other) {
            if (true) {
                return true;
            }'
expect "a domination check that answers yes to everything" "answering yes to everything"

edit $R '            if (!station.equals(other.station()) || !effect.equals(other.effect())) {' '            if (!effect.equals(other.effect())) {'
expect "a soup that can make a sandwich pointless" "different stations"

edit $R '            if (!station.equals(other.station()) || !effect.equals(other.effect())) {' '            if (!station.equals(other.station())) {'
expect "a meal made pointless for being worse at the one thing it is not for" "different things"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all agronomy checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | grep -A1 PROBLEM | tail -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "kitchen: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
