#!/usr/bin/env bash
# The feature catalogue's two files, made stale one way at a time: each case
# must be caught by check_features.py naming the line that was changed.
set -uo pipefail
ROOT=/tmp/Elysium-mono
MD=$ROOT/docs/FEATURES.md
JS=$ROOT/docs/features.json
CAT=$ROOT/tools/catalogue.py
SNAP=/tmp/features_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP docs/FEATURES.md docs/features.json tools/catalogue.py)
restore() { (cd $ROOT && tar xf $SNAP); }
out() { (cd $ROOT && timeout 900 python3 check_features.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
# newline='\n': a Windows text-mode write turns the whole file CRLF, and the
# check would then be caught on line 1 for the wrong reason
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p, 'w', encoding='utf-8', newline='\n').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }
crlf() { python3 -c "
import sys
p = sys.argv[1]
s = open(p, encoding='utf-8', newline='').read()
open(p, 'w', encoding='utf-8', newline='\r\n').write(s)
" "$@"; }

echo "a row of FEATURES.md edited by hand"
edit $MD '| `auto_trader` | Auto Trader |' '| `auto_trader` | Auto Trader, renamed by hand |'
expect "a hand-edited FEATURES.md row" "Auto Trader, renamed by hand"

echo "a features.json entry the tree no longer has"
edit $JS '     "Black Copper Chimes"' '     "Black Copper Wind Chimes"'
expect "a stale features.json name" "Black Copper Wind Chimes"

echo "FEATURES.md saved with Windows line endings"
crlf $MD
expect "a CRLF FEATURES.md" "with a CRLF ending"

echo "the catalogue failing"
edit $CAT '    modules = catalogue()
    total = write(modules, docs)' '    modules = catalogue()
    raise RuntimeError("negtest: the catalogue cannot read the tree")
    total = write(modules, docs)'
expect "a catalogue that crashes" "tools/catalogue.py failed"

echo "the catalogue exiting 0 having written nothing"
edit $CAT '    total = write(modules, docs)' '    total = {}'
expect "a catalogue that writes nothing" "exited 0 and wrote no FEATURES.md"

echo "the catalogue writing a features.json with nothing in it, or nothing readable"
edit $CAT '        json.dump(modules, handle, indent=1, ensure_ascii=False)' \
          '        json.dump({}, handle, indent=1, ensure_ascii=False)'
expect "a catalogue that writes an empty features.json" "wrote a features.json with no modules in it"

edit $CAT '        json.dump(modules, handle, indent=1, ensure_ascii=False)' \
          '        handle.write(json.dumps(modules, indent=1, ensure_ascii=False)[:-1])'
expect "a catalogue that writes a features.json cut short" "wrote a features.json that does not parse"

echo "restoring and re-checking"
restore
if out | grep -q "are what tools/catalogue.py writes"; then echo "  tree restored clean"; else echo "  RESTORE FAILED"; fail=$((fail+1)); fi
rm -f $SNAP
echo "features: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
