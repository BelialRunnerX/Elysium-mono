#!/usr/bin/env bash
# The server-join crash, reintroduced one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono/elysium-core
C=$ROOT/src/main/java/com/elysium/core/compat/ConvergenceSmithingRecipe.java
G=$ROOT/tools/gen_data.py
R=$ROOT/src/main/resources/data/elysium/recipe/thermal_ration.json
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

echo "the exact bug that shipped"
cp $C /tmp/cv.bak
edit $C 'StreamCodec.unit(INSTANCE);' 'StreamCodec.unit(new ConvergenceSmithingRecipe());'
expect "a fresh instance handed to the stream codec" "freshly constructed value"
cp /tmp/cv.bak $C

cp $C /tmp/cv.bak
edit $C 'MapCodec.unit(INSTANCE);' 'MapCodec.unit(ConvergenceSmithingRecipe::new);'
expect "a supplier on the map side" "::new supplier"
cp /tmp/cv.bak $C

cp $C /tmp/cv.bak
edit $C 'MapCodec.unit(INSTANCE);' 'MapCodec.unit(OTHER);'
expect "the two units naming different values" "have to be the same value"
cp /tmp/cv.bak $C

cp $C /tmp/cv.bak
edit $C '    public boolean equals(Object other) {' '    public boolean notEquals(Object other) {'
expect "a stateless recipe that does not answer equals" "does not override equals"
cp /tmp/cv.bak $C

echo "categories the game does not have"
cp $R /tmp/rr.bak
edit $R '"category": "misc"' '"category": "food"'
expect "a crafting category that drops the whole recipe" "is not one of"
cp /tmp/rr.bak $R

echo
if (cd $ROOT && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED"; fail=$((fail+1))
fi
echo "recipe codecs: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
