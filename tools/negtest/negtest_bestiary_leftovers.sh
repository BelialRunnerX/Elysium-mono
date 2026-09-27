#!/usr/bin/env bash
# A rename leaves the old name's files behind; the validator has to see them.
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
M=$ROOT/elysium-bestiary
D=$M/src/main/resources/data/elysiumbestiary
A=$M/src/main/resources/assets/elysiumbestiary
SNAP=/tmp/bestiary_leftovers_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-bestiary)
restore() { rm -rf $ROOT/elysium-bestiary && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }

echo "== a rename's leftovers"
# a spawn rule for a creature no roster names any more
cp $D/neoforge/biome_modifier/add_spallet_spawns.json $D/neoforge/biome_modifier/add_oldname_spawns.json
sed -i 's/elysiumbestiary:spallet/elysiumbestiary:oldname/' $D/neoforge/biome_modifier/add_oldname_spawns.json
expect "a spawn rule for a creature that was renamed" "named for a creature no roster has"
# an egg for one
cp $A/models/item/spallet_spawn_egg.json $A/models/item/oldname_spawn_egg.json
expect "an egg for a creature that was renamed" "named for a creature no roster has"
# a loot table for one
cp $D/loot_table/entities/spallet.json $D/loot_table/entities/oldname.json
expect "loot for a creature that was renamed" "named for a creature no roster has"

echo "bestiary (leftovers): $pass caught, $fail missed"
rm -f $SNAP
[ $fail -eq 0 ]
