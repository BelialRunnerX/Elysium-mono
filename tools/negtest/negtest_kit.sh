#!/usr/bin/env bash
# P12.18 — the starting kit, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-core
K=$M/src/main/java/com/elysium/core/event/ElysiumStartingKit.java
T=$M/src/main/resources/data/elysium/loot_table/gameplay/starting_kit.json
SNAP=/tmp/negtest_kit_snapshot.tgz
pass=0; fail=0

tar czf $SNAP -C /tmp Elysium-mono/elysium-core

out() { (cd $ROOT && python3 elysium-core/validate.py 2>&1); }
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

echo "the starting kit"
mv $K /tmp/k.away
expect "a new player given nothing" "no way to find out what this mod is"
restore
rm -f /tmp/k.away

mv $T /tmp/t.away
expect "a kit that resolves to nothing" "resolves to nothing"
restore
rm -f /tmp/t.away

edit $T '"type": "minecraft:gift"' '"type": "minecraft:chest"'
expect "a table expecting different parameters" "different sets"
restore

python3 -c "
import json
p='$T'
d=json.load(open(p)); d['pools']=[]
json.dump(d, open(p,'w'), indent=2)"
expect "a kit with nothing in it" "given nothing and the code runs anyway"
restore

edit $T '"name": "minecraft:bread"' '"name": "elysium:imperial_manual"'
expect "a kit handing out something nobody registers" "does not register"
restore

# One codex, not two: elysium-lib's join handler hands one over on the same
# login, so the kit leaves it out and core has to keep handing the library one.
edit $T '  "pools": [
    {' '  "pools": [
    {
      "rolls": 1,
      "bonus_rolls": 0,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "elysium:imperial_codex"
        }
      ]
    },
    {'
expect "two codices on a first login" "a new player starts with two"
restore

edit $T '          "type": "minecraft:item",
          "name": "minecraft:bread",' '          "type": "minecraft:alternatives",
          "children": [
            {
              "type": "minecraft:item",
              "name": "elysium:imperial_codex"
            }
          ]
        },
        {
          "type": "minecraft:item",
          "name": "minecraft:bread",'
expect "a second codex tucked inside an alternatives entry" "a new player starts with two"
restore

edit $M/src/main/java/com/elysium/core/ElysiumContent.java '        ElysiumHooks.setCodex(() -> new ItemStack(Elysium.IMPERIAL_CODEX.get()));' ''
expect "the codex left to a library that is given none" "no new player is given one"
restore

edit $K 'PlayerEvent.PlayerLoggedInEvent event' 'PlayerEvent.PlayerLoggedOutEvent event'
expect "a kit nothing listens for" "nothing listens for a player arriving"
restore

edit $K 'player.setData(GIVEN.get(), true);' ''
expect "a kit given every login" "handed one every time"
restore

edit $K '.copyOnDeath()' ''
expect "a kit you get again by dying" "dying is how"
restore

edit $K 'player.server.reloadableRegistries().getLootTable(KIT)' \
        'net.minecraft.world.level.storage.loot.LootTable.EMPTY'
expect "kit contents decided in Java" "a pack cannot change or empty it"
restore

edit $K 'if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }' 'player.getInventory().add(stack);'
expect "a kit destroyed by a full inventory" "silently destroyed"
restore

echo "the tier command"
C=$M/src/main/java/com/elysium/core/command/ElysiumCommand.java
CLANG=$M/src/main/resources/assets/elysium/lang/en_us.json

edit $C 'Commands.literal("tier")' 'Commands.literal("standing2")'
expect "no way to see your own world tier" "every other system in this mod is multiplied by"
restore

edit $C 'int tier = com.elysium.lib.tier.ElysiumTiers.of(player);' \
        'int tier = ElysiumCharacter.getLevel(player) / 10;'
expect "a tier worked out twice" "can disagree with every system"
restore

edit $C 'int floor = com.elysium.lib.tier.ElysiumTiers.floorOf(player);' 'int floor = 0;'
expect "a tier with no sign of where it came from" "cannot tell"
restore

edit $C 'com.elysium.lib.tier.ElysiumWorldTier.share(tier) * 100.0D))' '0.0D))'
expect "a tier number a player cannot act on" "the only part a player can act on"
restore

python3 -c "
import json
p='$CLANG'
d=json.load(open(p)); d.pop('elysium.command.tier.share')
json.dump(d, open(p,'w'), indent=2)"
expect "a command line with no words behind it" "nothing translates it"
restore

echo
echo "restoring and re-checking"
restore
if (cd $ROOT && python3 elysium-core/validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
rm -f $SNAP
echo "starting kit: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
