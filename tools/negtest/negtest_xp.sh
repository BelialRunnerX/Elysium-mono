#!/usr/bin/env bash
# Experience from every action, broken one way at a time (elysium-lib: character/ElysiumXpRules,
# event/ElysiumXpHandler, tools/XpHarness). Dylan, 28 Sep 2026: every game action gives xp.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-lib
RULES=$M/src/main/java/com/elysium/lib/character/ElysiumXpRules.java
HANDLER=$M/src/main/java/com/elysium/lib/event/ElysiumXpHandler.java
SNAP=/tmp/xp_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-lib/src/main/java/com/elysium/lib/character elysium-lib/src/main/java/com/elysium/lib/event elysium-lib/tools/XpHarness.java)
restore() { (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 1800 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | grep -i 'experience\|FAIL\|problem' | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the rules, through the harness"
edit $RULES '            case MINE -> 60;' '            case MINE -> 600;'
expect "a cap that is a level a minute" "a level a minute"

edit $RULES '            case DIG -> 1;' '            case DIG -> 3;'
expect "digging dirt paying as much as mining stone" "digging dirt pays as much as mining stone"

edit $RULES '        return Math.max(0, Math.min(wanted, cap - spentInWindow));' '        return Math.max(0, wanted);'
expect "a cap that lets everything through" "let through what is left and no more"

echo "the handler's wiring"
edit $HANDLER '            pay(player, Source.FISH);' '            ;'
expect "a source nothing pays" "FISH is a source the rules declare and is never paid"

edit $HANDLER '        int allowed = ElysiumXpRules.allow(ledger.spent[at], ElysiumXpRules.cap(source), wanted);' '        int allowed = wanted;'
expect "a door that never consults the cap" "the cap is never consulted"

edit $HANDLER 'player.isCreative() || ' ''
expect "a creative player paid for a mountain" "creative player"

edit $HANDLER '            pay(player, Source.TRADE);' '            ElysiumCharacter.addXp(player, 5);'
expect "a payment that walks past the door" "one door"

edit $HANDLER '        LEDGERS.remove(event.getEntity().getUUID());' '        ;'
expect "a ledger kept for every visitor for ever" "kept after they leave"

echo "restoring and re-checking"
restore
if out | grep -q "PROBLEM"; then echo "  RESTORE FAILED"; fail=$((fail+1)); else echo "  tree restored clean"; fi
rm -f $SNAP
echo "experience: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
