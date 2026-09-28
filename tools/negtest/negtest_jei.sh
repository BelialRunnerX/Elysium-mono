#!/usr/bin/env bash
# The recipe browser that is not a dependency, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
L=$ROOT/elysium-lib
F=$ROOT/elysium-foundry
PLUGIN=$L/src/main/java/com/elysium/lib/compat/jei/ElysiumJeiPlugin.java
MANIFEST=$L/src/main/resources/META-INF/neoforge.mods.toml
SNAP=/tmp/jei_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-lib elysium-foundry)
restore() { rm -rf $ROOT/elysium-lib $ROOT/elysium-foundry && (cd $ROOT && tar xf $SNAP); }
out() { (cd $1 && timeout 1800 python3 validate.py 2>&1); }
expect() { local m="$1" w="$3" g; g="$(out $m)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $2"; pass=$((pass+1));
  else echo "  MISSED : $2"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the library's plugin"
rm -f $PLUGIN
expect $L "no plugin at all" "there is no compat/jei/ElysiumJeiPlugin.java"

edit $PLUGIN '@JeiPlugin' '// unannotated'
expect $L "a plugin JEI never loads" "JEI never loads it"

edit $PLUGIN '            registration.addRecipeCatalysts(jeiType(kind), kind.catalyst().get());' '            // no catalysts'
expect $L "a plugin that forgot the machines" "the machine that runs each kind"

edit $MANIFEST '    modId="jei"
    type="optional"' '    modId="jei"
    type="required"'
expect $L "JEI made a hard dependency in the manifest" "it is to be optional"

edit $L/src/main/java/com/elysium/lib/sound/ElysiumVoices.java 'package com.elysium.lib.sound;' 'package com.elysium.lib.sound;

import mezz.jei.api.IModPlugin;'
expect $L "a class outside compat/jei naming JEI" "name JEI"

echo "a module's kinds"
edit $F/src/main/java/com/elysium/foundry/ElysiumFoundry.java '        ElysiumRecipeKinds.register(ResourceLocation.fromNamespaceAndPath(MODID, "refining"),' '        if (false) ElysiumRecipeKinds.register(ResourceLocation.fromNamespaceAndPath(MODID, "refinng"),'
expect $F "a kind the module never hands over" "never handed to ElysiumRecipeKinds"

echo "restoring and re-checking"
restore
if out $L | grep -q "problem"; then echo "  RESTORE FAILED (lib)"; fail=$((fail+1)); else echo "  lib restored clean"; fi
rm -f $SNAP
echo "jei: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
