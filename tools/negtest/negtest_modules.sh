#!/bin/bash
# Negative tests for elysium-armory, elysium-ascendancy and the hotbar.
cd /tmp/Elysium-mono || exit 1
exec 9>/tmp/negtest_modules.lock
flock -n 9 || { echo "another negtest_modules is running; refusing"; exit 2; }
A=elysium-armory/src/main/java/com/elysium/armory
S=elysium-ascendancy/src/main/java/com/elysium/ascendancy
AC=$A/property/ArmoryCompute.java
AM=$A/ElysiumArmory.java
AT=elysium-armory/src/main/resources/META-INF/neoforge.mods.toml
TW=$S/weight/TieredWeight.java
WP=$S/weight/WeightedPick.java
SM=$S/ElysiumAscendancy.java
HB=elysium-lib/src/main/resources/assets/minecraft/textures/gui/sprites/hud/hotbar.png
HS=elysium-lib/src/main/resources/assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png
FILES="$AC $AM $AT $TW $WP $SM $HB $HS"
pass=0; miss=0
snap()    { rm -rf /tmp/_nm; mkdir -p /tmp/_nm; for f in $FILES; do cp "$f" "/tmp/_nm/$(basename $f)"; done; }
restore() { for f in $FILES; do cp "/tmp/_nm/$(basename $f)" "$f"; done; rm -f $A/property/Leak.java; }
for v in armory ascendancy lib; do
  timeout 1700 python3 elysium-$v/validate.py >/dev/null 2>&1 || { echo "SETUP FAILED: $v not clean"; exit 2; }
done
echo "baseline clean"
try() { local label="$1" which="$2" want="$3" edit="$4"; snap
  if ! python3 -c "$edit"; then echo "SETUP FAILED: $label"; restore; return; fi
  if timeout 1700 python3 elysium-$which/validate.py 2>&1 | grep -qF "$want"; then echo "CAUGHT : $label"; pass=$((pass+1))
  else echo "MISSED : $label  (expected: $want)"; miss=$((miss+1)); fi
  restore; }
R='
import sys
def rep(path, old, new):
    s = open(path, encoding="utf-8").read()
    if old not in s: sys.exit("anchor not found in " + path + ": " + old[:60])
    open(path, "w", encoding="utf-8").write(s.replace(old, new, 1))
'
# independence
try "armory names Silent Gear" armory "refers to" "$R
rep('$AM', '    public static final Logger LOGGER', '    static final boolean SG = net.neoforged.fml.ModList.get().isLoaded(\\\"silentgear\\\");\n    public static final Logger LOGGER')"
try "armory imports core" armory "imports com.elysium.core" "$R
rep('$AM', 'import org.slf4j.Logger;', 'import org.slf4j.Logger;\nimport com.elysium.core.Elysium;')"
try "armory depends on silentgear" armory "declares a dependency on" "$R
s=open('$AT').read(); open('$AT','w').write(s+'\n[[dependencies.\${mod_id}]]\n    modId=\"silentgear\"\n    type=\"required\"\n')"
try "ascendancy names Apotheosis" ascendancy "refers to" "$R
rep('$SM', '    public static final Logger LOGGER', '    static final boolean AP = net.neoforged.fml.ModList.get().isLoaded(\\\"apotheosis\\\");\n    public static final Logger LOGGER')"
# purity
try "armory engine imports minecraft" armory "the engine imports" "$R
rep('$AC', 'package com.elysium.armory.property;', 'package com.elysium.armory.property;\nimport net.minecraft.util.Mth;')"
try "ascendancy engine imports minecraft" ascendancy "the engine imports" "$R
rep('$WP', 'import java.util.List;', 'import java.util.List;\nimport net.minecraft.util.RandomSource;')"
# engine behaviour
try "armory ceiling reintroduced" armory "PropertyHarness failed" "$R
rep('$AC', '        return Math.max(r, floor);\n    }', '        return Math.min(Math.max(r, floor), 1000.0D);\n    }')"
try "armory multiply_total before base" armory "PropertyHarness failed" "$R
rep('$AC', '            r *= 1.0D + v;', '            r *= 1.0D + v * 0.5D;')"
try "armory floor dropped" armory "PropertyHarness failed" "$R
rep('$AC', '        return Math.max(r, floor);\n    }', '        return r;\n    }')"
try "ascendancy luck ignored" ascendancy "WeightHarness failed" "$R
rep('$TW', 'Math.round(l * q)', '0L')"
# hotbar
try "hotbar wrong size" lib "vanilla's is" "
from PIL import Image
im=Image.open('$HB').convert('RGBA'); im.crop((0,0,180,22)).save('$HB')"
try "hotbar off-palette" lib "not in ElysiumPalette" "
from PIL import Image
im=Image.open('$HB').convert('RGBA'); im.putpixel((0,0),(1,2,3,255)); im.save('$HB')"
try "selection covers item" lib "paints over the item" "
from PIL import Image
im=Image.open('$HS').convert('RGBA'); im.putpixel((12,12),im.getpixel((0,0)) if im.getpixel((0,0))[3] else im.getpixel((1,1))); im.save('$HS')"
echo "caught $pass, missed $miss"
