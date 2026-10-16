#!/usr/bin/env bash
# The recipe-grid check, which the game's own pattern-sliding makes subtler
# than it looks. Against the generator, which is where the check lives.
set -uo pipefail
M=/tmp/Elysium-mono/elysium-atelier
G=$M/tools/gen_atelier.py
SNAP=/tmp/arch3_snap
pass=0; fail=0
rm -rf $SNAP && mkdir -p $SNAP && cp -a $M/tools $SNAP/tools && cp -a $M/src/main/resources $SNAP/resources
restore() { rm -rf $M/tools $M/src/main/resources; cp -a $SNAP/tools $M/tools; cp -a $SNAP/resources $M/src/main/resources; }
gen() { (cd $M && python3 tools/gen_atelier.py 2>&1); }
gexpect() { local w="$2" g; g="$(gen)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "  STALE ANCHOR"; fail=$((fail+1)); restore; return 1; }; }

edit $G '    "steep_roof_outer":      ["M M"],' '    "steep_roof_outer":      ["MM "],' \
  && gexpect "two grids the game slides onto one another" "laid out the same way"

edit $G '    "cornice": "minecraft:brick",' '    "cornice": "minecraft:clay_ball",' \
  && gexpect "two pieces on one grid with the same extra ingredient" "laid out the same way"

edit $G '    while rows and not rows[-1].strip():
        rows.pop()' '    if False:
        rows.pop()' \
  && gexpect "a trimmer that has stopped trimming" "would be missed"

edit $G '    first, last = 0, wide - 1
    while first <= last and all(row[first] == " " for row in rows):
        first += 1' '    first, last = 0, wide - 1
    while first <= last and any(row[first] == " " for row in rows):
        first += 1' \
  && gexpect "a trimmer that trims a column some row happens to leave blank" "laid out the same way"

edit $G '    "sill":                  ["M ", "MM"],' '    "sill":                  ["MM", "MM"],' \
  && gexpect "a grid using more material than the piece is priced at" "the price and the recipe would disagree"

echo
restore
if (cd $M && python3 tools/gen_atelier.py >/dev/null 2>&1); then echo "  tree restored clean"; else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "grids: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
