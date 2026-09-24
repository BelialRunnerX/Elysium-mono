#!/usr/bin/env bash
# The hazard layer, broken one way at a time.
set -uo pipefail
cd /tmp/Elysium-mono/elysium-lib
H=src/main/java/com/elysium/lib/hazard
L=src/main/resources/assets/elysiumlib/lang/en_us.json
pass=0; fail=0
out() { python3 validate.py 2>&1; }
expect() { local n="$1" w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $n"; pass=$((pass+1));
  else echo "  MISSED : $n"; fail=$((fail+1)); fi; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@"; }

echo "the stacking rule"
cp $H/ElysiumResistance.java /tmp/r.bak
edit $H/ElysiumResistance.java 'return Math.min(share, MAX_SINGLE_SHARE);' 'return share;'
expect "a source may claim everything" "produced immunity"
cp /tmp/r.bak $H/ElysiumResistance.java

cp $H/ElysiumResistance.java /tmp/r.bak
edit $H/ElysiumResistance.java 'remaining *= 1.0F - clampShare(share);' 'remaining -= clampShare(share);'
expect "additive stacking instead of independent" "two quarters did not combine"
cp /tmp/r.bak $H/ElysiumResistance.java

cp $H/ElysiumResistance.java /tmp/r.bak
edit $H/ElysiumResistance.java 'if (!(share > 0.0F)) {' 'if (false) {'
expect "a NaN share poisons the product" "NaN share poisoned"
cp /tmp/r.bak $H/ElysiumResistance.java

echo "the design budget"
# The scenario the budget comment describes: a not-yet-built feature ships
# generous and deletes the hazard layer between them. Raising gear instead trips
# a neighbouring check first, which is correct but tests the wrong guard.
cp tools/HazardHarness.java /tmp/hh.bak
edit tools/HazardHarness.java 'final float shelter = 0.35F;' 'final float shelter = 0.80F;'
expect "shelter generous enough to replace the seal" "without ever assembling the set"
cp /tmp/hh.bak tools/HazardHarness.java

cp $H/ElysiumProtection.java /tmp/p.bak
edit $H/ElysiumProtection.java 'public static final float BASE_SHARE = 0.11F;' \
                               'public static final float BASE_SHARE = 0.02F;'
expect "gear too weak to be worth assembling" "not worth the trouble"
cp /tmp/p.bak $H/ElysiumProtection.java

cp $H/ElysiumProtection.java /tmp/p.bak
edit $H/ElysiumProtection.java 'public static final float RING_FRACTION = 0.5F;' \
                               'public static final float RING_FRACTION = 1.0F;'
expect "the ring answer as good as the right element" "matching element is pointless"
cp /tmp/p.bak $H/ElysiumProtection.java

cp $H/ElysiumProtection.java /tmp/p.bak
edit $H/ElysiumProtection.java 'public static final float RING_FRACTION = 0.5F;' \
                               'public static final float RING_FRACTION = 0.0F;'
expect "the ring stops mattering outside combat" "counter ring stops existing"
cp /tmp/p.bak $H/ElysiumProtection.java

echo "the survival window"
cp $H/ElysiumHazards.java /tmp/h.bak
edit $H/ElysiumHazards.java 'id("pressure"), ElysiumElements.DIMENSIONAL, 2.4F);' \
                            'id("pressure"), ElysiumElements.DIMENSIONAL, 9.0F);'
expect "a hazard that kills before you can read it" "not long enough to read the message"
cp /tmp/h.bak $H/ElysiumHazards.java

cp $H/ElysiumHazards.java /tmp/h.bak
edit $H/ElysiumHazards.java 'id("radiological"), ElysiumElements.VOID, 1.0F);' \
                            'id("radiological"), ElysiumElements.VOID, 0.2F);'
expect "a hazard slow enough to ignore" "long enough to ignore"
cp /tmp/h.bak $H/ElysiumHazards.java

echo "one element, two hazards"
cp $H/ElysiumHazards.java /tmp/h.bak
edit $H/ElysiumHazards.java 'id("corrosive"), ElysiumElements.NEURAL, 1.5F);' \
                            'id("corrosive"), ElysiumElements.PLASMA, 1.5F);'
expect "two hazards sharing an element (structural)" "so that armour"
expect "two hazards sharing an element (executed)" "shares an element with another hazard"
cp /tmp/h.bak $H/ElysiumHazards.java

echo "being told what is killing you"
cp $L /tmp/l.bak
python3 -c "
import json,collections
p='src/main/resources/assets/elysiumlib/lang/en_us.json'
d=json.load(open(p),object_pairs_hook=collections.OrderedDict)
del d['elysium.hazard.radiological.cheap']
json.dump(d,open(p,'w'),indent=2,ensure_ascii=False)"
expect "a hazard with no advice" "the free thing a player can do about it"
cp /tmp/l.bak $L

echo "the seal"
cp $H/ElysiumProtection.java /tmp/p.bak
edit $H/ElysiumProtection.java 'if (worn == null || !hazard.getElement().equals(worn)) {' \
                               'if (worn != null && !hazard.getElement().equals(worn)) {'
expect "an empty slot still seals" "an empty slot"
cp /tmp/p.bak $H/ElysiumProtection.java

cp $H/ElysiumProtection.java /tmp/p.bak
edit $H/ElysiumProtection.java 'if (worn == null || !hazard.getElement().equals(worn)) {' \
                               'if (worn == null || (!hazard.getElement().equals(worn)
                    && !worn.isStrongAgainst(hazard.getElement()))) {'
expect "the ring answer grants immunity" "sealed against"
cp /tmp/p.bak $H/ElysiumProtection.java

cp $H/ElysiumProtection.java /tmp/p.bak
edit $H/ElysiumProtection.java 'if (hazard == null || wornElements == null || wornElements.isEmpty()) {' \
                               'if (hazard == null || wornElements == null) {'
expect "wearing nothing seals" "wearing nothing at all sealed"
cp /tmp/p.bak $H/ElysiumProtection.java

# The seal must actually be consulted by the thing that deals the damage.
cp src/main/java/com/elysium/lib/event/ElysiumHazardHandler.java /tmp/hh2.bak
edit src/main/java/com/elysium/lib/event/ElysiumHazardHandler.java \
  'boolean armoured = ElysiumProtection.isSealed(player, hazard);' \
  'boolean armoured = false;'
expect "the damage tick ignores the seal" "never asks whether the player is sealed"
cp /tmp/hh2.bak src/main/java/com/elysium/lib/event/ElysiumHazardHandler.java

echo
echo "negative tests: $pass caught, $fail missed"
python3 validate.py >/dev/null 2>&1 && echo "restored clean" || echo "RESTORE FAILED"
[ "$fail" -eq 0 ]
