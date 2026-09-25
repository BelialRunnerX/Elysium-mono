#!/usr/bin/env bash
# The devices and the logic layer, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-logistics
N=$M/src/main/java/com/elysium/logistics/rules/NetRules.java
H=$M/tools/NetHarness.java
RB=$M/src/main/java/com/elysium/logistics/registry/LogisticBlocks.java
G=$M/tools/gen_logistics.py
V=$M/validate.py
SNAP=/tmp/net_snap
OUT=/tmp/net_neg
KIN=$ROOT/elysium-kinetics/src/main/java/com/elysium/kinetics/rules
IND=$ROOT/elysium-industry/src/main/java/com/elysium/industry/rules
pass=0; fail=0
if ! python3 "$V" >/dev/null 2>&1; then
  echo "REFUSING TO START: the module does not validate before any edit is made."
  python3 "$V" 2>&1 | tail -5
  exit 3
fi
rm -rf $SNAP && mkdir -p $SNAP
cp -a $M/src/main/java/com/elysium/logistics $SNAP/logistics
cp -a $M/tools $SNAP/tools
cp -a $M/src/main/resources $SNAP/resources
cp -a $M/validate.py $SNAP/validate.py
restore() {
  rm -rf $M/src/main/java/com/elysium/logistics $M/tools $M/src/main/resources
  cp -a $SNAP/logistics $M/src/main/java/com/elysium/logistics
  cp -a $SNAP/tools $M/tools
  cp -a $SNAP/resources $M/src/main/resources
  cp -a $SNAP/validate.py $M/validate.py
}
trap 'restore' EXIT INT TERM
out() {
  rm -rf $OUT && mkdir -p $OUT
  if ! javac -nowarn -d $OUT $M/src/main/java/com/elysium/logistics/rules/*.java $KIN/*.java $IND/*.java "$H" 2>&1 | grep -v JAVA_TOOL; then :; fi
  java -cp $OUT com.elysium.logistics.rules.NetHarness 2>&1 | grep -v JAVA_TOOL
}
worldout() { python3 "$V" 2>&1 | grep -v JAVA_TOOL; }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-140)"; fail=$((fail+1)); fi; restore; }
wexpect() { local w="$2" g; g="$(worldout)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-140)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }
regen() { (cd $M && python3 tools/gen_logistics.py >/dev/null 2>&1); }

echo "the one thing that stops an expression looping"
edit $N '        return from >= 0 && from < at;' '        return from >= 0;'
expect "a step allowed to read one written after it, which is a loop written down" "a step was allowed to read a step after it"

edit $N '                if (!mayRead(at, from)) {
                    why.add("step " + at + " reads step " + from' '                if (false) {
                    why.add("step " + at + " reads step " + from'
expect "a refusal that gives no reason for an arrow pointing forwards" "and the reasons say"

echo "the types"
edit $N '                if (!types.get(from).equals(op.takes().get(which))) {
                    return null;
                }' '                if (false) {
                    return null;
                }'
expect "a number added to a truth, which is the types being a comment" "so the types are a comment rather than a check"

edit $N '            new Op("add", List.of(NUMBER, NUMBER), NUMBER),' '            new Op("add", List.of(NUMBER, NUMBER), TRUTH),'
expect "an operator that gives something other than what it says" "an ordinary expression is refused"

echo "the ceiling on how long an expression may be"
edit $N '    public static final int MOST_NODES = 64;' '    public static final int MOST_NODES = 100000;'
expect "a ceiling an expression cannot reach" "the ceiling is not a ceiling, it is the sky"

edit $N '        if (expression.isEmpty() || expression.size() > MOST_NODES) {' '        if (expression.isEmpty()) {'
expect "a length check that has stopped checking the length" "was allowed and the ceiling is"

echo "the cost of working one out"
edit $N '    public static int stepsFor(List<Node> expression) {
        return expression.size();
    }' '    public static int stepsFor(List<Node> expression) {
        return expression.size() * 2;
    }'
expect "an expression that costs more than its own length to work out" "so something is being worked out twice"

echo "a value getting in from the world at all"
edit $N '        for (Op one : OPS) {
            if (one.arity() == 0) {
                reader = true;' '        for (Op one : OPS) {
            if (false) {
                reader = true;'
expect "a check that cannot notice there is no way into an expression" "no way for a value to get into an expression from the world at all"

edit $N '            new Op("held", List.of(), ITEM),' '            new Op("held", List.of(), NUMBER),'
expect "a type nothing gives any more, so half the operators are unreachable" "so no expression can ever have one in it"

echo "the devices"
edit $N '            new Device("interface", SERVE, 2),' '            new Device("interface", IMPORT, 2),'
expect "a role nothing on a network fills" "so that role is a word in a list"

edit $N '            new Device("ender_link", LINK, 4));' '            new Device("ender_link", LINK, 40));'
expect "a device that draws more than a warehouse should" "which is more than eight ordinary devices"

edit $N '            if (draws < 1) {' '            if (false) {'
expect "a guard that would let a device be free to run" "the guards allow what they say they forbid"

echo "the world the devices are actually in"
edit $RB '        for (NetRules.Device device : NetRules.DEVICES) {' '        for (NetRules.Device device : NetRules.DEVICES.subList(0, 3)) {'
wexpect "devices listed by hand rather than walked from the roster" "not registered by walking the roster"

edit $RB '        if (made.size() != NetRules.howMany()) {' '        if (false) {'
wexpect "a registry that does not count what it registered" "counts the devices registered"

edit $G '        colour = ROLE_COLOURS.get(role)' '        colour = ROLE_COLOURS.get("import")'
regen
wexpect "every device drawn the same, so a wall of them says nothing" "says nothing about what a network does"

edit $G '               "key": {"M": {"item": parts[1]},
                       "B": {"item": f"{NS}:bus"},' '               "key": {"M": {"item": parts[1]},
                       "B": {"item": "minecraft:iron_ingot"},'
regen
wexpect "a device with nothing to hang it off" "no way of knowing why it does nothing"

echo
echo "restoring and re-checking"
restore
if python3 "$V" >/dev/null 2>&1; then echo "  tree restored clean"; else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "network: $pass caught, $fail missed"
[[ $fail -eq 0 ]] || exit 1
