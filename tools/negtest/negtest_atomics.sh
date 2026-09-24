#!/usr/bin/env bash
# The third age, broken one way at a time.
#
# Age III is the first age that can keep energy and the first that can destroy
# a base, so the breakages worth rehearsing are the ones that would be
# invisible until they had already ruined a save: energy that appears in a
# battery, a reactor that runs away while nobody is watching, and a second
# free-energy loop that the age below's proof does not cover.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-atomics
R=$M/src/main/java/com/elysium/atomics/rules/AtomicRules.java
H=$M/tools/AtomicHarness.java
SNAP=/tmp/atomics_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-atomics)
restore() { rm -rf $ROOT/elysium-atomics && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 600 python3 validate.py 2>&1); }
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

echo "the cells, and energy that must not appear"
edit $R '            int taken = (int) Math.floor(grid.toCells() * mine / couldHaveTaken);' '            int taken = (int) Math.ceil(grid.toCells() * mine / couldHaveTaken);'
expect "rounding that falls on the side of the player having more" "holding more than it was given"

edit $R '            int given = (int) Math.ceil(grid.fromCells() * mine / couldHaveGiven);' '            int given = (int) Math.floor(grid.fromCells() * mine / couldHaveGiven);'
expect "a bank that gives up less than the grid took from it" "made out of nothing"

edit $R '        } else if (surplus > 0.0F) {' '        }
        if (surplus > 0.0F) {'
expect "charging and draining written so they could both happen" "not exclusive"

edit $R '            float mine = Math.min(cell.mostIn(), cell.capacity() - was);' '            float mine = cell.mostIn();'
edit $R '            return clamp(cell, was + taken);' '            return was + taken;'
expect "a cell that ignores how much room it has left" "when offered a surplus"

edit $R '            float mine = Math.min(cell.mostOut(), was);' '            float mine = cell.mostOut();'
edit $R '            return clamp(cell, was - given);' '            return was - given;'
expect "a cell that ignores how little it has left" "when the grid drew on it"

edit $R '            fromCells = Math.min(shortfall, couldGive(cells, stored));' '            fromCells = shortfall;'
expect "a bank that hands over more than it holds" "an empty cell gives power away"

edit $R '            toCells = Math.min(surplus, couldTake(cells, stored));' '            toCells = surplus;'
expect "a bank that swallows a surplus it has no room for" "the difference was destroyed"

edit $R '        float carried = Math.max(supply, Math.min(demand, supply + fromCells) + toCells);' '        float carried = 0.0F;'
expect "a wire made safe by putting a battery on it" "on its way into a battery"

edit $R '            if (mostIn > IndustryRules.MOST_FE || mostOut > IndustryRules.MOST_FE) {' '            if (false) {'
expect "a cell that moves more power than any wire carries" "can be declared"

echo "the heat, and a reactor that must settle"
edit $R '        float shed = (temperature - AMBIENT) * Math.max(0.0F, cooling);' '        float shed = cooling * 100.0F;'
expect "a core that sheds a fixed amount instead of a fraction" "does not settle"

edit $R '        return AMBIENT + heatIn / (THERMAL_MASS * cooling);' '        return AMBIENT + heatIn / THERMAL_MASS;'
expect "a readout that predicts a temperature the core never reaches" "the readout does not predict"

edit $R '    public static final float COOLING_PER_CHANNEL = 0.05F;' '    public static final float COOLING_PER_CHANNEL = 0.30F;'
expect "cooling so strong the temperature oscillates instead of settling" "oscillates instead of settling"

edit $R '    public static final float COOLING_PER_CHANNEL = 0.05F;' '    public static final float COOLING_PER_CHANNEL = 0.02F;'
expect "a fully plumbed reactor that still cannot be run at full throttle" "there is no way to run one properly"

edit $R '    public static final float HEAT_PER_FE = 0.02F;' '    public static final float HEAT_PER_FE = 0.0002F;'
expect "a reactor that is survivable with no coolant at all" "so cooling is decoration"

edit $R '    public static final int MOST_CHANNELS = 4;' '    public static final int MOST_CHANNELS = 12;'
expect "so much cooling available that the last channels are not decisions" "not decisions"

edit $R '        if (temperature <= SAFE) {' '        if (temperature < SAFE) {'
expect "a core wearing out at exactly the temperature called safe" "already wearing out"

edit $R '        return through * through * WEAR_AT_MELTDOWN;' '        return through * WEAR_AT_MELTDOWN;'
expect "wear that climbs in a straight line, so overshooting costs nothing extra" "costs no more than the first"

edit $R '        float through = (Math.min(temperature, MELTDOWN) - SAFE) / (MELTDOWN - SAFE);' '        float through = (temperature - SAFE) / (MELTDOWN - SAFE);'
expect "wear that keeps climbing past a temperature nothing survives" "past meltdown"

edit $R '    public static final Core REACTOR = new Core(IndustryRules.MOST_FE / 2.0F);' '    public static final Core REACTOR = new Core(IndustryRules.MOST_FE / 8.0F);'
expect "a reactor three of which fit down one wire" "nothing in the age ever forces a second run of cable"

echo "the servo, and the loop the age below never proved"
edit $R '    public static final float SERVO_STALL_TORQUE = 384.0F;' '    public static final float SERVO_STALL_TORQUE = 900.0F;'
expect "a servo and an alternator that pay for themselves" "free-energy machine"

edit $R '    public static final float SERVO_STALL_TORQUE = 384.0F;' '    public static final float SERVO_STALL_TORQUE = 100.0F;'
expect "a loop that returns something other than what the age below's returns" "pinned together"

edit $R '    public static final float SERVO_FREE_RPM = KineticRules.MAX_RPM;' '    public static final float SERVO_FREE_RPM = 180.0F;'
expect "a servo a brass shaft survives, so Age III costs nothing below it" "costs nothing in the machine hall below"

edit $R '        return IndustryRules.roundTripBest(SERVO_FREE_RPM, SERVO_STALL_TORQUE,
                SERVO_RATED_FE);' '        return 0.5F;'
expect "a proof replaced by the answer it was supposed to produce" "which beats the closed form"

echo "the ladder, and the joints below"
edit $R '    public static final int ENRICHER_MULTIPLIER = IndustryRules.SEPARATOR_MULTIPLIER + 1;' '    public static final int ENRICHER_MULTIPLIER = 4;'
expect "an ore ladder retuned into 1, 2, 4 in one file" "rung below plus one"

edit $R '    public static final float SHAFT_LEAST_RPM = 256.0F;' '    public static final float SHAFT_LEAST_RPM = 192.0F;'
expect "a brass shaft at exactly its rating driving an Age III machine" "no reason to upgrade its shafts"

edit $R '    public static final float ON_A_SHAFT = IndustryRules.ON_A_SHAFT / 2.0F;' '    public static final float ON_A_SHAFT = 0.45F;'
expect "a fallback route that stops getting worse as the spine climbs" "written against Age II"

edit $R '            new IndustryRules.Machine(2048.0F, IndustryRules.Voltage.HIGH);' '            new IndustryRules.Machine(2048.0F, IndustryRules.Voltage.LOW);'
expect "an Age III machine that runs off Age II's first wire" "forces the step up to steel"

edit $R '    public static final IndustryRules.Generator SOLAR_PANEL =
            new IndustryRules.Generator(3.0F, IndustryRules.Voltage.LOW);' '    public static final IndustryRules.Generator SOLAR_PANEL =
            new IndustryRules.Generator(300.0F, IndustryRules.Voltage.LOW);'
expect "a solar panel that replaces the age instead of motivating its cells" "replaces the age"

echo "independence, and a second model of electricity"
edit $R 'import com.elysium.industry.rules.IndustryRules;' 'import com.elysium.industry.block.GridLine;
import com.elysium.industry.rules.IndustryRules;'
expect "a rules class reaching into the age below's blocks" "only into the rules of the ages below"

edit $R '    // ==================================================================
    // Storage
    // ==================================================================' '    public enum Voltage { LOW, HIGH }

    // ==================================================================
    // Storage
    // =================================================================='
expect "a second voltage table in a second age" "second voltage table"

edit $M/src/main/java/com/elysium/atomics/ElysiumAtomics.java \
  'public static final String MODID = "elysiumatomics";' \
  'public static final String MODID = "elysiumatomics";
    static final Class<?> UP = com.elysium.foundry.Anything.class;'
expect "Age III reaching up into Age IV" "depend downwards only"

python3 - <<'PYX'
import pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-atomics/src/main/resources/META-INF/neoforge.mods.toml")
s = p.read_text(encoding="utf-8")
s = s.replace('modId="elysiumindustry"', 'modId="mekanism"', 1)
p.write_text(s, encoding="utf-8")
PYX
expect "a declared dependency on the mod this age replaces" "needs the game, the library and the two ages below"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all atomics checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "atomics: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
