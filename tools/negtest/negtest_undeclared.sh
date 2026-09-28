#!/usr/bin/env bash
# The shield over a player who has not declared, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-lib
SH=$M/src/main/java/com/elysium/lib/event/ElysiumUndeclaredShield.java
SNAP=/tmp/undeclared_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-lib)
restore() { rm -rf $ROOT/elysium-lib && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 1800 python3 validate.py 2>&1); }
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

echo "a player under the declaration screen"
edit $SH '            event.setCanceled(true);' '            return;'
expect "a shield that sees the damage and lets it through" "lets it through"

edit $SH '        if (!ElysiumCharacter.hasChosen(player)) {' '        if (player.isCreative()) {'
expect "a shield that stopped asking whether the player has chosen" "no longer asks whether the player has chosen"

edit $SH '@EventBusSubscriber(modid = ElysiumLib.MODID)' '// unsubscribed'
expect "a shield nothing subscribes" "shields nothing"

rm -f $SH
expect "no shield at all" "helpless under the declaration screen"

echo "restoring and re-checking"
restore
if out | grep -q "problem"; then echo "  RESTORE FAILED"; fail=$((fail+1)); else echo "  tree restored clean"; fi
rm -f $SNAP
echo "undeclared: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
