set -u
cd /tmp/Elysium-mono
BK=/tmp/m-backup; rm -rf $BK; mkdir -p $BK
pass=0; fail=0
check () {
  out=$(cd elysium-lib && python3 validate.py 2>&1)
  if echo "$out" | grep -q "$2"; then echo "  CAUGHT  $1"; pass=$((pass+1));
  else echo "  MISSED  $1 (wanted: $2)"; echo "$out" | tail -3 | sed 's/^/          /'; fail=$((fail+1)); fi
}
K=elysium-lib/src/main/java/com/elysium/lib/social/ElysiumMemoryKinds.java
M=elysium-lib/src/main/java/com/elysium/lib/social/ElysiumMemories.java
save(){ mkdir -p "$BK/$(dirname "$1")"; cp "$1" "$BK/$1"; }
restore(){ cp "$BK/$1" "$1"; }

echo "1. one incident is made harsh enough to close the shop"
save $K; sed -i 's/\.weight(-45)/.weight(-95)/' $K
check "single incident refuses" "single incident must never close the shop"
restore $K

echo "2. refusal is made unreachable"
save $M; sed -i 's/int REFUSAL = -60;/int REFUSAL = -400;/' $M
check "refusal unreachable" "ever makes a villager refuse"
restore $M

echo "3. one good turn undoes the worst thing you can do"
save $K; sed -i 's/\.weight(20)/.weight(50)/' $K
check "apology is free" "One trade would undo a killing"
restore $K

echo "4. harm is made to fade faster than help"
save $K; sed -i 's/\.halfLife(120 \* MINUTE)/.halfLife(10 * MINUTE)/' $K
check "forgiven faster than thanked" "fades at least as fast as help"
restore $K

echo "5. the ledger is made too shallow to reach refusal"
save $M; sed -i 's/int DEPTH = 16;/int DEPTH = 1;/' $M
check "ledger too shallow" "would fall off the end"
restore $M

echo
(cd elysium-lib && python3 validate.py >/dev/null 2>&1) && echo "  restored tree passes" || { echo "  RESTORE FAILED"; fail=$((fail+1)); }
echo; echo "$pass caught, $fail missed"
