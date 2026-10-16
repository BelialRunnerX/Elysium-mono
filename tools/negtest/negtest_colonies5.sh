#!/usr/bin/env bash
# Elysium Colonies, part five: stakes, grants, the market and the styles.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-colonies
S=$M/src/main/java/com/elysium/colonies
R=$M/src/main/resources
D=$R/data/elysiumcolonies
B=$R/blueprints/elysiumcolonies
SNAP=/tmp/colonies5_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-colonies)
restore() { rm -rf $ROOT/elysium-colonies && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, 'anchor not found in ' + p
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "a stake, not a home"
edit $S/colony/Colonies.java 'if (colony.getPermissions().isColonyMember(player)) {' 'if (false) {'
expect "membership read as ownership" "counts for nothing"

edit $S/event/ColonyProgress.java 'java.util.List<Colonies.Settlement> towns = Colonies.memberOf(player);' \
  'java.util.List<Colonies.Settlement> towns = new java.util.ArrayList<>();
        Colonies.Settlement only = Colonies.ownedBy(player);
        if (only != null) towns.add(only);'
expect "a second stake invisible to the world tier" "a second stake is invisible"

edit $S/colony/Colonies.java 'if (leaving.equals(permissions.getOwner())) {
            return false;
        }' ''
expect "leaving that abandons the colony" "must not make that decision"

edit $S/rules/ColonyRules.java 'best = Math.max(best, worldFloor(citizens));' \
                               'best = best + worldFloor(citizens);'
expect "ten hamlets beating one town" "arithmetic farms"

echo "the grant and the watchdog"
edit $S/exchange/FrontierExchange.java 'public static int grant(ServerLevel level, Colonies.Settlement town) {' \
                                       'public static int noGrant(ServerLevel level, Colonies.Settlement town) {'
expect "a colony that can never get its first builder" "including the builder"

edit $S/exchange/FrontierExchange.java 'int giving = ReliefRules.granting(want.count() - already, given);' \
                                       'int giving = want.count() - already;'
expect "a grant with no limit" "an amount nobody decided"

edit $S/exchange/FrontierExchange.java 'List<Colonies.Owed> owed = Colonies.owed(level, town.id());
        if (owed.isEmpty()) {
            return 0;
        }
        int given = 0;' \
                                       'List<Colonies.Owed> owed = List.of();
        int given = 0;'
expect "a grant of things nobody asked for" "free materials"

edit $S/event/ColonyLife.java 'ReliefRules.Relief relief = ReliefRules.reliefFor(town.day(),
                ledger.lastProgress(), ledger.foundedOn());' \
                              'ReliefRules.Relief relief = ReliefRules.Relief.NONE;'
expect "nothing noticing that a town has stopped" "every lock this module did not"

edit $S/event/ColonyLife.java 'boolean moved = ReliefRules.progressed(ledger.builtThen(), built.size(),
                ledger.development(), banked, ledger.citizensThen(), town.citizens());' \
                              'boolean moved = built.size() != ledger.builtThen();'
expect "progress judged outside the rules" "harness proves"

edit $S/event/ColonyLife.java 'ledger = ledger.foundedOn(town.day());' '{ }'
expect "a grant that never starts or never ends" "never starts"

edit $S/event/ColonyLife.java 'if (report(level, town, ReliefRules.stuckFor(town.day(),
                        ledger.lastProgress()))) {' 'if (false) {'
expect "going on quietly forever" "how this ends"

edit $S/event/ColonyLife.java 'if (ReliefRules.dueToReport(town.day(), ledger.lastReported())) {' 'if (true) {'
expect "a stuck town reported every day" "stops being read on the third"

# The grant now runs a hundred days by design; what keeps that from being free
# materials for months is that a stuck town is reported alongside it.
edit $S/event/ColonyLife.java '|| ReliefRules.alsoReport(town.day(), ledger.lastProgress())' '|| false'
expect "a stuck town silent until its grant is spent" "sits silent for months"

edit $S/rules/ReliefRules.java 'if (granted(day, foundedOn)) {
            return Relief.GRANT;
        }' ''
expect "a new town stuck and given nothing" "was not given anything"

echo "the market"
edit $S/market/Market.java 'Charter charter = Charters.of(dimension);' \
                           'Charter charter = Charter.unwritten(dimension);'
expect "prices that ignore where you are" "the whole market is one number"

edit $S/market/Market.java 'return MarketRules.drifted(priced, day, seedFor(stack, dimension));' \
                           'return priced;'
expect "a route learned once and forever" "route forever"

edit $S/market/Market.java 'String family = familyOf(stack);' 'String family = null;'
expect "a market that prices items rather than materials" "unifying the pack bought nothing"

edit $S/market/Market.java 'demand = Charters.needs().contains(family) ? 1.0F : 0.4F;' 'demand = 1.0F;'
expect "a world equally short of everything" "equally short of everything"

edit $S/market/Market.java 'int hash = (id == null ? 0 : id.toString().hashCode()) * 31
                + dimension.toString().hashCode();' \
                           'int hash = (id == null ? 0 : id.toString().hashCode());'
expect "every world drifting together" "one market wearing many hats"

edit $S/exchange/FrontierExchange.java 'int price = Market.priceOf(where, want.wanted(), town.day());' 'int price = 100;'
expect "an exchange trading at a flat rate" "stops mattering the moment"

python3 -c "
import json
p='$D/charter_needs/market_values.json'
d=json.load(open(p)); d['families']['coal']=d['families']['diamond']
json.dump(d,open(p,'w'),indent=2)"
expect "coal worth as much as diamond" "says nothing"

python3 -c "
import json
p='$D/charter_needs/market_values.json'
d=json.load(open(p)); d['families']={'iron':90}
json.dump(d,open(p,'w'),indent=2)"
expect "a market that cannot tell iron from coal" "iron from coal"

edit $S/rules/MarketRules.java 'public static final float SPREAD = 1.0F / 3.0F;' \
                               'public static final float SPREAD = 0.0F;'
expect "a market you can round-trip for profit" "there is no spread at all"

edit $S/rules/MarketRules.java 'float scarcity = local ? -SWING : SWING * want;' 'float scarcity = 0.0F;'
expect "a charter that says nothing about prices" "does not sell it cheaply"

echo "the styles"
rm -rf "$B/Cinderhold"
expect "a charter naming a style nobody shipped" "silently keep whatever pack"

python3 -c "
import json
p='$B/Frostfall/pack.json'
d=json.load(open(p)); d['pack-format']=2
json.dump(d,open(p,'w'),indent=2)"
expect "a pack format the loader rejects" "rejected outright"

python3 -c "
import json
p='$B/Bastion/pack.json'
d=json.load(open(p)); d.pop('desc')
json.dump(d,open(p,'w'),indent=2)"
expect "a pack.json missing a key the loader reads" "throws without them"

python3 -c "
import json
p='$B/Bastion/pack.json'
d=json.load(open(p)); d['name']='Something Else'
json.dump(d,open(p,'w'),indent=2)"
expect "a pack keyed under a different name" "a charter naming the folder finds nothing"

python3 -c "
import json
p='$B/Bastion/pack.json'
d=json.load(open(p)); d['icon']='nope.png'
json.dump(d,open(p,'w'),indent=2)"
expect "a style naming an icon it does not ship" "icon it does not ship"

rm -f "$B/Pale Reach/pack.json"
expect "a style Structurize will never see" "will not see it"

python3 -c "
import os, glob
for f in sorted(glob.glob('$B/Frostfall/**/*.blueprint', recursive=True))[30:]:
    os.remove(f)"
expect "a style with too few buildings to live in" "most of the building types"

python3 -c "
import sys, gzip, io, glob, os
sys.path.insert(0,'$M/tools'); import nbt
f=sorted(glob.glob('$B/Cinderhold/**/*.blueprint', recursive=True))[0]
raw=open(f,'rb').read(); data=gzip.decompress(raw)
st=io.BytesIO(data); k=st.read(1)[0]; n=nbt._rs(st); root=nbt._read(st,k)
root.value['required_mods'].value=[]
buf=io.BytesIO(); buf.write(bytes([root.type])); e=n.encode(); buf.write(len(e).to_bytes(2,'big')+e)
nbt._write(buf, root); open(f,'wb').write(gzip.compress(buf.getvalue(), mtime=0))"
expect "a blueprint that builds air without its mod" "builds air"

python3 -c "
import sys, gzip, io, glob
sys.path.insert(0,'$M/tools'); import nbt
for f in sorted(glob.glob('$B/**/*.blueprint', recursive=True)):
    raw=open(f,'rb').read(); data=gzip.decompress(raw)
    st=io.BytesIO(data); k=st.read(1)[0]; n=nbt._rs(st); root=nbt._read(st,k)
    for p in root.value['palette'].value:
        if 'Name' in p.value and p.value['Name'].value.startswith('elysium'):
            p.value['Name'].value='minecraft:stone'
    buf=io.BytesIO(); buf.write(bytes([root.type])); e=n.encode(); buf.write(len(e).to_bytes(2,'big')+e)
    nbt._write(buf, root); open(f,'wb').write(gzip.compress(buf.getvalue(), mtime=0))"
expect "styles that are somebody else's packs renamed" "under a different name"

echo
echo "restoring and re-checking"
restore
if (cd $M && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
echo "colonies (stakes, grants, market, styles): $pass caught, $fail missed"
[ "$fail" -eq 0 ]
