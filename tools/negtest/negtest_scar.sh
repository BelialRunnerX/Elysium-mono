#!/bin/bash
cd /tmp/Elysium-mono/elysium-lib || exit 1
S=src/main/java/com/elysium/lib
T=src/main/resources/data/elysiumlib/tags/block/fragile_cover.json
pass=0; miss=0
# Two copies of this suite once ran at once — a foreground run the tool timed
# out kept going, and a second run snapshotted a file the first had broken for
# its own case, then "restored" the breakage. One copy at a time, and never
# start from a tree that is already failing.
exec 9>/tmp/negtest_scar.lock
negtest_lock() { if command -v flock >/dev/null 2>&1; then flock -n "$1"; else mkdir "$2" 2>/dev/null && trap "rmdir '$2' 2>/dev/null" EXIT; fi; }  # Git Bash on Windows has no flock
negtest_lock 9 /tmp/negtest_scar.lockdir || { echo "another negtest_scar is running; refusing"; exit 2; }
if ! timeout 1800 python3 validate.py >/dev/null 2>&1; then
  echo "SETUP FAILED: the tree is not clean before any case is broken"; exit 2
fi
snap() { rm -rf /tmp/_sc; mkdir -p /tmp/_sc; cp -r $S/scar /tmp/_sc/scar; cp $S/event/ElysiumCombatHandler.java /tmp/_sc/; cp $T /tmp/_sc/tag.json; }
restore() { rm -rf $S/scar; cp -r /tmp/_sc/scar $S/scar; cp /tmp/_sc/ElysiumCombatHandler.java $S/event/; cp /tmp/_sc/tag.json $T; }
try() {
  local label="$1" want="$2" break_it="$3"
  snap
  bash -c "$break_it" || { echo "SETUP FAILED: $label"; restore; return; }
  if timeout 1800 python3 validate.py 2>&1 | grep -qF "$want"; then
    echo "CAUGHT : $label"; pass=$((pass+1))
  else
    echo "MISSED : $label  (expected: $want)"; miss=$((miss+1))
  fi
  restore
}

try "the tag guard removed" "loses a crop field" \
  "sed -i 's|        if (!state.is(FRAGILE)) {|        if (false) {|' $S/scar/ElysiumCover.java"

try "the block-entity guard removed" "their contents go with them" \
  "sed -i 's|        if (state.hasBlockEntity()) {|        if (false) {|' $S/scar/ElysiumCover.java"

# The sed delimiter here is @, not |: the line under test contains a Java ||,
# which as a | delimiter would close the pattern early. That is what made this
# one case report SETUP FAILED while the guard it tests was fine.
try "the bed guard removed" "player's respawn" \
  "sed -i 's@state.is(BlockTags.PORTALS) || state.is(BlockTags.BEDS)@state.is(BlockTags.PORTALS)@' $S/scar/ElysiumCover.java"

try "the unloaded-chunk guard removed" "unloaded chunks" \
  "sed -i 's|        if (!level.isLoaded(at)) {|        if (false) {|' $S/scar/ElysiumCover.java"

try "the hardness rule ignored" "no longer consulted at all" \
  "sed -i 's|        return ElysiumScarring.breakable(hardness, true);|        return hardness >= 0.0F;|' $S/scar/ElysiumCover.java"

try "claims no longer protected" "existing answer to" \
  "sed -i 's|        if (ElysiumProtection.inShelteredClaim(server, centre)) {|        if (false) {|' $S/scar/ElysiumCover.java"

try "the per-blow limit removed" "limit on how many blocks" \
  "sed -i 's|                    if (broken >= ElysiumScarring.MOST_PER_BLOW) {|                    if (false) {|' $S/scar/ElysiumCover.java"

try "blocks deleted rather than dropped" "block a player has lost" \
  "sed -i 's|server.destroyBlock(at, true);|server.destroyBlock(at, false);|' $S/scar/ElysiumCover.java"

try "the telegraph link cut" "no warning at all" \
  "sed -i 's|        return com.elysium.lib.telegraph.ElysiumTelegraph.isDangerous(damage);|        return damage > 3.0F;|' $S/scar/ElysiumScarring.java"

try "scarring measured on what landed" "more solid the world becomes" \
  "sed -i 's|                victim.level(), victim, event.getAmount());|                victim.level(), victim, landed);|' $S/event/ElysiumCombatHandler.java"

try "scarring removed from the pipeline" "a package nothing calls" \
  "python3 -c \"
import io,re
p='$S/event/ElysiumCombatHandler.java'
s=io.open(p,encoding='utf-8').read()
s=re.sub(r'        com\.elysium\.lib\.scar\.ElysiumCover\.scar\(\n.*?\);\n','',s,count=1,flags=re.S)
io.open(p,'w',encoding='utf-8').write(s)
\""

try "planks put in the fragile tag" "something players build with" \
  "python3 -c \"
import json
p='$T'
d=json.load(open(p,encoding='utf-8'))
d['values'].append('minecraft:oak_planks')
json.dump(d,open(p,'w',encoding='utf-8'),indent=2)
\""

try "the fragile tag emptied" "empty as shipped" \
  "python3 -c \"
import json
p='$T'
d=json.load(open(p,encoding='utf-8'))
d['values']=[]
json.dump(d,open(p,'w',encoding='utf-8'),indent=2)
\""

try "a registry dragged into the rule" "takes the whole system out of the tests" \
  "sed -i 's|^package com.elysium.lib.scar;|package com.elysium.lib.scar;\n\nimport net.neoforged.neoforge.attachment.AttachmentType;|' $S/scar/ElysiumScarring.java"

try "structural blocks made breakable" "That is somebody's house" \
  "sed -i 's|    public static final float MAX_HARDNESS = 0.6F;|    public static final float MAX_HARDNESS = 6.0F;|' $S/scar/ElysiumScarring.java"

try "unbreakable blocks made breakable" "makes bedrock softer" \
  "sed -i 's|        if (!(hardness >= MIN_HARDNESS)) {|        if (false) {|' $S/scar/ElysiumScarring.java"

try "every blow made to scar" "Reliable cover destruction" \
  "sed -i 's|    public static final float CHANCE = 0.25F;|    public static final float CHANCE = 1.0F;|' $S/scar/ElysiumScarring.java"

echo "---- caught $pass, missed $miss"
