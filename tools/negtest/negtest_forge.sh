#!/usr/bin/env bash
# Reforging, broken one way at a time.
#
# The arithmetic goes against the forge harness, which is javac-and-run in
# about four seconds. The world -- hammers, names, the component, the events --
# only the module validator can see, and it runs four harnesses first, so it is
# used only where it has to be.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-arsenal
F=$M/src/main/java/com/elysium/arsenal/rules/ForgeRules.java
W=$M/src/main/java/com/elysium/arsenal/rules/WeaponRules.java
H=$M/tools/ForgeHarness.java
E=$M/src/main/java/com/elysium/arsenal/forge/ForgeEvents.java
K=$M/src/main/java/com/elysium/arsenal/forge/ForgeKinds.java
CO=$M/src/main/java/com/elysium/arsenal/forge/ForgeComponents.java
HI=$M/src/main/java/com/elysium/arsenal/item/HammerItem.java
FI=$M/src/main/java/com/elysium/arsenal/registry/ForgeItems.java
MC=$M/src/main/java/com/elysium/arsenal/ElysiumArsenal.java
G=$M/tools/gen_arsenal.py
V=$M/validate.py
SNAP=/tmp/forge_snap
OUT=/tmp/forge_out
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
  java -cp $OUT com.elysium.arsenal.rules.ForgeHarness 2>&1 | grep -v JAVA_TOOL
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

echo "the thing that makes two attributes comparable at all"
edit $F '            float asShare = share ? amount : amount * FLAT_AS_SHARE;
            return asShare * matters(attribute);' '            float asShare = share ? amount : amount * FLAT_AS_SHARE;
            return asShare;'
expect "percentages of incomparable things added together" "worth as much as a quarter more damage"

edit $F '            Map.entry("attack_knockback", 0.2F),' '            Map.entry("attack_knockback", 1.0F),'
expect "knockback priced as if it were damage" "worth as much as a quarter more damage"

edit $F '            Map.entry("attack_knockback", 0.2F),' '            Map.entry("attack_knockback_but_spelled_wrong", 0.2F),'
expect "a grade changing an attribute with no stated worth" "no stated worth"

echo "the gamble"
edit $F 'grade("dull", "melee", 30, 1, 1, share("attack_damage", -0.030F)),' 'grade("dull", "melee", 30, 1, 1, share("attack_damage", 0.030F)),'
expect "a table likelier to improve than to spoil" "likelier to improve than to spoil"

edit $F 'grade("dull", "melee", 30, 1, 1, share("attack_damage", -0.030F)),
            grade("unwieldy", "melee", 24, 1, 1, share("attack_speed", -0.042F)),' 'grade("dull", "melee", 30, 1, 1, share("attack_damage", 0.001F)),
            grade("unwieldy", "melee", 24, 1, 1, share("attack_speed", 0.001F)),'
expect "gear that cannot come out worse" "cannot come out worse"

edit $F 'grade("sharp", "melee", 14, 3, 2, share("attack_damage", 0.030F)),' 'grade("sharp", "melee", 14, 3, 2, share("attack_damage", 0.170F)),'
expect "a table that pays too well" "a table that pays"

edit $F 'grade("savage", "melee", 3, 9, 5, share("attack_damage", 0.060F),' 'grade("savage", "melee", 40, 9, 5, share("attack_damage", 0.060F),'
expect "a loss rarer than a win" "rarer than improving it"

echo "the ceiling, which is the weapon ladder's"
edit $F 'grade("fabled", "melee", 1, 12, 6, share("attack_damage", 0.070F),' 'grade("fabled", "melee", 1, 12, 6, share("attack_damage", 0.400F),'
expect "a grade worth more than a tier of the ladder" "as much as climbing a tier"

edit $F '    public static float smallestLadderStep() {
        float smallest = Float.MAX_VALUE;' '    public static float smallestLadderStep() {
        float smallest = 99.0F;'
expect "a ceiling that is not the ladder's, so any grade fits under it" "no grade reaches it"

echo "what a roll costs"
edit $F 'grade("fabled", "melee", 1, 12, 6,' 'grade("fabled", "melee", 1, 1, 6,'
expect "the best grade as cheap to gamble away as the worst" "cheaper to roll away from"

edit $F 'grade("fabled", "melee", 1, 12, 6,' 'grade("fabled", "melee", 60, 12, 6,'
expect "the best grade common enough to chase in an afternoon" "rolls to"

echo "the weighted roll itself"
edit $F '        int landed = Math.floorMod(pick, total);' '        int landed = pick % total;'
expect "a roll that returns the first grade for every negative pick" "should have come up about"

edit $F '        for (Grade one : grades) {
            landed -= one.weight();
            if (landed < 0) {
                return one;
            }
        }' '        for (Grade one : grades.subList(1, grades.size())) {
            landed -= one.weight();
            if (landed < 0) {
                return one;
            }
        }'
expect "a table with a grade that can never come up" "never came up"

echo "the hammers"
edit $F '            "weaponsmiths_hammer", List.of("melee", "bow"),' '            "weaponsmiths_hammer", List.of("melee"),'
expect "gear no hammer can reforge" "no hammer can reforge"

edit $F '            "toolsmiths_hammer", List.of("tool"),' '            "toolsmiths_hammer", List.of("tool", "melee"),'
expect "gear two hammers reforge" "matters for no reason"

echo "the world reforging is actually in"
edit $E '        if (ForgeComponents.gradeOn(stack) != null) {
            return;
        }' '        if (false) {
            return;
        }'
wexpect "a free hammer for anybody willing to drop and collect the same sword" "free hammer"

edit $E '        ForgeRules.Grade grade = ForgeRules.grade(id);
        // A grade whose name no longer exists' '        ForgeRules.Grade grade = null;
        // A grade whose name no longer exists'
wexpect "a grade baked in rather than read back" "read out of the rules at the moment"

edit $E '        if (grade == null) {
            return;
        }
        for (ForgeRules.Change change : grade.changes()) {' '        if (grade == ForgeRules.grade("nothing")) {
            return;
        }
        for (ForgeRules.Change change : grade.changes()) {'
wexpect "a world saved under an older roster crashing" "older roster"

edit $E '        float worth = grade.worth();' '        float worth = 1.0F;'
wexpect "a colour that no longer tracks what the grade does" "read from what it is worth"

edit $CO '                    .persistent(Codec.STRING)' '                    .persistent(Codec.STRING.xmap(s -> s, s -> s))'
wexpect "a grade that does not survive a restart" "not saved"

edit $CO '                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)' '                    .networkSynchronized(ByteBufCodecs.STRING_UTF8.map(s -> s, s -> s))'
wexpect "a grade that never reaches the client" "never reaches the client"

edit $MC '        com.elysium.arsenal.forge.ForgeComponents.TYPES.register(modEventBus);' '        // unregistered'
wexpect "a component written and never registered" "never registered"

edit $K '        if (stack.getItem() instanceof DiggerItem) {
            return "tool";
        }' '        if (false) {
            return "tool";
        }'
wexpect "a paxel that is a tool on one machine and a weapon on another" "disagree about what it is worth"

edit $K '        if (stack.getItem() instanceof BowItem
                || stack.getItem() instanceof ProjectileWeaponItem) {' '        if (stack.getItem() instanceof BowItem) {'
wexpect "a mechanic invisible to anybody carrying another mod's bow" "carrying anybody else"

edit $HI '        int experience = current != null ? current.experience() : cheapest(kind);' '        int experience = 1;'
wexpect "rolling away from a Fabled sword costing what a Dull one costs" "hold the hammer down"

edit $HI '                player.giveExperienceLevels(-experience);' '                player.giveExperienceLevels(0);'
wexpect "reforging that is free" "reforging is free"

edit $HI '        if (kind == null || !rolls(kind)) {' '        if (kind == null) {'
wexpect "any hammer reforging any gear" "two of the three are decoration"

edit $FI '        if (made.size() != ForgeRules.HAMMERS.size()) {' '        if (false) {'
wexpect "a hammer registry that does not count what it registered" "counts the hammers registered"

edit $FI '        for (String hammer : new TreeMap<>(ForgeRules.HAMMERS).keySet()) {' '        for (String hammer : List.of("weaponsmiths_hammer")) {'
wexpect "hammers listed by hand rather than read from the table" "not registered from the rules"

edit $G '    for id, _ in graded:
        entries[f"{NS}.grade.{id}"] = GRADE_NAMES[id]' '    for id, _ in graded[:20]:
        entries[f"{NS}.grade.{id}"] = GRADE_NAMES[id]'
regen
wexpect "grades whose tooltip reads as an id" "has no name, so its tooltip"

rm -f $M/src/main/resources/data/elysiumarsenal/recipe/toolsmiths_hammer.json
wexpect "a hammer nobody can make" "has no recipe"

echo
echo "restoring and re-checking"
restore
if python3 "$V" >/dev/null 2>&1; then echo "  tree restored clean"; else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "forge: $pass caught, $fail missed"
[[ $fail -eq 0 ]] || exit 1
