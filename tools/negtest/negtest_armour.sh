#!/bin/bash
cd /tmp/Elysium-mono || exit 1
exec 9>/tmp/negtest_armour.lock; flock -n 9 || exit 2
L=elysium-lib/src/main/java/com/elysium/lib; AR=$L/stats/ElysiumArmourRules.java; AH=$L/event/ElysiumArmourHandler.java; LM=$L/stats/ElysiumAttributeLimits.java; TM=elysium-lib/src/main/resources/META-INF/neoforge.mods.toml
FILES="$AR $AH $LM $TM"; snap() { rm -rf /tmp/_ar; mkdir -p /tmp/_ar; for f in $FILES; do cp $f /tmp/_ar/; done; }; restore() { for f in $FILES; do cp /tmp/_ar/$(basename $f) $f; done; }
pass=0; miss=0
t() { snap; python3 -c "$3" || { echo "SETUP FAILED $1"; restore; return; }
  if timeout 1700 python3 elysium-lib/validate.py 2>&1 | grep -qF "$2"; then echo "CAUGHT : $1"; pass=$((pass+1)); else echo "MISSED : $1"; miss=$((miss+1)); fi; restore; }
t "vanilla's 20 cap put back" "no better than vanilla" "
p='$AR'; s=open(p).read(); a='return Math.max(armour * MIN_ARMOUR_RATIO, against);'; assert a in s; open(p,'w').write(s.replace(a,'return Math.min(20.0D, Math.max(armour * MIN_ARMOUR_RATIO, against));',1))"
t "rules reach mobs" "they are for the player" "
p='$AH'; s=open(p).read(); a='if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {'; assert a in s
s=s.replace(a,'if (!(event.getEntity() instanceof net.minecraft.world.entity.LivingEntity player) || player.level().isClientSide()) {',1); open(p,'w').write(s)"
t "Breach ignored" "no longer pierce armour" "
p='$AH'; s=open(p).read(); a='EnchantmentHelper.modifyArmorEffectiveness(level, weapon, player, source, share)'; assert a in s; open(p,'w').write(s.replace(a,'share',1))"
t "ceiling lift unguarded" "not guarded" "
p='$LM'; s=open(p).read(); a='} catch (ReflectiveOperationException | RuntimeException failure) {'; assert a in s; open(p,'w').write(s.replace(a,'} catch (NoSuchFieldException failure) {',1))"
t "transformer not declared" "never applies it" "
p='$TM'; s=open(p).read(); a='file=\"META-INF/accesstransformer.cfg\"'; assert a in s; open(p,'w').write(s.replace(a,'file=\"META-INF/nothing.cfg\"',1))"
echo "---- armour: caught $pass, missed $miss"
