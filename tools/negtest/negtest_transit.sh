#!/usr/bin/env bash
# Getting there, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-transit
R=$M/src/main/java/com/elysium/transit/rules/TransitRules.java
S=$M/src/main/java/com/elysium/transit
G=$M/tools/gen_transit.py
RES=$M/src/main/resources
SNAP=/tmp/transit_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-transit)
restore() { rm -rf $ROOT/elysium-transit && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
# For the rules that can only be defeated by editing the generator and the
# files it writes together — an edit to one alone is caught by the drift check,
# which is a different rule and would make this one vacuous.
expect_regen() { local w="$2" g
  (cd $M && python3 tools/gen_transit.py >/dev/null 2>&1)
  g="$(out)"
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

echo "the arbitrage, which is the only way a teleport network is catastrophically wrong"
edit $R '    public static final float PER_JUMP = 2_000.0F;' '    public static final float PER_JUMP = 0.0F;'
expect "a jump with no fixed price, so patience is free travel" "free to anybody patient"

edit $R '        return (float) (MOST_FOR_DISTANCE * far / (far + HALF_PRICE_AT));' '        return (float) (far * 4.0);'
expect "a price for distance that climbs without limit" "saturating shape"

edit $R '        double far = Math.max(0.0, blocks);' '        double far = blocks;'
expect "a negative distance paid for in reverse" "paid for in reverse"

edit $R '    public static final float BETWEEN_WORLDS = 20_000.0F;' '    public static final float BETWEEN_WORLDS = 1_000.0F;'
expect "bouncing off another world being the cheap way to cross this one" "arbitrage this whole module"

edit $R '    public static final float MOST_FOR_DISTANCE = 18_000.0F;' '    public static final float MOST_FOR_DISTANCE = 180_000.0F;'
expect "a jump dearer than a waystone can ever hold" "nobody can ever reach"

edit $R '        if (!from.sameWorldAs(to)) {
            return PER_JUMP + BETWEEN_WORLDS;
        }
        return PER_JUMP + forDistance(distance(from, to));' '        return PER_JUMP + forDistance(distance(from, to));'
expect "two worlds' coordinates subtracted from one another" "the price means nothing"

echo "the early game, which is not a special case in the price"
edit $R '    public static final float TRICKLE = 2.0F;' '    public static final float TRICKLE = 0.0F;'
expect "an unwired waystone that never fills at all" "no short way home"

edit $R '    public static final float TRICKLE = 2.0F;' '    public static final float TRICKLE = 0.05F;'
expect "a short way home that is a chore rather than an incentive" "chore rather than an incentive"

edit $R '        return Math.max(TRICKLE, WAYSTONE.draw() * Math.min(1.0F, share));' '        return WAYSTONE.draw() * Math.min(1.0F, share);'
expect "a browned-out waystone filling slower than an unwired one" "wiring something made it worse"

edit $R '        if (forDistance >= MOST_FOR_DISTANCE) {' '        if (false) {'
expect "a full waystone reporting an impossible reach" "rather than 'anywhere'"

edit $R '        if (held >= want) {' '        if (false) {'
expect "a countdown on a jump a player can already afford" "something other than what the player is waiting for"

echo "the jump, and who decides it"
edit $S/travel/WaystoneTravel.java '        if (player.blockPosition().distSqr(from) > WITHIN * WITHIN) {
            return Component.translatable("elysiumtransit.waystone.far");
        }' '        if (false) {
            return Component.translatable("elysiumtransit.waystone.far");
        }'
expect "a waystone anybody can use from anywhere" "nowhere near"

edit $S/travel/WaystoneTravel.java '        TransitRules.Waypoint there = remembered(player, key);' '        TransitRules.Waypoint there = TransitRules.Waypoint.unpack(key);'
expect "a destination that comes out of the packet" "crafted packet reaches anywhere"

edit $S/travel/WaystoneTravel.java '        if (!waystone.spend(price)) {' '        if (false) {'
expect "a jump nobody pays for" "the whole price is decoration"

edit $S/travel/WaystoneTravel.java '            TransitAttachments.forget(player, there);' '            ;'
expect "a mined waystone offered forever" "refuse them every time"

edit $S/network/TransitPayloads.java 'public record ChooseWaystone(BlockPos from, String key)' 'public record ChooseWaystone(BlockPos from, String key, float price)'
expect "a packet from the client that carries the price" "the one number the whole module is about"

echo "what a player remembers"
edit $S/registry/TransitAttachments.java '                    .copyOnDeath()' '                    '
expect "every bound waystone lost on death" "aimed at the thing this module exists to remove"

edit $S/registry/TransitAttachments.java '                    .sync(StreamCodec.of(' '                    .unsynced(StreamCodec.of('
expect "a list the client never receives" "menu is empty however far"

edit $S/registry/TransitAttachments.java '                    .serialize(Codec.STRING.listOf())' '                    '
expect "a network that empties on restart" "not written to disk"

edit $S/registry/TransitAttachments.java '        player.setData(KNOWN.get(), List.copyOf(now));
        return !had;' '        was.add(row);
        return !had;'
expect "an attachment mutated in place" "silently stops growing"

edit $S/travel/WaystoneTravel.java '        for (String entry : TransitAttachments.known(player)) {
            TransitRules.Waypoint there = TransitAttachments.placeOf(entry);
            if (there != null && there.pack().equals(key)) {' '        for (String entry : java.util.List.<String>of()) {
            TransitRules.Waypoint there = TransitAttachments.placeOf(entry);
            if (there != null && there.pack().equals(key)) {'
expect "a destination list that is not per player" "handed to somebody who has not explored"

echo "transit in the world"
edit $G '        "W": ITEM(f"{IND}:copper_wire")}, "waystone"),' '        "W": ITEM("elysiumfoundry:starsteel_ingot")}, "waystone"),'
expect_regen "a short way home that arrives ages after the walking" "several ages after the walking"

rm $RES/assets/elysiumtransit/textures/block/waystone.png
expect "a waystone with no texture" "purple cube"

rm $RES/data/elysiumtransit/loot_table/blocks/paving.json
expect "paving that vanishes when mined" "has no drop"

edit $S/registry/TransitItems.java '    private static final List<DeferredItem<BlockItem>> ORDER =
            List.of(WAYSTONE, PAVING, GRAVEL_PATH);' '    private static final List<DeferredItem<BlockItem>> ORDER =
            List.of(WAYSTONE, PAVING);'
expect "a block nobody in a creative world can find" "no creative tab"

edit $S/client/WaystoneScreen.java 'public class WaystoneScreen extends ElysiumChooser {' 'public class WaystoneScreen extends net.minecraft.client.gui.screens.Screen {'
expect "four ways of crossing a distance looking like four mods" "four different mods"

edit $M/src/main/resources/META-INF/neoforge.mods.toml '    modId="elysiumindustry"' '    modId="waystones"'
expect "a dependency on the mod this module replaces" "the grid a waystone hangs off"

edit $S/rules/TransitRules.java 'import com.elysium.industry.rules.IndustryRules;' 'import com.elysium.industry.block.GridBlock;
import com.elysium.industry.rules.IndustryRules;'
expect "rules reaching into the age below's blocks" "pure like itself"

edit $S/rules/TransitRules.java 'import com.elysium.industry.rules.IndustryRules;' 'import net.minecraft.world.item.Item;
import com.elysium.industry.rules.IndustryRules;'
expect "rules no harness could price without a game" "without a game"

echo "the vehicles"
edit $R '    public static final Vehicle HAULER = new Vehicle("hauler", 3, 18.0F, 0.20F, 2, 54);' '    public static final Vehicle HAULER = new Vehicle("haulier", 3, 18.0F, 0.20F, 2, 54);'
expect "a rung in the rules that nobody can ride" "nobody can ride"

edit $S/registry/TransitEntities.java '        String name = EntityType.getKey(type).getPath();
        for (TransitRules.Vehicle rung : RUNGS) {' '        String name = "cart";
        for (TransitRules.Vehicle rung : RUNGS) {'
expect "a vehicle whose numbers are not its own" "registered with no numbers behind it"

edit $S/entity/VehicleEntity.java '                target = TransitRules.speedOn(rung(), surface(), load());' '                target = 12.0F;'
expect "a speed decided twice, and proved in neither place" "the proofs cover neither"

edit $S/entity/VehicleEntity.java '        float now = TransitRules.approach(was, target);' '        float now = target;'
expect "a vehicle that handles like a cursor" "acceleration in the rules is decoration"

edit $S/entity/VehicleEntity.java '            float burnt = (float) (went * TransitRules.thirstOn(rung(), surface(), load()));' '            float burnt = 0.05F;'
expect "fuel billed on something other than the distance covered" "distance actually covered"

edit $S/entity/VehicleEntity.java '        if (went > 0.0D && driver != null) {' '        if (driver != null) {'
expect "a vehicle burning fuel while held against a wall" "quietly empty somebody's tank"

edit $S/entity/VehicleEntity.java '            setFuel(TransitRules.fuelled(fuel(), TransitRules.fuelFrom(burns)));' '            setFuel(fuel() + TransitRules.fuelFrom(burns));'
expect "a tank filled past what it holds" "fuel over the top is destroyed"

edit $S/entity/VehicleEntity.java '        if (burns > 0 && fuel() < TransitRules.TANK) {' '        if (burns > 0) {'
expect "a full vehicle eating one more lump of coal" "feeding one coal too many"

edit $S/entity/VehicleEntity.java '            spill();' '            ;'
expect "a broken vehicle destroying everything in it" "vanish without them asking"

edit $S/entity/VehicleEntity.java '        if (state.is(com.elysium.transit.registry.TransitBlocks.PAVING.get())) {' '        if (false) {'
expect "paving that does nothing when you drive on it" "laying it does nothing at all"

edit $S/entity/VehicleEntity.java '        if (level().isClientSide) {
            // The client moves it from the server'"'"'s own updates; driving is
            // decided in one place, which is the other side.
            move(MoverType.SELF, getDeltaMovement());
            return;
        }' '        if (false) {
            move(MoverType.SELF, getDeltaMovement());
            return;
        }'
expect "driving decided on both sides at once" "whichever arrives last wins"

edit $S/client/VehicleRenderer.java '        String name = EntityType.getKey(vehicle.getType()).getPath();' '        String name = "cart";'
expect "a hauler and a cart drawn the same" "same thing across a field"

echo "the seam"
edit $S/ElysiumTransit.java 'MODID = "elysiumtransit"' 'MODID = "elysium_transit"'
expect "a module id nothing else in the tree spells that way" "everything else in the tree spells"

edit $S/ElysiumTransit.java '        com.elysium.transit.registry.TransitBlockEntities.TYPES.register(modEventBus);' '        '
expect "a block entity written and never registered" "none of it exists"

edit $S/ElysiumTransit.java '        com.elysium.transit.registry.TransitEntities.TYPES.register(modEventBus);' '        '
expect "three vehicles written and never registered" "none of it exists"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all transit checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "transit: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
