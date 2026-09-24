#!/bin/bash
cd /tmp/Elysium-mono/elysium-armory || exit 2
export JAVA_TOOL_OPTIONS=
pass=0; failn=0
t() {
    cp "$1" /tmp/nb_p3
    python3 - "$1" "$2" "$3" <<'PY'
import sys; p,o,n=sys.argv[1:]; s=open(p).read(); assert o in s, "pattern missing: "+o; open(p,'w').write(s.replace(o,n,1))
PY
    out=$(timeout 600 python3 validate.py 2>&1)
    cp /tmp/nb_p3 "$1"
    if grep -q "$4" <<<"$out"; then pass=$((pass+1)); echo "ok   : $4"; else failn=$((failn+1)); echo "MISS : $4"; fi
}
S=src/main/java/com/elysium/armory
t $S/registry/ArmoryItems.java '"katana", "machete"' '"machete"' "gear items and the gear types"
t $S/registry/ArmoryItems.java '"rod", "tip", "binding"' '"rod", "binding"' "part items and the part files"
t $S/ElysiumArmory.java 'ArmoryRecipes.register(modEventBus);' '' "ArmoryRecipes is never put on the mod bus"
t $S/client/ArmoryClient.java 'ArmorySmithingScreen::new' 'null' "bench has no screen registered"
L=src/main/resources/assets/elysiumarmory/lang/en_us.json
t $L '"item.elysiumarmory.sword":' '"item.elysiumarmory.sworb":' "item sword has no name"
python3 - <<'PY'
from PIL import Image
p='src/main/resources/assets/elysiumarmory/textures/item/sword.png'
im=Image.open(p).convert('RGBA'); im.save('/tmp/nb_p3_sword.png')
px=im.load(); px[8,8]=(200,40,40,255); im.save(p)
PY
out=$(timeout 600 python3 validate.py 2>&1); cp /tmp/nb_p3_sword.png src/main/resources/assets/elysiumarmory/textures/item/sword.png
grep -q "carries a colour of its own" <<<"$out" && { pass=$((pass+1)); echo "ok   : coloured sprite"; } || { failn=$((failn+1)); echo "MISS : coloured sprite"; }
echo "$pass passed, $failn missed"
