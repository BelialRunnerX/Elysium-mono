#!/usr/bin/env bash
# P7 — gems, sockets and augmenting, broken one rule at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
LIB=$ROOT/elysium-lib
CORE=$ROOT/elysium-core
LSRC=$LIB/src/main/java/com/elysium/lib
CSRC=$CORE/src/main/java/com/elysium/core
CRES=$CORE/src/main/resources
pass=0; fail=0

SNAP=/tmp/negtest_gems_snapshot.tgz
tar czf $SNAP -C $ROOT elysium-lib/src elysium-lib/tools elysium-core/src elysium-core/tools

outlib()  { (cd $LIB && python3 validate.py 2>&1); }
outcore() { (cd $CORE && python3 validate.py 2>&1); }
expect()  { local w="$2" g; g="$(outlib)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
expectc() { local w="$2" g; g="$(outcore)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@"; }

echo "what a purity is worth"
cp $LSRC/item/ElysiumPurity.java /tmp/pu.bak
edit $LSRC/item/ElysiumPurity.java 'return 1 << (at - 1);' 'return at;'
expect "a price that climbs instead of doubling" "stopped doubling"
cp /tmp/pu.bak $LSRC/item/ElysiumPurity.java

cp $LSRC/item/ElysiumPurity.java /tmp/pu.bak
edit $LSRC/item/ElysiumPurity.java 'if (at > 31) {
            return Integer.MAX_VALUE;
        }' ''
expect "a price that overflows at a tier a save can name" "overflowed instead of saturating"
cp /tmp/pu.bak $LSRC/item/ElysiumPurity.java

cp $LSRC/item/ElysiumPurity.java /tmp/pu.bak
edit $LSRC/item/ElysiumPurity.java 'long kept = (long) Math.floor(worth * (1.0D - GRINDING_LOSS));' \
                                   'long kept = worth;'
expect "grinding that loses nothing" "dust is a way to make value"
cp /tmp/pu.bak $LSRC/item/ElysiumPurity.java

cp $LSRC/item/ElysiumPurity.java /tmp/pu.bak
edit $LSRC/item/ElysiumPurity.java 'public static int cutCost(int fromTier) {
        return baseRunes(Math.max(1, fromTier));' 'public static int cutCost(int fromTier) {
        return Math.max(1, baseRunes(Math.max(1, fromTier)) / 2);'
expect "cutting cheaper than merging, which makes runes out of nothing" "makes runes out of nothing"
cp /tmp/pu.bak $LSRC/item/ElysiumPurity.java

cp $LSRC/item/ElysiumPurity.java /tmp/pu.bak
edit $LSRC/item/ElysiumPurity.java 'return 0.05D * com.elysium.lib.tier.ElysiumWorldTier.share(worldTier);' \
                                   'return 0.05D * worldTier;'
expect "a rune that becomes a certain drop" "certain drop at world tier"
cp /tmp/pu.bak $LSRC/item/ElysiumPurity.java

cp $LSRC/item/ElysiumPurity.java /tmp/pu.bak
edit $LSRC/item/ElysiumPurity.java 'while (tier < NAMED && rolls.getAsDouble() < chance) {' \
                                   'while (tier < NAMED && rolls.getAsDouble() < 0.9D) {'
expect "every find coming up flawless" "a rune is meant to be a find"
cp /tmp/pu.bak $LSRC/item/ElysiumPurity.java

cp $LSRC/item/ElysiumPurity.java /tmp/pu.bak
edit $LSRC/item/ElysiumPurity.java 'case 6 -> "Perfect";' 'case 6 -> "Tier 6";'
expect "a purity inside the named range with no name" "has no name"
cp /tmp/pu.bak $LSRC/item/ElysiumPurity.java

echo "the benches"
cp $CSRC/menu/AugmentingMenu.java /tmp/am.bak
edit $CSRC/menu/AugmentingMenu.java 'if (!ElysiumAugments.applies(sigil, gear, gearTier)) {
            return;
        }' ''
expectc "a sigil spent on a piece it does nothing to" "does nothing to"
cp /tmp/am.bak $CSRC/menu/AugmentingMenu.java

cp $CSRC/menu/AugmentingMenu.java /tmp/am.bak
edit $CSRC/menu/AugmentingMenu.java 'if (result.hasRune()) {' 'if (false) {'
expectc "a withdrawn rune destroyed by the bench that saves it" "exists to save"
cp /tmp/am.bak $CSRC/menu/AugmentingMenu.java

cp $CSRC/menu/GemCuttingMenu.java /tmp/gm.bak
edit $CSRC/menu/GemCuttingMenu.java 'ElysiumPurity.canCut(tier) ? ElysiumPurity.cutCost(tier) : 0' \
                                    'ElysiumPurity.cutCost(tier)'
expectc "a bench that will cut a rune past the arithmetic" "would overflow rather than refuse"
cp /tmp/gm.bak $CSRC/menu/GemCuttingMenu.java

cp $CSRC/event/RuneDropHandler.java /tmp/rd.bak
edit $CSRC/event/RuneDropHandler.java 'int tier = ElysiumTiers.of(player);' 'int tier = 4;'
expectc "rune drops that ignore the world tier" "Frontier would rain runes"
cp /tmp/rd.bak $CSRC/event/RuneDropHandler.java

cp $CSRC/event/RuneDropHandler.java /tmp/rd.bak
edit $CSRC/event/RuneDropHandler.java 'if (item instanceof ElysiumRuneItem) {' 'if (false) {'
expectc "a drop pool that is a list rather than the registry" "another mod adds"
cp /tmp/rd.bak $CSRC/event/RuneDropHandler.java

echo "what a bench makes"
python3 - <<'PYX'
import json
p = '/tmp/Elysium-mono/elysium-core/src/main/resources/data/elysium/recipe/gem_dust.json'
json.dump({"type": "minecraft:crafting_shapeless", "category": "misc",
           "ingredients": [{"item": "minecraft:amethyst_shard"}],
           "result": {"count": 1, "id": "elysium:gem_dust"}}, open(p, "w"), indent=2)
PYX
expectc "a way around grinding a rune for dust" "a way around grinding"
rm -f $CRES/data/elysium/recipe/gem_dust.json

cp $CSRC/recipe/GemGrindingRecipe.java /tmp/gg.bak
edit $CSRC/recipe/GemGrindingRecipe.java 'new ItemStack(Elysium.GEM_DUST.get(), Math.min(dust, 64))' \
                                         'ItemStack.EMPTY'
expectc "a grinding recipe that makes nothing while still counting as dust's source" "does not make it"
cp /tmp/gg.bak $CSRC/recipe/GemGrindingRecipe.java

cp $CSRC/item/SigilItem.java /tmp/si.bak
edit $CSRC/item/SigilItem.java 'lines.add(Component.translatable("elysium.sigil." + augment + ".desc")' \
                               'lines.add(Component.translatable("elysium.sigil.unknown")'
expectc "a sigil whose one line of interface is a raw key" "missing lang key"
cp /tmp/si.bak $CSRC/item/SigilItem.java

echo
echo "restoring and re-checking"
tar xzf $SNAP -C $ROOT
ok=1
(cd $LIB && python3 validate.py >/dev/null 2>&1) || ok=0
(cd $CORE && python3 validate.py >/dev/null 2>&1) || ok=0
if [ "$ok" = 1 ]; then echo "  tree restored clean";
else echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1)); fi
echo "gems: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
