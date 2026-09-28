#!/usr/bin/env bash
# A hundred and five fish, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-agronomy
R=$M/src/main/java/com/elysium/agronomy/rules/FishRules.java
SNAP=/tmp/fish_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-agronomy)
restore() { rm -rf $ROOT/elysium-agronomy && (cd $ROOT && tar xf $SNAP); }
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

echo "whether a fish can be caught at all, which is the whole point"
edit $R 'public static final Family END = new Family("end", VOID, 2, ANY);' 'public static final Family END = new Family("end", VOID, 2, DAY);
    public static final Family NOWHERE = new Family("nowhere", VOID, 0, NIGHT);'
edit $R 'fish("void_ray", END, 4, 140.0F, 6),' 'fish("void_ray", NOWHERE, 4, 140.0F, 6),'
expect "a fish in water nothing else shares, too thin to be worth a rod" "thin to be worth a rod"

edit $R '        return ANY.equals(wants) || ANY.equals(where.when()) || wants.equals(where.when());' '        return wants.equals(where.when());'
expect "a time rule that leaves every any-time fish uncatchable" "never come up"

edit $R '            if (!NOTHING.equals(bait) && one.family().name().equals(bait)) {' '            if (!NOTHING.equals(bait) && !one.family().name().equals(bait)) {'
expect "bait that drives away everything but the thing it names" "pulls away from the thing it names"

echo "the rod"
edit $R 'public static final double LUCK_TILT = 0.34D;' 'public static final double LUCK_TILT = 0.0D;'
expect "a rod ladder where every rung is the same rod" "tilts nothing"

edit $R '            for (int step = COMMONEST; step < one.rarity(); step++) {
                weight *= 1.0D + LUCK_TILT * rod;
            }' '            for (int step = COMMONEST; step < one.rarity(); step++) {
                weight /= 1.0D + LUCK_TILT * rod;
            }'
expect "a better rod that is worse at being a rod" "worse at being a rod"

edit $R '        int rod = Math.max(0, Math.min(RODS - 1, luck));' '        int rod = Math.max(0, luck);'
expect "a rod past the top of the ladder that keeps getting better" "keeps getting better"

edit $R 'public static final double BAIT_PULL = 4.0D;' 'public static final double BAIT_PULL = 1.0D;'
expect "bait that pulls towards nothing" "pull towards anything"

echo "the arithmetic"
edit $R '            out.add(new Chance(candidates.get(index).id(), weights.get(index) / total));' '            out.add(new Chance(candidates.get(index).id(), weights.get(index)));'
expect "a table that is not normalised" "rather than one"

edit $R '        double weight = 64.0D;
        for (int step = COMMONEST; step < rarity; step++) {
            weight /= 2.0D;
        }' '        double weight = 64.0D;
        for (int step = COMMONEST; step < rarity; step++) {
            weight *= 2.0D;
        }'
expect "rarity that makes a fish commoner" "rarer than"

edit $R 'public static final int MOST_CASTS = 400;' 'public static final int MOST_CASTS = 4;'
expect "a fish that is a second job rather than a story" "second job"

echo "the roster"
edit $R 'fish("mackerel", OCEAN, 1, 35.0F, 3),' 'fish("mackerel", OCEAN, 1, 35.0F, 9),'
expect "a common fish that is the best meal in the game" "commoner fish is the better meal"

echo "the world the fish are actually in"
rm -f $M/src/main/resources/assets/elysiumagronomy/textures/item/mackerel_bucket.png
expect "a fish with no bucket picture" "no picture"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-agronomy/src/main/resources/assets/elysiumagronomy/lang/en_us.json")
d = json.loads(p.read_text(encoding="utf-8"))
d.pop("item.elysiumagronomy.mackerel_bucket", None)
p.write_text(json.dumps(d, indent=2) + "\n", encoding="utf-8")
PYX
expect "a bucket with no name" "show their id"

edit $M/src/main/java/com/elysium/agronomy/registry/FishEntities.java 'float across = Math.max(0.3F, Math.min(2.0F, one.length() / 100.0F));' 'float across = 0.5F;'
expect "a hitbox that stops following the fish's own length" "number somebody chose"

edit $M/src/main/java/com/elysium/agronomy/client/FishRenderer.java 'float wanted = fish.rules().length() / MODEL_CENTIMETRES;' 'float wanted = 1.0F;'
expect "a drawn size that stops following the length" "two different sizes"

edit $M/src/main/java/com/elysium/agronomy/client/FishModel.java 'public static final int SHEET = 64;' 'public static final int SHEET = 128;'
expect "a model packed to a sheet its textures are not" "the model bakes against"

edit $M/src/main/java/com/elysium/agronomy/registry/FishItems.java \
  'if (made.size() != FishRules.FISH.size()) {
            throw new IllegalStateException("the roster has " + FishRules.FISH.size()
                    + " fish and " + made.size() + " were registered");' \
  'if (false) {
            throw new IllegalStateException("the roster has " + FishRules.FISH.size()
                    + " fish and " + made.size() + " were registered");'
expect "a registry that does not count what it registered" "do not count what they built"

edit $M/src/main/java/com/elysium/agronomy/registry/FishEntities.java \
  'for (FishRules.Fish one : FishRules.FISH) {
            // Size follows' \
  'for (FishRules.Fish one : FishRules.FISH.subList(0, 4)) {
            // Size follows'
expect "a registry that stops walking the roster" "no longer walks the whole roster"

edit $R 'public static final int RODS = 5;' 'public static final int RODS = 7;'
expect "a rod ladder with rungs nobody can hold" "cannot be held"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all agronomy checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | grep -A1 PROBLEM | tail -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "fish: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
