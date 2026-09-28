#!/usr/bin/env bash
# The empty hand's way to a block's second use path, blocked one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
TRANSMUTER=$ROOT/elysium-arcana/src/main/java/com/elysium/arcana/block/TransmuterBlock.java
TERMINAL=$ROOT/elysium-logistics/src/main/java/com/elysium/logistics/block/TerminalBlock.java
CONDUIT=$ROOT/elysium-solarpunk/src/main/java/com/elysium/solarpunk/power/PowerConduitBlock.java
RUNS=$ROOT/elysium-lib/src/main/java/com/elysium/lib/run/Runs.java
SNAP=/tmp/use_paths_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-arcana/src/main/java/com/elysium/arcana/block elysium-logistics/src/main/java/com/elysium/logistics/block elysium-solarpunk/src/main/java/com/elysium/solarpunk/power elysium-lib/src/main/java/com/elysium/lib/run)
restore() { (cd $ROOT && tar xf $SNAP); }
out() { (cd $ROOT && timeout 300 python3 check_use_paths.py 2>&1); }
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

echo "the transmuter, the way it was found"
edit $TRANSMUTER '            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;' '            return ItemInteractionResult.sidedSuccess(level.isClientSide);'
expect "a transmuter that swallows the empty hand, so its chooser never opens" "TransmuterBlock.java: overrides useItemOn and useWithoutItem and never answers"

echo "the terminal, the same shape"
edit $TERMINAL '            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;' '            return ItemInteractionResult.CONSUME;'
expect "a terminal that consumes the empty hand, so it never says what the core has" "TerminalBlock.java: overrides useItemOn and useWithoutItem and never answers"

echo "a hand-off to a helper that does not pass"
edit $RUNS '            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;' '            return ItemInteractionResult.CONSUME;'
expect "a conduit handing the hand to a dye helper that keeps it" "PowerConduitBlock.java: overrides useItemOn and useWithoutItem and never answers"

echo "a hand-off the check has to see inside useItemOn"
edit $CONDUIT '        return Runs.dye(held, level, pos, player, hand, this::device);' '        return ItemInteractionResult.CONSUME;'
expect "a conduit that stopped handing the hand to the helper" "PowerConduitBlock.java: overrides useItemOn and useWithoutItem and never answers"

echo "restoring and re-checking"
restore
if out | grep -q "PROBLEM"; then echo "  RESTORE FAILED"; fail=$((fail+1)); else echo "  tree restored clean"; fi
rm -f $SNAP
echo "use paths: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
