#!/usr/bin/env bash
# The event buses, broken one way at a time.
#
# The first break is the crash we actually shipped, put back exactly as it was.
# A check written after an incident that cannot reproduce the incident is a
# check that was written about something else.
set -uo pipefail
ROOT=/tmp/Elysium-mono
SNAP=/tmp/buses_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP check_buses.py \
    elysium-lib/src/main/java/com/elysium/lib/combat/ElysiumPotions.java \
    elysium-lib/src/main/java/com/elysium/lib/event/ElysiumCraftHandler.java \
    elysium-space/src/main/java/com/elysium/space/client/SpaceKeys.java \
    elysium-lib/src/main/java/com/elysium/lib/event)
restore() { (cd $ROOT && tar xf $SNAP); }
out() { (cd $ROOT && timeout 600 python3 check_buses.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:60] + '\n')
    sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

LIB=$ROOT/elysium-lib/src/main/java/com/elysium/lib
SPACE=$ROOT/elysium-space/src/main/java/com/elysium/space

echo "the crash we shipped"
edit $LIB/combat/ElysiumPotions.java 'NeoForge.EVENT_BUS.addListener(ElysiumPotions::onBrewing);' \
  'modBus.addListener(ElysiumPotions::onBrewing);'
expect "RegisterBrewingRecipesEvent put back on the mod bus" \
  "RegisterBrewingRecipesEvent is a game-bus event"

echo "the other direction"
edit $SPACE/client/SpaceKeys.java \
  '@EventBusSubscriber(modid = ElysiumSpace.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {' \
  '@EventBusSubscriber(modid = ElysiumSpace.MODID, value = Dist.CLIENT)
    public static final class Registration {'
expect "key mappings registered on the game bus, where nothing fires them" \
  "RegisterKeyMappingsEvent is a mod-bus event"

echo "a nested event, which is most of them"
edit $LIB/event/ElysiumAttributeCombat.java \
  '@EventBusSubscriber(modid = ElysiumLib.MODID)' \
  '@EventBusSubscriber(modid = ElysiumLib.MODID, bus = EventBusSubscriber.Bus.MOD)'
expect "PlayerTickEvent.Post, an inner class, on the mod bus" "is a game-bus event"

echo "two buses in one file"
edit $ROOT/check_buses.py '        subscriber = annotating(body, match.start())' \
  '        subscriber = SUBSCRIBER.search(body)'
g="$(out)"
if [[ "$g" == *"ClientTickEvent"* ]]; then
  echo "  caught : one file's first annotation applied to every class in it"; pass=$((pass+1))
else
  echo "  MISSED : one file's first annotation applied to every class in it"; fail=$((fail+1))
fi
restore

echo "the resolver reaching the jars at all"
edit $ROOT/check_buses.py '    head, *nested = name.split(".")' \
  '    head, nested = name, []'
g="$(out)"
if [[ "$g" == *"not in the jars, skipped"* ]]; then
  echo "  caught : nested events silently skipped instead of checked"; pass=$((pass+1))
else
  echo "  MISSED : nested events silently skipped instead of checked"; fail=$((fail+1))
fi
restore

edit $ROOT/check_buses.py '    if MARKER in line:
        return True' '    if MARKER in line:
        return True
    return False' 
g="$(out)"
if [[ "$g" == *"is a mod-bus event"* || "$g" == *"is a game-bus event"* ]]; then
  echo "  caught : a marker inherited from a superclass, not looked for"; pass=$((pass+1))
else
  echo "  MISSED : a marker inherited from a superclass, not looked for"; fail=$((fail+1))
fi
restore

echo
echo "restoring and re-checking"
restore
if out | grep -q "every one on the bus its event is fired on"; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "buses: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
