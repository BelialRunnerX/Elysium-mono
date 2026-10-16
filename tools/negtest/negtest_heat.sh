#!/usr/bin/env bash
# Heat's two gates, the experience bar, and the two skins — one break at a time.
set -uo pipefail
M=/tmp/Elysium-mono
LIB=$M/elysium-lib; CORE=$M/elysium-core
F=$LIB/src/main/java/com/elysium/lib/hazard/ElysiumHazardField.java
HC=$LIB/src/main/java/com/elysium/lib/hazard/ElysiumHeatCache.java
SB=$LIB/src/main/java/com/elysium/lib/client/ElysiumStatusBars.java
T=$LIB/src/main/resources/data/elysiumlib/tags/block/heat_source.json
CL=$CORE/src/main/java/com/elysium/core/client/ElysiumClient.java
SK=$CORE/src/main/resources/assets/elysium/textures/entity
pass=0; fail=0
expect()  { local w="$2" g; g="$(cd $LIB  && python3 validate.py 2>&1)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1)); else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
expectc() { local w="$2" g; g="$(cd $CORE && python3 validate.py 2>&1)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1)); else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read(); assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "the altitude gate"
cp $F /tmp/hf.bak
edit $F 'public static final int THERMAL_ALTITUDE = 200;' 'public static final int THERMAL_ALTITUDE = 0;'
expect "heat back at the height people live at" "most of the overworld's warm half"
cp /tmp/hf.bak $F

cp $F /tmp/hf.bak
edit $F 'public static final int THERMAL_ALTITUDE = 200;' 'public static final int THERMAL_ALTITUDE = 100;'
expect "heat beginning below where the cold does" "there is a band where a scorching biome is both"
cp /tmp/hf.bak $F

cp $F /tmp/hf.bak
edit $F 'if (temperature < SCORCHING || !daylight) {' 'if (temperature < SCORCHING) {'
expect "a desert at midnight as hot as at noon" "stops being an answer"
cp /tmp/hf.bak $F

cp $F /tmp/hf.bak
edit $F '        return Math.max(fromSun(level, pos), fromFire(level, pos));' '        return fromFire(level, pos);'
expect "the altitude gate computed and never used" "the altitude gate the owner asked for"
cp /tmp/hf.bak $F

echo "the fire gate"
cp $F /tmp/hf.bak
edit $F '        return Math.max(fromSun(level, pos), fromFire(level, pos));' '        return fromSun(level, pos);'
expect "the fire gate computed and never used" "the fire gate the owner asked for"
cp /tmp/hf.bak $F

cp $F /tmp/hf.bak
edit $F 'public static final int HEAT_FOR_FULL = 12;' 'public static final int HEAT_FOR_FULL = 2;'
expect "one lava block in a cave burning you" "ordinary mining is a hazard"
cp /tmp/hf.bak $F

cp $F /tmp/hf.bak
edit $F 'public static final int HEAT_STEP = 2;' 'public static final int HEAT_STEP = 1;'
expect "a scan four times as expensive" "somebody else's server"
cp /tmp/hf.bak $F

cp $F /tmp/hf.bak
edit $F '                    if (!level.isLoaded(at)) {
                        continue;
                    }' ''
expect "a scan that generates terrain at the view edge" "generates terrain"
cp /tmp/hf.bak $F

cp $F /tmp/hf.bak
edit $F '        float fromAltitude = biome.value().getBaseTemperature() < SCORCHING
                ? ramp(pos.getY(), COLD_ALTITUDE, COLD_CEILING)
                : 0.0F;' '        float fromAltitude = ramp(pos.getY(), COLD_ALTITUDE, COLD_CEILING);'
expect "a desert mesa both freezing and burning" "both freezing and burning"
cp /tmp/hf.bak $F

cp $HC /tmp/hc.bak
edit $HC 'public static final int TTL = 40;' 'public static final int TTL = 1;'
expect "the fire scan run nearly every tick" "nearly every time it is asked"
cp /tmp/hc.bak $HC

mv $T /tmp/ht.bak
expect "the heat_source tag deleted" "a campfire in a sealed room"
mv /tmp/ht.bak $T

echo "the experience bar"
cp $SB /tmp/sb.bak
edit $SB 'boolean level = VanillaGuiLayers.EXPERIENCE_LEVEL.equals(event.getName());' 'boolean level = false;'
expect "vanilla's green digits left over an Elysium bar" "the experience level"
cp /tmp/sb.bak $SB

cp $SB /tmp/sb.bak
edit $SB 'if (!mc.gameMode.hasExperience()) {' 'if (false) {'
expect "an experience bar in creative" "a bar vanilla never drew them"
cp /tmp/sb.bak $SB

echo "the two skins"
cp $CL /tmp/cl.bak
edit $CL '        public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Zombie entity) {
            return texture;
        }' '        public ResourceLocation notTheOverride(net.minecraft.world.entity.monster.Zombie entity) {
            return texture;
        }'
expectc "the texture override removed" "vanilla's zombie skin again"
cp /tmp/cl.bak $CL

mv $SK/imperial_enforcer.png /tmp/ie.bak
expectc "the enforcer's skin missing" "run regen.sh"
mv /tmp/ie.bak $SK/imperial_enforcer.png

echo
if (cd $M && python3 validate.py >/dev/null 2>&1); then echo "  tree restored clean"; else echo "  RESTORE FAILED"; fail=$((fail+1)); fi
echo "heat and skins: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
