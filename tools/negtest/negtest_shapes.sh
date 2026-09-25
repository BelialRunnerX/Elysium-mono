#!/usr/bin/env bash
# Multiblocks: what a shape is, and the four things one has to be.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-industry
S=$M/src/main/java/com/elysium/industry/rules/ShapeRules.java
F=$M/src/main/java/com/elysium/industry/rules/FurnaceRules.java
B=$M/src/main/java/com/elysium/industry/block/FurnaceBlock.java
R=$M/src/main/java/com/elysium/industry/registry/IndustryBlocks.java
G=$M/tools/gen_industry.py
SNAP=/tmp/shapes_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 400 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit is made."
  (cd $M && timeout 400 python3 validate.py 2>&1 | tail -5)
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-industry)
restore() { rm -rf $ROOT/elysium-industry && (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
gexpect() { (cd $M && python3 tools/gen_industry.py >/dev/null 2>&1); expect "$@"; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "a shape recognised in every way it can be stood up"
edit $S '        for (int handed = 0; handed < 2; handed++) {' '        for (int handed = 0; handed < 1; handed++) {'
expect "a shape that is never mirrored, so a machine built left-handed is refused" "one arrangement mirrored are not reported"

edit $S '            for (int turn = 0; turn < 4; turn++) {' '            for (int turn = 0; turn < 1; turn++) {'
expect "a shape that is never turned, so only one facing works" "turned a quarter turn are not reported"

edit $S '                    made[layer][column][deep - 1 - row] = grid[layer][row][column];' '                    made[layer][column][row] = grid[layer][row][column];'
expect "a quarter turn that is a transpose rather than a turn" "two quarter turns are not a half turn"

edit $S '                    made[layer][row][wide - 1 - column] = grid[layer][row][column];' '                    made[layer][row][column] = grid[layer][row][column];'
expect "a mirror that mirrors nothing" "a mirror does not put west where east was"

echo "a refusal that says which block"
edit $S '                        return "at layer " + layer + ", row " + row
                                + ", column " + column + " there should be "' '                        return "somewhere there should be "'
expect "a refusal that will not say where" "does not name where it went wrong"

edit $S '                    if (found != wanted) {' '                    if (false) {'
expect "a wrongAt that never finds anything wrong" "says nothing is wrong with a build"

edit $S '                    if (want[layer][row][column] != grid[layer][row][column]) {
                        return false;
                    }' '                    if (false) {
                        return false;
                    }'
expect "a matcher that accepts anything" "is accepted with the block at"

echo "no two machines out of one pile of bricks"
edit $F '            List.of("BBB\nBBB\nBBB",
                    "BBB\nB B\nBXB",
                    "BBB\nB B\nBBB",
                    "BBB\nBBB\nBBB"),' '            List.of("BBB\nBBB\nBBB",
                    "BBB\nB B\nBXB",
                    "BBB\nBBB\nBBB"),
'
edit $F '            Map.of('"'"'B'"'"', BLAST_BRICK, ShapeRules.CONTROLLER, "blast_furnace"));' '            Map.of('"'"'B'"'"', COKE_BRICK, ShapeRules.CONTROLLER, "blast_furnace"));'
expect "two machines that are the same pile of bricks" "can be built by accident while building the other"

edit $S '                if (already != null && !already.equals(shape.id())) {' '                if (false) {'
expect "a collision check that cannot report a collision" "are not reported, so a machine could be built by accident"

echo "a shape that is a shape at all"
edit $S '        if (controllers != 1) {' '        if (false) {'
expect "a shape check that cannot report a machine with no controller" "a shape with no controller is not reported"

edit $S '            if (!shape.key().containsKey(part)) {' '            if (false) {'
expect "a shape check that cannot report a mark nothing names" "a mark its key does not name is not reported"

edit $S '            if (!used.contains(part)) {' '            if (false) {'
expect "a shape check that cannot report a key entry nothing uses" "names something it never uses is not reported"

edit $S '                if (row.length() != columns) {' '                if (false) {'
expect "a shape check that cannot report a ragged row" "a ragged shape is not reported"

edit $S '        if (blocksIn(shape) < 2) {' '        if (false) {'
expect "a shape check that cannot report a one-block multiblock" "a one-block multiblock is not reported"

echo "everything comes back"
edit $S '                    made.merge(block, 1, Integer::sum);' '                    made.merge(block, 1, (a, b) -> a);'
expect "a bill that counts each kind of brick once" "so breaking one would not give everything back"

echo "the jobs the bricks do"
edit $F '            new Firing("blast_furnace", "minecraft:iron_ingot", 1,
                    "elysiumindustry:steel_ingot", 1, BLASTING_TICKS));' '            new Firing("blast_furnace", "minecraft:iron_ingot", 1,
                    "elysiumindustry:ferrite", 1, BLASTING_TICKS));'
expect "a brick machine making something nothing registers" "which this module does not make"

edit $F '            if (!named.contains(one.shape())) {' '            if (false) {'
expect "a firing check that cannot report a job in no machine" "a job in a machine this module does not have is not reported"

edit $F '            if (!busy.contains(shape.id())) {' '            if (false) {'
expect "a firing check that cannot report a machine with nothing to do" "a machine with nothing to fire is not reported"

edit $F '        if (cokeBurn <= coalBurn) {' '        if (false) {'
expect "a coke check that cannot report coke worth no more than coal" "coke worth exactly what the coal was worth is not reported"

edit $F '        if (cokeBurn >= bestBurn) {' '        if (false) {'
expect "a coke check that cannot report coke beating a refinery" "coke that beats the thing a refinery makes is not reported"

echo "the block a player prods"
edit $B '                if (ShapeRules.sameAs(want, found)) {' '                if (true) {'
expect "a controller that decides for itself that the bricks are right" "rather than asking the arithmetic"

edit $B '            wrong = best == null ? "this is not a shape anything can stand in"
                    : best;' '            wrong = "no";'
expect "a controller that throws away what it found wrong" "the one thing this block exists to do"

edit $B '            for (char[][][] want : ShapeRules.orientations(shape)) {' '            for (char[][][] want : java.util.List.of(ShapeRules.gridOf(shape))) {'
expect "a controller that tries one orientation" "a machine built facing the wrong way"

echo "the world the bricks are actually in"
rm -f $M/src/main/resources/data/elysiumindustry/recipe/coke_oven.json
expect "a multiblock controller with no recipe" "has no crafting recipe"

rm -f $M/src/main/resources/assets/elysiumindustry/models/block/blast_brick.json
expect "a brick with no model" "is a purple cube"

edit $M/src/main/java/com/elysium/industry/registry/IndustryItems.java '                        for (DeferredItem<? extends Item> item : BRICKWORK) {
                            output.accept(item.get());
                        }
' ''
expect "brickwork nobody can find in the creative tab" "the tab does not reach for that roster"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all industry checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | tail -3 | head -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "shapes: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
