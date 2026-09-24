#!/bin/bash
cd /tmp/Elysium-mono/elysium-lib || exit 1
S=src/main/java/com/elysium/lib
L=src/main/resources/assets/elysiumlib/lang/en_us.json
pass=0; miss=0
snap() { rm -rf /tmp/_st; mkdir -p /tmp/_st; cp -r $S/stress /tmp/_st/stress; cp $S/event/ElysiumSocialHandler.java /tmp/_st/; cp $L /tmp/_st/lang.json; }
restore() { rm -rf $S/stress; cp -r /tmp/_st/stress $S/stress; cp /tmp/_st/ElysiumSocialHandler.java $S/event/; cp /tmp/_st/lang.json $L; }
try() {
  local label="$1" want="$2" break_it="$3"
  snap
  bash -c "$break_it" || { echo "SETUP FAILED: $label"; restore; return; }
  if timeout 1800 python3 validate.py 2>&1 | grep -qF "$want"; then
    echo "CAUGHT : $label"; pass=$((pass+1))
  else
    echo "MISSED : $label  (expected: $want)"; miss=$((miss+1))
  fi
  restore
}

try "a Minecraft import in the band model" "only stays walkable while these" \
  "sed -i 's|^package com.elysium.lib.stress;|package com.elysium.lib.stress;\n\nimport net.minecraft.world.level.Level;|' $S/stress/ElysiumStressBand.java"

try "the field starts saving a total" "a stored total is a number that cannot" \
  "sed -i 's|^import net.minecraft.core.BlockPos;|import net.minecraft.core.BlockPos;\nimport net.neoforged.neoforge.attachment.AttachmentType;|' $S/stress/ElysiumStressField.java"

try "a second attachment appears" "wearing a different name" \
  "python3 -c \"
import io
p='$S/stress/ElysiumStressRecord.java'
s=io.open(p,encoding='utf-8').read()
s=s.replace('ATTACHMENTS.register(\\\"stress_peak\\\"','ATTACHMENTS.register(\\\"stress_total\\\"',1)
io.open(p,'w',encoding='utf-8').write(s)
\""

try "the total stops being floored" "banks relief against a future disaster" \
  "sed -i 's|        return Math.max(0, sum);|        return sum;|' $S/stress/ElysiumStress.java"

try "the readout drops the reasons" "told a band and not why" \
  "sed -i 's|        for (ElysiumStressor reason : reading.ordered()) {|        for (ElysiumStressor reason : java.util.List.<ElysiumStressor>of()) {|' $S/event/ElysiumSocialHandler.java"

try "the readout drops the weights" "it is a description of one" \
  "python3 -c \"
import io
p='$S/event/ElysiumSocialHandler.java'
s=io.open(p,encoding='utf-8').read()
s=s.replace('''.append(Component.literal(\\\" (\\\" + (reason.weight() > 0 ? \\\"+\\\" : \\\"\\\")
                                    + reason.weight() + \\\")\\\")
                            .withStyle(ChatFormatting.DARK_GRAY))''','''.append(Component.literal(\\\"\\\"))''',1)
io.open(p,'w',encoding='utf-8').write(s)
\""

try "a refusal with no reason" "indistinguishable from a bug" \
  "sed -i 's|            ElysiumStressor worst = stress.worst();|            ElysiumStressor worst = null;|' $S/event/ElysiumSocialHandler.java"

try "a stressor with no words" "stress key(s)" \
  "python3 -c \"
import json,collections
p='$L'
d=json.load(open(p,encoding='utf-8'),object_pairs_hook=collections.OrderedDict)
d.pop('elysium.stress.reason.homeless')
json.dump(d,open(p,'w',encoding='utf-8'),indent=2,ensure_ascii=False)
\""

try "relief made stronger than every burden" "would be immune to both at once" \
  "sed -i 's|    AT_HOME(\"at_home\", -20)|    AT_HOME(\"at_home\", -400)|' $S/stress/ElysiumStressor.java"

try "comfort made worthless" "building a good village does nothing" \
  "sed -i 's|    AT_HOME(\"at_home\", -20)|    AT_HOME(\"at_home\", -1)|; s|    SHELTERED(\"sheltered\", -15)|    SHELTERED(\"sheltered\", -1)|; s|    AMONG_FRIENDS(\"among_friends\", -10)|    AMONG_FRIENDS(\"among_friends\", -1)|' $S/stress/ElysiumStressor.java"

try "crisis made unreachably expensive" "which no player will ever see happen" \
  "sed -i 's|    CRISIS(\"crisis\", 75)|    CRISIS(\"crisis\", 160)|' $S/stress/ElysiumStressBand.java"

try "crisis made too cheap" "an ordinary bad afternoon shuts the village" \
  "sed -i 's|    CRISIS(\"crisis\", 75)|    CRISIS(\"crisis\", 28)|' $S/stress/ElysiumStressBand.java"

try "recovery treated as a level" "counts as at least" \
  "python3 -c \"
import io
p='$S/stress/ElysiumStressBand.java'
s=io.open(p,encoding='utf-8').read()
s=s.replace('''        if (this == RECOVERY || other == RECOVERY) {
            return false;
        }''','')
io.open(p,'w',encoding='utf-8').write(s)
\""

try "the peak overwritten by improvement" "getting better overwrote the peak" \
  "sed -i 's|        return candidate.atLeast(peak) \&\& candidate != peak;|        return candidate != peak;|' $S/stress/ElysiumStress.java"

echo "---- caught $pass, missed $miss"
