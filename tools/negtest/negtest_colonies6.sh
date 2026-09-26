#!/usr/bin/env bash
# Elysium Colonies, part six: towns that found themselves.
# Each case breaks one thing the settlement needs and proves validate.py says so.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-colonies
S=$M/src/main/java/com/elysium/colonies
R=$M/src/main/resources
D=$R/data/elysiumcolonies
SNAP=/tmp/colonies6_snap.tar
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
" "$@"; }

echo "== towns that found themselves"
# the structure set drifts from the rule
edit $D/worldgen/structure_set/settlements.json '"spacing": 40' '"spacing": 41'
expect "structure set spacing differs from SettleRules" "the harness proved the rule, not the file"
# a settlement on a village
edit $D/worldgen/structure_set/settlements.json '"other_set": "minecraft:villages"' '"other_set": "minecraft:pillager_outposts"'
expect "no village exclusion" "on top of a village"
# the structure stops reading the charters' tag
edit $D/worldgen/structure/settlement.json '"biomes": "#elysiumcolonies:settles_in"' '"biomes": "#minecraft:is_overworld"'
expect "structure ignores settles_in" "decided somewhere other than the charters"
# the Nether settles
edit $D/charter/the_nether.json '"settles": false' '"settles": true'
expect "the Nether settles" "nobody founds a town there"
# the Vale in the tag
edit $D/tags/worldgen/biome/settles_in.json '"elysiumspace:cinderveil_desert",' '"elysiumwilds:vale_emberwood", "elysiumspace:cinderveil_desert",'
expect "the Vale is settled" "reaches into the Vale"
# the stake stops ticking
edit $S/settle/SettlementSeedBlock.java 'super(properties.randomTicks());' 'super(properties);'
expect "stake never ticks" "does not tick at random"
# the stake draws on the solid layer
edit $R/assets/elysiumcolonies/models/block/settlement_seed.json '"render_type": "minecraft:cutout",' ''
expect "stake on the solid layer" "names no render layer"
# founding without the world's refusals
edit $S/settle/Settlements.java 'if (!charter.settles()) {' 'if (false) {'
expect "a non-settling world settles" "a world whose charter does not settle gets towns"
edit $S/settle/Settlements.java 'if (!Colonies.mayFoundAt(level, ground)) {' 'if (false) {'
expect "host distance not asked" "distance between colonies is not asked"
edit $S/settle/Settlements.java 'level.getFluidState(ground.below()).isEmpty()' 'true'
expect "a town on water" "a town may found itself on water"
edit $S/settle/Settlements.java 'BlockPos ground = dryGroundNear(level, stake);' 'BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, stake);'
expect "a stake on a lake is wasted" "wasted rather than looking around"
# the town waits for its steward
edit $S/settle/Settlements.java 'withSteward(StewardRules.Steward.TOWN)' 'withSteward(StewardRules.Steward.ATTENDANCE)'
expect "self-founded town waits for an owner" "waits for an owner who does not exist"
# the seam founds for a real player
edit $S/colony/Colonies.java 'Player steward = FakePlayerFactory.get(level, new GameProfile(stewardId, stewardName));' 'Player steward = level.getRandomPlayer();'
expect "a real player owns the town" "does not found a colony for a fake steward"
# a write escapes the seam
edit $S/settle/Settlements.java 'memory.record(town.id(), origin.claimed());' 'memory.record(town.id(), origin.claimed()); boolean owned = player.setOwner(player);'
expect "setOwner called outside the seam" "every write goes through the seam"
# the town demolishes by itself
edit $S/colony/Colonies.java 'if (order == null || order.getWorkOrderType() == WorkOrderType.REMOVE) {' 'if (order == null) {'
expect "a town demolishes by itself" "may demolish a building by itself"
# the director runs for a player's town
edit $S/settle/Settlements.java '        if (!origin.natural()) {
            return;
        }
        List<Colonies.Order> waiting' '        List<Colonies.Order> waiting'
expect "director runs for player towns" "runs for towns a player made"
# the daily pass forgets the director
edit $S/event/ColonyLife.java 'com.elysium.colonies.settle.Settlements.direct(level, town);' '// nothing'
expect "daily pass never directs" "never calls the director"
# claiming keeps the town natural
edit $S/settle/Settlements.java 'memory.record(town.id(), origin.claimed());' 'memory.record(town.id(), origin);'
expect "a claimed town stays natural" "mark it a player's"
# a style pack without a naming
edit $S/rules/SettleRules.java '"High Orbit", List.of("Station", "Ring"' '"Far Orbit", List.of("Station", "Ring"'
expect "a pack named in one list only" "has no naming of its own in SettleRules"
# a fake player conjured outside the seam's two methods
edit $S/settle/Settlements.java 'String stewardName = stewardName(name);' 'String stewardName = stewardName(name); Object fake = FakePlayerFactory.get(level, null);'
expect "fake player outside the seam" "conjures a fake player outside the seam"
# a harness that fails is reported, not a crash of the validator
edit $M/tools/ColonyHarness.java 'check(SettleRules.FROM_SPAWN > 0, ' 'check(SettleRules.FROM_SPAWN < 0, '
expect "a failing harness is reported" "the colony harness failed"
# hand-edited generated data
edit $D/worldgen/template_pool/settlement/seed.json '"fallback": "minecraft:empty"' '"fallback": "minecraft:empty "'
expect "hand-edited pool" "differ from what tools/gen_settlements.py writes"

echo "$pass passed, $fail missed"
(cd $ROOT && git status --short elysium-colonies | grep -q "^ M\|^??" ) && echo "tree has changes (expected: the feature is uncommitted)" || echo "tree clean"
