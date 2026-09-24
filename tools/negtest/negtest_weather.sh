#!/usr/bin/env bash
# Weather that does something, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
BAK=/tmp/weather_bak
pass=0; fail=0
rm -rf $BAK && mkdir -p $BAK
# The FIRST state of a file, never a later one: a test that edits the same
# file twice would otherwise back up the half-edited version and "restore" to
# it, leaving the tree quietly changed and every later test lying.
keep() { local rel="${1#$ROOT/}"; mkdir -p "$BAK/$(dirname "$rel")"; [ -f "$BAK/$rel" ] || cp "$1" "$BAK/$rel" 2>/dev/null; }
restore() {
  local missed=0
  while read -r f; do
    cp -f "$BAK/$f" "$ROOT/$f" || missed=1
  done < <(cd $BAK && find . -type f 2>/dev/null | sed 's|^\./||')
  if [ "$missed" -ne 0 ]; then echo "  RESTORE ERROR"; fail=$((fail+1)); fi
  rm -rf $BAK; mkdir -p $BAK
}
out() { (cd $ROOT/$1 && python3 validate.py 2>&1); }
expect() { local mod="$1" what="$2" want="$3" got
  got="$(out "$mod")"
  if [[ "$got" == *"$want"* ]]; then echo "  caught : $what"; pass=$((pass+1));
  else echo "  MISSED : $what"; fail=$((fail+1)); fi; restore; }
edit() { keep "$1"; python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, 'anchor not found in ' + p
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "  STALE ANCHOR in $1 — stopping"; exit 2; }; }

L=$ROOT/elysium-lib/src/main/java/com/elysium/lib
S=$ROOT/elysium-space/src/main/java/com/elysium/space

echo "the arithmetic"
edit $L/weather/ElysiumWeatherRules.java 'if (intoWatch < ramp) {
            return intoWatch / ramp;' 'if (intoWatch < ramp) {
            return 1.0F;'
expect elysium-lib "a storm at full strength the instant it starts" "full strength the instant it starts"

edit $L/weather/ElysiumWeatherRules.java 'return 1.0F;
    }

    /** Which watch a time of day falls in. */' 'return 0.5F;
    }

    /** Which watch a time of day falls in. */'
expect elysium-lib "a storm that never reaches full strength" "never reaches full strength"

edit $L/weather/ElysiumWeatherRules.java 'if (Math.floorMod(rolled, 8) >= Math.min(MOST_STORMY, storminess)) {' 'if (Math.floorMod(rolled, 16) >= Math.min(MOST_STORMY, storminess)) {'
expect elysium-lib "a world that storms half as often as it says" "storms 2.0 in eight"

# A calm world is calm twice over - an early return and a threshold of zero -
# so the only way to see whether the check works is to break both.
edit $L/weather/ElysiumWeatherRules.java 'if (kinds <= 0 || storminess <= 0) {' 'if (kinds <= 0) {'
edit $L/weather/ElysiumWeatherRules.java 'if (Math.floorMod(rolled, 8) >= Math.min(MOST_STORMY, storminess)) {' 'if (Math.floorMod(rolled, 8) >= Math.max(1, storminess)) {'
expect elysium-lib "a world declared calm that storms anyway" "declared calm had weather"

edit $L/weather/ElysiumWeatherRules.java 'public static final int MOST_STORMY = 6;' 'public static final int MOST_STORMY = 8;'
expect elysium-lib "a sky that is always angry" "climate rather than weather"

edit $L/weather/ElysiumWeatherRules.java 'public static final float LEAST_OUTPUT = 0.15F;' 'public static final float LEAST_OUTPUT = 0.0F;'
expect elysium-lib "a storm that switches machines off" "can stop a machine dead"

edit $L/weather/ElysiumWeatherRules.java 'long mixed = world * 0x9E3779B97F4A7C15L + day * 0xBF58476D1CE4E5B9L
                + watch * 0x94D049BB133111EBL;' 'long mixed = world + day + watch;'
expect elysium-lib "one world's weather a day later" "another's, a day later"

echo "the wiring"
edit $L/weather/ElysiumWeather.java 'ElysiumHazardField.registerSource("elysiumlib:weather",' 'if (false) ElysiumHazardField.registerSourceX("elysiumlib:weather",'
expect elysium-lib "weather with a pipeline of its own" "not a hazard source"

edit $L/weather/ElysiumWeather.java 'long time = level.getDayTime();' 'long time = 0L;'
expect elysium-lib "a sky that is not read off the clock" "not read off the world's own clock"

edit $L/weather/ElysiumWeather.java 'return ElysiumWeatherRules.machineShare(spell.kind().machines(),
                spell.strength());' 'return 1.0F - spell.kind().machines();'
expect elysium-lib "machine output decided outside the rules" "decided outside the rules"

edit $L/event/ElysiumWeatherHandler.java 'if (now.equals(was)) {
            return;
        }' ''
expect elysium-lib "the weather announced every second" "a message a player turns off"

edit $L/event/ElysiumWeatherHandler.java 'if (!level.canSeeSky(at)) {
                continue;
            }' ''
expect elysium-lib "a storm drawn underground" "drawn underground"

python3 - <<'PYEOF'
import json, os, shutil
p = "/tmp/Elysium-mono/elysium-lib/src/main/resources/assets/elysiumlib/lang/en_us.json"
os.makedirs("/tmp/weather_bak/elysium-lib/src/main/resources/assets/elysiumlib/lang", exist_ok=True)
shutil.copy(p, "/tmp/weather_bak/elysium-lib/src/main/resources/assets/elysiumlib/lang/en_us.json")
d = json.load(open(p, encoding="utf-8")); d.pop("elysium.weather.begins")
open(p, "w", encoding="utf-8").write(json.dumps(d, indent=2) + "\n")
PYEOF
expect elysium-lib "the line that explains the damage, as a raw key" "reads as a raw key"

echo "the worlds"
edit $S/world/SpaceWeather.java 'ElysiumWeather.declare(world("frostfall"),
                new ElysiumWeather.Climate(List.of(WHITEOUT), 3));' ''
edit $S/world/SpaceWeather.java 'public static final ElysiumWeather.Kind WHITEOUT =
            new ElysiumWeather.Kind("whiteout", ElysiumHazards.CRYOGENIC, 0.65F, 0.55F);' ''
edit $S/world/SpaceWeather.java 'public static final ElysiumWeather.Kind REGOLITH_GUST =
            new ElysiumWeather.Kind("regolith_gust", ElysiumHazards.CORROSIVE, 0.35F, 0.35F);' ''
edit $S/world/SpaceWeather.java 'public static final ElysiumWeather.Kind FIRESTORM =
            new ElysiumWeather.Kind("firestorm", ElysiumHazards.THERMAL, 0.70F, 0.30F);' ''
expect elysium-space "most skies doing one thing" "so most skies do one thing"

edit $S/world/SpaceWeather.java 'ElysiumWeather.declare(world("cinderveil"),' 'ElysiumWeather.declare(world("emerald_bastion"),'
expect elysium-space "a storm on the paradise" "weather is declared for emerald_bastion"

edit $S/world/SpaceWeather.java 'ElysiumWeather.declare(world("frostfall"),' 'ElysiumWeather.declare(world("frostfal"),'
expect elysium-space "weather for a world that does not exist" "not one of this module's worlds"

python3 - <<'PYEOF'
import json, os, shutil
p = "/tmp/Elysium-mono/elysium-space/src/main/resources/assets/elysiumspace/lang/en_us.json"
os.makedirs("/tmp/weather_bak/elysium-space/src/main/resources/assets/elysiumspace/lang", exist_ok=True)
shutil.copy(p, "/tmp/weather_bak/elysium-space/src/main/resources/assets/elysiumspace/lang/en_us.json")
d = json.load(open(p, encoding="utf-8")); d.pop("elysium.weather.ion_storm")
open(p, "w", encoding="utf-8").write(json.dumps(d, indent=2) + "\n")
PYEOF
expect elysium-space "a storm with no name" "the ion_storm has no name"

edit $S/machine/SolarPanelBlockEntity.java 'int made = Math.round(MachineRules.solarPerTick(sun, airless)
                * com.elysium.lib.weather.ElysiumWeather.machineShare(level));' 'int made = MachineRules.solarPerTick(sun, airless);'
expect elysium-space "a panel that ignores the sky" "as much under an ash cloud"

echo
echo "restoring and re-checking"
restore
clean=1
for mod in elysium-lib elysium-space; do
  (cd $ROOT/$mod && python3 validate.py >/dev/null 2>&1) || clean=0
done
if [ "$clean" -eq 1 ]; then echo "  trees restored clean"; else echo "  RESTORE FAILED"; fail=$((fail+1)); fi
echo "weather: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
