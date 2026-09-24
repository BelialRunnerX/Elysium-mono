#!/usr/bin/env bash
# The chooser's filter, broken one way at a time.
#
# Four rules in check_ui.py, all about ElysiumChooser rather than any one
# screen, because both failures a filter brings are invisible until somebody
# types something — which is to say invisible to whoever wrote it.
set -uo pipefail
ROOT=/tmp/Elysium-mono
C=$ROOT/elysium-lib/src/main/java/com/elysium/lib/client/ElysiumChooser.java
SNAP=/tmp/chooser_snap.java
pass=0; fail=0
cp "$C" "$SNAP"
restore() { cp "$SNAP" "$C"; }
out() { (cd $ROOT && timeout 200 python3 check_ui.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR: ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the row you clicked, and the row you got"
edit $C '            int theirs = surviving.get(index);' '            int theirs = index;'
expect "a pressed row not mapped back through the filter" "maps back through the filter"

edit $C 'b -> chose(theirs)' 'b -> chose(index)'
expect "a mapping computed and then thrown away" "computed and thrown away"

echo "the page, and the count under it"
edit $C '        typed = now;
        first = 0;' '        typed = now;
        first = first;'
expect "narrowing a list while paged through it" "returns to the first page"

echo "the panel, and the line drawn in it"
edit $C '        int head = ElysiumPalette.ROW + (statusShown() == null ? 0 : ElysiumPalette.ROW)' \
        '        int head = ElysiumPalette.ROW + (status() == null ? 0 : ElysiumPalette.ROW)'
expect "a panel measured against a different line from the one drawn in it" "measured against a different line"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"one interface"* && "$g" != *"PROBLEM"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "ui filter: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
