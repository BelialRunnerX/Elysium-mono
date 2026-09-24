#!/usr/bin/env bash
# The bundled dependency, broken one way at a time.
#
# This one works on the product rather than the source: every break is a way a
# jar-in-jar can be wrong in a zip file that no source validator can see.
set -uo pipefail
ROOT=/tmp/Elysium-mono
JAR=$ROOT/dist/elysium-all-1.0.0.jar
WORK=/tmp/bundle_test.jar
pass=0; fail=0

# Rewrite the jar: drop entries matching a pattern, optionally add one.
zipedit() { python3 - "$@" <<'PY'
import json, re, shutil, sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
drop = sys.argv[3] if len(sys.argv) > 3 else ""
add_name = sys.argv[4] if len(sys.argv) > 4 else ""
add_body = sys.argv[5] if len(sys.argv) > 5 else ""
patch = sys.argv[6] if len(sys.argv) > 6 else ""
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as out:
    for item in zin.infolist():
        if drop and re.search(drop, item.filename):
            continue
        body = zin.read(item.filename)
        if patch and item.filename == "META-INF/jarjar/metadata.json":
            index = json.loads(body)
            how, what = patch.split("=", 1)
            if how == "version":
                index["jars"][0]["version"]["artifactVersion"] = what
            elif how == "range":
                index["jars"][0]["version"]["range"] = what
            elif how == "path":
                index["jars"][0]["path"] = what
            elif how == "empty":
                index["jars"] = []
            body = (json.dumps(index, indent=2) + "\n").encode()
        out.writestr(item, body)
    if add_name:
        out.writestr(add_name, add_body.encode())
PY
}

expect() { local what="$1" want="$2" got
  got="$(python3 $ROOT/tools/checkbundle.py $WORK 2>&1)"
  if [[ "$got" == *"$want"* ]]; then echo "  caught : $what"; pass=$((pass+1));
  else echo "  MISSED : $what"; fail=$((fail+1)); fi; rm -f $WORK; }

echo "the licence"
zipedit $JAR $WORK 'licences/curios/COPYING$'
expect "half a licence" "part of its licence was left behind"

zipedit $JAR $WORK 'licences/curios/'
expect "a mod redistributed with no licence at all" "no licence text"

zipedit $JAR $WORK 'licences/curios/SOURCE.txt'
expect "a licence with no pointer to its source" "no pointer to its source"

zipedit $JAR $WORK 'THIRD-PARTY.md'
expect "a jar that does not say whose work it carries" "somebody else's work"

echo "the index"
zipedit $JAR $WORK '' '' '' 'version=8.0.0'
expect "a bundled version the dependents refuse" "does not satisfy its own declared range"

zipedit $JAR $WORK '' '' '' 'path=META-INF/jarjar/nothing.jar'
expect "an index naming a file that is not there" "does not contain it"

zipedit $JAR $WORK '' '' '' 'version=9.5.2+1.21.1'
expect "an index that disagrees with the jar it names" "the loader believes the jar"

zipedit $JAR $WORK '' '' '' 'empty='
expect "an index with nothing in it" "index is empty"

zipedit $JAR $WORK 'META-INF/jarjar/metadata.json'
expect "a bundled jar nothing indexes" "nothing indexes them"

zipedit $JAR $WORK '' 'META-INF/jarjar/stowaway.jar' 'not really a jar'
expect "a jar carried along that nobody loads" "downloaded and never loaded"

echo
echo "the packager's own refusals"
cp $ROOT/elysium-trinkets/src/main/resources/META-INF/neoforge.mods.toml /tmp/tk.bak
python3 - <<'PY'
p="/tmp/Elysium-mono/elysium-trinkets/src/main/resources/META-INF/neoforge.mods.toml"
s=open(p).read()
s=s.replace('modId="curios"','modId="curioss"',1)
open(p,"w").write(s)
PY
cp $JAR $WORK
# Captured rather than piped: the packager exits non-zero when it refuses,
# and `set -o pipefail` would read that refusal as the test failing.
refusal="$(python3 $ROOT/tools/jarjar.py $WORK /tmp/mods/curios-neoforge-9.5.1+1.21.1.jar 2>&1)"
if [[ "$refusal" == *"nothing in this repository declares a dependency"* ]]; then
  echo "  caught : bundling a mod nothing asks for"; pass=$((pass+1))
else
  echo "  MISSED : bundling a mod nothing asks for"; fail=$((fail+1))
fi
cp /tmp/tk.bak $ROOT/elysium-trinkets/src/main/resources/META-INF/neoforge.mods.toml
rm -f $WORK

echo
if python3 $ROOT/tools/checkbundle.py $JAR >/dev/null 2>&1; then
  echo "  the real jar is still clean"
else
  echo "  RESTORE FAILED — the real jar does not pass"; fail=$((fail+1))
fi
echo "bundle: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
