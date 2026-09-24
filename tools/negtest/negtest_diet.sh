#!/usr/bin/env bash
# A varied diet, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-agronomy
E=$M/src/main/java/com/elysium/agronomy/event/DietEvents.java
A=$M/src/main/java/com/elysium/agronomy/registry/DietAttachments.java
SNAP=/tmp/diet_snap.tar
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

echo "the bonus"
edit $E '        health.removeModifier(VARIETY);' '        // health.removeModifier(VARIETY);'
expect "a bonus added without the old one taken off" "one of them is for ever"

edit $E '            health.addPermanentModifier(new AttributeModifier(VARIETY, wanted,' '            health.addTransientModifier(new AttributeModifier(VARIETY, wanted,'
expect "a bonus that vanishes on relog" "silently does nothing"

edit $E '                    AttributeModifier.Operation.ADD_VALUE));' '                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));'
expect "a bonus whose size depends on what else you are wearing" "what else the player is wearing"

edit $E '        float wanted = AgronomyRules.extraHealth(DietAttachments.variety(player));' '        float wanted = 20.0F;'
expect "a bonus that is a number of its own" "retuning the rule changes nothing"

edit $E '        if (player.getHealth() > player.getMaxHealth()) {' '        if (false) {'
expect "nothing clamping health when the bonus shrinks" "above their own maximum"

echo "what counts as variety"
edit $A '        Set<String> had = new LinkedHashSet<>(player.getData(EATEN.get()));' '        List<String> had = new ArrayList<>(player.getData(EATEN.get()));'
expect "eating thirty loaves of bread counting as thirty foods" "exists to discourage"

edit $A '                    .copyOnDeath()' '                    '
expect "losing what you have eaten when you die" "punishment for dying"

echo "the book"
rm -f $M/src/main/resources/data/elysiumagronomy/recipe/food_book.json
expect "a book that cannot be made" "cannot be made"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-agronomy/src/main/resources/assets/elysiumagronomy/lang/en_us.json")
d = json.loads(p.read_text(encoding="utf-8"))
d.pop("elysiumagronomy.diet.read", None)
p.write_text(json.dumps(d, indent=2) + "\n", encoding="utf-8")
PYX
expect "the book showing a translation key" "it shows the key"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all agronomy checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | grep -A1 PROBLEM | tail -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "diet: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
