#!/usr/bin/env bash
# The Cordon's roots and the found trinkets, each left somewhere - broken one way at a time.
#
# elysium-ores: every drop and shard in CordonSources has a loot modifier on a
# vanilla table, listed where NeoForge looks, at the rules' chance, none twice.
# elysium-trinkets: every found trinket is rolled by the aggregate table, the
# aggregate is added to vanilla's chests, and the reward provider is registered.
set -uo pipefail
# The repository this script lives in. `pwd -W` is Git Bash's Windows spelling
# (C:/...), which is the one Windows Python can open; elsewhere it fails and
# the POSIX one is used.
ROOT=${ELYSIUM_ROOT:-$(cd "$(dirname "$0")/../.." && (pwd -W 2>/dev/null || pwd))}
O=$ROOT/elysium-ores
OS=$O/src/main/java/com/elysium/ores
OD=$O/src/main/resources/data/elysiumores
OG=$O/src/main/resources/data/neoforge/loot_modifiers/global_loot_modifiers.json
T=$ROOT/elysium-trinkets
TS=$T/src/main/java/com/elysium/trinkets
TD=$T/src/main/resources/data/elysiumtrinkets
TG=$T/src/main/resources/data/neoforge/loot_modifiers/global_loot_modifiers.json
SNAP=${TMPDIR:-/tmp}/cordon_trinkets_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-ores elysium-trinkets)
restore() { rm -rf $ROOT/elysium-ores $ROOT/elysium-trinkets && (cd $ROOT && tar xf $SNAP); }
out() { (cd $O && python3 validate.py 2>&1); }
tout() { (cd $T && python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
texpect() { local w="$2" g; g="$(tout)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, 'anchor not found in ' + p
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "the roots: the table in the rules"
edit $OS/rules/CordonSources.java '            at("blight_crystal", "minecraft:chests/ancient_city"),
' ''
expect "a root nothing in the world leaves" "the world does not give"

edit $OS/rules/CordonSources.java '            at("chitin", "minecraft:entities/spider"),' \
  '            at("chitin", "minecraft:entities/spider"),
            at("chitin", "minecraft:entities/spider"),'
expect "a source written twice, so its rate is doubled" "written twice"

edit $OS/rules/CordonSources.java '            at("chitin", "minecraft:entities/spider"),' \
  '            at("chitin", "minecraft:entities/spider"),
            at("moon_cheese", "minecraft:chests/igloo_chest"),'
expect "a source for something the roster does not ship" "the roster does not ship"

edit $OS/rules/CordonSources.java '            at("chitin", "minecraft:entities/spider"),' \
  '            at("chitin", "minecraft:entities/spider"),
            at("chitin", "somemod:entities/thing"),'
expect "a source on another mod's table" "another mod's table"

edit $OS/rules/CordonSources.java 'public static final float KILL_CHANCE = 0.03F;' \
                                  'public static final float KILL_CHANCE = 0.30F;'
expect "a root that is a common drop" "past the ceiling"

edit $O/tools/OreHarness.java 'check(CordonSources.unsourced().isEmpty(),' 'check(true,'
expect "a harness that stopped asking" "nothing executes"

echo "the roots: the table on the disk"
rm -f $OD/loot_modifiers/ember_slag_from_blaze.json
expect "a source with no loot modifier" "source on paper only"

python3 -c "
import json
p='$OG'
d=json.load(open(p)); d['entries'].remove('elysiumores:null_shard_from_enderman')
json.dump(d,open(p,'w'),indent=2)"
expect "a modifier NeoForge is never told about" "is not listed in global_loot_modifiers"

python3 -c "
import json
p='$OD/loot_modifiers/pressure_shell_from_guardian.json'
d=json.load(open(p)); d['conditions'][1]['chance']=0.5
json.dump(d,open(p,'w'),indent=2)"
expect "a chance the rules never said" "a rate the harness never saw"

python3 -c "
import json
p='$OD/loot_modifiers/bio_culture_from_witch.json'
d=json.load(open(p)); d['conditions'][0]['loot_table_id']='minecraft:entities/zombie'
json.dump(d,open(p,'w'),indent=2)"
expect "a modifier on the wrong table" "fires on the wrong table"

python3 -c "
import json
p='$OD/loot_modifiers/chitin_from_spider.json'
d=json.load(open(p)); d['conditions']=[c for c in d['conditions'] if c['condition']!='minecraft:killed_by_player']
json.dump(d,open(p,'w'),indent=2)"
expect "a kill nobody made" "a grinder is a root mine"

cp $OD/loot_modifiers/ember_slag_from_blaze.json $OD/loot_modifiers/ember_slag_from_zombie.json
expect "a modifier answering to no row" "answers to no row of CordonSources"

python3 -c "
import json
p='$OD/loot_table/cordon/ember_slag.json'
d=json.load(open(p)); d['pools'][0]['entries'][0]['name']='minecraft:cobblestone'
json.dump(d,open(p,'w'),indent=2)"
expect "a root's table dropping something else" "does not drop elysiumores:ember_slag"

python3 -c "
import json
p='$OD/loot_modifiers/lattice_from_end_city_treasure.json'
d=json.load(open(p))
json.dump(d,open(p,'w'),indent=4)"
expect "a modifier edited by hand" "not what tools/gen_ores.py writes"

mkdir -p $OD/loot_modifier && cp $OD/loot_modifiers/unifying.json $OD/loot_modifier/unifying.json
expect "the singular directory NeoForge never reads" "NeoForge never reads it"

echo "the found trinkets: the aggregate"
python3 -c "
import json
p='$TD/loot_table/trinkets/found.json'
d=json.load(open(p)); d['pools'][0]['entries']=[e for e in d['pools'][0]['entries'] if not e['value'].endswith('/mote')]
json.dump(d,open(p,'w'),indent=2)"
texpect "a found trinket the aggregate never rolls" "the aggregate table never rolls"

rm -f $TD/loot_table/trinkets/found.json
texpect "no aggregate at all" "no aggregate table"

python3 -c "
import json
p='$TD/loot_table/trinkets/found.json'
d=json.load(open(p)); d['pools'][0]['entries'].append(dict(d['pools'][0]['entries'][0]))
json.dump(d,open(p,'w'),indent=2)"
texpect "one trinket rolled twice" "lists a table twice"

echo "the found trinkets: the chests"
rm -f $TD/loot_modifiers/found_in_ancient_city.json
texpect "a chest with no loot modifier" "has no loot modifier at"

python3 -c "
import json
p='$TG'
d=json.load(open(p)); d['entries'].remove('elysiumtrinkets:found_in_end_city_treasure')
json.dump(d,open(p,'w'),indent=2)"
texpect "a modifier NeoForge is never told about" "shipped and never runs"

python3 -c "
import json
p='$TD/loot_modifiers/found_in_bastion_treasure.json'
d=json.load(open(p)); d['conditions'][1]['chance']=0.5
json.dump(d,open(p,'w'),indent=2)"
texpect "a chance the Java never said" "a rate the Java never said"

python3 -c "
import json
p='$TD/loot_modifiers/found_in_stronghold_library.json'
d=json.load(open(p)); d['table']='elysiumtrinkets:trinkets/mote'
json.dump(d,open(p,'w'),indent=2)"
texpect "a modifier adding one trinket rather than the aggregate" "does not add elysiumtrinkets:trinkets/found"

edit $TS/trinket/FoundTrinkets.java '            "minecraft:chests/woodland_mansion");' \
                                     '            "minecraft:chests/stronghold_library");'
texpect "a modifier for a chest the Java no longer names" "answers to no chest in FoundTrinkets"

echo "the found trinkets: the reward shelf"
edit $TS/ElysiumTrinkets.java '        FoundTrinkets.registerRewards();
' ''
texpect "a provider nobody registers" "never registered"

edit $TS/trinket/FoundTrinkets.java 'ElysiumRewards.register(FoundTrinkets::roll);' \
                                     'ElysiumRewards.register((tier, random) -> ItemStack.EMPTY);'
texpect "a provider that always declines" "does not put FoundTrinkets::roll"

edit $TS/trinket/FoundTrinkets.java 'if (tier < ElysiumRewards.MAX_TIER) {' 'if (tier < 0) {'
texpect "a provider answering every tier" "answers every tier"

edit $TS/trinket/FoundTrinkets.java 'if (random.nextFloat() >= REWARD_CHANCE) {' 'if (false) {'
texpect "a provider that never declines at the top" "shadowed"

edit $TS/trinket/FoundTrinkets.java '        for (ElysiumTrinket trinket : CompanionTrinkets.ALL) {
            out.add(trinket);
        }
' ''
texpect "a shelf with no companions on it" "does not walk both rosters"

echo
echo "restoring and re-checking"
restore
if (cd $O && python3 validate.py >/dev/null 2>&1) && (cd $T && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED - the tree is still broken"; fail=$((fail+1))
fi
echo "cordon roots and found trinkets: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
