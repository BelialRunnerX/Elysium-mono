#!/bin/bash
# Negative tests for the mob checks: break one rule, expect its message, restore.
cd /tmp/Elysium-mono/elysium-space || exit 2
export JAVA_TOOL_OPTIONS=
S=src/main/java/com/elysium/space
G=src/main/resources/assets/elysiumspace/geometry
pass=0; failn=0
t() {  # file, python-replace old, new, expected message
    cp "$1" /tmp/neg_backup
    python3 - "$1" "$2" "$3" <<'PY' || { echo "STALE ANCHOR: $4"; failn=$((failn+1)); return; }
import sys; p,o,n=sys.argv[1:]; s=open(p).read(); assert o in s, "pattern missing: "+o; open(p,'w').write(s.replace(o,n,1))
PY
    out=$(timeout 900 python3 validate.py 2>&1)
    cp /tmp/neg_backup "$1"
    if grep -q "$4" <<<"$out"; then pass=$((pass+1)); echo "ok   : $4"; else failn=$((failn+1)); echo "MISS : $4"; fi
}
# The cinder boar holds its immunity three times since 88af2f0e (the
# constructor, readAdditionalSaveData, and isConverting() returning false), so
# taking out the constructor's call, as this case did, left a boar that never
# turns: no break, and the validator rightly said nothing. The cinderling holds
# it once, in its constructor, so taking that out is a piglin that zombifies
# off Cinderveil (17 Oct 2026).
t $S/entity/mob/Cinderling.java "setImmuneToZombification(true);" "" "would zombify outside the Nether"
t $G/cinder_boar.json '"head": "head",' '"head": "snout",' "rig moves snout"
t $S/registry/SpaceMobs.java "put(event, SpaceEntities.FROST_RAPTOR.get(), Monster.createMonsterAttributes(), MobRules.FROST_RAPTOR);" "" "frost_raptor gets no attributes"
t $S/world/MobRules.java '40.0D, 0.4D, 9.0D' '40.0D, 0.4D, 0.0D' "only hunters deal damage"
t $S/entity/mob/FrostRaptor.java '"attack".equals(state)' '"bite".equals(state)' "never gives the rig its attack"
t $S/entity/mob/Moonfolk.java 'spawnGolemIfNeeded' 'spawnGolemsIfNeeded' "must not summon iron golems"
# The tree the cases leave must pass its validator: a case is "caught" just as
# well off a break the tree had before the first one (16 Oct: run on a snapshot
# taken half way through negtest_p2.sh, whose iron.json was still broken, the
# first case passed that way and nothing looked again).
if timeout 900 python3 validate.py >/dev/null 2>&1; then echo "tree restored clean"
else echo "RESTORE FAILED: validate.py does not pass on the tree the cases left"; failn=$((failn+1)); fi
echo "$pass passed, $failn missed"
[ "$failn" -eq 0 ]
