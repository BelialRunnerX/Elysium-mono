#!/usr/bin/env bash
# P12.1 / P12.13 — the attribute tooltip and the detail key.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-lib
L=$M/src/main/java/com/elysium/lib
LANG=$M/src/main/resources/assets/elysiumlib/lang/en_us.json
SNAP=/tmp/negtest_tooltip_snapshot.tgz
pass=0; fail=0
tar czf $SNAP -C /tmp Elysium-mono/elysium-lib
out() { (cd $ROOT && python3 elysium-lib/validate.py 2>&1); }
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
restore() { rm -rf $M; tar xzf $SNAP -C /tmp; }

T=$L/client/ElysiumAttributeTooltips.java
K=$L/client/ElysiumKeys.java

echo "the attribute tooltip"
mv $T /tmp/t.away
expect "four blue lines and none of them the number" "none of the numbers is the number"
restore
rm -f /tmp/t.away

edit $T 'value = Dist.CLIENT)' 'value = Dist.DEDICATED_SERVER)'
expect "a server loading a class that draws" "not client-only"
restore

edit $T 'int at = removeVanillaLines(lines);' 'int at = lines.size();'
expect "every tooltip saying everything twice" "says everything twice"
restore

edit $T 'stack.getOrDefault(
                DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);' \
        'stack.getOrDefault(
                DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiersWithNoTooltip());'
expect "the item changed rather than the tooltip" "travels with the item"
restore

edit $T 'if (line.getContents() instanceof TranslatableContents contents) {' 'if (false) {'
edit $T 'if (part.getContents() instanceof TranslatableContents contents
                    && contents.getKey().startsWith(MODIFIER_PREFIX)) {' 'if (false) {'
expect "vanilla's lines found by position" "another mod's line"
restore

edit $T 'if (ElysiumKeys.detailWanted()) {
            // Out of the way: vanilla'"'"'s own lines are what a player comparing
            // two affixes wants, and they are already there.
            return;
        }' ''
expect "no way back to the individual modifiers" "comparing two affixes actually wants"
restore

edit $T 'double percent = sums[AttributeModifier.Operation.ADD_MULTIPLIED_BASE.ordinal()]
                + sums[AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL.ordinal()];' \
        'double percent = 0.0D;'
expect "a percentage modifier that disappears" "silently disappears"
restore

echo "the detail key"
mv $K /tmp/k.away
expect "no detail key at all" "no detail key"
restore
rm -f /tmp/k.away

edit $K 'event.register(DETAIL);' ''
expect "a key nobody can rebind" "cannot be rebound"
restore

edit $K 'return InputConstants.isKeyDown(client.getWindow().getWindow(),
                DETAIL.getKey().getValue());' 'return DETAIL.consumeClick();'
expect "a key press the rest of the game never sees" "never sees"
restore

python3 -c "
import json
p='$LANG'
d=json.load(open(p)); d.pop('key.elysiumlib.detail')
json.dump(d, open(p,'w'), indent=2)"
expect "a control with no name" "nothing translates it"
restore

echo "quality of life"
Q=$L/event/ElysiumQualityOfLife.java

mv $Q /tmp/q.away
expect "neither small kindness" "neither quality-of-life feature ships"
restore
rm -f /tmp/q.away

edit $Q 'public static void onDeath(LivingDeathEvent event) {' \
        'public static void onNotDeath(LivingDeathEvent event) {'
edit $Q '@SubscribeEvent
    public static void onNotDeath' 'public static void onNotDeath'
expect "nothing saying where you died" "stops showing it"
restore

edit $Q 'Component.translatable(
                                player.level().dimension().location().toLanguageKey("dimension")))' \
        'Component.empty())'
expect "a coordinate with no world" "wrong world"
restore

edit $Q 'player.tickCount - last < QUIET_FOR' 'player.tickCount - last < 0'
expect "a warning that repeats until it is muted" "gets muted"
restore

edit $Q 'player.tickCount % INTERVAL != 0' 'false'
expect "every player's gear checked every tick" "rather than on an interval"
restore

edit $Q 'player.displayClientMessage(Component.translatable("elysium.qol.wearing_out",
                    stack.getHoverName()).withStyle(ChatFormatting.RED), true);' \
        'player.sendSystemMessage(Component.translatable("elysium.qol.wearing_out",
                    stack.getHoverName()).withStyle(ChatFormatting.RED));'
expect "a warning that scrolls away" "scrolls away"
restore

edit $Q 'if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {' \
        'if (stack.isEmpty() || stack.getMaxDamage() <= 0) {'
expect "a durability check on things with no durability" "can be damaged at all"
restore

python3 -c "
import json
p='$LANG'
d=json.load(open(p)); d.pop('elysium.qol.died')
json.dump(d, open(p,'w'), indent=2)"
expect "a death message with no words behind it" "nothing translates it"
restore

echo
echo "restoring and re-checking"
restore
if (cd $ROOT && python3 elysium-lib/validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
rm -f $SNAP
echo "attribute tooltips: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
