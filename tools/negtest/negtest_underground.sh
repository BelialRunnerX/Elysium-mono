#!/usr/bin/env bash
# P9.5 and P9.6 — what is waiting underground, broken one rule at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
CORE=$ROOT/elysium-core
CSRC=$CORE/src/main/java/com/elysium/core
CRES=$CORE/src/main/resources
pass=0; fail=0

SNAP=/tmp/negtest_underground_snapshot.tgz
tar czf $SNAP -C $ROOT elysium-core/src elysium-core/tools

out() { (cd $CORE && python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "the spawners"
cp $CSRC/block/RogueSpawnerBlockEntity.java /tmp/rs.bak
edit $CSRC/block/RogueSpawnerBlockEntity.java 'level.destroyBlock(pos, true);' ''
expect "a spawner that never runs out" "chore to suppress"
cp /tmp/rs.bak $CSRC/block/RogueSpawnerBlockEntity.java

cp $CSRC/block/RogueSpawnerBlockEntity.java /tmp/rs.bak
edit $CSRC/block/RogueSpawnerBlockEntity.java 'Player nearest = level.getNearestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D,
                pos.getZ() + 0.5D, spawner.mode.range, false);
        if (nearest == null) {
            return;                         // nobody to send them at
        }' ''
expect "a spawner filling a cave nobody is in" "sends creatures at nobody"
cp /tmp/rs.bak $CSRC/block/RogueSpawnerBlockEntity.java

cp $CSRC/block/RogueSpawnerBlockEntity.java /tmp/rs.bak
edit $CSRC/block/RogueSpawnerBlockEntity.java 'com.elysium.lib.elite.ElysiumElites.promote(mob,' 'promoteNothing(mob,'
expect "a brutal spawner that sends ordinary creatures" "what the swarm one is for"
cp /tmp/rs.bak $CSRC/block/RogueSpawnerBlockEntity.java

echo "the cage"
cp $CSRC/block/CagedInvaderBlock.java /tmp/ci.bak
edit $CSRC/block/CagedInvaderBlock.java 'public BlockState playerWillDestroy(' 'public BlockState onOpened('
expect "a cage that opens by some other means" "opened by accident or from safety"
cp /tmp/ci.bak $CSRC/block/CagedInvaderBlock.java

cp $CSRC/block/CagedInvaderBlock.java /tmp/ci.bak
edit $CSRC/block/CagedInvaderBlock.java 'com.elysium.lib.elite.ElysiumElites.release(mob,' 'releaseOrdinary(mob,'
expect "a cage that lets out an ordinary creature" "disappointment rather than a decision"
cp /tmp/ci.bak $CSRC/block/CagedInvaderBlock.java

echo "where they are"
cp $CSRC/../../../../resources/data/elysium/worldgen/placed_feature/brutal_spawner.json /tmp/pf.bak 2>/dev/null || cp $CRES/data/elysium/worldgen/placed_feature/brutal_spawner.json /tmp/pf.bak
python3 -c "
import json
p='$CRES/data/elysium/worldgen/placed_feature/brutal_spawner.json'
d=json.load(open(p))
d['placement']=[s for s in d['placement'] if s['type']!='minecraft:rarity_filter']
json.dump(d, open(p,'w'), indent=2)
"
expect "a spawner in every chunk" "stop meaning anything"
cp /tmp/pf.bak $CRES/data/elysium/worldgen/placed_feature/brutal_spawner.json

cp $CRES/data/elysium/worldgen/placed_feature/swarm_spawner.json /tmp/pf2.bak
python3 -c "
import json
p='$CRES/data/elysium/worldgen/placed_feature/swarm_spawner.json'
d=json.load(open(p))
d['placement']=[s for s in d['placement'] if s['type']!='minecraft:block_predicate_filter']
json.dump(d, open(p,'w'), indent=2)
"
expect "a spawner placed inside stone" "inside stone where nothing can reach it"
cp /tmp/pf2.bak $CRES/data/elysium/worldgen/placed_feature/swarm_spawner.json

cp $CRES/data/elysium/neoforge/biome_modifier/add_caged_invader.json /tmp/bm.bak
rm $CRES/data/elysium/neoforge/biome_modifier/add_caged_invader.json
expect "a feature no biome ever loads" "one thing that puts a feature into a world"
cp /tmp/bm.bak $CRES/data/elysium/neoforge/biome_modifier/add_caged_invader.json

cp $CRES/data/elysium/worldgen/placed_feature/caged_invader.json /tmp/pf3.bak
rm $CRES/data/elysium/worldgen/placed_feature/caged_invader.json
expect "a block that only exists if a player places it" "one they placed themselves"
cp /tmp/pf3.bak $CRES/data/elysium/worldgen/placed_feature/caged_invader.json

echo
echo "restoring and re-checking"
tar xzf $SNAP -C $ROOT
if (cd $CORE && python3 validate.py >/dev/null 2>&1); then echo "  tree restored clean";
else echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1)); fi
echo "underground: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
