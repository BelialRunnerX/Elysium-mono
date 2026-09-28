#!/usr/bin/env bash
# The pack's gear on vanilla's lists, taken off one way at a time.
#
# Two validators hold this: the library's own (the pack, the table, the
# harness against vanilla's files) and the root check_kind_tags.py (the seam
# to the modules' gear classes and ids). Each case names which one it runs.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-lib
TABLE=$M/src/main/java/com/elysium/lib/item/ElysiumKindTags.java
PACK=$M/src/main/java/com/elysium/lib/data/ElysiumKindPack.java
MAIN=$M/src/main/java/com/elysium/lib/ElysiumLib.java
BROAD=$ROOT/elysium-arsenal/src/main/java/com/elysium/arsenal/item/BroadToolItem.java
PROPS=$ROOT/elysium-transit/gradle.properties
SNAP=/tmp/kind_tags_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-lib/src/main/java/com/elysium/lib/item elysium-lib/src/main/java/com/elysium/lib/data elysium-lib/src/main/java/com/elysium/lib/ElysiumLib.java elysium-lib/tools elysium-arsenal/src/main/java/com/elysium/arsenal/item elysium-transit/gradle.properties)
restore() { (cd $ROOT && tar xf $SNAP); }
lib() { (cd $M && timeout 1800 python3 validate.py 2>&1); }
root() { (cd $ROOT && timeout 300 python3 check_kind_tags.py 2>&1); }
expect() { local runner="$1" what="$2" w="$3" g; g="$($runner)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $what"; pass=$((pass+1));
  else echo "  MISSED : $what"; echo "         got: $(echo "$g" | grep -i 'kind\|problem' | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the table (library validator, through the harness)"
edit $TABLE '            m.put(blade, List.of("minecraft:swords", "c:tools/melee_weapon"));' '            m.put(blade, List.of("c:tools/melee_weapon"));'
expect lib "a sword on no list the enchanting tables read" 'is on no list that minecraft:enchantable/durability reaches'

edit $TABLE '        m.put("helmet", List.of("minecraft:head_armor", "c:armors"));' '        m.put("helmet", List.of("minecraft:helmets", "c:armors"));'
expect lib "a helmet put on a list vanilla does not have" 'which is not a list vanilla-assets.jar has'

edit $TABLE '        m.put("mace", List.of(' '        m.put("flail", List.of('
expect lib "a row naming a kind the tree does not have" 'names a kind the gear tree does not have'

echo "the pack (library validator)"
edit $PACK '                    new PackSelectionConfig(true, Pack.Position.TOP, true));' '                    new PackSelectionConfig(false, Pack.Position.TOP, false));'
expect lib "a pack a player can turn off" 'can be turned off, or moved under a pack that replaces its tags'

edit $PACK '            String kind = ElysiumLootCategory.of(entry.getValue());' '            String kind = entry.getValue() instanceof net.minecraft.world.item.SwordItem ? "sword" : null;'
expect lib "a pack deciding for itself what an item is" 'decides for itself what an item is with instanceof'

edit $PACK '            if (!id.getNamespace().startsWith(ElysiumKindTags.NAMESPACE_PREFIX)) {' '            if (false) {'
expect lib "a pack tagging every mod's items" "tags other mods' items, or none"

edit $MAIN '                        com.elysium.lib.data.ElysiumKindPack.addFinder(packs));' '                        packs.getPackType());'
expect lib "a pack nothing adds to the list" 'no longer adds the kind-tag pack'

echo "the seam to the modules (root check)"
edit $BROAD 'public class BroadToolItem extends DiggerItem implements com.elysium.lib.item.ElysiumKinded {' 'public class BroadToolItem extends DiggerItem {'
expect root "a digging tool that no longer says what it is" 'neither it nor a parent implements ElysiumKinded'

edit $BROAD '        return "scythe".equals(shape) ? "hoe" : shape;' '        return "scythe".equals(shape) ? "reaper" : shape;'
expect root "a tool answering with a kind the tree does not have" 'which the gear tree does not have'

edit $PROPS 'mod_id=elysiumtransit' 'mod_id=transit'
expect root "a module whose id the pack does not tag" 'does not start with "elysium", so the kind-tag pack'

echo "restoring and re-checking"
restore
if root | grep -q "PROBLEM"; then echo "  RESTORE FAILED (root)"; fail=$((fail+1)); else echo "  tree restored clean"; fi
rm -f $SNAP
echo "kind tags: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
