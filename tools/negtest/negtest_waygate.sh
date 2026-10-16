#!/usr/bin/env bash
# The waygate, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-space
S=$M/src/main/java/com/elysium/space
R=$M/src/main/resources
SNAP=/tmp/waygate_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-space)
restore() { rm -rf $ROOT/elysium-space && (cd $ROOT && tar xf $SNAP); }
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

echo "the ring"
edit $S/gate/WaygateRing.java 'for (GateRules.Offset at : GateRules.frame()) {' \
                              'for (GateRules.Offset at : java.util.List.<GateRules.Offset>of()) {'
expect "a ring shape written twice" "harness proves nothing"

edit $S/gate/WaygateRing.java 'return acrossX
                ? anchor.offset(at.across(), at.up(), 0)
                : anchor.offset(0, at.up(), at.across());' \
                              'return anchor.offset(1, 2, 3);'
expect "a hard-coded offset in the ring" "two halves of the shape can disagree"

echo "what it must not charge"
edit $S/gate/WaygateAnchorBlock.java 'WaygateRing.Reading ring = WaygateRing.read(server, pos);' \
  'player.getMainHandItem().shrink(1);
        WaygateRing.Reading ring = WaygateRing.read(server, pos);'
expect "a gate that charges a toll" "paid for once"

edit $S/gate/WaygateTravel.java 'traveller.resetFallDistance();' \
                                'traveller.giveExperienceLevels(-1);'
expect "a gate that charges experience" "paid for once"

echo "joining and leaving the network"
edit $S/gate/WaygateAnchorBlock.java 'WaygateNetwork.of(server).record(new WaygateNetwork.Gate(
                server.dimension().location(), pos, "", ring.acrossX()));' ''
expect "a finished gate nothing can travel to" "never joins the network"

edit $S/gate/WaygateAnchorBlock.java 'WaygateNetwork.of(server).forget(server.dimension().location(), pos);' ''
expect "a broken anchor still on the network" "arrives to find nothing"

edit $S/gate/WaygateAnchorBlock.java 'if (!state.is(replacement.getBlock()) && level instanceof ServerLevel server) {' \
                                     'if (level instanceof ServerLevel server) {'
expect "a state update taking a working gate off the network" "state update"

echo "travelling"
edit $S/gate/WaygateTravel.java 'BlockPos standing = nearestAnchor(from, traveller.blockPosition());' \
                                'BlockPos standing = traveller.blockPosition();'
expect "a kept packet that teleports from anywhere" "teleport from anywhere"

edit $S/gate/WaygateTravel.java 'if (!WaygateRing.read(there, anchor).complete()) {' 'if (false) {'
expect "the far end never checked" "drops a player into the ground"

edit $S/gate/WaygateTravel.java 'there.getChunkSource().addRegionTicket(
                net.minecraft.server.level.TicketType.POST_TELEPORT,
                new net.minecraft.world.level.ChunkPos(anchor), 3,
                traveller.getId());' ''
expect "arriving in unloaded terrain" "inside unloaded terrain"

edit $S/gate/WaygateTravel.java 'traveller.resetFallDistance();' ''
expect "a doorway that can kill you" "stepping through"

edit $S/gate/WaygateTravel.java 'network.forget(going.dimension(), anchor);' ''
expect "a broken far gate left to be found again" "broken again tomorrow"

echo "one network"
edit $S/gate/WaygateNetwork.java 'ServerLevel overworld = server.getLevel(Level.OVERWORLD);' \
                                 'ServerLevel overworld = server.getLevel(anywhere.dimension());'
expect "gates that only work one way" "one direction only"

edit $S/gate/WaygateNetwork.java 'public final class WaygateNetwork extends SavedData {' \
                                 'public final class WaygateNetwork {'
expect "a network forgotten on restart" "forgotten on restart"

echo "the blocks"
rm -f $R/assets/elysiumspace/textures/block/waygate_frame.png
expect "a frame nobody can see" "has no texture"

rm -f $R/data/elysiumspace/loot_table/blocks/waygate_anchor.json
expect "an anchor that destroys itself" "breaking a gate destroys it"

rm -f $R/data/elysiumspace/recipe/waygate_frame.json
expect "a creative-only gate" "creative-only"

python3 -c "
import json
p='$R/data/elysiumspace/recipe/waygate_anchor.json'
d=json.load(open(p)); d['key']['L']={'item':'elysium:aetherium_ingot'}
json.dump(d,open(p,'w'),indent=2)"
expect "a gate that needs the content module" "not a dependant"

python3 -c "
import json
p='$R/assets/elysiumspace/lang/en_us.json'
d=json.load(open(p)); d.pop('block.elysiumspace.waygate_anchor')
json.dump(d,open(p,'w'),indent=2)"
expect "an anchor with no name" "has no name"

echo "the packets"
edit $S/network/SpaceNetwork.java 'registrar.playToServer(SpacePayloads.ChooseWaygate.TYPE, SpacePayloads.ChooseWaygate.STREAM_CODEC,' \
                                  'if (false) registrar.playToServer(SpacePayloads.ChooseDestination.TYPE, SpacePayloads.ChooseDestination.STREAM_CODEC,'
expect "a choice dropped on arrival" "dropped on arrival"

mv $S/client/WaygateScreen.java /tmp/wgs.bak
expect "no way to choose a gate" "no screen to choose"
mv /tmp/wgs.bak $S/client/WaygateScreen.java

echo "the arithmetic"
edit $S/world/GateRules.java 'public static final int SIZE = 5;' 'public static final int SIZE = 3;'
expect "an opening nobody can see through" "see through"

edit $S/world/GateRules.java 'public static final int ARRIVAL_UP = 1;' 'public static final int ARRIVAL_UP = 0;'
expect "arriving inside the anchor block" "inside the anchor"

edit $S/world/GateRules.java 'public static final int SETTLE = 4;' 'public static final int SETTLE = 200;'
expect "a gate that charges a waiting toll" "charges a toll"

edit $S/world/GateRules.java 'return trimmed.length() <= NAME_LIMIT ? trimmed
                : trimmed.substring(0, NAME_LIMIT);' 'return trimmed;'
expect "one gate pushing the list off the screen" "off the screen"

echo
echo "restoring and re-checking"
restore
if (cd $M && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
echo "waygate: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
