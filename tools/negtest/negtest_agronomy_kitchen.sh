#!/usr/bin/env bash
# The kitchen made real: stations that cook, pots that hold soil, a colony
# that grows, crates that open and cabinets that hold -- broken one way at a
# time, and every break reported.
set -uo pipefail
ROOT=${ELYSIUM_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}
M=$ROOT/elysium-agronomy
S=$M/src/main/java/com/elysium/agronomy
R=$M/src/main/resources
D=$R/data/elysiumagronomy
A=$R/assets/elysiumagronomy
L=$A/lang/en_us.json
K=$S/rules/KitchenRules.java
C=$S/rules/CropRules.java
P=$S/rules/PotRules.java
ST=$S/block/StationBlock.java
PB=$S/block/PotBlock.java
CB=$S/block/ColonyBlock.java
CA=$S/block/CabinetBlock.java
KB=$S/registry/KitchenBlocks.java
BE=$S/registry/AgronomyBlockEntities.java
H=$M/tools/AgronomyHarness.java
DA=$S/data/AgronomyData.java
SNAP=${TMPDIR:-/tmp}/agronomy_kitchen_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-agronomy)
restore() { rm -rf $ROOT/elysium-agronomy && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 900 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, 'anchor not found in ' + p
open(p,'w',encoding='utf-8',newline='\n').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "the listener, and the three folders it opens"
edit $DA '        orders.addAll(each(manager, COOKING, AgronomyData::meal));' ''
expect "the cooking folder read by nothing" "no longer reads the cooking folder"

edit $DA '        orders.addAll(each(manager, LARDER, AgronomyData::larder));' ''
expect "the larder folder read by nothing" "no longer reads the larder folder"

edit $DA '        cuts = List.copyOf(each(manager, CUTTING, AgronomyData::cut));' '        cuts = List.of();'
expect "the cutting folder read by nothing" "no longer reads the cutting folder"

edit $DA '    public static void onReload(AddReloadListenerEvent event) {' '    public static void onLoad(AddReloadListenerEvent event) {'
expect "a reader that is not a reload listener" "is not a reload listener"

edit $DA '    public static final String LARDER = "larder";' '    public static final String LARDER = "pantry";'
expect "the listener reading a folder the generator does not write" "no longer calls the larder folder larder"

echo "the stations"
edit $KB '                    () -> new com.elysium.agronomy.block.StationBlock(properties, station));' \
         '                    () -> new Block(properties));'
expect "four stations that are plain blocks" "the four stations are plain blocks"

edit $ST '            KitchenRules.Order order = AgronomyData.orderFor(station(), have());
            if (order == null) {
                return says(level, pos);
            }' \
         '            KitchenRules.Order order = AgronomyData.menu().isEmpty() ? null : AgronomyData.menu().get(0);
            if (order == null) {
                return says(level, pos);
            }'
expect "a station deciding for itself what is a recipe" "decides for itself whether what is in it is a recipe"

edit $ST '            if (KitchenRules.needsHeat(station()) && !heated(level, pos)) {
                return Component.translatable("elysiumagronomy.station.cold");
            }
            cooking = true;' \
         '            if (false) {
                return Component.translatable("elysiumagronomy.station.cold");
            }
            cooking = true;'
expect "a pot that starts cold" "starts with no fire under it"

edit $ST '            if (KitchenRules.needsHeat(station()) && !heated(level, pos)) {
                return;                      // the fire went out; wait for it
            }' \
         '            if (false) {
                return;
            }'
expect "a pot that cooks on after the fire goes out" "goes on cooking after the fire under it goes out"

edit $ST '            if (progress < order.ticks()) {' '            if (progress < 20) {'
expect "a station with a time of its own" "finishes on a time of its own"

edit $ST '            made.add(new ItemStack(itemOf(order.id()), order.count()));' \
         '            made.add(new ItemStack(itemOf(order.id()), 64));'
expect "a stack of stew from one pot" "other than the entry's servings"

edit $ST '            return KitchenRules.isHeat(BuiltInRegistries.BLOCK.getKey(' \
         '            return level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.TORCH) || KitchenRules.isHeat(BuiltInRegistries.BLOCK.getKey('
expect "a fuel the rules never saw" "rather than KitchenRules.HEAT"

echo "the knife"
edit $ST '                KitchenRules.Cut cut = AgronomyData.cutFor(nameOf(contents.get(at)),
                        toolOf(knife));' \
         '                KitchenRules.Cut cut = null;'
expect "a knife that asks nobody what it cuts" "does not ask the cutting folder"

edit $ST '            return knife.getItem() instanceof KnifeItem one ? one.tool() : KitchenRules.KNIFE;' \
         '            return KitchenRules.KNIFE;'
expect "a board that asks every knife for a kitchen knife's cuts" "a board asks for cuts under a name"

edit $ST '                knife.hurtAndBreak(KitchenRules.KNIFE_WEAR, player,
                        LivingEntity.getSlotForHand(hand));' ''
expect "a knife that never wears" "does not wear the knife"

edit $ST '            if (!KitchenRules.BOARD.equals(station())) {' '            if (false) {'
expect "a knife that cuts in a pot" "a knife cuts on any station"

edit $ST '            Component said = held.getItem() instanceof KnifeItem
                    ? entity.slice(held, player, hand)
                    : entity.offer(held, level, pos);' \
         '            Component said = entity.offer(held, level, pos);'
expect "a knife that goes in as an ingredient" "goes in as an ingredient rather than cutting"

edit $S/registry/KitchenItems.java '                    () -> new com.elysium.agronomy.item.KnifeItem(' '                    () -> new Item('
expect "knives that are plain items" "the knives are plain items"

edit $BE '        for (String station : com.elysium.agronomy.rules.KitchenRules.STATIONS) {
            all.add(KitchenBlocks.STATIONS.get(station).get());' \
         '        for (String station : java.util.List.of(com.elysium.agronomy.rules.KitchenRules.POT)) {
            all.add(KitchenBlocks.STATIONS.get(station).get());'
expect "a station with no block entity" "no longer names every station"

echo "the data and the words"
edit $D/larder/stock.json '"ticks": 200' '"ticks": 5'
expect "stock quicker on disk than in the rules" "takes 5 ticks on disk"

edit $L '  "elysiumagronomy.station.cold": "No fire under it.",
' ''
expect "a station saying something with no words for it" "the language file has no such line"

echo "the arithmetic"
edit $K '            if (meanTicks(quicker, orders) >= meanTicks(slower, orders)) {' '            if (false) {'
expect "a pace check that answers no to everything" "the pace check cannot see a board slower than an oven"

edit $K '                if (one.station().equals(other.station())
                        && one.wants().equals(other.wants())) {' '                if (false) {'
expect "a clash check that answers no to everything" "cannot see two orders that are the same things"

edit $K '            if (order.station().equals(station) && order.wants().equals(got)) {' \
        '            if (order.station().equals(station) && order.wants().keySet().equals(got.keySet())) {'
expect "matching that is not exact" "two dough on a board matched something"

edit $K '    public static final List<String> PACE = List.of(BOARD, SKILLET, POT, OVEN);' \
        '    public static final List<String> PACE = List.of(BOARD, SKILLET, POT);'
expect "a station whose pace nothing states" "nothing says how quick the oven is"

edit $C '        bad.addAll(KitchenRules.clashing(all));' ''
expect "a larder never checked against the meals" "not checked against the meals"

edit $H '        theStations();' ''
expect "a harness that skips the stations" "no longer runs the station checks"

echo "a pot with soil in it"
edit $DA '    public static final String SOILS = "botany_soil";' '    public static final String SOILS = "soils";'
expect "the pots reading a soil folder nobody writes" "no longer calls the botany_soil folder botany_soil"

edit $DA '        sownBySeed = Map.copyOf(sown);' ''
expect "the sprout folder read and thrown away" "no longer reads the botany_sprout folder"

edit $DA '        for (Bed bed : each(manager, SOILS, AgronomyData::soil)) {' '        for (Bed bed : List.<Bed>of()) {'
expect "the soil folder read by nothing" "no longer reads the botany_soil folder"

edit $PB '        private final ItemStackHandler items = new ItemStackHandler(3) {' \
         '        private final ItemStackHandler items = new ItemStackHandler(2) {'
expect "a pot with no soil slot" "a pot has no soil slot"

edit $PB '                    items.setStackInSlot(SOIL, held.copyWithCount(1));' ''
expect "a soil that fills nothing" "does not fill it with that soil"

edit $PB '                if (!PotRules.growsIn(under.soil(), planting.sprout())) {' '                if (false) {'
expect "a sprout planted over any soil" "takes a sprout over any soil at all"

edit $PB '            Component said = pot.use(held);' '            Component said = null; pot.plant(held);'
expect "using a thing on a pot going nowhere" "no longer goes through PotBlockEntity.use"

edit $PB '                    || !PotRules.growsIn(under.soil(), sprout.sprout())) {' '                    ) {'
expect "a sprout grown in a soil that does not take it" "grows a sprout in a soil that does not take it"

edit $PB '            int whole = sprout.sprout().ticksIn(under.soil(), speed);' \
         '            int whole = sprout.sprout().ticks();'
expect "a sprout on a time of its own" "a time of the pot's own"

edit $PB 'ResourceLocation.parse(sprout.drop())), sprout.sprout().amount());' \
         'ResourceLocation.parse(sprout.drop())), 64);'
expect "a sprout that gives a stack" "gives something other than its drop times its amount"

edit $PB '                growSprout(level, pos, state);' '                settle(level, pos, state, 0);'
expect "a pot whose tick never grows a sprout" "never grows a sprout"

edit $PB '            if (!roomFor(harvest)) {
                return;                        // no room; stop rather than lose it' \
         '            if (!items.insertItem(OUT, harvest, true).isEmpty()) {
                return;                        // no room; stop rather than lose it'
expect "a pot asking a locked slot whether it has room" "whether a harvest fits"

edit $PB '                    items.getStackInSlot(OUT), items.getStackInSlot(SOIL));' \
         '                    items.getStackInSlot(OUT));'
expect "a broken pot that loses its soil" "loses the soil in it"

edit $L '  "elysiumagronomy.pot.soilless": "No soil in it: use a soil on it first.",
' ''
expect "a pot saying something with no words for it" "a pot says elysiumagronomy.pot.soilless"

edit $D/botany_sprout/wheat.json '"seed": "minecraft:wheat_seeds"' '"seed": "minecraft:wheat"'
expect "a sprout file planted by something else" "sprout file(s) that say something different"

edit $D/botany_soil/rich_loam.json '"speed": 1.7' '"speed": 2.7'
expect "a soil file quicker than the rules" "soil file(s) that say something different"

edit $P '            "melon", "melon_seeds",' '            "melon", "pumpkin_seeds",'
expect "one seed for two sprouts" "one item plants two sprouts"

edit $P '        return soil.takes(sprout.needs());' '        return true;'
expect "every soil growing everything" "wheat grows in sand or not in farmland"

edit $H '        thePotSoils();' ''
expect "a harness that skips the pot soils" "no longer runs the pot-soil checks"

echo "the mushroom colony"
edit $KB '            () -> new com.elysium.agronomy.block.ColonyBlock(BlockBehaviour.Properties' \
         '            () -> new Block(BlockBehaviour.Properties'
expect "a colony that is a plain block" "the mushroom colony is a plain block"

edit $CB '            IntegerProperty.create("age", 0, CropRules.COLONY_MOST);' \
         '            IntegerProperty.create("age", 0, 3);'
expect "a colony's age typed in" "a colony's age is not 0 to CropRules.COLONY_MOST"

edit $CB '        int next = CropRules.colonyAfter(age, random.nextFloat());' '        int next = age + 1;'
expect "a colony that grows on every random tick" "grows on a random tick of its own"

edit $CB '        return state.getValue(KIND) > 0 && state.getValue(AGE) < CropRules.COLONY_MOST;' \
         '        return true;'
expect "a colony that ticks for ever" "stops ticking at a different place"

edit $CB '        player.getInventory().placeItemBackInInventory(new ItemStack(mushroom, ready));' ''
expect "a colony that cannot be harvested" "does not take what it grew"

edit $CB '            level.setBlock(pos, state.setValue(KIND, kind).setValue(AGE, 0), Block.UPDATE_ALL);' ''
expect "a colony that cannot be planted" "does not plant it"

edit $C '        return Math.min(1.0F, RANDOM_TICK_EVERY / COLONY_TICKS);' '        return 1.0F;'
expect "a colony on the random tick's own clock" "not COLONY_TICKS on the game's clock"

edit $C '    public static final float RANDOM_TICK_EVERY = 4096.0F / 3.0F;' \
        '    public static final float RANDOM_TICK_EVERY = 4096.0F / 9.0F;'
expect "a colony on the wrong clock" "at its default speed"

edit $A/blockstates/mushroom_colony.json '"age=3,kind=2"' '"age=3,kind=9"'
expect "a colony state with no model" "the colony's blockstate has"

edit $H '        theColony();' ''
expect "a harness that skips the colony" "no longer runs the colony's clock"

echo "crates that open, cabinets that hold"
rm $D/recipe/carrot_from_crate.json
expect "a crate that gives nothing back" "nothing gives them back"

edit $D/recipe/onion_from_crate.json '"count": 9' '"count": 8'
expect "a crate that eats a crop" "a onion crate gives back"

edit $K '    public static final int CRATE_HOLDS = 9;' '    public static final int CRATE_HOLDS = 8;'
expect "a crate that holds eight of a nine grid" "three-by-three grid of nine"

edit $KB '                    () -> new com.elysium.agronomy.block.CabinetBlock(timber()));' \
         '                    () -> new Block(timber()));'
expect "cabinets that are plain blocks" "the cabinets are plain blocks"

edit $BE '        for (String wood : KitchenBlocks.WOODS) {' '        for (String wood : java.util.List.of("oak")) {'
expect "a cabinet with no block entity" "no longer names every cabinet"

edit $CA '            return com.elysium.lib.menu.ElysiumChests.threeRows(id, inventory, this);' '            return ChestMenu.oneRow(id, inventory);'
expect "a cabinet opening some other menu" "three-row chest menu"

edit $CA '            return KitchenRules.cabinetSlots();' '            return 9;'
expect "a cabinet whose size is its own" "a cabinet's size is not"

edit $CA '            player.openMenu(cabinet);' ''
expect "a cabinet that does not open" "does not open it"

edit $CA '            Containers.dropContents(level, pos, cabinet);' ''
expect "a broken cabinet that eats its contents" "destroys what is in it"

edit $CA '            ContainerHelper.saveAllItems(tag, items, registries);' ''
expect "a cabinet that forgets" "does not save what is in it"

edit $K '    public static final int CABINET_ROWS = 3;' '    public static final int CABINET_ROWS = 2;'
expect "a cabinet of two rows in a three-row menu" "and the game's chest menu it opens"

edit $H '        theCrates();' ''
expect "a harness that skips the crates" "no longer checks that a crate gives back"

echo
echo "$pass caught, $fail missed"
[[ $fail -eq 0 ]]
