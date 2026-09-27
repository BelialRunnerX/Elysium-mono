#!/usr/bin/env bash
# Storage in the world, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-logistics
S=$M/src/main/java/com/elysium/logistics
R=$M/src/main/resources
G=$M/tools/gen_logistics.py
SNAP=/tmp/logistics2_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-logistics)
restore() { rm -rf $ROOT/elysium-logistics && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 300 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }
regen() { (cd $M && ELYSIUM_LOGISTICS_OUT=$M python3 tools/gen_logistics.py >/dev/null) \
  || { echo "GENERATOR BROKE"; exit 2; }; }

echo "the five blocks and what each of them needs"
edit $S/registry/LogisticBlocks.java 'BLOCKS.register("controller",' 'BLOCKS.register("hub",'
expect "a block registered under a name nothing else knows" "has no item"

rm $R/data/elysiumlogistics/loot_table/blocks/steel_drawer.json
expect "a drawer that destroys everything in it when mined" "has no drop"

edit $G '    "bus": "Bus",' '    "conduit": "Bus",'
regen
expect "a block with no name but its id" "has no name in the language file"

edit $S/registry/LogisticItems.java '            TIMBER_DRAWER, BRASS_DRAWER, STEEL_DRAWER, BUS, CONTROLLER,' '            TIMBER_DRAWER, BRASS_DRAWER, STEEL_DRAWER, BUS,'
expect "a block in no creative tab" "is in no creative tab"

edit $S/block/BusBlock.java 'box(5, 5, 5, 11, 11, 11)' 'box(3, 3, 3, 13, 13, 13)'
expect "a bus you can hit where you cannot see it" "different sizes"

edit $G 'STEPS = 5' 'STEPS = 3'
regen
expect "a drawer whose blockstate no longer covers every step of fullness" "every step of fullness"

edit $G '            entry = {"model": f"{NS}:block/{tier}_{step}"}' '            entry = {"model": f"{NS}:block/{tier}_0"}'
regen
expect "a drawer that looks the same however full it is" "read at a glance"

echo "the drawer, and the items it must not lose"
edit $S/block/DrawerStore.java '        int took = LogisticRules.accepted(stack.getCount(), roomIn(slot));' '        int took = Math.min(stack.getCount(), roomIn(slot));'
expect "a drawer doing its own arithmetic on a transfer" "not the one the game runs"

edit $S/block/DrawerStore.java '        if (left > 0 && LogisticRules.voids(voiding)) {' '        if (left > 0) {'
expect "a drawer that destroys the overflow without being asked" "single named place"

edit $S/block/DrawerStore.java '        if (!kinds[slot].isEmpty()
                && !ItemStack.isSameItemSameComponents(kinds[slot], stack)) {
            return stack;
        }' '        if (false) {
            return stack;
        }'
expect "a drawer that counts two different items as one" "counted as one"

edit $S/block/DrawerStore.java '        int given = Math.min(Math.min(wanted, counts[slot]), kinds[slot].getMaxStackSize());' '        int given = wanted;'
expect "a drawer that hands out more than it holds" "more than it holds"

edit $S/block/DrawerStore.java '            if (!kinds[slot].isEmpty()
                    && ItemStack.isSameItemSameComponents(kinds[slot], stack)) {
                return slot;
            }' '            if (false) {
                return slot;
            }'
expect "a drawer that starts a second pile of what it already holds" "second pile"

echo "the network, and the controller that indexes it"
edit $S/block/LogisticNetwork.java '                if (drawerAt(level, next) != null) {
                    drawers.add(next.immutable());
                    continue;
                }' '                if (drawerAt(level, next) != null) {
                    drawers.add(next.immutable());
                }'
expect "a network that chains through drawers as well as bus" "accidental network"

edit $S/block/LogisticNetwork.java '                if (seen.size() > MOST_STEPS) {' '                if (false) {'
expect "a network walk with no limit on it" "no limit"

edit $S/block/LogisticNetwork.java '        List<BlockPos> found = sorted(drawers);' '        List<BlockPos> found = new ArrayList<>(drawers);'
expect "a network that orders its drawers by hash" "whatever order a hash table"

edit $S/block/ControllerBlock.java '            return network.devices() <= 0 ? null : network.machine();' '            return new IndustryRules.Machine(
                    LogisticRules.DEVICE_DRAW * share(), IndustryRules.Voltage.LOW);'
expect "a controller whose draw depends on what it is given" "the other way round"

edit $S/block/ControllerBlock.java '            return Math.max(0.0F, network().movesAt(share()) - spent);' '            return Float.MAX_VALUE;'
expect "a controller that ignores the grid entirely" "ignores the grid"

edit $S/block/ControllerBlock.java '                controller.spend(moved);' '                controller.spend(0);'
edit $S/block/ControllerBlock.java '                controller.spend(out.getCount());' '                controller.spend(0);'
expect "a budget nothing is ever charged against" "nobody applies"

echo "the recipes, and the first rung"
edit $G '        "P": TAG("minecraft:planks"), "G": ITEM("minecraft:glass_pane")},' '        "P": TAG("c:plates/steel"), "G": ITEM("minecraft:glass_pane")},'
regen
expect "a first drawer that needs a factory" "long before it"

edit $G '        "C": ITEM("minecraft:copper_ingot"), "D": ITEM(f"{NS}:timber_drawer"),' '        "C": ITEM("storagedrawers:upgrade_template"), "D": ITEM(f"{NS}:timber_drawer"),'
regen
expect "a dependency on one of the seventeen" "and nothing else"

edit $R/assets/elysiumlogistics/lang/en_us.json '"Empty. It will hold %s of each thing."' '"Empty. It will hold %s of each thing. "'
expect "a generated file quietly edited by hand" "edited by hand"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all logistics checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "logistics2: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
