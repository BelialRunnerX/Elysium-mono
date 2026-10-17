#!/usr/bin/env bash
# Break each new check on purpose, confirm it fires, restore.
#
# No pipefail here: validate.py exits 1 when it finds the problem we asked it
# to find, and under pipefail that turned every successful catch into a miss.
set -uo pipefail
cd /tmp/Elysium-mono/elysium-solarpunk
M=src/main/java/com/elysium/solarpunk/machine
pass=0; fail=0

out() { python3 validate.py 2>&1; }

expect() {   # name, substring that must appear
  local name="$1" want="$2" got
  got="$(out)"
  if printf '%s' "$got" | grep -qF -- "$want"; then
    echo "  caught : $name"; pass=$((pass+1))
  else
    echo "  MISSED : $name"; fail=$((fail+1))
  fi
}

# A phrase that is absent proves nothing unless the validator finished: a crash
# or a kill prints no phrase either (16 Oct). So the validator must also pass,
# with its own last line, before a must-not-flag case counts.
reject() {   # name, substring that must NOT appear
  local name="$1" unwanted="$2" got code
  got="$(out)"; code=$?
  if printf '%s' "$got" | grep -qF -- "$unwanted"; then
    echo "  FALSE+ : $name"; fail=$((fail+1))
  elif [ "$code" -ne 0 ] || ! printf '%s' "$got" | grep -qF -- "all solarpunk checks passed"; then
    echo "  MISSED : $name (validate.py did not finish with its pass line: exit $code, ${#got} byte(s))"
    fail=$((fail+1))
  else
    echo "  quiet  : $name"; pass=$((pass+1))
  fi
}

# Bytes in, bytes out (17 Oct 2026). A bare open() on Windows reads cp1252 and
# writes every line ending back as CRLF, so a file this edits and then edits
# back, as the renamed-accessor case once did to SolarpunkMachines.java, came
# back with every line changed.
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
assert old in s, 'anchor not found in ' + p
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "capability registration"
cp $M/SolarpunkMachines.java /tmp/sm.bak
edit $M/SolarpunkMachines.java '        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
                WATER_RECLAIMER_ENTITY.get(),
                (machine, side) -> machine.getTank());' ''
expect "reclaimer tank never registered" "water_reclaimer exposes getTank()"
cp /tmp/sm.bak $M/SolarpunkMachines.java

cp $M/SolarpunkMachines.java /tmp/sm.bak
edit $M/SolarpunkMachines.java '        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                WIND_CAPSTAN_ENTITY.get(),
                (machine, side) -> machine.getEnergy());' ''
expect "capstan energy never registered" "wind_capstan exposes getEnergy()"
cp /tmp/sm.bak $M/SolarpunkMachines.java

# getEnergy() lives on the shared superclass, so this only fires if the check
# follows the extends chain. It did not, at first, and every machine's energy
# registration was unguarded.

cp $M/SolarpunkMachines.java /tmp/sm.bak
edit $M/SolarpunkMachines.java '        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                ALGAE_BIOREACTOR_ENTITY.get(),
                (machine, side) -> machine.getOutput());' ''
expect "bioreactor output never registered" "algae_bioreactor exposes getOutput()"
cp /tmp/sm.bak $M/SolarpunkMachines.java

# The capability has to be matched to the right machine, not just present
# somewhere in the file. This is the mistake a copy-paste actually makes.
cp $M/SolarpunkMachines.java /tmp/sm.bak
edit $M/SolarpunkMachines.java 'Capabilities.FluidHandler.BLOCK,
                WATER_RECLAIMER_ENTITY.get()' 'Capabilities.FluidHandler.BLOCK,
                ALGAE_BIOREACTOR_ENTITY.get()'
expect "tank registered against the wrong machine" "water_reclaimer exposes getTank()"
cp /tmp/sm.bak $M/SolarpunkMachines.java

echo "a mention is not a declaration"
cp $M/WindCapstanEntity.java /tmp/wc.bak
edit $M/WindCapstanEntity.java '    public static final int CAPACITY = 24_000;' \
  '    public static final int CAPACITY = 24_000;
    private static int noise() { return "getTank".length(); }'
reject "string literal read as an accessor" "wind_capstan exposes getTank()"
cp /tmp/wc.bak $M/WindCapstanEntity.java

echo "the harness arithmetic"
cp $M/WindCapstanEntity.java /tmp/wc.bak
edit $M/WindCapstanEntity.java 'if (!seesSky || exposed <= 0) {' 'if (!seesSky) {'
expect "capstan runs while bricked in" "enclosed capstan generated"
cp /tmp/wc.bak $M/WindCapstanEntity.java

cp $M/WindCapstanEntity.java /tmp/wc.bak
edit $M/WindCapstanEntity.java 'int above = Math.max(0, Math.min(span, height - BASE_HEIGHT));' \
  'int above = Math.max(0, height - BASE_HEIGHT);'
expect "height is not clamped at the top" "above the full-height mark still pays more"
cp /tmp/wc.bak $M/WindCapstanEntity.java

# The check the whole feature exists for, broken in each direction.
#
# First: a capstan that stops at night is a condenser with extra steps.
cp $M/WindCapstanEntity.java /tmp/wc.bak
edit $M/WindCapstanEntity.java 'if (!seesSky || exposed <= 0) {
            return 0;
        }' 'if (!seesSky || exposed <= 0 || true) {
            return 0;
        }'
expect "capstan does not cover the gap" "the capstan does not cover the condenser"
cp /tmp/wc.bak $M/WindCapstanEntity.java

# Second: a capstan that beats a condenser everywhere makes the condenser
# pointless. This is the direction a balance pass reaches by accident.
cp $M/WindCapstanEntity.java /tmp/wc.bak
edit $M/WindCapstanEntity.java 'public static final int PEAK_OUTPUT = 14;' \
  'public static final int PEAK_OUTPUT = 30;'
edit $M/WindCapstanEntity.java 'int byHeight = PEAK_OUTPUT * above / span;' \
  'int byHeight = PEAK_OUTPUT;'
expect "capstan strictly dominates" "no reason to build a condenser"
cp /tmp/wc.bak $M/WindCapstanEntity.java

cp $M/WaterReclaimerEntity.java /tmp/wr.bak
edit $M/WaterReclaimerEntity.java 'return raining ? CLEAR_RATE * RAIN_FACTOR : CLEAR_RATE;' \
  'return raining ? CLEAR_RATE * RAIN_FACTOR : 0;'
expect "rain is a switch rather than a rate" "so it is a rain sensor"
cp /tmp/wr.bak $M/WaterReclaimerEntity.java

# The two halves of the interlock disagreeing by a little is the bug that would
# actually ship: a reclaimer that says it is supplying and a bioreactor beside
# it that is stopped.
cp $M/WaterReclaimerEntity.java /tmp/wr.bak
edit $M/WaterReclaimerEntity.java 'public static final int SUPPLY_THRESHOLD = FluidType.BUCKET_VOLUME;' \
  'public static final int SUPPLY_THRESHOLD = FluidType.BUCKET_VOLUME / 2;'
expect "the two halves of the interlock disagree" "there is a band where a reclaimer says"
cp /tmp/wr.bak $M/WaterReclaimerEntity.java

echo "the kiln"
# Efficiency 1 alone is not a break — the kiln would still win on being faster
# per item — so break the thing the promise is actually about: make one fuel
# item go exactly as far as it does in a furnace.
cp $M/CeramicKilnEntity.java /tmp/ck.bak
edit $M/CeramicKilnEntity.java 'return burnTime * FUEL_EFFICIENCY / COOK_TICKS;' \
  'return furnaceItemsPerFuel(burnTime);'
expect "kiln saves no fuel" "the block's whole promise"
cp /tmp/ck.bak $M/CeramicKilnEntity.java

cp $M/CeramicKilnEntity.java /tmp/ck.bak
edit $M/CeramicKilnEntity.java 'public static final int FUEL_EFFICIENCY = 2;' \
  'public static final int FUEL_EFFICIENCY = 9;'
expect "kiln makes every other heat source pointless" "more than a fourfold saving"
cp /tmp/ck.bak $M/CeramicKilnEntity.java

cp $M/CeramicKilnEntity.java /tmp/ck.bak
edit $M/CeramicKilnEntity.java 'public static final int DRAW = 3;' \
  'public static final int DRAW = 6;'
expect "kiln power input is decoration" "power input is decoration"
cp /tmp/ck.bak $M/CeramicKilnEntity.java

cp $M/CeramicKilnEntity.java /tmp/ck.bak
edit $M/CeramicKilnEntity.java 'public static final int DRAW = 3;' \
  'public static final int DRAW = 1;'
expect "bioreactor detour loses" "a detour that loses"
cp /tmp/ck.bak $M/CeramicKilnEntity.java

cp $M/CeramicKilnEntity.java /tmp/ck.bak
cp $M/SolarpunkMachines.java /tmp/sm.bak
edit $M/CeramicKilnEntity.java 'public ItemStackHandler getInventory()' \
  'public ItemStackHandler getInventoryX()'
edit $M/SolarpunkMachines.java 'machine.getInventory()' 'machine.getInventoryX()'
reject "renamed accessor still registered" "ceramic_kiln exposes getInventory()"
cp /tmp/ck.bak $M/CeramicKilnEntity.java
cp /tmp/sm.bak $M/SolarpunkMachines.java

echo "the kiln's input set"
cp tools/gen_solarpunk.py /tmp/gs.bak
edit tools/gen_solarpunk.py '"minecraft:clay_ball": "clay fired into brick is what a kiln is for",' ''
expect "an addition dropped while the tag keeps it" "the kiln takes minecraft:clay_ball"
cp /tmp/gs.bak tools/gen_solarpunk.py

# The other direction, and the one a hand-list would reach by rotting: the rule
# loses an entry, the tag is regenerated, and vanilla still fires it.
#
# Running the generator rewrites everything it owns (main() deletes it first),
# not only the tag, and the Pillow on this machine writes the same pixels as
# other bytes than the PNGs in the repository: putting back the generator and
# the tag left 82 textures modified (17 Oct 2026). So all it owns is kept and
# put back byte for byte.
G_OWNS="src/main/resources/assets/elysiumsolarpunk src/main/resources/data src/main/java/com/elysium/solarpunk/SolarpunkContent.java src/main/java/com/elysium/solarpunk/block/pending MACHINES.md"
cp tools/gen_solarpunk.py /tmp/gs.bak
tar cf /tmp/machines2_gen.tar $G_OWNS
edit tools/gen_solarpunk.py '    "minecraft:clay",
' ''
python3 tools/gen_solarpunk.py >/dev/null 2>&1
expect "the shipped list rots behind vanilla" "vanilla fires minecraft:clay and the kiln will not take it"
cp /tmp/gs.bak tools/gen_solarpunk.py
rm -rf src/main/resources/assets/elysiumsolarpunk src/main/resources/data src/main/java/com/elysium/solarpunk/block/pending
tar xf /tmp/machines2_gen.tar; rm -f /tmp/machines2_gen.tar

cp tools/gen_solarpunk.py /tmp/gs.bak
edit tools/gen_solarpunk.py '"minecraft:redstone_ore": "an ore, whatever category it is filed under",' ''
expect "ore processing leaks into the kiln" "vanilla fires minecraft:redstone_ore"
cp /tmp/gs.bak tools/gen_solarpunk.py

# An exception that has stopped excluding anything.
cp tools/gen_solarpunk.py /tmp/gs.bak
edit tools/gen_solarpunk.py '"minecraft:wet_sponge": "drying a sponge is not firing anything",' \
  '"minecraft:wet_sponge": "drying a sponge is not firing anything",
    "minecraft:beef": "cooking is not firing",'
expect "a no-op exclusion left in the list" "excludes nothing"
cp /tmp/gs.bak tools/gen_solarpunk.py

# The shipped tag drifting from the rule that produced it.
cp src/main/resources/data/elysiumsolarpunk/tags/item/kiln_inputs.json /tmp/kt.bak
edit src/main/resources/data/elysiumsolarpunk/tags/item/kiln_inputs.json \
  '"minecraft:clay",' '"minecraft:iron_ore",'
expect "hand-edited tag takes an ore" "which vanilla files as"
cp /tmp/kt.bak src/main/resources/data/elysiumsolarpunk/tags/item/kiln_inputs.json

echo
echo "negative tests: $pass caught, $fail missed"
python3 validate.py >/dev/null 2>&1 && echo "restored clean" || echo "RESTORE FAILED"
[ "$fail" -eq 0 ]
