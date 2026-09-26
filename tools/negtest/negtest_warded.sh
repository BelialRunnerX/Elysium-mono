#!/usr/bin/env bash
# Thirteen creatures that exist to be fought with particular gear, broken one
# way at a time.
#
# The property every case below circles: none of them is a gear check. The
# failure comes in two directions and both look like content while they are
# happening. Too far one way and the creature is a door -- without the set it
# takes the player apart, with it the fight is over before it starts, and the
# player who has the set never learns there was a fight. Too far the other way
# and the gear is decoration: the set makes no difference anybody can feel, so
# the armour is a texture with a tooltip. That second one is the commoner,
# because it is the one nobody ever complains about; they just stop wearing it.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-bestiary
R=$M/src/main/java/com/elysium/bestiary/rules/WardRules.java
EN=$M/src/main/java/com/elysium/bestiary/entity/WardEntity.java
AC=$M/src/main/java/com/elysium/bestiary/entity/act/WardActs.java
RG=$M/src/main/java/com/elysium/bestiary/registry/WardEntities.java
MD=$M/src/main/java/com/elysium/bestiary/ElysiumBestiary.java
CL=$M/src/main/java/com/elysium/bestiary/client/BestiaryClient.java
G=$M/tools/gen_bestiary.py
V=$M/validate.py
SNAP=/tmp/warded_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 600 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit."
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-bestiary)
restore() { rm -rf $ROOT/elysium-bestiary && (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
out() { (cd $M && timeout 600 python3 validate.py 2>&1); }
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

echo "a door rather than a fight"
edit $R '            new Warded("eldbomb", BLAST, "minecraft:iron_chestplate", 2, 18.0D, 8.0D, 3.5D, 0.30D, 24),' \
        '            new Warded("eldbomb", BLAST, "minecraft:iron_chestplate", 2, 18.0D, 8.0D, 0.5D, 0.30D, 24),'
expect "a creature the right gear nearly solves" "it is a door, and the key is an inventory slot"

edit $R '            if (one.left() < floor) {' '            if (false) {'
expect "the rule that finds a door" "the right gear trivialises is not reported"

edit $R '    public static final double LEAST_LEFT = 0.35D;' \
        '    public static final double LEAST_LEFT = 0.0D;'
expect "a floor low enough that nothing is ever a door" "never built a door or never built a texture"

echo "gear that is a texture"
edit $R '            new Warded("eldbomb", BLAST, "minecraft:iron_chestplate", 2, 18.0D, 8.0D, 3.5D, 0.30D, 24),' \
        '            new Warded("eldbomb", BLAST, "minecraft:iron_chestplate", 2, 18.0D, 8.0D, 7.9D, 0.30D, 24),'
expect "a creature the right gear barely helps against" "is a texture with a tooltip"

edit $R '            if (one.left() > ceiling) {' '            if (false) {'
expect "the rule that finds decoration" "the right gear barely helps against is not reported"

edit $R '    public static final double MOST_LEFT = 0.80D;' \
        '    public static final double MOST_LEFT = 1.0D;'
expect "a ceiling high enough that no gear is ever a texture" "barely helps against is not reported"

edit $R '            return threat() <= 0.0D ? 1.0D : blunt() / threat();' '            return 1.0D;'
expect "a ratio that is the same whatever the row says" "leaves 100% of itself"

echo "one set that answers everything"
edit $R '            new Warded("gremlin", SHOT, "minecraft:shield", 1, 14.0D, 3.5D, 2.0D, 0.36D, 50),' \
        '            new Warded("gremlin", ROT, "minecraft:shield", 1, 14.0D, 3.5D, 2.0D, 0.36D, 50),'
expect "a fourth creature answered by one set" "rather than one per creature"

edit $R '            if (entry.getValue() > mostCovered) {' '            if (false) {'
expect "the rule that spreads the roster over the wards" "answering most of the roster is not reported"

edit $R '    public static final int MOST_COVERED = 3;' \
        '    public static final int MOST_COVERED = 300;'
expect "a cap high enough that one set could answer everything" "answering most of the roster is not reported"

echo "nothing spare and nothing missing"
edit $R '            new Warded("eldknight", FROST, "minecraft:leather_boots", 3, 52.0D, 9.5D, 5.5D, 0.28D, 10),' \
        '            new Warded("eldknight", CURSE, "minecraft:leather_boots", 3, 52.0D, 9.5D, 5.5D, 0.28D, 10),'
expect_regen "a kind of protection nothing asks for" "leaves a kind of protection unasked for"

edit $R '            if (covers(ward, roster) == 0) {' '            if (false) {'
expect "the rule that finds an unasked-for protection" "nothing in the roster asks for is not reported"

edit $R '            if (!wards.contains(one.ward())) {' '            if (false) {'
expect "the rule that holds a ward to one this module has" "protection this module does not have is not reported"

edit $R '            if (!seen.add(one.id())) {' '            if (false) {'
expect "the rule that finds two creatures under one name" "written under one name are not reported"

echo "standing under the ladder"
edit $R '            new Warded("netherman", BLAST, "minecraft:netherite_ingot", 3, 60.0D, 10.0D, 6.0D, 0.26D, 8));' \
        '            new Warded("netherman", BLAST, "minecraft:netherite_ingot", 3, 600.0D, 10.0D, 6.0D, 0.26D, 8));'
expect "something harder unwarded than its own rung contains" "so a player without the gear meets something harder"

edit $R '            if (beside != null && one.threat() >= beside.threat()) {' '            if (false) {'
expect "the rule that holds everything under the beasts" "out-fighting the beast of its own rung unwarded is not reported"

edit $R '            if (beside(one, beasts) == null) {' '            if (false) {'
expect "something on a rung with no beast on it" "on a rung with no beast on it is not reported"

echo "what the player is told to bring"
edit $R '            new Warded("ratman", ROT, "minecraft:golden_carrot", 1, 16.0D, 3.0D, 2.0D, 0.34D, 55),' \
        '            new Warded("ratman", ROT, "minecraft:golden_carrott", 1, 16.0D, 3.0D, 2.0D, 0.34D, 55),'
expect_regen "an answer nothing makes" "nothing makes one of those"

edit $G '        lang["bestiary.%s.bring.%s" % (NS, one["id"])] = one["answer"]' \
        '        lang["bestiary.%s.bring.%s" % (NS, one["id"])] = "minecraft:stone"'
expect_regen "advice that is not the row's own answer" "have already come apart"

edit $R '            if (!one.answer().contains(":")) {' '            if (false) {'
expect "the rule that an answer is a name" "not the name of anything is not reported"

echo "one branch per ward, and no creature named"
edit $AC '            case WardRules.FROST -> wearing(who, Items.LEATHER_BOOTS)' \
        '            case "unreachable" -> wearing(who, Items.LEATHER_BOOTS)'
expect "a kind of protection nothing looks for" "whether a player is protected from frost"

edit $AC '        return switch (row.ward()) {' '        return switch (row.id()) {'
expect "which ward to look for chosen by which creature it is" "not chosen by the row's own ward"

edit $AC '    public static boolean warded(LivingEntity who, WardRules.Warded row) {' \
        '    private static final String WHICH = "eldknight";
    public static boolean warded(LivingEntity who, WardRules.Warded row) {'
expect "a creature named where the ward is looked for" "thirteen slightly different ways"

edit $AC '        return (float) (warded(who, row) ? row.blunted() : row.bite());' \
        '        return (float) 4.0;'
expect "a hit that is a number typed into the branch" "the game hits for something else"

echo "the numbers the entity carries"
edit $EN '        WardRules.Warded found = WardRules.warded(name);' \
        '        WardRules.Warded found = WardRules.WARDED.get(0);'
expect "a creature that does not find its own row" "does not find its own row"

edit $EN '        float takes = WardActs.bites(this, hurt, row);' '        float takes = 4.0F;'
expect "nothing asking what a hit should take off" "the ward is a note in a table"

edit $EN '                .add(Attributes.ATTACK_DAMAGE, row.bite())' \
        '                .add(Attributes.ATTACK_DAMAGE, row.blunted())'
expect "an attack attribute that is the best case rather than the worst" "makes every one of these fights free rather than hard"

echo "nothing registered by hand"
edit $RG '        for (WardRules.Warded one : WardRules.WARDED) {
            made.put(one.id(), BeastEntities.TYPES.register(one.id(),' \
        '        for (WardRules.Warded one : java.util.List.of(WardRules.WARDED.get(0))) {
            made.put(one.id(), BeastEntities.TYPES.register(one.id(),'
expect "creatures registered from a list somebody wrote out" "named by hand rather than walked"

edit $RG '        if (made.size() != WardRules.WARDED.size()) {' '        if (false) {'
expect "nothing counting the creatures registered" "a roster that half exists"

edit $MD '        com.elysium.bestiary.registry.WardEntities.ALL.size();' '        '
expect "a register nothing touches while the mod is built" "depends on when FML"

echo "something has to draw them"
edit $CL '                    context -> new WardRenderer(context,' '                    context -> new MythRenderer(context,'
expect "creatures with no renderer registered" "takes the client down with it"

edit $CL '            event.registerLayerDefinition(WardModel.LAYERS.get(form),' \
        '            event.registerLayerDefinition(MythModel.LAYERS.get(form),'
expect "model layers never registered" "baking one throws"

echo "what the generator writes"
remove $M/src/main/resources/assets/elysiumbestiary/textures/entity/netherman.png
expect "a creature with no texture" "has no texture"

edit $G '        lang["bestiary.%s.ward.%s" % (NS, one["id"])] = WARD_WORDS[one["ward"]]' \
        '        pass'
expect_regen "a creature with no sentence saying what to bring" "has no sentence saying what to bring"

echo "the rules are all asked"
edit $R '        bad.addAll(faultsInDoors(WARDED, LEAST_LEFT));' '        '
expect "a rule written, compiled and never added up" "faultsInDoors is written, compiled and never added up"

edit $R '        bad.addAll(faultsInDecoration(WARDED, MOST_LEFT));' '        if (false) bad.addAll(faultsInDecoration(WARDED, MOST_LEFT));'
expect "a rule added up only sometimes" "faults() has a condition in it"

echo
echo "warded: $pass caught, $fail missed"
[ "$fail" -eq 0 ] || exit 1
