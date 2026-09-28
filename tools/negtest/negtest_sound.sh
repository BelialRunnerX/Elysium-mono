#!/usr/bin/env bash
# The seam between what asks for a sound and the table that has it, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
RULES=$ROOT/elysium-lib/src/main/java/com/elysium/lib/sound/ElysiumSoundRules.java
BEAST=$ROOT/elysium-bestiary/src/main/java/com/elysium/bestiary/entity/BeastEntity.java
PIGEON=$ROOT/elysium-comforts/src/main/java/com/elysium/comforts/entity/PigeonEntity.java
KILN=$ROOT/elysium-solarpunk/src/main/java/com/elysium/solarpunk/machine/CeramicKilnEntity.java
ANVIL=$ROOT/elysium-armory/src/main/java/com/elysium/armory/block/ArmoryStoneAnvilBlock.java
SNAP=/tmp/sound_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-lib/src/main/java/com/elysium/lib/sound elysium-bestiary/src/main/java/com/elysium/bestiary/entity elysium-comforts/src/main/java/com/elysium/comforts/entity elysium-solarpunk/src/main/java/com/elysium/solarpunk/machine elysium-armory/src/main/java/com/elysium/armory/block)
restore() { (cd $ROOT && tar xf $SNAP); }
out() { (cd $ROOT && timeout 300 python3 check_sound.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "a creature and the table"
edit $PIGEON '        return "pigeon";' '        return "dove";'
expect "a creature speaking with a voice the table does not have" "which the sound table does not have"

edit $PIGEON '    protected SoundEvent getAmbientSound() {
        return ElysiumVoices.ambient(voice(), super.getAmbientSound());
    }' '    protected SoundEvent getAmbientSound() {
        return super.getAmbientSound();
    }'
expect "a creature that stopped answering for its voice" "does not answer for a voice"

edit $RULES '            voice("wight", 190.0, METAL, 0.10, 0.30, 1.00, 6.0, 0.030, 0.60, 13),' '            voice("ghoul", 190.0, METAL, 0.10, 0.30, 1.00, 6.0, 0.030, 0.60, 13),'
expect "a beast family with no voice in the table" "has no voice"

edit $RULES '            voice("envoy", 300.0, CHIRP, 0.00, 0.20, 0.50, 5.0, 0.020, 0.30, 81),' '            voice("envoy", 300.0, CHIRP, 0.00, 0.20, 0.50, 5.0, 0.020, 0.30, 81),
            voice("ghost", 300.0, CHIRP, 0.00, 0.20, 0.50, 5.0, 0.020, 0.30, 99),'
expect "a voice in the table nothing speaks with" "nothing speaks with"

echo "a machine and a station"
edit $KILN 'ElysiumVoices.hum(level, worldPosition, "roar");' 'ElysiumVoices.hum(level, worldPosition, "bellow");'
expect "a machine humming a hum the table does not have" "hums \"bellow\""

edit $ANVIL 'ElysiumVoices.click(level, pos, "station");' 'ElysiumVoices.click(level, pos, "clang");'
expect "a station clicking a click the table does not have" "clicks \"clang\""

echo "restoring and re-checking"
restore
if out | grep -q "PROBLEM"; then echo "  RESTORE FAILED"; fail=$((fail+1)); else echo "  tree restored clean"; fi
rm -f $SNAP
echo "sound: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
