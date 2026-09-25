#!/bin/bash
# Valoria: effects in elysium-lib. Each line breaks one thing the section
# promises and expects validate.py to say so.
cd /tmp/Elysium-mono/elysium-lib || exit 2
export JAVA_TOOL_OPTIONS=
S=src/main/java/com/elysium/lib
pass=0; failn=0
t() {
    cp "$1" /tmp/nb_ve
    python3 - "$1" "$2" "$3" <<'PY'
import sys; p,o,n=sys.argv[1:]; s=open(p,encoding="utf-8").read(); assert o in s, "pattern missing: "+o; open(p,'w',encoding="utf-8",newline="\n").write(s.replace(o,n,1))
PY
    out=$(timeout 1800 python3 validate.py 2>&1)
    cp /tmp/nb_ve "$1"
    if grep -q "$4" <<<"$out"; then pass=$((pass+1)); echo "ok   : $4"; else failn=$((failn+1)); echo "MISS : $4"; fi
}
t $S/nihil/ElysiumNullRules.java 'import java.util.Random;' 'import java.util.Random;' "executed without a game"
t $S/nihil/ElysiumNullRules.java 'public final class ElysiumNullRules {' 'public final class ElysiumNullRules {
    private static final net.minecraft.world.entity.LivingEntity NOPE = null;' "executed without a game"
t $S/nihil/ElysiumNullRules.java 'public static final float COLLAPSE = 0.95F;' 'public static final float COLLAPSE = 0.40F;' "FAIL"
t $S/nihil/ElysiumNullRules.java 'public static final double WARD_POINTS_FOR_ALL = 32.0D;' 'public static final double WARD_POINTS_FOR_ALL = 8.0D;' "FAIL"
t $S/nihil/ElysiumNull.java 'ElysiumElements.VOID, 0.0F)' 'ElysiumElements.VOID, 1.0F)' "hurts twice"
t $S/nihil/ElysiumNull.java 'ElysiumWards.bind(NULL, WARD);' 'ElysiumWards.bind(NULL, null);' "still accrues"
t $S/nihil/ElysiumNullHandler.java 'ElysiumNull.add(player, -ElysiumNullRules.decay(difficulty));' 'ElysiumNull.add(player, 0.0F);' "never decays"
t $S/nihil/ElysiumNullHandler.java '|| ElysiumProtection.inShelteredClaim(level, pos)) {' '|| false) {' "does not shelter"
t $S/nihil/ElysiumNullHandler.java 'ElysiumNull.add(player, ElysiumNullRules.wardLeak(incoming, reduced));' 'ElysiumNull.add(player, 0.0F);' "never leaks"
t $S/event/ElysiumStatusHandler.java 'amount *= ElysiumCombatRules.exhaustionScale(exhaustion.getAmplifier() + 1);' 'amount *= 0.5F;' "do not scale healing"
t $S/event/ElysiumStatusHandler.java 'ElysiumNull.add(player, landed);' 'ElysiumNull.add(player, 0.0F);' "never fills the target"
t $S/combat/ElysiumEffects.java 'EFFECTS.register("salve", Salve::new)' 'EFFECTS.register("salve2", Salve::new)' "Valoria's seven"
t $S/combat/ElysiumCombatRules.java 'EXHAUSTION_POINTS_FOR_ALL = 36.0D' 'EXHAUSTION_POINTS_FOR_ALL = 4.0D' "FAILURE"
t $S/attribute/ElysiumAttributeSpec.java 'new Renamed("ranged_crit_damage", "crit_damage")' 'new Renamed("ranged_crit_damage", "crit_damage_ranged")' "FAIL\|Exception"
t tools/gen_valoria_effects.py '"accuracy": ("Accuracy",' '"accuracy": ("Precision",' "not what tools/gen_valoria_effects.py generates"
F=src/main/resources/data/elysiumlib/enchantment/accuracy.json
cp $F /tmp/nb_ve_b; rm $F
out=$(timeout 1800 python3 validate.py 2>&1); cp /tmp/nb_ve_b $F
grep -q "Valoria's" <<<"$out" && { pass=$((pass+1)); echo "ok   : missing enchantment file"; } || { failn=$((failn+1)); echo "MISS : missing enchantment file"; }
echo "$pass passed, $failn missed"
