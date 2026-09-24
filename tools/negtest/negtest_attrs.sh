#!/bin/bash
# Negative tests for the skill tree's attribute and operation rules.
# Each case breaks the generated pack in one specific way, asserts the
# validator says so, and restores.
cd /tmp/Elysium-mono || exit 1
DEFS=elysium-core/src/main/resources/data/elysium/puffish_skills/categories/extraction_ledger/definitions.json
GEN=elysium-core/tools/gen_skilltree.py
cp "$DEFS" /tmp/defs.bak
cp "$GEN" /tmp/gen.bak
pass=0; fail=0

restore() { cp /tmp/defs.bak "$DEFS"; cp /tmp/gen.bak "$GEN"; }

check() { # name, expected substring
  out=$(python3 elysium-core/validate.py 2>&1)
  if echo "$out" | grep -qF "$2"; then
    echo "  PASS  $1"; pass=$((pass+1))
  else
    echo "  FAIL  $1 -- validator did not object"; fail=$((fail+1))
  fi
  restore
}

echo "== baseline"
if python3 elysium-core/validate.py >/dev/null 2>&1; then echo "  clean tree passes"; else echo "  SETUP FAILED: clean tree already fails"; exit 1; fi

echo "== 1. the actual bug: 1.21.2 flattened spelling on 1.21.1"
python3 - <<'PY'
import json
p="elysium-core/src/main/resources/data/elysium/puffish_skills/categories/extraction_ledger/definitions.json"
d=json.load(open(p))
for v in d.values():
    for r in v.get("rewards",[]):
        if r["type"]=="puffish_skills:attribute":
            r["data"]["attribute"]="minecraft:max_health"
json.dump(d,open(p,"w"),indent=2)
PY
check "flattened attribute ids rejected" "Minecraft 1.21.1 does not have"

echo "== 2. one single misspelled attribute among fifty-four good ones"
python3 - <<'PY'
import json
p="elysium-core/src/main/resources/data/elysium/puffish_skills/categories/extraction_ledger/definitions.json"
d=json.load(open(p))
for v in d.values():
    for r in v.get("rewards",[]):
        if r["type"]=="puffish_skills:attribute":
            r["data"]["attribute"]="minecraft:generic.mining_speed"
            raise SystemExit(json.dump(d,open(p,"w"),indent=2))
PY
check "a lone bad attribute rejected" "Minecraft 1.21.1 does not have"

echo "== 3. namespaceless attribute"
python3 - <<'PY'
import json
p="elysium-core/src/main/resources/data/elysium/puffish_skills/categories/extraction_ledger/definitions.json"
d=json.load(open(p))
for v in d.values():
    for r in v.get("rewards",[]):
        if r["type"]=="puffish_skills:attribute":
            r["data"]["attribute"]="max_health"
            raise SystemExit(json.dump(d,open(p,"w"),indent=2))
PY
check "unnamespaced attribute rejected" "Minecraft 1.21.1 does not have"

echo "== 4. Minecraft's operation name, which the mod does not take"
python3 - <<'PY'
import json
p="elysium-core/src/main/resources/data/elysium/puffish_skills/categories/extraction_ledger/definitions.json"
d=json.load(open(p))
for v in d.values():
    for r in v.get("rewards",[]):
        if r["type"]=="puffish_skills:attribute":
            r["data"]["operation"]="add_value"
            raise SystemExit(json.dump(d,open(p,"w"),indent=2))
PY
check "add_value rejected" "operation the mod does not accept"

echo "== 5. add_multiplied_total is accepted (rule is not just a whitelist of two)"
python3 - <<'PY'
import json
p="elysium-core/src/main/resources/data/elysium/puffish_skills/categories/extraction_ledger/definitions.json"
d=json.load(open(p))
for v in d.values():
    for r in v.get("rewards",[]):
        if r["type"]=="puffish_skills:attribute":
            r["data"]["operation"]="add_multiplied_total"
            raise SystemExit(json.dump(d,open(p,"w"),indent=2))
PY
out=$(python3 elysium-core/validate.py 2>&1)
if echo "$out" | grep -qF "operation the mod does not accept"; then
  echo "  FAIL  a valid operation was rejected"; fail=$((fail+1))
else
  echo "  PASS  a valid operation is accepted"; pass=$((pass+1))
fi
restore

echo "== 6. the generator refuses an attribute name that does not exist"
python3 - <<'PY'
import pathlib
p=pathlib.Path("elysium-core/tools/gen_skilltree.py")
s=p.read_text().replace('add("max_health"','add("maximum_health"',1)
p.write_text(s)
PY
out=$(python3 elysium-core/tools/gen_skilltree.py 2>&1)
if echo "$out" | grep -qF "no vanilla attribute named"; then
  echo "  PASS  generator stops on an invented name"; pass=$((pass+1))
else
  echo "  FAIL  generator emitted an invented name"; fail=$((fail+1))
fi
restore
python3 elysium-core/tools/gen_skilltree.py >/dev/null

echo
echo "  $pass passed, $fail failed"
[ "$fail" -eq 0 ]
