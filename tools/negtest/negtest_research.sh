#!/bin/bash
cd /tmp/Elysium-mono/elysium-lib || exit 1
R=src/main/java/com/elysium/lib/research
L=src/main/resources/assets/elysiumlib/lang/en_us.json
pass=0; miss=0
snap() { rm -rf /tmp/_rs; mkdir -p /tmp/_rs; cp -r $R /tmp/_rs/research; cp $L /tmp/_rs/lang.json; cp tools/ResearchHarness.java /tmp/_rs/; }
restore() { rm -rf $R; cp -r /tmp/_rs/research $R; cp /tmp/_rs/lang.json $L; cp /tmp/_rs/ResearchHarness.java tools/; }
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

try "a Minecraft import in the deed table" "only stays executable while these" \
  "sed -i 's|^package com.elysium.lib.research;|package com.elysium.lib.research;\n\nimport net.minecraft.world.level.Level;|' $R/ElysiumDeed.java"

try "an unlock nothing reads" "promise the game makes aloud" \
  "python3 - <<'PY'
import io
p='$R/ElysiumUnlock.java'
s=io.open(p,encoding='utf-8').read()
s=s.replace('    BENCH_TEARDOWN(\"bench_teardown\", ElysiumResearchField.ENGINEERING,\n            ElysiumResearchRank.FAMILIAR);',
 '    BENCH_TEARDOWN(\"bench_teardown\", ElysiumResearchField.ENGINEERING,\n            ElysiumResearchRank.FAMILIAR),\n    GHOST_UNLOCK(\"ghost_unlock\", ElysiumResearchField.ENGINEERING,\n            ElysiumResearchRank.ADEPT);')
io.open(p,'w',encoding='utf-8').write(s)
PY"

try "an unlock that grants power" "never power" \
  "python3 - <<'PY'
import json,collections
p='$L'
d=json.load(open(p,encoding='utf-8'),object_pairs_hook=collections.OrderedDict)
d['elysium.research.unlock.bench_teardown.blurb']='Grants a bonus to the gear on the bench.'
json.dump(d,open(p,'w',encoding='utf-8'),indent=2,ensure_ascii=False)
PY"

try "an unlock described as neither" "says nothing about what the player" \
  "python3 - <<'PY'
import json,collections
p='$L'
d=json.load(open(p,encoding='utf-8'),object_pairs_hook=collections.OrderedDict)
d['elysium.research.unlock.survey_depth.blurb']='Something happens at the scanner.'
json.dump(d,open(p,'w',encoding='utf-8'),indent=2,ensure_ascii=False)
PY"

try "a missing language key" "research key(s)" \
  "python3 - <<'PY'
import json,collections
p='$L'
d=json.load(open(p,encoding='utf-8'),object_pairs_hook=collections.OrderedDict)
d.pop('elysium.research.rank.adept')
json.dump(d,open(p,'w',encoding='utf-8'),indent=2,ensure_ascii=False)
PY"

try "novelty stops being enforced" "can be farmed by" \
  "sed -i 's|        if (deed.novel()) {|        if (false) {|' $R/ElysiumResearch.java"

try "the ceiling removed" "counts past the end of its own track" \
  "sed -i 's|int now = Math.min(CEILING, was + points);|int now = was + points;|' $R/ElysiumResearch.java"

try "exposure made persistent" "has become persistent" \
  "sed -i 's|^import net.minecraft.world.entity.player.Player;|import net.minecraft.world.entity.player.Player;\nimport net.neoforged.neoforge.attachment.AttachmentType;|' $R/ElysiumExposure.java"

try "exposure unbounded" "is unbounded" \
  "sed -i 's|CAPACITY|LIMIT|g' $R/ElysiumExposure.java"

try "a field with no deed feeding it" "has no deed feeding it" \
  "sed -i 's|SCANNED_MINERAL(ElysiumResearchField.GEOLOGY, 10, true)|SCANNED_MINERAL(ElysiumResearchField.XENOBIOLOGY, 10, true)|; s|CUT_A_VEIN(ElysiumResearchField.GEOLOGY, 1, false)|CUT_A_VEIN(ElysiumResearchField.XENOBIOLOGY, 1, false)|' $R/ElysiumDeed.java"

try "a repeatable deed worth more than a discovery" "Grinding now beats exploring" \
  "sed -i 's|WORKED_A_BENCH(ElysiumResearchField.ENGINEERING, 2, false)|WORKED_A_BENCH(ElysiumResearchField.ENGINEERING, 9, false)|' $R/ElysiumDeed.java"

try "the bands put out of order" "not above" \
  "sed -i 's|ADEPT(\"adept\", 300)|ADEPT(\"adept\", 90)|' $R/ElysiumResearchRank.java"

try "a field whose first reward is too far up" "A field whose first reward is that far up" \
  "sed -i 's|    SURVEY_DEPTH(\"survey_depth\", ElysiumResearchField.GEOLOGY,\n            ElysiumResearchRank.FAMILIAR)|X|' $R/ElysiumUnlock.java; python3 -c \"
import io
p='$R/ElysiumUnlock.java'
s=io.open(p,encoding='utf-8').read()
s=s.replace('SURVEY_DEPTH(\\\"survey_depth\\\", ElysiumResearchField.GEOLOGY,\n            ElysiumResearchRank.FAMILIAR)','SURVEY_DEPTH(\\\"survey_depth\\\", ElysiumResearchField.GEOLOGY,\n            ElysiumResearchRank.AUTHORITY)',1)
io.open(p,'w',encoding='utf-8').write(s)
\""

try "an unlock granted on the very last point" "granted on the last point" \
  "python3 -c \"
import io
p='$R/ElysiumUnlock.java'
s=io.open(p,encoding='utf-8').read()
s=s.replace('ElysiumResearchRank.PRACTISED),\n\n    // --- Imperial','ElysiumResearchRank.PREEMINENT),\n\n    // --- Imperial',1)
io.open(p,'w',encoding='utf-8').write(s)
\""

echo "---- caught $pass, missed $miss"
