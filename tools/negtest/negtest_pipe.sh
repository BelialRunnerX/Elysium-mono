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
  bash -c "$break_it" || { echo "SETUP FAILED: $label"; restore; return; }
  if timeout 1800 python3 validate.py 2>&1 | grep -qF "$want"; then
    echo "CAUGHT : $label"; pass=$((pass+1))
  else
    echo "MISSED : $label  (expected: $want)"; miss=$((miss+1))
  fi
  restore
}

try "one arm's collision box shifted a pixel" "have drifted apart" \
  "sed -i 's|Block.box(11, 6, 6, 16, 10, 10)|Block.box(12, 6, 6, 16, 10, 10)|' $J/PowerConduitBlock.java"

try "up and down arms swapped" "have drifted apart" \
  "python3 - <<'PY'
import io,re
p='$J/PowerConduitBlock.java'
s=io.open(p,encoding='utf-8').read()
boxes=dict(re.findall(r'ARM_SHAPES\\.put\\(Direction\\.([A-Z]+), (Block\\.box\\([^)]*\\))\\)', s))
s=s.replace('ARM_SHAPES.put(Direction.UP, '+boxes['UP']+')','ARM_SHAPES.put(Direction.UP, '+boxes['DOWN']+')')
s=s.replace('ARM_SHAPES.put(Direction.DOWN, '+boxes['DOWN']+')','ARM_SHAPES.put(Direction.DOWN, '+boxes['UP']+')')
io.open(p,'w',encoding='utf-8').write(s)
PY"

try "the model's core resized, the collision not" "a player would stand inside" \
  "sed -i 's|^PIPE_CORE = (5, 5, 5, 11, 11, 11)|PIPE_CORE = (4, 4, 4, 12, 12, 12)|' tools/gen_solarpunk.py"

try "an arm dropped from the Java" "no longer declares a core and six arm shapes" \
  "sed -i '/ARM_SHAPES.put(Direction.WEST/d' $J/PowerConduitBlock.java"

try "the multipart switches on a property that is not there" "renders as the" \
  "python3 - <<'PY'
import io,json
p='$R/blockstates/power_conduit.json'
d=json.load(open(p))
for case in d['multipart']:
    if case.get('when',{}).get('west'):
        case['when']={'facing':'true'}
json.dump(d,open(p,'w'),indent=2)
PY"

try "the core case deleted from the multipart" "it wants a core and one arm per face" \
  "python3 - <<'PY'
import io,json
p='$R/blockstates/power_conduit.json'
d=json.load(open(p))
d['multipart']=[c for c in d['multipart'] if c.get('when')]
json.dump(d,open(p,'w'),indent=2)
PY"

try "an arm case deleted from the multipart" "renders as the" \
  "python3 - <<'PY'
import io,json
p='$R/blockstates/power_conduit.json'
d=json.load(open(p))
d['multipart']=[c for c in d['multipart'] if c.get('when',{}).get('west') is None]
json.dump(d,open(p,'w'),indent=2)
PY"

try "the arm draws its outward face" "z-fights with the block it reaches into" \
  "python3 - <<'PY'
import json
p='$R/models/block/power_conduit_arm.json'
d=json.load(open(p))
d['elements'][0]['faces']['north']={'uv':[0,0,16,16],'texture':'#pipe'}
json.dump(d,open(p,'w'),indent=2)
PY"

try "the core loses a face" "so a pipe with no arm on that side is see-through" \
  "python3 - <<'PY'
import json
p='$R/models/block/power_conduit_core.json'
d=json.load(open(p))
d['elements'][0]['faces'].pop('up')
json.dump(d,open(p,'w'),indent=2)
PY"

echo "---- caught $pass, missed $miss"
