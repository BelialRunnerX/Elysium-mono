set -u
cd /tmp/Elysium-mono
BK=/tmp/t-backup; rm -rf $BK; mkdir -p $BK
pass=0; fail=0
check () {
  rm -rf art/__pycache__
  out=$(cd elysium-core && python3 -W ignore validate.py 2>&1)
  if echo "$out" | grep -q "$2"; then echo "  CAUGHT  $1"; pass=$((pass+1));
  else echo "  MISSED  $1 (wanted: $2)"; echo "$out" | tail -3 | sed 's/^/          /'; fail=$((fail+1)); fi
}
save(){ mkdir -p "$BK/$(dirname "$1")"; cp "$1" "$BK/$1"; }
restore(){ cp "$BK/$1" "$1"; rm -rf art/__pycache__; }

# The sprites tools/gen_material_gear.py draws, kept before case 4 runs it and
# put back byte for byte after it (17 Oct 2026). Case 4 used to put them back
# by running the generator again on the restored art, and that left all 372
# modified: the generator redraws every sprite it owns, and the Pillow on this
# machine writes the same pixels as other bytes than the PNGs in the
# repository were written with. Its models, recipes and lang come out byte for
# byte as they were (written only when missing, or sorted JSON).
T=elysium-core/src/main/resources/assets/elysium/textures
tar cf "$BK/sprites.tar" $T/item $T/models/armor
putsprites(){ rm -rf $T/item $T/models/armor; tar xf "$BK/sprites.tar"; }

echo "1. a shape is registered with no art for it"
save elysium-core/src/main/java/com/elysium/core/item/ElysiumMaterialGear.java
python3 - <<'PY'
import pathlib
p=pathlib.Path("elysium-core/src/main/java/com/elysium/core/item/ElysiumMaterialGear.java")
t=p.read_text().replace('        SWORD("sword"),','        SWORD("sword"),\n        HALBERD("halberd"),',1)
p.write_text(t)
PY
check "shape with no drawing" "nothing can draw one"
restore elysium-core/src/main/java/com/elysium/core/item/ElysiumMaterialGear.java

echo "2. the validator's shape list goes stale again"
save elysium-core/validate.py
python3 - <<'PY'
import pathlib
p=pathlib.Path("elysium-core/validate.py")
t=p.read_text().replace('SHAPES = re.findall(r\'\\w+\\("(\\w+)"\\)\', _enum.group(1)) if _enum else []',
                        'SHAPES = ["hammer", "broadaxe", "scythe", "spear"]',1)
p.write_text(t)
PY
check "hard-coded shape list" "references unknown item"
restore elysium-core/validate.py

echo "3. a tool head floats off its haft"
save art/pixelart.py
python3 - <<'PY'
import pathlib,re
p=pathlib.Path("art/pixelart.py"); t=p.read_text()
block=re.search(r'    "hoe": \(\n(?:        "[^"]*",\n)+    \),\n',t).group(0)
broken=block.replace('        ".........0520...",\n','        "................",\n',1)
p.write_text(t.replace(block,broken,1))
PY
check "floating tool head" "separate pieces"
restore art/pixelart.py

echo "4. two shapes given the same map"
save art/pixelart.py
python3 - <<'PY'
import pathlib,re
p=pathlib.Path("art/pixelart.py"); t=p.read_text()
axe=re.search(r'    "axe": \(\n((?:        "[^"]*",\n)+)    \),\n',t).group(1)
t=re.sub(r"(    \"shovel\": \(\n)(?:        \"[^\"]*\",\n)+(    \),\n)", lambda m: m.group(1)+axe+m.group(2), t, count=1)
t=t.replace('    "shovel":     [(11, 3)],', '    "shovel":     [(12, 4)],')
p.write_text(t)
PY
python3 elysium-core/tools/gen_material_gear.py >/dev/null 2>&1
if cmp -s elysium-core/src/main/resources/assets/elysium/textures/item/iron_axe.png \
          elysium-core/src/main/resources/assets/elysium/textures/item/iron_shovel.png; then
  echo "  (identical sprites produced — checking whether anything notices)"
fi
check "two shapes drawn identically" "differ by only"
restore art/pixelart.py
putsprites

echo
rm -rf art/__pycache__
(cd elysium-core && python3 -W ignore validate.py >/dev/null 2>&1) && echo "  restored tree passes" || { echo "  RESTORE FAILED"; fail=$((fail+1)); }
echo; echo "$pass caught, $fail missed"
