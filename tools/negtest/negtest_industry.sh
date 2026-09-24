#!/usr/bin/env bash
# The second age, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-industry
R=$M/src/main/java/com/elysium/industry/rules/IndustryRules.java
SNAP=/tmp/industry_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-industry docs/SPEC_TECH.md)
restore() { rm -rf $ROOT/elysium-industry && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 300 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the model, which is what makes Age II not Age I"
edit $R '            return Math.min(1.0F, supply / demand);' '            return supply >= demand ? 1.0F : 0.0F;'
expect "a grid that is a light switch instead of a share" "share what it has out proportionally"

edit $R '        float carried = Math.min(supply, demand);' '        float carried = demand;'
expect "a wire that burns on what was asked, not what flowed" "burns on what is asked"

edit $R '        return carried > voltage.capacity();' '        return carried >= voltage.capacity();'
expect "a wire that burns at exactly its rating" "not the rating the game uses"

edit $R '        float kept = 1.0F - voltage.lossPerBlock() * Math.max(0, blocks);' '        float kept = 1.0F;'
expect "wire that loses nothing over any distance" "distance costs nothing"

edit $R '        return Math.max(0.0F, sent * kept);' '        return sent * kept;'
expect "a long enough wire delivering negative power" "negative power"

edit $R '        return supplied.ordinal() > rated.ordinal();' '        return supplied.ordinal() < rated.ordinal();'
expect "under-voltage destroying a machine instead of over-voltage" "a transformer has no purpose"

echo "the ladder"
edit $R 'new Generator(STEAM_TURBINE.output() * DIESEL_HASTE, Voltage.MEDIUM)' 'new Generator(STEAM_TURBINE.output() * DIESEL_HASTE, Voltage.LOW)'
expect "a diesel engine that fits down the first wire" "nowhere it must go"

edit $R 'new Generator(STEAM_TURBINE.output() * DIESEL_HASTE, Voltage.MEDIUM)' 'new Generator(90.0F, Voltage.MEDIUM)'
expect "a diesel engine's output written back as a number of its own" "a multiple of the turbine"

edit $R 'public static final int DIESEL_HASTE = 4;' 'public static final int DIESEL_HASTE = 1;'
expect "a diesel engine no hungrier and no stronger than a turbine" "no hastier than a turbine"

edit $R 'ARC_FURNACE = new Machine(512.0F, Voltage.MEDIUM);' 'ARC_FURNACE = new Machine(100.0F, Voltage.MEDIUM);'
expect "an arc furnace that costs no more than a crusher" "not the expensive machine"

edit $R 'public static final int SEPARATOR_MULTIPLIER = 2;' 'public static final int SEPARATOR_MULTIPLIER = 3;'
expect "Age II tripling ore, which is Age III's job" "does not double ore"

echo "the joint with the age below"
edit $R 'public static final float ON_A_SHAFT = 0.45F;' 'public static final float ON_A_SHAFT = 1.0F;'
expect "a shaft driving an Age II machine at no penalty at all" "not free either"

edit $R 'public static final float SHAFT_LEAST_RPM = 96.0F;' 'public static final float SHAFT_LEAST_RPM = 32.0F;'
expect "a timber shaft driving an arc furnace" "no reason to upgrade its shafts"

edit $R 'public static final float MOTOR_EFFICIENCY = 0.80F;' 'public static final float MOTOR_EFFICIENCY = 1.0F;'
expect "a motor that loses nothing, closing a free-energy loop" "out of nothing"

edit $R '        return KineticRules.dynamoOutput(rpm) * ALTERNATOR_OVER_DYNAMO;' '        return Math.abs(rpm) * 1.5F;'
expect "an alternator that is not measured against the dynamo" "decided in two places"

echo "how long a job takes, and the loop"
edit $R '        return Math.max(1, Math.round(Math.min(1.0F, share) * WORK_FULL));' '        return Math.round(Math.min(1.0F, share) * WORK_FULL);'
expect "a browned-out machine that makes no progress at all" "no progress at all"

edit $R '        return (work + perTick - 1) / perTick;' '        return work / perTick;'
expect "a countdown that disagrees with the machine it describes" "but the readout says"

edit $R '        return Math.max(1, ticks) * WORK_FULL;' '        return Math.max(1, ticks);'
expect "a job measured in ticks where the machine counts work" "means nothing by itself"

edit $R '                / (4.0F * ALTERNATOR_DRAW * ratedFe);' '                / (1.0F * ALTERNATOR_DRAW * ratedFe);'
expect "a closed form that says the loop pays for itself" "free energy loop"

edit $R '                / (4.0F * ALTERNATOR_DRAW * ratedFe);' '                / (40.0F * ALTERNATOR_DRAW * ratedFe);'
expect "a free-energy proof a real arrangement beats" "which beats the closed form"

edit $R '        return new KineticRules.Load(machine.draw() / SHAFT_FE_PER_TORQUE, SHAFT_LEAST_RPM);' '        return new KineticRules.Load(8.0F, SHAFT_LEAST_RPM);'
expect "every Age II machine the same weight on a shaft" "no heavier on a shaft than a crusher"

edit $R '    public static final float ALTERNATOR_DRAW =
            KineticRules.DYNAMO.draw() * ALTERNATOR_OVER_DYNAMO;' '    public static final float ALTERNATOR_DRAW = 1.0F;'
expect "an alternator that gives six times the power for a tenth of the torque" "free energy loop"

echo "lending and borrowing, which Age II knows the shape of and does not do"
edit $R '        } else if (supply > demand) {' '        }
        if (supply > demand) {'
expect "lending and borrowing written so they could both happen" "are not exclusive"

edit $R '            lent = Math.min(demand - supply, reserve.couldGive());' '            lent = demand - supply;'
expect "a grid borrowing more than the reserve has" "more than the reserve could give"

edit $R '            kept = Math.min(supply - demand, reserve.couldTake());' '            kept = supply - demand;'
expect "a grid storing more than the reserve has room for" "more than the reserve could take"

edit $R '        float carried = Math.max(supply, Math.min(demand, supply + lent) + kept);
        return new Flow(new Reading(supply + lent, demand, burns(wire, carried)),' '        float carried = 0.0F;
        return new Flow(new Reading(supply + lent, demand, burns(wire, carried)),'
expect "a wire made safe by putting a battery on the grid" "on its way into storage"

edit $R '        return new Flow(new Reading(supply + lent, demand, burns(wire, carried)),' '        return new Flow(new Reading(supply, demand, burns(wire, carried)),'
expect "a loan that never reaches the machines it was made for" "storage is a hole"

edit $R '            if (couldGive < 0.0F || couldTake < 0.0F) {' '            if (false) {'
expect "a reserve that lends a negative amount" "negative amount can be declared"

echo "independence, and the age above"
edit $M/src/main/java/com/elysium/industry/ElysiumIndustry.java \
  'public static final String MODID = "elysiumindustry";' \
  'public static final String MODID = "elysiumindustry";
    static final Class<?> UP = com.elysium.atomics.Anything.class;'
expect "Age II reaching up into Age III" "depend downwards only"

edit $R 'import com.elysium.kinetics.rules.KineticRules;' 'import com.elysium.kinetics.block.KineticLine;
import com.elysium.kinetics.rules.KineticRules;'
expect "a rules class reaching into the age below's blocks" "only into the rules of the age below"

python3 - <<'PYX'
import pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-industry/src/main/resources/META-INF/neoforge.mods.toml")
s = p.read_text(encoding="utf-8")
s = s.replace('modId="elysiumkinetics"', 'modId="immersiveengineering"', 1)
p.write_text(s, encoding="utf-8")
PYX
expect "a declared dependency on the mod this age replaces" "needs the game, the library and the age below"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all industry checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "industry: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
