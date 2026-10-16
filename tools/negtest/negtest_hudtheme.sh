#!/usr/bin/env bash
# Food, armour, the attack indicator and the inventory, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono/elysium-lib
B=$ROOT/src/main/java/com/elysium/lib/client/ElysiumStatusBars.java
M=$ROOT/src/main/java/com/elysium/lib/client/ElysiumMeter.java
G=$ROOT/tools/gen_gui.py
T=$ROOT/src/main/resources/assets/minecraft/textures/gui
pass=0; fail=0
regen() { (cd /tmp/Elysium-mono && python3 elysium-lib/tools/gen_gui.py >/dev/null 2>&1); }
out() { (cd $ROOT && python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read(); assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "two readouts at once"
mv $B /tmp/sb.bak
expect "the food and armour bars deleted" "vanilla icons beside an Elysium health bar"
mv /tmp/sb.bak $B

cp $B /tmp/sb.bak
edit $B 'if (!VanillaGuiLayers.FOOD_LEVEL.equals(event.getName())) {' 'if (true) {'
edit $B '        event.setCanceled(true);' ''
expect "vanilla's drumsticks left drawing underneath" "one on top of the other"
cp /tmp/sb.bak $B

cp $B /tmp/sb.bak
edit $B 'if (!mc.gameMode.canHurtPlayer()) {' 'if (false) {'
edit $B '        event.setCanceled(true);' '        event.setCanceled(true); // kept'
expect "food and armour bars in creative" "vanilla never gave them"
cp /tmp/sb.bak $B

echo "the stat that outgrew its icons"
cp $B /tmp/sb.bak
edit $B 'float toughness = Math.max(0.0F,
                (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));' 'float toughness = 0.0F;'
expect "toughness invisible again" "invisible again"
cp /tmp/sb.bak $B

cp $B /tmp/sb.bak
edit $B 'public static final float ARMOUR_CEILING = 30.0F;' 'public static final float ARMOUR_CEILING = 8.0F;'
expect "toughness taking most of the armour bar" "the smaller ceiling and the rarer stat"
cp /tmp/sb.bak $B

echo "the layout"
cp $B /tmp/sb.bak
edit $B 'public static final int RIGHT_OF_CENTRE = 14;' 'public static final int RIGHT_OF_CENTRE = -40;'
expect "food and health overlapping across the centre" "they overlap"
cp /tmp/sb.bak $B

cp $B /tmp/sb.bak
edit $B 'public static final int ARMOUR_LIFT = 5;' 'public static final int ARMOUR_LIFT = -6;'
expect "the armour bar landing on the health text" "overlaps the health readout"
cp /tmp/sb.bak $B

cp $B /tmp/sb.bak
edit $B 'public static final int ARMOUR_HEIGHT = 3;' 'public static final int ARMOUR_HEIGHT = 5;'
expect "armour drawn as loud as health" "the eye has no anchor"
cp /tmp/sb.bak $B

echo "the shared arithmetic"
cp $M /tmp/mt.bak
edit $M 'return Math.max(1, Math.min(width - 1, head));' 'return head;'
expect "a region collapsing to nothing" "not shown at all"
cp /tmp/mt.bak $M

echo "the textures"
mv $T/container/inventory.png /tmp/inv.bak
expect "the inventory texture missing" "run regen.sh"
mv /tmp/inv.bak $T/container/inventory.png

cp $G /tmp/gg.bak
edit $G 'INVENTORY_SLOTS = [(8 + c * 18, 84 + r * 18) for r in range(3) for c in range(9)]' \
        'INVENTORY_SLOTS = [(9 + c * 18, 85 + r * 18) for r in range(3) for c in range(9)]'
regen
expect "slot recesses a pixel out of true" "offset from the slots"
cp /tmp/gg.bak $G
regen

cp $G /tmp/gg.bak
edit $G '    img = Image.new("RGBA", (256, 256), CLEAR)
    panel(img, 0, 0, 176, 166)' '    img = Image.new("RGBA", (176, 166), CLEAR)
    panel(img, 0, 0, 176, 166)'
regen
expect "the inventory sheet resized" "the game reads fixed offsets"
cp /tmp/gg.bak $G
regen

echo
if (cd $ROOT && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED"; fail=$((fail+1))
fi
echo "hud theme: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
