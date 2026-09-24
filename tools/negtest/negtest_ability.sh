#!/usr/bin/env bash
# Thirty abilities, broken one way at a time.
#
# Run against the harness rather than the whole library validator: every check
# being broken here lives in the harness, and elysium-lib's validator takes two
# minutes a run because it compiles and runs nineteen other harnesses first.
# Twelve tests times two minutes is a test nobody runs.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-lib
R=$M/src/main/java/com/elysium/lib/ability/ElysiumAbilityRules.java
H=$M/tools/AbilityHarness.java
SNAP=/tmp/ability_snap.tar
OUT=/tmp/ability_out
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-lib/src/main/java/com/elysium/lib/ability elysium-lib/tools/AbilityHarness.java)
restore() { rm -rf $ROOT/elysium-lib/src/main/java/com/elysium/lib/ability && (cd $ROOT && tar xf $SNAP); }
out() {
  rm -rf $OUT && mkdir -p $OUT
  if ! javac -nowarn -d $OUT "$R" "$H" 2>&1 | grep -v JAVA_TOOL; then :; fi
  java -cp $OUT com.elysium.lib.ability.AbilityHarness 2>&1 | grep -v JAVA_TOOL
}
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | head -2 | tail -1 | cut -c1-140)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the rule that makes thirty abilities into thirty abilities"
edit $R 'made("ironhide", PASSIVE, "always", 0, 0, 1.0F, "armour", "defence"),' 'made("ironhide", PASSIVE, "always", 0, 0, 1.0F, "armour", "poise"),'
expect "two passives in one domain, one strictly better" "makes pointless"

edit $R 'made("cleave", TRIGGERED, "on_hit", 80, 0, 1.0F, "damage", "offence"),' 'made("cleave", TRIGGERED, "on_hit", 140, 0, 1.3F, "damage", "sundering"),'
expect "two abilities that are the same ability under two names" "same ability under two names"

edit $R '            if (!trigger.equals(other.trigger()) || !domain.equals(other.domain())) {
                return false;
            }' '            if (true) {
                return false;
            }'
expect "a domination check that answers no to everything" "answers no to everything"

edit $R '            boolean asGood = power >= other.power()' '            boolean asGood = power <= other.power()'
expect "a domination check with the comparison the wrong way round" "answers no to everything"

edit $R '            if (!trigger.equals(other.trigger()) || !domain.equals(other.domain())) {' '            if (!trigger.equals(other.trigger())) {'
expect "a damage passive making a night-vision passive pointless" "different kinds of thing"

edit $R '            if (!trigger.equals(other.trigger()) || !domain.equals(other.domain())) {' '            if (!domain.equals(other.domain())) {'
expect "something on every hit making something on a kill pointless" "fire on different things"

echo "what an ability is worth"
edit $R '    public static final Set<String> BY_MAGNITUDE = Set.of("always", "on_low_health");' '    public static final Set<String> BY_MAGNITUDE = Set.of("always");'
expect "a rescue priced by how often it comes round" "priced by how often"

edit $R '    public static final float CEILING = 4.0F;' '    public static final float CEILING = 1.0F;'
expect "abilities over a ceiling" "the ceiling is"

edit $R '    public static final float FLOOR = 0.25F;' '    public static final float FLOOR = 2.0F;'
expect "abilities under the floor" "not worth the line it takes up"

echo "what one item may carry"
edit $R '            if (!seen.add(id)) {' '            if (false) {'
expect "the same ability twice on one item" "not twice as good"

edit $R '        if (ids.size() > MOST_ON_AN_ITEM) {' '        if (false) {'
expect "more abilities on an item than the limit" "than the limit on one item is allowed"

edit $R '    public static final float MOST_ON_AN_ITEM_WORTH = 6.0F;' '    public static final float MOST_ON_AN_ITEM_WORTH = 60.0F;'
expect "a worth ceiling that lets the three strongest share an item" "worth ceiling does nothing"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"none of them pointless"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | head -2 | cut -c1-160)"; fail=$((fail+1)); fi
echo "abilities: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
