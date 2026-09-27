#!/usr/bin/env bash
# Elysium Colonies, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-colonies
S=$M/src/main/java/com/elysium/colonies
R=$M/src/main/resources
SNAP=/tmp/colonies_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-colonies)
restore() { rm -rf $ROOT/elysium-colonies && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, 'anchor not found in ' + p
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@"; }

echo "the one seam"
edit $S/event/ColonyProgress.java 'import com.elysium.colonies.colony.Colonies;' \
  'import com.elysium.colonies.colony.Colonies;
import com.minecolonies.api.colony.IColonyManager;'
expect "a second file betting on somebody else's API" "exactly one may"

edit $S/colony/Colonies.java 'public record Settlement(int id, String name, int citizens, @Nullable UUID owner,
                             ResourceLocation dimension, BlockPos centre, int day,
                             double happiness) {
    }' 'public record Settlement(int id, String name, int citizens, @Nullable UUID owner,
                             ResourceLocation dimension, BlockPos centre, int day,
                             double happiness) {
    }
    public static IColony raw(Level level, BlockPos at) {
        return IColonyManager.getInstance().getClosestIColony(level, at);
    }'
expect "a seam that hands out the type it was containing" "puts the bet back outside"

edit $S/event/ColonyRaids.java 'Mob raider = Colonies.raiderOf(event.getEntity());' \
  'AbstractEntityMinecolonies x = null;
        Mob raider = Colonies.raiderOf(event.getEntity());'
expect "a handler speaking MineColonies' own types" "speaks in Settlements"

echo "additive only"
edit $S/event/ColonyRaids.java 'ElysiumElites.promote(raider, ColonyRules.raiderTier(tier));' \
  'raider.setColony(null); ElysiumElites.promote(raider, ColonyRules.raiderTier(tier));'
expect "an add-on writing back into its host" "writes nothing back"

edit $S/event/ColonyRaids.java 'if (!ColonyRules.promotes(tier)) {' \
  'raider.setHealth(raider.getMaxHealth() * 2);
        if (!ColonyRules.promotes(tier)) {'
expect "one difficulty curve multiplied by another" "neither author would recognise"

echo "a town is progress"
edit $S/event/ColonyProgress.java 'ColonyRules.worldFloor(sizes)' 'largest / 5'
expect "a colony's tier decided outside the rules" "harness proves nothing about it"

edit $S/event/ColonyProgress.java 'if (ElysiumTiers.lift(player, floor)) {' \
  'if (floor >= 0) { player.setData(ElysiumTiers.WORLD_FLOOR.get(), floor);'
expect "progress a raid can take away" "cost them their progress"

edit $S/event/ColonyProgress.java 'if (player.tickCount % INTERVAL != 0) {
            return;
        }' ''
expect "a colony asked on every tick of every player" "moves into a hut"

edit $S/event/ColonyProgress.java 'if (!(event.getEntity() instanceof ServerPlayer player)) {' \
                                  'if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player player)) {'
expect "a colony read on the client" "different numbers on it"

echo "a town is conspicuous"
edit $S/event/ColonyStanding.java 'ElysiumStanding.addFloorSource(player -> {
            java.util.List<Colonies.Settlement> towns = Colonies.memberOf(player);
            if (towns.isEmpty()) {
                return 0;
            }
            java.util.List<Integer> sizes = new java.util.ArrayList<>();
            for (Colonies.Settlement town : towns) {
                sizes.add(town.citizens());
            }
            return ColonyRules.noticeFloor(sizes);
        });' ''
expect "a town of thirty quietly forgotten" "quietly forgotten"

edit $S/event/ColonyStanding.java 'ColonyRules.noticeFloor(sizes)' 'sizes.size() * 2'
expect "how noticed a settlement is, decided outside the rules" "decided outside the rules"

edit $S/ElysiumColonies.java 'com.elysium.colonies.event.ColonyStanding.install();' ''
expect "a source written and never installed" "a class nobody calls"

echo "a town is worth raiding properly"
edit $S/event/ColonyRaids.java 'ColonyRules.raiderTier(tier)' 'tier'
expect "raiders promoted at a tier nobody capped" "level-five guards"

edit $S/event/ColonyRaids.java 'if (!ColonyRules.promotes(tier)) {' 'if (false) {'
expect "a new player's first raid already promoted" "anything to meet about"

edit $S/event/ColonyRaids.java 'if (ElysiumElites.decided(raider)) {
            // Already looked at. This is the common case by a very long way,
            // so it is the first thing asked after the type check.
            return;
        }' ''
expect "a raider examined on every tick of its life" "rather than once"

edit $S/event/ColonyRaids.java 'public static void onTick(EntityTickEvent.Post event) {' \
  'public static void onTick(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {'
expect "a raider promoted before it was finished" "before MineColonies has told them"

echo "the arithmetic"
edit $S/rules/ColonyRules.java 'return Math.max(0, Math.min(MAX_RAIDER_TIER, ownerTier));' \
                               'return Math.max(0, ownerTier);'
expect "a raid that scales without limit (harness)" "walks through a town"

edit $S/rules/ColonyRules.java 'public static final int MAX_COLONY_TIER = 6;' \
                               'public static final int MAX_COLONY_TIER = 600;'
expect "a colony worth more tiers than an adventurer can reach" "fastest road to the"

edit $S/rules/ColonyRules.java 'public final class ColonyRules {' \
  'public final class ColonyRules {
    static final Class<?> REACH = com.elysium.lib.tier.ElysiumTiers.class;'
expect "rules reaching outside the JDK" "the JDK and nothing else"

echo "what it declares"
edit $R/META-INF/neoforge.mods.toml 'modId="minecolonies"
    type="required"' 'modId="minecolonies"
    type="optional"'
expect "an add-on whose host is optional" "loads and does nothing"

edit $R/META-INF/neoforge.mods.toml 'modId="elysiumlib"
    type="required"' 'modId="elysiumlib"
    type="optional"'
expect "the engine made optional" "written in its vocabulary"

python3 -c "
import json
p='$R/assets/elysiumcolonies/lang/en_us.json'
json.dump({},open(p,'w'),indent=2)"
expect "the moment a town raises a tier, as a raw key" "reads a raw key"

echo
echo "restoring and re-checking"
restore
if (cd $M && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
echo "colonies: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
