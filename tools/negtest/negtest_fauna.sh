#!/usr/bin/env bash
# The half of the world that does not attack you, broken one way at a time.
#
# A farm animal is priced against time rather than danger, so almost every
# rule here is about a number nobody can see — how long a pen has to hold one
# before it gives again. A roster where that number is wrong does not look
# wrong; it looks like a pack where everybody keeps the same animal.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-bestiary
F=$M/src/main/java/com/elysium/bestiary/rules/FaunaRules.java
B=$M/src/main/java/com/elysium/bestiary/rules/BeastRules.java
HR=$M/src/main/java/com/elysium/bestiary/rules/HauntRules.java
EN=$M/src/main/java/com/elysium/bestiary/entity/FaunaEntity.java
RE=$M/src/main/java/com/elysium/bestiary/registry/FaunaEntities.java
RI=$M/src/main/java/com/elysium/bestiary/registry/FaunaItems.java
MD=$M/src/main/java/com/elysium/bestiary/client/FaunaModel.java
RD=$M/src/main/java/com/elysium/bestiary/client/FaunaRenderer.java
CL=$M/src/main/java/com/elysium/bestiary/client/BestiaryClient.java
MAIN=$M/src/main/java/com/elysium/bestiary/ElysiumBestiary.java
G=$M/tools/gen_bestiary.py
V=$M/validate.py
SNAP=/tmp/fauna_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 400 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit."
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-bestiary)
restore() { rm -rf $ROOT/elysium-bestiary && (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }
regen() { (cd $M && python3 tools/gen_bestiary.py >/dev/null 2>&1); }
expect_regen() { regen; expect "$1" "$2"; }

echo "four kinds, and twelve animals that are one of them"
edit $F '            if (!KINDS.contains(one.kind())) {
                bad.add(one.id() + " is a " + one.kind()' \
        '            if (false) {
                bad.add(one.id() + " is a " + one.kind()'
expect "an animal that is no kind of animal, unreported" "not any kind of animal is not reported"

edit $F '            if (!named.add(one.id())) {' '            if (false) {'
expect "one animal written twice, unreported" "one animal written twice is not reported"

edit $F '            if (many < LEAST_PER_KIND) {' '            if (false) {'
expect "a kind that is one animal with a label on it, unreported" "a kind with one animal in it is not reported"

edit $F '        bad.addAll(faultsInGroups(ANIMALS));' '        '
expect "a kind that could shrink to one animal with nothing watching" "never added up by the fauna's faults()"

echo "nothing that wins on both of a pen's two costs"
edit $F '                if (everywhere && somewhere) {' '                if (false) {'
expect "an animal there is no reason to keep, unreported" "more per square block"

edit $F '                double betterFloor = worthAnHour(better) / better.footprint();
                double worseFloor = worthAnHour(worse) / worse.footprint();' \
        '                double betterFloor = worthAnHour(better);
                double worseFloor = worthAnHour(worse);'
expect "a pen measured without its floor, so only the yield decides" "has an animal nobody would keep"

edit $F '                if (worse == better || !worse.kind().equals(better.kind())
                        || !KINDS.contains(worse.kind())) {' \
        '                if (worse == better) {'
expect "a fowl compared against a grazer, which is the rule that gets switched off" "gives no more per trip than"

echo "a wait somebody would sit through"
edit $F '            if (waits < least) {' '            if (false) {'
expect "a pen ready again before you can walk to it, unreported" "before a player can walk to it is not"

edit $F '            if (waits > most) {' '            if (false) {'
expect "a wait nobody would sit through, unreported" "a wait nobody would sit through is not reported"

edit $F '        return (int) Math.round(animal.yield() * TICKS_AN_HOUR / rateOf(animal.kind()));' \
        '        return (int) Math.round(TICKS_AN_HOUR / rateOf(animal.kind()));'
expect "a wait that does not grow with the yield, so leaving more is free" "leaves more does not wait longer"

echo "every kind worth keeping"
edit $F '            if (one == null) {' '            if (false) {'
expect "a kind nothing prices, unreported" "a kind with no tilt at all is not reported"

edit $F '            if (one != null && one <= 0.0D) {' '            if (false) {'
expect "a kind worth nothing an hour, unreported" "a kind worth nothing an hour is not reported"

edit $F '        if (worst > 0.0D && best > worst * MOST_TIMES_ANOTHER) {' '        if (false) {'
expect "one kind worth three times another, unreported" "worth three times another is not reported"

edit $F '    public static final double MOST_TIMES_ANOTHER = 1.5D;' \
        '    public static final double MOST_TIMES_ANOTHER = 100.0D;'
expect "the band widened until nothing is ever out of it" "worth three times another is not reported"

echo "a pen that cannot feed itself"
edit $F '            if (food.contains(one.gives())) {' '            if (false) {'
expect "a pen that makes what it is fed, unreported" "a pen that makes what it is fed is not reported"

edit $F '        Set<String> food = eaten(animals);' '        Set<String> food = new LinkedHashSet<>();'
expect "a loop check that reads nothing the roster eats" "a pen that makes what it is fed is not reported"

echo "nothing a pen produces that a fight already produces"
edit $F '            if (hunted.contains(one.gives())) {' '            if (false) {'
expect "an animal that makes the bestiary optional, unreported" "leaving what something in the"

edit $F '            hunted.addAll(BeastRules.leaves(beast));' '            hunted.addAll(java.util.List.of());'
expect "an overlap check that never asks what a beast leaves" "leaving what something in the"

edit $B '        List<String> rare = left.rare();
        int at = Math.max(0, Math.min(rare.size() - 1, beast.tier() - 1));
        return List.of(left.staple(), left.essence(), rare.get(at));' \
        '        return List.of();'
expect "a bestiary that no longer says what anything leaves" "leaving what something in the"

echo "out in the daylight, and somewhere a player goes"
edit $F '            if (where.brightest() < HauntRules.FULL_LIGHT) {' '            if (false) {'
expect "an animal that never sees full daylight, unreported" "never spawns in full daylight is not"

edit $F '            if (where.darkest() <= HauntRules.PITCH_DARK) {' '            if (false) {'
expect "an animal down in the pitch dark with the things that eat it, unreported" "down to pitch dark is not reported"

edit $F '            if (SWIMMER.equals(one.kind())) {
                continue;
            }' '            if (false) {
                continue;
            }'
expect "a rule that demands daylight of a fish" "its animals in is reported as"

edit $F '            if (any && !anywhere) {' '            if (false) {'
expect "a kind nobody ever meets, unreported" "certainly goes is not reported"

edit $F '            List.of("#minecraft:is_overworld", "#minecraft:is_ocean");' \
        '            List.of("#minecraft:is_nether");'
expect "the places a player certainly goes, moved to the nether" "puts its animals are reported as out of the way"

echo "the rules added up rather than merely written"
edit $F '        bad.addAll(faultsInLoop(ANIMALS));' '        '
expect "a fauna rule that answers its controls and is never asked about the roster" "never added up by the fauna's faults()"

edit $V '    _fauna_rules = set(re.findall(
        r"public static List<String> (faultsIn\w+)\(", FAUNA_RULES))' \
        '    _fauna_rules = set(re.findall(
        r"public static List<String> (faultsInZ\w+)\(", FAUNA_RULES))'
expect "a fauna rule sweep that finds no rules at all" "fauna rule(s) are declared at all"

echo "the arithmetic reaching the game"
edit $EN '        setAge(FaunaRules.patienceOf(animal()));' '        setAge(6000);'
expect "a wait typed into the entity rather than derived" "is not the number the arithmetic worked out"

edit $EN '        return Ingredient.of(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(net.minecraft.resources.ResourceLocation.parse(animal().eats())));' \
        '        return Ingredient.of(net.minecraft.world.item.Items.WHEAT);'
expect "every animal bred with wheat whatever its row says" "is no longer its own row's"

edit $EN '        HauntRules.Haunt haunt = HauntRules.haunt(id);' \
         '        HauntRules.Haunt haunt = HauntRules.haunt("zombie");'
expect "an animal that looks up somebody else's haunt" "a table nothing reads"

edit $EN '        return net.minecraft.world.entity.EntityDimensions.scalable(animal.width(),
                animal.height());' '        return net.minecraft.world.entity.EntityDimensions.scalable(1.0F, 1.0F);'
expect "every animal the same size whatever its row says" "dimensions are not its row's"

edit $EN '        String name = EntityType.getKey(getType()).getPath();' \
         '        String name = FaunaRules.ANIMALS.get(0).id();'
expect "an animal that no longer finds its own row" "finds its own row by the name"

echo "the world the animals are actually in"
edit $RE '        return FaunaRules.SWIMMER.equals(animal.kind())
                ? MobCategory.WATER_CREATURE
                : MobCategory.CREATURE;' '        return MobCategory.CREATURE;'
expect "fish counted against the land cap, so the world has too few of everything" "two spawn buckets"

edit $RE '                    FaunaRules.SWIMMER.equals(animal.kind())
                            ? SpawnPlacementTypes.IN_WATER
                            : SpawnPlacementTypes.ON_GROUND,' \
         '                    SpawnPlacementTypes.ON_GROUND,'
expect "swimmers placed on the ground, so the ocean is empty" "so the swimmers are put on the"

edit $RE '        for (FaunaRules.Animal animal : FaunaRules.ANIMALS) {
            made.put(animal.id(), TYPES.register(animal.id(),' \
         '        for (FaunaRules.Animal animal : FaunaRules.kind(FaunaRules.GRAZER)) {
            made.put(animal.id(), TYPES.register(animal.id(),'
expect "a registry that registers whichever animals somebody remembered" "walks the roster 2 time(s)"

edit $RE '        for (FaunaRules.Animal animal : FaunaRules.ANIMALS) {
            event.put(ALL.get(animal.id()).get(), FaunaEntity.attributes(animal).build());' \
         '        for (FaunaRules.Animal animal : FaunaRules.kind(FaunaRules.FOWL)) {
            event.put(ALL.get(animal.id()).get(), FaunaEntity.attributes(animal).build());'
expect "nine of twelve animals given no attributes at all" "walks the roster 2 time(s)"

edit $RI '        if (made.size() != FaunaRules.ANIMALS.size()) {' '        if (made.size() != made.size()) {'
expect "a registry that never counts what it registered" "does not count what it registered"

edit $MAIN '        com.elysium.bestiary.registry.FaunaItems.ITEMS.register(modEventBus);' '        '
expect "a register declared and never put on the bus" "never put on the bus"

edit $CL '        for (FaunaRules.Animal animal : FaunaRules.ANIMALS) {
            event.registerEntityRenderer(FaunaEntities.ALL.get(animal.id()).get(),' \
        '        for (FaunaRules.Animal animal : FaunaRules.kind(FaunaRules.FOWL)) {
            event.registerEntityRenderer(FaunaEntities.ALL.get(animal.id()).get(),'
expect "an animal drawn as a black and magenta cube" "not walked from the roster"

edit $CL '        for (String kind : FaunaRules.KINDS) {' '        for (String kind : java.util.List.of(FaunaRules.GRAZER)) {'
expect "a kind with no model layer, which is a crash on sight" "not walked from the kinds"

echo "as big as it looks"
edit $RD '        float across = row.width() * PIXELS / Math.max(shape.wide(), shape.deep());' \
        '        float across = 1.0F;'
expect "an animal as wide as whatever shape it shares" "no longer worked out from how wide it is"

edit $RD '        float up = row.height() * PIXELS / shape.tall();' '        float up = 1.0F;'
expect "a head above its own hitbox" "no longer worked out from how tall it is"

edit $MD '        return CreatureModel.bounds(parts);' \
        '        float lowY = Float.MAX_VALUE;
        float highY = -Float.MAX_VALUE;
        for (Part part : parts) {
            lowY = Math.min(lowY, part.lowY());
            highY = Math.max(highY, part.highY());
        }
        return new Bounds(16.0F, highY - lowY, 16.0F);'
expect "a second copy of the measuring, which is two copies of a hitbox" "measures its own shapes instead of asking"

edit $MD '                new Part(LEG_RIGHT, 3.0F, -5.0F, 4.0F, -1.5F, 0.0F, -1.5F, 3, 5, 3)));
        made.put(FaunaRules.FOWL' '                ));
        made.put(FaunaRules.FOWL'
expect "a kind missing a limb the walk cycle reaches for by name" "rather than six"

edit $MD '                new Part(BODY, 0.0F, -18.0F, 0.0F, -5.0F, -5.0F, -9.0F, 10, 9, 18),' \
        '                new Part(BODY, 0.0F, -18.0F, 0.0F, -5.0F, -5.0F, -9.0F, 10, 9, 30),'
expect "a box too big for the corner of the sheet it is drawn in" "corner of the sheet"

echo "the world written out from the roster"
edit $G '                      "weight": BY_ANIMAL[haunt["beast"]]["weight"],' \
        '                      "weight": 1,'
expect_regen "an animal met at a weight that is not its row's" "how often a player meets one is not what the roster declares"

edit $G '        "pools": [pool(animal["gives"], animal["yield"] * 2, True)],' \
        '        "pools": [pool(animal["gives"], animal["yield"], True)],'
expect_regen "a loot table that leaves half what the row says" "is a wait for something else"

edit $F '            new Animal("glasseel", SWIMMER, 4.0D, 0.40D, 0.4F, 0.4F,
                    "minecraft:kelp", "minecraft:salmon", 2, 85),' \
        '            new Animal("glasseel", SWIMMER, 4.0D, 0.40D, 0.4F, 0.4F,
                    "minecraft:kelpp", "minecraft:salmon", 2, 85),'
expect_regen "an animal bred on something the game does not have" "nor this pack makes it"

edit $V '                if entry.get("type") == "minecraft:item" and entry.get("name"):
                    dropped_by_animals.add(entry["name"])' \
        '                if entry.get("type") == "minecraft:itemm" and entry.get("name"):
                    dropped_by_animals.add(entry["name"])'
expect "a sweep that reads no drops at all out of twelve loot tables" "leave nothing at all"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all bestiary checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "fauna: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
