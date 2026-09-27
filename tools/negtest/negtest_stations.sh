#!/usr/bin/env bash
# Eight blocks that do what a player would otherwise do, broken one at a time.
#
# What a station buys is attention rather than speed. Every rule below is a
# way one of them could quietly become a multiplier instead.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-comforts
S=$M/src/main/java/com/elysium/comforts/rules/StationRules.java
SB=$M/src/main/java/com/elysium/comforts/block/StationBlock.java
KE=$M/src/main/java/com/elysium/comforts/block/StationBlockEntity.java
RB=$M/src/main/java/com/elysium/comforts/registry/ComfortBlocks.java
G=$M/tools/gen_comforts.py
V=$M/validate.py
SNAP=/tmp/stations_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 400 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit."
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-comforts)
restore() { rm -rf $ROOT/elysium-comforts && (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }
regen() { (cd $M && python3 tools/gen_comforts.py >/dev/null 2>&1); }
expect_regen() { regen; expect "$1" "$2"; }

echo "never better value than a pair of hands"
edit $S '            if (hand != null && station.perInput() > hand + 1.0E-9D) {' '            if (false) {'
expect "a station that hands back twice what a player gets, unreported" "a multiplier on everything"

edit $S '            Double hand = byHand.get(station.job());
            if (hand == null) {
                bad.add("nothing says what a player gets out of "' \
        '            Double hand = byHand.get(station.job());
            if (false) {
                bad.add("nothing says what a player gets out of "'
expect "a station measured against nothing at all, unreported" "measured against nothing at all is not reported"

edit $S '            new Station("villager_farm", FARM, 1, 3, 600, true),' \
        '            new Station("villager_farm", FARM, 1, 6, 600, true),'
expect "a farm that doubles what a farmer grows" "it is a multiplier"

edit $S '        return takes <= 0 ? gives : gives / (double) takes;' '        return gives;'
expect "value worked out without counting what went in" "for every one put in and a player"

echo "never quicker than the player it replaces"
edit $S '            if (hand != null && station.ticks() < hand) {' '            if (false) {'
expect "a station quicker than a player, unreported" "quicker than the player it replaces is not"

edit $S '            Integer hand = byHand.get(station.job());
            if (hand == null) {
                bad.add("nothing says how long a player takes over "' \
        '            Integer hand = byHand.get(station.job());
            if (false) {
                bad.add("nothing says how long a player takes over "'
expect "a station with nothing to be slower than, unreported" "nothing to be slower than is not reported"

edit $S '            new Station("converter", CURE, 1, 1, 3_000, true),' \
        '            new Station("converter", CURE, 1, 1, 30, true),'
expect "a converter that cures a zombie villager in a second and a half" "no reason left to do it yourself"

echo "eight jobs, eight blocks"
edit $S '            if (!jobs.contains(station.job())) {' '            if (false) {'
expect "a station doing something nothing knows how to do, unreported" "nothing knows how to do is not"

edit $S '            if (!done.add(station.job())) {' '            if (false) {'
expect "two stations doing one job, unreported" "two stations doing one job are not reported"

edit $S '            if (!done.contains(job)) {' '            if (false) {'
expect "a job nothing does, unreported" "a job nothing does is not reported"

edit $S '        bad.addAll(faultsInNames(STATIONS));' '        '
expect "the rule that says no two stations share a name, never added up" "never added up by the"

edit $S '            if (!named.add(station.id())) {' '            if (false) {'
expect "two stations under one name, unreported" "written under one name are not reported"

echo "a villager you get back"
edit $S '        if (holding == 0) {' '        if (false) {'
expect "a set of stations none of which holds a villager, unreported" "not one of which holds a villager is not"

edit $S '        if (holding == stations.size()) {' '        if (false) {'
expect "a viewer that swallows the villager you wanted to look at, unreported" "swallows a villager is"

edit $S '        bad.addAll(faultsInHolding(STATIONS));' '        '
expect "the rule that says a villager comes back, never added up" "never added up by the"

echo "the block a player places"
edit $SB '        StationRules.Station found = StationRules.station(id);' \
         '        StationRules.Station found = StationRules.STATIONS.get(0);'
expect "a station that no longer finds its own row" "find its own row by the name"

edit $SB '        if (level.getBlockEntity(pos) instanceof StationBlockEntity kept) {
            kept.aGoAt(level, pos, state, station());
        }
        level.scheduleTick(pos, this, station().ticks());' \
        '        if (level.getBlockEntity(pos) instanceof StationBlockEntity kept) {
            kept.aGoAt(level, pos, state, station());
        }
        level.scheduleTick(pos, this, 40);'
expect "a pace typed into the block rather than derived" "typed in the block"

edit $RB '        for (StationRules.Station station : StationRules.STATIONS) {' \
         '        for (StationRules.Station station : StationRules.STATIONS.subList(0, 4)) {'
expect "a registry that registers half the stations" "named by hand rather than walked"

edit $RB '        if (made.size() != StationRules.STATIONS.size()) {
            throw new IllegalStateException("the rules ask for "
                    + StationRules.STATIONS.size() + " station(s) and "
                    + made.size() + " were registered");
        }' '        '
expect "a registry that never counts the stations it registered" "counts the stations that were registered"

echo "a villager that goes in and comes out"
edit $KE '        from.remove(ComfortComponents.TRADER.get());' '        '
expect "a station that takes a villager and leaves it in the crate as well" "there are two of it"

edit $KE '        held = new CompoundTag();
        setChanged();
        return back;' '        setChanged();
        return back;'
expect "a station that hands a villager back and keeps it too" "clicking one twice is a villager duplicator"

edit $KE '            back.set(ComfortComponents.TRADER.get(), held.copy());' '        '
expect "a station that hands back an empty crate" "hands back an empty crate"

edit $KE '    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {'         '    protected void notSavedAtAll(CompoundTag tag, HolderLookup.Provider lookup) {'
expect "a station that never writes down who is in it" "the first time the world is saved"

edit $KE '    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {'         '    protected void notLoadedAtAll(CompoundTag tag, HolderLookup.Provider lookup) {'
expect "a station that never reads back who was in it" "the first time the chunk unloads"

edit $KE '        CompoundTag written = from.get(ComfortComponents.TRADER.get());'         '        CompoundTag written = new CompoundTag();'
expect "a station that keeps a villager somewhere the crate does not" "two different villagers"

edit $SB '        if (!state.is(to.getBlock())
                && level.getBlockEntity(pos) instanceof StationBlockEntity kept
                && !kept.empty()) {' '        if (false) {
            StationBlockEntity kept = null;'
expect "breaking a station keeping whoever was in it" "the one way a player can get a villager out"

rm -f $KE
expect "nothing holding the villager a station is meant to be working" "blocks that do something to nothing"

echo "the world it is written out into"
edit $G '                    "e": {"item": STATION_HEART[name]}},' \
        '                    "e": {"item": "minecraft:emerald"}},'
expect_regen "eight stations made of the same nine things, so seven can never be crafted" "the game cannot tell them apart"

rm -f $M/src/main/resources/assets/elysiumcomforts/blockstates/iron_farm.json
expect "a station with no blockstate, which is a missing-texture cube" "has no blockstate"

edit $V '    _rules = set(re.findall(r"public static List<String> (faultsIn\w+)\(",
                            STATION))' '    _rules = set(re.findall(r"public static List<String> (faultsInZ\w+)\(",
                            STATION))'
expect "a rule sweep that finds no station rules at all" "station rule(s) are declared at all"

rm -f $S
expect "nothing saying what a station does or what it costs" "could hand back anything at all"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all comforts checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "stations: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
