#!/usr/bin/env bash
# The codex page-reset bug, reintroduced one way at a time.
set -uo pipefail
cd /tmp/Elysium-mono/elysium-lib
H=src/main/java/com/elysium/lib/network/ElysiumClientHooks.java
S=src/main/java/com/elysium/lib/screen/ElysiumCharacterScreen.java
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
" "$@"; }

echo "the original bug, exactly as it was"
cp $H /tmp/h.bak
edit $H '        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof ElysiumCharacterScreen open) {
            open.refresh(parsed, payload.unspent());
            return;
        }
        client.setScreen(new ElysiumCharacterScreen(parsed, payload.unspent()));' \
  '        Minecraft.getInstance().setScreen(new ElysiumCharacterScreen(parsed, payload.unspent()));'
expect "the handler rebuilds the screen every time" "This is the reported bug"
cp /tmp/h.bak $H

echo "the fix half-applied"
cp $H /tmp/h.bak
edit $H '            open.refresh(parsed, payload.unspent());
            return;' '            return;'
expect "an open screen found and never refreshed" "keeps showing the numbers"
cp /tmp/h.bak $H

echo "the bug moved inside the fix"
cp $S /tmp/s.bak
edit $S '        this.sheet = newSheet;
        this.unspent = newUnspent;' '        this.sheet = newSheet;
        this.unspent = newUnspent;
        this.tab = TAB_CHARACTER;'
expect "refresh throws the page away itself (structural)" "the page is thrown away anyway"
expect "refresh throws the page away itself (executed)" "the upgrade button throws you back"
cp /tmp/s.bak $S

echo "the screen loses the ability to be refreshed"
cp $S /tmp/s.bak
edit $S '    public void refresh(CharacterSheet.Parsed newSheet, int newUnspent) {' \
        '    public void refreshed(CharacterSheet.Parsed newSheet, int newUnspent) {'
expect "no refresh method at all" "has no refresh method"
cp /tmp/s.bak $S

echo "the sheet goes back to being immutable"
cp $S /tmp/s.bak
edit $S '    private CharacterSheet.Parsed sheet;' '    private final CharacterSheet.Parsed sheet;'
expect "a final sheet field" "cannot take a newer sheet"
cp /tmp/s.bak $S

echo "the coupled race/class condition, which the fix originally had"
cp $S /tmp/s.bak
edit $S '        if (this.pickedRace == null) {
            this.pickedRace = newSheet.race();
        }
        if (this.pickedClass == null) {
            this.pickedClass = newSheet.job();
        }' '        if (this.pickedRace == null || this.pickedClass == null) {
            this.pickedRace = newSheet.race();
            this.pickedClass = newSheet.job();
        }'
expect "a half-made choice cleared by a refresh" "cleared the race they had selected"
cp /tmp/s.bak $S

echo
echo "negative tests: $pass caught, $fail missed"
python3 validate.py >/dev/null 2>&1 && echo "restored clean" || echo "RESTORE FAILED"
[ "$fail" -eq 0 ]
