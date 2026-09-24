set -u
cd /tmp/Elysium-mono
BK=/tmp/g-backup; rm -rf $BK; mkdir -p $BK
pass=0; fail=0
check () {
  out=$(cd elysium-lib && python3 validate.py 2>&1)
  if echo "$out" | grep -q "$2"; then echo "  CAUGHT  $1"; pass=$((pass+1));
  else echo "  MISSED  $1 (wanted: $2)"; echo "$out" | tail -3 | sed 's/^/          /'; fail=$((fail+1)); fi
}
save(){ mkdir -p "$BK/$(dirname "$1")"; cp "$1" "$BK/$1"; }
restore(){ cp "$BK/$1" "$1"; }

L=elysium-lib/src/main/java/com/elysium/lib

echo "1. a call site goes back to the instanceof gate"
save $L/event/ElysiumCombatHandler.java
python3 - <<'PY'
import pathlib
p=pathlib.Path("elysium-lib/src/main/java/com/elysium/lib/event/ElysiumCombatHandler.java")
t=p.read_text()
t=t.replace("        ElysiumSocketable weapon = ElysiumGear.weapon(held);",
            "        ElysiumSocketable weapon = held.getItem() instanceof ElysiumSocketable g && !g.isArmour() ? g : null;",1)
p.write_text(t)
PY
check "gate restored at a call site" "asks .instanceof ElysiumSocketable. directly"
restore $L/event/ElysiumCombatHandler.java

echo "2. nothing clears the resolution cache"
save $L/event/ElysiumGearHandler.java
python3 - <<'PY'
import pathlib
p=pathlib.Path("elysium-lib/src/main/java/com/elysium/lib/event/ElysiumGearHandler.java")
t=p.read_text().replace("ElysiumGear.invalidate();","// ElysiumGear.invalidate();",1)
p.write_text(t)
PY
check "cache never cleared" "resolution cache"
restore $L/event/ElysiumGearHandler.java

echo "3. the classification rule loses a half"
save $L/item/ElysiumGear.java
python3 - <<'PY'
import pathlib
p=pathlib.Path("elysium-lib/src/main/java/com/elysium/lib/item/ElysiumGear.java")
t=p.read_text().replace("        if (humanoidArmourSlot && suppliesArmour) {","        if (humanoidArmourSlot) {",1)
p.write_text(t)
PY
check "creeper heads become armour" "carved pumpkin classified as ARMOUR"
restore $L/item/ElysiumGear.java

echo "4. a wieldable item is turned away"
save $L/item/ElysiumGear.java
python3 - <<'PY'
import pathlib
p=pathlib.Path("elysium-lib/src/main/java/com/elysium/lib/item/ElysiumGear.java")
t=p.read_text().replace("        if (tiered || suppliesAttackDamage) {","        if (tiered) {",1)
p.write_text(t)
PY
check "trident turned away" "wieldable item was turned away"
restore $L/item/ElysiumGear.java

echo "5. the harness is deleted"
save elysium-lib/tools/GearHarness.java
rm elysium-lib/tools/GearHarness.java
check "harness gone" "no longer executed anywhere"
restore elysium-lib/tools/GearHarness.java

echo
(cd elysium-lib && python3 validate.py >/dev/null 2>&1) && echo "  restored tree passes" || { echo "  RESTORE FAILED"; fail=$((fail+1)); }
echo; echo "$pass caught, $fail missed"
