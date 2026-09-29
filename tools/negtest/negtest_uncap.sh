#!/bin/bash
cd /tmp/Elysium-mono || exit 1
negtest_lock() { if command -v flock >/dev/null 2>&1; then flock -n "$1"; else mkdir "$2" 2>/dev/null && trap "rmdir '$2' 2>/dev/null" EXIT; fi; }  # Git Bash on Windows has no flock
exec 9>/tmp/negtest_uncap.lock; negtest_lock 9 /tmp/negtest_uncap.lockdir || exit 2
CB=elysium-lib/src/main/java/com/elysium/lib/event/ElysiumCombatHandler.java
TI=elysium-lib/src/main/java/com/elysium/lib/item/ElysiumRuneTiers.java
cp $CB /tmp/cb.bak; cp $TI /tmp/ti.bak; pass=0; miss=0
t() { python3 -c "$3" || { echo "SETUP FAILED $1"; cp /tmp/cb.bak $CB; cp /tmp/ti.bak $TI; return; }
  if timeout 1700 python3 elysium-lib/validate.py 2>&1 | grep -qF "$2"; then echo "CAUGHT : $1"; pass=$((pass+1)); else echo "MISSED : $1"; miss=$((miss+1)); fi
  cp /tmp/cb.bak $CB; cp /tmp/ti.bak $TI; }
t "dodge capped again" "rune dodge is capped again" "
p='$CB'; s=open(p).read(); a='nextFloat() < dodge)'; assert a in s; open(p,'w').write(s.replace(a,'nextFloat() < Math.min(0.6F, dodge))',1))"
t "heat capped again" "heat reduction is capped again" "
p='$CB'; s=open(p).read(); a='amount *= Math.max(0.0F, 1.0F - reduction);'; assert a in s; open(p,'w').write(s.replace(a,'amount *= Math.max(0.0F, 1.0F - Math.min(0.6F, reduction));',1))"
t "hits that heal" "a hit heals its victim" "
p='$CB'; s=open(p).read(); a='amount *= Math.max(0.0F, 1.0F - reduction);'; assert a in s; open(p,'w').write(s.replace(a,'amount *= (1.0F - reduction);',1))"
t "potion levels capped in the rule" "capping potion levels" "
p='$TI'; s=open(p).read(); a='return (int) Math.min(MAX_AMPLIFIER, level - 1L);'; assert a in s; open(p,'w').write(s.replace(a,'return (int) Math.min(2, level - 1L);',1))"
echo "---- lib: caught $pass, missed $miss"
