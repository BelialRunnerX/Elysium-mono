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

echo "the classpath itself (15 Oct 2026)"
# From late September this check split cp.txt on ":" itself, read "C:/..." as
# two entries that do not exist, and printed "0 listener(s) checked ... (277
# not in the jars, skipped)" as a pass. Each way of having no jars to ask must
# now FAIL and name what is missing. Every cp.txt below is written in a scratch
# folder handed over as ELYSIUM_MC_LIBS; the real one is never touched.
CPD=$(mktemp -d)
cp_out() { (cd $ROOT && ELYSIUM_MC_LIBS="$CPD" timeout 600 python3 check_buses.py 2>&1); }
cp_expect() { local w="$2" g; g="$(cp_out)"
  if [[ "$g" == *"FAILED"* && "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
cp_write() { python3 - "$CPD" "$1" "$ROOT/tools" <<'PYX'
import os, re, sys, zipfile
folder, how, tools = sys.argv[1], sys.argv[2], sys.argv[3]
sys.path.insert(0, tools)
import mcpath
target = os.path.join(folder, "cp.txt")
if how == "none":
    if os.path.exists(target):
        os.remove(target)
    sys.exit(0)
if how == "absent":
    text = "/nowhere/neoforge.jar:/nowhere/loader.jar"
elif how == "stranger":
    jar = os.path.join(folder, "stranger.jar")
    with zipfile.ZipFile(jar, "w") as out:
        out.writestr("stranger/Nothing.txt", "not the game")
    text = jar
elif how == "posix":
    real = mcpath.classpath()
    if not real:
        sys.exit("the real classpath is empty; this case needs it")
    text = ":".join("/" + p[0].lower() + p[2:] if re.match(r"^[A-Za-z]:", p) else p
                    for p in real)
open(target, "w", encoding="utf-8", newline=chr(10)).write(text + chr(10))
PYX
}
cp_write none
cp_expect "no cp.txt at all, which used to print skipped and pass" "there is no classpath file at"
cp_write absent
cp_expect "a cp.txt naming jars that are not here" "none of them exists here"
cp_write stranger
cp_expect "a cp.txt naming a real jar that is not the game's" "javap cannot find net.neoforged.fml.event.IModBusEvent"

cp_write posix
g="$(cp_out)"
if [[ "$g" == *"every one on the bus its event is fired on"* && "$g" != *" 0 listener(s)"* ]]; then
  echo "  allowed: the colon-joined POSIX cp.txt, read through mcpath"; pass=$((pass+1))
else
  echo "  FALSE POSITIVE : the colon-joined POSIX cp.txt, read through mcpath"; fail=$((fail+1))
fi
restore

case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*)
  # The September bug itself, which only Windows can show: the POSIX file
  # handed to javap whole instead of through mcpath.
  edit $ROOT/check_buses.py 'CLASSPATH = mcpath.join(mcpath.classpath(CLASSPATH_FILE))' \
    'CLASSPATH = open(CLASSPATH_FILE, encoding="utf-8").read().strip()'
  cp_expect "the POSIX cp.txt handed to javap unread, as from September to 15 October" \
    "javap cannot find net.neoforged.fml.event.IModBusEvent" ;;
esac

# The guard under the per-event one: with that switched off and every event
# answering "not in the jars", 0 of N checked must still fail on its own.
edit $ROOT/check_buses.py '        if fqcn.startswith(("net.neoforged.", "net.minecraft.")):' \
  '        if False:'
edit $ROOT/check_buses.py 'def on_mod_bus(fqcn, seen=()):' 'def on_mod_bus(fqcn, seen=()):
    return None'
g="$(out)"
if [[ "$g" == *"FAILED"* && "$g" == *"not one of them checked against"* ]]; then
  echo "  caught : the jars answering for the marker and for no event, 0 of N checked"; pass=$((pass+1))
else
  echo "  MISSED : the jars answering for the marker and for no event, 0 of N checked"; fail=$((fail+1))
fi
restore

edit $ROOT/check_buses.py 'def on_mod_bus(fqcn, seen=()):' 'def on_mod_bus(fqcn, seen=()):
    if fqcn.endswith("PlayerTickEvent$Post"):
        return None'
expect "one of the game's own events not found in the jars, which used to be skipped" \
  "which is not on the classpath in"
rm -rf "$CPD"

echo
echo "restoring and re-checking"
restore
if out | grep -q "every one on the bus its event is fired on"; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "buses: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
