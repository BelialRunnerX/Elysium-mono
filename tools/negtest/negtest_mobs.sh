#!/bin/bash
# Negative tests for the mob checks: break one rule, expect its message, restore.
cd /tmp/Elysium-mono/elysium-space || exit 2
export JAVA_TOOL_OPTIONS=
S=src/main/java/com/elysium/space
G=src/main/resources/assets/elysiumspace/geometry
pass=0; failn=0
t() {  # file, python-replace old, new, expected message
    cp "$1" /tmp/neg_backup
    python3 - "$1" "$2" "$3" <<'PY'
import sys; p,o,n=sys.argv[1:]; s=open(p).read(); assert o in s, "pattern missing: "+o; open(p,'w').write(s.replace(o,n,1))
PY
    out=$(timeout 900 python3 validate.py 2>&1)
    cp /tmp/neg_backup "$1"
    if grep -q "$4" <<<"$out"; then pass=$((pass+1)); echo "ok   : $4"; else failn=$((failn+1)); echo "MISS : $4"; fi
}
t $S/entity/mob/CinderBoar.java "setImmuneToZombification(true);" "" "would zombify outside the Nether"
t $G/cinder_boar.json '"head": "head",' '"head": "snout",' "rig moves snout"
t $S/registry/SpaceMobs.java "put(event, SpaceEntities.FROST_RAPTOR.get(), Monster.createMonsterAttributes(), MobRules.FROST_RAPTOR);" "" "frost_raptor gets no attributes"
t $S/world/MobRules.java '40.0D, 0.4D, 9.0D' '40.0D, 0.4D, 0.0D' "only hunters deal damage"
t $S/entity/mob/FrostRaptor.java '"attack".equals(state)' '"bite".equals(state)' "never gives the rig its attack"
t $S/entity/mob/Moonfolk.java 'spawnGolemIfNeeded' 'spawnGolemsIfNeeded' "must not summon iron golems"
echo "$pass passed, $failn missed"
