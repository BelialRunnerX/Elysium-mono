#!/usr/bin/env bash
# Eleven creatures that do a block's job, broken one way at a time.
#
# The property every case below circles: none of them does it for free. A block
# sits there, and that is the whole of what a block has over something alive. A
# companion that is neither tamed nor fed nor kept close is strictly better than
# the thing it replaces, and the thing it replaces stops being built -- quietly,
# one category at a time, with nothing in the pack saying when.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-bestiary
R=$M/src/main/java/com/elysium/bestiary/rules/CompanionRules.java
EN=$M/src/main/java/com/elysium/bestiary/entity/CompanionEntity.java
AC=$M/src/main/java/com/elysium/bestiary/entity/act/CompanionGifts.java
RG=$M/src/main/java/com/elysium/bestiary/registry/CompanionEntities.java
MD=$M/src/main/java/com/elysium/bestiary/ElysiumBestiary.java
CL=$M/src/main/java/com/elysium/bestiary/client/BestiaryClient.java
G=$M/tools/gen_bestiary.py
V=$M/validate.py
SNAP=/tmp/companions_snap.tar
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

echo "nothing does a block's job for free"
edit $R '            if (!earned && !fed && !near) {' '            if (false) {'
expect "a companion that costs nothing, unreported" "does a block's job for free"

edit $R '            new Companion("tether", REACH, "minecraft:scaffolding", false, 0, 6.0D,
                    40, 40),' \
        '            new Companion("tether", REACH, "minecraft:scaffolding", false, 0, 600.0D,
                    40, 40),'
expect "a crab that reaches from anywhere for nothing" "nobody builds one of those again"

edit $R '            boolean near = one.reach() <= mostReach;' '            boolean near = true;'
expect "everything counted as kept close, whatever its reach" "working from 100.0 is reported wrongly"

echo "near enough to be a companion, far enough to be one"
edit $R '            if (one.reach() < least) {' '            if (false) {'
expect "a companion you stand on, unreported" "you have to stand on is not reported"

edit $R '            if (one.reach() > most) {' '            if (false) {'
expect "a companion that works from anywhere, unreported" "a permanent effect with a mob"

edit $R '    public static final double MOST_REACH = 24.0D;' \
        '    public static final double MOST_REACH = 2.0D;'
expect "a reach band narrower than what it ships" "past the 2.0 this module allows"

echo "nothing gives for ever"
edit $R '            if (one.fades() > longest) {' '            if (false) {'
expect "a gift that never fades, unreported" "outlasts the creature is not reported"

edit $R '    public static final int LONGEST_FADE = 200;' \
        '    public static final int LONGEST_FADE = 50;'
expect "a gift lingering past what the module allows" "a stat a player keeps"

echo "one job each, and no two the same"
edit $R '            if (!done.add(one.gift())) {' '            if (false) {'
expect "two companions doing one job, unreported" "two companions doing the same job"

edit $R '            new Companion("tether", REACH, "minecraft:scaffolding", false, 0, 6.0D,' \
        '            new Companion("tether", SHOWS_DARK, "minecraft:scaffolding", false, 0, 6.0D,'
expect "two creatures with one job" "whichever is easier to keep"

edit $R '            if (!gifts.contains(one.gift())) {' '            if (false) {'
expect "a job nothing here has heard of, unreported" "nothing here has heard of is not reported"

edit $R '            if (!done.contains(gift)) {' '            if (false) {'
expect "a job nothing does, unreported" "a job nothing does is not reported"

echo "fed things are kept things"
edit $R '            if (one.upkeep() > 0 && !one.tamed() && one.reach() > LEAST_REACH * 2) {' \
        '            if (false) {'
expect "something fed that can wander off, unreported" "free to wander off with it"

edit $R '            new Companion("lumen", SHOWS_DARK, "minecraft:torch", true, 1, 8.0D,' \
        '            new Companion("lumen", SHOWS_DARK, "minecraft:torch", false, 1, 8.0D,'
expect "a fed creature nobody owns" "feeds something that is free to wander off"

echo "met, not read about"
edit $R '            if (one.weight() < least) {' '            if (false) {'
expect "a creature nobody ever meets, unreported" "a creature nobody ever meets is not reported"

edit $R '            if (!named.add(one.id())) {' '            if (false) {'
expect "two companions under one name, unreported" "two companions written under one name"

echo "what it is measured against has to exist"
edit $R '            new Companion("stasher", HIDES, "minecraft:chest", false, 0, 16.0D, 0,' \
        '            new Companion("stasher", HIDES, "minecraft:cupboard", false, 0, 16.0D, 0,'
expect "a companion held under a block nothing makes" "what it is measured against does not exist"

echo "the compact constructor"
edit $R '            if (reach <= 0.0D) {' '            if (false) {'
expect "a companion that works from nowhere, accepted" "works from nowhere is accepted"

edit $R '            if (upkeep < 0 || fades < 0) {' '            if (false) {'
expect "a companion costing less than nothing, accepted" "less than nothing to keep is accepted"

edit $R '            if (weight < 1) {' '            if (false) {'
expect "a companion nothing spawns, accepted" "nothing ever spawns is accepted"

edit $R '            if (rivals == null || rivals.isEmpty()) {' '            if (false) {'
expect "a companion competing with nothing, accepted" "competes with nothing is accepted"

echo "one branch per job, and no creature named"
edit $AC '            case CompanionRules.REACH -> lasting(near, MobEffects.MOVEMENT_SPEED, 0);' \
        '            case "nothing_at_all" -> lasting(near, MobEffects.MOVEMENT_SPEED, 0);'
expect "a job nothing makes happen" "a sentence in a language file"

edit $AC '        switch (row.gift()) {' '        switch (row.id()) {'
expect "jobs chosen by which creature it is" "not chosen by which gift it is"

edit $AC '    public static final int LINGERS = 120;' \
        '    public static final int LINGERS = 120;
    private static final String WHICH = "lumen";'
expect "a creature named in the gifts" "started growing a branch per creature"

edit $AC '            one.addEffect(new MobEffectInstance(what, LINGERS, strength, true, false));' \
        '            one.addEffect(new MobEffectInstance(what, 100, strength, true, false));'
expect "a duration typed where one place is supposed to decide" "is not decided in one"

edit $AC '                    other.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN, LINGERS, 1));' \
        '                    other.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN, 300, 1));'
expect "a gift lasting a number typed into its own branch" "typed into the branch"

edit $AC '        List<Player> near = nearby(self, row.reach());' \
        '        List<Player> near = nearby(self, 8.0D);'
expect "a reach typed into the gifts" "nearby(self, 8.0D), which is a distance that is not the row's own"

echo "the numbers the entity carries"
edit $EN '        CompanionRules.Companion found = CompanionRules.companion(name);' \
        '        CompanionRules.Companion found = CompanionRules.COMPANIONS.get(0);'
expect "a companion that does not find its own row" "does not find its own row"

edit $EN '        return !companion().tamed() || owned;' '        return true;'
expect "a companion that works before it is earned" "will do its one thing at all is not the row's own"

edit $EN '        if (companion().tamed() && !owned && isFood(held)) {' \
        '        if (!owned && isFood(held)) {'
expect "a companion earned without the row being asked" "what it takes to earn a companion is not the row's own"

echo "nothing registered by hand"
edit $RG '        for (CompanionRules.Companion one : CompanionRules.COMPANIONS) {
            made.put(one.id(), BeastEntities.TYPES.register(one.id(),' \
        '        for (CompanionRules.Companion one : java.util.List.of(CompanionRules.COMPANIONS.get(0))) {
            made.put(one.id(), BeastEntities.TYPES.register(one.id(),'
expect "companions registered from a list somebody wrote out" "named by hand rather than walked"

edit $RG '        if (made.size() != CompanionRules.COMPANIONS.size()) {' '        if (false) {'
expect "nothing counting the companions registered" "nothing counts the companions"

edit $RG '                                    CompanionEntity::new, MobCategory.CREATURE)' \
        '                                    CompanionEntity::new, MobCategory.MONSTER)'
expect "companions in the monster bucket" "crowd out everything a player is meant"

edit $MD '        com.elysium.bestiary.registry.CompanionEntities.ALL.size();' '        ;'
expect "a register nothing touches while the mod is built" "depends on when FML happens to load"

echo "something has to draw them"
edit $CL '                            .get(one.id()).get(), CompanionRenderer::new);' \
        '                            .get(one.id()).get(), null);'
expect "companions with no renderer registered" "takes the client down with it"

edit $CL '        event.registerLayerDefinition(CompanionModel.LAYER,' \
        '        event.registerLayerDefinition(ShadeRenderer.LAYER,'
expect "a companion layer never registered" "baking it"

remove $M/src/main/resources/assets/elysiumbestiary/textures/entity/tether.png
expect "a companion with no texture" "the world never shows"

remove $M/src/main/resources/data/elysiumbestiary/neoforge/biome_modifier/add_lumen_spawns.json
expect "a companion with no spawn rule" "the world never shows"

echo "what the generator writes"
edit $G '        lang["bestiary.%s.gift.%s" % (NS, one["id"])] = GIFT_WORDS[one["gift"]]' \
        '        lang["bestiary.%s.gift.%s_x" % (NS, one["id"])] = GIFT_WORDS[one["gift"]]'
expect_regen "a job with no sentence saying what it is" "keeps by accident and never uses"

edit $G '        lang["entity.%s.%s" % (NS, one["id"])] = titled(one["id"])
        lang["item.%s.%s_spawn_egg" % (NS, one["id"])] = \
            titled(one["id"]) + " Spawn Egg"' \
        '        lang["entity.%s.%s_x" % (NS, one["id"])] = titled(one["id"])
        lang["item.%s.%s_spawn_egg" % (NS, one["id"])] = \
            titled(one["id"]) + " Spawn Egg"'
expect_regen "a companion with no name" "reads as its own translation key"

echo "the rules are all asked"
edit $R '        bad.addAll(faultsInUpkeep(COMPANIONS));' ''
expect "a rule written, compiled and never added up" "never added up by the companions"

echo
echo "companions: $pass caught, $fail missed"
[ $fail -eq 0 ] || exit 1
