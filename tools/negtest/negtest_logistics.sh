#!/usr/bin/env bash
# Storage, broken one way at a time.
#
# The two faults worth rehearsing here are the ones a player would meet as
# "my stuff is gone" and "the server stopped responding", and neither leaves
# a stack trace pointing anywhere useful.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-logistics
R=$M/src/main/java/com/elysium/logistics/rules/LogisticRules.java
SNAP=/tmp/logistics_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-logistics)
restore() { rm -rf $ROOT/elysium-logistics && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 600 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "items, which must neither appear nor vanish"
edit $R '        return Math.max(0, Math.min(Math.max(0, offered), Math.max(0, room)));' '        return Math.max(0, offered);'
expect "a store that takes more than it has room for" "more than it had room for"

edit $R '        return Math.max(0, offered) - accepted(offered, room);' '        return Math.max(0, offered) - Math.max(0, room);'
expect "a leftover worked out separately from what was taken" "separately from what the store took"

edit $R '        return Math.max(0, offered) - accepted(offered, room);' '        return 0;'
expect "a store that swallows whatever it refused" "lost items between the hand and the store"

edit $R '    public static boolean voids(boolean upgraded) {
        return upgraded;' '    public static boolean voids(boolean upgraded) {
        return true;'
expect "a drawer nobody upgraded destroying what will not fit" "must be asked for"

echo "compression, which must be exact"
edit $R '    public static final int COMPRESSION = 9;' '    public static final int COMPRESSION = 8;'
expect "compression that is not what a crafting grid does" "is not nine"

edit $R '        return new long[] {smallest / divisor, smallest % divisor};' '        return new long[] {smallest / divisor, 0};'
expect "a drawer that quietly eats the remainder" "does not read back as what was put into it"

edit $R '        long divisor = inSmallest(1, steps);' '        long divisor = inSmallest(1, steps + 1);'
expect "a pool read back at the wrong step of the chain" "remainder is bigger than the thing"

echo "the planner, which must always come back"
edit $R '        if (path.contains(want)) {' '        if (false) {'
expect "a planner that follows a circular recipe set" "already inside what it is planning"

edit $R '        if (depth > MOST_DEPTH) {' '        if (false) {'
expect "a planner with no depth limit" "no depth limit"

edit $R '        if (runs.size() >= MOST_STEPS) {' '        if (false) {'
expect "a plan that may contain any number of steps" "any number of steps"

edit $R '        long times = (count + step.makes() - 1) / step.makes();' '        long times = count / step.makes();'
expect "a planner that rounds runs down and comes up short" "does not round runs up"

# This one does not fail, it *hangs* — which is the point, and is why the
# validator now runs the harness with a clock on it. A rules file is meant to
# be arithmetic, and arithmetic finishes.
edit $R '            if (!seen.add(here)) {
                continue;
            }' '            if (false) {
                continue;
            }'
expect "a reachability walk with no memory, which hangs rather than failing" "can loop forever"

edit $R '        for (String name : stuck) {
            if (reaches(steps, name, name)) {
                caught.add(name);
            }
        }' '        caught.addAll(stuck);'
expect "a cycle check that blames everything downstream of the fault" "rather than the cycle"

echo "the network, and the grid it hangs off"
edit $R '            return new IndustryRules.Machine(devices * DEVICE_DRAW,' '            return new IndustryRules.Machine(DEVICE_DRAW,'
expect "a network that costs the same however big it is" "not its devices times what one costs"

edit $R '            return BANDWIDTH * Math.min(1.0F, share);' '            return BANDWIDTH * share;'
expect "a network made faster than full by over-building the grid" "faster than full"

edit $R '            if (overloaded() || share <= 0.0F) {' '            if (share <= 0.0F) {'
expect "an overloaded controller that works anyway" "still works"

edit $R '    public static final float DEVICE_DRAW = 1.5F;' '    public static final float DEVICE_DRAW = 4.0F;'
expect "a full network no single turbine can run" "is not true and there is no simple sentence"

edit $R '    public static final float DEVICE_DRAW = 1.5F;' '    public static final float DEVICE_DRAW = 0.1F;'
expect "a network so cheap it never registers as a load" "never registers as a load"

edit $R '    public static final int DRAWER_STEP = 2;' '    public static final int DRAWER_STEP = 2;
    public static final int UNUSED_STEP = 3;'
edit $R '    public static final Drawer STEEL_DRAWER = new Drawer(
            BRASS_DRAWER.kinds() * DRAWER_STEP, BRASS_DRAWER.stacks() * DRAWER_STEP);' '    public static final Drawer STEEL_DRAWER = new Drawer(
            BRASS_DRAWER.kinds() * UNUSED_STEP, BRASS_DRAWER.stacks() * DRAWER_STEP);'
expect "a drawer ladder whose rungs are different sizes" "not the same step at every rung"

edit $R '    public static final Drawer TIMBER_DRAWER = new Drawer(1, 32);' '    public static final Drawer TIMBER_DRAWER = new Drawer(3, 32);'
expect "a first drawer that already holds three things" "arrives already blurred"

echo "independence, and a second idea of power"
edit $R 'import com.elysium.industry.rules.IndustryRules;' 'import com.elysium.industry.block.GridLine;
import com.elysium.industry.rules.IndustryRules;'
expect "a rules class reaching into another module's blocks" "only into other rules"

edit $M/src/main/java/com/elysium/logistics/ElysiumLogistics.java \
  'public static final String MODID = "elysiumlogistics";' \
  'public static final String MODID = "elysiumlogistics";
    static final Class<?> UP = com.elysium.foundry.Anything.class;'
expect "logistics reaching up into a later module" "dependencies run one way"

python3 - <<'PYX'
import pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-logistics/src/main/resources/META-INF/neoforge.mods.toml")
s = p.read_text(encoding="utf-8")
s = s.replace('modId="elysiumindustry"', 'modId="appliedenergistics2"', 1)
p.write_text(s, encoding="utf-8")
PYX
expect "a declared dependency on one of the seventeen" "the grid it hangs off"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all logistics checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "logistics: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
