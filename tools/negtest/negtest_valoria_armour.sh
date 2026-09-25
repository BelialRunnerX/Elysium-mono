#!/bin/bash
# Valoria: armour in elysium-armory. Each line breaks one thing the roster-driven
# materials promise and expects validate.py to say so.
cd /tmp/Elysium-mono/elysium-armory || exit 2
export JAVA_TOOL_OPTIONS=
pass=0; failn=0
t() {
    cp "$1" /tmp/nb_va
    python3 - "$1" "$2" "$3" <<'PY'
import sys; p,o,n=sys.argv[1:]; s=open(p,encoding="utf-8").read(); assert o in s, "pattern missing: "+o; open(p,'w',encoding="utf-8",newline="\n").write(s.replace(o,n,1))
PY
    out=$(timeout 1800 python3 validate.py 2>&1)
    cp /tmp/nb_va "$1"
    if grep -q "$4" <<<"$out"; then pass=$((pass+1)); echo "ok   : $4"; else failn=$((failn+1)); echo "MISS : $4"; fi
}
M=src/main/resources/data/elysiumarmory/elysium_materials
t $M/nullsteel.json '"armor": 70.0,' '"armor": 7.0,' "in the roster"
t $M/cobalt.json '"durability": 1750.0,' '"durability": 175.0,' "in the roster"
t $M/chitin.json '"cordon"' '"cordoned"' "cordon category"
t $M/lattice.json '"trait": "void_ward"' '"trait": "void_warden"' "a trait that does not exist"
t tools/gen_materials.py '"lunite": "moonwalker",' '"lunite": "malleable",' "not what tools/gen_materials.py generates"
F=$M/mireweave.json
cp $F /tmp/nb_va_b; rm $F
out=$(timeout 1800 python3 validate.py 2>&1); cp /tmp/nb_va_b $F
grep -q "has no material here" <<<"$out" && { pass=$((pass+1)); echo "ok   : missing Cordon material"; } || { failn=$((failn+1)); echo "MISS : missing Cordon material"; }
echo "$pass passed, $failn missed"
