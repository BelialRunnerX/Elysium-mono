#!/bin/bash
cd /tmp/Elysium-mono/elysium-lib || exit 1
S=src/main/java/com/elysium/lib
L=src/main/resources/assets/elysiumlib/lang/en_us.json
pass=0; miss=0
snap() { rm -rf /tmp/_co; mkdir -p /tmp/_co; cp -r $S/contract /tmp/_co/contract; cp $L /tmp/_co/lang.json; }
restore() { rm -rf $S/contract; cp -r /tmp/_co/contract $S/contract; cp /tmp/_co/lang.json $L; }
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

try "a second reader of the attachment" "any other reader is one on which" \
  "python3 -c \"
import io
p='$S/contract/ElysiumContracts.java'
s=io.open(p,encoding='utf-8').read()
s=s.replace('    public static boolean hasRoom(Player player) {','    public static int countRaw(Player player) { return player.getData(HELD.get()).size(); }\n\n    public static boolean hasRoom(Player player) {',1)
io.open(p,'w',encoding='utf-8').write(s)
\""

try "expiry removed from the read" "a deadline is a suggestion" \
  "sed -i 's|            if (contract.expired(now)) {|            if (false) {|' $S/contract/ElysiumContracts.java"

try "the penalty stops moving standing" "is not a contract" \
  "sed -i 's|        ElysiumStanding.addFavor(player, -cost, CAUSE_LAPSED);|        int unused = cost;|' $S/contract/ElysiumContracts.java"

try "failing made to raise Suspicion" "not the same as being seditious" \
  "sed -i 's|        ElysiumStanding.addFavor(player, -cost, CAUSE_LAPSED);|        ElysiumStanding.addSuspicion(player, cost, CAUSE_LAPSED);|' $S/contract/ElysiumContracts.java"

try "an office asking you to kill its own" "does not pay you to kill its people" \
  "sed -i 's|            target = (empire ? ElysiumFaction.UNSWORN : ElysiumFaction.EMPIRE).name();|            target = ElysiumFaction.UNSWORN.name();|' $S/contract/ElysiumContracts.java"

try "a delivery asking for mod materials" "one they cannot take" \
  "sed -i 's|\"minecraft:iron_ingot\", \"minecraft:copper_ingot\", \"minecraft:coal\",|\"elysium:neutronium_ingot\", \"minecraft:copper_ingot\", \"minecraft:coal\",|' $S/contract/ElysiumContracts.java"

try "progress uncapped" "reads as the office not knowing" \
  "sed -i 's|                Math.min(quantity, done + more), issued, deadline, pay,|                done + more, issued, deadline, pay,|' $S/contract/ElysiumContract.java"

try "a countdown field added" "read when somebody asks" \
  "sed -i 's|    public int penalty() {|    public int countdown() { return 0; }\n\n    public int penalty() {|' $S/contract/ElysiumContract.java"

try "a kind with no words" "raw key on the one line describing" \
  "python3 -c \"
import json,collections
p='$L'
d=json.load(open(p,encoding='utf-8'),object_pairs_hook=collections.OrderedDict)
d.pop('elysium.contract.kind.cull.line')
json.dump(d,open(p,'w',encoding='utf-8'),indent=2,ensure_ascii=False)
\""

try "a registry dragged into the terms" "takes the whole system out of the tests" \
  "sed -i 's|^package com.elysium.lib.contract;|package com.elysium.lib.contract;\n\nimport net.minecraft.world.item.ItemStack;|' $S/contract/ElysiumTerms.java"

try "failing made to cost as much as succeeding" "the rational move is to take none" \
  "sed -i 's|        return Math.max(1, (standingFor(kind, quantity) + 3) / 4);|        return Math.max(1, standingFor(kind, quantity));|' $S/contract/ElysiumTerms.java"

try "a contract that pays nothing" "learns to walk past" \
  "sed -i 's|    public static final float PAY_PER_UNIT = 0.75F;|    public static final float PAY_PER_UNIT = 0.0F;|' $S/contract/ElysiumTerms.java"

try "a deadline nobody can meet" "not a deadline anybody meets" \
  "sed -i 's|    public static final int TICKS_PER_UNIT = 20 \* 60;|    public static final int TICKS_PER_UNIT = 20 * 5;|; s|    public static final int MIN_DEADLINE = 20 \* 60 \* 10;|    public static final int MIN_DEADLINE = 20 * 30;|' $S/contract/ElysiumTerms.java"

try "the kinds put out of order" "not in the order of how hard they are" \
  "sed -i 's|    DELIVER(\"deliver\", 1.0F)|    DELIVER(\"deliver\", 9.0F)|' $S/contract/ElysiumContractKind.java"

try "a player allowed to hoard contracts" "chore list rather than a" \
  "sed -i 's|    public static final int MAX_HELD = 3;|    public static final int MAX_HELD = 20;|' $S/contract/ElysiumTerms.java"

echo "---- caught $pass, missed $miss"
