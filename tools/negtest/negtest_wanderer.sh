#!/usr/bin/env bash
# P12.2 / P12.8 — the wandering trader, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-npcs
W=$M/src/main/java/com/elysium/npcs/village/ElysiumWandererTrades.java
SNAP=/tmp/negtest_wanderer_snapshot.tgz
pass=0; fail=0

tar czf $SNAP -C /tmp Elysium-mono/elysium-npcs

out() { (cd $ROOT && python3 elysium-npcs/validate.py 2>&1); }
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

echo "the wandering trader"
mv $W /tmp/w.away
expect "a trader left as vanilla's" "he is a llama with some coral"
restore
rm -f /tmp/w.away

edit $W 'event.getRareTrades().addAll(rare());' ''
expect "a pocket written and never offered" "never offered"
restore

edit $W 'sell(Items.LEAD, 1, 3, 8),
                sell(Items.HONEYCOMB, 3, 3, 8),
                sell(Items.SEA_PICKLE, 2, 4, 6),
                sell(Items.GLOW_INK_SAC, 2, 4, 6),
                sell(Items.PRISMARINE_CRYSTALS, 3, 5, 6),
                sell(Items.AMETHYST_SHARD, 4, 5, 6),
                sell(Items.POWDER_SNOW_BUCKET, 1, 6, 4)' 'sell(Items.LEAD, 1, 3, 8)'
expect "the same trader every time" "a short list is the same trader"
restore

edit $W 'sell(Items.NAUTILUS_SHELL, 1, 10, 3),
                sell(Items.ENDER_PEARL, 2, 12, 3),
                sell(Items.SLIME_BALL, 6, 9, 3),
                sell(Items.PHANTOM_MEMBRANE, 2, 12, 2),
                sell(Items.SHULKER_SHELL, 1, 32, 1)' 'sell(Items.NAUTILUS_SHELL, 1, 10, 3)'
expect "rare slots that are always the same two" "'rare' means 'always these'"
restore

edit $W 'sell(Items.NAME_TAG, 1, 12, 2),' 'sell(Items.NAME_TAG, 1, 2, 2),'
expect "a rare pocket no dearer than the common one" "two more common slots"
restore

edit $W 'sell(Items.SHULKER_SHELL, 1, 32, 1)));' 'buy(Items.SHULKER_SHELL, 1, 32, 1)));'
expect "a chore in the rare pocket" "a chore that sometimes exists"
restore

edit $W 'public static void register(VillagerTrades.ItemListing listing, boolean rare) {' \
        'private static void register(VillagerTrades.ItemListing listing, boolean rare) {'
expect "a registry nothing can reach" "whole of the trade registry"
restore

edit $W 'new CopyOnWriteArrayList<>()' 'new java.util.ArrayList<>()'
expect "a registry read while it is being written" "safe to read from a world thread"
restore

edit $W 'for (Added extra : ADDED) {' 'for (Added extra : List.<Added>of()) {'
expect "a registry that accepts entries and drops them" "accepts entries and drops them"
restore

edit $W 'private static final List<Added> ADDED' \
        'private static final String CORE_INGOT = "elysium:aetherium_ingot";

    private static final List<Added> ADDED'
expect "an offer that is air without the content mod" "or it is air in a pack"
restore

echo
echo "restoring and re-checking"
restore
if (cd $ROOT && python3 elysium-npcs/validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
rm -f $SNAP
echo "wandering trader: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
