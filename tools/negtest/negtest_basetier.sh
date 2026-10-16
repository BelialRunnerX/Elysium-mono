#!/usr/bin/env bash
# The base claim block as a road to world tier, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
B=$ROOT/elysium-core/src/main/java/com/elysium/core/block/RegistryBeaconBlock.java
WT=$ROOT/elysium-lib/src/main/java/com/elysium/lib/tier/ElysiumWorldTier.java
T=$ROOT/elysium-lib/src/main/java/com/elysium/lib/tier/ElysiumTiers.java
CLANG=$ROOT/elysium-core/src/main/resources/assets/elysium/lang/en_us.json
pass=0; fail=0

outcore() { (cd $ROOT/elysium-core && python3 validate.py 2>&1); }
outlib()  { (cd $ROOT/elysium-lib  && python3 validate.py 2>&1); }

expectc() { local w="$2" g; g="$(outcore)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
expectl() { local w="$2" g; g="$(outlib)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }

edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, 'anchor not found in ' + p
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "paying for a tier"
cp $B /tmp/b.bak
edit $B 'noticed(player, grown);' ''
expectc "ascending a base that buys no tier" "buys ground and"
cp /tmp/b.bak $B

cp $B /tmp/b.bak
edit $B 'if (ElysiumTiers.lift(player, tier)) {' \
        'if (tier > 0 && player.getData(ElysiumTiers.WORLD_FLOOR.get()) >= 0) { player.setData(ElysiumTiers.WORLD_FLOOR.get(), tier);'
expectc "the floor written around the one place that refuses to lower it" "other than lift()"
cp /tmp/b.bak $B

cp $B /tmp/b.bak
edit $B 'ElysiumWorldTier.tierForAscension(claim.ascension())' 'claim.ascension()'
expectc "the beacon doing the ascension arithmetic itself" "arithmetic of its"
cp /tmp/b.bak $B

cp $B /tmp/b.bak
edit $B '        noticed(player, claim);
' ''
expectc "a world ascended before this existed with no way to catch up" "can never catch up"
cp /tmp/b.bak $B

cp $CLANG /tmp/cl.bak
edit $CLANG '"elysium.claim.world_tier"' '"elysium.claim.world_tier_gone"'
expectc "the moment a base raises the world tier, as a raw key" "raw key"
cp /tmp/cl.bak $CLANG

echo "the floor itself"
cp $T /tmp/t.bak
edit $T 'if (floor <= floorOf(player)) {' 'if (false) {'
expectl "a floor that can be lowered by lifting it" "never compares against the floor"
cp /tmp/t.bak $T

cp $T /tmp/t.bak
edit $T '    public static boolean visited(Player player, Level level) {
        return lift(player, worldTier(level.dimension().location()));' \
        '    public static boolean visited(Player player, Level level) {
        player.setData(WORLD_FLOOR.get(), worldTier(level.dimension().location()));
        if (true) return true;
        return lift(player, worldTier(level.dimension().location()));'
expectl "a second writer of the world floor" "written in more than one place"
cp /tmp/t.bak $T

cp $T /tmp/t.bak
edit $T 'return lift(player, worldTier(level.dimension().location()));' \
        'return floorOf(player) >= 0;'
expectl "arriving in a deeper world writing a floor nothing compared" "no longer raises the floor through lift()"
cp /tmp/t.bak $T

echo
echo "restoring and re-checking"
if (cd $ROOT && python3 elysium-core/validate.py >/dev/null 2>&1) && \
   (cd $ROOT && python3 elysium-lib/validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
echo "base tier: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
