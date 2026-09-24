#!/usr/bin/env bash
# P8 — materials science, broken one rule at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
ARM=$ROOT/elysium-armory
SRC=$ARM/src/main/java/com/elysium/armory
RES=$ARM/src/main/resources
LANG=$RES/assets/elysiumarmory/lang/en_us.json
pass=0; fail=0

SNAP=/tmp/negtest_science_snapshot.tgz
tar czf $SNAP -C $ROOT elysium-armory/src elysium-armory/tools

out() { (cd $ARM && python3 validate.py 2>&1); }
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

echo "what mixing is worth"
cp $SRC/property/ArmoryMaterialScience.java /tmp/ms.bak
edit $SRC/property/ArmoryMaterialScience.java 'double harmony = (double) shared.size() / union.size();' \
                                              'double harmony = 1.0D;'
expect "materials with nothing in common paying as if they agreed" "gained something anyway"
cp /tmp/ms.bak $SRC/property/ArmoryMaterialScience.java

cp $SRC/property/ArmoryMaterialScience.java /tmp/ms.bak
edit $SRC/property/ArmoryMaterialScience.java 'union.addAll(one);' 'union.add("only");'
expect "harmony measured against nothing, so mixing pays more than the rate" "not one third of the rate"
cp /tmp/ms.bak $SRC/property/ArmoryMaterialScience.java

cp $SRC/property/ArmoryMaterialScience.java /tmp/ms.bak
edit $SRC/property/ArmoryMaterialScience.java 'return 1.0D + SYNERGY_RATE * (distinct - 1) * harmony;' \
                                              'return 1.0D + SYNERGY_RATE * harmony;'
expect "a third material worth nothing extra" "was worth nothing"
cp /tmp/ms.bak $SRC/property/ArmoryMaterialScience.java

echo "grades"
cp $SRC/property/ArmoryMaterialScience.java /tmp/ms.bak
edit $SRC/property/ArmoryMaterialScience.java 'return 1.0D - CRUDE_PENALTY;' 'return 1.0D;'
expect "Crude costing nothing" "so a failed grading costs nothing"
cp /tmp/ms.bak $SRC/property/ArmoryMaterialScience.java

cp $SRC/property/ArmoryMaterialScience.java /tmp/ms.bak
edit $SRC/property/ArmoryMaterialScience.java 'if (roll < failureChance(at)) {
            return at == UNGRADED ? CRUDE : at;
        }' 'if (roll < failureChance(at)) {
            return at - 1;
        }'
expect "a bad roll undoing work already done" "punishment rather than a risk"
cp /tmp/ms.bak $SRC/property/ArmoryMaterialScience.java

cp $SRC/property/ArmoryMaterialScience.java /tmp/ms.bak
edit $SRC/property/ArmoryMaterialScience.java 'double at = Math.max(0, clampGrade(grade)) + 1.0D;
        return at / (at + 8.0D);' 'return 0.0D;'
expect "grading that can never fail" "a free ratchet"
cp /tmp/ms.bak $SRC/property/ArmoryMaterialScience.java

cp $SRC/property/ArmoryMaterialScience.java /tmp/ms.bak
edit $SRC/property/ArmoryMaterialScience.java 'return Math.max(CRUDE, Math.min(MAX_GRADE, grade));' 'return grade;'
expect "a grade from a hand-edited save read as written" "outside the range the rules cover"
cp /tmp/ms.bak $SRC/property/ArmoryMaterialScience.java

cp $SRC/property/ArmoryMaterialScience.java /tmp/ms.bak
edit $SRC/property/ArmoryMaterialScience.java 'public static final double STARCHARGE_FACTOR = 1.15D;' \
                                              'public static final double STARCHARGE_FACTOR = 1.0D;'
expect "a charge that is worth nothing" "was worth nothing"
cp /tmp/ms.bak $SRC/property/ArmoryMaterialScience.java

echo "the benches"
cp $SRC/craft/ArmoryAlloy.java /tmp/aa.bak
edit $SRC/craft/ArmoryAlloy.java 'marks(part).synergy() <= 1.0D' 'true'
expect "a part that can be forged over and over" "as many times as they like"
cp /tmp/aa.bak $SRC/craft/ArmoryAlloy.java

cp $SRC/craft/ArmoryAlloy.java /tmp/aa.bak
edit $SRC/craft/ArmoryAlloy.java 'out.set(ArmoryComponents.MARKS.get(), marks(part).withSynergy(synergyOf(part)));' ''
expect "a forge that computes and writes nothing" "forging a part does nothing"
cp /tmp/aa.bak $SRC/craft/ArmoryAlloy.java

cp $SRC/craft/ArmoryGrading.java /tmp/ag.bak
edit $SRC/craft/ArmoryGrading.java 'return isPart(part) && !marks(part).starcharged();' 'return isPart(part);'
expect "a part that can take the charge twice" "more than once"
cp /tmp/ag.bak $SRC/craft/ArmoryGrading.java

cp $SRC/block/ArmoryMaterialGraderBlock.java /tmp/gb.bak
edit $SRC/block/ArmoryMaterialGraderBlock.java 'payment.shrink(1);' ''
expect "a grading that is free when it fails" "free attempt with extra steps"
cp /tmp/gb.bak $SRC/block/ArmoryMaterialGraderBlock.java

cp $SRC/block/ArmoryStarlightChargerBlockEntity.java /tmp/cb.bak
edit $SRC/block/ArmoryStarlightChargerBlockEntity.java 'if (!level.canSeeSky(pos.above()) || level.isDay()) {
            return;                         // no sky, or no stars in it
        }' ''
expect "a charger that works under a roof" "under a roof"
cp /tmp/cb.bak $SRC/block/ArmoryStarlightChargerBlockEntity.java

cp $SRC/block/ArmoryStarlightChargerBlock.java /tmp/cc.bak
edit $SRC/block/ArmoryStarlightChargerBlock.java 'Block.popResource(level, pos, charger.take());' ''
expect "a charger that swallows what is on it" "swallows what is on it"
cp /tmp/cc.bak $SRC/block/ArmoryStarlightChargerBlock.java

echo "what a mark is for"
cp $SRC/item/ArmoryGearLogic.java /tmp/gl.bak
edit $SRC/item/ArmoryGearLogic.java '        ArmoryMarks marks = marksOf(stack);
        if (!marks.isPlain()) {
            double factor = marks.factor();
            for (java.util.Map.Entry<String, Double> entry : values.entrySet()) {
                entry.setValue(entry.getValue() * factor);
            }
        }
' ''
expect "marks that change nothing about the gear" "would change nothing about the gear"
cp /tmp/gl.bak $SRC/item/ArmoryGearLogic.java

cp $SRC/item/ArmoryBuilder.java /tmp/ab.bak
edit $SRC/item/ArmoryBuilder.java 'ArmoryMarks marks = combine(parts);' 'ArmoryMarks marks = ArmoryMarks.PLAIN;'
expect "marks that never reach the gear" "never reach the gear"
cp /tmp/ab.bak $SRC/item/ArmoryBuilder.java

cp $SRC/item/ArmoryBuilder.java /tmp/ab.bak
edit $SRC/item/ArmoryBuilder.java 'ArmoryMaterial named = com.elysium.armory.material.ArmoryCompounds.named(resolved);' \
                                  'ArmoryMaterial named = null;'
expect "an alloy named after whichever metal was first" "laid down first"
cp /tmp/ab.bak $SRC/item/ArmoryBuilder.java

cp $SRC/material/ArmoryCompounds.java /tmp/ac.bak
edit $SRC/material/ArmoryCompounds.java 'return all(distinct, ArmoryCategory.BASIC) ? CRUDE_ALLOY : METAL_ALLOY;' \
                                        'return METAL_ALLOY;'
expect "a named mixture nothing can ever be" "no mixture can ever be"
cp /tmp/ac.bak $SRC/material/ArmoryCompounds.java

cp $LANG /tmp/lg.bak
edit $LANG '"block.elysiumarmory.starlight_charger"' '"block.elysiumarmory.star_charger"'
expect "a bench with no name" "has no name"
cp /tmp/lg.bak $LANG

cp $LANG /tmp/lg.bak
edit $LANG '"elysiumarmory.grader.needs_gem"' '"elysiumarmory.grader.wants_gem"'
expect "a bench that speaks in raw keys" "speak in raw keys"
cp /tmp/lg.bak $LANG

echo
echo "restoring and re-checking"
tar xzf $SNAP -C $ROOT
if (cd $ARM && python3 validate.py >/dev/null 2>&1); then echo "  tree restored clean";
else echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1)); fi
echo "science: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
