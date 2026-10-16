#!/bin/bash
cd /tmp/Elysium-mono || exit 1
exec 9>/tmp/negtest_medical.lock
negtest_lock() { if command -v flock >/dev/null 2>&1; then flock -n "$1"; else mkdir "$2" 2>/dev/null && trap "rmdir '$2' 2>/dev/null" EXIT; fi; }  # Git Bash on Windows has no flock
negtest_lock 9 /tmp/negtest_medical.lockdir || { echo "another negtest_medical is running; refusing"; exit 2; }
L=elysium-lib/src/main/java/com/elysium/lib
H=$L/event/ElysiumMedicalHandler.java
M=$L/medical/ElysiumMedical.java
K=$L/medical/ElysiumWoundKind.java
D=$L/medical/ElysiumMedicine.java
I=$L/medical/ElysiumInjury.java
R=$L/room/ElysiumRooms.java
T=elysium-lib/src/main/resources/data/elysiumlib/tags/block/room/medical.json
FILES="$H $M $K $D $I $R $T"
pass=0; miss=0
snap()    { rm -rf /tmp/_md; mkdir -p /tmp/_md; for f in $FILES; do cp "$f" "/tmp/_md/$(basename $f)"; done; }
restore() { for f in $FILES; do cp "/tmp/_md/$(basename $f)" "$f"; done; }
if ! timeout 1700 python3 elysium-lib/validate.py >/dev/null 2>&1; then echo "SETUP FAILED: tree not clean"; exit 2; fi
echo "baseline clean"
try() {
  local label="$1" want="$2" edit="$3"
  snap
  if ! python3 -c "$edit"; then echo "SETUP FAILED: $label"; miss=$((miss+1)); restore; return; fi
  if timeout 1700 python3 elysium-lib/validate.py 2>&1 | grep -qF "$want"; then
    echo "CAUGHT : $label"; pass=$((pass+1))
  else
    echo "MISSED : $label  (expected: $want)"; miss=$((miss+1))
  fi
  restore
}
R_='
import sys
def rep(path, old, new):
    s = open(path, encoding="utf-8").read()
    if old not in s:
        sys.exit("anchor not found in " + path + ": " + old[:60])
    open(path, "w", encoding="utf-8").write(s.replace(old, new, 1))
'
try "rolled on the swing" "rolled on the blow as swung" "$R_
rep('$H', 'event.getNewDamage()', 'event.getOriginalDamage()')"
try "creative wounded" "creative players can be wounded" "$R_
rep('$H', '|| player.isCreative() || player.isSpectator()', '|| player.isSpectator()')"
try "fire arrows cut" "tests for a projectile before fire" "$R_
rep('$H', 'if (source.is(DamageTypeTags.IS_FIRE)) {', 'if (source.is(DamageTypeTags.IS_DROWNING)) {')"
try "everything wounds" "ways of dying, not wounds" "$R_
rep('$H', '''        return null;
    }

    /** On what landed''', '''        return ElysiumWoundKind.LACERATION;
    }

    /** On what landed''')"
try "Med bay ignored" "the room is decoration" "$R_
rep('$H', 'boolean medBay = room == ElysiumRooms.MED_BAY;', 'boolean medBay = false;')"
try "login forgets penalties" "re-applied at login" "$R_
rep('$H', '        ElysiumMedical.apply(event.getEntity(), ElysiumMedical.of(event.getEntity()));\n', '')"
try "wounds survive death" "death spiral" "$R_
rep('$M', '.serialize(ElysiumInjury.CODEC)', '.serialize(ElysiumInjury.CODEC)\n                    .copyOnDeath()')"
try "health not clamped" "clamps health" "$R_
rep('$M', 'player.setHealth(player.getMaxHealth());', ';')"
try "Med bay under the shelter" "does not outrank the shelter" "$R_
rep('$R', 'id(\"med_bay\"), 15, 27,', 'id(\"med_bay\"), 5, 27,')"
try "Med bay over the bunker" "stops being a bunker" "$R_
rep('$R', 'id(\"med_bay\"), 15, 27,', 'id(\"med_bay\"), 35, 27,')"
try "Med bay needs no medicine" "no longer asks for MEDICAL" "$R_
rep('$R', 'Map.of(BlockTags.BEDS, 1, LIGHT, 1, MEDICAL, 1)', 'Map.of(BlockTags.BEDS, 1, LIGHT, 1)')"
try "a torch is medicine" "makes every bedroom a Med bay" "$R_
rep('$T', '\"minecraft:brewing_stand\",', '\"minecraft:brewing_stand\", \"minecraft:torch\",')"
try "lacerations stack to ten hearts" "under the health floor" "$R_
rep('$K', 'LACERATION(\"laceration\", 5.0F, 20, -2.0D, 3)', 'LACERATION(\"laceration\", 5.0F, 20, -2.0D, 5)')"
try "chance cap raised to certainty" "more likely than not" "$R_
rep('$D', 'MAX_CHANCE = 0.5F;', 'MAX_CHANCE = 1.0F;')"
try "every zombie hit lacerates" "ordinary zombie hit can lacerate" "$R_
rep('$K', 'LACERATION(\"laceration\", 5.0F,', 'LACERATION(\"laceration\", 3.0F,')"
try "Med bay no faster" "a Med bay or sleep is no faster" "$R_
rep('$D', 'RATE_MED_BAY = 4;', 'RATE_MED_BAY = 1;')"
try "burns permanent" "not lasting, permanent" "$R_
rep('$K', 'BURN(\"burn\", 4.0F, 25,', 'BURN(\"burn\", 4.0F, 300,')"
try "heal overflow put back" "multiplication wrapped" "$R_
rep('$D', '''        if (elapsed > Long.MAX_VALUE / rate) {
            return 0;
        }''', '')"
try "strict decoding" "lost the whole injury" "$R_
rep('$I', '            .promotePartial(problem -> {})\n', '')"
try "wounds stack past the cap" "past the cap stacked" "$R_
rep('$I', 'if (count(kind) < kind.stack()) {', 'if (true) {')"
try "registry in the rule" "takes the whole system out of the tests" "$R_
rep('$D', 'package com.elysium.lib.medical;', 'package com.elysium.lib.medical;\n\nimport net.neoforged.neoforge.attachment.AttachmentType;')"
echo "---- caught $pass, missed $miss"
[ "$miss" -eq 0 ]
