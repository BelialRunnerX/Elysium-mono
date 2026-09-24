#!/usr/bin/env bash
# P6 — the crafting loops, broken one rule at a time.
#
# Loot becomes materials and materials become better gear. Every check that
# keeps that loop honest — that salvage costs something, that a reroll cannot
# be pulled for free, that a bench can be built and broken — is defeated
# deliberately here.
set -uo pipefail
ROOT=/tmp/Elysium-mono
ARM=$ROOT/elysium-armory
SRC=$ARM/src/main/java/com/elysium/armory
RES=$ARM/src/main/resources
LANG=$RES/assets/elysiumarmory/lang/en_us.json
pass=0; fail=0

SNAP=/tmp/negtest_benches_snapshot.tgz
tar czf $SNAP -C $ROOT elysium-armory/src elysium-armory/tools

out() { (cd $ARM && python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }

edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@"; }

echo "what salvage costs"
cp $SRC/property/ArmorySalvageRules.java /tmp/sr.bak
edit $SRC/property/ArmorySalvageRules.java 'return Math.max(0.05D, Math.min(0.95D, adjusted));' 'return adjusted;'
expect "salvage that can be certain, or impossible" "certain or impossible"
cp /tmp/sr.bak $SRC/property/ArmorySalvageRules.java

cp $SRC/property/ArmorySalvageRules.java /tmp/sr.bak
edit $SRC/property/ArmorySalvageRules.java 'double base = BASE_CHANCE - (BASE_CHANCE - WORN_FLOOR) * wear;' \
                                           'double base = BASE_CHANCE;'
expect "a worn piece salvaging as well as a new one" "did not salvage at the floor"
cp /tmp/sr.bak $SRC/property/ArmorySalvageRules.java

cp $SRC/property/ArmorySalvageRules.java /tmp/sr.bak
edit $SRC/property/ArmorySalvageRules.java 'public static int rerollCost(int targetRarity) {
        return Math.max(1, targetRarity);' 'public static int rerollCost(int targetRarity) {
        return Math.max(0, targetRarity);'
expect "a reroll that can be pulled for nothing" "a player could roll forever"
cp /tmp/sr.bak $SRC/property/ArmorySalvageRules.java

cp $SRC/property/ArmorySalvageRules.java /tmp/sr.bak
edit $SRC/property/ArmorySalvageRules.java 'public static final int CHOICES = 3;' 'public static final int CHOICES = 1;'
expect "a reforge that is a coin flip rather than a decision" "coin flip"
cp /tmp/sr.bak $SRC/property/ArmorySalvageRules.java

cp $SRC/property/ArmorySalvageRules.java /tmp/sr.bak
edit $SRC/property/ArmorySalvageRules.java 'package com.elysium.armory.property;' \
                                           'package com.elysium.armory.property;

import com.google.gson.Gson;'
expect "the salvage rules reaching outside the JDK" "nothing but the JDK"
cp /tmp/sr.bak $SRC/property/ArmorySalvageRules.java

echo "the benches themselves"
cp $SRC/craft/ArmorySalvage.java /tmp/sv.bak
edit $SRC/craft/ArmorySalvage.java 'double chance = ArmorySalvageRules.chance(wear, efficiency);' \
                                   'double chance = 1.0D;'
expect "salvage that always returns everything" "crafting would be an operation you could undo"
cp /tmp/sv.bak $SRC/craft/ArmorySalvage.java

cp $SRC/craft/ArmorySalvage.java /tmp/sv.bak
edit $SRC/craft/ArmorySalvage.java 'new ItemStack(ArmoryItems.AFFIX_ESSENCE.get(), here)' 'ItemStack.EMPTY'
expect "an affixed piece that salvages into nothing to pay with" "could never be paid for"
cp /tmp/sv.bak $SRC/craft/ArmorySalvage.java

cp $SRC/menu/ArmorySalvagerMenu.java /tmp/sm.bak
edit $SRC/menu/ArmorySalvagerMenu.java 'input.setItem(0, ItemStack.EMPTY);' ''
expect "a sword that salvages forever" "would salvage forever"
cp /tmp/sm.bak $SRC/menu/ArmorySalvagerMenu.java

cp $SRC/menu/ArmorySalvagerMenu.java /tmp/sm.bak
edit $SRC/menu/ArmorySalvagerMenu.java 'player.getInventory().placeItemBackInInventory(result);' ''
expect "results deleted when the bench is full" "that was the player's metal"
cp /tmp/sm.bak $SRC/menu/ArmorySalvagerMenu.java

cp $SRC/menu/ArmoryReforgingMenu.java /tmp/rm.bak
edit $SRC/menu/ArmoryReforgingMenu.java 'inputs.removeItem(1, cost);' ''
expect "a reroll nobody pays for" "does not spend the essence"
cp /tmp/rm.bak $SRC/menu/ArmoryReforgingMenu.java

cp $SRC/block/ArmoryMetalPressBlock.java /tmp/mp.bak
edit $SRC/block/ArmoryMetalPressBlock.java 'player.getCooldowns().addCooldown(held.getItem(), COOLDOWN);' ''
expect "a press with no cooldown at all" "presses in a single tick"
cp /tmp/mp.bak $SRC/block/ArmoryMetalPressBlock.java

echo "what a bench is made of"
cp $RES/data/elysiumarmory/recipe/salvager.json /tmp/rc.bak
rm $RES/data/elysiumarmory/recipe/salvager.json
expect "a bench nobody can build" "uncraftable"
cp /tmp/rc.bak $RES/data/elysiumarmory/recipe/salvager.json

cp $RES/data/elysiumarmory/loot_table/blocks/metal_press.json /tmp/lt.bak
rm $RES/data/elysiumarmory/loot_table/blocks/metal_press.json
expect "a bench nobody can take back" "unbreakable"
cp /tmp/lt.bak $RES/data/elysiumarmory/loot_table/blocks/metal_press.json

cp $RES/assets/elysiumarmory/blockstates/stone_anvil.json /tmp/bs.bak
rm $RES/assets/elysiumarmory/blockstates/stone_anvil.json
expect "a bench nobody can see" "invisible"
cp /tmp/bs.bak $RES/assets/elysiumarmory/blockstates/stone_anvil.json

cp $SRC/registry/ArmoryBlocks.java /tmp/ab.bak
edit $SRC/registry/ArmoryBlocks.java 'BLOCKS.register("reforging_table"' 'BLOCKS.register("reforge_bench"'
expect "a bench drawn and named and never registered" "never registered"
cp /tmp/ab.bak $SRC/registry/ArmoryBlocks.java

cp $RES/data/elysiumarmory/recipe/repair.json /tmp/rp.bak
edit $RES/data/elysiumarmory/recipe/repair.json '"elysiumarmory:repair"' '"elysiumarmory:mend"'
expect "a repair recipe naming a serializer nobody registered" "names another serializer"
cp /tmp/rp.bak $RES/data/elysiumarmory/recipe/repair.json

echo "the things a bench makes"
python3 - <<'PYX'
import json, os
p = '/tmp/Elysium-mono/elysium-armory/src/main/resources/data/elysiumarmory/recipe/affix_essence.json'
json.dump({"type": "minecraft:crafting_shapeless", "category": "misc",
           "ingredients": [{"item": "minecraft:amethyst_shard"}],
           "result": {"count": 1, "id": "elysiumarmory:affix_essence"}}, open(p, "w"), indent=2)
PYX
expect "a way around the bench that makes essence" "a way around the bench"
rm -f $RES/data/elysiumarmory/recipe/affix_essence.json

cp $LANG /tmp/lg.bak
edit $LANG '"elysiumarmory.reforge.cost"' '"elysiumarmory.reforge.price"'
expect "a bench speaking in raw keys" "speak in raw keys"
cp /tmp/lg.bak $LANG

cp $LANG /tmp/lg.bak
edit $LANG '"block.elysiumarmory.stone_anvil"' '"block.elysiumarmory.rock_anvil"'
expect "a bench with no name" "has no name"
cp /tmp/lg.bak $LANG

echo "enchanting and mending"
cp $SRC/item/ArmoryGearLogic.java /tmp/gl.bak
edit $SRC/item/ArmoryGearLogic.java 'public static int enchantmentValue(ItemStack stack) {' \
                                    'public static int enchantmentValueGone(ItemStack stack) {'
expect "gear that enchants like the class it had to extend" "rather than like what it is made of"
cp /tmp/gl.bak $SRC/item/ArmoryGearLogic.java

cp $SRC/item/ArmoryArmorItem.java /tmp/ai.bak
edit $SRC/item/ArmoryArmorItem.java '    @Override
    public boolean isEnchantable(ItemStack stack) {
        return !ArmoryGearLogic.isBroken(stack) && ArmoryGearLogic.enchantmentValue(stack) > 0;
    }' ''
expect "a table taking levels for a broken piece" "does not work"
cp /tmp/ai.bak $SRC/item/ArmoryArmorItem.java

echo
echo "restoring and re-checking"
tar xzf $SNAP -C $ROOT
if (cd $ARM && python3 validate.py >/dev/null 2>&1); then echo "  tree restored clean";
else echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1)); fi
echo "benches: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
