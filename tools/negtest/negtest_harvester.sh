#!/usr/bin/env bash
# The harvester, broken one way at a time.
set -uo pipefail
cd /tmp/Elysium-mono/elysium-solarpunk
M=src/main/java/com/elysium/solarpunk/machine
pass=0; fail=0
out() { python3 validate.py 2>&1; }
expect() { local n="$1" w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $n"; pass=$((pass+1));
  else echo "  MISSED : $n"; fail=$((fail+1)); fi; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "never destroy what cannot be stored"
cp $M/HarvesterEntity.java /tmp/h.bak
edit $M/HarvesterEntity.java '        if (!SolarpunkOutput.fits(tray, drops)) {
            return false;               // leave it standing until there is room
        }

        BlockState replanted' '        BlockState replanted'
expect "cuts without checking there is room" "never asks whether the harvest will fit"
cp /tmp/h.bak $M/HarvesterEntity.java

# The order matters as much as the presence.
cp $M/HarvesterEntity.java /tmp/h.bak
python3 - <<'PYEOF'
p = "src/main/java/com/elysium/solarpunk/machine/HarvesterEntity.java"
s = open(p).read()
fit = """        if (!SolarpunkOutput.fits(tray, drops)) {
            return false;               // leave it standing until there is room
        }

        BlockState replanted = replantedState(state);
        server.setBlockAndUpdate(target, replanted);
"""
swapped = """        BlockState replanted = replantedState(state);
        server.setBlockAndUpdate(target, replanted);
        if (!SolarpunkOutput.fits(tray, drops)) {
            return false;
        }
"""
assert fit in s
open(p, "w").write(s.replace(fit, swapped, 1))
PYEOF
expect "cuts first and checks afterwards" "before it checks the harvest will fit"
cp /tmp/h.bak $M/HarvesterEntity.java

echo "the seed"
cp $M/HarvesterEntity.java /tmp/h.bak
edit $M/HarvesterEntity.java 'if (!seedPaid && paysForReplant(drop, replanted)) {' \
                             'if (false) {'
expect "replants without paying a seed" "replants without consuming anything"
cp /tmp/h.bak $M/HarvesterEntity.java

echo "the tray is an out-tray"
cp $M/HarvesterEntity.java /tmp/h.bak
edit $M/HarvesterEntity.java '            return stack;
        }

        @Override
        protected void onContentsChanged(int slot) {' '            return super.insertItem(slot, stack, simulate);
        }

        @Override
        protected void onContentsChanged(int slot) {'
expect "a hopper can fill the harvest space" "accepts insertion"
cp /tmp/h.bak $M/HarvesterEntity.java

echo "posting downward"
# Back up the file this actually edits. The first version saved
# HarvesterEntity and restored that afterwards, leaving the sabotage in
# SolarpunkOutput behind — which the suite then reported as RESTORE FAILED
# rather than as a pass, and would have left in the tree if nobody looked.
cp $M/SolarpunkOutput.java /tmp/o.bak
edit $M/SolarpunkOutput.java 'Capabilities.ItemHandler.BLOCK, pos.below(), Direction.UP);' \
                             'Capabilities.ItemHandler.BLOCK, pos.above(), Direction.DOWN);'
expect "pushes up instead of down" "does not post downward"
cp /tmp/o.bak $M/SolarpunkOutput.java

echo "the sweep"
cp $M/HarvesterEntity.java /tmp/h.bak
edit $M/HarvesterEntity.java 'return new BlockPos(wrapped % side - RADIUS, 0, wrapped / side - RADIUS);' \
                             'return new BlockPos(wrapped % side - RADIUS, 0, wrapped / side - RADIUS + 1);'
expect "the sweep misses one edge of the field" "outside a radius of"
cp /tmp/h.bak $M/HarvesterEntity.java

cp $M/HarvesterEntity.java /tmp/h.bak
edit $M/HarvesterEntity.java 'int wrapped = Math.floorMod(index, side * side);' 'int wrapped = index;'
expect "a saved cursor runs off the field" "outside the field"
cp /tmp/h.bak $M/HarvesterEntity.java

cp $M/HarvesterEntity.java /tmp/h.bak
edit $M/HarvesterEntity.java 'public static final int RADIUS = 4;' 'public static final int RADIUS = 40;'
expect "a sweep slower than crops ripen" "slower than crops ripen"
cp /tmp/h.bak $M/HarvesterEntity.java

echo "the cost"
cp $M/HarvesterEntity.java /tmp/h.bak
edit $M/HarvesterEntity.java 'public static final int COST_PER_HARVEST = 60;' 'public static final int COST_PER_HARVEST = 1;'
expect "harvesting is effectively free" "never meaningfully draws power"
cp /tmp/h.bak $M/HarvesterEntity.java

echo
echo "negative tests: $pass caught, $fail missed"
python3 validate.py >/dev/null 2>&1 && echo "restored clean" || echo "RESTORE FAILED"
[ "$fail" -eq 0 ]
