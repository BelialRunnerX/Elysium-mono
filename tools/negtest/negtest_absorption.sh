#!/bin/bash
# The absorption check: a shield set straight is caught wherever it is.
cd /tmp/Elysium-mono || exit 1
pass=0; miss=0

try() {
  local label="$1" want="$2" file="$3" old="$4" new="$5"
  cp "$file" /tmp/_abs_backup.java
  python3 - "$file" "$old" "$new" <<'PY' || { echo "SETUP FAILED: $label"; cp /tmp/_abs_backup.java "$file"; return; }
import sys
p, old, new = sys.argv[1:4]
s = open(p, encoding="utf-8").read()
assert s.count(old) == 1, s.count(old)
open(p, "w", encoding="utf-8", newline="\n").write(s.replace(old, new))
PY
  if python3 check_absorption.py 2>&1 | grep -qF "$want"; then
    echo "CAUGHT : $label"; pass=$((pass+1))
  else
    echo "MISSED : $label  (expected: $want)"; miss=$((miss+1))
  fi
  cp /tmp/_abs_backup.java "$file"
}

try "the Praetor's field set straight" "ElysiumBosses.java" \
  elysium-mobs/src/main/java/com/elysium/mobs/boss/ElysiumBosses.java \
  "com.elysium.lib.combat.ElysiumAbsorption.set(this, getMaxHealth() * 0.25F);" "setAbsorptionAmount(getMaxHealth() * 0.25F);"

try "the barrier rune set straight" "ElysiumRunes.java" \
  elysium-core/src/main/java/com/elysium/core/item/ElysiumRunes.java \
  "com.elysium.lib.combat.ElysiumAbsorption.set(player, Math.min(cap, current + BARRIER_RECHARGE * scale));" "player.setAbsorptionAmount(Math.min(cap, current + BARRIER_RECHARGE * scale));"

echo "---- caught $pass, missed $miss"
