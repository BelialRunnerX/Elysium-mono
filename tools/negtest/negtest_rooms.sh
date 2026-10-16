#!/usr/bin/env bash
# §8.5's rooms, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono/elysium-lib
R=$ROOT/src/main/java/com/elysium/lib/room
H=$ROOT/src/main/java/com/elysium/lib/hazard
E=$ROOT/src/main/java/com/elysium/lib/event
T=$ROOT/src/main/resources/data/elysiumlib/tags/block/room
L=$ROOT/src/main/resources/assets/elysiumlib/lang/en_us.json
pass=0; fail=0
out() { (cd $ROOT && python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "the minimum function"
cp $R/ElysiumRoom.java /tmp/rm.bak
edit $R/ElysiumRoom.java 'if (census.count(requirement.getKey()) < requirement.getValue()) {' \
                         'if (false) {'
expect "a room that asks for nothing" "that requirement is decoration"
cp /tmp/rm.bak $R/ElysiumRoom.java

cp $R/ElysiumRoom.java /tmp/rm.bak
edit $R/ElysiumRoom.java 'if (census == null || !census.sealed() || census.volume() < minimumVolume) {' \
                         'if (census == null) {'
expect "an unsealed field counting as a room" "matches an unsealed volume"
cp /tmp/rm.bak $R/ElysiumRoom.java

echo "one volume, one room"
cp $R/ElysiumRooms.java /tmp/rs.bak
edit $R/ElysiumRooms.java 'id("greenhouse"), 20, 27,' 'id("greenhouse"), 30, 27,'
expect "two rooms sharing a rank" "depends on the order"
cp /tmp/rs.bak $R/ElysiumRooms.java

cp $R/ElysiumRoom.java /tmp/rm.bak
edit $R/ElysiumRoom.java 'if (room.matches(census) && (best == null || room.rank > best.rank)) {' \
                         'if (room.matches(census) && best == null) {'
expect "the first matching room winning instead of the best" "rather than the highest-ranked"
cp /tmp/rm.bak $R/ElysiumRoom.java

echo "the budget"
cp $R/ElysiumRooms.java /tmp/rs.bak
edit $R/ElysiumRooms.java '0.35F, Set.of());' '0.80F, Set.of());'
expect "a room generous enough to replace the armour set" "above the 0.35"
cp /tmp/rs.bak $R/ElysiumRooms.java

cp $R/ElysiumRooms.java /tmp/rs.bak
edit $R/ElysiumRooms.java '            0.20F, Set.of());' '            0.02F, Set.of());'
expect "a room worth less than a sandwich" "less than eating something does"
cp /tmp/rs.bak $R/ElysiumRooms.java

echo "the wiring"
cp $H/ElysiumProtection.java /tmp/p.bak
edit $H/ElysiumProtection.java 'float room = roomShare(entity, hazard);' 'float room = 0.0F;'
expect "the room never reaching the arithmetic" "exactly as much as standing in the rain"
cp /tmp/p.bak $H/ElysiumProtection.java

cp $E/ElysiumHazardHandler.java /tmp/e.bak
edit $E/ElysiumHazardHandler.java 'announceRoom(player, com.elysium.lib.room.ElysiumRoomCache.at(level, pos));' ''
expect "the tick never asking which room" "detected by nothing"
cp /tmp/e.bak $E/ElysiumHazardHandler.java

cp $H/ElysiumProtection.java /tmp/p.bak
edit $H/ElysiumProtection.java 'com.elysium.lib.room.ElysiumRoomCache.at(' \
                               'com.elysium.lib.room.ElysiumRoom.classify(com.elysium.lib.room.ElysiumRoomScan.survey('
expect "scanning on every hazard instead of through the cache" "five times a second"
cp /tmp/p.bak $H/ElysiumProtection.java

echo "the data behind it"
mv $T/shielding.json /tmp/sh.bak
expect "a requirement tag with no file" "can never be built"
mv /tmp/sh.bak $T/shielding.json

cp $T/growing.json /tmp/gr.bak
python3 -c "
import json,sys
p=sys.argv[1]; d=json.load(open(p)); d['values']=[]; json.dump(d,open(p,'w'))
" $T/growing.json
expect "an empty requirement tag" "is empty"
cp /tmp/gr.bak $T/growing.json

cp $T/light.json /tmp/li.bak
python3 -c "
import json,sys
p=sys.argv[1]; d=json.load(open(p)); d['values'].append('elysium:registry_beacon'); json.dump(d,open(p,'w'))
" $T/light.json
expect "the library naming a content mod" "must not know about any mod"
cp /tmp/li.bak $T/light.json

cp $L /tmp/l.bak
edit $L '"elysium.room.bunker.desc"' '"elysium.room.bunker.gone"'
expect "a room with no description" "what it is made of"
cp /tmp/l.bak $L

echo "the cache's bounds"
cp $R/ElysiumRoomCache.java /tmp/rc.bak
edit $R/ElysiumRoomCache.java 'public static final int TTL = 40;' 'public static final int TTL = 6000;'
expect "a cached room trusted for minutes" "keep its protection"
cp /tmp/rc.bak $R/ElysiumRoomCache.java

cp $R/ElysiumRoomScan.java /tmp/rk.bak
edit $R/ElysiumRoomScan.java 'public static final int MAX_VOLUME = 512;' 'public static final int MAX_VOLUME = 40;'
expect "a scan that gives up before a bunker" "can never be detected"
cp /tmp/rk.bak $R/ElysiumRoomScan.java

echo
if (cd $ROOT && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED"; fail=$((fail+1))
fi
echo "rooms: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
