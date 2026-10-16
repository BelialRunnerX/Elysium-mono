#!/bin/bash
cd /tmp/Elysium-mono/elysium-lib || exit 1
S=src/main/java/com/elysium/lib
pass=0; miss=0
snap() { rm -rf /tmp/_pr; mkdir -p /tmp/_pr; cp -r $S/practice /tmp/_pr/practice; cp $S/event/ElysiumSocialHandler.java /tmp/_pr/; }
restore() { rm -rf $S/practice; cp -r /tmp/_pr/practice $S/practice; cp /tmp/_pr/ElysiumSocialHandler.java $S/event/; }
try() {
  local label="$1" want="$2" break_it="$3"
  snap
  bash -c "$break_it" || { echo "SETUP FAILED: $label"; miss=$((miss+1)); restore; return; }
  if timeout 1800 python3 validate.py 2>&1 | grep -qF "$want"; then
    echo "CAUGHT : $label"; pass=$((pass+1))
  else
    echo "MISSED : $label  (expected: $want)"; miss=$((miss+1))
  fi
  restore
}

try "practice keyed on the profession" "vanilla's villager level with a new name" \
  "sed -i 's|        ResourceLocation id = BuiltInRegistries.ITEM.getKey(result.getItem());|        ResourceLocation id = null;|' $S/practice/ElysiumPracticeLog.java"

try "the profession read at all" "two bakers who have sold different things" \
  "sed -i 's|    public static String keyFor(ItemStack result) {|    public static String professionOf(net.minecraft.world.entity.npc.Villager v) { return v.getVillagerData().getProfession().name(); }\n\n    public static String keyFor(ItemStack result) {|' $S/practice/ElysiumPracticeLog.java"

try "the discount added instead of set" "would compound each time the screen" \
  "sed -i 's|            offer.setSpecialPriceDiff(|            offer.addToSpecialPriceDiff(|' $S/practice/ElysiumPracticeLog.java"

try "offers never priced" "after the player has already paid" \
  "sed -i 's|        ElysiumPracticeLog.price(villager, villager.getOffers());||' $S/event/ElysiumSocialHandler.java"

try "trades never recorded" "no villager ever becomes" \
  "sed -i 's|            ElysiumPracticeLog.record(villager, event.getMerchantOffer().getResult());||' $S/event/ElysiumSocialHandler.java"

try "the floor removed" "infinite emerald machine" \
  "sed -i 's|FLOOR|LIMIT|g' $S/practice/ElysiumPractice.java"

try "the discount made flat again" "exactly backwards" \
  "sed -i 's|        return Math.max(FLOOR, (int) Math.ceil(baseCost \* (1.0F - discount)));|        return Math.max(FLOOR, baseCost - Math.round(discount * 12));|' $S/practice/ElysiumPractice.java"

try "a registry dragged into the ladder" "takes the whole system out of the tests" \
  "sed -i 's|^package com.elysium.lib.practice;|package com.elysium.lib.practice;\n\nimport net.minecraft.world.item.ItemStack;|' $S/practice/ElysiumPractice.java"

try "the top band made ruinous" "they are getting the shop" \
  "sed -i 's|    MASTER_OF_IT(\"master_of_it\", 96, 0.25F);|    MASTER_OF_IT(\"master_of_it\", 96, 0.90F);|' $S/practice/ElysiumPractice.java"

try "every villager starting practised" "practice buys nothing" \
  "sed -i 's|    UNFAMILIAR(\"unfamiliar\", 0, 0.00F),|    UNFAMILIAR(\"unfamiliar\", 0, 0.10F),|' $S/practice/ElysiumPractice.java"

try "two bands made identical" "two bands that do the same thing" \
  "sed -i 's|    SEASONED(\"seasoned\", 32, 0.16F),|    SEASONED(\"seasoned\", 32, 0.08F),|' $S/practice/ElysiumPractice.java"

try "the counting ceiling misaligned" "one number written twice" \
  "sed -i 's|    public static final int CEILING = 97;|    public static final int CEILING = 200;|' $S/practice/ElysiumPractice.java"

echo "---- caught $pass, missed $miss"
[ "$miss" -eq 0 ]
