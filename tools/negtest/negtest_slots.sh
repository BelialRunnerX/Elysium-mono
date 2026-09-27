#!/bin/bash
# The cross-cutting slot check: a machine that asks its own refusing output
# slot for room through insertItem is caught wherever it is.
cd /tmp/Elysium-mono || exit 1
pass=0; miss=0

try() {
  local label="$1" want="$2" file="$3" old="$4" new="$5"
  cp "$file" /tmp/_slots_backup.java
  python3 - "$file" "$old" "$new" <<'PY' || { echo "SETUP FAILED: $label"; cp /tmp/_slots_backup.java "$file"; return; }
import sys
p, old, new = sys.argv[1:4]
s = open(p, encoding="utf-8").read()
assert s.count(old) == 1, s.count(old)
open(p, "w", encoding="utf-8", newline="\n").write(s.replace(old, new))
PY
  if python3 check_slots.py 2>&1 | grep -qF "$want"; then
    echo "CAUGHT : $label"; pass=$((pass+1))
  else
    echo "MISSED : $label  (expected: $want)"; miss=$((miss+1))
  fi
  cp /tmp/_slots_backup.java "$file"
}

try "an industry plant asking its output slot through insertItem" "PlantBlock.java" \
  elysium-industry/src/main/java/com/elysium/industry/block/PlantBlock.java \
  "if (!ElysiumSlots.roomFor(items, OUT, result)) {" "if (!items.insertItem(OUT, result, true).isEmpty()) {"

try "a centrifuge asking its numbered output slots" "CentrifugeBlock.java" \
  elysium-agronomy/src/main/java/com/elysium/agronomy/block/CentrifugeBlock.java \
  "if (!ElysiumSlots.roomFor(items, FIRST_OUT + at, coming)) {" "if (!items.insertItem(FIRST_OUT + at, coming, true).isEmpty()) {"

try "a refinery asking about the recovered companion" "RefineryBlock.java" \
  elysium-foundry/src/main/java/com/elysium/foundry/block/RefineryBlock.java \
  "|| !ElysiumSlots.roomFor(items, RECOVERED, recovered)) {" "|| !items.insertItem(RECOVERED, recovered, true).isEmpty()) {"

try "a new machine with a slot called PRODUCT" "output slot PRODUCT" \
  elysium-kinetics/src/main/java/com/elysium/kinetics/block/MachineBlock.java \
  "if (!ElysiumSlots.roomFor(items, OUT, result)) {" "if (!items.insertItem(PRODUCT, result, true).isEmpty()) {"

try "a centrifuge filling its output through a real insert" "CentrifugeBlock.java"   elysium-agronomy/src/main/java/com/elysium/agronomy/block/CentrifugeBlock.java   "items.setStackInSlot(slot, ElysiumSlots.grown(items.getStackInSlot(slot), coming));" "items.insertItem(FIRST_OUT + slot, coming, false);"

echo "---- caught $pass, missed $miss"
