#!/usr/bin/env bash
# Jewellery you can wear, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
BAK=/tmp/jewel_bak
pass=0; fail=0
rm -rf $BAK && mkdir -p $BAK
# The FIRST state of a file, never a later one: a test that edits the same
# file twice would otherwise back up the half-edited version and "restore" to
# it, leaving the tree quietly changed and every later test lying.
keep() { local rel="${1#$ROOT/}"; mkdir -p "$BAK/$(dirname "$rel")"; [ -f "$BAK/$rel" ] || cp "$1" "$BAK/$rel" 2>/dev/null; }
restore() { (cd $BAK && find . -type f 2>/dev/null | while read -r f; do cp "$f" "$ROOT/${f#./}"; done); rm -rf $BAK; mkdir -p $BAK; }
out() { (cd $ROOT/$1 && python3 validate.py 2>&1); }
expect() { local mod="$1" what="$2" want="$3" got
  got="$(out "$mod")"
  if [[ "$got" == *"$want"* ]]; then echo "  caught : $what"; pass=$((pass+1));
  else echo "  MISSED : $what"; fail=$((fail+1)); fi; restore; }
edit() { keep "$1"; python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, 'anchor not found in ' + p
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

L=$ROOT/elysium-lib/src/main/java/com/elysium/lib
A=$ROOT/elysium-armory/src/main/java/com/elysium/armory
AR=$ROOT/elysium-armory/src/main/resources
T=$ROOT/elysium-trinkets/src/main/java/com/elysium/trinkets

echo "the seam"
keep $L/trinket/ElysiumWearables.java; mv $L/trinket/ElysiumWearables.java /tmp/ew.bak
expect elysium-lib "no register of what wants wearing" "no register of what wants wearing"
mv /tmp/ew.bak $L/trinket/ElysiumWearables.java

edit $L/trinket/ElysiumTrinkets.java 'public interface StackProvider {' 'public interface StackProviderX {'
expect elysium-lib "a seam that only knows this mod's own trinkets" "not what stacks are worn"

edit $L/effect/ElysiumEffectEvents.java 'for (ItemStack stack : accessories(entity)) {' 'for (ItemStack stack : java.util.List.<ItemStack>of()) {'
expect elysium-lib "effects that reach armour and not accessories" "fewer places than it looks"

edit $L/effect/ElysiumEffectEvents.java 'return com.elysium.lib.trinket.ElysiumTrinkets.wornStacks(player);' 'return java.util.List.of();'
expect elysium-lib "an engine that reaches past the seam" "some other way than the seam"

echo "the armoury's half"
edit $A/item/ArmoryGearLogic.java 'case "ring", "bracelet", "necklace" -> gearType;' 'case "ring" -> gearType;'
expect elysium-armory "a bracelet built as a sword" "grants attack damage from a finger"

edit $A/item/ArmoryGearLogic.java 'AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.ANY);' 'AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);'
expect elysium-armory "jewellery written for a vanilla slot group" "a worn ring grants nothing"

edit $A/registry/ArmoryItems.java 'com.elysium.lib.trinket.ElysiumWearables.register(item.get(), entry.getValue());' ''
expect elysium-armory "jewellery nobody declares wearable" "can be made and never worn"

edit $A/ElysiumArmory.java 'modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event)
                -> event.enqueueWork(
                        com.elysium.armory.registry.ArmoryItems::declareWearables));' 'com.elysium.armory.registry.ArmoryItems.declareWearables();'
expect elysium-armory "wearables declared during construction" "which is the crash this repository has had twice"

keep $AR/data/curios/tags/item/necklace.json; rm $AR/data/curios/tags/item/necklace.json
expect elysium-armory "a slot that refuses the item" "no curios tag for necklace"

python3 - <<'PYEOF'
import json
p = "/tmp/Elysium-mono/elysium-armory/src/main/resources/data/curios/tags/item/ring.json"
import shutil, os
os.makedirs("/tmp/jewel_bak/elysium-armory/src/main/resources/data/curios/tags/item", exist_ok=True)
shutil.copy(p, "/tmp/jewel_bak/elysium-armory/src/main/resources/data/curios/tags/item/ring.json")
body = {"replace": False, "values": ["elysiumarmory:ring"]}
open(p, "w").write(json.dumps(body, indent=2) + "\n")
PYEOF
expect elysium-armory "a tag that demands the item exists" "logs an error every launch"

edit $A/item/ArmoryGearLogic.java 'private static void jewel(ItemAttributeModifiers.Builder builder,' 'private static void notJewel(ItemAttributeModifiers.Builder builder,'
expect elysium-armory "jewellery with no branch of its own" "no attribute branch of its own"

edit $A/item/ArmoryGearItem.java 'package com.elysium.armory.item;' 'package com.elysium.armory.item;
import top.theillusivec4.curios.api.CuriosApi;'
expect elysium-armory "the armoury learning what a curio is" "not allowed to know which mod holds the slot"

echo "the bridge"
edit $T/CuriosSlots.java 'com.elysium.lib.trinket.ElysiumTrinkets.setStackProvider(CuriosSlots::wornStacks);' ''
expect elysium-trinkets "an engine never told what is worn" "never told what is in an accessory slot"

edit $T/CuriosSlots.java 'for (com.elysium.lib.trinket.ElysiumWearables.Wearable wearable
                : com.elysium.lib.trinket.ElysiumWearables.all()) {' 'for (com.elysium.lib.trinket.ElysiumWearables.Wearable wearable
                : java.util.List.<com.elysium.lib.trinket.ElysiumWearables.Wearable>of()) {'
expect elysium-trinkets "a bridge with its own idea of what is wearable" "keeps its own list"

edit $T/CuriosSlots.java 'if (entry.slot() == net.minecraft.world.entity.EquipmentSlotGroup.ANY) {' 'if (true) {'
expect elysium-trinkets "a sword handing over its damage from a charm slot" "hands over its attack damage"

edit $T/CuriosSlots.java 'STACKS.remove(player);' ''
expect elysium-trinkets "a cache that grows forever" "grows a map forever"

edit $T/ElysiumTrinkets.java 'CuriosSlots.registerWearables();' ''
expect elysium-trinkets "wearables registered by nobody" "registered by a method nobody calls"

echo "showing the working"
keep $A/command/ArmoryCommand.java; mv $A/command/ArmoryCommand.java /tmp/ac.bak
expect elysium-armory "no way to ask a piece what it is made of" "only account of why a sword hits"
mv /tmp/ac.bak $A/command/ArmoryCommand.java

edit $A/command/ArmoryCommand.java 'ArmoryGearParts parts = ArmoryGearLogic.partsOf(held);' 'ArmoryGearParts parts = ArmoryGearParts.EMPTY;'
expect elysium-armory "a printout with no parts in it" "does not use partsOf("

edit $A/command/ArmoryCommand.java 'for (Map.Entry<String, Double> value : sorted(stats.values())) {' 'for (Map.Entry<String, Double> value : stats.values().entrySet()) {'
expect elysium-armory "properties in whatever order the map had" "cannot be compared"

edit $A/command/ArmoryCommand.java 'public static final int LINES = 24;' 'public static final int LINES = 24;
    public static boolean statLock;'
expect elysium-armory "a stat lock creeping back in" "second source of truth"

python3 - <<'PYEOF'
import json, os, shutil
p = "/tmp/Elysium-mono/elysium-armory/src/main/resources/assets/elysiumarmory/lang/en_us.json"
os.makedirs("/tmp/jewel_bak/elysium-armory/src/main/resources/assets/elysiumarmory/lang", exist_ok=True)
shutil.copy(p, "/tmp/jewel_bak/elysium-armory/src/main/resources/assets/elysiumarmory/lang/en_us.json")
d = json.load(open(p, encoding="utf-8"))
d.pop("elysiumarmory.properties.part")
open(p, "w", encoding="utf-8").write(json.dumps(d, indent=2) + "\n")
PYEOF
expect elysium-armory "a printout that reads as a raw key" "prints a raw key"

echo
echo "restoring and re-checking"
restore
clean=1
for mod in elysium-lib elysium-armory elysium-trinkets; do
  (cd $ROOT/$mod && python3 validate.py >/dev/null 2>&1) || clean=0
done
if [ "$clean" -eq 1 ]; then echo "  trees restored clean"; else echo "  RESTORE FAILED"; fail=$((fail+1)); fi
echo "jewellery: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
