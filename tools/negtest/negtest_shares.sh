#!/bin/bash
cd /tmp/Elysium-mono || exit 1
negtest_lock() { if command -v flock >/dev/null 2>&1; then flock -n "$1"; else mkdir "$2" 2>/dev/null && trap "rmdir '$2' 2>/dev/null" EXIT; fi; }  # Git Bash on Windows has no flock
exec 9>/tmp/negtest_shares.lock; negtest_lock 9 /tmp/negtest_shares.lockdir || exit 2
L=elysium-lib/src/main/java/com/elysium/lib
ST=$L/stats/ElysiumStats.java; SH=$L/stats/ElysiumShares.java; RS=$L/hazard/ElysiumResistance.java
SO=$L/item/ElysiumSockets.java; CB=$L/event/ElysiumCombatHandler.java; DO=$L/doctrine/ElysiumDoctrines.java
TI=$L/item/ElysiumRuneTiers.java
FILES="$ST $SH $RS $SO $CB $DO $TI"
snap() { rm -rf /tmp/_sh; mkdir -p /tmp/_sh; for f in $FILES; do cp $f /tmp/_sh/; done; }
restore() { for f in $FILES; do cp /tmp/_sh/$(basename $f) $f; done; }
if ! timeout 1700 python3 elysium-lib/validate.py >/dev/null 2>&1; then echo "SETUP FAILED"; exit 2; fi
echo "baseline clean"; pass=0; miss=0
R='
import sys
def rep(path, old, new):
    s = open(path).read()
    if old not in s: sys.exit("anchor: " + old[:60])
    open(path, "w").write(s.replace(old, new, 1))
'
t() { snap; python3 -c "$R
$3" || { echo "SETUP FAILED $1"; miss=$((miss+1)); restore; return; }
  if timeout 1700 python3 elysium-lib/validate.py 2>&1 | grep -qF "$2"; then echo "CAUGHT : $1"; pass=$((pass+1)); else echo "MISSED : $1"; miss=$((miss+1)); fi; restore; }
t "merging multiplies and clamps again" "no longer adds shares as stats" "rep('$ST', 'return ElysiumShares.add(a, b);', 'return 1.0F - (1.0F - clampShare(a)) * (1.0F - clampShare(b));')"
t "Resilience capped at 90%" "has a ceiling of" "rep('$ST', 'ChatFormatting.DARK_AQUA, (float) (1400.0F / ElysiumShares.PACE), UNCAPPED)', 'ChatFormatting.DARK_AQUA, (float) (1400.0F / ElysiumShares.PACE), 0.90F)')"
t "float rounds to 100%" "reached 100%" "rep('$SH', 'return share < 1.0F ? share : Math.nextDown(1.0F);', 'return share;')"
t "hazard sources clamped at 95%" "clamps each source again" "rep('$RS', 'return com.elysium.lib.stats.ElysiumShares.sane(share);', 'return Math.min(com.elysium.lib.stats.ElysiumShares.sane(share), 0.95F);')"
t "gear hazard share linear and clamped" "removes more than 99%" "rep('$RS', '''        return com.elysium.lib.stats.ElysiumShares.fromOdds(
                stat * fraction * com.elysium.lib.stats.ElysiumShares.PACE);''', '''        return (float) Math.min(0.95D, (baseShare + stepPerTier * Math.max(0, tier)) * fraction);''')"
t "knockback affix linear" "scale their" "rep('$SO', 'affix.isShare()', 'false')"
# the player's statement, whole: an add(dodge, ...) for the dodge_chance attribute
# now precedes it, and the first occurrence is not the one the validator reads
t "dodge summed as percentages" "no longer adds runes, Reflexes and passives" "rep('$CB', '''            dodge = com.elysium.lib.stats.ElysiumShares.add(dodge,
                    ElysiumStats.dodgeChance(defender),
                    ElysiumPassives.dodgeChance(defender, event.getSource()));''', '''            dodge = ElysiumStats.combine(dodge + ElysiumStats.dodgeChance(defender),
                    ElysiumPassives.dodgeChance(defender, event.getSource()));''')"
t "elemental blunt clamped at 90%" "elemental blunting is a clamped percentage" "rep('$CB', '''com.elysium.lib.stats.ElysiumShares.fromOdds(
                    blunt * com.elysium.lib.stats.ElysiumShares.PACE)''', 'Math.min(0.90F, blunt)')"
t "doctrine share capped" "the doctrine hazard share is capped" "rep('$DO', '        return best;', '        return Math.min(0.5F, best);')"
t "Resistance held at III" "never passes level III" "rep('$TI', 'return Math.max(0, Math.min(MAX_AMPLIFIER, level - 1));', 'return Math.max(0, Math.min(2, level - 1));')"
echo "---- shares: caught $pass, missed $miss"
[ "$miss" -eq 0 ]
