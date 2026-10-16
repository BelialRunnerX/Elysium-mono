#!/bin/bash
cd /tmp/Elysium-mono/elysium-lib || exit 2
export JAVA_TOOL_OPTIONS=
S=src/main/java/com/elysium/lib
pass=0; failn=0
t() {
    cp "$1" /tmp/nb_p1
    python3 - "$1" "$2" "$3" <<'PY' || { echo "STALE ANCHOR: $4"; failn=$((failn+1)); return; }
import sys; p,o,n=sys.argv[1:]; s=open(p).read(); assert o in s, "pattern missing: "+o; open(p,'w').write(s.replace(o,n,1))
PY
    out=$(timeout 1800 python3 validate.py 2>&1)
    cp /tmp/nb_p1 "$1"
    if grep -q "$4" <<<"$out"; then pass=$((pass+1)); echo "ok   : $4"; else failn=$((failn+1)); echo "MISS : $4"; fi
}
t $S/event/ElysiumArmourHandler.java 'pierce(attacker, "armor_pierce"), toughness' 'pierce(attacker, "prot_pierce"), toughness' "armor_shred then armor_pierce"
t $S/combat/ElysiumCombatRules.java 'import com.elysium.lib.stats.ElysiumShares;' 'import com.elysium.lib.stats.ElysiumShares;
import net.minecraft.world.entity.LivingEntity;' "executed without a game"
t $S/combat/ElysiumCombatRules.java 'NEGATIVE_ARMOUR_PER_POINT = 0.015D' 'NEGATIVE_ARMOUR_PER_POINT = 0.15D' "twenty points below zero"
t $S/event/ElysiumCombatHandler.java 'amount *= ElysiumCombatRules.critMultiplier(odds, critDamage,' 'amount *= 1.0F * (0 * critMultiplierPlaceholder(odds, critDamage,' "no longer go through the tested rule"
t $S/combat/ElysiumEffects.java 'EFFECTS.register("bleeding", Bleeding::new)' 'EFFECTS.register("bleeding2", Bleeding::new)' "are not Apothic Attributes' seven"
F=src/main/resources/data/elysiumlib/brewing_mixes/flying.json
cp $F /tmp/nb_p1b; rm $F
out=$(timeout 1800 python3 validate.py 2>&1); cp /tmp/nb_p1b $F
grep -q "mixes listed in code and the files on disk disagree" <<<"$out" && { pass=$((pass+1)); echo "ok   : missing mix file"; } || { failn=$((failn+1)); echo "MISS : missing mix file"; }
# The tree the cases leave must pass its validator: a case is "caught" just as
# well off a break the tree had before the first one (16 Oct: run on a snapshot
# taken half way through negtest_p2.sh, whose iron.json was still broken, the
# first case passed that way and nothing looked again).
if timeout 1800 python3 validate.py >/dev/null 2>&1; then echo "tree restored clean"
else echo "RESTORE FAILED: validate.py does not pass on the tree the cases left"; failn=$((failn+1)); fi
echo "$pass passed, $failn missed"
[ "$failn" -eq 0 ]
