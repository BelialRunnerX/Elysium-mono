#!/usr/bin/env bash
# P4 — one item-effect engine, broken one rule at a time.
#
# The trait engine is data on one side and code on the other: a trait file says
# what it does, and something in the mod has to read that kind of effect. Every
# check that holds the two together is deliberately defeated here, and the
# suite fails if the validator or the harness lets it through.
set -uo pipefail
ROOT=/tmp/Elysium-mono/elysium-armory
SRC=$ROOT/src/main/java/com/elysium/armory
RES=$ROOT/src/main/resources
TRAITS=$RES/data/elysiumarmory/elysium_traits
LANG=$RES/assets/elysiumarmory/lang/en_us.json
PURE=$ROOT/src/main/java/com/elysium/armory/property/ArmoryTraitRules.java
GEN=$ROOT/tools/gen_traits.py
pass=0; fail=0

out() { (cd $ROOT && python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }

edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@"; }

echo "an effect kind with nobody on the other end"
cp $SRC/trait/ArmoryTraitHandlers.java /tmp/th.bak
edit $SRC/trait/ArmoryTraitHandlers.java '"item_magnet"' '"item_magnetism"'
expect "the magnet effect renamed on the code side only" "nothing in the mod reads one"
cp /tmp/th.bak $SRC/trait/ArmoryTraitHandlers.java

cp $SRC/trait/ArmoryTraitHandlers.java /tmp/th.bak
edit $SRC/trait/ArmoryTraitHandlers.java '"synergy"' '"synergy_bonus"'
expect "the synergy reader added this phase, removed again" "nothing in the mod reads one"
cp /tmp/th.bak $SRC/trait/ArmoryTraitHandlers.java

cp $SRC/trait/ArmoryTraitProperties.java /tmp/tp.bak
edit $SRC/trait/ArmoryTraitProperties.java '"enchantment"' '"enchant"'
expect "traits that grant enchantments, granting none" "nothing in the mod reads one"
cp /tmp/tp.bak $SRC/trait/ArmoryTraitProperties.java

cp $SRC/item/ArmoryGearLogic.java /tmp/gl.bak
edit $SRC/item/ArmoryGearLogic.java '"block_filler"' '"block_fill"'
expect "the filler effect read under another name" "nothing in the mod reads one"
cp /tmp/gl.bak $SRC/item/ArmoryGearLogic.java

echo "behaviour that says it is code"
cp $SRC/trait/ArmoryCodedTraits.java /tmp/ct.bak
edit $SRC/trait/ArmoryCodedTraits.java 'public static final String VOID_WARD = "void_ward";' \
                                       'public static final String VOID_WARD = "voidward";'
expect "a coded trait whose id no longer matches its file" "behaviour is code, and it is not there"
cp /tmp/ct.bak $SRC/trait/ArmoryCodedTraits.java

cp $SRC/trait/ArmoryCodedTraits.java /tmp/ct.bak
edit $SRC/trait/ArmoryCodedTraits.java 'public static final String MULTI_BREAK = "multi_break";' ''
expect "the trait Silent Gear never wrote, quietly dropped" "behaviour is code, and it is not there"
cp /tmp/ct.bak $SRC/trait/ArmoryCodedTraits.java

echo "written but never run"
cp $SRC/item/ArmoryArmorItem.java /tmp/ai.bak
edit $SRC/item/ArmoryArmorItem.java 'ArmoryCodedTraits.snowWalker(wearer)' 'false'
expect "snow walker written and unplugged" "written and never called"
cp /tmp/ai.bak $SRC/item/ArmoryArmorItem.java

cp $SRC/item/ArmoryArmorItem.java /tmp/ai.bak
edit $SRC/item/ArmoryArmorItem.java 'ArmoryCodedTraits.brilliant(stack)' 'false'
expect "brilliant no longer read by anything piglins ask" "written and never called"
cp /tmp/ai.bak $SRC/item/ArmoryArmorItem.java

cp $SRC/item/ArmoryGearItem.java /tmp/gi.bak
edit $SRC/item/ArmoryGearItem.java 'com.elysium.armory.trait.ArmoryCodedTraits.spoonMines(stack, state)' 'false'
expect "the spoon that no longer digs" "written and never called"
cp /tmp/gi.bak $SRC/item/ArmoryGearItem.java

cp $SRC/event/ArmoryToolEvents.java /tmp/te.bak
edit $SRC/event/ArmoryToolEvents.java 'com.elysium.armory.trait.ArmoryCodedTraits.widen(stack)' '0'
expect "a wide tool whose width stopped coming from Widen" "the trait is decoration"
cp /tmp/te.bak $SRC/event/ArmoryToolEvents.java

echo "the rules underneath"
cp $PURE /tmp/tr.bak
edit $PURE '        return false;
    }

    private static double number' '        return true;
    }

    private static double number'
expect "a condition nobody understands, granted anyway" "granted by accident"
cp /tmp/tr.bak $PURE

cp $PURE /tmp/tr.bak
edit $PURE 'Math.max(1.0D, Math.min(total / 2.0D, count))' '1.0D'
expect "several small grants adding up instead of averaging" "which is not an average"
cp /tmp/tr.bak $PURE

cp $PURE /tmp/tr.bak
edit $PURE 'return maxLevel <= 0 ? level : Math.min(level, maxLevel);' \
           'return Math.min(level, maxLevel <= 0 ? 1 : maxLevel);'
expect "a trait level capped where the spec says uncapped" "not capped where its effects are read"
cp /tmp/tr.bak $PURE

cp $PURE /tmp/tr.bak
edit $PURE 'import java.util.List;' 'import java.util.List; import com.google.gson.Gson;'
expect "the rules reaching outside the JDK contract" "imports nothing but the JDK"
cp /tmp/tr.bak $PURE

echo "the data and the words for it"
cp $LANG /tmp/lg.bak
edit $LANG '"elysiumarmory.trait.void_ward.desc"' '"elysiumarmory.trait.void_ward.blurb"'
expect "a trait nobody can read the meaning of" "has no elysiumarmory.trait.void_ward.desc"
cp /tmp/lg.bak $LANG

cp $TRAITS/void_ward.json /tmp/vw.bak
edit $TRAITS/void_ward.json '"max_level"' '"maximum_level"'
expect "a trait file edited by hand, away from its generator" "not what tools/gen_traits.py generates"
cp /tmp/vw.bak $TRAITS/void_ward.json

cp $GEN /tmp/gt.bak
edit $GEN 'import json' 'import nonexistent_module_xyz
import json'
expect "the generator itself no longer running" "no longer runs"
cp /tmp/gt.bak $GEN

echo
echo "restoring and re-checking"
if (cd $ROOT && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
echo "traits: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
