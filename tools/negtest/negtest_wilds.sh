#!/usr/bin/env bash
# The game's own biome placement, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-wilds
R=$M/src/main/java/com/elysium/wilds/rules/WildRules.java
H=$M/tools/WildHarness.java
C=$ROOT/check_worldgen.py
V=$ROOT/validate.py
EB=$ROOT/elysium-space/src/main/resources/data/elysiumspace/dimension/emerald_bastion.json
N=$M/src/main/java/com/elysium/wilds/network/WildNetwork.java
P=$M/src/main/java/com/elysium/wilds/network/WildPayloads.java
I=$M/src/main/java/com/elysium/wilds/item/BiomeCompassItem.java
CH=$M/src/main/java/com/elysium/wilds/client/WildsClientHooks.java
W=$M/src/main/java/com/elysium/wilds/client/WildsClient.java
G=$M/tools/gen_wilds.py
SC=$M/src/main/java/com/elysium/wilds/client/CompassScreen.java
SNAP=/tmp/wilds_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-wilds check_worldgen.py validate.py \
   elysium-space/src/main/resources/data/elysiumspace/dimension)
restore() { rm -rf $ROOT/elysium-wilds \
  $ROOT/elysium-space/src/main/resources/data/elysiumspace/dimension
  (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
worldout() { (cd $ROOT && timeout 400 python3 check_worldgen.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
regen() { (cd $M && python3 tools/gen_wilds.py >/dev/null 2>&1); }
expect_regen() { regen; expect "$1" "$2"; }
expect_world() { local w="$2" g; g="$(worldout)"
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

echo "the metric, which is not allowed to be a good idea of ours"
edit $R '            return past > 0.0D ? past : Math.max(before, 0.0D);' \
        '            return Math.abs(value - (low + high) / 2.0D);'
expect "a climate inside a box costing something, which is a different game" "costs nothing"

edit $R '            double before = low - value;' '            double before = 0.0D;'
expect "a span measured to one edge only" "measured to both edges"

edit $R '                total += apart * apart;' '                total += apart;'
expect "a metric that adds distances instead of squares" "a different metric from the game"

edit $R '            return total + offset * offset;' '            return total;'
expect "an offset that does nothing at all" "no longer the seventh axis"

edit $R '            if (apart < closest) {' '            if (apart <= closest) {'
expect "ties going to whoever was declared last" "ties no longer go to whichever biome"

edit $R '                if (mine.low() < theirs.low() || mine.high() > theirs.high()) {' \
        '                if (mine.low() < theirs.low()) {'
expect "containment checked on one edge of each axis" "is blind"

edit $R '    public static final int AXES = 6;' '    public static final int AXES = 5;'
expect "a climate space with an axis missing" "stopped being six-dimensional"

edit $R '            "temperature", "humidity", "continentalness", "erosion", "depth", "weirdness");' \
        '            "temperature", "humidity", "erosion", "continentalness", "depth", "weirdness");'
expect "the axes in an order that is not the game's" "in the game's order"

echo "the theorem"
edit $R '            for (int earlier = 0; earlier < later; earlier++) {' \
        '            for (int earlier = 0; earlier < regions.size(); earlier++) {'
expect "a claim compared against claims declared after it" "compared only against claims declared before"

edit $R '                if (!inner.biome().equals(outer.biome()) && inner.inside(outer)) {' \
        '                if (inner.inside(outer)) {'
expect "a biome's own second band reported as swallowing it" "must not read as the bug"

echo "the numbers that are decisions"
edit $R '        if (mostOfIt > NOTHING_IS_A_WORLD_ABOVE) {' '        if (false) {'
expect "a world allowed to claim any ceiling it likes" "passes by saying so"

edit $R '            if (share > mostOfIt) {' '            if (false) {'
expect "a world that never has to be the world it claimed" "the only thing making the claim worth writing"

edit $R '    public static final double WORLD_GRAIN = 1_400.0D;' \
        '    public static final double VANISHING_FRACTION = 0.1D;
    public static final double WORLD_GRAIN = 1_400.0D;'
expect "a share floor creeping back in after a river failed it" "for being a river"

edit $R '    public static final double FURTHEST_WORTH_WALKING = 20_000.0D;' \
        '    public static final double FURTHEST_WORTH_WALKING_NOW = 20_000.0D;'
expect "a decision constant quietly deleted" "is a decision rather than the game's"

edit $R '        return WORLD_GRAIN / Math.sqrt(share);' '        return WORLD_GRAIN / share;'
expect "a distance that scales with share instead of its square root" "area is what scales"

edit $R '        if (share <= 0.0D) {' '        if (false) {'
expect "a dead biome reading as a long walk" "given a finite distance"

edit $R '            if (separation < 0 || separation >= spacing) {' '            if (false) {'
expect "a separation the game itself rejects" "never places at all"

echo "the compass, which is where this becomes a server problem"
edit $R '        return Math.max(FINEST_STEP, across / Math.sqrt(SAMPLES_PER_SEARCH));' \
        '        return FINEST_STEP;'
expect "a search whose cost is whatever the radius makes it" "stop multiplying out to a constant"

edit $R '        return Math.min(MOST_SEARCH_BLOCKS, walkTo(share) * SEARCH_MARGIN);' \
        '        return walkTo(share) * SEARCH_MARGIN;'
expect "a search that reaches as far as some biome's rarity says" "no longer capped"

edit $R '        if (worstPerSecond() > MOST_PER_SECOND) {' '        if (false) {'
expect "nothing checking what a player holding the compass down costs" "the only number that decides"

edit $R '        if (mightMiss(coarsest) >= WORLD_GRAIN) {' '        if (false) {'
expect "a search allowed to step over a patch of biome" "standing in is worse than a slow one"

edit $N '        int radius = (int) WildRules.MOST_SEARCH_BLOCKS;' '        int radius = 64000;'
expect "a compass looking further than the rules allow" "not the stated ceiling"

edit $N '        int step = (int) Math.round(WildRules.stepFor(WildRules.MOST_SEARCH_BLOCKS));' \
        '        int step = 4;'
expect "a grid finer than the cost the harness proved" "not the cost the server pays"

edit $I '        player.getCooldowns().addCooldown(this, WildRules.COOLDOWN_TICKS);' '        ;'
expect "a compass with no cooldown under it" "a bound on nothing"

edit $P '    public record Found(String place, boolean found, BlockPos where,' \
        '    public record Found(String place, boolean ok, BlockPos where,'
expect "a failed search answered with a position" "sends somebody walking to nothing"

edit $CH '        if (!answer.found()) {' '        if (false) {'
expect "a failed search never told to the player" "not told to the player as a failure"

edit $W '                    if (bearing == null || level == null' '                    if (false'
expect "a needle that points across worlds at something that is not there" "worse than one that says nothing"

edit $W '                        return wobble(holder, seed);' '                        return 0.0F;'
expect "a compass with nothing to point at pointing anyway" "so a player follows it"

echo "the words"
edit $G '        "elysiumwilds.compass.nowhere": "No %s anywhere the compass can see from here",' \
        '        "elysiumwilds.compass.nothere": "No %s anywhere the compass can see from here",'
expect_regen "a message that reads as its own translation key" "reads as its own translation key"

python3 - <<'PYX'
import pathlib
from PIL import Image
p = pathlib.Path("/tmp/Elysium-mono/elysium-wilds/src/main/resources/assets/elysiumwilds/textures/item/biome_compass_04.png")
image = Image.open(p).convert("RGBA")
image.putpixel((1, 1), (255, 0, 255, 255))
image.save(p)
PYX
expect "a needle frame edited by hand under a generator" "edited by hand"

edit $W "                ResourceLocation.fromNamespaceAndPath(ElysiumWilds.MODID, \"angle\")," \
        "                ResourceLocation.fromNamespaceAndPath(ElysiumWilds.MODID, \"turn\"),"
expect_regen "models switching on a property nothing sets" "the needle would never move"

edit $SC '    protected boolean filterable() {
        return true;
    }' '    protected boolean filterable() {
        return false;
    }'
expect "a hundred and forty biomes paged seven at a time" "closes it and opens a wiki instead"

edit $SC '    @Override
    protected Component nothing() {' '    @Override
    protected Component nothingAtAll() {'
expect "a filter that matches nothing saying nothing about why" "reads as the screen being broken"

edit $M/src/main/java/com/elysium/wilds/ElysiumWilds.java \
  'com.elysium.wilds.registry.WildItems.ITEMS.register(modEventBus);' '/* */'
expect "a register declared and never put on the bus" "never put on the bus"

echo "the seam, and the check at the root"
edit $C 'WORLDS = {' 'NOT_WORLDS = {'
expect "a checker with no table of what each world should look like" "how much of a world one biome may be is being guessed"

python3 - <<'PYX'
import pathlib
p = pathlib.Path("/tmp/Elysium-mono/check_worldgen.py")
s = p.read_text(encoding="utf-8")
p.write_text(s.replace("WildRules", "WildRulez"), encoding="utf-8")
PYX
expect "a checker measuring the worlds against its own copy of the arithmetic" "a second copy of the game's arithmetic"

edit $C '        fail(f"{name} is a multi-noise world with no row in check_worldgen.py; add one "' \
        '        fail(f"{name} is a world we have not thought about; add one "'
expect "a world nothing has ever looked at passing" "no longer fails"

edit $V '    if subprocess.run([sys.executable, "check_worldgen.py"], cwd=HERE).returncode != 0:' \
        '    if False:'
expect "a worldgen check nothing at the root runs" "not run by the root validator"

echo "the worlds themselves"
python3 - <<'PYX'
import json, pathlib
# The bug as it shipped: a river's band nested inside the plains it threads.
p = pathlib.Path("/tmp/Elysium-mono/elysium-space/src/main/resources/data/elysiumspace/dimension/emerald_bastion.json")
b = json.loads(p.read_text(encoding="utf-8"))
entries = b["generator"]["biome_source"]["biomes"]
plains = [e for e in entries if e["biome"].endswith("_plains")]
river = [e for e in entries if e["biome"].endswith("_river")][0]
rest = [e for e in entries if not e["biome"].endswith("_plains")]
whole = json.loads(json.dumps(plains[0]))
whole["parameters"]["weirdness"] = [-1.0, 1.0]
b["generator"]["biome_source"]["biomes"] = [whole] + rest
p.write_text(json.dumps(b, indent=2) + "\n", encoding="utf-8")
PYX
expect_world "the river's band nested back inside the plains that threads it" "sits entirely inside"

edit $R '    public static final double FURTHEST_WORTH_WALKING = 20_000.0D;' \
        '    public static final double FURTHEST_WORTH_WALKING = 5_000.0D;'
expect_world "a world with a biome further off than the pack will make anybody walk" "blocks away, which is a compass telling a player"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-space/src/main/resources/data/elysiumspace/dimension/emerald_bastion.json")
b = json.loads(p.read_text(encoding="utf-8"))
b["generator"]["biome_source"]["biomes"] = [
    e for e in b["generator"]["biome_source"]["biomes"]
    if not e["biome"].endswith("_highlands")]
p.write_text(json.dumps(b, indent=2) + "\n", encoding="utf-8")
PYX
expect_world "a world that has become mostly one biome" "and the world says no biome of its own may be more than"

python3 - <<'PYX'
import json, pathlib, shutil
src = pathlib.Path("/tmp/Elysium-mono/elysium-space/src/main/resources/data/elysiumspace/dimension/pale_moon.json")
dst = src.with_name("unlooked_at.json")
shutil.copy(src, dst)
PYX
expect_world "a new world with no row in the table" "no row in check_worldgen.py"

python3 - <<'PYX'
import pathlib
p = pathlib.Path("/tmp/Elysium-mono/check_worldgen.py")
s = p.read_text(encoding="utf-8")
p.write_text(s.replace('"pale_moon": (0.65,', '"pale_lagoon": (0.65,'), encoding="utf-8")
PYX
expect_world "a row describing a world that no longer exists" "the row is describing something that no longer exists"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all wilds checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN (validator)"; fail=$((fail+1)); fi
g="$(worldout)"
if [[ "$g" == *"multi-noise world(s)"* ]]; then echo "  worlds restored clean"
else echo "  WORLDS LEFT BROKEN"; fail=$((fail+1)); fi
echo "the room pieces stand where their box is"
edit $M/src/main/java/com/elysium/wilds/structure/RoomPiece.java '        this.room = tag.getString("Room");
        setOrientation(net.minecraft.core.Direction.NORTH);' '        this.room = tag.getString("Room");'
expect "a room piece loaded without an orientation" "built without an orientation"

echo "a crag shadowed by the game's own peaks"
edit $M/tools/gen_wilds.py '    entries += unshadowed(vanilla_entries(), entries)' '    entries += vanilla_entries()'
expect_regen "vanilla claims left inside our boxes" "wholly inside a crag"

echo "only the crags pressing the claim, the hoar hill left to lose its ties"
edit $M/tools/gen_wilds.py '    entries += unshadowed(vanilla_entries(), entries)' '    entries += unshadowed(vanilla_entries(), [one for one in entries if one["biome"].endswith("_crag")])'
expect_regen "vanilla claims left inside the hoar hill's box" "wholly inside a crag"

echo "wilds: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
