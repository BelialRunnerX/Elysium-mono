#!/usr/bin/env bash
# P12.20 — the add-on API, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-lib
L=$M/src/main/java/com/elysium/lib
SNAP=/tmp/negtest_api_snapshot.tgz
pass=0; fail=0

tar czf $SNAP -C /tmp Elysium-mono/elysium-lib

out() { (cd $ROOT && python3 elysium-lib/validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }
restore() { rm -rf $M; tar xzf $SNAP -C /tmp; }

E=$L/api/ElysiumEvents.java

echo "the add-on api"
mv $E /tmp/e.away
expect "a library with no api at all" "no add-on API"
restore
rm -f /tmp/e.away

edit $E 'public static class WorldTier extends Event {' \
        'public static class WorldTierGone extends Event {'
expect "an event removed from a published api" "somebody else has to fix"
restore

edit $E 'public static class Promote extends Event implements ICancellableEvent {' \
        'public static class Promote extends Event {'
expect "an event that can no longer be cancelled" "watch it happen and nothing else"
restore

edit $L/affix/ElysiumAffixRoll.java 'com.elysium.lib.api.ElysiumEvents.AffixRoll asked =
                com.elysium.lib.api.ElysiumEvents.fireAffixRoll(stack, count, worldTier);
        if (asked.isCanceled() || asked.getCount() <= 0) {
            return stack;
        }
        count = asked.getCount();
        worldTier = asked.getWorldTier();' ''
expect "a system that never fires its event" "says a listener will be asked"
restore

edit $L/affix/ElysiumAffixRoll.java 'if (asked.isCanceled() || asked.getCount() <= 0) {
            return stack;
        }' ''
expect "a cancelled roll that rolls anyway" "asks and then rolls anyway"
restore

edit $L/affix/ElysiumAffixRoll.java 'count = asked.getCount();
        worldTier = asked.getWorldTier();' ''
expect "a listener's changes ignored" "the roller ignores both"
restore

edit $L/tier/ElysiumTiers.java 'return com.elysium.lib.api.ElysiumEvents.fireWorldTier(player, worked);' \
                               'return worked;'
expect "a world tier nobody is asked about" "says a listener will be asked"
restore

edit $L/elite/ElysiumElites.java 'if (rank != ElysiumEliteRules.Rank.ORDINARY
                && !com.elysium.lib.api.ElysiumEvents.firePromote(mob, worldTier,
                        rank.name())) {
            // Somebody said no. Marked ordinary and decided, so the same
            // creature is not asked about again on the next spawn attempt.
            rank = ElysiumEliteRules.Rank.ORDINARY;
        }' ''
expect "a promotion nobody can refuse" "says a listener will be asked"
restore

edit $L/elite/ElysiumElites.java 'rank = ElysiumEliteRules.Rank.ORDINARY;' 'rank = rank;'
expect "a refused promotion that happens anyway" "a listener that says no gets an elite"
restore

edit $L/tier/ElysiumTiers.java 'public final class ElysiumTiers {' \
                               'public final class ElysiumTiers {

    static {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.bus.api.Event());
    }
'
expect "a system posting its own event" "Exactly one file does"
restore

echo
echo "restoring and re-checking"
restore
if (cd $ROOT && python3 elysium-lib/validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
rm -f $SNAP
echo "add-on api: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
