#!/usr/bin/env bash
# F3 — food as a hazard counter, broken one way at a time.
#
# Every check added for the ward is deliberately defeated here and the suite
# fails if the validators or the harness do not notice. A check nobody has seen
# fail is a check nobody has seen.
set -uo pipefail
ROOT=/tmp/Elysium-mono
H=$ROOT/elysium-lib/src/main/java/com/elysium/lib/hazard
E=$ROOT/elysium-lib/src/main/java/com/elysium/lib/event
LANG=$ROOT/elysium-lib/src/main/resources/assets/elysiumlib/lang/en_us.json
HH=$ROOT/elysium-lib/tools/HazardHarness.java
CJ=$ROOT/elysium-core/src/main/java/com/elysium/core/Elysium.java
CI=$ROOT/elysium-core/src/main/java/com/elysium/core/item/ElysiumRationItem.java
CLANG=$ROOT/elysium-core/src/main/resources/assets/elysium/lang/en_us.json
pass=0; fail=0

outlib()  { (cd $ROOT/elysium-lib  && python3 validate.py 2>&1); }
outcore() { (cd $ROOT/elysium-core && python3 validate.py 2>&1); }

expect() { local w="$2" g; g="$(outlib)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
expectc() { local w="$2" g; g="$(outcore)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }

edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "the ward itself"
mv $H/ElysiumWards.java /tmp/w.bak
expect "the ward layer is gone entirely" "cheap counter is a line of tooltip text"
mv /tmp/w.bak $H/ElysiumWards.java

cp $H/ElysiumWards.java /tmp/w.bak
edit $H/ElysiumWards.java 'ward("pressure_ward", ElysiumHazards.PRESSURE)' 'null'
expect "one hazard left with no ward" "has no cheap counter"
cp /tmp/w.bak $H/ElysiumWards.java

cp $H/ElysiumWards.java /tmp/w.bak
edit $H/ElysiumWards.java 'ward("pressure_ward", ElysiumHazards.PRESSURE)' \
                          'ward("pressure_ward", ElysiumHazards.THERMAL)'
expect "two wards bound to the same hazard" "one hazard has two answers"
cp /tmp/w.bak $H/ElysiumWards.java

cp $H/ElysiumWards.java /tmp/w.bak
edit $H/ElysiumWards.java 'bind(hazard, holder);' ''
expect "effects registered but never bound" "never look at"
cp /tmp/w.bak $H/ElysiumWards.java

cp $H/ElysiumWards.java /tmp/w.bak
edit $H/ElysiumWards.java 'return present;' 'return present && amplifier == 0;'
expect "the ward reading its amplifier (validator)" "a stacked ward means something different"
expect "the ward reading its amplifier (harness)" "stops counting"
cp /tmp/w.bak $H/ElysiumWards.java

echo "the clock"
cp $H/ElysiumWards.java /tmp/w.bak
edit $H/ElysiumWards.java 'public static final int DURATION = 20 * 180;' \
                          'public static final int DURATION = 20 * 10;'
expect "a ward too short to cross anything" "not long enough to cross"
cp /tmp/w.bak $H/ElysiumWards.java

cp $H/ElysiumWards.java /tmp/w.bak
edit $H/ElysiumWards.java 'public static final int DURATION = 20 * 180;' \
                          'public static final int DURATION = 20 * 3600;'
expect "immunity long enough to retire the armour set" "nothing left to be for"
cp /tmp/w.bak $H/ElysiumWards.java

echo "the wiring"
cp $E/ElysiumHazardHandler.java /tmp/e.bak
edit $E/ElysiumHazardHandler.java 'boolean warded = com.elysium.lib.hazard.ElysiumWards.isWarded(player, hazard);' \
                                  'boolean warded = false;'
expect "the tick never asking about the meal" "does not exist"
cp /tmp/e.bak $E/ElysiumHazardHandler.java

cp $H/ElysiumProtection.java /tmp/p.bak
edit $H/ElysiumProtection.java 'float room = roomShare(entity, hazard);' \
                               'float room = roomShare(entity, hazard) + (ElysiumWards.isWarded(entity, hazard) ? 0.1F : 0.0F);'
expect "the meal combining as a share as well" "two implementations that have to agree"
cp /tmp/p.bak $H/ElysiumProtection.java

cp $H/ElysiumProtection.java /tmp/p.bak
edit $H/ElysiumProtection.java 'return sealedBy(worn, hazard);' \
                               'return sealedBy(worn, hazard) || ElysiumWards.isWarded(entity, hazard);'
expect "the meal folded into the armour seal" "when it was lunch"
cp /tmp/p.bak $H/ElysiumProtection.java

cp $LANG /tmp/l.bak
edit $LANG '"elysium.hazard.warded"' '"elysium.hazard.gone"'
expect "the moment the meal pays off, as a raw key" "at the one moment the meal pays off"
cp /tmp/l.bak $LANG

echo "being told what you ate"
cp $LANG /tmp/l.bak
edit $LANG '"effect.elysiumlib.pressure_ward"' '"effect.elysiumlib.no_such_ward"'
expect "a ward with no name" "reads a raw key"
cp /tmp/l.bak $LANG

echo "the rations themselves"
cp $CI /tmp/ci.bak
edit $CI '.food(new FoodProperties.Builder()' '.stacksTo(64); if (false) new FoodProperties.Builder()'
expectc "a ration nobody can eat" "cannot be eaten"
cp /tmp/ci.bak $CI

cp $CI /tmp/ci.bak
edit $CI 'entity.addEffect(new MobEffectInstance(ward, ElysiumWards.DURATION));' ''
expectc "a ration that wards nothing" "wards them against nothing"
cp /tmp/ci.bak $CI

cp $CI /tmp/ci.bak
edit $CI 'ElysiumWards.wardFor(hazard)' 'null'
expectc "a ration granting something the tick does not read" "not what the damage tick looks for"
cp /tmp/ci.bak $CI

cp $CI /tmp/ci.bak
edit $CI 'ElysiumWards.DURATION));' '1200));'
expectc "a ration setting its own duration" "not the number a player gets"
cp /tmp/ci.bak $CI

cp $CJ /tmp/cj.bak
edit $CJ 'ration("radiological_ration", com.elysium.lib.hazard.ElysiumHazards.RADIOLOGICAL)' \
         'simpleItem("radiological_ration")'
expectc "a hazard with no ration" "no ration wards"
cp /tmp/cj.bak $CJ

cp $CJ /tmp/cj.bak
edit $CJ 'ration("pressure_ration", com.elysium.lib.hazard.ElysiumHazards.PRESSURE)' \
         'ration("pressure_ration", com.elysium.lib.hazard.ElysiumHazards.THERMAL)'
expectc "two rations answering one hazard" "is being answered twice"
cp /tmp/cj.bak $CJ

cp $CLANG /tmp/cl.bak
edit $CLANG '"elysium.tooltip.ration.whole"' '"elysium.tooltip.ration.deleted"'
expectc "the line that says how long the immunity lasts" "how long the immunity lasts"
cp /tmp/cl.bak $CLANG

echo
echo "restoring and re-checking"
if (cd $ROOT && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
echo "wards: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
