#!/bin/bash
cd /tmp/Elysium-mono/elysium-solarpunk || exit 1
J=src/main/java/com/elysium/solarpunk/power
R=src/main/resources/assets/elysiumsolarpunk
pass=0; miss=0
snap() { rm -rf /tmp/_pp; mkdir -p /tmp/_pp; cp $J/PowerConduitBlock.java /tmp/_pp/; cp tools/gen_solarpunk.py /tmp/_pp/; cp -r $R/blockstates /tmp/_pp/bs; cp -r $R/models /tmp/_pp/models; }
restore() { cp /tmp/_pp/PowerConduitBlock.java $J/; cp /tmp/_pp/gen_solarpunk.py tools/; rm -rf $R/blockstates $R/models; cp -r /tmp/_pp/bs $R/blockstates; cp -r /tmp/_pp/models $R/models; }
try() {
  local label="$1" want="$2" break_it="$3"
  snap
  bash -c "$break_it" || { echo "SETUP FAILED: $label"; miss=$((miss+1)); restore; return; }
  if timeout 1800 python3 validate.py 2>&1 | grep -qF "$want"; then
    echo "CAUGHT : $label"; pass=$((pass+1))
  else
    echo "MISSED : $label  (expected: $want)"; miss=$((miss+1))
  fi
  restore
}


# The conduit became a run on 27 Sep (1d7d704d): its core, arm, plate and
# blockstate are art/runs.py's, the same as every pipe, cable, wire, bus and
# channel, and its rules moved to art/runs.py check(). Until 17 Oct 2026 the
# collision, multipart and core-face rules these cases break had been left
# behind in that move, so every case missed; they are in check() again, for
# every run. Each edit below fails its setup when its anchor is gone, rather
# than letting a sed that matched nothing test the clean tree.

# The arms are 6 pixels across since the runs (RunRules ARM_FROM 5, ARM_TO 11).
try "one arm's collision box shifted a pixel" "have drifted apart" \
  "grep -qF 'Block.box(11, 5, 5, 16, 11, 11)' $J/PowerConduitBlock.java && sed -i 's|Block.box(11, 5, 5, 16, 11, 11)|Block.box(12, 5, 5, 16, 11, 11)|' $J/PowerConduitBlock.java"

try "up and down arms swapped" "have drifted apart" \
  "python3 - <<'PY'
import io,re
p='$J/PowerConduitBlock.java'
s=io.open(p,encoding='utf-8').read()
boxes=dict(re.findall(r'ARM_SHAPES\\.put\\(Direction\\.([A-Z]+), (Block\\.box\\([^)]*\\))\\)', s))
assert boxes.get('UP') and boxes.get('DOWN') and boxes['UP'] != boxes['DOWN'], 'pattern missing: ARM_SHAPES.put(Direction.UP/DOWN'
s=s.replace('ARM_SHAPES.put(Direction.UP, '+boxes['UP']+')','ARM_SHAPES.put(Direction.UP, '+boxes['DOWN']+')')
s=s.replace('ARM_SHAPES.put(Direction.DOWN, '+boxes['DOWN']+')','ARM_SHAPES.put(Direction.DOWN, '+boxes['UP']+')')
io.open(p,'w',encoding='utf-8',newline='').write(s)
PY"

# The model's core is the shipped power_conduit_core.json, which art/runs.py
# writes from RunRules. The generator's PIPE_CORE this case used to edit is no
# longer read for the conduit (_write_pipe hands it to runs.write_all and
# returns), so editing it changed nothing a player sees.
try "the model's core resized, the collision not" "a player would stand inside" \
  "python3 - <<'PY'
import json
p='$R/models/block/power_conduit_core.json'
d=json.load(open(p,encoding='utf-8'))
e=d['elements'][0]
assert e['from']==[5,5,5] and e['to']==[11,11,11], 'pattern missing: the core from [5,5,5] to [11,11,11]'
e['from']=[4,4,4]; e['to']=[12,12,12]
json.dump(d,open(p,'w',encoding='utf-8',newline=''),indent=2)
PY"

try "an arm dropped from the Java" "no longer declares a core and six arm shapes" \
  "grep -qF 'ARM_SHAPES.put(Direction.WEST' $J/PowerConduitBlock.java && sed -i '/ARM_SHAPES.put(Direction.WEST/d' $J/PowerConduitBlock.java"

# The run's cases switch on the six sides, each none, run or device; the west
# plate's case is the one with a plain west key.
try "the multipart switches on a property that is not there" "renders as the" \
  "python3 - <<'PY'
import io,json
p='$R/blockstates/power_conduit.json'
d=json.load(open(p,encoding='utf-8'))
hit=0
for case in d['multipart']:
    if case.get('when',{}).get('west'):
        case['when']={'facing':'true'}; hit+=1
assert hit, 'pattern missing: a case on west'
json.dump(d,open(p,'w',encoding='utf-8',newline=''),indent=2)
PY"

try "the core case deleted from the multipart" "it wants a core and one arm per face" \
  "python3 - <<'PY'
import io,json
p='$R/blockstates/power_conduit.json'
d=json.load(open(p,encoding='utf-8'))
n=len(d['multipart'])
d['multipart']=[c for c in d['multipart'] if c.get('when')]
assert len(d['multipart'])==n-1, 'pattern missing: one unconditional core case'
json.dump(d,open(p,'w',encoding='utf-8',newline=''),indent=2)
PY"

# A run's arm case is an OR of a run and a device on that side, and its
# rule's words are the shared check's (the old "renders as the" came from the
# property rule, which a deleted case tripped only by taking west away).
try "an arm case deleted from the multipart" "draws no arm towards west" \
  "python3 - <<'PY'
import io,json
p='$R/blockstates/power_conduit.json'
d=json.load(open(p,encoding='utf-8'))
n=len(d['multipart'])
d['multipart']=[c for c in d['multipart'] if c.get('when')!={'OR':[{'west':'run'},{'west':'device'}]}]
assert len(d['multipart'])==n-1, 'pattern missing: the west arm case'
json.dump(d,open(p,'w',encoding='utf-8',newline=''),indent=2)
PY"

# Retired 17 Oct 2026: "the arm draws its outward face" (wanted "z-fights with
# the block it reaches into"). The run's arm (art/runs.py arm_model) draws all
# six faces by design and check() holds each to the body tint: a run meeting a
# run meets it back to back, and a device face gets the plate over it. The
# break this case made is the shipped model, so there is nothing to break.

try "the core loses a face" "so a pipe with no arm on that side is see-through" \
  "python3 - <<'PY'
import json
p='$R/models/block/power_conduit_core.json'
d=json.load(open(p,encoding='utf-8'))
d['elements'][0]['faces'].pop('up')
json.dump(d,open(p,'w',encoding='utf-8',newline=''),indent=2)
PY"

echo "---- caught $pass, missed $miss"
[ "$miss" -eq 0 ]
