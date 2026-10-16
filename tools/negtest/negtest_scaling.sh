#!/usr/bin/env bash
# Ten points a level and uncapped totals, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono/elysium-lib
S=$ROOT/src/main/java/com/elysium/lib/stats
C=$ROOT/src/main/java/com/elysium/lib/character
E=$ROOT/src/main/java/com/elysium/lib/event
W=$ROOT/src/main/java/com/elysium/lib/screen
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

echo "the level grant"
cp $C/ElysiumCharacter.java /tmp/ch.bak
edit $C/ElysiumCharacter.java 'POINTS_PER_LEVEL = 10;' 'POINTS_PER_LEVEL = 2;'
expect "a level quietly back to two points" "back to the old number"
cp /tmp/ch.bak $C/ElysiumCharacter.java

echo "curves that arrive"
cp $S/ElysiumStats.java /tmp/st.bak
edit $S/ElysiumStats.java 'ChatFormatting.AQUA, 1200.0F, 0.85F' 'ChatFormatting.AQUA, 1200.0F, 1.0F'
expect "a proportional stat converging on 1.0" "converges on deleting"
cp /tmp/st.bak $S/ElysiumStats.java

cp $S/ElysiumStats.java /tmp/st.bak
edit $S/ElysiumStats.java 'ChatFormatting.DARK_AQUA, 1400.0F, 0.90F' 'ChatFormatting.DARK_AQUA, 1400.0F, 0.95F'
expect "resilience high enough to outrun difficulty" "nobody can reach them at"
cp /tmp/st.bak $S/ElysiumStats.java

cp $S/ElysiumStats.java /tmp/st.bak
edit $S/ElysiumStats.java 'ChatFormatting.AQUA, 1200.0F, 0.85F' 'ChatFormatting.AQUA, 200000.0F, 0.85F'
expect "a soft cap nothing in the game approaches" "not worth building toward"
cp /tmp/st.bak $S/ElysiumStats.java

cp $S/ElysiumStat.java /tmp/s1.bak
edit $S/ElysiumStat.java 'return value / (value + halfway) * ceiling;' \
                         'return value / (value + halfway * 0.5F) * ceiling;'
expect "a halfway point that is not the halfway point" "not half of"
cp /tmp/s1.bak $S/ElysiumStat.java

cp $S/ElysiumStat.java /tmp/s1.bak
edit $S/ElysiumStat.java 'return value / (value + halfway) * ceiling;' \
                         'return Math.min(ceiling, value / (value + halfway) * ceiling + 0.2F);'
expect "a curve that reaches its own ceiling" "reached its ceiling"
cp /tmp/s1.bak $S/ElysiumStat.java

echo "the two-term scaling"
cp $S/ElysiumStat.java /tmp/s1.bak
edit $S/ElysiumStat.java 'return raw + raw * raw / SCALING_DIVISOR;' 'return raw;'
expect "the square term removed, leaving a linear stat" "the same slope it had in the first hour"
cp /tmp/s1.bak $S/ElysiumStat.java

cp $S/ElysiumStat.java /tmp/s1.bak
edit $S/ElysiumStat.java 'public static final float SCALING_DIVISOR = 512.0F;' \
                         'public static final float SCALING_DIVISOR = 64.0F;'
expect "the exponential taking over inside the ordinary game" "far end"
cp /tmp/s1.bak $S/ElysiumStat.java

cp $S/ElysiumStat.java /tmp/s1.bak
edit $S/ElysiumStat.java 'return raw + raw * raw / SCALING_DIVISOR;' \
                         'return raw * raw / SCALING_DIVISOR;'
expect "the linear term dropped, so early points are worthless" "less than itself"
cp /tmp/s1.bak $S/ElysiumStat.java

cp $S/ElysiumStat.java /tmp/s1.bak
edit $S/ElysiumStat.java 'return curveOf(effective(points));' 'return curveOf(points);'
expect "a scaling function nothing is fed through" "nothing is scaled by it"
cp /tmp/s1.bak $S/ElysiumStat.java

cp $S/ElysiumStats.java /tmp/st.bak
edit $S/ElysiumStats.java 'return effective(strength) * DAMAGE_PER_POINT;' \
                          'return strength * DAMAGE_PER_POINT;'
expect "a flat stat left on the raw total" "falls further behind"
cp /tmp/st.bak $S/ElysiumStats.java

echo "decimal points"
cp $S/ElysiumStats.java /tmp/st.bak
edit $S/ElysiumStats.java 'DAMAGE_PER_POINT = 0.5F;' 'DAMAGE_PER_POINT = 5.0F;'
expect "strength off by a factor of ten" "in one swing"
cp /tmp/st.bak $S/ElysiumStats.java

cp $S/ElysiumStats.java /tmp/st.bak
edit $S/ElysiumStats.java 'HEALTH_PER_POINT = 0.5D;' 'HEALTH_PER_POINT = 0.01D;'
expect "vitality too small to build around" "less than the player starts with"
cp /tmp/st.bak $S/ElysiumStats.java

cp $S/ElysiumStats.java /tmp/st.bak
edit $S/ElysiumStats.java 'SHIELD_PER_POINT = 0.8F;' 'SHIELD_PER_POINT = 40.0F;'
expect "a shield that replaces the health bar" "health bar is"
cp /tmp/st.bak $S/ElysiumStats.java

echo "fortitude past vanilla's ceiling"
cp $S/ElysiumStats.java /tmp/st.bak
edit $S/ElysiumStats.java 'if (fortitude <= ARMOUR_FULL) {' 'if (fortitude < 0) {'
expect "toughness granted before armour is full" "have not earned"
cp /tmp/st.bak $S/ElysiumStats.java

cp $S/ElysiumStats.java /tmp/st.bak
edit $S/ElysiumStats.java 'return Math.min(VANILLA_TOUGHNESS_CEILING, beyond * TOUGHNESS_PER_POINT);' \
                          'return beyond * TOUGHNESS_PER_POINT;'
expect "toughness past the point the game reads it" "own ceiling"
cp /tmp/st.bak $S/ElysiumStats.java

cp $E/ElysiumStatHandler.java /tmp/eh.bak
edit $E/ElysiumStatHandler.java 'set(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID,' \
                                'if (false) set(player, Attributes.ARMOR, TOUGHNESS_ID,'
expect "toughness computed and never applied" "never touches ARMOR_TOUGHNESS"
cp /tmp/eh.bak $E/ElysiumStatHandler.java

echo "the sheet"
cp $W/ElysiumCharacterScreen.java /tmp/cs.bak
edit $W/ElysiumCharacterScreen.java 'private static float barFor(int value, int scale) {' \
                                    'private static float barFor(int value) {'
edit $W/ElysiumCharacterScreen.java 'barFor(value, scale)' 'barFor(value)'
expect "the bars back on a fixed denominator" "stopped distinguishing"
cp /tmp/cs.bak $W/ElysiumCharacterScreen.java

echo
if (cd /tmp/Elysium-mono && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED"; fail=$((fail+1))
fi
echo "scaling: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
