#!/usr/bin/env bash
# The two things an ability puts in the world, broken one way at a time.
#
# The property every case below circles: a summon must not outlive the cooldown
# that is supposed to limit it. An ability with a nine-hundred-tick cooldown
# that calls up something lasting a thousand is an ability a player can have two
# of, and then three, and the cost stops being a limit. It is invisible in every
# screenshot and it looks exactly like a mod that is generous.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-bestiary
L=$ROOT/elysium-lib
R=$M/src/main/java/com/elysium/bestiary/rules/SummonRules.java
BE=$M/src/main/java/com/elysium/bestiary/entity/RelicBoltEntity.java
SE=$M/src/main/java/com/elysium/bestiary/entity/ShadeEntity.java
RG=$M/src/main/java/com/elysium/bestiary/registry/SummonEntities.java
CL=$M/src/main/java/com/elysium/bestiary/client/BestiaryClient.java
G=$M/tools/gen_bestiary.py
V=$M/validate.py
SNAP=/tmp/summons_snap.tar
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

echo "nothing outlives the cooldown meant to limit it"
edit $R '            if (made.lasts() >= ability.cooldown()) {' '            if (false) {'
expect "a summon outlasting its own cooldown, unreported" "far longer than its own cooldown"

edit $R '            new Summon(SHADE, ElysiumAbilityShapes.SUMMON, 600, 20.0D, 3.0D,' \
        '            new Summon(SHADE, ElysiumAbilityShapes.SUMMON, 1200, 20.0D, 3.0D,'
expect "a shade that stands longer than gravewake comes round" "the cooldown stops being a limit"

edit $R '            new Summon(BOLT, ElysiumAbilityShapes.BOLT, 60, 0.0D, IMPACT,' \
        '            new Summon(BOLT, ElysiumAbilityShapes.BOLT, 600, 0.0D, IMPACT,'
expect "a bolt that lives longer than the mark that threw it" "the cooldown stops being a limit"

echo "a thrown thing never arrives weightless"
edit $R '        if (floor <= 0.0D) {' '        if (false) {'
expect "a floor of nought on contact, unreported" "a floor of nought on contact"

edit $R '            if (Math.max(floor, ElysiumAbilityShapes.hurtOf(ability)) <= 0.0D) {' \
        '            if (false) {'
expect "a bolt worth nothing on contact, unreported" "arrives weightless is not reported"

edit $R '    public static final double IMPACT = 2.0D;' \
        '    public static final double IMPACT = 0.0D;'
expect "nothing happening when a bolt arrives" "goes out, hits, and nothing happens"

echo "what the pricing buys"
edit $R '            if (hurtOfBolt(ability) <= floor) {' \
        '            if (false) {'
expect "a price that buys nothing, unreported" "hits no harder than one priced to mark"

edit $R '        return Math.max(IMPACT, ElysiumAbilityShapes.hurtOf(ability));' \
        '        return IMPACT;'
expect "every bolt hitting for the same whatever it cost" "bought nothing"

echo "a shade helps rather than replaces"
edit $R '        if (shade.damage() > weakestHurt) {' '        if (false) {'
expect "a summon out-fighting everything, unreported" "out-fights every beast in the world"

edit $R '        if (shade.health() < weakestHealth) {' '        if (false) {'
expect "a summon that dies on contact, unreported" "dies on contact is not reported"

edit $R '        if (shade == null) {
            bad.add("there is no shade at all' '        if (false) {
            bad.add("there is no shade at all'
expect "no shade at all, unreported" "no shade at all is not reported"

edit $R '        if (beasts.isEmpty()) {
            bad.add("there is nothing for a shade' '        if (false) {
            bad.add("there is nothing for a shade'
expect "a shade measured against nothing, unreported" "measured against nothing at all"

edit $R '            new Summon(SHADE, ElysiumAbilityShapes.SUMMON, 600, 20.0D, 3.0D,' \
        '            new Summon(SHADE, ElysiumAbilityShapes.SUMMON, 600, 20.0D, 30.0D,'
expect "a shade that hits harder than any beast in the game" "the player stands behind it"

edit $R '            new Summon(SHADE, ElysiumAbilityShapes.SUMMON, 600, 20.0D, 3.0D,' \
        '            new Summon(SHADE, ElysiumAbilityShapes.SUMMON, 600, 2.0D, 3.0D,'
expect "a shade that dies on contact" "nobody calls one twice"

echo "the two, and the shapes they answer"
edit $R '            if (!named.add(one.id())) {' '            if (false) {'
expect "two summons under one name, unreported" "two summons written under one name"

edit $R '            if (!shapes.contains(one.shape())) {' '            if (false) {'
expect "a summon made by a shape nothing has, unreported" "the library has never heard of"

edit $R '            if (!made.contains(shape)) {' '            if (false) {'
expect "a shape with nothing behind it, unreported" "with nothing behind it, is not reported"

edit $R '            if (thrown && one.fights()) {' '            if (false) {'
expect "a homing bolt, unreported" "picks its own targets is not reported"

edit $R '            if (!thrown && !one.fights()) {' '            if (false) {'
expect "a summon that stands there, unreported" "picks no targets is not reported"

edit $R '            if (one.wide() > 4.0F || one.tall() > 4.0F) {' '            if (false) {'
expect "a summon too big for a door, unreported" "too big to follow a player through a door"

echo "the compact constructor"
edit $R '            if (lasts <= 0) {' '            if (false) {'
expect "a summon that lasts no time at all, accepted" "lasts no time at all is accepted"

edit $R '            if (health < 0.0D || damage < 0.0D) {' '            if (false) {'
expect "a summon with less than no health, accepted" "less than no health is accepted"

edit $R '            if (wide <= 0.0F || tall <= 0.0F) {' '            if (false) {'
expect "a summon of no width at all, accepted" "no width at all"

echo "the numbers the entities carry"
edit $BE '        setBaseDamage(SummonRules.summon(SummonRules.BOLT).damage());' \
        '        setBaseDamage(4.0D);'
expect "a bolt hitting for a number typed into the entity" "a number typed into the entity"

edit $BE '    public static final int LASTS = SummonRules.summon(SummonRules.BOLT).lasts();' \
        '    public static final int LASTS = 60;'
expect "a bolt whose lifetime is typed into the entity" "typed into the entity"

edit $BE '        setBaseDamage(SummonRules.hurtOfBolt(ability));' \
        '        setBaseDamage(getBaseDamage());'
expect "a bolt that never asks what threw it" "does not ask what the ability that threw it"

edit $SE '    public static final int LASTS = SummonRules.summon(SummonRules.SHADE).lasts();' \
        '    public static final int LASTS = 600;'
expect "a shade whose lifetime is typed into the entity" "typed into the entity"

edit $SE '                .add(Attributes.MAX_HEALTH, shade.health())' \
        '                .add(Attributes.MAX_HEALTH, 14.0D)'
expect "a shade given health by a number typed into the entity" "by a number typed into the entity"

edit $SE '                .add(Attributes.MOVEMENT_SPEED, SummonRules.SHADE_SPEED)' \
        '                .add(Attributes.MOVEMENT_SPEED, 0.32D)'
expect "a shade given speed by a number typed into the entity" "does not ask the rules for"

edit $SE '                Monster.class, 10, true, false,' \
        '                net.minecraft.world.entity.LivingEntity.class, 10, true, false,'
expect "a shade that can turn on its summoner" "turn on its summoner"

echo "something has to draw them"
edit $CL '        event.registerEntityRenderer(
                com.elysium.bestiary.registry.SummonEntities.SHADE.get(),
                ShadeRenderer::new);' '        // nothing'
expect "a shade with no renderer registered" "takes the client down with it"

edit $CL '        event.registerEntityRenderer(
                com.elysium.bestiary.registry.SummonEntities.BOLT.get(),
                RelicBoltRenderer::new);' '        // nothing'
expect "a bolt with no renderer registered" "takes the client down with it"

edit $CL '        event.registerLayerDefinition(ShadeRenderer.LAYER, ShadeModel::createLayer);' \
        '        // nothing'
expect "a shade whose model layer is never registered" "baking it"

remove $M/src/main/java/com/elysium/bestiary/client/ShadeRenderer.java
expect "no shade renderer at all" "no shade renderer at all"

remove $M/src/main/java/com/elysium/bestiary/client/ShadeModel.java
expect "no shade model at all" "nothing for the renderer"

remove $M/src/main/resources/assets/elysiumbestiary/textures/entity/shade.png
expect "a shade with no texture" "black-and-magenta cube"

remove $M/src/main/resources/assets/elysiumbestiary/textures/entity/bolt.png
expect "a bolt with no texture" "black-and-magenta cube"

echo "the library gets what it asked for"
edit $RG '            ElysiumAbilityEntities.bolt(BOLT::get);' '            // nothing'
expect "the library never handed a bolt" "never handed a bolt"

edit $RG '            ElysiumAbilityEntities.summon(SHADE::get);' '            // nothing'
expect "the library never handed a summon" "never handed a summon"

edit $RG '            ElysiumAbilityEntities.dress((made, ability) -> {' \
        '            if (false) ElysiumAbilityEntities.dressNothing((made, ability) -> {'
expect "nothing telling a bolt which ability threw it" "which ability threw it"

edit $RG '                    thrown.thrownBy(ability);' '                    thrown.tick();'
expect "a dressing that never reaches the bolt" "never reaches the bolt"

edit $RG '            if (!ElysiumAbilityEntities.missing().isEmpty()) {' '            if (false) {'
expect "nothing checking the library ended up whole" "nothing checks that the library ended up"

edit $RG '                            .sized(SummonRules.summon(SummonRules.BOLT).wide(),
                                    SummonRules.summon(SummonRules.BOLT).tall())' \
        '                            .sized(0.5F, 0.5F)'
expect "a hitbox typed into the register" "typed into the register"

echo "what the generator writes"
edit $G '        lang["entity.%s.%s" % (NS, summon_id)] = titled(summon_id)' \
        '        lang["entity.%s.%s_x" % (NS, summon_id)] = titled(summon_id)'
expect_regen "a summon with no name" "reads as its own translation key"


echo "the rules are all asked"
edit $R '        bad.addAll(faultsInPricing(abilitiesThatSummon(), IMPACT));' ''
expect "a rule written, compiled and never added up" "never added up by the summons"

echo
echo "summons: $pass caught, $fail missed"
[ $fail -eq 0 ] || exit 1
