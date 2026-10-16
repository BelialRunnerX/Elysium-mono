#!/usr/bin/env bash
# The health bar, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono/elysium-lib
B=$ROOT/src/main/java/com/elysium/lib/client/ElysiumHealthBar.java
pass=0; fail=0
out() { (cd $ROOT && python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "two readouts at once"
mv $B /tmp/hb.bak
expect "the bar deleted entirely" "back to a row of hearts"
mv /tmp/hb.bak $B

cp $B /tmp/hb.bak
edit $B 'event.setCanceled(true);' ''
expect "vanilla's hearts left drawing underneath" "both readouts at once"
cp /tmp/hb.bak $B

cp $B /tmp/hb.bak
edit $B 'if (!mc.gameMode.canHurtPlayer()) {' 'if (false) {'
expect "a health bar in creative" "vanilla never gave them"
cp /tmp/hb.bak $B

echo "scaling"
cp $B /tmp/hb.bak
edit $B 'float maxHealth = Math.max(1.0F, player.getMaxHealth());' 'float maxHealth = 20.0F;'
expect "a bar fixed at twenty health" "cannot account for the Vitality"
cp /tmp/hb.bak $B

cp $B /tmp/hb.bak
edit $B 'float absorption = Math.max(0.0F, player.getAbsorptionAmount());' 'float absorption = 0.0F;'
expect "the shield made invisible" "invisible"
cp /tmp/hb.bak $B

echo "the geometry"
cp $B /tmp/hb.bak
edit $B 'return Math.max(1, Math.min(width - 1, health));' 'return health;'
expect "a region collapsing to nothing" "not shown at all"
cp /tmp/hb.bak $B

cp $B /tmp/hb.bak
edit $B 'return Math.min(1.0F, value / capacity);' 'return value / capacity;'
expect "a fill that overflows its own bar" "overflowed the bar"
cp /tmp/hb.bak $B

cp $B /tmp/hb.bak
edit $B 'if (!(capacity > 0.0F) || !(value > 0.0F)) {' 'if (false) {'
expect "a division by a zero capacity" "divided by zero"
cp /tmp/hb.bak $B

cp $B /tmp/hb.bak
edit $B 'public static final int BOTTOM_INSET = 23;' 'public static final int BOTTOM_INSET = 12;'
expect "the bar drawn inside the hotbar" "inside the hotbar"
cp /tmp/hb.bak $B

cp $B /tmp/hb.bak
edit $B 'public static final int WIDTH = 81;' 'public static final int WIDTH = 140;'
expect "a bar running under the food gauge" "other side of centre"
cp /tmp/hb.bak $B

echo
if (cd $ROOT && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED"; fail=$((fail+1))
fi
echo "health bar: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
