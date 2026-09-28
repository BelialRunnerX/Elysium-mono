#!/usr/bin/env bash
# The take a shift-click owes, forgotten one menu at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
BENCH=$ROOT/elysium-core/src/main/java/com/elysium/core/menu/TwoInputBenchMenu.java
SMITH=$ROOT/elysium-armory/src/main/java/com/elysium/armory/menu/ArmorySmithingMenu.java
REFORGE=$ROOT/elysium-armory/src/main/java/com/elysium/armory/menu/ArmoryReforgingMenu.java
SNAP=/tmp/quick_move_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-core/src/main/java/com/elysium/core/menu elysium-armory/src/main/java/com/elysium/armory/menu)
restore() { (cd $ROOT && tar xf $SNAP); }
out() { (cd $ROOT && timeout 300 python3 check_quick_move.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the benches, the way they were found"
edit $BENCH '        slot.onTake(player, stack);' '        slot.setChanged();'
expect "a bench handing a shift-clicked result over for nothing" "TwoInputBenchMenu.java: a slot here charges in onTake, and quickMoveStack never calls it"

echo "the smithing table"
edit $SMITH '        slot.onTake(player, stack);' '        slot.setChanged();'
expect "a smithing table that keeps its paper on a shift-click" "ArmorySmithingMenu.java: a slot here charges in onTake"

echo "the reforging table's choices"
edit $REFORGE '        slot.onTake(player, stack);' '        slot.setChanged();'
expect "a reforging table whose choices come free by shift-click" "ArmoryReforgingMenu.java: a slot here charges in onTake"

echo "restoring and re-checking"
restore
if out | grep -q "PROBLEM"; then echo "  RESTORE FAILED"; fail=$((fail+1)); else echo "  tree restored clean"; fi
rm -f $SNAP
echo "quick move: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
