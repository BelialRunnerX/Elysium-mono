#!/usr/bin/env bash
# Elysium Colonies, part four: whose map it is.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-colonies
S=$M/src/main/java/com/elysium/colonies
R=$M/src/main/resources
SNAP=/tmp/colonies4_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-colonies)
restore() { rm -rf $ROOT/elysium-colonies && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, 'anchor not found in ' + p
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@"; }

echo "the gate"
edit $S/event/ColonyLife.java 'boolean townDecides = StewardRules.maySite(steward, town.day(),
                ledger.lastSeen());' 'boolean townDecides = true;'
expect "a town building where the player was going to" "put the market"

edit $S/event/ColonyLife.java 'if (ownerIsHere(level, town)) {
            ledger = ledger.seenOn(town.day());
        }' ''
expect "attendance that can never be established" "decides nothing"

edit $S/event/ColonyLife.java 'ledger = ledger.seenOn(town.day());' '{ }'
expect "a visit forgotten by the next day" "forgotten by the next day"

edit $S/event/ColonyLife.java 'return Colonies.inside(level, town.id(), owner.blockPosition());' 'return true;'
expect "attendance meaning merely logged in" "other side of the world"

echo "what the gate must not touch"
edit $S/event/ColonyLife.java 'int traded = FrontierExchange.settle(level, town, world);' \
                              'int traded = 0;'
expect "a town that stops trading" "no longer happens at all"

echo "telling the owner"
edit $S/event/ColonyLife.java 'private static boolean suggest(ServerLevel level, Colonies.Settlement town,
                                   String wanted) {' \
                              'private static boolean suggestNothing(ServerLevel level, Colonies.Settlement town,
                                   String wanted) {'
expect "a deferring town that never says what it wanted" "never learns what it needs"

edit $S/event/ColonyLife.java '&& StewardRules.dueToSuggest(town.day(), ledger.lastSuggested())' ''
expect "a town that nags every single day" "a player turns off"

echo "the rules"
edit $S/rules/StewardRules.java 'case OWNER -> false;' 'case OWNER -> true;'
expect "an owner who reserved placement, built around anyway" "can still be built around"

edit $S/rules/StewardRules.java 'case TOWN -> true;' 'case TOWN -> false;'
expect "an owner who delegated, still asked" "is still asked"

edit $S/rules/StewardRules.java 'case ATTENDANCE -> !everVisited(lastSeen) || day - lastSeen >= ABSENCE;' \
                                'case ATTENDANCE -> day - lastSeen >= ABSENCE;'
expect "an abandoned town idling out an absence that never happened" "visit that never"

edit $S/rules/StewardRules.java 'public static final int ABSENCE = 3;' \
                                'public static final int ABSENCE = 0;'
expect "a town taking over the moment you turn your back" "logging off for the night"

edit $S/rules/StewardRules.java 'public static final int BETWEEN_SUGGESTIONS = 4;' \
                                'public static final int BETWEEN_SUGGESTIONS = 1;'
expect "reminders more often than it would have built the thing" "notification a player turns off"

edit $S/rules/StewardRules.java 'return ATTENDANCE;' 'return OWNER;'
expect "a typo in a command changing who runs a town" "silently changes who runs a town"

echo "the command and the ledger"
edit $S/command/ColonyCommand.java 'memory.record(town.id(), memory.ledgerOf(town.id()).withSteward(who));' ''
expect "no way to say who decides" "whether they happen to be standing there"

edit $S/command/ColonyCommand.java 'Colonies.Settlement town = Colonies.ownedBy(player);
        if (town == null) {
            source.sendSuccess(() -> Component.translatable("elysium.colony.none")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        ColonyMemory memory' 'Colonies.Settlement town = Colonies.in(player.serverLevel()).get(0);
        ColonyMemory memory'
expect "reaching across the map to change somebody else's town" "somebody else's town"

edit $S/citizen/ColonyMemory.java 'Codec.INT.optionalFieldOf("last_seen", -1)' \
                                  'Codec.INT.fieldOf("last_seen")'
expect "an existing world's colonies failing to load" "fail to load"

python3 -c "
import json
p='$R/assets/elysiumcolonies/lang/en_us.json'
d=json.load(open(p)); d.pop('elysium.colony.steward.name.town')
json.dump(d,open(p,'w'),indent=2)"
expect "the stewardship nobody picked in testing" "reads as a"

echo
echo "restoring and re-checking"
restore
if (cd $M && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
echo "colonies (stewardship): $pass caught, $fail missed"
[ "$fail" -eq 0 ]
