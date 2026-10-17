#!/usr/bin/env bash
# The aquaponics tank and the finished-set edge, broken one way at a time.
set -uo pipefail
cd /tmp/Elysium-mono/elysium-solarpunk
M=src/main/java/com/elysium/solarpunk/machine
B=src/main/java/com/elysium/solarpunk/block
pass=0; fail=0

out() { python3 validate.py 2>&1; }
expect() { local n="$1" w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $n"; pass=$((pass+1));
  else echo "  MISSED : $n"; fail=$((fail+1)); fi; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
assert old in s, 'anchor not found in ' + p
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }
regen() { python3 tools/gen_solarpunk.py >/dev/null 2>&1; }

echo "the finished set"
# The bug the empty case actually had: a second table read as the to-do list.
cp tools/gen_solarpunk.py /tmp/g.bak
cp MACHINES.md /tmp/m.bak
# The marker is the contract, not the heading — so the sabotage is to put the
# marker in front of the working-machines table, which is exactly what a
# careless edit to the template would do.
edit MACHINES.md '## What the machines do' '## What the machines do

<!-- OUTSTANDING: -->'
expect "another table marked as outstanding work" "still a row in MACHINES.md"
cp /tmp/m.bak MACHINES.md

# An orphan stub left behind after a machine graduated.
cp $B/pending/PendingMachineBlock.java /tmp/pmb.bak
# A well-formed stub, so it reaches the orphan check rather than being stopped
# by the "carries no marker" one above it.
cat > $B/pending/AquaponicsTankBlock.java <<'EOF'
package com.elysium.solarpunk.block.pending;

public class AquaponicsTankBlock extends PendingMachineBlock {
    public AquaponicsTankBlock(Properties properties) {
        super(properties, "Grows crops and fish together off one supply of water.");
    }
}
EOF
expect "an orphan stub in block/pending/" "a stub left in it says work is outstanding"
rm -f $B/pending/AquaponicsTankBlock.java

# A translation string nothing can ever show.
cp src/main/resources/assets/elysiumsolarpunk/lang/en_us.json /tmp/lang.bak
python3 -c "
import json
p='src/main/resources/assets/elysiumsolarpunk/lang/en_us.json'
d=json.load(open(p))
d['elysiumsolarpunk.tooltip.pending']='Decorative for now.'
json.dump(d, open(p,'w'), indent=2)
"
expect "a dead tooltip string" "no player can reach"
cp /tmp/lang.bak src/main/resources/assets/elysiumsolarpunk/lang/en_us.json

# Scaffolding tidied away because the list is empty.
cp $B/PendingMachine.java /tmp/pm.bak
rm -f $B/PendingMachine.java
expect "the marker interface deleted" "the marker interface for an unfinished machine"
cp /tmp/pm.bak $B/PendingMachine.java

echo "the tank's siting"
cp $M/AquaponicsTankEntity.java /tmp/at.bak
edit $M/AquaponicsTankEntity.java 'return touchingWater && light >= MIN_LIGHT;' \
  'return light >= MIN_LIGHT;'
expect "a tank that grows without water" "a dry tank grew"
cp /tmp/at.bak $M/AquaponicsTankEntity.java

cp $M/AquaponicsTankEntity.java /tmp/at.bak
edit $M/AquaponicsTankEntity.java 'return touchingWater && light >= MIN_LIGHT;' \
  'return touchingWater;'
expect "a tank that grows in the dark" "a tank grew in the dark"
cp /tmp/at.bak $M/AquaponicsTankEntity.java

# The check the machine exists for: it must not want the bioreactor's site.
cp $M/AquaponicsTankEntity.java /tmp/at.bak
edit $M/AquaponicsTankEntity.java 'public static final int MIN_LIGHT = 9;' \
  'public static final int MIN_LIGHT = 12;'
expect "a light floor a player cannot read off the world" "not the 9 that vanilla crops"
cp /tmp/at.bak $M/AquaponicsTankEntity.java

echo "the fish"
cp $M/AquaponicsTankEntity.java /tmp/at.bak
edit $M/AquaponicsTankEntity.java 'public static final int CROPS_PER_FISH = 4;' \
  'public static final int CROPS_PER_FISH = 1;'
expect "a fish for every crop" "the fish are not a by-product"
cp /tmp/at.bak $M/AquaponicsTankEntity.java

cp $M/AquaponicsTankEntity.java /tmp/at.bak
edit $M/AquaponicsTankEntity.java 'public static final int CROPS_PER_FISH = 4;' \
  'public static final int CROPS_PER_FISH = 40;'
expect "a fish nobody will ever connect to the crops" "will never connect the two"
cp /tmp/at.bak $M/AquaponicsTankEntity.java

echo "the recipe types, derived rather than listed"
# Running the generator rewrites everything it owns (main() deletes it first),
# and the Pillow on this machine writes the same pixels as other bytes than the
# PNGs in the repository: undoing this case by running it again on the
# restored script left all 82 textures modified (17 Oct 2026). So all it owns
# is kept and put back byte for byte.
OWNS="src/main/resources/assets/elysiumsolarpunk src/main/resources/data src/main/java/com/elysium/solarpunk/SolarpunkContent.java $B/pending MACHINES.md"
cp tools/gen_solarpunk.py /tmp/g.bak
tar cf /tmp/tank_gen.tar $OWNS
edit tools/gen_solarpunk.py '    "aquaponics_tank": "farming",' '    "aquaponics_tank": "farmingg",'
regen
expect "a machine whose recipe type nothing registers" "the recipe will not load"
cp /tmp/g.bak tools/gen_solarpunk.py
rm -rf src/main/resources/assets/elysiumsolarpunk src/main/resources/data $B/pending
tar xf /tmp/tank_gen.tar; rm -f /tmp/tank_gen.tar

echo
echo "negative tests: $pass caught, $fail missed"
python3 validate.py >/dev/null 2>&1 && echo "restored clean" || echo "RESTORE FAILED"
[ "$fail" -eq 0 ]
