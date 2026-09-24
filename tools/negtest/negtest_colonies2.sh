#!/usr/bin/env bash
# Elysium Colonies, part two: the charter, the reckoning and the people.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-colonies
S=$M/src/main/java/com/elysium/colonies
R=$M/src/main/resources
D=$R/data/elysiumcolonies
SNAP=/tmp/colonies2_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-colonies elysium-space/src/main/java/com/elysium/space/world/SpaceWorld.java)
restore() { rm -rf $ROOT/elysium-colonies && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, 'anchor not found in ' + p
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@"; }

echo "the charter"
python3 -c "
import json
p='$D/charter/cinderveil.json'
d=json.load(open(p)); d['hazard']=0.0
json.dump(d,open(p,'w'),indent=2)"
expect "a charter that says Cinderveil is a meadow" "the two have drifted"

python3 -c "
import json
p='$D/charter/pale_moon.json'
d=json.load(open(p)); d['air']='breathable'
json.dump(d,open(p,'w'),indent=2)"
expect "a charter that says the moon has air" "its air is breathable"

rm -f $D/charter/frostfall.json
expect "a declared world nobody wrote a charter for" "lives in a meadow"

python3 -c "
import json
p='$D/charter/cinderveil.json'
d=json.load(open(p)); d['natives'].append('cooper')
json.dump(d,open(p,'w'),indent=2)"
expect "a misspelled material silently costing a world its charter" "most likely a misspelling"

python3 -c "
import json
p='$D/charter/cinderveil.json'
d=json.load(open(p)); d['air']='thick'
json.dump(d,open(p,'w'),indent=2)"
expect "air the game has no word for" "no word for"

python3 -c "
import json
p='$D/charter/frostfall.json'
d=json.load(open(p)); d['reach']=-3
json.dump(d,open(p,'w'),indent=2)"
expect "a world a negative distance from home" "negative distance"

cp $D/charter/frostfall.json $D/charter/frostfall_copy.json
expect "two charters claiming one world" "silently ignored"

rm -f $D/charter_needs/settlement_needs.json
expect "nothing saying what a settlement consumes" "worth the same as a bare one"

echo "the reckoning"
edit $S/event/ColonyLife.java 'if (town.day() <= ledger.lastDay()) {
            return;                       // already counted today
        }' ''
expect "development accruing at the tick rate" "at the tick rate"

edit $S/event/ColonyLife.java 'if (level.getGameTime() % INTERVAL != 0L) {
            return;
        }' ''
expect "every level ticked for colonies every tick" "rather than on a schedule"

edit $S/event/ColonyLife.java 'CharterRules.weighted(CharterRules.workFactor(local),
                charter.reach())' 'CharterRules.workFactor(local)'
expect "distance that weighs on nothing" "import its way out of its world"

edit $S/event/ColonyLife.java 'sum += CitizenRules.output(stress, wounds, practised, world);' \
                              'sum += 1.0F;'
expect "a day's work decided outside the rules" "harness proves"

edit $S/event/ColonyLife.java 'if (!GrowthRules.willRaise(structure.level(), structure.maximum())) {
                continue;
            }' ''
expect "a town raising itself past its allowance" "nothing left to come home for"

edit $S/event/ColonyLife.java 'if (!GrowthRules.affords(banked, structure.level() + 1)) {
                continue;
            }' ''
expect "a town ordering upgrades it has not paid for" "has not paid for"

edit $S/event/ColonyLife.java 'if (GrowthRules.patient(town.day(), ledger.lastUpgrade())) {' 'if (true) {'
expect "a builder handed every upgrade at once" "empty warehouse"

edit $S/event/ColonyLife.java 'if (!(event.getLevel() instanceof ServerLevel level)) {' \
                              'if (!(event.getLevel() instanceof net.minecraft.world.level.Level level)) {'
expect "a colony reckoned on the client" "different numbers on it"

echo "the people"
edit $S/citizen/ColonyMemory.java 'public final class ColonyMemory extends SavedData {' \
                                  'public final class ColonyMemory {'
expect "a lifetime of practice thrown away by a chunk unload" "chunk unload"

edit $S/citizen/ColonyMemory.java 'return new Life(stress, wounds, practice + Math.max(0, more));' \
                                  'return new Life(stress, wounds, more);'
expect "practice a change of trade takes away" "lifetime of work"

edit $S/citizen/CitizenStress.java 'reasons.add(ElysiumStressor.HOMELESS);' ''
expect "a citizen who is never homeless" "one of the twelve reasons"

edit $S/citizen/CitizenStress.java 'int fromLife = ElysiumStress.total(reasonsFor(person, town, buildings, raided));' \
                                   'int fromLife = reasonsFor(person, town, buildings, raided).size() * 10;'
expect "stress added up twice, differently" "different amounts"

edit $S/citizen/CitizenStress.java 'CharterRules.burden(charter.hazard(),
                sealed(buildings, town.citizens()))' '0'
expect "a world that costs its people nothing" "Cinderveil is a meadow"

edit $S/citizen/CitizenStress.java '+ CharterRules.airBurden(charter.air() == Charter.Air.BREATHABLE,
                        charter.air() == Charter.Air.NONE)' ''
expect "vacuum that costs nothing" "air costs a citizen nothing"

echo "what reaches the host"
edit $S/ElysiumColonies.java 'com.elysium.colonies.colony.Colonies.registerHappiness(modEventBus,
                (level, colonyId, person) ->
                        com.elysium.colonies.rules.CitizenRules.happiness(
                                com.elysium.colonies.citizen.ColonyMemory.of(level)
                                        .lifeOf(colonyId, person.id()).stress()));' ''
expect "every number computed and none of it visible" "every number it computes is invisible"

edit $S/colony/Colonies.java 'colony.getCitizenManager().injectModifier(new StaticHappinessModifier(
                id, weight, new DynamicHappinessSupplier(HAPPINESS_FUNCTION)));' ''
expect "a modifier built and never installed" "never installed"

edit $S/colony/Colonies.java 'if (data.getCitizenHappinessHandler().getModifier(id) != null) {
                return false;              // already carried; nothing to do
            }' ''
expect "one copy of the modifier per day" "one copy of it per day"

edit $S/colony/Colonies.java 'new DynamicHappinessSupplier(HAPPINESS_FUNCTION)));' \
                             'new com.minecolonies.api.entity.citizen.happiness.StaticHappinessSupplier(1.0D)));'
expect "a modifier that cannot be written to disk" "written to disk and read back"

echo
echo "restoring and re-checking"
restore
if (cd $M && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
echo "colonies (depth): $pass caught, $fail missed"
[ "$fail" -eq 0 ]
