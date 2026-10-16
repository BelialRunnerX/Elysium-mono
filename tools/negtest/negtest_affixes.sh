#!/usr/bin/env bash
# P4.1, P4.2 and P4.7 — one item-effect engine, broken one rule at a time.
#
# Traits, affixes, gems and runes are the same idea four times over. P4 makes
# them one. Every check that keeps them one is deliberately defeated here, and
# the suite fails if the validators or the harnesses let it through.
set -uo pipefail
ROOT=/tmp/Elysium-mono
LIB=$ROOT/elysium-lib
ARM=$ROOT/elysium-armory
LSRC=$LIB/src/main/java/com/elysium/lib
ASRC=$ARM/src/main/java/com/elysium/armory
AFFIX=$LIB/src/main/resources/data/elysiumlib/elysium_affixes
LLANG=$LIB/src/main/resources/assets/elysiumlib/lang/en_us.json
pass=0; fail=0

# One snapshot of both modules' sources, restored at the end. A per-file backup
# is only as good as the case that took it: one edit whose anchor has moved
# leaves the file it meant to restore holding someone else's copy, and every
# case after it is testing a tree that is already broken.
SNAP=/tmp/negtest_affixes_snapshot.tgz
tar czf $SNAP -C $ROOT elysium-lib/src elysium-lib/tools elysium-armory/src

outlib() { (cd $LIB && python3 validate.py 2>&1); }
outarm() { (cd $ARM && python3 validate.py 2>&1); }

expect()  { local w="$2" g; g="$(outlib)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
expecta() { local w="$2" g; g="$(outarm)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }

edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "what kind of thing a stack is"
cp $LSRC/item/ElysiumLootCategory.java /tmp/lc.bak
edit $LSRC/item/ElysiumLootCategory.java 'return "sword";' 'return "greatsword";'
expect "the resolver answering with a kind the tree never declared" "never heard of"
cp /tmp/lc.bak $LSRC/item/ElysiumLootCategory.java

cp $LSRC/item/ElysiumLootCategory.java /tmp/lc.bak
edit $LSRC/item/ElysiumLootCategory.java '        if (item instanceof ElysiumKinded kinded) {
            String named = kinded.gearKind();
            if (ElysiumGearTree.exists(named)) {
                return named;
            }
        }
' ''
expect "the resolver no longer asking an item what it is" "never asks an item what it is"
cp /tmp/lc.bak $LSRC/item/ElysiumLootCategory.java

cp $LSRC/item/ElysiumLootCategory.java /tmp/lc.bak
edit $LSRC/item/ElysiumLootCategory.java '        String byClass = byClass(item);
        if (byClass != null) {
            return byClass;
        }
' ''
edit $LSRC/item/ElysiumLootCategory.java '    private static String resolve(Item item) {' \
     '    private static String resolve(Item item) {
        if (item instanceof SwordItem) { return "sword"; }'
expect "the class read before the item's own answer" "before asking the item"
cp /tmp/lc.bak $LSRC/item/ElysiumLootCategory.java

cp $LSRC/event/ElysiumGearHandler.java /tmp/gh.bak
edit $LSRC/event/ElysiumGearHandler.java 'com.elysium.lib.item.ElysiumLootCategory.invalidate();' ''
expect "a kind cache nothing ever clears" "outlive the tag reload"
cp /tmp/gh.bak $LSRC/event/ElysiumGearHandler.java

echo "the tree itself"
cp $LSRC/item/ElysiumGearTree.java /tmp/gt.bak
edit $LSRC/item/ElysiumGearTree.java '{"hammer", "pickaxe"}' '{"hammer", "warhammer"}'
expect "a kind descending from something that is not a kind" "which is not a kind"
cp /tmp/gt.bak $LSRC/item/ElysiumGearTree.java

cp $LSRC/item/ElysiumGearTree.java /tmp/gt.bak
edit $LSRC/item/ElysiumGearTree.java '        if (selector.equals("any")) {
            return true;
        }' ''
expect "'any' no longer matching everything" "what it is for"
cp /tmp/gt.bak $LSRC/item/ElysiumGearTree.java

cp $LSRC/item/ElysiumGearTree.java /tmp/gt.bak
edit $LSRC/item/ElysiumGearTree.java '        if (selector.startsWith("not:")) {
            return !matches(kind, selector.substring(4));
        }' ''
expect "negation that does not negate" "negation does not negate"
cp /tmp/gt.bak $LSRC/item/ElysiumGearTree.java

cp $LSRC/item/ElysiumGearTree.java /tmp/gt.bak
edit $LSRC/item/ElysiumGearTree.java '{"trident", "hybrid_weapon"}' '{"trident", "melee_weapon"}'
expect "a trident quietly becoming an ordinary melee weapon" "so nothing could write a rule for the thrown ones"
cp /tmp/gt.bak $LSRC/item/ElysiumGearTree.java

echo "the affixes"
cp $AFFIX/keen.json /tmp/af.bak
edit $AFFIX/keen.json '"weight": 12' '"weight": 0'
expect "an affix that could never be drawn" "weighs nothing"
cp /tmp/af.bak $AFFIX/keen.json

cp $AFFIX/keen.json /tmp/af.bak
edit $AFFIX/keen.json '"type": "attribute"' '"type": "property"'
expect "an affix stating a kind only a build reads" "would do nothing"
cp /tmp/af.bak $AFFIX/keen.json

cp $AFFIX/keen.json /tmp/af.bak
edit $AFFIX/keen.json '"type": "attribute"' '"type": "sharpness"'
expect "an affix stating a kind the engine has no word for" "not a kind the engine has a word for"
cp /tmp/af.bak $AFFIX/keen.json

cp $AFFIX/keen.json /tmp/af.bak
edit $AFFIX/keen.json '"on": [
    "melee_weapon"
  ],' '"on": [],'
expect "an affix that would roll on a potion" "names no kinds of gear"
cp /tmp/af.bak $AFFIX/keen.json

cp $LLANG /tmp/ll.bak
edit $LLANG '"elysium.affix.elysiumlib.keen"' '"elysium.affix.elysiumlib.sharp"'
expect "an affix nobody can read the name of" "would be called by a raw key"
cp /tmp/ll.bak $LLANG

cp $LLANG /tmp/ll.bak
edit $LLANG '"elysium.affix.line"' '"elysium.affix.text"'
expect "the tooltip line with no words behind it" "raw key"
cp /tmp/ll.bak $LLANG

cp $AFFIX/keen.json /tmp/af.bak
edit $AFFIX/keen.json '"max_level": 4' '"max_level": 5'
expect "an affix file edited away from its table" "not what tools/gen_affixes.py generates"
cp /tmp/af.bak $AFFIX/keen.json

echo "one engine, not four"
cp $LSRC/affix/ElysiumAffixEffects.java /tmp/ae.bak
edit $LSRC/affix/ElysiumAffixEffects.java 'ElysiumItemEffects.register("elysiumlib:affixes", INSTANCE);' ''
expect "affixes that feed nothing" "read by nothing"
cp /tmp/ae.bak $LSRC/affix/ElysiumAffixEffects.java

cp $LSRC/effect/ElysiumEffectKinds.java /tmp/ek.bak
edit $LSRC/effect/ElysiumEffectKinds.java 'm.put("extra_damage",        true);' 'm.put("extra_damage",        false);'
expect "a live kind demoted to a build kind under the affixes using it" "would do nothing"
cp /tmp/ek.bak $LSRC/effect/ElysiumEffectKinds.java

cp $LSRC/effect/ElysiumEffectKinds.java /tmp/ek.bak
edit $LSRC/effect/ElysiumEffectKinds.java 'm.put(CODE,                  false);' ''
expect "the vocabulary forgetting that behaviour can be Java" "no longer names 'code'"
cp /tmp/ek.bak $LSRC/effect/ElysiumEffectKinds.java

echo "the arithmetic"
cp $LSRC/affix/ElysiumAffixRules.java /tmp/ar.bak
edit $LSRC/affix/ElysiumAffixRules.java 'seen += candidate.weight();' 'seen += 1;'
expect "a pool that ignores its own weights" "times in 1600"
cp /tmp/ar.bak $LSRC/affix/ElysiumAffixRules.java

cp $LSRC/affix/ElysiumAffixRules.java /tmp/ar.bak
edit $LSRC/affix/ElysiumAffixRules.java 'double clamped = Math.max(0.0D, Math.min(1.0D, roll));
        int level = (int) Math.floor(clamped * maxLevel) + 1;' 'int level = (int) Math.floor(roll * maxLevel) + 1;'
edit $LSRC/affix/ElysiumAffixRules.java 'return Math.max(1, Math.min(maxLevel, level));' 'return Math.min(maxLevel, level);'
expect "a roll that can come out at level zero" "went below level 1"
cp /tmp/ar.bak $LSRC/affix/ElysiumAffixRules.java

cp $LSRC/affix/ElysiumAffixRules.java /tmp/ar.bak
edit $LSRC/affix/ElysiumAffixRules.java '        double clamped = Math.max(0.0D, Math.min(1.0D, roll));
        return min + (max - min) * clamped;' '        return min + (max - min) * roll;'
expect "interpolation that runs past both ends" "went past the maximum"
cp /tmp/ar.bak $LSRC/affix/ElysiumAffixRules.java

cp $LSRC/affix/ElysiumAffixRules.java /tmp/ar.bak
edit $LSRC/affix/ElysiumAffixRules.java 'import java.util.List;' 'import java.util.List;
import com.google.gson.Gson;'
expect "the affix arithmetic reaching outside the JDK" "imports"
cp /tmp/ar.bak $LSRC/affix/ElysiumAffixRules.java

echo "the armory's half"
cp $ASRC/property/ArmoryGearType.java /tmp/agt.bak
sed -i 's/ElysiumGearTree/ArmoryOwnTreeCopy/g' $ASRC/property/ArmoryGearType.java
expecta "the armory keeping its own copy of the tree" "no longer reads the library's gear tree"
cp /tmp/agt.bak $ASRC/property/ArmoryGearType.java

cp $ASRC/loot/ArmoryLoot.java /tmp/al.bak
edit $ASRC/loot/ArmoryLoot.java 'CONDITIONS.register("gear"' 'CONDITIONS.register("modular_gear"'
expecta "a loot condition registered under another name" "is not registered with"
cp /tmp/al.bak $ASRC/loot/ArmoryLoot.java

cp $ASRC/ElysiumArmory.java /tmp/am.bak
edit $ASRC/ElysiumArmory.java 'com.elysium.armory.loot.ArmoryLoot.register(modEventBus);' ''
expecta "loot types that never reach the registry" "never put on the mod bus"
cp /tmp/am.bak $ASRC/ElysiumArmory.java

cp $ASRC/loot/ArmoryGearCondition.java /tmp/agc.bak
edit $ASRC/loot/ArmoryGearCondition.java 'ElysiumItemEffects.level(tool, trait.get())' '0'
expecta "the loot condition reading traits outside the engine" "silently never match"
cp /tmp/agc.bak $ASRC/loot/ArmoryGearCondition.java

cp $ASRC/loot/ArmoryGearCondition.java /tmp/agc.bak
edit $ASRC/loot/ArmoryGearCondition.java 'String resolved = ElysiumLootCategory.of(tool);' 'String resolved = "all";'
expecta "a condition that cannot tell a hammer from a hoe" "could not be written"
cp /tmp/agc.bak $ASRC/loot/ArmoryGearCondition.java

echo "reachable from data"
cp $LSRC/loot/ElysiumRollAffixesFunction.java /tmp/rf.bak
edit $LSRC/loot/ElysiumRollAffixesFunction.java 'ElysiumAffixRoll.roll(stack, count.getInt(context), context.getRandom())' 'stack'
expect "a roller nothing can reach" "all written and all dead"
cp /tmp/rf.bak $LSRC/loot/ElysiumRollAffixesFunction.java

cp $LSRC/loot/ElysiumLoot.java /tmp/el.bak
edit $LSRC/loot/ElysiumLoot.java 'FUNCTIONS.register("roll_affixes"' 'FUNCTIONS.register("affixes"'
expect "the affix loot function registered under another name" "not registered with its own codec"
cp /tmp/el.bak $LSRC/loot/ElysiumLoot.java

cp $LSRC/ElysiumLib.java /tmp/lm.bak
edit $LSRC/ElysiumLib.java 'com.elysium.lib.loot.ElysiumLoot.register(modEventBus);' ''
expect "loot types that never reach the registry" "never reach the registry"
cp /tmp/lm.bak $LSRC/ElysiumLib.java

echo
echo "restoring and re-checking"
tar xzf $SNAP -C $ROOT
ok=1
(cd $LIB && python3 validate.py >/dev/null 2>&1) || ok=0
(cd $ARM && python3 validate.py >/dev/null 2>&1) || ok=0
if [ "$ok" = 1 ]; then echo "  tree restored clean";
else echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1)); fi
echo "affixes: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
