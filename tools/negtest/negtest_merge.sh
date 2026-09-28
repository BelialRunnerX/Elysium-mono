#!/usr/bin/env bash
# The mod list's emblem and credits, taken away one at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
SNAP=/tmp/merge_snap.tar
M=$ROOT/elysium-arcana/src/main/resources/META-INF/neoforge.mods.toml
P=$ROOT/elysium-arcana/gradle.properties
E=$ROOT/elysium-lib/src/main/resources/elysium_logo.png
pass=0; fail=0
(cd $ROOT && tar cf $SNAP check_merge.py elysium-arcana/src/main/resources/META-INF/neoforge.mods.toml elysium-arcana/gradle.properties elysium-lib/src/main/resources/elysium_logo.png)
restore() { (cd $ROOT && tar xf $SNAP); }
out() { (cd $ROOT && timeout 300 python3 check_merge.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:60] + '\n')
    sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "a mod list square left blank"
edit $M 'logoFile="elysium_logo.png"' 'logoFile="nothing.png"'
expect "a manifest naming no emblem" "names no emblem"

echo "credits dropped from a manifest"
edit $M 'credits="${mod_credits}"' 'credits=""'
expect "a manifest with no credits" "carries no credits"

echo "credits the properties never define"
edit $P 'mod_credits=' 'mod_credit_s='
expect "properties with no mod_credits" "defines no mod_credits"

echo "an emblem that is not there"
rm -f $E
expect "no emblem in the library" "has no emblem"

echo "an emblem in a colour the palette has not got"
python3 -c "
from PIL import Image
im = Image.open('$E').convert('RGBA'); im.putpixel((128, 128), (255, 0, 0, 255)); im.save('$E')"
expect "an emblem off the palette" "outside the interface palette"

echo "an emblem the wrong size"
python3 -c "
from PIL import Image
Image.open('$E').convert('RGBA').resize((128, 128)).save('$E')"
expect "an emblem not 256 square" "not the 256 square"

g="$(out)"
if [[ "$g" == *"an emblem and credits on every manifest"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "merge: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
