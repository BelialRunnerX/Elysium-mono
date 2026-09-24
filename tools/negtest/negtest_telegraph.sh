#!/bin/bash
cd /tmp/Elysium-mono/elysium-lib || exit 1
S=src/main/java/com/elysium/lib
M=../elysium-mobs/src/main/java/com/elysium/mobs
pass=0; miss=0
snap() { rm -rf /tmp/_tg; mkdir -p /tmp/_tg; cp -r $S/telegraph /tmp/_tg/telegraph; cp $S/event/ElysiumCombatHandler.java /tmp/_tg/; cp $M/entity/ElysiumFamilies.java /tmp/_tg/; }
restore() { rm -rf $S/telegraph; cp -r /tmp/_tg/telegraph $S/telegraph; cp /tmp/_tg/ElysiumCombatHandler.java $S/event/; cp /tmp/_tg/ElysiumFamilies.java $M/entity/; }
try() {
  local label="$1" want="$2" break_it="$3"
  snap
  bash -c "$break_it" || { echo "SETUP FAILED: $label"; restore; return; }
  if timeout 1800 python3 validate.py 2>&1 | grep -qF "$want"; then
    echo "CAUGHT : $label"; pass=$((pass+1))
  else
    echo "MISSED : $label  (expected: $want)"; miss=$((miss+1))
  fi
  restore
}

try "a Minecraft import in the rule" "only stays walkable while this file" \
  "sed -i 's|^package com.elysium.lib.telegraph;|package com.elysium.lib.telegraph;\n\nimport net.minecraft.world.level.Level;|' $S/telegraph/ElysiumTelegraph.java"

try "the cap removed from the pipeline" "holds only for mobs that choose" \
  "sed -i 's|float permitted = ElysiumTelegraph.allowed(amount, announced);|float permitted = amount;|' $S/event/ElysiumCombatHandler.java"

try "the warning never spent" "licences every blow that follows" \
  "sed -i 's|            ElysiumWarnings.spend(swinging);||' $S/event/ElysiumCombatHandler.java"

try "a bare melee goal in the bestiary" "still construct(s) vanilla's" \
  "sed -i 's|new ElysiumTelegraphedAttack(mob, chaseSpeed, false)|new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(mob, chaseSpeed, false)|' $M/entity/ElysiumFamilies.java"

try "the warning made silent" "the sound and the particle are the readability" \
  "python3 -c \"
import io,re
p='$S/telegraph/ElysiumWarnings.java'
s=io.open(p,encoding='utf-8').read()
s=re.sub(r'\\n        attacker\\.level\\(\\)\\.playSound\\(.*?\\);\\n', '\\n', s, count=1, flags=re.S)
io.open(p,'w',encoding='utf-8').write(s)
\""

try "warnings made persistent" "warnings have become persistent" \
  "sed -i 's|^import net.minecraft.world.entity.LivingEntity;|import net.minecraft.world.entity.LivingEntity;\nimport net.neoforged.neoforge.attachment.AttachmentType;|' $S/telegraph/ElysiumWarnings.java"

try "warnings unbounded" "warning map is unbounded" \
  "sed -i 's|CAPACITY|ROOM|g' $S/telegraph/ElysiumWarnings.java"

try "the wind-up sized off a constant" "keeps a warning sized for what it used to" \
  "sed -i 's|float damage = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);|float damage = 9.0F;|' $S/telegraph/ElysiumTelegraphedAttack.java"

try "the warning raised every tick" "turns it into a continuous noise" \
  "sed -i 's|if (windup > 0 \&\& getTicksUntilNextAttack() == windup|if (windup > 0|' $S/telegraph/ElysiumTelegraphedAttack.java"

try "the ceiling made unreachably long" "easier to fight than a small one" \
  "sed -i 's|    public static final int MAXIMUM_WINDUP = 25;|    public static final int MAXIMUM_WINDUP = 80;|' $S/telegraph/ElysiumTelegraph.java"

try "the floor made unusable" "not a window anybody can use" \
  "sed -i 's|    public static final int MINIMUM_WINDUP = 10;|    public static final int MINIMUM_WINDUP = 3;|' $S/telegraph/ElysiumTelegraph.java"

try "an unannounced blow cancelled outright" "reduced to nothing" \
  "sed -i 's|        return announcedFor >= windupFor(damage) ? damage : DANGEROUS;|        return announcedFor >= windupFor(damage) ? damage : 0.0F;|' $S/telegraph/ElysiumTelegraph.java"

try "the wind-up overflow put back" "does not earn the longest warning" \
  "python3 -c \"
import io
p='$S/telegraph/ElysiumTelegraph.java'
s=io.open(p,encoding='utf-8').read()
s=s.replace('''        float earned = MINIMUM_WINDUP + (damage - DANGEROUS) * WINDUP_PER_POINT;
        if (!(earned < MAXIMUM_WINDUP)) {
            return MAXIMUM_WINDUP;
        }
        return Math.max(MINIMUM_WINDUP, Math.round(earned));''','''        int earned = MINIMUM_WINDUP + Math.round((damage - DANGEROUS) * WINDUP_PER_POINT);
        return Math.min(MAXIMUM_WINDUP, earned);''',1)
io.open(p,'w',encoding='utf-8').write(s)
\""

echo "---- caught $pass, missed $miss"
