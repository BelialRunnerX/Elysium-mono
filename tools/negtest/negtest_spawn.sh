#!/usr/bin/env bash
# The spawn distance rule, broken one way at a time.
set -uo pipefail
cd /tmp/Elysium-mono/elysium-lib
E=src/main/java/com/elysium/lib/event/ElysiumSpawnDistanceHandler.java
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
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "what the rule applies to"
cp $E /tmp/e.bak
edit $E 'return category == MobCategory.MONSTER && type == MobSpawnType.NATURAL;' \
        'return category == MobCategory.MONSTER;'
expect "spawners held to the player distance" "every spawner and mob farm in the world stops working"
cp /tmp/e.bak $E

cp $E /tmp/e.bak
edit $E 'return category == MobCategory.MONSTER && type == MobSpawnType.NATURAL;' \
        'return type == MobSpawnType.NATURAL;'
expect "passive animals held back too" "every farm in the game is quietly ruined"
cp /tmp/e.bak $E

cp $E /tmp/e.bak
edit $E 'return category == MobCategory.MONSTER && type == MobSpawnType.NATURAL;' \
        'return category == MobCategory.MONSTER || type == MobSpawnType.NATURAL;'
expect "the rule spreads across the matrix" "combinations are governed, not the single one"
cp /tmp/e.bak $E

echo "the distance"
cp $E /tmp/e.bak
edit $E 'public static final double MINIMUM_DISTANCE = 32.0D;' \
        'public static final double MINIMUM_DISTANCE = 20.0D;'
expect "no further than vanilla" "it changes nothing"
cp /tmp/e.bak $E

cp $E /tmp/e.bak
edit $E 'public static final double MINIMUM_DISTANCE = 32.0D;' \
        'public static final double MINIMUM_DISTANCE = 128.0D;'
expect "a radius that empties the world" "empties the world around a player"
cp /tmp/e.bak $E

cp $E /tmp/e.bak
edit $E 'MINIMUM_DISTANCE * MINIMUM_DISTANCE;' 'MINIMUM_DISTANCE * 2.0D;'
expect "the documented radius is not the enforced one" "documented radius and the enforced one differ"
cp /tmp/e.bak $E

echo "the boundary"
cp $E /tmp/e.bak
edit $E 'return nearestPlayerDistanceSquared >= MINIMUM_DISTANCE_SQUARED;' \
        'return nearestPlayerDistanceSquared > MINIMUM_DISTANCE_SQUARED;'
expect "off by one at the radius" "off by one"
cp /tmp/e.bak $E

cp $E /tmp/e.bak
edit $E 'return nearestPlayerDistanceSquared >= MINIMUM_DISTANCE_SQUARED;' 'return true;'
expect "the rule never refuses anything" "just inside the radius is allowed"
cp /tmp/e.bak $E

echo
echo "negative tests: $pass caught, $fail missed"
python3 validate.py >/dev/null 2>&1 && echo "restored clean" || echo "RESTORE FAILED"
[ "$fail" -eq 0 ]
