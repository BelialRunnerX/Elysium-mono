#!/usr/bin/env bash
# The digging, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-arsenal
D=$M/src/main/java/com/elysium/arsenal/rules/DigRules.java
F=$M/src/main/java/com/elysium/arsenal/rules/FossilRules.java
A=$M/src/main/java/com/elysium/arsenal/rules/ArtifactRules.java
T=$M/src/main/java/com/elysium/arsenal/rules/TotemRules.java
H=$M/tools/DigHarness.java
SE=$M/src/main/java/com/elysium/arsenal/block/entity/SiteBlockEntity.java
BI=$M/src/main/java/com/elysium/arsenal/item/BrushItem.java
FE=$M/src/main/java/com/elysium/arsenal/event/FossilEffects.java
AE=$M/src/main/java/com/elysium/arsenal/event/ArtifactEffects.java
TB=$M/src/main/java/com/elysium/arsenal/block/TotemBlock.java
DB=$M/src/main/java/com/elysium/arsenal/registry/DigBlocks.java
DI=$M/src/main/java/com/elysium/arsenal/registry/DigItems.java
BE=$M/src/main/java/com/elysium/arsenal/registry/DigBlockEntities.java
MC=$M/src/main/java/com/elysium/arsenal/ElysiumArsenal.java
G=$M/tools/gen_arsenal.py
V=$M/validate.py
R=$M/src/main/resources
SNAP=/tmp/dig_snap
OUT=/tmp/dig_out
pass=0; fail=0
rm -rf $SNAP && mkdir -p $SNAP
cp -a $M/src/main/java/com/elysium/arsenal $SNAP/arsenal
cp -a $M/tools $SNAP/tools
cp -a $M/src/main/resources $SNAP/resources
cp -a $M/validate.py $SNAP/validate.py
restore() {
  rm -rf $M/src/main/java/com/elysium/arsenal $M/tools $M/src/main/resources
  cp -a $SNAP/arsenal $M/src/main/java/com/elysium/arsenal
  cp -a $SNAP/tools $M/tools
  cp -a $SNAP/resources $M/src/main/resources
  cp -a $SNAP/validate.py $M/validate.py
}
out() {
  rm -rf $OUT && mkdir -p $OUT
  if ! javac -nowarn -d $OUT $M/src/main/java/com/elysium/arsenal/rules/*.java "$H" \
      $ROOT/elysium-lib/src/main/java/com/elysium/lib/ability/ElysiumAbilityRules.java \
      2>&1 | grep -v JAVA_TOOL; then :; fi
  java -cp $OUT com.elysium.arsenal.rules.DigHarness 2>&1 | grep -v JAVA_TOOL
}
worldout() { python3 "$V" 2>&1 | grep -v JAVA_TOOL; }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-140)"; fail=$((fail+1)); fi; restore; }
wexpect() { local w="$2" g; g="$(worldout)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-140)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }
regen() { (cd $M && python3 tools/gen_arsenal.py >/dev/null 2>&1); }

echo "the one function everything about sites rests on"
edit $D '        mixed ^= (mixed >>> 33);
        mixed *= 0xFF51AFD7ED558CCDL;
        mixed ^= (mixed >>> 33);' '        mixed = seed + x + y + z;'
expect "a hash that barely stirs, so runs of blocks hold the same thing" "barely stirs"

edit $D '        long mixed = seed;' '        long mixed = 0L;'
expect "loot that is the same in every world ever made" "barely a function of the seed"

edit $D '        int landed = (int) Math.floorMod(mixed, weightOf(pool));' '        int landed = (int) (mixed % weightOf(pool));'
expect "negative coordinates all landing on the first find" "should have turned up about"

edit $D '        for (Find one : finds) {
            landed -= one.weight();' '        for (Find one : finds.subList(1, finds.size())) {
            landed -= one.weight();'
expect "a pool with a find that can never come up" "never once buried"

echo "the brushes"
edit $D 'new Brush("diamond_brush", 6, 4.5F),' 'new Brush("diamond_brush", 9, 4.5F),'
expect "a rung that buys nothing" "buy nothing"

edit $D '    public static final int FASTEST = 4;' '    public static final int FASTEST = 1;'
expect "a floor low enough that brushing stops being slow" "about as long as breaking a block"

edit $D 'new Brush("iron_brush", 8, 4.0F),' 'new Brush("iron_brush", 4, 4.0F),'
expect "a first rung that already deletes the game's own brush" "crafting ingredient"

echo "the sites"
edit $D 'new Site("fossiliferous_dirt", "minecraft:coarse_dirt", "fossil"));' 'new Site("fossiliferous_dirt", "minecraft:coarse_dirt", "nothing_at_all"));'
expect "a site holding a pool nothing fills" "nothing is in it"

edit $D 'new Find("minecraft:bone", 30),' 'new Find("minecraft:bone", 3000),'
expect "a find so rare it may as well not exist" "it is absent"

edit $D 'new Find("minecraft:flint", 24),' 'new Find("minecraft:bone", 24),'
expect "one find entered into a pool twice" "twice"

echo "the fossils"
edit $F 'new Fossil("wolf", "skeletons_keep_away", "fear", 1.0F, 8),' 'new Fossil("wolf", "creepers_keep_away", "fear", 1.0F, 8),'
expect "two fossils that are one fossil in two shapes" "the other in a different shape"

edit $F 'new Fossil("wolf", "skeletons_keep_away", "fear", 1.0F, 8),' 'new Fossil("wolf", "skeletons_keep_away", "fear", 0.5F, 4),'
expect "a fossil a player digs past" "digs past"

edit $F '    public static final float HALF = 0.4F;' '    public static final float HALF = 0.5F;'
expect "halves worth as much apart as together" "pairing them buys nothing"

edit $F '    public static final float HALF = 0.4F;' '    public static final float HALF = 0.0F;'
expect "a half that does nothing at all" "has found a decoration"

edit $F 'new Fossil("creeper", "blasts_hurt_less", "body", 0.5F, 6),' 'new Fossil("creeper", "blasts_hurt_less", "alone", 0.5F, 6),'
expect "a sort of fossil with only one fossil in it" "can never run on that sort"

echo "the artefacts"
edit $A 'new Artifact("tunneling", "blocks_taken", 1.0F, 1, "tool"));' 'new Artifact("tunneling", "armour_ignored", 0.06F, 1, "melee"));'
expect "an artefact that is a worse version of another" "artefacts that are worse versions of another"

edit $A '    public static final int SHARDS_PER_ARTIFACT = 9;' '    public static final int SHARDS_PER_ARTIFACT = 1;'
expect "a shard that is already an artefact" "no collecting to do"

edit $A '        return ARTIFACTS.get(Math.floorMod(pick, ARTIFACTS.size()));' '        return ARTIFACTS.get(Math.abs(pick % Math.max(1, ARTIFACTS.size() - 1)));'
expect "an artefact that can never be identified into" "never came out of"

echo "the totems"
edit $T 'new Totem("growth_totem", PLACED, 1.0F, 6, 200),' 'new Totem("growth_totem", PLACED, 1.0F, 6, 40),'
expect "a placed totem that never stops and does too much" "a minute for ever"

edit $T 'new Totem("torrent_totem", HELD, 3.0F, 1, 0),' 'new Totem("torrent_totem", HELD, 1.0F, 1, 0),'
expect "a held totem nobody would carry" "nobody would carry"

edit $T '    public static final List<Totem> TOTEMS = List.of(
            new Totem("growth_totem", PLACED, 1.0F, 6, 200),
            new Totem("radiance_totem", PLACED, 1.5F, 12, 400),' '    public static final List<Totem> TOTEMS = List.of('
expect "a sort of totem with nothing in it" "nothing to compare"

echo "the world the digging is actually in"
edit $SE '        DigRules.Find find = DigRules.whatIsBuried(pool, level.getSeed(),
                getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ());' '        DigRules.Find find = DigRules.whatIsBuried(pool,
                level.getRandom().nextLong(), 0, 0, 0);'
wexpect "a site a player can break and re-roll" "break the block, put it back and roll"

edit $SE '        tag.putInt("stage", stage);' '        tag.putInt("not_the_stage", stage);'
wexpect "a dig that starts again every time you walk away" "walks away and comes back starts again"

edit $BI '        if (!entity.brushFor(brush.ticks())) {' '        if (!entity.brushFor(8)) {'
wexpect "three brushes that all work at one speed" "behave identically"

edit $BI '        level.setBlock(pos, site.leavesBehind(), Block.UPDATE_ALL);' '        level.setBlock(pos, state, Block.UPDATE_ALL);'
wexpect "a finished site that can be brushed for ever" "brushed for ever"

edit $BI '        BlockPos pos = aimedAt(player);' '        BlockPos pos = player.blockPosition();'
wexpect "a site brushed from across the room" "from across the room"

edit $FE '        if (event.getServer().getTickCount() % EVERY != 0) {
            return;
        }' '        if (false) {
            return;
        }'
wexpect "four and a half thousand blocks searched twenty times a second" "twenty times a second"

edit $FE '            float worth = FossilRules.given(one,
                    skulls.containsKey(one.creature()),
                    bodies.containsKey(one.creature()));' '            float worth = skulls.containsKey(one.creature()) ? one.power() : 0.0F;'
wexpect "a pair worth no more in the game than the arithmetic says" "about a different fossil"

edit $TB '        level.scheduleTick(pos, this, totem.every());
        switch (totem.name()) {' '        switch (totem.name()) {'
wexpect "a placed totem that acts once and stops" "acts once and stops"

edit $AE '        return found.at(ForgeComponents.artifactLevel(stack));' '        return found.power();'
wexpect "an artefact that does the same at every depth" "read out of its own rules"

edit $AE '        if (DIGGING.get()) {
            return;
        }' '        if (false) {
            return;
        }'
wexpect "one swing that digs to bedrock" "digs to bedrock"

edit $AE '        if (!tool.isCorrectToolForDrops(under)) {
            return;
        }' '        if (false) {
            return;
        }'
wexpect "a tunnelling pickaxe that takes dirt" "every tool at once"

edit $DB '        if (made.size() != FossilRules.catalogue().size()) {' '        if (false) {'
wexpect "a fossil registry that does not count what it registered" "counts what the block registry registered"

edit $DB '        for (FossilRules.Fossil one : FossilRules.FOSSILS) {
            for (String part : FossilRules.PARTS) {' '        for (FossilRules.Fossil one : FossilRules.FOSSILS.subList(0, 1)) {
            for (String part : FossilRules.PARTS) {'
wexpect "fossils listed by hand rather than walked from the roster" "not registered from their own roster"

edit $DI '        for (Map.Entry<String,
                DeferredBlock<net.minecraft.world.level.block.Block>> row
                : blocks.entrySet()) {' '        for (Map.Entry<String,
                DeferredBlock<net.minecraft.world.level.block.Block>> row
                : java.util.List.<Map.Entry<String, DeferredBlock<net.minecraft.world.level.block.Block>>>of()) {'
wexpect "blocks that exist, generate and cannot be picked up" "cannot be picked up"

edit $BE '        return DigBlocks.SITES.values().stream()
                .map(DeferredHolder::get).toArray(Block[]::new);' '        return new Block[]{DigBlocks.SITES.get("suspicious_dirt").get()};'
wexpect "a site whose block entity drops the moment anybody looks at it" "typed by hand"

edit $MC '        com.elysium.arsenal.registry.DigBlockEntities.TYPES.register(modEventBus);' '        // unregistered'
wexpect "a block entity written and never registered" "none of it exists"

edit $G '    base = noisy(BLOCK_PALETTES["fossiliferous_dirt"], 3 + steady(creature) % 89)' '    base = noisy(BLOCK_PALETTES["fossiliferous_dirt"], 3 + hash(creature) % 89)'
regen
wexpect "a generator that draws something different every run" "differ from what the generator writes"

edit $G '            variants[f"dusted={stage}"] = {"model": f"{NS}:block/{id}"}' '            if stage < whole - 1:
                variants[f"dusted={stage}"] = {"model": f"{NS}:block/{id}"}'
regen
wexpect "a dig whose last stage has no model" "stages drawn"

echo "the dig in a survival world: what the rules promise"
edit $D '            fossil.add(new Find(OURS + one.id(FossilRules.BODY), HALF_A_FOSSIL));
' ''
expect "a fossil whose body no site gives up" "can never be made whole"

edit $D '                new Find("elysiumarsenal:torrent_totem", 3),
' ''
expect "a totem no pool holds" "a totem no pool holds"

edit $D '                new Find("elysiumarsenal:rotten_log", 10),
' ''
expect "a rotten log no pool holds" "promised by the dig and in no pool"

edit $D '                            "minecraft:meadow")),
            new Patch("suspicious_red_sand", 6, 1, 2,
                    List.of("minecraft:red_sand"),
                    List.of("#minecraft:is_badlands")));' '                            "minecraft:meadow")));'
expect "a site nothing lays in any world" "sites no world holds"

edit $D '            if (most > WIDEST_PATCH) {' '            if (false) {'
expect "a patch wider than the game loads, let through" "impossible placements were refused"

edit $D '            if (!laid.containsKey(site.block())) {' '            if (false) {'
expect "a check for sites in no world that can never report one" "with no patches at all"

edit $D '            if (!held.contains(OURS + name)) {' '            if (false) {'
expect "a check for promised finds that can never report one" "an empty set of pools"

echo "the dig in a survival world: what ships"
rm -f $R/data/elysiumarsenal/neoforge/biome_modifier/fossiliferous_dirt.json
wexpect "a site added to no biome" "is added to no biome"

edit $G 'DIG_STEP = "underground_ores"' 'DIG_STEP = "underground_ore"'
regen
wexpect "a site laid at a step the game does not have" "not one of the game's eleven"

edit $G '          {"replace": False, "values": biomes})' '          {"replace": False, "values": []})'
regen
wexpect "a site laid in a biome tag that names nothing" "which names no biome"

edit $G '                {"type": "minecraft:random_offset", "xz_spread": 0, "y_spread": -1},
                {"type": "minecraft:biome"}]})' '                {"type": "minecraft:random_offset", "xz_spread": 0, "y_spread": -1}]})'
regen
wexpect "a patch placed with no biome filter" "no biome filter"

edit $G '                                 "state": {"Name": f"{NS}:{site}"}},
                    "rules": []},' '                                 "state": {"Name": "minecraft:coarse_dirt"}},
                    "rules": []},'
regen
wexpect "a feature that lays something other than its site" "has no feature that lays it down"

edit $D '        for (FossilRules.Fossil one : FossilRules.FOSSILS) {
            fossil.add(' '        for (FossilRules.Fossil one : FossilRules.FOSSILS.subList(0, 1)) {
            fossil.add('
wexpect "a fossil pool that walks one creature of seven" "walked from the fossil roster"

edit $D '                new Find("elysiumarsenal:growth_totem", 2),
' ''
wexpect "a totem left out of every pool" "held by no pool in DigRules"

edit $D 'new Find("elysiumarsenal:artifact_shard", 12),' 'new Find("elysiumarsenal:artifact_shards", 12),'
wexpect "a pool entry naming nothing the game has" "nothing the game has"

edit $G '                            {"item": f"{NS}:{creature}_fossil_body"}],' '                            {"item": f"{NS}:{creature}_fossil_head"}],'
regen
wexpect "a whole fossil made from two skulls" "cannot be made from its skull"

edit $G '        key = {"#": {"item": f"{NS}:rotten_planks"}}' '        key = {"#": {"item": "minecraft:oak_planks"}}'
regen
wexpect "a rotten set made of oak" "not made from what a dig turns up"

edit $G '        "ingredient": {"item": "minecraft:mud_bricks"},' '        "ingredient": {"item": "minecraft:bricks"},'
regen
wexpect "cracked mud bricks smelted from something else" "not smelted from the game's own mud bricks"

edit $DI '    // There was a disc here, retired on 29 September 2026: a plain item with' '    public static final DeferredItem<Item> DISC = ITEMS.register("music_disc_relic",
            () -> new Item(new Item.Properties().stacksTo(1)));
    // There was a disc here, retired on 29 September 2026: a plain item with'
wexpect "the songless disc registered again" "and it is back"

edit $D '                new Find("minecraft:music_disc_relic", 4),
' ''
wexpect "a dirt pool without the game's own relic disc" "no longer holds the game's own relic disc"

cp $R/assets/elysiumarsenal/models/item/iron_brush.json $R/assets/elysiumarsenal/models/item/stray_brush.json
wexpect "a shipped file the generator does not write" "the generator no longer writes"

echo "the two sets, shaped"
edit $G '        state = {"variants": {"type=bottom": {"model": f"{NS}:block/{id}"},
                              "type=top": {"model": f"{NS}:block/{id}_top"},
                              "type=double": {"model": cut}}}' '        state = {"variants": {"": {"model": f"{NS}:block/{id}"}}}'
regen
wexpect "a slab drawn as one cube for every state, as all of them were" "is one model for every state"

edit $G '                if half == "top" and shape.endswith("_right"):
                    turn = (y + 90) % 360
' ''
regen
wexpect "stairs whose upside-down corners turn like the right-way-up ones" "states are not the game's own"

edit $DB '                case "_slab" -> new SlabBlock(rotten());' '                case "_slab" -> new Block(rotten());'
wexpect "a slab drawn as a slab and registered as a plain block" "and drawn as a SlabBlock"

edit $G '        loot = door_drops(id)' '        loot = drops(id)'
regen
wexpect "a door that drops from both its halves" "every door broken is two doors"

edit $G '    pickaxe = [f"{creature}_fossil{part}" for creature, _, _, _, _ in boned
               for part in ("_head", "_body", "")]' '    pickaxe = []'
regen
wexpect "fossils no pickaxe gets anything from" "on no tool's list"

echo
echo "restoring and re-checking"
restore
if python3 "$V" >/dev/null 2>&1; then echo "  tree restored clean"; else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "dig: $pass caught, $fail missed"
[[ $fail -eq 0 ]] || exit 1
