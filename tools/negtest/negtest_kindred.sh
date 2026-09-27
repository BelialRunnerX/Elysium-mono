#!/usr/bin/env bash
# Twenty-seven creatures in thirteen kindreds, broken one way at a time.
#
# The property every case below circles: a variant is worth meeting twice. A
# roster grows by variant because variants are cheap -- one scorpion becomes
# four, the count goes up by three and the number of fights in the game goes up
# by none. What makes that failure hard to see is that it never looks like a
# mistake from inside: each row reads well, each one is quick to add, and the
# world does feel fuller. It shows up hours later as the sense that nothing new
# has happened in a while.
#
# So each case below takes away one of the things that stop it: the trait, the
# place, the rung, the width of a family, the branch that makes a difference
# happen, or the spawn rule that puts it somewhere of its own.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-bestiary
R=$M/src/main/java/com/elysium/bestiary/rules/KindredRules.java
EN=$M/src/main/java/com/elysium/bestiary/entity/KinEntity.java
AC=$M/src/main/java/com/elysium/bestiary/entity/act/KinTraits.java
RG=$M/src/main/java/com/elysium/bestiary/registry/KinEntities.java
MD=$M/src/main/java/com/elysium/bestiary/ElysiumBestiary.java
CL=$M/src/main/java/com/elysium/bestiary/client/BestiaryClient.java
G=$M/tools/gen_bestiary.py
V=$M/validate.py
SNAP=/tmp/kindred_snap.tar
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
remove() { rm -f "$1"; }

echo "two of a kindred doing one thing"
edit $R '            new Kin("rimebarb", "barb", false, FREEZES, COLD, 1, 20.0D, 3.0D, 0.28D, 24, false),' \
        '            new Kin("rimebarb", "barb", false, POISONS, COLD, 1, 20.0D, 3.0D, 0.28D, 24, false),'
expect "a variant doing what its base already does" "both of them poisons"

edit $R '                if (!seen.add(one.trait())) {' '                if (false) {'
expect "the rule that stops two of a kindred sharing a thing" "doing the same thing are not reported"

edit $R '        bad.addAll(faultsInTraitNames(KINDRED));' '        '
expect "the trait rule never asked about what ships" "faultsInTraitNames is written, compiled and never added up"

echo "two of a kindred in one place"
edit $R '            new Kin("dunebarb", "barb", false, BURROWS, DRYLANDS, 1, 18.0D, 3.0D, 0.30D, 30, false),' \
        '            new Kin("dunebarb", "barb", false, BURROWS, OPEN, 1, 18.0D, 3.0D, 0.30D, 30, false),'
expect "a variant living where its base lives" "both of them in #minecraft:is_overworld"

edit $R '                if (!seen.add(one.home())) {' '                if (false) {'
expect "the rule that stops two of a kindred crowding one biome" "living in one place are not reported"

echo "a version of something that is really a rung of its own"
edit $R '            new Kin("cinderhulk", "hulk", false, IGNITES, HELL, 2, 44.0D, 7.0D, 0.24D, 14, false),' \
        '            new Kin("cinderhulk", "hulk", false, IGNITES, HELL, 3, 44.0D, 7.0D, 0.24D, 14, false),'
expect "a member of a kindred on a rung of its own" "the rest of the hulk kindred on tier 2"

edit $R '                if (one.tier() != rung) {' '                if (false) {'
expect "the rule that holds a kindred to one rung" "standing on a different rung is not reported"

echo "a family wide enough to be a ladder"
edit $R '            new Kin("moonfang", "fang", false, LEAPS, WOODS, 2, 34.0D, 6.0D, 0.40D, 12, false),' \
        '            new Kin("moonfang", "fang", false, LEAPS, WOODS, 2, 134.0D, 6.0D, 0.40D, 12, false),'
expect "a kindred wider than the module allows" "which is past the 2.0 this module allows"

edit $R '    public static final double WIDEST_KINDRED = 2.0D;' \
        '    public static final double WIDEST_KINDRED = 200.0D;'
expect "a cap wide enough that nothing is ever too wide" "ten times wider than it is allowed to be is not reported"

edit $R '            if (least.threat() > 0.0D && most.threat() > least.threat() * widest) {' \
        '            if (false) {'
expect "the rule that caps how wide a family may be" "is reported wrongly"

echo "the head of a family"
edit $R '            new Kin("ferricbarb", "barb", true, POISONS, OPEN, 1, 16.0D, 3.0D, 0.30D, 40, true),' \
        '            new Kin("ferricbarb", "barb", false, POISONS, OPEN, 1, 16.0D, 3.0D, 0.30D, 40, true),'
expect "a kindred with nobody at the head of it" "kindred has no base"

edit $R '            new Kin("dunebarb", "barb", false, BURROWS, DRYLANDS, 1, 18.0D, 3.0D, 0.30D, 30, false),' \
        '            new Kin("dunebarb", "barb", true, BURROWS, DRYLANDS, 1, 18.0D, 3.0D, 0.30D, 30, false),'
expect "a kindred with two heads" "kindred has 2 bases"

edit $R '            if (entry.getValue() == 0) {' '            if (false) {'
expect "the rule that finds a headless family" "a kindred with no base is not reported"

edit $R '            if (entry.getValue() > 1) {' '            if (false) {'
expect "the rule that finds a two-headed family" "a kindred with two bases is not reported"

echo "young that belong to a costume"
edit $R '            new Kin("rimebarb", "barb", false, FREEZES, COLD, 1, 20.0D, 3.0D, 0.28D, 24, false),' \
        '            new Kin("rimebarb", "barb", false, FREEZES, COLD, 1, 20.0D, 3.0D, 0.28D, 24, true),'
expect "young belonging to a variant rather than the family" "has young and is not the base"

edit $R '            if (one.young() && !one.base()) {' '            if (false) {'
expect "the rule that keeps young with the family" "belonging to a costume rather than to the family are not reported"

echo "nothing spare and nothing missing"
edit $R '            new Kin("chromecaller", "caller", true, SUMMONS, RIVERSIDE, 2, 28.0D, 5.0D, 0.30D, 24, false));' \
        '            new Kin("chromecaller", "caller", true, THROWS, RIVERSIDE, 2, 28.0D, 5.0D, 0.30D, 24, false));'
edit $R '            new Kin("ferricrunt", "runt", true, SUMMONS, OPEN, 1, 14.0D, 2.0D, 0.34D, 55, false),' \
        '            new Kin("ferricrunt", "runt", true, DRAINS, OPEN, 1, 14.0D, 2.0D, 0.34D, 55, false),'
expect_regen "a thing nothing in the roster does" "leaves a trait uncarried"

edit $R '            if (!carried) {' '            if (false) {'
expect "the rule that finds a thing nothing does" "a trait nothing carries is not reported"

edit $R '            new Kin("rotcharger", "charger", false, POISONS, RIVERSIDE, 1, 20.0D, 3.0D, 0.32D, 20, false),' \
        '            new Kin("rotcharger", "charger", false, POISONS, PEAKS, 1, 20.0D, 3.0D, 0.32D, 20, false),'
edit $R '            new Kin("chromecaller", "caller", true, SUMMONS, RIVERSIDE, 2, 28.0D, 5.0D, 0.30D, 24, false));' \
        '            new Kin("chromecaller", "caller", true, SUMMONS, PEAKS, 2, 28.0D, 5.0D, 0.30D, 24, false));'
expect_regen "a place nothing lives in any more" "leaves a place empty"

edit $R '            if (!lived) {' '            if (false) {'
expect "the rule that finds an empty place" "a place nothing lives in is not reported"

echo "what has to exist for any of it to happen"
edit $R '    public static final String COLD = "#minecraft:spawns_snow_foxes";' \
        '    public static final String COLD = "#c:is_cold";'
expect "a biome tag the game does not have" "is not reported"

edit $R '            if (!homes.contains(one.home())) {' '            if (false) {'
expect "the rule that holds a place to a tag the game ships" "in a tag the game does not have is not reported"

edit $R '            if (!traits.contains(one.trait())) {' '            if (false) {'
expect "the rule that holds a trait to something made to happen" "doing something nothing here has heard of is not reported"

echo "standing under the ladder the pack is built on"
edit $R '            new Kin("deepbore", "bore", true, BURROWS, DRYLANDS, 3, 70.0D, 9.0D, 0.24D, 6, false),' \
        '            new Kin("deepbore", "bore", true, BURROWS, DRYLANDS, 3, 700.0D, 9.0D, 0.24D, 6, false),'
expect "a variant that is also the hardest thing on its rung" "on the same rung, so the ladder"

edit $R '            if (beside != null && one.threat() >= beside.threat()) {' '            if (false) {'
expect "the rule that holds everything under the beasts" "out-fighting the beast of its own rung is not reported"

edit $R '            if (beside(one, beasts) == null) {' '            if (false) {'
expect "a creature on a rung with no beast on it" "on a rung with no beast on it is not reported"

echo "the shape a family is drawn with"
edit $R '            Map.entry("barb", BeastRules.PROWLER),' \
        '            Map.entry("barb", "serpent"),'
expect "a family drawn as something nothing draws" "renders as a black-and-magenta cube"

edit $R '            Map.entry("wing", FaunaRules.FOWL),' '            Map.entry("bats", FaunaRules.FOWL),'
expect "a family drawn with nothing at all" "is drawn with nothing"

edit $R '        bad.addAll(faultsInShapes(KINDRED, MythRules.forms()));' '        '
expect "the shape rule never asked about what ships" "faultsInShapes is written, compiled and never added up"

echo "one branch per thing, and no creature named"
edit $AC '            case KindredRules.BURROWS -> {' '            case "unreachable" -> {'
expect "a thing nothing makes happen on the clock" "nothing burrows on the clock"

edit $AC '            case KindredRules.POISONS -> hurt.addEffect(new MobEffectInstance(' \
        '            case "unreachable" -> hurt.addEffect(new MobEffectInstance('
expect "a thing nothing decides on contact" "nothing decides what poisons does when the creature reaches you"

edit $AC '        Level level = self.level();
        switch (row.trait()) {' '        Level level = self.level();
        switch (row.id()) {'
expect "what a creature does on the clock chosen by which creature it is" "what a creature does in happen is not chosen"

edit $AC '        switch (row.trait()) {
            case KindredRules.POISONS -> hurt.addEffect' '        switch (row.id()) {
            case KindredRules.POISONS -> hurt.addEffect'
expect "what a creature does on contact chosen by which creature it is" "what a creature does in onContact is not chosen"

edit $AC '    public static final int BETWEEN = 80;' \
        '    public static final int BETWEEN = 80;
    private static final String WHICH = "moonfang";'
expect "a creature named in the branch that makes its difference happen" "is named in the traits"

edit $AC '    public static void onContact(KinEntity self, LivingEntity hurt,' \
        '    public static void notOnContact(KinEntity self, LivingEntity hurt,'
expect "nothing happening when it reaches you" "a timer the player learns to walk away from"

edit $AC '    public static void happen(KinEntity self, KindredRules.Kin row) {' \
        '    public static void notHappen(KinEntity self, KindredRules.Kin row) {'
expect "nothing happening on its own clock" "only lands if the player lets one reach them"

echo "the numbers the entity carries"
edit $EN '        KindredRules.Kin found = KindredRules.creature(name);' \
        '        KindredRules.Kin found = KindredRules.KINDRED.get(0);'
expect "a creature that does not find its own row" "does not find its own row"

edit $EN '            KinTraits.onContact(this, hurt, creature());' '            hurt.hurt(damageSources().generic(), 1.0F);'
expect "an entity that never hands itself to the traits on contact" "the four that land on contact never land"

edit $EN '        if (called >= CALLS_UP_TO || level().isClientSide()) {' \
        '        if (level().isClientSide()) {'
expect "a caller with nothing capping how many it has out" "it is the only move"

echo "nothing registered by hand"
edit $RG '        for (KindredRules.Kin one : KindredRules.KINDRED) {
            made.put(one.id(), BeastEntities.TYPES.register(one.id(),' \
        '        for (KindredRules.Kin one : java.util.List.of(KindredRules.KINDRED.get(0))) {
            made.put(one.id(), BeastEntities.TYPES.register(one.id(),'
expect "a roster of near-identical names typed out by hand" "four chances to paste the wrong id"

edit $RG '        if (made.size() != KindredRules.KINDRED.size()) {' '        if (false) {'
expect "nothing counting the creatures registered" "a roster that half exists"

edit $RG '                            MobCategory.MONSTER)' '                            MobCategory.CREATURE)'
expect "twenty-seven hostile things in the harmless bucket" "against the cap meant for the harmless ones"

edit $MD '        com.elysium.bestiary.registry.KinEntities.ALL.size();' '        '
expect "a register nothing touches while the mod is built" "depends on when FML"

echo "something has to draw them"
edit $CL '                    context -> new KinRenderer(context,' '                    context -> new MythRenderer(context,'
expect "creatures with no renderer registered" "takes the client down with it"

edit $CL '            event.registerLayerDefinition(KinModel.LAYERS.get(form),' \
        '            event.registerLayerDefinition(MythModel.LAYERS.get(form),'
expect "model layers never registered" "baking one throws"

echo "what the generator writes"
edit $G '                  "biomes": KINDRED_HOMES[one["home"]],' \
        '                  "biomes": "#minecraft:is_overworld",'
expect_regen "every spawn rule written into the overworld at large" "and its row says"

remove $M/src/main/resources/assets/elysiumbestiary/textures/entity/deepbore.png
expect "a creature with no texture" "has no texture"

edit $G '        lang["bestiary.%s.trait.%s" % (NS, one["id"])] = TRAIT_WORDS[one["trait"]]' \
        '        pass'
expect_regen "a creature with no sentence saying what it does" "has no sentence saying what it does"

echo "the rules are all asked"
edit $R '        bad.addAll(faultsInStanding(KINDRED, BeastRules.BEASTS));' '        '
expect "a rule written, compiled and never added up" "faultsInStanding is written, compiled and never added up"

echo
echo "kindred: $pass caught, $fail missed"
[ "$fail" -eq 0 ] || exit 1
