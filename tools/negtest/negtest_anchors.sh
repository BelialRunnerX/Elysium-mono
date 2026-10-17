#!/usr/bin/env bash
# Anchors: machines whose product is that the world keeps running. One break at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-kinetics
A=$M/src/main/java/com/elysium/kinetics/rules/AnchorRules.java
B=$M/src/main/java/com/elysium/kinetics/block/AnchorBlock.java
R=$M/src/main/java/com/elysium/kinetics/registry/KineticBlockEntities.java
G=$M/tools/gen_kinetics.py
SNAP=/tmp/anchors_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 400 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit is made."
  (cd $M && timeout 400 python3 validate.py 2>&1 | tail -5)
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-kinetics)
restore() { rm -rf $ROOT/elysium-kinetics && (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
gexpect() { (cd $M && python3 tools/gen_kinetics.py >/dev/null 2>&1); expect "$@"; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the one thing a chunk loader must not be"
edit $A '    public static final float RPM_PER_ROW = 48.0F;' '    public static final float RPM_PER_ROW = 6.0F;'
expect "a widest anchor a water wheel turns on its own" "a block you place and forget"

edit $A '    public static final float TORQUE_PER_ROW = 6.0F;' '    public static final float TORQUE_PER_ROW = 400.0F;'
expect "a cheapest anchor nothing in the age can drive" "the cheap anchor is not a cheap anchor"

edit $A '        if (drivenBySomething(loadAt(last.widest()))) {' '        if (false) {'
expect "a reach check that cannot report a place-and-forget anchor" "is not reported, so the biggest anchor in the pack could be"

edit $A '        if (!drivenBySomething(loadAt(first.widest()))) {' '        if (false) {'
expect "a reach check that cannot report an anchor nothing drives" "a first anchor nothing in the age can drive is not reported"

echo "what a width costs"
edit $A '        int across = acrossAt(radius);
        return new KineticRules.Load(TORQUE_PER_ROW * across,
                RPM_PER_ROW * across);' '        int chunks = chunksAt(radius);
        return new KineticRules.Load(TORQUE_PER_ROW * chunks,
                RPM_PER_ROW * chunks);'
expect "an anchor priced by its area on both numbers, so one width is the only one worth building" "costs something other than a chunk at radius 0"

edit $A '            if (Math.abs(here - first) > first * 1.0E-3F) {' '            if (false) {'
expect "a cost check that cannot report one width being the only one worth building" "priced by their area on both numbers are not reported"

edit $A '        if (byRadius.size() < 2) {' '        if (false) {'
expect "a cost check that cannot report an anchor with nothing to choose" "is not reported, so the panel could be a screen with one button"

edit $A '        if (!(first > 0.0F)) {' '        if (false) {'
expect "a cost check that cannot report an anchor that costs nothing" "an anchor that costs nothing at all is not reported"

edit $A '    public static int acrossAt(int radius) {
        return 2 * radius + 1;' '    public static int acrossAt(int radius) {
        return radius + 1;'
expect "a square whose width is not twice its radius and one" "a radius of two is not twenty-five"

echo "the ladder of two"
edit $A '            new Anchor("wide_anchor", WIDEST, KineticRules.Shaft.STEEL));' '            new Anchor("wide_anchor", WIDEST, KineticRules.Shaft.BRASS));'
expect "an anchor made of a metal the speed it needs shears" "breaks itself the first time it works"

edit $A '            if (KineticRules.shears(one.made(),
                    loadAt(one.widest()).leastRpm())) {' '            if (false) {'
expect "a roster check that cannot report an anchor that shears its own shaft" "is not reported, so it could be a block that breaks itself"

edit $A '            if (one.widest() <= under.widest()) {' '            if (false) {'
expect "a roster check that cannot report an anchor holding no more than the one below" "holding no more than the one below it is not reported"

edit $A '            if (!seen.add(one.id())) {' '            if (false) {'
expect "a roster check that cannot report two anchors under one name" "two anchors under one name are not reported"

echo "the three ways a chunk loader leaks"
edit $B '            } else if (!want && held >= 0) {
                letGo(server);' '            } else if (false) {
                letGo(server);'
expect "an anchor that does not let go when its line stops" "a block you place and forget after all"

edit $B '            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                letGo(server);
            }
            super.setRemoved();' '            super.setRemoved();'
expect "an anchor that does not let go when it is broken" "stay awake with nothing left in the world asking for them"

edit $B '            held = -1;
        }

        @Override
        protected void saveAdditional' '            held = tag.getInt("held");
        }

        @Override
        protected void saveAdditional'
expect "an anchor that reads back the ticket it was holding" "a ticket outlives the block that asked for it"

edit $R '                        helper.getBlockTickets().forEach((pos, tickets) ->
                                helper.removeAllTickets(pos));' '                        helper.getBlockTickets().forEach((pos, tickets) -> {
                        });'
expect "a world that comes back up still holding what it left behind" "holds its chunks for ever"

edit $R '            event.register(ANCHORS);' ''
expect "a ticket controller nothing registers" "keeps nothing awake at all"

echo "the block a player actually places"
edit $B '            IntegerProperty.create("radius", 0, AnchorRules.WIDEST);' '            IntegerProperty.create("radius", 0, 2);'
expect "an anchor whose widths are not read off the rules" "can drift apart"

edit $B '                ? AnchorRules.comparator(anchor.holding(), state.getValue(RADIUS)) : 0;' '                ? (anchor.holding() ? 15 : 0) : 0;'
expect "an anchor working out its own comparator reading" "a number no harness has ever executed"

edit $B '            return AnchorRules.loadAt(getBlockState().getValue(RADIUS));' '            return AnchorRules.loadAt(0);'
expect "an anchor that draws the same whatever it is holding" "widening it could be free"

echo "the world the anchors are actually in"
# The generator spans the RADIUS property (0..WIDEST) for every anchor since
# ec8a3914; a blockstate that stops at radius 0 is the anchor drawn at one width.
edit $G '        for radius in range(WIDEST + 1):' '        for radius in range(0 + 1):'
gexpect "an anchor drawn at one width and settable to three" "a width the game draws as a missing model"

rm -f $M/src/main/resources/data/elysiumkinetics/recipe/wide_anchor.json
expect "an anchor with no recipe" "wide_anchor has no recipe"

edit $M/src/main/java/com/elysium/kinetics/registry/KineticItems.java '        made.addAll(ANCHORS.values());
' ''
expect "an anchor nobody can find in the creative tab" "is not in the creative tab"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all kinetics checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | tail -3 | head -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "anchors: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
