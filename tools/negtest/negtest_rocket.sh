#!/bin/bash
cd /tmp/Elysium-mono || exit 1
exec 9>/tmp/negtest_rocket.lock
negtest_lock() { if command -v flock >/dev/null 2>&1; then flock -n "$1"; else mkdir "$2" 2>/dev/null && trap "rmdir '$2' 2>/dev/null" EXIT; fi; }  # Git Bash on Windows has no flock
negtest_lock 9 /tmp/negtest_rocket.lockdir || { echo "busy"; exit 2; }
J=elysium-space/src/main/java/com/elysium/space
RK=$J/entity/RocketEntity.java; NET=$J/network/SpaceNetwork.java; RT=$J/world/SpaceRoute.java
ITEM=$J/registry/SpaceItems.java; LANG=elysium-space/src/main/resources/assets/elysiumspace/lang/en_us.json
ET=elysium-space/src/main/resources/assets/elysiumspace/textures/entity/rocket.png
FILES="$RK $NET $RT $ITEM $LANG $ET"
pass=0; miss=0
snap()    { rm -rf /tmp/_nr; mkdir -p /tmp/_nr; i=0; for f in $FILES; do cp "$f" /tmp/_nr/$i; i=$((i+1)); done; }
restore() { i=0; for f in $FILES; do cp /tmp/_nr/$i "$f"; i=$((i+1)); done; }
python3 elysium-space/validate.py >/dev/null 2>&1 || { echo "SETUP FAILED: not clean"; exit 2; }
echo "baseline clean"
try() { local label="$1" want="$2" edit="$3"; snap
  if ! python3 -c "$edit"; then echo "SETUP FAILED: $label"; restore; return; fi
  if python3 elysium-space/validate.py 2>&1 | grep -qF "$want"; then echo "CAUGHT : $label"; pass=$((pass+1))
  else echo "MISSED : $label  (expected: $want)"; miss=$((miss+1)); fi
  restore; }
R='
import sys, json, os
def rep(path, old, new):
    s = open(path, encoding="utf-8").read()
    if old not in s: sys.exit("anchor not found in " + path + ": " + old[:60])
    open(path, "w", encoding="utf-8").write(s.replace(old, new, 1))
'
try "no pilot check" "checks that the sender is riding" "$R
rep('$RK', 'if (phase() != Phase.AWAITING || pilot.getVehicle() != this) {', 'if (phase() != Phase.AWAITING) {')"
try "no route check" "checks the destination against the fuel" "$R
rep('$RK', 'if (from == null || !SpaceRoute.canTravel(from, destination, fuel(), SpaceRoute.Fuel.FUEL)) {', 'if (from == null) {')"
try "anyone launches" "does not check that the asker is its pilot" "$R
rep('$RK', 'if (phase() != Phase.IDLE || getFirstPassenger() != asker) {', 'if (phase() != Phase.IDLE) {')"
try "countdown shortened" "Stellaris's 200 ticks" "$R
rep('$RK', 'COUNTDOWN_TICKS = 200;', 'COUNTDOWN_TICKS = 20;')"
try "rocket found by id" "some way other than the sender's own vehicle" "$R
rep('$NET', 'player.getVehicle() instanceof RocketEntity rocket) {\n                rocket.travelTo', 'player.level().getEntities(null, player.getBoundingBox()).get(0) instanceof RocketEntity rocket) {\n                rocket.travelTo')"
try "client class in entity" "client classes load only on the client" "$R
rep('$RK', 'import com.elysium.space.network.SpacePayloads;', 'import com.elysium.space.network.SpacePayloads;\nimport com.elysium.space.client.SpaceKeys;')"
try "client import outside client" "a client-only class, outside the client package" "$R
rep('$RK', 'import com.elysium.space.network.SpacePayloads;', 'import com.elysium.space.network.SpacePayloads;\nimport net.minecraft.client.Minecraft;')"
try "free trips" "WorldHarness failed" "$R
rep('$RT', 'return (int) Math.ceil(gap / fuel.megametresPerMb);', 'return 0;')"
try "bastion too close" "WorldHarness failed" "$R
rep('$RT', 'd.put(\"emerald_bastion\", 92_000.0D);', 'd.put(\"emerald_bastion\", 50_000.0D);')"
try "item without lang" "item fuel_canister has no lang entry" "$R
d=json.load(open('$LANG')); del d['item.elysiumspace.fuel_canister']; json.dump(d,open('$LANG','w'))"
try "message key missing" "which the lang file does not have" "$R
d=json.load(open('$LANG')); del d['elysiumspace.rocket.no_fuel']; json.dump(d,open('$LANG','w'))"
try "entity texture missing" "entity rocket has no texture" "
import os; os.remove('$ET')"
try "a motor no upgrade fits" "no upgrade item fits the PLUTONIUM motor" "$R
rep('$RK', 'return SpaceRoute.Fuel.PLUTONIUM;', 'return SpaceRoute.Fuel.URANIUM;')"
try "a fuel no case takes" "has no case for the RADIOACTIVE motor" "$R
rep('$RK', 'case RADIOACTIVE -> {', 'default -> {')"
try "a motor fitted over a full tank" "does not require an empty tank" "$R
rep('$RK', 'if (phase() != Phase.IDLE || motor() == fitted || fuel() > 0) {', 'if (phase() != Phase.IDLE || motor() == fitted) {')"
echo "caught $pass, missed $miss"
