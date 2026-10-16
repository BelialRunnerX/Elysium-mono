#!/usr/bin/env bash
# The file that checks the checkers, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
V=$ROOT/check_validators.py
SNAP=/tmp/validators_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP check_validators.py validate.py compile.sh check_lang.py)
restore() { (cd $ROOT && tar xf $SNAP); rm -f $ROOT/check_stray.py; }
out() { (cd $ROOT && timeout 120 python3 check_validators.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: ${g:0:160}"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the structural property itself"
edit $ROOT/check_lang.py '    if problems:' '    if problems and False:'
expect "a cross-cutting check that can never report" "can never fail"

python3 - <<'PYX'
import pathlib
p = pathlib.Path("/tmp/Elysium-mono/check_lang.py")
s = p.read_text(encoding="utf-8")
i = s.index("    if problems:")
p.write_text(s[:i] + "    problems.append('late')\n" + s[i:], encoding="utf-8")
PYX
expect "a check that records a problem and prints it" "reports once and reports last"

python3 - <<'PYX'
import pathlib
p = pathlib.Path("/tmp/Elysium-mono/check_lang.py")
s = p.read_text(encoding="utf-8")
p.write_text(s + "\n\ndef later():\n    problems.append('dead')\n", encoding="utf-8")
PYX
expect "a fail() call after the report, where nobody sees it" "after"

echo "the two lists, which used to be copied by hand"
edit $ROOT/validate.py '    if subprocess.run([sys.executable, "check_lang.py"], cwd=HERE).returncode != 0:' \
                       '    if False and subprocess.run([sys.executable, "check_lang_off.py"], cwd=HERE).returncode != 0:'
expect "a root check that exists and is never run" "never runs it"

cp $ROOT/check_lang.py $ROOT/check_stray.py
expect "a new check written at the root and never wired in" "check_stray.py"

edit $ROOT/compile.sh 'MODULES="elysium-lib ' 'MODULES="elysium-nosuchmodule elysium-lib '
expect "a module built by compile.sh that nothing validates" "checked by nothing"

edit $ROOT/compile.sh 'MODULES="elysium-lib' 'MODULES_WAS="elysium-lib'
expect "compile.sh with no module list to read" "no MODULES= line"

python3 - <<'PYW' || { echo "  STALE ANCHOR, counted as a miss"; fail=$((fail+1)); }
import pathlib, sys
p = pathlib.Path("/tmp/Elysium-mono/validate.py")
s = p.read_text(encoding="utf-8")
old = 'subprocess.run([sys.executable, "check_'
if old not in s:
    sys.stderr.write("STALE ANCHOR in validate.py :: subprocess call\n"); sys.exit(2)
p.write_text(s.replace(old, 'subprocess.run([sys.executable, "notacheck_'), encoding="utf-8")
PYW
expect "a validate.py that runs no cross-cutting checks at all" "runs no check_"

echo "the blanking, which must hide docstrings and nothing else"
edit $V 'def code_only(text):' 'def code_only(text):
    return text
def unused_code_only(text):'
expect "a report block quoted in a docstring read as a real one" "reports 2 times"

python3 - <<'PYX'
import pathlib
p = pathlib.Path("/tmp/Elysium-mono/check_lang.py")
s = p.read_text(encoding="utf-8")
i = s.index("    if problems:")
p.write_text(s[:i] + "    if problems:\n        pass\n" + s[i:], encoding="utf-8")
PYX
expect "a real second report block, which blanking must not hide" "reports 2 times"

python3 - <<'PYZ' || { echo "  STALE ANCHOR, counted as a miss"; fail=$((fail+1)); }
import pathlib, sys
p = pathlib.Path("/tmp/Elysium-mono/check_validators.py")
s = p.read_text(encoding="utf-8")
old = "for match in re.finditer(r'"
if old not in s:
    sys.stderr.write("STALE ANCHOR in check_validators.py :: blanking regex\n"); sys.exit(2)
p.write_text(s.replace(old, "for match in re.finditer(r'XXNOMATCHXX", 1), encoding="utf-8")
PYZ
expect "blanking that stops covering docstrings" "reports 2 times"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"45 validators — 29 module and 16 cross-cutting"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: ${g:0:200}"; fail=$((fail+1)); fi
echo "validators: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
