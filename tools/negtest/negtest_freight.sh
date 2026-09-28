#!/usr/bin/env bash
# The freight network, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-space
S=$M/src/main/java/com/elysium/space
R=$M/src/main/resources
SNAP=/tmp/freight_snap.tar
BAK=/tmp/freight_bak
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-space)
rm -rf $BAK && mkdir -p $BAK
# Only the files a test touched are put back, because untarring two thousand
# files for a one-line edit is most of a run's time.
# The FIRST state of a file, never a later one: a test that edits the same
# file twice would otherwise back up the half-edited version and "restore" to
# it, leaving the tree quietly changed and every later test lying.
keep() { local rel="${1#$ROOT/}"; mkdir -p "$BAK/$(dirname "$rel")"; [ -f "$BAK/$rel" ] || cp "$1" "$BAK/$rel" 2>/dev/null; }
restore() { (cd $BAK && find . -type f | while read -r f; do cp "$f" "$ROOT/${f#./}"; done); rm -rf $BAK; mkdir -p $BAK; }
out() { (cd $M && python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { keep "$1"; python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, 'anchor not found in ' + p
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@"; }

echo "what a run costs"
edit $S/world/FreightRules.java 'public static final float PER_STACK = 0.15F;' 'public static final float PER_STACK = 0.0F;'
expect "cargo that rides free" "cargo rides free"

edit $S/world/FreightRules.java 'public static final float EMPTY_SHARE = 1.0F;' 'public static final float EMPTY_SHARE = 0.1F;'
expect "a cargo rocket cheaper than a crewed one" "flies cheaper than the same rocket"

edit $S/world/FreightRules.java 'float weighted = EMPTY_SHARE + PER_STACK * Math.max(0, Math.min(SLOTS, stacks));' 'float weighted = EMPTY_SHARE + PER_STACK * Math.max(0, stacks);'
expect "more cargo than the rocket holds, charged for" "more cargo than the rocket holds"

echo "how long a run takes"
edit $S/world/FreightRules.java 'public static final int MOST_TICKS = 20 * 240;' 'public static final int MOST_TICKS = 20 * 21;'
expect "far worlds barely slower than near ones" "barely slower than the near ones"

edit $S/world/FreightRules.java 'double share = reach / (reach + HALFWAY);' 'double share = reach / HALFWAY;'
expect "a flight time that grows without bound" "the flight time is unbounded"

edit $S/world/FreightRules.java 'double share = reach / (reach + HALFWAY);' 'double share = 0.0D;'
expect "every world the same distance away" "same distance away, so there is nothing to plan"

edit $S/world/FreightRules.java 'double share = reach / (reach + HALFWAY);' 'double share = reach / (reach + HALFWAY * 2.0D);'
expect "a halfway distance that is not halfway" "is not halfway between"

edit $S/world/FreightRules.java 'public static final int IN_FLIGHT = 3;' 'public static final int IN_FLIGHT = 0;'
expect "a terminal that can never launch" "either blocked or an infinite pipe"

edit $S/world/FreightRules.java 'return now - launchedAt >= flight;' 'return now - launchedAt > flight * 2;'
expect "a shipment that never lands" "a shipment never landed"

echo "where the cargo lives"
edit $S/freight/FreightNetwork.java 'ServerLevel home = server.getLevel(Level.OVERWORLD);' 'ServerLevel home = server.overworld(); home = null;'
expect "a network kept per world" "kept per world"

edit $S/freight/FreightNetwork.java 'RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, lookup);
        TERMINALS.parse(ops' 'RegistryOps<Tag> ops = null;
        TERMINALS.parse(ops'
expect "cargo written without the registries" "plain NBT ops"

edit $S/freight/FreightNetwork.java 'public final class FreightNetwork extends SavedData {' 'public final class FreightNetwork {'
expect "a network nothing saves" "not saved, so every shipment"

echo "landing"
edit $S/freight/FreightDispatch.java 'level.getChunk(at);' ''
expect "a landing that needs a witness" "needs somebody standing there"

edit $S/freight/FreightDispatch.java 'network.replace(shipment, shipment.returned(now));
            ElysiumSpace.LOGGER.info' 'network.settle(shipment);
            ElysiumSpace.LOGGER.info'
expect "cargo destroyed when the far end is mined" "not sent home"

edit $S/freight/FreightDispatch.java 'if (!over.isEmpty()) {' 'if (false) {'
expect "a full destination that spills its cargo" "does not hold the rocket"

edit $S/freight/FreightDispatch.java 'if (level.dimension() != Level.OVERWORLD) {' 'if (false) {'
expect "a sweep that runs in every world" "once per loaded dimension"

echo "launching"
edit $S/freight/FreightDispatch.java 'terminal.fuelTank().drain(need, IFluidHandler.FluidAction.EXECUTE);' ''
expect "fuel checked and never spent" "checked and never spent"

edit $S/freight/FreightDispatch.java 'network.launch(new FreightNetwork.Shipment(here, there, terminal.unload(),' 'network.launch(new FreightNetwork.Shipment(here, there, java.util.List.of(),'
expect "a launch that leaves the cargo behind" "launching duplicates it"

edit $S/freight/FreightDispatch.java 'if (network.inFlightFrom(here) >= FreightRules.IN_FLIGHT) {' 'if (false) {'
expect "a terminal with unlimited rockets in the air" "any number of rockets in the air"

edit $S/freight/FreightDispatch.java 'int route = SpaceRoute.fuelFor(here.world(), there.world(),
                SpaceRoute.Fuel.FUEL);' 'int route = 100;'
expect "freight that prices itself" "instead of using the route table"

echo "the terminal"
edit $S/freight/FreightTerminalBlock.java 'Containers.dropContents(level, pos, terminal);' ''
expect "a terminal that eats its contents when mined" "destroys what is inside"

edit $S/freight/FreightTerminalBlock.java 'protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {' 'protected int notTheSignal(BlockState state, Level level, BlockPos pos) {'
expect "a terminal no comparator can read" "comparator cannot read"

edit $S/freight/FreightTerminalBlockEntity.java 'implements WorldlyContainer {' '{'
expect "a terminal no hopper can fill" "hopper cannot fill"

edit $S/freight/FreightTerminalBlockEntity.java 'return com.elysium.lib.menu.ElysiumChests.threeRows(id, inventory, this);' 'return null;'
expect "a terminal that opens something other than a chest" "other than the library's chest screen"

edit $S/registry/SpaceBlockEntities.java 'event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, FREIGHT_TERMINAL.get(),' 'if (false) event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, FREIGHT_TERMINALX.get(),'
expect "a terminal no pipe can reach" "cannot reach it"

echo "the packets"
edit $S/network/SpaceNetwork.java 'if (player.blockPosition().distSqr(payload.from()) > 64.0D * 64.0D) {' 'if (false) {'
expect "a client that can empty any terminal in the world" "checking the player is near it"

edit $S/network/SpaceNetwork.java 'registrar.playToClient(SpacePayloads.OpenFreight.TYPE,' 'if (false) registrar.playToClient(SpacePayloads.OpenFreightX.TYPE,'
expect "a packet declared and never registered" "dropped on arrival"

keep $S/client/FreightScreen.java; mv $S/client/FreightScreen.java /tmp/fs.bak
expect "no way to choose a destination" "no screen to choose a destination"
mv /tmp/fs.bak $S/client/FreightScreen.java

echo "the block itself"
keep $R/assets/elysiumspace/textures/block/freight_terminal_top.png
rm $R/assets/elysiumspace/textures/block/freight_terminal_top.png
expect "a terminal with no top texture" "has no top texture"

keep $R/data/elysiumspace/recipe/freight_terminal.json
rm $R/data/elysiumspace/recipe/freight_terminal.json
expect "a terminal nobody can make" "cannot be made"

keep $R/assets/elysiumspace/lang/en_us.json
python3 -c "
import json
p='$R/assets/elysiumspace/lang/en_us.json'
d=json.load(open(p)); d.pop('block.elysiumspace.freight_terminal')
json.dump(d,open(p,'w'),indent=2)"
expect "a terminal with no name" "has no name"

keep $S/freight/FreightNetwork.java; mv $S/freight/FreightNetwork.java /tmp/fn.bak
expect "the logistics system removed entirely" "no logistics system behind them"
mv /tmp/fn.bak $S/freight/FreightNetwork.java

echo
echo "restoring and re-checking"
restore
rm -rf $ROOT/elysium-space && (cd $ROOT && tar xf $SNAP)
if (cd $M && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
echo "freight: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
