#!/usr/bin/env bash
# The gear convergence, broken one way at a time.
#
# The check that matters most is the first: Elysium must build and run with
# neither Silent Gear nor Apotheosis installed, and that is invisible here
# because both jars are on the compile classpath.
set -uo pipefail
cd /tmp/Elysium-mono/elysium-core
C=src/main/java/com/elysium/core/compat
R=src/main/resources
pass=0; fail=0

out() { python3 validate.py 2>&1; }
expect() { local n="$1" w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $n"; pass=$((pass+1));
  else echo "  MISSED : $n"; fail=$((fail+1)); fi; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "the guard that keeps the mod loadable"
# The exact mistake FIXES.md records: a foreign import on an always-loaded class.
cp $C/GearConvergence.java /tmp/gc.bak
edit $C/GearConvergence.java 'import net.neoforged.fml.ModList;' \
  'import net.neoforged.fml.ModList;
import dev.shadowsoffire.apotheosis.affix.AffixHelper;'
expect "a foreign import on the dispatcher" "GearConvergence names dev.shadowsoffire"
cp /tmp/gc.bak $C/GearConvergence.java

# The subtler version: a bridge named in a method that never asks ModList.
cp $C/GearConvergence.java /tmp/gc.bak
edit $C/GearConvergence.java '    public static Direction directionFor(ItemStack stack, boolean silentTemplate) {' \
  '    public static boolean peek(ItemStack stack) {
        return SilentGearBridge.isSilentGear(stack);
    }

    public static Direction directionFor(ItemStack stack, boolean silentTemplate) {'
expect "a bridge called without a ModList check" "without asking ModList first"
cp /tmp/gc.bak $C/GearConvergence.java

# A foreign import anywhere outside compat/.
cp src/main/java/com/elysium/core/item/ElysiumReforgeHandler.java /tmp/rh.bak
edit src/main/java/com/elysium/core/item/ElysiumReforgeHandler.java \
  'import net.minecraft.util.RandomSource;' \
  'import net.minecraft.util.RandomSource;
import net.silentchaos512.gear.util.GearData;'
expect "a foreign import outside compat/" "outside compat/"
cp /tmp/rh.bak src/main/java/com/elysium/core/item/ElysiumReforgeHandler.java

# A foreign import on a compat file that is not a bridge.
cp $C/ConvergenceComponents.java /tmp/cc.bak
edit $C/ConvergenceComponents.java 'import net.minecraft.resources.ResourceLocation;' \
  'import net.minecraft.resources.ResourceLocation;
import net.silentchaos512.gear.util.GearData;'
expect "a foreign import on a non-bridge compat file" "Only ['ApotheosisBridge.java', 'SilentGearBridge.java']"
cp /tmp/cc.bak $C/ConvergenceComponents.java

# The validator run with ELYSIUM_OUT pointed elsewhere for one run, so the
# real build is not touched.
env_expect() { local n="$1" w="$2" g; shift 2; g="$(env "$@" python3 validate.py 2>&1)"
  if [[ "$g" == *"PROBLEM(S)"* && "$g" == *"$w"* ]]; then echo "  caught : $n"; pass=$((pass+1));
  else echo "  MISSED : $n"; fail=$((fail+1)); fi; }

# The leak the source scan cannot see: a foreign type reaching the class file
# through an inferred local, with no import line anywhere.
#
# Built into a folder of its own and scanned there (16 October 2026). It was
# compiled into the real build and compiled back afterwards, both with the
# output thrown away: the classes every other check reads were a broken
# module's in between, a build that failed was a case that missed with nothing
# to say why, and under parallel.py the worker's classes changed.
echo "the bytecode guard"
LEAK_OUT=/tmp/conv_leak_classes
cp $C/GearConvergence.java /tmp/gc.bak
edit $C/GearConvergence.java '        return switch (direction) {' \
  '        var leaked = new dev.shadowsoffire.apotheosis.socket.SocketedGems[0];
        if (leaked.length > 0) {
            return ItemStack.EMPTY;
        }
        return switch (direction) {'
rm -rf $LEAK_OUT
# compile.sh joins its classpath with ':' and wants POSIX spellings, where the
# validators are handed Windows ones (tools/setup_env.py prints both sets).
# Run with the validators' set, javac lost the compat jars (the colonies
# failed on every MineColonies type) and the core was not built, so the case
# missed (16 October 2026).
COMPILE_ENV=$(cd /tmp/Elysium-mono && python3 -c "
import os, sys
sys.path.insert(0, 'tools')
if os.name == 'nt':
    import setup_env as s
    print('ELYSIUM_MC_LIBS=%s ELYSIUM_ANNOTATIONS=%s ELYSIUM_COMPAT_LIBS=%s'
          % (s.posix(s.MC), s.posix(s.ANNOTATIONS), s.posix(s.MODS)))
")
( cd /tmp/Elysium-mono && env $COMPILE_ENV ELYSIUM_OUT=$LEAK_OUT ./compile.sh >/tmp/conv_leak_compile.log 2>&1 )
if grep -q '^ok    elysium-core ' /tmp/conv_leak_compile.log; then
  env_expect "a foreign type with no import line" "in its bytecode" ELYSIUM_OUT=$LEAK_OUT
else
  echo "SETUP FAILED: the core did not compile with the leak in it:"
  grep -v '^ok    ' /tmp/conv_leak_compile.log | tail -12 | sed 's/^/    /'
  fail=$((fail+1))
fi
cp /tmp/gc.bak $C/GearConvergence.java
rm -rf $LEAK_OUT

# Nothing compiled to scan (15 Oct 2026). Until 15 October no build printed
# "bytecode not scanned" and the module passed with the guard above never run.
# Now no build, and a build folder with nothing in it (compile.sh empties its
# output rather than removing it), each FAIL and name ELYSIUM_OUT.
NOCLASSES=$(mktemp -d)
env_expect "no compiled classes, which used to print not scanned and pass" \
  "the core's compiled classes are not at" ELYSIUM_OUT="$NOCLASSES/nothing"
mkdir "$NOCLASSES/elysium-core"
env_expect "an emptied build folder, which would have scanned 0 classes and passed" \
  "holds no compiled class under com/elysium/core" ELYSIUM_OUT="$NOCLASSES"
rmdir "$NOCLASSES/elysium-core" "$NOCLASSES"

echo "the manifest"
cp $R/META-INF/neoforge.mods.toml /tmp/toml.bak
python3 -c "
p='src/main/resources/META-INF/neoforge.mods.toml'
s=open(p, encoding='utf-8', newline='').read()
i=s.find('modId=\"silentgear\"')
# turn the whole surrounding block's optional flag into a required one
j=s.rfind('[[dependencies', 0, i)
k=s.find('[[dependencies', i)
k=len(s) if k<0 else k
block=s[j:k].replace('optional','required')
open(p, 'w', encoding='utf-8', newline='').write(s[:j]+block+s[k:])
"
expect "a required dependency on an optional mod" "not optional"
cp /tmp/toml.bak $R/META-INF/neoforge.mods.toml

echo "the recipe and its serializer"
cp $R/data/elysium/recipe/convergence.json /tmp/cj.bak
edit $R/data/elysium/recipe/convergence.json '"elysium:convergence"' '"elysium:convergance"'
expect "a recipe type nothing registers" "the recipe fails to load"
cp /tmp/cj.bak $R/data/elysium/recipe/convergence.json

rm -f $R/data/elysium/recipe/convergence.json
expect "no convergence recipe at all" "the smithing table does nothing"
cp /tmp/cj.bak $R/data/elysium/recipe/convergence.json

echo "the templates"
cp $R/data/elysium/recipe/silent_convergence_template.json /tmp/st.bak
rm -f $R/data/elysium/recipe/silent_convergence_template.json
expect "an uncraftable template" "unreachable in survival"
cp /tmp/st.bak $R/data/elysium/recipe/silent_convergence_template.json

echo "every shape has somewhere to go"
cp $C/SilentGearBridge.java /tmp/sb.bak
edit $C/SilentGearBridge.java 'ElysiumMaterialGear.Shape.SPEAR, new String[] {"spear", "spear_tip"}));' \
  'ElysiumMaterialGear.Shape.PAXEL_PLACEHOLDER, new String[] {"paxel", "paxel_head"}));'
expect "a shape with no counterpart" "has no Silent Gear counterpart"
cp /tmp/sb.bak $C/SilentGearBridge.java

cp $C/SilentGearBridge.java /tmp/sb.bak
edit $C/SilentGearBridge.java 'ArmorItem.Type.BOOTS, new String[] {"boots", "boot_plates"}));' \
  'ArmorItem.Type.HELMET, new String[] {"boots", "boot_plates"}));'
expect "an armour slot with no counterpart" "BOOTS has no Silent Gear counterpart"
cp /tmp/sb.bak $C/SilentGearBridge.java

echo
echo "negative tests: $pass caught, $fail missed"
python3 validate.py >/dev/null 2>&1 && echo "restored clean" || echo "RESTORE FAILED"
[ "$fail" -eq 0 ]
