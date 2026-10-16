#!/usr/bin/env bash
# P12.22 — sample gear in the creative tab.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-armory
S=$M/src/main/java/com/elysium/armory/item/ArmorySamples.java
I=$M/src/main/java/com/elysium/armory/registry/ArmoryItems.java
SNAP=/tmp/negtest_samples_snapshot.tgz
pass=0; fail=0
tar czf $SNAP -C /tmp Elysium-mono/elysium-armory
out() { (cd $ROOT && python3 elysium-armory/validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read(); assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }
restore() { rm -rf $M; tar xzf $SNAP -C /tmp; }

echo "sample gear"
mv $S /tmp/s.away
expect "a tab of empty swords" "no name worth reading"
restore
rm -f /tmp/s.away

edit $I 'com.elysium.armory.item.ArmorySamples.all().forEach(out::accept);' ''
expect "samples built and never shown" "never put in the tab"
restore

edit $S 'return ArmoryBuilder.gear(gearType, List.of(part));' \
        'return new ItemStack(ArmoryItems.gear().get(gearType).get());'
expect "a sample assembled a second way" "two different swords"
restore

edit $S 'if (main == null || ArmoryMaterials.get().byId(MATERIAL) == null) {' 'if (main == null) {'
edit $S 'if (ArmoryMaterials.get().byId(MATERIAL) == null) {
            return samples;
        }' ''
expect "a tab that does not survive the main menu" "before a world is even loaded"
restore

edit $S 'if (part.partType().equals("main") && part.gearType().equals(gearType)) {' \
        'if (part.gearType().equals(gearType)) {'
expect "a sample built from whatever came first" "decides what the piece is"
restore

echo "what the client is told"
T=$M/src/main/java/com/elysium/armory/client/ArmoryTooltips.java
C=$M/src/main/java/com/elysium/armory/registry/ArmoryComponents.java

edit $T 'import net.minecraft.world.item.ItemStack;' \
        'import net.minecraft.world.item.ItemStack;
import com.elysium.armory.material.ArmoryMaterials;'
edit $T 'public final class ArmoryTooltips {' \
        'public final class ArmoryTooltips {

    private static Object peek() {
        return ArmoryMaterials.get().all();
    }
'
expect "a tooltip reading the datapack from the client" "empty on every"
restore

edit $C '.networkSynchronized(ArmoryGearStats.STREAM_CODEC)' ''
edit $C '.networkSynchronized(ArmoryGearParts.STREAM_CODEC)' ''
edit $C '.networkSynchronized(ArmoryTint.STREAM_CODEC)' ''
expect "components that never reach the client" "blank on a server"
restore

echo
echo "restoring and re-checking"
restore
if (cd $ROOT && python3 elysium-armory/validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
rm -f $SNAP
echo "sample gear: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
