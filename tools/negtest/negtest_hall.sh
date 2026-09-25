#!/usr/bin/env bash
# The reactor hall: a reactor whose size is a decision. One break at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-atomics
H=$M/src/main/java/com/elysium/atomics/rules/HallRules.java
B=$M/src/main/java/com/elysium/atomics/block/ReactorPortBlock.java
G=$M/tools/gen_atomics.py
SNAP=/tmp/hall_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 400 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit is made."
  (cd $M && timeout 400 python3 validate.py 2>&1 | tail -5)
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-atomics)
restore() { rm -rf $ROOT/elysium-atomics && (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
gexpect() { (cd $M && python3 tools/gen_atomics.py >/dev/null 2>&1); expect "$@"; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the trade that makes a size a decision"
edit $H '    public static int mostChannelsOn(int side) {
        return side * side;' '    public static int mostChannelsOn(int side) {
        return side * side * side;'
expect "cooling that keeps up with the rods, so bigger is simply better" "the throttle is a slider nobody has to touch"

edit $H '        return AtomicRules.BARE_LOSS + AtomicRules.COOLING_PER_CHANNEL * used;' '        return AtomicRules.BARE_LOSS + AtomicRules.COOLING_PER_CHANNEL * used
                / Math.max(1, rodsIn(side));'
expect "cooling divided by the rods, so the big halls are rungs nobody builds" "so nobody would build it"

edit $H '    public static final float PER_ROD = AtomicRules.REACTOR.mostOutput() / 4.0F;' '    public static final float PER_ROD = AtomicRules.REACTOR.mostOutput() / 64.0F;'
expect "rods so weak the largest hall never needs throttling" "a slider nobody has to touch"

edit $H '            if (here[1] > under[1] + 1.0E-4F) {' '            if (false) {'
expect "a ladder check that cannot report bigger being free" "makes more per rod the bigger it gets is not reported"

edit $H '            if (here[0] <= under[0]) {' '            if (false) {'
expect "a ladder check that cannot report a rung nobody builds" "whose bigger rung makes less altogether is not reported"

edit $H '        if (safeThrottleWith(largest, mostChannelsOn(largest), perRod) >= 1.0F) {' '        if (false) {'
expect "a plumbing check that cannot report a throttle nobody touches" "whose largest hall never needs throttling is not reported"

edit $H '            if (safeThrottleWith(side, 0, perRod) >= 1.0F) {' '            if (false) {'
expect "a plumbing check that cannot report a hall safe with no cooling at all" "no cooling at all are not reported"

echo "the throttle solved rather than searched"
edit $H '        float room = (AtomicRules.SAFE - AtomicRules.AMBIENT)
                * coolingOf(side, channels);' '        float room = (AtomicRules.MELTDOWN - AtomicRules.AMBIENT)
                * coolingOf(side, channels);'
expect "a safe throttle solved against meltdown rather than against safe" "run at the throttle solved as safe settles at"

edit $H '        return Math.max(0.0F, Math.min(1.0F, room / flatOut));' '        return Math.max(0.0F, Math.min(1.0F, room / flatOut)) * 0.5F;'
expect "a safe throttle solved well under where it could be" "is still safe a notch above the throttle solved as its limit"

echo "the box"
edit $H '        int hollow = side - 2;' '        int hollow = side - 1;'
expect "a box whose inside is bigger than its outside allows" "rod(s) and holds"

edit $H '                    boolean wall = layer == 0 || layer == side - 1
                            || row == 0 || row == side - 1
                            || column == 0 || column == side - 1;' '                    boolean wall = layer == 0 || layer == side - 1
                            || row == 0 || row == side - 1;'
expect "a box with two faces missing" "and its wall is"

edit $H '                    boolean port = layer == 1 && row == side - 1
                            && column == side / 2;' '                    boolean port = false;'
expect "a hall with no port anywhere in it" "has no port"

edit $H '    public static final int LARGEST = 7;' '    public static final int LARGEST = 3;'
expect "a range with one size in it" "so there is nothing to choose"

edit $H '        if (smallest < 3) {' '        if (false) {'
expect "a range check that cannot report a box with nothing inside it" "a smallest hall with nothing inside it is not reported"

edit $H '        if (largest <= smallest) {' '        if (false) {'
expect "a range check that cannot report a range that is not a range" "a range with one size in it is not reported"

echo "the block a player prods"
edit $B '                    if (ShapeRules.sameAs(want, found)) {' '                    if (true) {'
expect "a port that decides for itself that the blocks are a hall" "rather than asking the arithmetic"

edit $B '            wrong = best;' '            wrong = "no";'
expect "a port that throws away what it found wrong" "is told nothing at all"

edit $B '                for (char[][][] want : ShapeRules.orientations(shape)) {' '                for (char[][][] want : java.util.List.of(ShapeRules.gridOf(shape))) {'
expect "a port that tries one orientation" "a hall built facing the wrong way"

edit $B '            port.temperature = AtomicRules.nextTemperature(port.temperature, heat,
                    HallRules.coolingOf(side, port.channels));' '            port.temperature = AtomicRules.nextTemperature(port.temperature, heat,
                    AtomicRules.BARE_LOSS);'
expect "a hall that sheds heat at a rate of its own" "a number no harness has ever executed"

edit $H '    public static float safeThrottle(int side, int channels) {' '    public static float safeLimit(int side, int channels) {'
edit $H '        return outputAt(side, safeThrottle(side, channels));' '        return outputAt(side, safeLimit(side, channels));'
edit $B '                            HallRules.safeThrottle(side, channels) * 100.0F));' '                            HallRules.safeLimit(side, channels) * 100.0F));'
expect "the solved throttle taken out from under the validator" "a slider they find by melting one"

echo "the world the hall is actually in"
rm -f $M/src/main/resources/data/elysiumatomics/recipe/fuel_rod.json
expect "a fuel rod with no recipe" "has no crafting recipe"

rm -f $M/src/main/resources/assets/elysiumatomics/models/block/reactor_casing.json
expect "casing with no model" "is a purple cube"

edit $M/src/main/java/com/elysium/atomics/registry/AtomicItems.java '                        for (DeferredItem<? extends Item> item : HALL) {
                            output.accept(item.get());
                        }
' ''
expect "a hall nobody can find in the creative tab" "does not reach for the reactor hall"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all atomics checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | tail -3 | head -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "hall: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
