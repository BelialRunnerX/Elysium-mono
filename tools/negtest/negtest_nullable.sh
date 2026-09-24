#!/usr/bin/env bash
# The nullable check, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
SNAP=/tmp/nullable_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP check_nullable.py \
    elysium-spawners/src/main/java/com/elysium/spawners/event/SpawnerBehaviour.java \
    elysium-lib/src/main/java/com/elysium/lib/event/ElysiumStandingHandler.java)
restore() { (cd $ROOT && tar xf $SNAP); rm -f $ROOT/elysium-lib/src/main/java/com/elysium/lib/Probe.java; }
out() { (cd $ROOT && timeout 900 python3 check_nullable.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
reject() { local g; g="$(out)"
  if [[ "$g" == *"nothing chains off one"* ]]; then echo "  allowed: $1"; pass=$((pass+1));
  else echo "  FALSE POSITIVE : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

S=$ROOT/elysium-spawners/src/main/java/com/elysium/spawners/event/SpawnerBehaviour.java
PROBE=$ROOT/elysium-lib/src/main/java/com/elysium/lib/Probe.java

echo "the line that stopped a world at 51%"
edit $S '        Either<BlockEntity, Entity> spawner = event.getSpawner();
        if (spawner == null) {
            return null;
        }
        BlockEntity entity = spawner.left().orElse(null);' \
        '        BlockEntity entity = event.getSpawner().left().orElse(null);'
expect "the chain off getSpawner() put back" "calls straight off event.getSpawner()"

echo "the same shape elsewhere"
cat > $PROBE <<'JAVA'
package com.elysium.lib;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class Probe {

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent placing) {
        String name = placing.getEntity().getName().getString();
    }
}
JAVA
expect "a chain off EntityPlaceEvent.getEntity(), which has no entity for a piston" \
       "on a EntityPlaceEvent"

echo "things that must not be flagged"
cat > $PROBE <<'JAVA'
package com.elysium.lib;

import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class Probe {

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent placing) {
        Entity who = placing.getEntity();
        if (who == null) {
            return;
        }
        String name = who.getName().getString();
    }
}
JAVA
reject "the same getter put in a local and checked"

cat > $PROBE <<'JAVA'
package com.elysium.lib;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class Probe {

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent dying) {
        boolean client = dying.getEntity().level().isClientSide();
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent placing) {
        int x = placing.getPos().getX();
    }
}
JAVA
reject "getEntity() on LivingDeathEvent, where it is not nullable, beside an event where it is"

echo "the check switched off"
edit $ROOT/check_nullable.py '        if current and stripped == "org.jetbrains.annotations.Nullable":' \
                             '        if False:'
edit $S '        Either<BlockEntity, Entity> spawner = event.getSpawner();
        if (spawner == null) {
            return null;
        }
        BlockEntity entity = spawner.left().orElse(null);' \
        '        BlockEntity entity = event.getSpawner().left().orElse(null);'
g="$(out)"
if [[ "$g" == *"nothing chains off one"* ]]; then
  echo "  caught : the jar no longer asked which getters return null"; pass=$((pass+1))
else
  echo "  MISSED : the jar no longer asked which getters return null"; fail=$((fail+1))
fi
restore

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"nothing chains off one"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "nullable: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
