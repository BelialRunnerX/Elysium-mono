#!/usr/bin/env bash
# The seven network devices and the packs, broken one way at a time.
#
# Every rule the logistics validator gained for the devices and for bags and
# crates, and every harness property behind them: each case below breaks one
# thing, runs the module validator, and expects the sentence that names it.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
M=$ROOT/elysium-logistics
S=$M/src/main/java/com/elysium/logistics
R=$M/src/main/resources
G=$M/tools/gen_logistics.py
SNAP=/tmp/logistics_devices_snap.tar
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
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; restore; exit 2; }; }
regen() { (cd $M && ELYSIUM_LOGISTICS_OUT=$M python3 tools/gen_logistics.py >/dev/null 2>&1) \
  || { echo "GENERATOR BROKE"; restore; exit 2; }; }

D=$S/block/DeviceBlock.java

echo "a device's round"
edit $D 'if (++since < NetRules.TICK_EVERY) {' 'if (++since < 1) {'
expect "a device that works every tick" "twenty times a second"

edit $D '            findController(level, pos);
            ControllerBlock.ControllerBlockEntity controller = controller();' \
        '            ControllerBlock.ControllerBlockEntity controller = controller();'
expect "a device that never finds its controller again" "never looks for its controller"

edit $D 'if (controller.share() <= 0.0F) {' 'if (false) {'
expect "a device working on a dark grid" "a network nobody feeds"

edit $D 'case NetRules.IMPORT -> importRound(controller);' 'case NetRules.IMPORT -> { }'
expect "an importer that is never run" "the importer is never run"

edit $D 'case NetRules.SIGNAL -> power(level, pos, state, detects());' \
        'case NetRules.SIGNAL -> power(level, pos, state, false);'
expect "a detector that never says anything" "the detector is never run"

edit $D 'if (offered.isEmpty() || !passes(offered)) {' 'if (offered.isEmpty()) {'
expect "an importer that ignores its filter" "ignores its filter"

edit $D 'if (into == null || filter.isEmpty()) {' 'if (into == null) {'
expect "an unfiltered exporter emptying the network" "empties the whole network"

edit $D 'left = from.insertItem(slot, left, false);' 'left = left.copy();'
expect "a move that does not put back what it could not place" "loses the difference"

echo "the logic layer in the world"
edit $D '            NetRules.Value said = NetRules.evaluate(
                    NetRules.detectorExpression(idOf(filter), threshold), this);
            return said != null && said.truth();' \
        '            return countOf(idOf(filter)) >= threshold;'
expect "a detector comparing in Java" "a file nothing in the world reads"

edit $D '            NetRules.Value line = NetRules.evaluate(
                    NetRules.monitorExpression(idOf(filter)), this);' \
        '            NetRules.Value line = null;'
expect "a monitor that says nothing through the layer" "a monitor does not say"

edit $D 'threshold = NetRules.nextThreshold(threshold);' 'threshold = threshold * 2;'
expect "a detector stepped off the ladder" "stepped along the ladder"

edit $S/rules/NetRules.java '            made.add(one.name());' \
        '            if (!one.name().equals("monitor")) made.add(one.name());'
expect "a roster the blocks are registered from that leaves a device out" "the network harness failed"

edit $S/rules/NetRules.java 'extra += one.draws() - 1;' 'extra += one.draws();'
expect "a device charged twice for its slot" "the network harness failed"

echo "what the devices bring onto the network"
edit $D 'case NetRules.EXPOSE -> facingHandler();' 'case NetRules.EXPOSE -> null;'
expect "a storage bus that brings nothing" "does not bring the inventory"

edit $D 'if (linking > 0) {' 'if (false) {'
expect "an ender link reaching through another" "never returns"

edit $D 'if (there instanceof ControllerBlock.ControllerBlockEntity
                    || there instanceof DeviceBlockEntity) {' \
        'if (there instanceof DeviceBlockEntity) {'
expect "a storage bus pointed at its own controller" "contain itself"

edit $D '                gone.leaveChannel();
' ''
expect "a broken ender link left on its channel" "stays on its channel"

edit $S/block/LogisticNetwork.java '                    devices.add(next.immutable());
                    continue;' '                    continue;'
expect "a network walk that finds no devices" "finds no devices"

edit $S/block/LogisticNetwork.java 'surchargeOf(level, devices),
                found.size() + faces.size() > LogisticRules.MOST_DEVICES);' \
        '0,
                found.size() > LogisticRules.MOST_DEVICES);'
expect "a device that takes no slot and pays nothing" "take a slot on its controller"

edit $S/rules/LogisticRules.java 'devices * DEVICE_DRAW + extra * DEVICE_DRAW,' 'devices * DEVICE_DRAW,'
expect "dear devices billed as drawers" "costs what a network of drawers does"

edit $S/block/ControllerBlock.java 'IItemHandler brought = device == null ? null : device.store();' \
        'IItemHandler brought = null;'
expect "a controller that ignores what its devices bring" "faces onto nothing"

edit $S/registry/LogisticBlockEntities.java '            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, DEVICE.get(),
                    (entity, side) -> entity.exposed());' ''
expect "an interface with no capability" "nothing can take from it"

edit $S/registry/LogisticBlockEntities.java 'blocks[at++] = LogisticBlocks.DEVICES.get(device.name()).get();' \
        'blocks[at++] = LogisticBlocks.BUS.get();'
expect "a device block entity bound to the wrong blocks" "crashes the moment"

edit $S/registry/LogisticBlocks.java '() -> new DeviceBlock(steel().noOcclusion(), device)));' \
        '() -> new Block(steel())));'
expect "devices registered as plain blocks again" "registered as plain blocks"

edit $G '            for powered in ("false", "true"):' '            for powered in ("false",):'
regen
expect "a device whose blockstate forgets its redstone" "every facing times both states"

edit $G '    "elysiumlogistics.detector.stepped": "Now firing at %s.",
' ''
regen
expect "a device sentence missing from the language file" "not in the language file"

P=$S/pack/PackStore.java
echo "every change a pack goes through"
edit $P 'return PackRules.capacity(body, fitted());' 'return PackRules.capacity(body, List.of());'
expect "a pack whose capacity ignores its parts" "not the rules' sum"

edit $P 'if (stack.isEmpty() || !mayHold(stack)) {' 'if (stack.isEmpty()) {'
expect "a pack that takes a pack" "a pack takes a pack"

edit $P '        return !(ours && !PackRules.mayHold(id.getPath()))
                && !stack.has(LogisticComponents.CONTENTS.get());' \
        '        return !(ours && !PackRules.mayHold(id.getPath()));'
expect "a renamed bag going inside a bag" "renamed in an anvil"

edit $P 'if (!chosen && !PackRules.admits(fitted, held)) {' 'if (false) {'
expect "a sieve that does not sieve" "a sieve does not sieve"

edit $P 'if (PackRules.accepted(body, fitted, stored, 1) != 1) {' 'if (false) {'
expect "new piles started without the rules" "without asking the rules"

edit $P 'if (!left.isEmpty() && PackRules.voidsOverflow(fitted, held)) {' \
        'if (!left.isEmpty() && PackRules.voids(fitted)) {'
expect "a seal destroying what the pack does not hold" "does not already hold"

edit $P 'if (!PackRules.fits(body, after)) {' 'if (false) {'
expect "a part fitted without asking" "a pocket goes in twice"

edit $P 'if (!PackRules.mayRemove(body, fitted, going, occupied())) {' 'if (false) {'
expect "a pocket pulled out of a full bag" "too full to lose it"

edit $P 'if (PackRules.has(fitted, PackRules.COOK)) {' 'if (false) {'
expect "an ember plate that cooks nothing" "cooks nothing"

edit $P '                    items = before;
                    continue;' '                    continue;'
expect "a press that loses nine when it cannot place one" "are gone"

echo "the screen, the bag and the crate"
edit $S/pack/PackMenu.java 'return PackStore.mayHold(stack) && super.mayPlace(stack);' 'return super.mayPlace(stack);'
expect "a pack's screen that takes a pack" "lets a pack be put inside it"

edit $S/pack/PackMenu.java '        public boolean mayPickup(Player player) {
            return false;' '        public boolean mayPickup(Player player) {
            return true;'
expect "the open bag picked up out of its screen" "picked up out of its own screen"

edit $S/pack/PackMenu.java 'if (locked >= 0 && type == ClickType.SWAP && button == locked) {' 'if (false) {'
expect "a number key swapping the open bag away" "number key"

edit $S/pack/PackMenu.java '    /** The game'"'"'s chest menus, by how many rows they have. */' \
        '    static final Object MINE = net.minecraft.core.registries.Registries.MENU;
    /** The game'"'"'s chest menus, by how many rows they have. */'
expect "a menu type of the module's own" "registers a menu type of its own"

edit $S/pack/PackMenu.java 'PackRules.shownOn(store.capacity(), pageNumber), level, still);' \
        'Math.min(54, store.capacity()), level, still);'
expect "a pack capped at one screen" "capped at one screen"

edit $S/pack/PackItem.java 'bag.set(LogisticComponents.PAGE.get(), PackRules.nextPage(room, page));' \
        'bag.set(LogisticComponents.PAGE.get(), 0);'
expect "a big bag that never turns its page" "never turns its page"

edit $S/pack/PackItem.java '() -> who.getItemInHand(hand) == bag && !bag.isEmpty(), locked),' '() -> true, locked),'
expect "a bag's screen outliving the bag in hand" "after the bag has left the hand"

edit $S/pack/PackEvents.java 'bus = EventBusSubscriber.Bus.GAME)' 'bus = EventBusSubscriber.Bus.MOD)'
expect "the parts' behaviours on the wrong bus" "none of them run"

edit $S/pack/PackEvents.java 'if (player.level().isClientSide || entity.hasPickUpDelay()' 'if (player.level().isClientSide'
expect "a lens that takes what was just thrown" "just thrown"

edit $S/pack/PackEvents.java '            coming.setCount(left.getCount());
' ''
expect "gathered and picked up both" "gathered and picked up both"

edit $S/pack/PackEvents.java '!PackRules.worthEating(missing, worth.nutrition())' 'worth.nutrition() <= 0'
expect "a ration tin wasting food" "whose worth is wasted"

edit $S/pack/PackEvents.java 'int wanted = PackRules.restockWanted(held.getCount(), held.getMaxStackSize());' 'int wanted = 64;'
expect "a restocker overfilling the hand" "overfills a hand"

edit $S/pack/PackBlock.java '            Containers.dropContents(level, pos, crate.store().items());
' ''
expect "a broken crate losing its contents" "does not drop what is in it"

edit $S/pack/PackBlock.java 'return store().insert(stack, simulate, false, level);' 'return store().insert(stack, simulate, true, level);'
expect "a hopper past the sieve" "some way other than through its rules"

edit $S/registry/LogisticBlockEntities.java '            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PACK.get(),
                    (entity, side) -> entity.handler());' ''
expect "a crate no hopper can reach" "no hopper or storage bus can reach it"

edit $S/registry/LogisticBlocks.java '            if (!body.carried()) {' '            if (body.carried()) {'
expect "the bags registered as blocks and the crates not" "cannot be put down"

edit $S/registry/LogisticItems.java 'properties -> new PackItem(properties, body),' 'properties -> new Item(properties),'
expect "a bag that is a plain item again" "a bag is a plain item"

edit $S/ElysiumLogistics.java '        com.elysium.logistics.registry.LogisticComponents.COMPONENTS.register(modEventBus);
' ''
expect "components never registered" "forgets everything"

rm $R/data/elysiumlogistics/loot_table/blocks/vault.json
expect "a vault that drops nothing when mined" "a placed body needs are missing"

echo "the arithmetic behind a pack"
edit $S/rules/PackRules.java '            room += one.slots();' '            room += one.slots() + (one.slots() > 0 ? 1 : 0);'
expect "a capacity bigger than the sum of the parts" "the pack harness failed"

edit $S/rules/PackRules.java '        return body(name) == null;' '        return true;'
expect "a rule that lets a bag inside a bag" "the pack harness failed"

edit $S/rules/PackRules.java '        return Math.min(MOST_SHOWN, capacity - firstOn(page));' '        return MOST_SHOWN;'
expect "pages that show slots that are not there" "the pack harness failed"

edit $S/rules/PackRules.java '        return !has(fitted, SIEVE) || alreadyHeld;' '        return true;'
expect "a sieve rule that lets everything in" "the pack harness failed"

edit $S/rules/PackRules.java '        return voids(fitted) && alreadyHeld;' '        return voids(fitted);'
expect "a seal rule that destroys the unheld" "the pack harness failed"

edit $S/rules/PackRules.java '        return nutrition > 0 && nutrition <= missing;' '        return nutrition > 0;'
expect "a feeding rule that wastes food" "the pack harness failed"

edit $S/rules/PackRules.java '        return most - held;' '        return most;'
expect "a restock rule that overfills" "the pack harness failed"

edit $S/rules/PackRules.java '        return new int[] {count / step, count % step};' '        return new int[] {count / step, 0};'
expect "a press rule that loses the remainder" "the pack harness failed"

echo
echo "restoring and re-checking"
restore
if (cd $M && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
echo "logistics (devices and packs): $pass caught, $fail missed"
[ "$fail" -eq 0 ]
