#!/usr/bin/env bash
# The spaceport, broken one way at a time.
#
# Every check the port added is deliberately defeated here and the suite fails
# if the validator or the harness does not notice. A check nobody has seen fail
# is a check nobody has seen.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-colonies
S=$M/src/main/java/com/elysium/colonies
R=$M/src/main/resources
SNAP=/tmp/port_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-colonies)
restore() { rm -rf $ROOT/elysium-colonies && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, 'anchor not found in ' + p
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "the blocks a port is made of"
edit $S/registry/PortBlocks.java 'BLOCKS.register(
            "elevator_tether",' 'BLOCKS.register(
            "elevator_cable",'
expect "a block of the structure that is never registered" "is not registered as a block"

edit $S/registry/PortBlocks.java 'ITEMS.register("elevator_tether",' 'ITEMS.register("elevator_cable",'
expect "eighty blocks nobody can carry" "has no item, so it can be placed by a command"

rm $R/assets/elysiumcolonies/blockstates/elevator_counterweight.json
expect "a block with no blockstate" "has no blockstate"

rm $R/data/elysiumcolonies/loot_table/blocks/elevator_tether.json
expect "eighty blocks of tether that vanish when mined" "has no drop"

edit $R/data/elysiumcolonies/recipe/elevator_tether.json '"minecraft:iron_ingot"' '"elysium:aetherium_ingot"'
expect "a spaceport only a full Elysium install can build" "buildable out of the base game"
restore
echo "subset: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
