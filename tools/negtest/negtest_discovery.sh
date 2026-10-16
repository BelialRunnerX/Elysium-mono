#!/usr/bin/env bash
# Scanning and filing, broken one way at a time.
set -uo pipefail
cd /tmp/Elysium-mono/elysium-lib
D=src/main/java/com/elysium/lib/discovery
L=src/main/resources/assets/elysiumlib/lang/en_us.json
pass=0; fail=0
out() { python3 validate.py 2>&1; }
expect() { local n="$1" w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $n"; pass=$((pass+1));
  else echo "  MISSED : $n"; fail=$((fail+1)); fi; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "the three offers must differ"
cp $D/ElysiumFiling.java /tmp/f.bak
edit $D/ElysiumFiling.java 'UNSWORN("unsworn", 0.5F, 0, 1, ChatFormatting.GREEN),' \
                           'UNSWORN("unsworn", 1.0F, 0, 1, ChatFormatting.GREEN),'
expect "the Unsworn pay as much as the Empire" "the Empire is supposed to pay more"
cp /tmp/f.bak $D/ElysiumFiling.java

cp $D/ElysiumFiling.java /tmp/f.bak
edit $D/ElysiumFiling.java 'EMPIRE("empire", 1.0F, 1, 0, ChatFormatting.GOLD),' \
                           'EMPIRE("empire", 1.0F, 0, 0, ChatFormatting.GOLD),'
expect "filing with the Empire costs nothing" "does not raise Suspicion"
cp /tmp/f.bak $D/ElysiumFiling.java

cp $D/ElysiumFiling.java /tmp/f.bak
edit $D/ElysiumFiling.java 'PRIVATE("private", 0.0F, 0, 0, ChatFormatting.GRAY);' \
                           'PRIVATE("private", 0.0F, 0, 1, ChatFormatting.GRAY);'
expect "telling nobody still moves a meter" "telling nobody moved a meter"
cp /tmp/f.bak $D/ElysiumFiling.java

echo "difficulty has to pay"
cp $D/ElysiumDiscoveryKind.java /tmp/k.bak
edit $D/ElysiumDiscoveryKind.java 'ANOMALY("anomaly", 5, 12);' 'ANOMALY("anomaly", 5, 3);'
expect "an anomaly worth less than a flower" "less than the easier kind before it"
cp /tmp/k.bak $D/ElysiumDiscoveryKind.java

cp $D/ElysiumDiscoveryKind.java /tmp/k.bak
edit $D/ElysiumDiscoveryKind.java 'ANOMALY("anomaly", 5, 12);' 'ANOMALY("anomaly", 5, 7);'
expect "an anomaly not worth the journey" "not enough to be worth the journey"
cp /tmp/k.bak $D/ElysiumDiscoveryKind.java

echo "low stakes"
cp $D/ElysiumFiling.java /tmp/f.bak
edit $D/ElysiumFiling.java 'EMPIRE("empire", 1.0F, 1, 0, ChatFormatting.GOLD),' \
                           'EMPIRE("empire", 1.0F, 5, 0, ChatFormatting.GOLD),'
expect "cataloguing is the fastest route to Marked" "makes a peaceful activity"
cp /tmp/f.bak $D/ElysiumFiling.java

echo "the cycle"
cp $D/ElysiumFiling.java /tmp/f.bak
edit $D/ElysiumFiling.java 'return values()[(ordinal() + 1) % values().length];' \
                           'return values()[Math.min(ordinal() + 1, values().length - 1)];'
expect "the setting cannot be cycled back" "does not return to the first"
cp /tmp/f.bak $D/ElysiumFiling.java

echo "first find only"
cp $D/ElysiumDiscoveries.java /tmp/s.bak
edit $D/ElysiumDiscoveries.java '        if (!catalogue.add(key)) {
            return Result.ALREADY_KNOWN;
        }' '        catalogue.add(key);'
expect "the same find pays twice" "scanning the same rabbit twice pays twice"
cp /tmp/s.bak $D/ElysiumDiscoveries.java

cp $D/ElysiumDiscoveries.java /tmp/s.bak
edit $D/ElysiumDiscoveries.java '                            .copyOnDeath()' '                            '
expect "the catalogue resets on death" "makes dying an income"
cp /tmp/s.bak $D/ElysiumDiscoveries.java

echo "being able to read the offer"
cp $L /tmp/l.bak
python3 -c "
import json,collections
p='src/main/resources/assets/elysiumlib/lang/en_us.json'
d=json.load(open(p),object_pairs_hook=collections.OrderedDict)
del d['elysium.discovery.filing.unsworn']
json.dump(d,open(p,'w'),indent=2,ensure_ascii=False)"
expect "an office with no name" "renders as a key"
cp /tmp/l.bak $L

echo
echo "negative tests: $pass caught, $fail missed"
python3 validate.py >/dev/null 2>&1 && echo "restored clean" || echo "RESTORE FAILED"
[ "$fail" -eq 0 ]
