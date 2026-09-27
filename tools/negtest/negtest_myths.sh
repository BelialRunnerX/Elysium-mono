#!/usr/bin/env bash
# Eight creatures armour does not answer, broken one way at a time.
#
# Two properties every case below circles. Each myth is dangerous in its own
# way and nothing else's — two creatures whose danger is the same danger are
# one creature with two models. And no myth is also the hardest thing on its
# rung, because one that was would make gearing up answer nothing.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-bestiary
R=$M/src/main/java/com/elysium/bestiary/rules/MythRules.java
EN=$M/src/main/java/com/elysium/bestiary/entity/MythEntity.java
AC=$M/src/main/java/com/elysium/bestiary/entity/act/MythBanes.java
RG=$M/src/main/java/com/elysium/bestiary/registry/MythEntities.java
MD=$M/src/main/java/com/elysium/bestiary/ElysiumBestiary.java
CL=$M/src/main/java/com/elysium/bestiary/client/BestiaryClient.java
G=$M/tools/gen_bestiary.py
V=$M/validate.py
SNAP=/tmp/myths_snap.tar
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

echo "one danger each, and no two the same"
edit $R '            if (!used.add(one.bane())) {' '            if (false) {'
expect "two myths dangerous the same way, unreported" "two myths dangerous in the same way"

edit $R '            new Myth("siphon", DRAIN, "minecraft:golden_apple", 2,' \
        '            new Myth("siphon", PETRIFY, "minecraft:golden_apple", 2,'
expect "two creatures whose danger is the same danger" "whichever a player meets first"

edit $R '            if (!banes.contains(one.bane())) {' '            if (false) {'
expect "a bane nothing here has heard of, unreported" "nothing here has heard of is not reported"

edit $R '            if (!used.contains(bane)) {' '            if (false) {'
expect "a bane nothing uses, unreported" "written down and never happens"

echo "everything dangerous has an answer"
edit $R '            if (!answered.add(one.answer())) {' '            if (false) {'
expect "one item answering two of them, unreported" "answering two of these is not reported"

edit $R '            new Myth("bloomlet", SPORES, "minecraft:shears", 1,' \
        '            new Myth("bloomlet", SPORES, "minecraft:bow", 1,'
expect "one thing in a pocket ending two of these" "the player stops thinking about"

edit $R '            new Myth("blastjaw", BREATH, "minecraft:magma_cream", 3,' \
        '            new Myth("blastjaw", BREATH, "elysium:dragon_ward", 3,'
expect "an answer nothing in the pack makes" "the one thing that beats it does not exist"

edit $R '            new Myth("skimwing", HEIGHT, "minecraft:bow", 2,' \
        '            new Myth("skimwing", HEIGHT, "minecraft:skyhook", 2,'
expect "an answer the game does not make" "the game does not make that"

echo "a myth is never also the hardest thing on its rung"
edit $R '            if (one.threat() >= beside.threat()) {' '            if (false) {'
expect "a myth out-fighting its own rung, unreported" "also the hardest thing on its rung"

edit $R '            new Myth("blastjaw", BREATH, "minecraft:magma_cream", 3,
                    44.0D, 7.0D, 0.30D, 12, false, BeastRules.BRUTE),' \
        '            new Myth("blastjaw", BREATH, "minecraft:magma_cream", 3,
                    440.0D, 70.0D, 0.30D, 12, false, BeastRules.BRUTE),'
expect "a drake that beats every beast on its rung" "gearing up answers nothing"

edit $R '            if (beside(one, beasts) == null) {' '            if (false) {'
expect "a myth standing beside nothing, unreported" "a rung with no beast on it is not reported"

edit $R '        return health * (1.0D + damage) * speed / 0.25D;' \
        '        return health * speed / 0.25D;'
expect "danger worked out without what it hits for" "what a myth does to you is not part"

edit $R '        return health * (1.0D + damage) * speed / 0.25D;' \
        '        return health * (1.0D + damage);'
expect "danger worked out without how fast it closes" "how hard it is to walk away from"

echo "what runs is worth less than what stands"
edit $R '                if (one.threat() > other.threat()) {' '                if (false) {'
expect "a runner worth more than a stander, unreported" "the safest thing on the rung"

edit $R '            new Myth("pilferhand", THIEVERY, "minecraft:tripwire_hook", 1,
                    12.0D, 1.0D, 0.44D, 75, true, BeastRules.WIGHT),' \
        '            new Myth("pilferhand", THIEVERY, "minecraft:tripwire_hook", 1,
                    12.0D, 9.0D, 0.44D, 75, true, BeastRules.WIGHT),'
expect "a thief that hits harder than the thing that stands" "is also the worst to meet"

echo "met, not read about"
edit $R '            if (one.weight() < least) {' '            if (false) {'
expect "a creature nobody ever meets, unreported" "a creature nobody ever meets is not reported"

edit $R '            if (!forms.contains(one.form())) {' '            if (false) {'
expect "a myth drawn as nothing, unreported" "black-and-magenta cube"

edit $R '            if (left.isEmpty()) {' '            if (false) {'
expect "a myth that leaves nothing, unreported" "leaves nothing at all is not reported"

edit $R '            if (!left.isEmpty() && !left.contains(":")) {' '            if (false) {'
expect "a leaving that is not a name, unreported" "not a name anything can look up is not"

echo "the compact constructor"
edit $R '            if (health <= 0.0D || damage < 0.0D || speed <= 0.0D) {' \
        '            if (false) {'
expect "a myth with no health, accepted" "no health is accepted"

edit $R '            if (weight < 1) {' '            if (false) {'
expect "a myth nothing spawns, accepted" "nothing ever spawns is accepted"

edit $R '            if (answer == null || answer.isEmpty()) {' '            if (false) {'
expect "a myth nothing answers, accepted" "nothing answers is accepted"

edit $R '            if (form == null || form.isEmpty()) {' '            if (false) {'
expect "a myth with no shape, accepted" "no shape, which nothing could draw"

echo "one branch per bane, and no creature named"
edit $AC '            case MythRules.PETRIFY -> {' '            case "nothing_at_all" -> {'
expect "a bane nothing makes happen" "is a sentence in a language file"

edit $AC '        switch (myth.bane()) {
            case MythRules.PETRIFY -> {' '        switch (myth.id()) {
            case MythRules.PETRIFY -> {'
expect "banes chosen by which creature it is" "is not chosen by which bane it is"

edit $AC '    public static final int A_CROWD = 4;' \
        '    public static final int A_CROWD = 4;
    private static final String WHICH = "glasseye";'
expect "a creature named in the banes" "started growing a branch per creature"

echo "the numbers the entity carries"
edit $EN '                .add(Attributes.MAX_HEALTH, myth.health())' \
        '                .add(Attributes.MAX_HEALTH, 30.0D)'
expect "a myth given health by a number typed into the entity" "does not ask the rules for"

edit $EN '                .add(Attributes.MOVEMENT_SPEED, myth.speed())' \
        '                .add(Attributes.MOVEMENT_SPEED, 0.30D)'
expect "a myth given speed by a number typed into the entity" "does not ask the rules for"

edit $EN '        MythRules.Myth found = MythRules.myth(name);' \
        '        MythRules.Myth found = MythRules.MYTHS.get(0);'
expect "a myth that does not find its own row" "does not find its own row"

edit $EN '        if (myth().flees()) {' '        if (false) {'
expect "a myth whose running is not the row" "not decided by the row"

echo "nothing registered by hand"
edit $RG '        for (MythRules.Myth myth : MythRules.MYTHS) {
            made.put(myth.id(), BeastEntities.TYPES.register(myth.id(),' \
        '        for (MythRules.Myth myth : java.util.List.of(MythRules.MYTHS.get(0))) {
            made.put(myth.id(), BeastEntities.TYPES.register(myth.id(),'
expect "myths registered from a list somebody wrote out" "named by hand rather than walked"

edit $RG '        if (made.size() != MythRules.MYTHS.size()) {' '        if (false) {'
expect "nothing counting the myths registered" "nothing counts the myths"

edit $RG '                            .sized(sizeOf(myth)[0], sizeOf(myth)[1])' \
        '                            .sized(0.9F, 1.8F)'
expect "a hitbox typed into the register" "a creature a player swings through"

edit $MD '        com.elysium.bestiary.registry.MythEntities.ALL.size();' '        ;'
expect "a register nothing touches while the mod is built" "depends on when FML happens to load"

echo "something has to draw them"
edit $CL '                    context -> new MythRenderer(context, myth.form()));' \
        '                    context -> null);'
expect "myths with no renderer registered" "takes the client down with it"

edit $CL '            event.registerLayerDefinition(MythModel.LAYERS.get(form),' \
        '            event.registerLayerDefinition(ShadeRenderer.LAYER,'
expect "myth layers never registered" "baking one"

remove $M/src/main/java/com/elysium/bestiary/client/MythRenderer.java
expect "no myth renderer at all" "nothing draws the myths"

remove $M/src/main/java/com/elysium/bestiary/client/MythModel.java
expect "no myth model at all" "nothing draws the myths"

remove $M/src/main/resources/assets/elysiumbestiary/textures/entity/glasseye.png
expect "a myth with no texture" "the world never shows"

remove $M/src/main/resources/data/elysiumbestiary/neoforge/biome_modifier/add_blastjaw_spawns.json
expect "a myth with no spawn rule" "the world never shows"

remove $M/src/main/resources/data/elysiumbestiary/loot_table/entities/pilferhand.json
expect "a myth with no loot table" "the world never shows"

echo "what the generator writes"
edit $G '        lang["bestiary.%s.bane.%s" % (NS, myth["id"])] = BANE_WORDS[myth["bane"]]' \
        '        lang["bestiary.%s.bane.%s_x" % (NS, myth["id"])] = BANE_WORDS[myth["bane"]]'
expect_regen "a danger with no sentence saying what it is" "dying to it twice"

edit $G '        lang["entity.%s.%s" % (NS, myth["id"])] = titled(myth["id"])
        lang["item.%s.%s_spawn_egg" % (NS, myth["id"])] = \
            titled(myth["id"]) + " Spawn Egg"' \
        '        lang["entity.%s.%s_x" % (NS, myth["id"])] = titled(myth["id"])
        lang["item.%s.%s_spawn_egg" % (NS, myth["id"])] = \
            titled(myth["id"]) + " Spawn Egg"'
expect_regen "a myth with no name" "reads as its own translation key"

echo "the rules are all asked"
edit $R '        bad.addAll(faultsInRarity(MYTHS, LEAST_WEIGHT));' ''
expect "a rule written, compiled and never added up" "never added up by the myths"

echo
echo "myths: $pass caught, $fail missed"
[ $fail -eq 0 ] || exit 1
