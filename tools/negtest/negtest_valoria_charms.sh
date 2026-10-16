#!/bin/bash
# Valoria: charms in elysium-trinkets. Each line breaks one thing the companion
# slot promises and expects validate.py to say so.
cd /tmp/Elysium-mono/elysium-trinkets || exit 2
export JAVA_TOOL_OPTIONS=
S=src/main/java/com/elysium/trinkets
pass=0; failn=0
t() {
    cp "$1" /tmp/nb_vc
    python3 - "$1" "$2" "$3" <<'PY' || { echo "STALE ANCHOR: $4"; failn=$((failn+1)); return; }
import sys; p,o,n=sys.argv[1:]; s=open(p,encoding="utf-8").read(); assert o in s, "pattern missing: "+o; open(p,'w',encoding="utf-8",newline="\n").write(s.replace(o,n,1))
PY
    out=$(timeout 600 python3 validate.py 2>&1)
    cp /tmp/nb_vc "$1"
    if grep -q "$4" <<<"$out"; then pass=$((pass+1)); echo "ok   : $4"; else failn=$((failn+1)); echo "MISS : $4"; fi
}
t $S/trinket/CompanionTrinkets.java '                @Override
                public float favorScale(Player player) {
                    return 0.75F;
                }' '' "strictly better than an empty slot"
t $S/trinket/CompanionTrinkets.java 'MOTE, CARRION_DRONE, LATTICE_SHARD, PURGE_DRONE,' 'MOTE, CARRION_DRONE, LATTICE_SHARD,' "missing from its ALL array"
t $S/ElysiumTrinkets.java 'for (ElysiumTrinket trinket : CompanionTrinkets.ALL) {' 'for (ElysiumTrinket trinket : new ElysiumTrinket[0]) {' "never registers CompanionTrinkets.ALL"
t src/main/resources/data/elysiumtrinkets/curios/slots/companion.json '"order": 1200,' '"operation": "SET",' "names no icon or no order\|sets validators or an operation"
t src/main/resources/data/elysiumtrinkets/curios/slots/companion.json '"icon": "elysiumtrinkets:slot/companion_slot"' '"icon": "elysiumtrinkets:slot/pet_slot"' "has no texture"
F=src/main/resources/data/elysiumtrinkets/loot_table/trinkets/purge_drone.json
cp $F /tmp/nb_vc_b; rm $F
out=$(timeout 600 python3 validate.py 2>&1); cp /tmp/nb_vc_b $F
grep -q "found trinket with no loot table" <<<"$out" && { pass=$((pass+1)); echo "ok   : companion with no loot table"; } || { failn=$((failn+1)); echo "MISS : companion with no loot table"; }
# The tree the cases leave must pass its validator: a case is "caught" just as
# well off a break the tree had before the first one (16 Oct: run on a snapshot
# taken half way through negtest_p2.sh, whose iron.json was still broken, the
# first case passed that way and nothing looked again).
if timeout 600 python3 validate.py >/dev/null 2>&1; then echo "tree restored clean"
else echo "RESTORE FAILED: validate.py does not pass on the tree the cases left"; failn=$((failn+1)); fi
echo "$pass passed, $failn missed"
[ "$failn" -eq 0 ]
