#!/usr/bin/env bash
# The way into the Vale, broken one way at a time.
#
# The Vale had no way in but an operator's command (the obtainability survey,
# 29 September 2026), so its twenty-eight creatures and two almanac quests were
# content a survival player could not have. A ring of Vale Frames lit with the
# Surveyor's Compass is the way in. Every case below undoes one piece of it and
# proves the wilds validator, or the way-in harness under it, says so.
#
# Snapshots elysium-wilds alone. negtest_wilds.sh also snapshots the root
# validate.py, and other work edits that file: a restore from a snapshot taken
# before somebody's edit silently undoes it.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-wilds
J=$M/src/main/java/com/elysium/wilds
R=$J/rules/PortalRules.java
H=$M/tools/PortalHarness.java
CI=$J/item/BiomeCompassItem.java
VP=$J/block/ValePortal.java
VF=$J/block/ValeFrameBlock.java
VB=$J/block/ValePortalBlock.java
VT=$J/level/ValeTravel.java
VL=$J/level/ValeLinks.java
VC=$J/command/ValeCommand.java
WB=$J/registry/WildBlocks.java
WI=$J/registry/WildItems.java
MW=$J/ElysiumWilds.java
G=$M/tools/gen_wilds.py
RES=$M/src/main/resources
SNAP=/tmp/wilds_portal_snap.tar
HOUT=/tmp/wilds_portal_harness
pass=0; fail=0
if ! (cd $M && timeout 900 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit."
  exit 3
fi
(cd $ROOT && tar cf $SNAP elysium-wilds)
restore() { rm -rf $ROOT/elysium-wilds; (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 900 python3 validate.py 2>&1); }
# The harness alone, for the cases that break the rules: the validator runs it
# too (the first harness case below goes through the validator to prove that),
# and seconds against minutes is thirty minutes a run. A small heap, so a flood
# that never stops runs out of room in seconds rather than of time in minutes.
hout() { rm -rf $HOUT && mkdir -p $HOUT
  javac -nowarn -d $HOUT $J/rules/*.java "$H" 2>&1 | grep -v JAVA_TOOL
  timeout 120 java -Xmx512m -cp $HOUT com.elysium.wilds.rules.PortalHarness > $HOUT/run.txt 2>&1
  local code=$?
  grep -v JAVA_TOOL $HOUT/run.txt
  if [ $code -eq 124 ] || grep -q OutOfMemoryError $HOUT/run.txt; then
    echo "the way-in harness did not finish"
  fi
  return 0; }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
expect_h() { local w="$2" g; g="$(hout)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
regen() { (cd $M && python3 tools/gen_wilds.py >/dev/null 2>&1); }
expect_regen() { regen; expect "$1" "$2"; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; restore; exit 2; }; }

echo "the rules, through the validator"
edit $R '        if (opening.size() != wide * tall) {' '        if (false) {'
expect "an opening closed in but not a rectangle, seen by the validator" "the way-in harness failed"

echo "the rules, through the harness"
edit $R '            if (opening.size() > MOST * MOST) {' '            if (false) {'
expect_h "a flood of the open sky that never stops" "the way-in harness did not finish"

edit $R '        if (opening.size() != wide * tall) {' '        if (false) {'
expect_h "an L-shaped opening lighting" "an L-shaped opening lights"

edit $R '                if (cell != Cell.OPEN) {' '                if (cell != Cell.OPEN && cell != Cell.OTHER) {'
expect_h "a block left in the opening flooded round" "with a block left inside"

edit $R '        if (wide < LEAST_ACROSS || tall < LEAST_UP || wide > MOST || tall > MOST) {' \
        '        if (wide < LEAST_ACROSS || tall < LEAST_UP) {'
expect_h "an opening wider than the Nether portal allows" "opening lights, and the rule is"

edit $R '        for (int[][] ways : new int[][][] {SIDEWAYS, CORNERWAYS}) {' \
        '        for (int[][] ways : new int[][][] {SIDEWAYS}) {'
expect_h "a ring that does not light from its corners" "does not light when the compass is used on its frame at"

edit $R '        ordered.sort(Comparator.comparingInt(Spot::up).thenComparingInt(Spot::across));' \
        '        ordered.sort(Comparator.comparingInt(Spot::up).thenComparingInt(Spot::across).reversed());'
expect_h "a ring known by a different cell than its lowest" "and its lowest, then least-across, cell is (0, 0)"

edit $R '    public static final int LANDING_OUT = 1;' '    public static final int LANDING_OUT = 0;'
expect_h "a player put down in the gate they arrive by" "which is in the gate"

edit $R '    public static final String ARRIVAL = "verdant_vale";' \
        '    public static final String ARRIVAL = "sulphur_springs";'
expect_h "a way in that drops a player three rings out" "ring(s) out from the middle"

edit $R '        return past < 0.5D ? -1 : 1;' '        return past < 0.5D ? 1 : -1;'
expect_h "the side of a ring read backwards" "read backwards"

edit $R '    public static final int NEAR_ACROSS = LEAST_ACROSS + 2;' \
        '    public static final int NEAR_ACROSS = LEAST_ACROSS;'
expect_h "a standing ring looked for nearer than two rings share frames" "share frames"

edit $R '    public static final int FRAMES_PER_CRAFT = 4;' '    public static final int FRAMES_PER_CRAFT = 3;'
expect_h "a smallest ring that is not three crafts" "the smallest ring takes 4 crafts"

edit $R '    public static final double ARRIVAL_SEARCH_BLOCKS = WildRules.MOST_SEARCH_BLOCKS;' \
        '    public static final double ARRIVAL_SEARCH_BLOCKS = 40_000.0D;'
expect_h "a way in that looks further than the compass may" "looks further than the compass"

edit $R '    public static final int DRY_SEARCH_BLOCKS = 64;' '    public static final int DRY_SEARCH_BLOCKS = 0;'
expect_h "a Vale side never moved off the water" "never moved off the water"

edit $R '    public static final int DRY_SEARCH_BLOCKS = 64;' '    public static final int DRY_SEARCH_BLOCKS = 200;'
expect_h "a dry column looked for across a hundred chunks" "a flood of chunks generated"

edit $R '        return !top.fluid() && top.sturdy();' '        return top.fluid() || !top.sturdy();'
expect_h "dry and wet read backwards" "a lake is dry"

edit $R '                columns.add(new Column(east, south));
            }
        }
        columns.sort(NEAREST_FIRST);' '                columns.add(new Column(east, south));
            }
        }'
expect_h "a dry search that is not nearest first" "the dry search is not nearest first"

edit $R '        for (Column one : drySearch()) {
            if (dry(surface, one)) {' '        for (Column one : List.of(new Column(0, 0))) {
            if (dry(surface, one)) {'
expect_h "a dry search of the one column" "has no bank within"

edit $R '        return !top.fluid() && top.sturdy();' '        return !top.fluid();'
expect_h "a fence post called ground by the rules" "a fence post is called ground"

edit $R '    public static final int FAR_DRY_SEARCH_BLOCKS = 256;' '    public static final int FAR_DRY_SEARCH_BLOCKS = 0;'
expect_h "a far stage that reaches nowhere" "looks nowhere it did not"

edit $R '    public static final int FAR_DRY_SEARCH_BLOCKS = 256;' '    public static final int FAR_DRY_SEARCH_BLOCKS = 10_000;'
expect_h "a far stage that walks ten thousand blocks of chunks" "the chunks under them"

edit $R '    public static final int FAR_DRY_STEP = 4;' '    public static final int FAR_DRY_STEP = 0;'
expect_h "a far stage that strides nowhere" "which is standing still"

edit $R '                columns.add(new Column(line[0] * out, line[1] * out));
            }
        }
        columns.sort(NEAREST_FIRST);' '                columns.add(new Column(line[0] * out, line[1] * out));
            }
        }'
expect_h "a far search that is not nearest first" "the far search is not nearest first"

edit $R '            if (out <= DRY_SEARCH_BLOCKS) {
                continue;
            }
' ''
expect_h "a far search looking where the spiral already did" "which has looked there already"

edit $R '{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};' \
        '{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};'
expect_h "a far search of seven compass lines" "does not walk all eight compass lines"

rm -f $H
expect "no way-in harness at all" "there is no way-in harness"

edit $R '    public static final int MOST = 21;' '    public static final int MOST = 25;'
expect "a Vale ring and the rift that are not one shape" "the rift's MAX_INNER is 21"

echo "the compass"
edit $CI '        if (!level.getBlockState(pos).is(WildBlocks.VALE_FRAME.get())) {
            return InteractionResult.PASS;
        }
' ''
expect "a compass that answers every block as if it were a frame" "neither opens its search nor lights anything"

edit $CI '        ValePortal.Frame frame = ValePortal.findFrame(level, pos);' '        ValePortal.Frame frame = null;'
expect "a compass that lights without finding the ring" "does not find the ring it is used on"

edit $CI '        ValePortal.light(level, frame);' '        ;'
expect "a compass that finds a ring and never lights it" "never lights it"

edit $CI '        ValePortal.light(level, frame);' '        ValePortal.light(level, frame);
        context.getItemInHand().shrink(1);'
expect "a compass used up by lighting a ring" "uses the compass up"

edit $CI '"elysiumwilds.gate.no_frame",' '"elysiumwilds.gate.nothing",'
expect "a ring one frame short refused in silence" "refused in silence"

echo "the ring in the world"
edit $VP '                    (a, u) -> cell(level.getBlockState(used.relative(along, a).above(u))), 0, 0);' \
         '                    (a, u) -> PortalRules.Cell.OPEN, 0, 0);'
expect "a ring found by something other than the rules' flood" "is not found by PortalRules.around"

edit $VP '        for (Direction.Axis axis : new Direction.Axis[] {first, second}) {' \
         '        for (Direction.Axis axis : new Direction.Axis[] {first}) {'
expect "a ring looked for one way round only" "looked for on one axis only"

edit $VP '        if (state.is(WildBlocks.VALE_FRAME.get())) {
            return PortalRules.Cell.FRAME;
        }' '        if (state.is(WildBlocks.VALE_PORTAL.get())) {
            return PortalRules.Cell.FRAME;
        }'
expect "a Vale Frame that is not a frame to the flood" "is not a frame to the flood"

edit $VP '        if (state.isAir() || state.is(WildBlocks.VALE_PORTAL.get())) {' \
         '        if (state.canBeReplaced() || state.is(WildBlocks.VALE_PORTAL.get())) {'
expect "grass and flowers in the opening counted as open" "counts as open"

edit $VP '        if (level.getBlockState(pos).hasBlockEntity()) {' '        if (false) {'
expect "a Vale side rebuilt over a player's chest" "takes the chest and everything in it"

edit $VF '            ValePortal.close(level, pos);' '            ;'
expect "a frame broken and its gate left hanging" "hanging in the air"

edit $VB '        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {' \
         '        if (!(entity instanceof ServerPlayer player)) {'
expect "a gate that moves players on the client" "moves players on the client"

edit $VB '        ValeTravel.use(player, pos);' '        ;'
expect "a gate that does nothing when stood in" "standing in a Vale Gate does nothing"

echo "the gate is a block and never an item"
edit $WI '            ITEMS.registerSimpleBlockItem(WildBlocks.VALE_FRAME);' \
         '            ITEMS.registerSimpleBlockItem(WildBlocks.VALE_FRAME);
    public static final DeferredItem<BlockItem> VALE_GATE =
            ITEMS.registerSimpleBlockItem(WildBlocks.VALE_PORTAL);'
expect "a gate a player can carry" "the Vale Gate has an item"

edit $WB '                    .noLootTable()
' ''
expect "a gate with a loot table it has nothing for" "no collision and no loot table"

cp $RES/assets/elysiumwilds/models/item/vale_frame.json $RES/assets/elysiumwilds/models/item/vale_portal.json
expect "an item model for the gate" "neither an item nor a drop"

edit $MW 'com.elysium.wilds.registry.WildBlocks.BLOCKS.register(modEventBus);' '/* */'
expect "the blocks declared and never put on the bus" "WildBlocks.BLOCKS is declared and never put on the bus"

echo "what a ring is built from"
rm -f $RES/data/elysiumwilds/recipe/vale_frame.json
expect "a Vale Frame nothing makes" "has no recipe"

edit $G '        "result": {"id": "%s:vale_frame" % NS, "count": frames_per_craft()},' \
        '        "result": {"id": "%s:vale_frame" % NS, "count": 8},'
expect_regen "a craft of frames that is not what the rules say" "a craft of Vale Frames makes 8"

edit $G '            "B": {"item": "minecraft:mossy_stone_bricks"},' '            "B": "minecraft:mossy_stone_bricks",'
expect_regen "a recipe ingredient written as a bare id" "an ingredient in 1.21 is an object"

edit $G '            "entries": [{"type": "minecraft:item", "name": "%s:vale_frame" % NS}],' \
        '            "entries": [{"type": "minecraft:item", "name": "%s:vale_portal" % NS}],'
expect_regen "a mined frame that does not drop itself" "does not drop itself"

edit $G '          {"replace": False, "values": ["%s:vale_frame" % NS]})' '          {"replace": False, "values": []})'
expect_regen "a frame off the pickaxe's list" "not on the pickaxe's list"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-wilds/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json")
d = json.loads(p.read_text(encoding="utf-8"))
d["values"].append("minecraft:stone")
p.write_text(json.dumps(d, indent=2) + "\n", encoding="utf-8")
PYX
expect "the pickaxe's list edited by hand under the generator" "minecraft tags"

edit $G '        "axis=z": {"model": "%s:block/vale_portal_ew" % NS},
' ''
expect_regen "a gate with no model one way round" "has no axis=z in its blockstate"

edit $G '        "render_type": "minecraft:translucent",
' ''
expect_regen "a see-through gate drawn on the solid layer" "not drawn on the translucent layer"

edit $G '    "elysiumwilds.gate.home": "Back where you went in.",' '    "elysiumwilds.gate.homeward": "Back where you went in.",'
expect_regen "a message that reads as its own key" "elysiumwilds.gate.home has no words"

edit $G '                "depth": 0.0,' '                "depth": round(0.1 * warmth, 4),'
expect_regen "a Vale whose crossings change with depth" "claim different depths"

echo "the trip"
edit $VT '        if (onCooldown(player, server)) {
            return;
        }
' ''
expect "a gate that sends an arrival straight back" "has just come through"

edit $VT '        if (player.level().dimension().equals(VALE)) {' \
         '        if (player.level().dimension().equals(Level.NETHER)) {'
expect "which way a gate leads read off something else" "read off the world the player is in"

edit $VT '        ValePortal.Frame frame = ValePortal.findFrame(here, gatePos);' '        ValePortal.Frame frame = null;'
expect "a trip filed under whichever block was touched" "filed under whichever block was touched"

edit $VT '        links.remember(player.getUUID(), new ValeLinks.Return(here.dimension(),
                back(player, anchor, axis, side), facing(axis, side)));' '        ;'
expect "where a player came in forgotten" "where a player came in is not remembered"

edit $VT '        ValeLinks.Arrival arrival = links.arrival(here.dimension(), anchor);' \
         '        ValeLinks.Arrival arrival = null;'
expect "a ring's Vale side never looked up" "is not looked up before a trip"

edit $VT '            links.link(here.dimension(), anchor, arrival);
' ''
expect "a ring's Vale side never filed" "no longer searches once and files"

# The first-trip line (16 Oct 2026): the live footing stage proves a ring's first
# trip, and a fresh search's footing, by this one line and by the world's own
# record of it (round 4: a named entity's death can write a copy of an old line
# into the client's log, so the line is kept in command storage too).
edit $VT '            ElysiumWilds.LOGGER.info("{}", said);
' ''
expect "a ring's first trip that says nothing" "is not said at INFO in the log"

edit $VT '            ElysiumWilds.LOGGER.info("{}", said);' '            ElysiumWilds.LOGGER.debug("{}", said);'
expect "the first-trip line said at debug, under the client log's INFO floor" "is not said at INFO in the log"

edit $VT '                    arrival.anchor().getX(), arrival.anchor().getY(), arrival.anchor().getZ(),' \
         '                    anchor.getX(), anchor.getY(), anchor.getZ(),'
expect "the first-trip line naming the ring where its Vale side belongs" "is not said at INFO in the log"

edit $VT '"Vale first trip: player %s ring %s %d %d %d side %d %d %d axis %s %s"' \
         '"Vale first trip: %s ring %s %d %d %d side %d %d %d axis %s %s"'
expect "the first-trip line reworded under the stages that read it" "not in the fixed form the live stages read"

edit $VT '            boolean standing = settled.standing();' '            boolean standing = false;'
expect "a reused ring called built" "is not the settling's own word"

# Round 4, M1: the anchor-only test, which called a ring whose anchor cell was
# broken by hand built though the search found it standing and used it.
edit $VT '            boolean standing = settled.standing();' '            boolean standing = ValePortal.stands(vale, arrival.anchor());'
expect "whether the side was standing asked of its anchor cell alone" "is not the settling's own word"

edit $VT 'new Settled(new ValeLinks.Arrival(there.anchor(), there.axis()), true);' \
         'new Settled(new ValeLinks.Arrival(there.anchor(), there.axis()), false);'
expect "a ring the search found standing said to be built" "is not said to be standing"

edit $VT '        return new Settled(on(vale, column, axis), false);' '        return new Settled(on(vale, column, axis), true);'
expect "the side built on the sea said to be a ring already standing" "has no far side at all, or the side built there"

edit $VT '        return standing != null ? standing : new Settled(on(vale, spot, axis), false);' \
         '        return standing != null ? standing : new Settled(on(vale, spot, axis), true);'
expect "the side built at the settled column said to be a ring already standing" "is built over, or the side built there"

edit $VT '            String said = String.format(Locale.ROOT, FIRST_TRIP, player.getGameProfile().getName(),
                    here.dimension().location(), anchor.getX(), anchor.getY(), anchor.getZ(),
                    arrival.anchor().getX(), arrival.anchor().getY(), arrival.anchor().getZ(),
                    arrival.axis().getName(), standing ? "standing" : "built");
            ElysiumWilds.LOGGER.info("{}", said);
            links.firstTrip(said);
        }
' '        }
        String said = String.format(Locale.ROOT, FIRST_TRIP, player.getGameProfile().getName(),
                here.dimension().location(), anchor.getX(), anchor.getY(), anchor.getZ(),
                arrival.anchor().getX(), arrival.anchor().getY(), arrival.anchor().getZ(),
                arrival.axis().getName(), "built");
        ElysiumWilds.LOGGER.info("{}", said);
        links.firstTrip(said);
'
expect "the first-trip line said and kept on every trip" "saying so once in the log"

edit $VT '        teleport(player, server, vale, Vec3.atBottomCenterOf(landing), facing(arrival.axis(), onward));
' '        teleport(player, server, vale, Vec3.atBottomCenterOf(landing), facing(arrival.axis(), onward));
        ElysiumWilds.LOGGER.info(String.format(Locale.ROOT, FIRST_TRIP, player.getGameProfile().getName(), "", 0, 0, 0, 0, 0, 0, "x", "built"));
'
expect "a second trip through a ring that logs a first trip too" "named somewhere other than its one declaration"

edit $VT '        teleport(player, server, vale, Vec3.atBottomCenterOf(landing), facing(arrival.axis(), onward));
' '        teleport(player, server, vale, Vec3.atBottomCenterOf(landing), facing(arrival.axis(), onward));
        ElysiumWilds.LOGGER.info("Vale first trip: player {} ring {} 0 0 0 side 0 0 0 axis x built", player.getGameProfile().getName(), "");
'
expect "a second trip that writes the first-trip line out by hand" "named somewhere other than its one declaration"

# Round 4, F1v and F1n, and round 5, OP1: the world's own record of first trips,
# which the footing stage reads before the gate and after the crossing, so a copy
# of an old line written into the log by a named entity's death is not taken for
# a first trip. Kept in ValeLinks since round 5: command storage, where it was,
# is what any operator's /data modify writes.
edit $VT '            links.firstTrip(said);
' ''
expect "a first trip not kept in the world's record" "is not kept in the world's own record of first trips"

edit $VT '        teleport(player, server, vale, Vec3.atBottomCenterOf(landing), facing(arrival.axis(), onward));
' '        teleport(player, server, vale, Vec3.atBottomCenterOf(landing), facing(arrival.axis(), onward));
        links.firstTrip("");
'
expect "every trip kept in the record of first trips" "is not kept in the world's own record of first trips"

edit $VT '            links.firstTrip(said);
' '            links.firstTrip(said);
            server.getCommandStorage().set(ResourceLocation.fromNamespaceAndPath(ElysiumWilds.MODID, "trips"), new net.minecraft.nbt.CompoundTag());
'
expect "the first trips kept in command storage too, where /data modify writes" "is kept in command storage, which any operator"

edit $VL '    /** How many first trips the world has had. */' '    public void mirror(MinecraftServer server) {
        server.getCommandStorage().set(ResourceLocation.fromNamespaceAndPath("elysiumwilds", "trips"), new CompoundTag());
    }

    /** How many first trips the world has had. */'
expect "ValeLinks copying its record into command storage" "is kept in command storage, which any operator"

edit $VL '        firstTrips += 1;' '        firstTrips = 1;'
expect "the record's count not advanced" "does not count one more for each first trip"

edit $VL '        lastFirstTrip = said;' '        lastFirstTrip = "";'
expect "the record without the line" "does not keep the line the latest one logged"

edit $VL '        lastFirstTrip = said;
        setDirty();' '        lastFirstTrip = said;'
expect "a first trip kept and never marked to be saved" "a first trip kept in the record is not marked to be saved"

# Round 5, RZ1: each way out of the Vale to the world's spawn, counted for the
# player in the same record, which the vale stage reads before the gate and at
# home: the log line saying so is lost with a log that rolled over at midnight.
edit $VL '        outToSpawn.merge(player, 1, Integer::sum);' '        outToSpawn.put(player, 1);'
expect "a player's ways out to the spawn counted as one for ever" "is not counted one more for the player"

edit $VL '        outToSpawn.merge(player, 1, Integer::sum);
        setDirty();' '        outToSpawn.merge(player, 1, Integer::sum);'
expect "a way out to the spawn counted and never marked to be saved" "counted in the record is not marked to be saved"

edit $VL '        return firstTrips;' '        return 0;'
expect "the record's first trips read back as nought" "firstTrips does not read back what the record keeps"

edit $VL '        return lastFirstTrip;' '        return "";'
expect "the record's last line read back as nothing" "lastFirstTrip does not read back what the record keeps"

edit $VL '        return outToSpawn.getOrDefault(player, 0);' '        return 0;'
expect "a player's ways out to the spawn read back as nought" "outToSpawnFor does not read back what the record keeps"

edit $VL '        tag.putInt("FirstTrips", firstTrips);
' ''
expect "the record's first trips never saved" "is not saved and read back whole"

edit $VL '        tag.putString("LastFirstTrip", lastFirstTrip);
' ''
expect "the record's last line never saved" "is not saved and read back whole"

edit $VL '            one.putInt("Count", entry.getValue());
' ''
expect "a player's ways out to the spawn saved without their number" "is not saved and read back whole"

edit $VL '        tag.put("OutToSpawn", spawned);
' ''
expect "the ways out to the spawn never saved" "is not saved and read back whole"

edit $VL '        data.firstTrips = tag.getInt("FirstTrips");
' ''
expect "the record's first trips never read back" "is not saved and read back whole"

edit $VL '        data.lastFirstTrip = tag.getString("LastFirstTrip");
' ''
expect "the record's last line never read back" "is not saved and read back whole"

edit $VL '        ListTag spawned = tag.getList("OutToSpawn", Tag.TAG_COMPOUND);' '        ListTag spawned = new ListTag();'
expect "the ways out to the spawn never read back" "is not saved and read back whole"

edit $VL '                data.outToSpawn.put(one.getUUID("Player"), one.getInt("Count"));
' ''
expect "a player's ways out to the spawn read and dropped" "is not saved and read back whole"

edit $VT '            links.outToSpawn(player.getUUID());
' ''
expect "a way out to the spawn not counted" "is not counted in the world's record, once"

edit $VT '        teleport(player, server, home, back.where(), back.facing());' '        links.outToSpawn(player.getUUID());
        teleport(player, server, home, back.where(), back.facing());'
expect "the way back counted as a way out to the spawn" "is not counted in the world's record, once"

edit $VT '        ValeLinks links = ValeLinks.get(server);
        ValeLinks.Return back = links.returnFor(player.getUUID());' '        ValeLinks links = new ValeLinks();
        ValeLinks.Return back = ValeLinks.get(server).returnFor(player.getUUID());'
expect "a way out to the spawn counted in a record the world does not keep" "is not counted in the world's record, once"

# Round 5, OP1 and RZ1: /elysium vale trips, the one way the live stages read the
# record, in one fixed line, for operators, and writing nothing.
rm -f $VC
expect "no command to read the record" "there is no /elysium vale trips"

edit $VC '        event.getDispatcher().register(root());
' ''
expect "the record's command written and never registered" "is written and never registered"

edit $VC '@EventBusSubscriber(modid = ElysiumWilds.MODID)
' ''
expect "the record's command off the bus" "is written and never registered"

edit $VC 'Commands.literal("trips")' 'Commands.literal("trip")'
expect "the record's command under another name" "is not read by /elysium vale trips, for operators"

edit $VC '                .requires(source -> source.hasPermission(2))
' ''
expect "the record read by anybody at all" "is not read by /elysium vale trips, for operators"

edit $VC '"Vale trips: first %d, out to spawn %d, last %s"' '"Vale trips: %d first, out to spawn %d, last %s"'
expect "the record's line reworded under the stages that read it" "not in the fixed form the live stages read (Vale trips"

edit $VC '        ServerPlayer player = source.getPlayerOrException();' '        ServerPlayer player = source.getServer().getPlayerList().getPlayers().get(0);'
expect "the ways out to the spawn answered for some other player" "does not answer the world's record as it stands"

edit $VC '        ValeLinks links = ValeLinks.get(source.getServer());' '        ValeLinks links = new ValeLinks();'
expect "the record answered from an empty one" "does not answer the world's record as it stands"

edit $VC '? "none" : links.lastFirstTrip();' '? "" : links.lastFirstTrip();'
expect "no first trip yet answered as an empty last line" "does not answer the world's record as it stands"

edit $VC 'links.outToSpawnFor(player.getUUID()), last);' '0, last);'
expect "the ways out to the spawn answered as nought" "does not answer the world's record as it stands"

edit $VC '        source.sendSuccess(() -> Component.literal(said), false);
' ''
expect "the record never answered" "does not answer the world's record as it stands"

edit $VC '        source.sendSuccess(() -> Component.literal(said), false);' '        source.sendSuccess(() -> Component.literal(said), false);
        links.firstTrip(said);'
expect "the record's command adding a first trip to what it reads" "writes to what it reads"

edit $VC '        source.sendSuccess(() -> Component.literal(said), false);' '        source.sendSuccess(() -> Component.literal(said), false);
        links.setDirty();'
expect "the record's command marking what it reads to be saved" "writes to what it reads"

edit $VT '        if (!ValePortal.stands(vale, arrival.anchor())) {' '        if (false) {'
expect "a Vale side never built" "arrives with no way back"

edit $VT '                onward * PortalRules.LANDING_OUT);' '                0);'
expect "a player put down in the far ring" "not put down beside the ring"

edit $VT '        teleport(player, server, vale, Vec3.atBottomCenterOf(landing), facing(arrival.axis(), onward));' \
         '        teleport(player, server, here, Vec3.atBottomCenterOf(landing), facing(arrival.axis(), onward));'
expect "a way in that goes nowhere" "does not take a player to the Vale"

edit $VT '        int radius = (int) PortalRules.ARRIVAL_SEARCH_BLOCKS;' '        int radius = 64000;'
expect "a way in looking further than the rules allow" "looks as far as something other than the rules say"

edit $VT '        int step = (int) Math.round(PortalRules.arrivalStep());' '        int step = 4;'
expect "a way in sampling finer than the cost proved" "the cost the harness proved is not the cost the server pays"

edit $VT 'radius, step, vale.getHeight());' 'radius, step, 8);'
expect "a way in sampling every height of every column" "at one height from the ring's own"

edit $VT '        int middle = vale.getMinBuildHeight() + vale.getHeight() / 2;' '        int middle = 400;'
expect "a way in searching from above the top of the world" "finds nothing at all"

edit $VT '        PortalRules.Column dry = PortalRules.nearestDry(footing(vale, column));' \
         '        PortalRules.Column dry = null;'
expect "a Vale side built on the water" "built on the water and the player lands in it"

edit $VT '        return !vale.getFluidState(under).isEmpty();' '        return false;'
expect "every column dry as far as the trip can tell" "a lake can count as ground again"

edit $VT '        return vale.getBlockState(under).isFaceSturdy(vale, under, Direction.UP);' '        return true;'
expect "a fence post called ground by the trip" "holds nobody up is called ground"

edit $VT '        return new Settled(on(vale, column, axis), false);
    }' '        return null;
    }'
expect "no settling for the column found when all of it is sea" "all sea has no far side at all"

edit $VT '        Settled standing = standingBy(vale, groundAt(vale, column));
        if (standing != null) {
            return standing;
        }
        PortalRules.Column dry' '        Settled standing = null;
        PortalRules.Column dry'
expect "a ring already standing built over" "built over it and breaks it"

edit $VT '        PortalRules.Column far = PortalRules.nearestFarDry(footing(vale, column));' \
         '        PortalRules.Column far = null;'
expect "the far stage dropped from the trip" "a hundred blocks from land"

edit $VT '            near = near(vale, column.offset(shore.east(), 0, shore.south()), axis);' \
         '            near = new Settled(on(vale, column.offset(shore.east(), 0, shore.south()), axis), false);'
expect "a far shore built on without settling" "somewhere other than the shore found"

echo "the footing"
edit $VT '            PortalRules.Column shore = PortalRules.settled(footing(vale, column), far, axisOf(axis));' \
         '            PortalRules.Column shore = far;'
expect "a far shore's ring left astride the waterline" "the shore the far stage finds is not walked on along its line"

edit $VT '        PortalRules.Column site = PortalRules.settled(footing(vale, column), dry, axisOf(axis));' \
         '        PortalRules.Column site = dry;'
expect "the spiral's dry column left astride the waterline" "the dry column the spiral finds is not walked on along its line"

edit $VT '        return axis == Direction.Axis.X ? PortalRules.Axis.X : PortalRules.Axis.Z;' \
         '        return axis == Direction.Axis.X ? PortalRules.Axis.Z : PortalRules.Axis.X;'
expect "a ring's axis read into the rules the wrong way round" "the side posts' columns that are kept dry"

edit $R '        for (int row : new int[] {-LANDING_OUT, 0, LANDING_OUT}) {' \
        '        for (int row : new int[] {0, LANDING_OUT}) {'
expect_h "a footprint missing a landing row" "is not the sill and both landings"

edit $R '            if (!dry(surface, new Column(column.east() + one.east(), column.south() + one.south()))) {' \
        '            if (dry(surface, new Column(column.east() + one.east(), column.south() + one.south()))) {'
expect_h "the footing test read backwards" "dry ground is not a dry footing"

edit $R '    public static final int SETTLE_IN_BLOCKS = 12;' '    public static final int SETTLE_IN_BLOCKS = 0;'
expect_h "a ring never walked off the waterline" "stays astride it"

edit $R '    public static final int SETTLE_IN_BLOCKS = 12;' '    public static final int SETTLE_IN_BLOCKS = 1_000;'
expect_h "a ring walked a thousand blocks of chunks inland" "two chunks on from the shore"

edit $R '        for (int stride = 0; stride <= SETTLE_IN_BLOCKS; stride++) {' \
        '        for (int stride = 0; stride <= SETTLE_IN_BLOCKS * 3; stride++) {'
expect_h "a settling that walks past its reach" "past 12"

edit $VT '        Settled near = near(vale, column, axis);
        if (near != null) {
            return near;
        }
' '        Settled near;
'
expect "the far stage before the near one" "walked off to a far shore"

edit $VT '                ElysiumWilds.MODID, PortalRules.arrivalBiome()));' \
         '                ElysiumWilds.MODID, "vale_sulphur_springs"));'
expect "a way in looking for somewhere other than the gentlest crossing" "not the one PortalRules names"

edit $VT '        teleport(player, server, home, back.where(), back.facing());' \
         '        teleport(player, server, server.overworld(), back.where(), back.facing());'
expect "a way back to somewhere other than where they came in" "does not put a player where they came in"

edit $VT '            teleport(player, server, overworld,
                    Vec3.atBottomCenterOf(overworld.getSharedSpawnPos()), player.getYRot());' '            ;'
expect "somebody with no way in on record left standing in the gate" "goes nowhere through a gate"

edit $VT '                + 0.5D + side * PortalRules.LANDING_OUT;' '                + 0.5D;'
expect "a way back into the gate itself" "somewhere other than in front of the ring"

edit $VT '        cooldowns.put(player.getUUID(), server.getTickCount() + PortalRules.COOLDOWN_TICKS);' ''
expect "a trip that starts no cooldown" "does not start the cooldown the rules state"

edit $VT '        if (left <= 0 || left > PortalRules.COOLDOWN_TICKS) {' '        if (left <= 0) {'
expect "a cooldown from an earlier world obeyed in this one" "left over from an earlier run of the server"

edit $VL '        tag.put("Arrivals", links);' ''
expect "the rings' Vale sides forgotten at a restart" "not both saved and read back"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all wilds checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN (validator)"; fail=$((fail+1)); fi

echo "wilds portal: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
