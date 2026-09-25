#!/usr/bin/env bash
# The allowlist of things the game already has, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-agronomy
K=$M/src/main/java/com/elysium/agronomy/rules/KitchenRules.java
SNAP=/tmp/gamesown_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 600 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit is made."
  (cd $M && timeout 600 python3 validate.py 2>&1 | tail -5)
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-agronomy)
restore() { rm -rf $ROOT/elysium-agronomy && (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
out() { (cd $M && timeout 600 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
gexpect() { (cd $M && python3 tools/gen_kitchen.py >/dev/null 2>&1); expect "$@"; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the list of things the game already has"
edit $K '            "apple", "beef", "bone",' '            "apple", "barley_sheaf", "beef", "bone",'
expect "a mod's item allowed as though the game shipped it" "the game does not have them"

edit $K '            "hay_block", "honey_bottle",' '            "hay_bale", "honey_bottle",'
expect "a vanilla item allowed under a name the game does not use" "the game does not have them"

edit $K '    public static final Set<String> THE_GAMES_OWN = Set.of(' '    public static final Set<String> THE_GAMES_OWN_LIST = Set.of('
edit $M/validate.py 'games_own = set(re.findall(r'"'"'THE_GAMES_OWN\s*=\s*Set\.of\(([^;]*)\);'"'"', bee_source))' 'games_own = set(re.findall(r'"'"'THE_GAMES_OWN_LIST\s*=\s*Set\.of\(([^;]*)\);'"'"', bee_source))'
expect "the allowlist renamed out from under the check" "reads as naming nothing"

edit $K '            "hay_block", "honey_bottle", "milk_bucket", "mutton", "porkchop",' '            "honey_bottle", "milk_bucket", "mutton", "porkchop",'
expect "an ingredient a cut needs dropped out of the allowlist" "cutting hay_block, which nothing makes"

echo "the recipes that name it"
edit $K '            new Cut("cabbage", "knife", Map.of("cabbage_leaf", 3)),' '            new Cut("cabbage", "knife", Map.of("cabbage_shreds", 3)),'
gexpect "a cut yielding something nothing makes" "neither this module makes nor the game ships"

edit $K '            new Cut("cabbage", "knife", Map.of("cabbage_leaf", 3)),' '            new Cut("kohlrabi", "knife", Map.of("cabbage_leaf", 3)),'
gexpect "a cut of something nothing grows" "neither this module makes nor the game ships"

edit $K '            new Cut("hay_block", "knife", Map.of("barley", 2, "rice", 1)));' '            new Cut("hay_block", "knife", Map.of("barley", 2, "paddy", 1)));'
gexpect "a cut whose second yield is nothing" "neither this module makes nor the game ships"

echo "the check itself"
edit $M/validate.py '                    if key.startswith(("item.minecraft.", "block.minecraft."))}' '                    if key.startswith(("item.minecraft.",))}'
expect "a game list that forgets the game's blocks" "checking nothing"

edit $M/validate.py '                    if key.startswith(("item.minecraft.", "block.minecraft."))}' '                    if key.startswith(("item.nothing.", "block.nothing."))}'
expect "a game list that reads as empty, so the check below it checks nothing" "checking nothing"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all agronomy checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | tail -3 | head -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "the game's own: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
