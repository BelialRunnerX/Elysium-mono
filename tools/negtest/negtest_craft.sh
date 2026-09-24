#!/bin/bash
cd /tmp/Elysium-mono/elysium-lib || exit 1
S=src/main/java/com/elysium/lib
L=src/main/resources/assets/elysiumlib/lang/en_us.json
pass=0; miss=0
snap() { rm -rf /tmp/_cf; mkdir -p /tmp/_cf; cp -r $S/craft /tmp/_cf/craft; cp $S/event/ElysiumCraftHandler.java /tmp/_cf/; cp $L /tmp/_cf/lang.json; }
restore() { rm -rf $S/craft; cp -r /tmp/_cf/craft $S/craft; cp /tmp/_cf/ElysiumCraftHandler.java $S/event/; cp /tmp/_cf/lang.json $L; }
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

try "quality sourced from research" "which is the one thing" \
  "python3 -c \"
import io
p='$S/craft/ElysiumCraftLog.java'
s=io.open(p,encoding='utf-8').read()
s=s.replace('import com.elysium.lib.ElysiumLib;','import com.elysium.lib.ElysiumLib;\nimport com.elysium.lib.research.ElysiumResearch;',1)
io.open(p,'w',encoding='utf-8').write(s)
\""

try "the count raised before the grade is read" "free grade on their first attempt" \
  "python3 -c \"
import io
p='$S/event/ElysiumCraftHandler.java'
s=io.open(p,encoding='utf-8').read()
s=s.replace('''        ElysiumQuality grade = ElysiumCraftLog.gradeFor(player, made);''','''        ElysiumCraftLog.record(player, made, Math.max(1, made.getCount()));
        ElysiumQuality grade = ElysiumCraftLog.gradeFor(player, made);''',1)
s=s.replace('''        // After the stamp. See the note above on why the order is the feature.
        ElysiumCraftLog.record(player, made, Math.max(1, made.getCount()));
''','')
io.open(p,'w',encoding='utf-8').write(s)
\""

try "marks put on things with no durability" "a grade that means nothing" \
  "sed -i 's|if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) {|if (stack == null || stack.isEmpty()) {|' $S/event/ElysiumCraftHandler.java"

try "other mods' items signed" "rewriting somebody else" \
  "sed -i 's|        return ElysiumCraftLog.keyFor(stack).startsWith(NAMESPACE_PREFIX + \":\");|        return true;|' $S/event/ElysiumCraftHandler.java"

try "quality given an attribute" "belongs in the damage pipeline" \
  "sed -i 's|^import java.lang.Object;||; s|public String translationKey() {|public String attributeNote() { return \"Attributes.ATTACK_DAMAGE\"; }\n\n    public String translationKey() {|' $S/craft/ElysiumQuality.java"

try "the mark stops being synced" "no longer both saved and synced" \
  "sed -i 's|.networkSynchronized(STREAM_CODEC)||' $S/craft/ElysiumMakersMark.java"

try "a grade with no words" "would render as the raw key" \
  "python3 -c \"
import json,collections
p='$L'
d=json.load(open(p,encoding='utf-8'),object_pairs_hook=collections.OrderedDict)
d.pop('elysium.craft.quality.fine')
json.dump(d,open(p,'w',encoding='utf-8'),indent=2,ensure_ascii=False)
\""

try "a registry dragged into the quality ladder" "takes the whole system out of the tests" \
  "sed -i 's|^package com.elysium.lib.craft;|package com.elysium.lib.craft;\n\nimport net.minecraft.world.item.ItemStack;|' $S/craft/ElysiumQuality.java"

try "the ladder file starts registering" "has started registering something" \
  "sed -i 's|    private final String id;|    private static final Object DeferredRegister = null;\n\n    private final String id;|' $S/craft/ElysiumQuality.java"

try "a first attempt made no worse than ordinary" "no bottom rung" \
  "sed -i 's|    CRUDE(\"crude\", 0, 0.80F)|    CRUDE(\"crude\", 0, 1.00F)|' $S/craft/ElysiumQuality.java"

try "the spread made enormous" "stops being flavour" \
  "sed -i 's|    MASTERWORK(\"masterwork\", 128, 1.50F)|    MASTERWORK(\"masterwork\", 128, 4.00F)|' $S/craft/ElysiumQuality.java"

try "two grades made identical" "two grades that do the same thing" \
  "sed -i 's|    FINE(\"fine\", 48, 1.30F)|    FINE(\"fine\", 48, 1.15F)|' $S/craft/ElysiumQuality.java"

try "the counting ceiling put below mastery" "they are one number written twice" \
  "sed -i 's|    public static final int COUNT_CEILING = 129;|    public static final int COUNT_CEILING = 64;|' $S/craft/ElysiumQuality.java"

echo "---- caught $pass, missed $miss"
