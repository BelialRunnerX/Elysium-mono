#!/usr/bin/env bash
# The books of a grid: what it made against what it handed out. One break at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-industry
I=$M/src/main/java/com/elysium/industry/rules/IndustryRules.java
L=$M/src/main/java/com/elysium/industry/block/GridLine.java
SNAP=/tmp/books_snap.tar
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
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the one thing a grid may not do"
edit $I '        return new Books(made, flow.fromReserve(), made - arrived, used,
                flow.toReserve(), spare);' '        return new Books(made, flow.fromReserve(), 0.0F, used,
                flow.toReserve(), spare);'
expect "a grid that forgets what the wire took" "do not add up"

edit $I '        float spare = Math.max(0.0F,
                arrived + flow.fromReserve() - used - flow.toReserve());' '        float spare = 0.0F;'
expect "a grid that throws away its surplus rather than counting it" "do not add up"

edit $I '        float used = grid.burned() ? 0.0F
                : Math.min(grid.supply(), grid.demand());' '        float used = grid.burned() ? 0.0F
                : grid.demand();'
expect "a grid whose machines are counted as fed when they are starved" "do not add up"

edit $I '            float came = made + lent;
            float went = lost + used + stored + spare;' '            float came = made + lent;
            float went = came;'
expect "books that are told they balance rather than asked" "cannot say no"

edit $I '            return size <= 0.0F ? 0.0F : Math.abs(came - went) / size;' '            return 0.0F;'
expect "an apart that is always nothing" "cannot say no"

echo "the loss the voltage ladder exists for"
edit $I '            arriving.add(new Generator(
                    delivered(wire, one.what().output(), furthest),
                    one.what().voltage()));' '            arriving.add(new Generator(one.what().output(),
                    one.what().voltage()));'
expect "a settle that stops charging the loss its own books still count" "do not add up"

edit $I '        int furthest = 0;
        for (Placed<Machine> one : loads) {
            furthest = Math.max(furthest, one.blocks());
        }' '        int furthest = 0;
        for (Placed<Machine> one : loads) {
            furthest = Math.max(furthest, 0);
        }'
expect "a grid that only measures to its generators, so a distant machine is free" "so the voltage ladder is decoration"

edit $I '    public static final int MOST_FE = Voltage.EXTREME.capacity();' '    public static final int MOST_FE = Voltage.EXTREME.capacity();
    // a stepUp that does not step up'
edit $I '            return this == all[all.length - 1] ? this : all[ordinal() + 1];' '            return this == all[0] ? this : all[ordinal() - 1];'
expect "a step up the voltage ladder that steps down" "lost more, so the ladder runs backwards"

echo "the block that walks the wire"
edit $L '            generators.add(new IndustryRules.Placed<>(generator, member.blocks()));' '            generators.add(new IndustryRules.Placed<>(
                    new IndustryRules.Generator(IndustryRules.delivered(
                            found.weakest(), generator.output(), 0),
                            generator.voltage()), member.blocks()));'
expect "a block that charges the distance loss itself again" "applied where no harness can sweep it"

edit $L '                IndustryRules.along(generators, machines, found.weakest(), bank);' '                IndustryRules.settle(java.util.List.of(), java.util.List.of(),
                        found.weakest(), bank);'
expect "a grid settled without its distances" "two different models"

echo "the rules the books are written in"
edit $I '    public static Books books(List<Placed<Generator>> sources,' '    public static Books ledger(List<Placed<Generator>> sources,'
expect "the books taken out from under the validator" "the sweep that proves they balance has nothing to sweep"

edit $I '    public record Placed<T>(T what, int blocks) {' '    public record Sited<T>(T what, int blocks) {'
expect "a placement record renamed out from under the validator" "cannot be worked out where the rest of the arithmetic is"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all industry checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | tail -3 | head -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "books: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
