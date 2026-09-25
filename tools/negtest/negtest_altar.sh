#!/usr/bin/env bash
# The awakening altar and the sixth rung, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-agronomy
A=$M/src/main/java/com/elysium/agronomy/rules/AltarRules.java
K=$M/src/main/java/com/elysium/agronomy/block/AwakeningAltarBlock.java
S=$M/src/main/java/com/elysium/agronomy/item/EssenceSeed.java
G=$M/tools/gen_agronomy.py
SNAP=/tmp/altar_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 400 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit is made."
  (cd $M && timeout 400 python3 validate.py 2>&1 | tail -5)
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-agronomy elysium-foundry)
restore() { rm -rf $ROOT/elysium-agronomy $ROOT/elysium-foundry && (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
gexpect() { (cd $M && python3 tools/gen_agronomy.py >/dev/null 2>&1); expect "$@"; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the one thing awakening is not: a bigger number"
edit $A '    public static final int AWAKENED_YIELD = 2;' '    public static final int AWAKENED_YIELD = 1;'
expect "an awakened seed that yields exactly what an ordinary one does" "so awakening buys nothing"

edit $A '        float allowed = AgronomyRules.minedPerHour() * AgronomyRules.FARM_AT_MOST;
        float each = awakenedPerHour(tier, 1);
        return each <= 0.0F ? Integer.MAX_VALUE : (int) Math.floor(allowed / each);' '        return AgronomyRules.plotsBefore(tier);'
expect "an awakened farm allowed as many plots as an ordinary one" "so awakening is a bigger number rather than a smaller room"

edit $A '    public static float awakenedPerHour(int tier, int plots) {
        return AgronomyRules.farmedPerHour(tier, plots) * AWAKENED_YIELD;
    }' '    public static float awakenedPerHour(int tier, int plots) {
        return AgronomyRules.farmedPerHour(tier, plots);
    }'
expect "an awakened seed that yields no more than an ordinary one" "yields no more than an ordinary one"

edit $A '        return Math.abs(awakened - ordinary) <= onePlot + 0.001F;' '        return true;'
expect "an earnings check that cannot report anything" "cannot say no"

echo "the sixth rung"
edit $A '    public static final int PER_INSANIUM = AgronomyRules.ESSENCE_PER_STEP;' '    public static final int PER_INSANIUM = 1;'
expect "a sixth rung that costs one of the fifth rather than four" "so the altar is a discount on the ladder it sits on top of"

edit $A '    public static final String TOP_ESSENCE = "ascendant_essence";' '    public static final String TOP_ESSENCE = "essence_of_tier_5";'
expect "an altar asking for an essence that has never existed" "the altar would ask for an item that has never existed"

edit $A '        made.add(new Awakening(INSANIUM, "", PER_INSANIUM, top, INSANIUM));' '        made.add(new Awakening(INSANIUM, "", PER_INSANIUM, top, "wax"));'
expect "nothing an altar does making the sixth rung" "so the sixth rung is a name with no way up to it"

edit $A '        for (SeedRules.Seed seed : SeedRules.SEEDS) {
            made.add(new Awakening(awakenedSeed(seed.material()), seed.id(),
                    PER_INSANIUM, INSANIUM, awakenedSeed(seed.material())));
        }' '        for (SeedRules.Seed seed : SeedRules.SEEDS.subList(0, 4)) {
            made.add(new Awakening(awakenedSeed(seed.material()), seed.id(),
                    PER_INSANIUM, INSANIUM, awakenedSeed(seed.material())));
        }'
expect "seeds the altar has never heard of" "of them can be awakened"

echo "the shape"
edit $A '        Place relay = new Place(RELAY, RELAY_REACH, 0);' '        Place relay = new Place(RELAY, RELAY_REACH * 10, 0);'
expect "a relay ten times further out than the altar claims" "so it reaches into the chunk next door"

edit $A '            Place turning = one;
            for (int quarter = 0; quarter < 4; quarter++) {
                made.add(turning);
                turning = turning.turned();
            }' '            made.add(one);'
expect "an altar with one of each rather than four, so it works facing one way" "so the altar is not the same altar turned round"

edit $A '        public Place turned() {
            return new Place(kind, -north, east);
        }' '        public Place turned() {
            return new Place(kind, north, east);
        }'
expect "a quarter turn that is a mirror rather than a turn" "two blocks of the altar stand at"

edit $A '            if (one.reach() > THE_BOX) {' '            if (false) {'
expect "a box check that cannot report anything" "is not reported, so an altar may reach into the chunk next door"

edit $A '            if (!seen.add(where)) {' '            if (false) {'
expect "a doubles check that cannot report anything" "two blocks of the altar in one place are not reported"

edit $A '            if (!places.contains(where)) {' '            if (false) {'
expect "a symmetry check that cannot report anything" "and no mirror images of it is not reported"

echo "the block a player actually uses"
edit $K '            if (!built(level, centre)) {
                return Component.translatable("elysiumagronomy.altar.unbuilt",' '            if (false) {
                return Component.translatable("elysiumagronomy.altar.unbuilt",'
expect "an altar that fires without being built" "so its pedestals are decoration"

edit $K '            for (AltarBlock.HeldBlockEntity stand : pedestals(level, centre)) {
                stand.spend(one.each());
            }' '            for (AltarBlock.HeldBlockEntity stand : pedestals(level, centre)) {
                stand.held();
            }'
expect "an altar that fires without spending the essence on its pedestals" "which is a machine for making essence out of nothing"

edit $K '            for (AltarRules.Place place : AltarRules.AROUND) {
                BlockPos where = centre.offset(place.east(), 0, place.north());
                if (!(level.getBlockState(where).getBlock() instanceof AltarBlock stand)
                        || !stand.kind().equals(place.kind())) {
                    return false;
                }
            }' '            if (level == null) {
                return false;
            }'
expect "an altar whose shape in the world is not the shape the harness proved" "are two different shapes"

edit $S '        return awakened
                ? plain * com.elysium.agronomy.rules.AltarRules.AWAKENED_YIELD
                : plain;' '        return awakened ? plain * 2 : plain;'
expect "an awakened seed with its own opinion about what a plot yields" "are two different arithmetics"

echo "the world the altar is actually in"
rm -f $M/src/main/resources/data/elysiumagronomy/awakening/table.json
expect "no table of what an altar can do" "so the thirty-one things it makes are in Java"

edit $G '    for material in SEEDS:
        table.append({
            "id": f"awakened_{material}_seed",' '    for material in list(SEEDS)[:3]:
        table.append({
            "id": f"awakened_{material}_seed",'
gexpect "a table with fewer rows than there are seeds" "is a seed nobody can awaken"

edit $G '            "each": PER_INSANIUM,
            "essence": INSANIUM,' '            "each": 0,
            "essence": INSANIUM,'
gexpect "a table row that swallows no essence" "so the altar is free"

edit $G '            "makes": f"awakened_{material}_seed",' '            "makes": f"{material}_seed",'
gexpect "a table row that hands back what it took" "hands back what it took"

rm -f $M/src/main/resources/assets/elysiumagronomy/models/item/insanium_essence.json
expect "the sixth rung with no model" "so the sixth rung is a purple cube"

rm -f $M/src/main/resources/data/elysiumagronomy/recipe/essence_pedestal.json
expect "a block of the altar with no recipe" "so the altar is a structure only the creative menu can build"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all agronomy checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | tail -3 | head -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "altar: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
