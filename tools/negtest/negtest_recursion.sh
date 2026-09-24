#!/usr/bin/env bash
# The recursion check, broken one way at a time.
#
# This check exists because of one shipped line, so the first test is that line
# put back. The rest are the two ways a check this narrow goes wrong: too
# narrow to see a real case, or wide enough to flag a correct one. Both halves
# are tested, because a check that cries wolf gets switched off.
set -uo pipefail
ROOT=/tmp/Elysium-mono
SNAP=/tmp/recursion_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP check_recursion.py \
    elysium-lib/src/main/java/com/elysium/lib/combat/ElysiumEffects.java)
restore() { (cd $ROOT && tar xf $SNAP); rm -f $ROOT/elysium-lib/src/main/java/com/elysium/lib/Probe.java; }
out() { (cd $ROOT && timeout 300 python3 check_recursion.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
reject() { local g; g="$(out)"
  if [[ "$g" == *"no method whose whole body"* ]]; then echo "  allowed: $1"; pass=$((pass+1));
  else echo "  FALSE POSITIVE : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

E=$ROOT/elysium-lib/src/main/java/com/elysium/lib/combat/ElysiumEffects.java
PROBE=$ROOT/elysium-lib/src/main/java/com/elysium/lib/Probe.java
probe() { printf 'package com.elysium.lib;\n\npublic final class Probe {\n%s\n}\n' "$1" > $PROBE; }

echo "the line we shipped"
edit $E 'return ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, name);' 'return id(name);'
expect "ElysiumEffects.id(name) returning id(name)" "is its whole body calling itself"

echo "the same shape, written other ways"
probe '    static int spin(int a, int b) {
        return spin(a, b);
    }'
expect "two parameters, both handed straight back" "is its whole body calling itself"

probe '    static void loop(String s) {
        loop(s);
    }'
expect "a void method with no return at all" "is its whole body calling itself"

probe '    void member(String s) {
        this.member(s);
    }'
expect "a self-call written through this" "is its whole body calling itself"

probe '    static int spin(int a) {
        return   spin( a );
    }'
expect "the same call with whitespace through it" "is its whole body calling itself"

echo "things that must not be flagged"
probe '    static int countdown(int n) {
        if (n <= 0) {
            return 0;
        }
        return countdown(n - 1);
    }'
reject "real recursion with a base case"

probe '    static int half(int n) {
        return half(n / 2);
    }'
reject "a self-call with a changed argument"

probe '    static int outer(int n) {
        return inner(n);
    }

    static int inner(int n) {
        return n + 1;
    }'
reject "one method calling a different one with the same argument"

probe '    static String named(String name) {
        return String.valueOf(name);
    }'
reject "a call whose name merely contains the parameter"

probe '    static int twice(int n) {
        int doubled = n * 2;
        return doubled;
    }'
reject "an ordinary two-statement method"

echo "the check switched off"
edit $ROOT/check_recursion.py '        if given == wanted:' '        if False:'
edit $E 'return ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, name);' 'return id(name);'
g="$(out)"
if [[ "$g" == *"no method whose whole body"* ]]; then
  echo "  caught : the comparison that makes it a check at all"; pass=$((pass+1))
else
  echo "  MISSED : the comparison that makes it a check at all"; fail=$((fail+1))
fi
restore

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"no method whose whole body"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "recursion: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
