set -u
cd /tmp/Elysium-mono
BK=/tmp/c-backup; rm -rf $BK; mkdir -p $BK
pass=0; fail=0
check () {
  rm -rf art/__pycache__
  out=$(cd elysium-core && python3 -W ignore validate.py 2>&1)
  if echo "$out" | grep -q "$2"; then echo "  CAUGHT  $1"; pass=$((pass+1));
  else echo "  MISSED  $1 (wanted: $2)"; echo "$out" | tail -3 | sed 's/^/          /'; fail=$((fail+1)); fi
}
save(){ mkdir -p "$BK/$(dirname "$1")"; cp "$1" "$BK/$1"; }
checkpre(){ rm -rf art/__pycache__; }
restore(){ cp "$BK/$1" "$1"; rm -rf art/__pycache__ elysium-core/tools/__pycache__; }

echo "1. two materials given the same colour"
save art/materials.py
sed -i 's|"brass":      "#c4a748",|"brass":      "#e6c34d",|' art/materials.py
check "materials indistinguishable" "apart in CIELAB"
restore art/materials.py

echo "2. a shade map loses a column"
save art/pixelart.py
python3 - <<'PY'
import pathlib
p=pathlib.Path("art/pixelart.py"); t=p.read_text()
t=t.replace('        "..055555555540..",\n        "..054444444430..",\n        "..054444444430..",\n        "..054444444430..",',
            '        "..05555555540..",\n        "..054444444430..",\n        "..054444444430..",\n        "..054444444430..",',1)
p.write_text(t)
PY
check "short map row" "characters, expected 16"
restore art/pixelart.py

echo "3. an accent drifts off the sprite"
save art/pixelart.py
sed -i 's|"helmet":     \[(8, 7)\],|"helmet":     [(0, 0)],|' art/pixelart.py
check "accent on transparency" "transparent pixel"
restore art/pixelart.py

echo "4. a tool loses its wooden handle"
save art/pixelart.py
python3 - <<'PY'
import pathlib,re
p=pathlib.Path("art/pixelart.py"); t=p.read_text()
block=re.search(r'    "hammer": \(\n(?:        "[^"]*",\n)+    \),\n',t).group(0)
p.write_text(t.replace(block, block.replace("k","0").replace("d","4").replace("b","2")))
PY
(cd elysium-core && python3 tools/gen_material_gear.py >/dev/null 2>&1)
check "handle-less tool" "no handle pixels left"
restore art/pixelart.py
(cd elysium-core && python3 tools/gen_material_gear.py >/dev/null 2>&1)

echo "5. the generator goes back to skipping what exists"
save elysium-core/tools/gen_material_gear.py
python3 - <<'PY'
import pathlib
p=pathlib.Path("elysium-core/tools/gen_material_gear.py"); t=p.read_text()
t=t.replace("            if item in reserved:\n                skipped += 1\n                continue\n            getattr(sprites, shape)",
            "            if os.path.exists(os.path.join(TEXTURES,'item',item+'.png')):\n                skipped += 1\n                continue\n            getattr(sprites, shape)",1)
p.write_text(t)
PY
check "generator back to a no-op" "skipping sprites that already exist"
restore elysium-core/tools/gen_material_gear.py

echo "6. a sprite loses its shading"
save art/materials.py
sed -i 's|^_STEPS = \[0.12, 0.24, 0.38, 0.55, 0.78\]|_STEPS = [0.40, 0.42, 0.44, 0.46, 0.48]|' art/materials.py
(cd elysium-core && python3 tools/gen_material_gear.py >/dev/null 2>&1)
check "flat sprite" "units of luminance"
restore art/materials.py
(cd elysium-core && python3 tools/gen_material_gear.py >/dev/null 2>&1)

echo
(cd elysium-core && python3 -W ignore validate.py >/dev/null 2>&1) && echo "  restored tree passes" || { echo "  RESTORE FAILED"; fail=$((fail+1)); }
echo; echo "$pass caught, $fail missed"
