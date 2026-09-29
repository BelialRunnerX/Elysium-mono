#!/usr/bin/env bash
# The harvest audit (29 Sep 2026): the running game is asked whether every block that
# needs a tool has one tagged to mine it, and the whole answer goes to a file — broken
# one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-core
A=$M/src/main/java/com/elysium/core/command/HarvestAudit.java
C=$M/src/main/java/com/elysium/core/command/ElysiumCommand.java
L=$M/src/main/resources/assets/elysium/lang/en_us.json
SNAP=/tmp/negtest_harvest_audit_snapshot.tgz
pass=0; fail=0

tar czf $SNAP -C /tmp Elysium-mono/elysium-core

out() { (cd $ROOT && python3 elysium-core/validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, 'anchor not found in ' + p
open(p, 'w', encoding='utf-8', newline='\n').write(s.replace(old, new, 1))
" "$@"; }
restore() { rm -rf $M; tar xzf $SNAP -C /tmp; }

echo "the harvest audit asks the game"
edit $A '            if (state.requiresCorrectToolForDrops()) {' '            if (true) {'
expect "an audit that no longer asks which blocks need a tool" "no longer asks which blocks need a tool"

edit $A '                if (!(state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_AXE)' '                if (!(state.is(BlockTags.MINEABLE_WITH_AXE) || state.is(BlockTags.MINEABLE_WITH_AXE)'
expect "an audit that never asks about the pickaxe" "no longer asks whether a pickaxe is tagged"

edit $A '            if (item != Items.AIR && state.getDestroySpeed(EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO) < 0.0F) {' '            if (false) {'
expect "an audit that never asks whether a placed block can be broken" "no longer asks whether a placed block can be broken"

edit $C 'HarvestAudit.run(context.getSource())' '0'
expect "an audit nobody can run" "is not registered, so the live suite cannot ask the game"

echo "and writes the whole list to a file"
edit $A '        writeList(source, untagged, unbreakable);' ' '
expect "an answer that names a dozen and keeps the rest" "no longer writes its whole list to a file"

edit $A '            Files.write(file, lines, StandardCharsets.UTF_8);' '            Files.write(file, List.of(lines.get(0)), StandardCharsets.UTF_8);'
expect "a list file holding the count and no names" "no longer writes its whole list to a file"

edit $A '    public static final String LIST_FILE = "logs/harvest-audit.txt";' '    public static final String LIST_FILE = "harvest-audit.txt";'
expect "a message that sends the reader to the wrong file" "do not name the same file"

echo
echo "restoring and re-checking"
restore
if (cd $ROOT && python3 elysium-core/validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
rm -f $SNAP
echo "harvest audit: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
