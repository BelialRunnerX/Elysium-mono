#!/bin/bash
cd /tmp/Elysium-mono/elysium-lib || exit 1
S=src/main/java/com/elysium/lib
pass=0; miss=0
snap() { rm -rf /tmp/_sh; mkdir -p /tmp/_sh; cp $S/network/CharacterSheet.java /tmp/_sh/; cp $S/screen/ElysiumCharacterScreen.java /tmp/_sh/; cp $S/event/ElysiumSyncHandler.java /tmp/_sh/; }
restore() { cp /tmp/_sh/CharacterSheet.java $S/network/; cp /tmp/_sh/ElysiumCharacterScreen.java $S/screen/; cp /tmp/_sh/ElysiumSyncHandler.java $S/event/; }
try() {
  local label="$1" want="$2" break_it="$3"
  snap
  bash -c "$break_it" || { echo "SETUP FAILED: $label"; miss=$((miss+1)); restore; return; }
  if timeout 1800 python3 validate.py 2>&1 | grep -qF "$want"; then
    echo "CAUGHT : $label"; pass=$((pass+1))
  else
    echo "MISSED : $label  (expected: $want)"; miss=$((miss+1))
  fi
  restore
}

try "research dropped from the synced sheet" "draws six zeroes" \
  "sed -i 's|                research(player),|                \"\",|' $S/network/CharacterSheet.java"

try "the catalogue count dropped" "B5's readout is blank again" \
  "python3 -c \"
import io
p='$S/network/CharacterSheet.java'
s=io.open(p,encoding='utf-8').read()
s=s.replace('''Integer.toString(com.elysium.lib.discovery.ElysiumDiscoveries
                        .catalogued(player))''','''\\\"0\\\"''',1)
io.open(p,'w',encoding='utf-8').write(s)
\""

try "the sync handler stops comparing" "nothing notices a change" \
  "sed -i 's|String packed = CharacterSheet.pack(player);|String packed = \"\";|' $S/event/ElysiumSyncHandler.java"

try "a tab label with no constant" "falls through to the character page" \
  "python3 -c \"
import io
p='$S/screen/ElysiumCharacterScreen.java'
s=io.open(p,encoding='utf-8').read()
s=s.replace('            Component.translatable(\\\"elysium.screen.tab.research\\\"),\n    };','            Component.translatable(\\\"elysium.screen.tab.research\\\"),\n            Component.translatable(\\\"elysium.screen.tab.extra\\\"),\n    };',1)
io.open(p,'w',encoding='utf-8').write(s)
\""

try "a tab constant with no case in the switch" "draws the default page" \
  "sed -i 's|            case TAB_RESEARCH -> renderResearchTab(g, x, y, inner, bodyBottom, mouseX, mouseY);||' $S/screen/ElysiumCharacterScreen.java"

echo "---- caught $pass, missed $miss"
[ "$miss" -eq 0 ]
