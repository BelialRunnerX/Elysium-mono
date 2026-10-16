#!/bin/bash
cd /tmp/Elysium-mono/elysium-core || exit 1
T=src/main/resources/data/elysium/puffish_skills/categories/extraction_ledger
D=../elysium-lib/src/main/java/com/elysium/lib/doctrine
L=src/main/resources/assets/elysium/lang/en_us.json
pass=0; miss=0
snap() { rm -rf /tmp/_tr; mkdir -p /tmp/_tr; cp -r $T /tmp/_tr/tree; cp $D/ElysiumDoctrine.java /tmp/_tr/; cp $L /tmp/_tr/lang.json; }
restore() { rm -rf $T; cp -r /tmp/_tr/tree $T; cp /tmp/_tr/ElysiumDoctrine.java $D/; cp /tmp/_tr/lang.json $L; }
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

try "a tag the tree sets and nothing reads" "disagree about the tags" \
  "python3 -c \"
import json
p='$T/definitions.json'
d=json.load(open(p,encoding='utf-8'))
for r in d['sanctioned']['rewards']:
    if r['type']=='puffish_skills:command':
        r['data']['unlock_command']='tag @s add elysium.doctrine.invented'
        r['data']['lock_command']='tag @s remove elysium.doctrine.invented'
json.dump(d,open(p,'w',encoding='utf-8'),indent=2)
\""

try "a doctrine the mod reads and nothing sets" "disagree about the tags" \
  "sed -i 's|    ACCLIMATISED(\"acclimatised\", 1.0F, 1.0F, 0.20F),|    ACCLIMATISED(\"acclimatised\", 1.0F, 1.0F, 0.20F),\n    UNUSED_ONE(\"unused_one\", 1.0F, 1.0F, 0.10F),|' $D/ElysiumDoctrine.java"

try "an unlock with no matching lock" "permanently committed" \
  "python3 -c \"
import json
p='$T/definitions.json'
d=json.load(open(p,encoding='utf-8'))
for r in d['warranted']['rewards']:
    if r['type']=='puffish_skills:command':
        r['data']['lock_command']=''
json.dump(d,open(p,'w',encoding='utf-8'),indent=2)
\""

try "a skill naming a missing definition" "name a definition that does not exist" \
  "python3 -c \"
import json
p='$T/skills.json'
d=json.load(open(p,encoding='utf-8'))
d['braced']['definition']='nonexistent'
json.dump(d,open(p,'w',encoding='utf-8'),indent=2)
\""

try "a definition never placed" "never placed" \
  "python3 -c \"
import json
p='$T/definitions.json'
d=json.load(open(p,encoding='utf-8'))
d['orphan_skill']=dict(d['braced'])
json.dump(d,open(p,'w',encoding='utf-8'),indent=2)
\""

try "a stranded skill" "cannot be reached from the root" \
  "python3 -c \"
import json
p='$T/connections.json'
d=json.load(open(p,encoding='utf-8'))
d['normal']=[c for c in d['normal'] if c[1]!='close_order']
json.dump(d,open(p,'w',encoding='utf-8'),indent=2)
\""

try "a second root" "One is the design" \
  "python3 -c \"
import json
p='$T/skills.json'
d=json.load(open(p,encoding='utf-8'))
d['braced']['root']=True
json.dump(d,open(p,'w',encoding='utf-8'),indent=2)
\""

try "a connection to nowhere" "which is not a skill" \
  "python3 -c \"
import json
p='$T/connections.json'
d=json.load(open(p,encoding='utf-8'))
d['normal'].append(['enrolled','ghost_node'])
json.dump(d,open(p,'w',encoding='utf-8'),indent=2)
\""

try "an exclusive pair the mod does not oppose" "resolves silently" \
  "python3 -c \"
import json
p='$T/connections.json'
d=json.load(open(p,encoding='utf-8'))
d['exclusive']=[c for c in d['exclusive'] if 'warranted' not in c]
json.dump(d,open(p,'w',encoding='utf-8'),indent=2)
\""

try "a node with no reward" "must not contain" \
  "python3 -c \"
import json
p='$T/definitions.json'
d=json.load(open(p,encoding='utf-8'))
d['braced']['rewards']=[]
json.dump(d,open(p,'w',encoding='utf-8'),indent=2)
\""

try "a skill with no words" "renders as the raw key" \
  "python3 -c \"
import json,collections
p='$L'
d=json.load(open(p,encoding='utf-8'),object_pairs_hook=collections.OrderedDict)
d.pop('elysium.skilltree.braced')
json.dump(d,open(p,'w',encoding='utf-8'),indent=2,ensure_ascii=False)
\""

try "a missing config file" "refuses the category without them" \
  "rm $T/connections.json"

echo "---- caught $pass, missed $miss"
[ "$miss" -eq 0 ]
