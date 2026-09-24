#!/usr/bin/env bash
# P12.3 — the potion charm, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-trinkets
J=$M/src/main/java/com/elysium/trinkets
RES=$M/src/main/resources
LANG=$RES/assets/elysiumtrinkets/lang/en_us.json
GEN=$M/tools/gen_trinkets.py
SNAP=/tmp/negtest_charm_snapshot.tgz
pass=0; fail=0

tar czf $SNAP -C /tmp Elysium-mono/elysium-trinkets Elysium-mono/art

out() { (cd $ROOT && python3 elysium-trinkets/validate.py 2>&1); }
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
restore() { rm -rf $M $ROOT/art; tar xzf $SNAP -C /tmp; }

CI=$J/item/PotionCharmItem.java
CH=$J/event/PotionCharmHandler.java
CR=$J/recipe/PotionCharmRecipe.java
CC=$J/client/TrinketsClient.java
CS=$J/CuriosSlots.java

echo "what may be in one"
edit $CI 'return !holder.value().isInstantenous() && holder.value().isBeneficial();' 'return true;'
expect "a charm that will hold anything" "which is not a thing a worn item can be"
restore

edit $CI 'super(properties.stacksTo(1).durability(512));' 'super(properties.stacksTo(1));'
expect "a charm that never wears out" "one potion is every potion forever"
restore

echo "wearing one"
edit $CH 'if (!PotionCharmItem.working(stack)) {
                continue;
            }' ''
expect "a spent charm that keeps working" "does not ask whether a charm is spent"
restore

edit $CH 'stack.setDamageValue(Math.min(stack.getMaxDamage(),
                        stack.getDamageValue() + 1));' ''
expect "a charm that costs nothing to wear" "costs nothing"
restore

edit $CH 'stack.setDamageValue(Math.min(stack.getMaxDamage(),
                        stack.getDamageValue() + 1));' \
         'stack.shrink(1);'
expect "a spent charm that is destroyed" "investment that vanishes"
restore

edit $CH 'player.tickCount % PotionCharmItem.INTERVAL != 0' 'false'
expect "an inventory scan every tick" "looked at every tick"
restore

edit $CH 'if (!PotionCharmItem.allowed(effect)) {
                continue;
            }' ''
expect "a charm applying what a charm may not hold" "rather than what a charm is allowed"
restore

edit $CH 'MobEffectInstance had = player.getEffect(effect.getEffect());
            if (had != null && (had.getAmplifier() > effect.getAmplifier()
                    || had.getDuration() > PotionCharmItem.REFRESH_AT)) {
                continue;
            }' ''
expect "a charm that re-applies what the wearer already has" "for doing nothing"
restore

echo "filling one"
edit $CR 'if (!PotionCharmItem.allowed(contents)) {' 'if (false) {'
expect "a bench that fills a charm with the unusable" "finds out by wearing an inert one"
restore

edit $CR 'left.set(slot, new ItemStack(Items.GLASS_BOTTLE));' ''
expect "a recipe that eats the bottle" "no vanilla recipe that drinks one"
restore

edit $CR 'if (!already.equals(PotionContents.EMPTY) && !already.equals(contents)) {' 'if (false) {'
expect "a free swap of what is in a charm" "throws away what was in it"
restore

edit $CR 'made.setDamageValue(0);' ''
expect "a refill that refills nothing" "a spent charm stays spent"
restore

echo "wearing it at all"
edit $CS 'CuriosApi.registerCurio(ElysiumTrinkets.POTION_CHARM.get(),' 'registerNothing('
expect "a charm Curios has never heard of" "cannot be put in a slot"
restore

edit $CS 'public static List<ItemStack> wornCharms(Player player) {' \
         'public static List<ItemStack> notAsking(Player player) {'
expect "nothing asking where the charm is worn" "where the charm is worn"
restore

echo "seeing one"
edit $GEN '"textures": {"layer0": f"{MOD}:item/potion_charm",
                           "layer1": f"{MOD}:item/potion_charm_fill"}}' \
          '"textures": {"layer0": f"{MOD}:item/potion_charm"}}'
rm -f $RES/assets/elysiumtrinkets/models/item/potion_charm.json
(cd $ROOT && python3 elysium-trinkets/tools/gen_trinkets.py >/dev/null 2>&1)
expect "a charm with nowhere to put the potion's colour" "needs a layer to paint"
restore

edit $CC 'if (layer != TINTED_LAYER) {' 'if (layer != 7) {'
edit $CC 'return contents.equals(PotionContents.EMPTY) ? -1
                    : 0xFF000000 | contents.getColor();' 'return -1;'
expect "every charm in a hotbar looking alike" "looks like every other"
restore

rm -f $RES/assets/elysiumtrinkets/textures/item/potion_charm_fill.png
expect "a charm missing the field its colour goes on" "missing assets"
restore

edit $RES/data/curios/tags/item/charm.json '"elysiumtrinkets:potion_charm",' ''
expect "a charm that cannot be worn" "cannot be worn"
restore

edit $LANG '"item.elysiumtrinkets.potion_charm.of"' '"item.elysiumtrinkets.potion_charm.named"'
expect "a charm with no name for what is in it" "nothing translates it"
restore

echo "and the module's own rules"
edit $CI 'public class PotionCharmItem extends Item {' \
         'public class PotionCharmItem extends Item implements top.theillusivec4.curios.api.type.capability.ICurioItem {'
expect "Curios leaking out of the one file that knows it" "imports Curios directly"
restore

edit $RES/data/elysiumtrinkets/recipe/potion_charm.json '"id": "elysiumtrinkets:potion_charm"' \
                                                        '"id": "elysiumtrinkets:potion_charmed"'
expect "a recipe making something nobody registers" "neither a registered trinket nor"
restore

edit $RES/data/elysiumtrinkets/recipe/potion_charm_filling.json '"type": "elysiumtrinkets:potion_charm"' \
                                                                '"type": "elysiumtrinkets:charm_filling"'
expect "a recipe naming a serializer nobody registered" "neither a registered trinket nor"
restore

echo
echo "restoring and re-checking"
restore
if (cd $ROOT && python3 elysium-trinkets/validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
rm -f $SNAP
echo "potion charm: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
