#!/bin/bash
cd /tmp/Elysium-mono || exit 1
negtest_lock() { if command -v flock >/dev/null 2>&1; then flock -n "$1"; else mkdir "$2" 2>/dev/null && trap "rmdir '$2' 2>/dev/null" EXIT; fi; }  # Git Bash on Windows has no flock
exec 9>/tmp/negtest_runes.lock; negtest_lock 9 /tmp/negtest_runes.lockdir || { echo "already running"; exit 2; }
L=elysium-lib/src/main/java/com/elysium/lib
SO=$L/item/ElysiumSockets.java; RN=$L/item/ElysiumRune.java; TK=$L/event/ElysiumRuneTickHandler.java
GD=$L/item/ElysiumGearData.java; TI=$L/item/ElysiumRuneTiers.java
FILES="$SO $RN $TK $GD $TI"
snap() { rm -rf /tmp/_rn; mkdir -p /tmp/_rn; for f in $FILES; do cp $f /tmp/_rn/; done; }
restore() { for f in $FILES; do cp /tmp/_rn/$(basename $f) $f; done; }
if ! timeout 1700 python3 elysium-lib/validate.py >/dev/null 2>&1; then echo "SETUP FAILED: not clean"; exit 2; fi
echo "baseline clean"
pass=0; miss=0
R='
import sys
def rep(path, old, new):
    s = open(path).read()
    if old not in s: sys.exit("anchor: " + old[:60])
    open(path, "w").write(s.replace(old, new, 1))
'
t() { snap; python3 -c "$R
$3" || { echo "SETUP FAILED $1"; restore; return; }
  if timeout 1700 python3 elysium-lib/validate.py 2>&1 | grep -qF "$2"; then echo "CAUGHT : $1"; pass=$((pass+1)); else echo "MISSED : $1"; miss=$((miss+1)); fi
  restore; }
t "affix ignores tier" "tier-4 Plasmaforge" "rep('$SO', 'float scale = alignmentScale(rune, element) * socketed.scale();', 'float scale = alignmentScale(rune, element);')"
t "dodge ignores tier" "weights dodge and heat" "rep('$RN', 'total += value.apply(socketed.rune()) * socketed.scale();', 'total += value.apply(socketed.rune());')"
t "barrier counts copies" "counts by tier" "rep('$RN', '''            if (socketed.rune() == rune) {
                total += socketed.scale();''', '''            if (socketed.rune() == rune) {
                total += 1.0F;''')"
t "effects run at tier 1" "hands each effect its tier" "rep('$TK', 'socketed.scale());', '1.0F);')"
t "ascension drops tiers" "drops its runes' tiers" "rep('$GD', '''                tier, 0, runeTiers);''', '''                tier, 0, List.of());''')"
t "old saves fail" "rune_tiers is not optional" "rep('$GD', 'Codec.INT.listOf().optionalFieldOf(\"rune_tiers\", List.of())', 'Codec.INT.listOf().fieldOf(\"rune_tiers\")')"
t "tier squared" "the rule is exactly" "rep('$TI', 'return (float) clamp(tier);', 'return (float) clamp(tier) * clamp(tier);')"
t "Resistance cap lifted" "immunity to all damage" "rep('$TI', 'RESISTANCE_MAX_LEVEL = 3;', 'RESISTANCE_MAX_LEVEL = 6;')"
t "potion level wraps" "wrapped" "rep('$TI', 'return (int) Math.min(MAX_AMPLIFIER, level - 1L);', 'return (int) (level - 1L);')"
t "unequal tiers merge" "only a matched pair may" "rep('$TI', 'return a == b && a >= BASE && a < MAX_TIER;', 'return a >= BASE && a < MAX_TIER;')"
t "new rune lands in the wrong socket" "added beside two old ones read as" "rep('$GD', '''        for (int i = 0; i < this.runes.size(); i++) {
            tiers.add(tierAt(i));
        }
''', '''        tiers.addAll(runeTiers);
''')"
t "registry in the rule" "takes the whole system out of the tests" "rep('$TI', 'package com.elysium.lib.item;', 'package com.elysium.lib.item;\n\nimport net.neoforged.neoforge.attachment.AttachmentType;')"
echo "---- lib: caught $pass, missed $miss"
