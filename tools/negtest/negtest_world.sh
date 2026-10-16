#!/usr/bin/env bash
# P9 (first batch) — gear sets, elites and invaders, broken one rule at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
LIB=$ROOT/elysium-lib
CORE=$ROOT/elysium-core
NPCS=$ROOT/elysium-npcs
LSRC=$LIB/src/main/java/com/elysium/lib
AUG=$LIB/src/main/resources/data/elysiumlib/elysium_mob_augments
SETS=$CORE/src/main/resources/data/elysium/elysium_gear_sets
LLANG=$LIB/src/main/resources/assets/elysiumlib/lang/en_us.json
pass=0; fail=0

SNAP=/tmp/negtest_world_snapshot.tgz
tar czf $SNAP -C $ROOT elysium-lib/src elysium-lib/tools elysium-core/src elysium-npcs/src

outlib()  { (cd $LIB && python3 validate.py 2>&1); }
outnpcs() { (cd $NPCS && python3 validate.py 2>&1); }
expect()  { local w="$2" g; g="$(outlib)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
expectn() { local w="$2" g; g="$(outnpcs)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "gear sets"
cp $LSRC/set/ElysiumGearSets.java /tmp/gs.bak
edit $LSRC/set/ElysiumGearSets.java 'ElysiumItemEffects.register("elysiumlib:gear_sets"' \
                                    'register("elysiumlib:gear_sets_unused"'
expect "a set bonus written as its own system" "fourth implementation"
cp /tmp/gs.bak $LSRC/set/ElysiumGearSets.java

cp $LSRC/set/ElysiumGearSets.java /tmp/gs.bak
edit $LSRC/set/ElysiumGearSets.java 'new ElysiumItemEffects.Active(set.id(), 1, effect)' \
                                    'new ElysiumItemEffects.Active(set.id(), worn, effect)'
expect "a four-piece set granting four times what its file says" "four times what its file says"
cp /tmp/gs.bak $LSRC/set/ElysiumGearSets.java

cp $LSRC/set/ElysiumGearSetHandler.java /tmp/gh.bak
edit $LSRC/set/ElysiumGearSetHandler.java 'instance.addTransientModifier(new AttributeModifier(id, amount, operation));' ''
expect "a set bonus that is never applied" "not re-asserted"
cp /tmp/gh.bak $LSRC/set/ElysiumGearSetHandler.java

cp $LSRC/set/ElysiumGearSetHandler.java /tmp/gh.bak
edit $LSRC/set/ElysiumGearSetHandler.java 'id.getPath().startsWith("gear_set/")' 'false'
expect "a bonus that survives taking the armour off" "survive the armour"
cp /tmp/gh.bak $LSRC/set/ElysiumGearSetHandler.java

cp $LSRC/set/ElysiumGearSetWearers.java /tmp/gw.bak
edit $LSRC/set/ElysiumGearSetWearers.java 'private static final ThreadLocal<LivingEntity> CURRENT = new ThreadLocal<>();' \
                                          'private static LivingEntity CURRENT_VALUE;
    private static final Object CURRENT_LOCK = new Object();'
expect "a wearer two threads would share" "two threads would share"
cp /tmp/gw.bak $LSRC/set/ElysiumGearSetWearers.java

cp $SETS/elysium_regalia.json /tmp/sr.bak
python3 -c "
import json
p='$SETS/elysium_regalia.json'
d=json.load(open(p)); d['items']=d['items'][:1]; json.dump(d, open(p,'w'), indent=2)
"
expect "a set of one piece" "fewer than two items"
cp /tmp/sr.bak $SETS/elysium_regalia.json

cp $SETS/elysium_regalia.json /tmp/sr.bak
python3 -c "
import json
p='$SETS/elysium_regalia.json'
d=json.load(open(p)); d['bonuses'][0]['effects'][0]['type']='property'; json.dump(d, open(p,'w'), indent=2)
"
expect "a set bonus only a build could read" "only read while gear is built"
cp /tmp/sr.bak $SETS/elysium_regalia.json

echo "elites and invaders"
cp $LSRC/elite/ElysiumEliteRules.java /tmp/er.bak
edit $LSRC/elite/ElysiumEliteRules.java 'return ELITE_SHARE * com.elysium.lib.tier.ElysiumWorldTier.share(worldTier);' \
                                        'return ELITE_SHARE * worldTier;'
expect "a world where every creature is an elite" "every mob became an elite"
cp /tmp/er.bak $LSRC/elite/ElysiumEliteRules.java

cp $LSRC/elite/ElysiumEliteRules.java /tmp/er.bak
edit $LSRC/elite/ElysiumEliteRules.java 'public static final double INVADER_FRACTION = 0.04D;' \
                                        'public static final double INVADER_FRACTION = 1.0D;'
expect "an invader as common as an elite" "as likely as an elite"
cp /tmp/er.bak $LSRC/elite/ElysiumEliteRules.java

cp $LSRC/elite/ElysiumEliteRules.java /tmp/er.bak
edit $LSRC/elite/ElysiumEliteRules.java 'return ELITE_SHARE * com.elysium.lib.tier.ElysiumWorldTier.share(worldTier);' \
                                        'return ELITE_SHARE * (0.25D + com.elysium.lib.tier.ElysiumWorldTier.share(worldTier));'
expect "a floor under the promotion chance, so the Frontier promotes" "the Frontier promoted somebody"
cp /tmp/er.bak $LSRC/elite/ElysiumEliteRules.java

cp $LSRC/elite/ElysiumEliteRules.java /tmp/er.bak
edit $LSRC/elite/ElysiumEliteRules.java 'case INVADER -> 3.0D;' 'case INVADER -> 1.5D;'
expect "an invader that pays no better than an elite" "its extra weight is a tax"
cp /tmp/er.bak $LSRC/elite/ElysiumEliteRules.java

cp $LSRC/elite/ElysiumElites.java /tmp/ee.bak
edit $LSRC/elite/ElysiumElites.java 'if (decided(mob)) {
            return;
        }' ''
expect "a creature promoted twice" "no way back from that"
cp /tmp/ee.bak $LSRC/elite/ElysiumElites.java

cp $LSRC/elite/ElysiumElites.java /tmp/ee.bak
edit $LSRC/elite/ElysiumElites.java 'mob.setCustomName(named);' ''
expect "an elite nobody can tell from an ordinary creature" "until it kills them"
cp /tmp/ee.bak $LSRC/elite/ElysiumElites.java

cp $LSRC/elite/ElysiumElites.java /tmp/ee.bak
edit $LSRC/elite/ElysiumElites.java 'mob.setHealth(mob.getMaxHealth());' ''
expect "an elite arriving at a fraction of its own bar" "fraction of its own bar"
cp /tmp/ee.bak $LSRC/elite/ElysiumElites.java

cp $LSRC/elite/ElysiumMobAugments.java /tmp/ma.bak
edit $LSRC/elite/ElysiumMobAugments.java 'String chosen = ElysiumAffixRules.pick(pool, random.nextDouble());' \
                                         'String chosen = pool.isEmpty() ? null : pool.get(0).id();'
expect "a second implementation of the weighted pick" "second thing that can disagree"
cp /tmp/ma.bak $LSRC/elite/ElysiumMobAugments.java

cp $LSRC/tier/ElysiumTierMobs.java /tmp/tm.bak
edit $LSRC/tier/ElysiumTierMobs.java 'com.elysium.lib.elite.ElysiumElites.promote(mob, tier);' ''
expect "nothing promoting a creature at all" "one moment it is safe to"
cp /tmp/tm.bak $LSRC/tier/ElysiumTierMobs.java

cp $LSRC/elite/ElysiumEliteDrops.java /tmp/ed.bak
edit $LSRC/elite/ElysiumEliteDrops.java 'event.getDrops().addAll(extra);' ''
expect "extra drops computed and never added" "computed and never added"
cp /tmp/ed.bak $LSRC/elite/ElysiumEliteDrops.java

echo "the augments themselves"
cp $AUG/fleet.json /tmp/af.bak
python3 -c "
import json
p='$AUG/fleet.json'
d=json.load(open(p)); d['attributes']={'minecraft:generic.movement_speed': 1.0}; json.dump(d, open(p,'w'), indent=2)
"
expect "an augment that multiplies by one" "which is not a change"
cp /tmp/af.bak $AUG/fleet.json

cp $AUG/fleet.json /tmp/af.bak
python3 -c "
import json
p='$AUG/fleet.json'
d=json.load(open(p)); d['attributes']={'minecraft:generic.movement_speed': 0.0}; json.dump(d, open(p,'w'), indent=2)
"
expect "an augment that takes an attribute away" "rather than augment it"
cp /tmp/af.bak $AUG/fleet.json

cp $AUG/fleet.json /tmp/af.bak
python3 -c "
import json
p='$AUG/fleet.json'
d=json.load(open(p)); d['weight']=0; json.dump(d, open(p,'w'), indent=2)
"
expect "an augment that could never be drawn" "weighs nothing"
cp /tmp/af.bak $AUG/fleet.json

cp $LLANG /tmp/ll.bak
edit $LLANG '"elysium.augment.elysiumlib.fleet"' '"elysium.augment.elysiumlib.swift"'
expect "an augment with no name, which is what a creature is called" "its name is what a creature is called"
cp /tmp/ll.bak $LLANG

cp $LLANG /tmp/ll.bak
edit $LLANG '"elysium.rank.invader"' '"elysium.rank.raider"'
expect "an invader announcing itself as a raw key" "raw key"
cp /tmp/ll.bak $LLANG

echo "the gear smith"
cp $NPCS/src/main/java/com/elysium/npcs/village/ElysiumVillagerTrades.java /tmp/vt.bak
edit $NPCS/src/main/java/com/elysium/npcs/village/ElysiumVillagerTrades.java \
     'if (profession == ElysiumStations.GEAR_SMITH.profession().get()) {' 'if (false) {'
expectn "a profession that can be trained into and offers nothing" "offers nothing"
cp /tmp/vt.bak $NPCS/src/main/java/com/elysium/npcs/village/ElysiumVillagerTrades.java

echo
echo "restoring and re-checking"
tar xzf $SNAP -C $ROOT
ok=1
(cd $LIB && python3 validate.py >/dev/null 2>&1) || ok=0
(cd $NPCS && python3 validate.py >/dev/null 2>&1) || ok=0
if [ "$ok" = 1 ]; then echo "  tree restored clean";
else echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1)); fi
echo "world: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
