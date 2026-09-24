#!/bin/bash
cd /tmp/Elysium-mono || exit 1
exec 9>/tmp/negtest_suit.lock; flock -n 9 || { echo busy; exit 2; }
J=elysium-space/src/main/java/com/elysium/space; A=elysium-space/src/main/resources/assets/elysiumspace
SUIT=$J/item/SpaceSuitItem.java; EV=$J/event/SuitEvents.java; SA=$J/world/SuitAir.java
L1=$A/textures/models/armor/space_suit_layer_1.png; LANG=$A/lang/en_us.json
FILES="$SUIT $EV $SA $L1 $LANG"
pass=0; miss=0
snap(){ rm -rf /tmp/_su; mkdir -p /tmp/_su; i=0; for f in $FILES; do cp "$f" /tmp/_su/$i; i=$((i+1)); done; }
restore(){ i=0; for f in $FILES; do cp /tmp/_su/$i "$f"; i=$((i+1)); done; }
python3 elysium-space/validate.py >/dev/null 2>&1 || { echo "SETUP FAILED"; exit 2; }; echo "baseline clean"
try(){ snap; if ! python3 -c "$3"; then echo "SETUP FAILED: $1"; restore; return; fi
  if python3 elysium-space/validate.py 2>&1 | grep -qF "$2"; then echo "CAUGHT : $1"; pass=$((pass+1)); else echo "MISSED : $1 ($2)"; miss=$((miss+1)); fi; restore; }
R='
import sys, json, os
def rep(p, a, b):
    s=open(p).read()
    if a not in s: sys.exit("anchor: "+a[:50])
    open(p,"w").write(s.replace(a,b,1))
'
try "suit wrong element" "is not vacuum's" "$R
rep('$SUIT', 'return ElysiumElements.VOID;', 'return ElysiumElements.PLASMA;')"
try "exposure decided locally" "decides exposure itself" "$R
rep('$EV', 'ElysiumHazardField.intensity(player.level(), pos, SpaceHazards.VACUUM) > 0.0F', 'com.elysium.space.world.SpaceWorlds.of(player.level()) != null')"
try "drain rule bypassed" "does not use SuitAir's tested rule" "$R
rep('$EV', 'int after = SuitAir.step(before, exposed, inRoom);', 'int after = before - 5;')"
try "suit lasts too little" "WorldHarness failed" "$R
rep('$SA', 'DRAIN_PER_SECOND = 5;', 'DRAIN_PER_SECOND = 50;')"
try "room does not refill" "WorldHarness failed" "$R
rep('$SA', 'return clamp(now + REFILL_PER_SECOND);', 'return now;')"
try "suffocate with air left" "WorldHarness failed" "$R
rep('$SA', 'return exposed && !inFilledRoom && clamp(air) == 0;', 'return exposed && !inFilledRoom && clamp(air) < 100;')"
try "worn texture missing" "worn suit texture" "
import os; os.remove('$L1')"
try "death message missing" "dies with the raw key" "$R
d=json.load(open('$LANG')); del d['death.attack.elysiumspace.asphyxiation.player']; json.dump(d,open('$LANG','w'))"
echo "caught $pass, missed $miss"
