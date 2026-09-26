#!/usr/bin/env bash
# Twenty-eight things that live in the Vale, broken one way at a time.
#
# The property every case below circles: walking further out gets harder rather
# than riskier. A dimension where the hardest thing can be next to the portal is
# a dimension where exploring is a coin flip -- the player who walked north dies
# and concludes it is unfair, the player who walked south clears it and concludes
# it is empty, and neither of them is wrong.
#
# It is also nobody's decision. A roster gets filled in the order somebody
# thought of the creatures and placed for flavour, because "the devil belongs in
# the sulphur springs" is a much better sentence than "the devil belongs two
# rings out". So the ring is on the row where a control can make it lie, and the
# rule that catches that is a rule rather than a tautology.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-bestiary
R=$M/src/main/java/com/elysium/bestiary/rules/DenizenRules.java
EN=$M/src/main/java/com/elysium/bestiary/entity/DenizenEntity.java
RG=$M/src/main/java/com/elysium/bestiary/registry/DenizenEntities.java
RN=$M/src/main/java/com/elysium/bestiary/client/DenizenRenderer.java
MD=$M/src/main/java/com/elysium/bestiary/ElysiumBestiary.java
CL=$M/src/main/java/com/elysium/bestiary/client/BestiaryClient.java
KR=$M/src/main/java/com/elysium/bestiary/rules/KindredRules.java
G=$M/tools/gen_bestiary.py
V=$M/validate.py
SNAP=/tmp/vale_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 700 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit."
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-bestiary)
restore() { rm -rf $ROOT/elysium-bestiary && (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
out() { (cd $M && timeout 700 python3 validate.py 2>&1); }
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

echo "the gradient"
edit $R '            new Denizen("devourer", "sulphur_springs", 3, 61.0D, 8.5D, 0.28D, 10));' \
        '            new Denizen("devourer", "sulphur_springs", 3, 7.0D, 0.5D, 0.28D, 10));'
expect "something three rings out a player could have met at the portal" "the walk bought them nothing"

edit $R '            new Denizen("pixie", "verdant_vale", 0, 16.0D, 2.0D, 0.26D, 50),' \
        '            new Denizen("pixie", "verdant_vale", 0, 160.0D, 20.0D, 0.26D, 50),'
expect "something at the portal harder than what is three rings out" "stops being a decision"

edit $R '            if (one.threat() < band[0]) {' '            if (false) {'
expect "the rule that finds something too soft for its ring" "could have met at the portal is not reported"

edit $R '            if (one.threat() >= band[1]) {' '            if (false) {'
expect "the rule that finds something too hard for its ring" "harder than what is three rings out is not reported"

echo "bands that stop being a gradient"
edit $R '        double[] edges = {20.0D, 80.0D, 200.0D, 500.0D, 1_100.0D};' \
        '        double[] edges = {20.0D, 900.0D, 200.0D, 500.0D, 1_100.0D};'
expect "an edge that goes backwards" "which is not a band at all"

edit $R '        double[] edges = {20.0D, 80.0D, 200.0D, 500.0D, 1_100.0D};' \
        '        double[] edges = {20.0D, 900.0D, 200.0D, 500.0D, 1_100.0D};'
edit $R '            if (band[0] >= band[1]) {' '            if (false) {'
expect "the rule that keeps a band a band" "where the floor is 900"

echo "a ring that lies"
edit $R '            new Denizen("devil", "cinder_flats", 3, 84.0D, 7.5D, 0.30D, 10),' \
        '            new Denizen("devil", "cinder_flats", 1, 84.0D, 7.5D, 0.30D, 10),'
expect "a creature written nearer than its own crossing" "the band of danger it is held to is the band of somewhere else"

edit $R '            if (really >= 0 && one.ring() != really) {' '            if (false) {'
expect "the rule that catches a lying ring" "written as nearer than its own crossing is not reported"

edit $R '        return Math.abs(warmth - WARMTHS / 2) + Math.abs(wet - WETS / 2);' \
        '        return 0;'
expect "a map on which everywhere is the middle" "and rime_barrens is 0"

echo "a crossing with nothing in it"
edit $R '            new Denizen("ent", "goldwood", 1, 27.0D, 5.0D, 0.26D, 34),' \
        '            new Denizen("ent", "pinewold", 1, 27.0D, 5.0D, 0.26D, 34),'
expect_regen "a crossing nothing lives in" "which reads as the dimension being unfinished"

edit $R '            if (living(place, roster).isEmpty()) {' '            if (false) {'
expect "the rule that finds an empty crossing" "with nothing in it is not reported"

edit $R '            if (!places.contains(one.place())) {' '            if (false) {'
expect "the rule that holds a creature to a crossing the Vale has" "the Vale does not have is not reported"

echo "the ceiling the whole pack is priced against"
edit $R '            new Denizen("undead", "frostmere", 3, 101.0D, 7.5D, 0.28D, 10),' \
        '            new Denizen("undead", "frostmere", 3, 1010.0D, 7.5D, 0.28D, 10),'
expect "something in the Vale harder than the hardest thing in the world" "has something above it in another dimension"

edit $R '            if (hardest > 0.0D && one.threat() >= hardest) {' '            if (false) {'
expect "the rule that holds the Vale under the world" "harder than the hardest thing in the world is not reported"

edit $R '        if (hardest <= 0.0D) {' '        if (false) {'
expect "a Vale with no beast to be held under" "no beast to be held under is not reported"

echo "what the spawn rule on disk actually says"
edit $G '              {"values": ["elysiumwilds:vale_%s" % place]})' \
        '              {"values": ["elysiumwilds:%s" % place]})'
expect_regen "a tag holding a biome no module grows" "no module in this pack grows a biome of that name"

edit $G '              {"values": ["elysiumwilds:vale_%s" % place]})' \
        '              {"values": []})'
expect_regen "a tag that holds nothing" "that tag holds nothing"

edit $G '                  "biomes": "#%s:vale_%s" % (NS, one["place"]),' \
        '                  "biomes": "#%s:vale_verdant_vale" % NS,'
expect_regen "every spawn rule pointed at one crossing" "so the gradient the rules prove is about somewhere the world does not put it"

echo "no two rosters under one name"
edit $R '            new Denizen("pixie", "verdant_vale", 0, 16.0D, 2.0D, 0.26D, 50),' \
        '            new Denizen("goblin", "verdant_vale", 0, 16.0D, 2.0D, 0.26D, 50),'
expect "one name used by two rosters" "and they all register onto one registry"

edit $R '            new Denizen("pixie", "verdant_vale", 0, 16.0D, 2.0D, 0.26D, 50),' \
        '            new Denizen("goblin", "verdant_vale", 0, 16.0D, 2.0D, 0.26D, 50),'
edit $V '        if len(_in) > 1:' '        if False:'
expect "the rule that finds a name used twice" "this module writes no such tag"

echo "the numbers the entity and the screen carry"
edit $EN '        DenizenRules.Denizen found = DenizenRules.denizen(name);' \
        '        DenizenRules.Denizen found = DenizenRules.DENIZENS.get(0);'
expect "a creature that does not find its own row" "does not find its own row"

edit $EN '        return denizen().ring();' '        return 0;'
expect "a screen that says everything is at the portal" "two different numbers"

edit $RN '        float further = 1.0F + FURTHER * one.ringsOut();' '        float further = 1.0F;'
expect "nothing on the screen saying how far out a creature lives" "the coin flip this roster exists to remove"

echo "nothing registered by hand"
edit $RG '        for (DenizenRules.Denizen one : DenizenRules.DENIZENS) {
            made.put(one.id(), BeastEntities.TYPES.register(one.id(),' \
        '        for (DenizenRules.Denizen one : java.util.List.of(DenizenRules.DENIZENS.get(0))) {
            made.put(one.id(), BeastEntities.TYPES.register(one.id(),'
expect "creatures registered from a list somebody wrote out" "named by hand rather than walked"

edit $RG '        if (made.size() != DenizenRules.DENIZENS.size()) {' '        if (false) {'
expect "nothing counting the creatures registered" "a roster that half exists"

edit $MD '        com.elysium.bestiary.registry.DenizenEntities.ALL.size();' '        '
expect "a register nothing touches while the mod is built" "depends on when FML"

echo "something has to draw them"
edit $CL '                    context -> new DenizenRenderer(context,' '                    context -> new MythRenderer(context,'
expect "creatures with no renderer registered" "takes the client down with it"

edit $CL '            event.registerLayerDefinition(DenizenModel.LAYERS.get(form),' \
        '            event.registerLayerDefinition(MythModel.LAYERS.get(form),'
expect "model layers never registered" "baking one throws"

echo "what the generator writes"
remove $M/src/main/resources/assets/elysiumbestiary/textures/entity/devourer.png
expect "a creature with no texture" "has no texture"

edit $G '        lang["bestiary.%s.ring.%s" % (NS, one["id"])] = RING_WORDS[one["ring"]]' \
        '        pass'
expect_regen "a creature with no sentence saying how far out it lives" "has no sentence saying how far out it lives"

echo "the rules are all asked"
edit $R '        bad.addAll(faultsInGradient(DENIZENS));' '        '
expect "a rule written, compiled and never added up" "faultsInGradient is written, compiled and never added up"

edit $R '        bad.addAll(faultsInBands());' '        if (false) bad.addAll(faultsInBands());'
expect "a rule added up only sometimes" "faults() has a condition in it"

echo
echo "vale: $pass caught, $fail missed"
[ "$fail" -eq 0 ] || exit 1
