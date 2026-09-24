#!/bin/bash
cd /tmp/Elysium-mono/elysium-space || exit 2
export JAVA_TOOL_OPTIONS=
S=src/main/java/com/elysium/space
pass=0; failn=0
t() {
    cp "$1" /tmp/neg_backup2
    python3 - "$1" "$2" "$3" <<'PY'
import sys; p,o,n=sys.argv[1:]; s=open(p).read(); assert o in s, "pattern missing: "+o; open(p,'w').write(s.replace(o,n,1))
PY
    out=$(timeout 900 python3 validate.py 2>&1)
    cp /tmp/neg_backup2 "$1"
    if grep -q "$4" <<<"$out"; then pass=$((pass+1)); echo "ok   : $4"; else failn=$((failn+1)); echo "MISS : $4"; fi
}
t $S/world/StationRules.java 'SMALL("small", 100, 100, 38, 58, 38)' 'SMALL("small", 100, 100, 38, 58, 39)' "StationRules says"
t $S/world/StationRules.java 'SPACING = 1024' 'SPACING = 100' "could stand in each other"
t $S/entity/RocketEntity.java 'SpaceStations.arrival(pilot, target)' 'SpaceStations.arrival(pilot, null)' "do not land on the pilot"
t tools/gen_space.py '("medium", 150, 150)' '("medium", 150, 120)' "disagree on what stations cost"
t $S/station/SpaceStations.java 'MISSING_MATERIALS, NO_TEMPLATE }' 'MISSING_MATERIALS, NO_TEMPLATE, LOST }' "result LOST has no message"
echo "$pass passed, $failn missed"
