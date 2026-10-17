#!/usr/bin/env bash
# The bestiary rendering as silhouettes, reintroduced one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono/elysium-mobs
G=$ROOT/tools/gen_mobs.py
pass=0; fail=0
# The module is put back from a copy at the end rather than by a last regen.
# The textures come out of this Pillow with the same pixels and other zlib
# bytes than the ones committed, so a regen leaves 42 PNGs changed in git that
# samefile.py rightly calls the same (16 October 2026).
SNAP=/tmp/mobart_snap.tar
(cd /tmp/Elysium-mono && tar cf $SNAP elysium-mobs)
restore() { rm -rf $ROOT && (cd /tmp/Elysium-mono && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
regen() { (cd /tmp/Elysium-mono && python3 elysium-mobs/tools/gen_mobs.py >/dev/null 2>&1); }
out() { (cd $ROOT && python3 validate.py 2>&1); }
expect() { local w="$2" g; regen; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
assert old in s, 'anchor not found in ' + p
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "the palette going dark again"
cp $G /tmp/gm.bak
edit $G '"scavenger": ("#888175", "#c5bbab"),' '"scavenger": ("#3a352c", "#5c5344"),'
expect "a family back at a quarter of vanilla's value" "renders as a silhouette"
cp /tmp/gm.bak $G

cp $G /tmp/gm.bak
edit $G '"scavenger": ("#888175", "#c5bbab"),' '"scavenger": ("#f0eee8", "#c5bbab"),'
expect "a family brighter than anything vanilla ships" "glow against every world"
cp /tmp/gm.bak $G

echo "the range collapsing"
cp $G /tmp/gm.bak
edit $G '    "plate": (1.52, 0.55),   # helm, cuirass, pauldrons, greaves — the shell' \
        '    "plate": (1.00, 1.00),'
expect "the armour plate no lighter than the body" "no value structure"
cp /tmp/gm.bak $G

cp $G /tmp/gm.bak
python3 - <<'EOF'
p = "/tmp/Elysium-mono/elysium-mobs/tools/gen_mobs.py"
s = open(p, encoding="utf-8", newline="").read()
old = 'def material_for(boxes):\n    """part name -> material key."""\n    out = {}'
assert old in s
open(p, "w", encoding="utf-8", newline="").write(s.replace(old, old + "\n    return out", 1))
EOF
expect "every part made of the same thing" "nothing inside it"
cp /tmp/gm.bak $G

echo "the measurements with nothing to measure with (15 Oct 2026)"
# Until 15 October a Python without Pillow printed "texture sizes unchecked",
# one without Pillow or numpy "mob value range : not measured", and the module
# passed with neither measured. Each must now FAIL and say what to install. The
# import is broken in the validator for one run each (no env var uninstalls a
# package), which is what a Python without it looks like to the check.
fexpect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"PROBLEM(S)"* && "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
cp $ROOT/validate.py /tmp/mv.bak
edit $ROOT/validate.py '    from PIL import Image
except ImportError:
    Image = None' '    from PIL import ImageThatIsNotThere as Image
except ImportError:
    Image = None'
fexpect "no Pillow for the model sheets, which used to print unchecked and pass" "Pillow does not import in"
cp /tmp/mv.bak $ROOT/validate.py
cp $ROOT/validate.py /tmp/mv.bak
edit $ROOT/validate.py '    import numpy as _np' '    import numpy_that_is_not_here as _np'
fexpect "no numpy for the value range, which used to print not measured and pass" \
  "the mob value range cannot be measured: No module named 'numpy_that_is_not_here'"
cp /tmp/mv.bak $ROOT/validate.py

echo
restore
if (cd $ROOT && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED"; fail=$((fail+1))
fi
echo "mob art: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
