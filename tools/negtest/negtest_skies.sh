#!/bin/bash
cd /tmp/Elysium-mono/elysium-space || exit 2
export JAVA_TOOL_OPTIONS=
pass=0; failn=0
t() {
    cp "$1" /tmp/neg_backup3
    python3 - "$1" "$2" "$3" <<'PY'
import sys; p,o,n=sys.argv[1:]; s=open(p).read(); assert o in s, "pattern missing: "+o; open(p,'w').write(s.replace(o,n,1))
PY
    out=$(timeout 900 python3 validate.py 2>&1)
    cp /tmp/neg_backup3 "$1"
    if grep -q "$4" <<<"$out"; then pass=$((pass+1)); echo "ok   : $4"; else failn=$((failn+1)); echo "MISS : $4"; fi
}
D=src/main/resources/data/elysiumspace/dimension_type
A=src/main/resources/assets/elysiumspace/skies
t $D/frostfall.json '"elysiumspace:frostfall"' '"minecraft:overworld"' "frostfall still uses minecraft:overworld"
t $A/pale_moon.json '"count": 40000' '"count": 100000' "100000 stars"
t $A/high_orbit.json 'environment/overworld.png' 'environment/earth.png' "which is missing"
t $A/cinderveil.json '"clouds": true' '"cloudz": true' "FAIL sky:cinderveil"
echo "$pass passed, $failn missed"
