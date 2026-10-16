#!/usr/bin/env bash
# Three owner decisions about pace and pay, broken one way at a time.
set -uo pipefail
M=/tmp/Elysium-mono
LIB=$M/elysium-lib; CORE=$M/elysium-core
LV=$LIB/src/main/java/com/elysium/lib/character/ElysiumLevelling.java
CT=$CORE/src/main/java/com/elysium/core/ElysiumContent.java
EN=$CORE/src/main/java/com/elysium/core/entity/ImperialEnforcer.java
GD=$CORE/tools/gen_data.py
pass=0; fail=0
outlib()  { (cd $LIB  && python3 validate.py 2>&1); }
outcore() { (cd $CORE && python3 validate.py 2>&1); }
expect()  { local w="$2" g; g="$(outlib)";  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1)); else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
expectc() { local w="$2" g; g="$(outcore)"; if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1)); else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read(); assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "the curve"
cp $LV /tmp/lv.bak
edit $LV 'public static final int XP_PER_LEVEL = 10;' 'public static final int XP_PER_LEVEL = 40;'
expect "the quadratic climb back" "asked to flatten"
cp /tmp/lv.bak $LV
cp $LV /tmp/lv.bak
edit $LV 'public static final int XP_PER_LEVEL = 10;' 'public static final int XP_PER_LEVEL = 0;'
expect "a flat cost, so the climb is gone" "arrive as fast as the first"
cp /tmp/lv.bak $LV
cp $LV /tmp/lv.bak
edit $LV 'public static final int XP_BASE = 100;' 'public static final int XP_BASE = 10;'
edit $LV 'public static final int XP_PER_LEVEL = 10;' 'public static final int XP_PER_LEVEL = 1;'
expect "level fifty in an evening" "single evening"
cp /tmp/lv.bak $LV

echo "the drops"
cp $CT /tmp/ct.bak
edit $CT 'WEAPON_CHANCE = 0.04F;' 'WEAPON_CHANCE = 0.12F;'
expectc "weapons back to one in eight" "the forge loses"
cp /tmp/ct.bak $CT
cp $CT /tmp/ct.bak
edit $CT 'MATERIAL_MIN = 2;' 'MATERIAL_MIN = 1;'
expectc "single-ingot drops" "rounding error"
cp /tmp/ct.bak $CT
cp $EN /tmp/en.bak
edit $EN 'band >= 3 ? 0.03F : 0.015F' 'band >= 3 ? 0.08F : 0.04F'
expectc "an enforcer handing over its blade again" "drops its weapon"
cp /tmp/en.bak $EN

echo "the benches"
cp $GD /tmp/gd.bak
edit $GD '{"a": AET, "#": ING, "i": "minecraft:iron_ingot"},' '{"a": AET, "#": ING, "i": "minecraft:obsidian"},'
(cd $M && python3 elysium-core/tools/gen_data.py >/dev/null 2>&1)
expectc "the forge back on obsidian" "diamond pickaxe"
cp /tmp/gd.bak $GD
(cd $M && ./regen.sh >/dev/null 2>&1)

echo
if (cd $M && python3 validate.py >/dev/null 2>&1); then echo "  tree restored clean"; else echo "  RESTORE FAILED"; fail=$((fail+1)); fi
echo "economy: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
