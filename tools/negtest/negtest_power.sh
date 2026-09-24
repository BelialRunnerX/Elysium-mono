#!/bin/bash
cd /tmp/Elysium-mono/elysium-solarpunk || exit 1
J=src/main/java/com/elysium/solarpunk
pass=0; miss=0

snap() { rm -rf /tmp/_pw; mkdir -p /tmp/_pw; cp -r $J/power /tmp/_pw/; cp -r $J/machine /tmp/_pw/; cp tools/gen_solarpunk.py /tmp/_pw/; cp src/main/resources/assets/elysiumsolarpunk/lang/en_us.json /tmp/_pw/; }
restore() { rm -rf $J/power $J/machine; cp -r /tmp/_pw/power $J/; cp -r /tmp/_pw/machine $J/; cp /tmp/_pw/gen_solarpunk.py tools/; cp /tmp/_pw/en_us.json src/main/resources/assets/elysiumsolarpunk/lang/; }

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

try "Minecraft import in PowerBudget" "The budget arithmetic is" \
  "sed -i \"s|^package com.elysium.solarpunk.power;|package com.elysium.solarpunk.power;\\n\\nimport net.minecraft.world.level.Level;|\" $J/power/PowerBudget.java"

try "a class renamed in the enum only" "disagree about the power classes" \
  "sed -i 's|COMFORT(\"comfort\")|COMFORT(\"luxury\")|' $J/power/PowerClass.java"

try "comfort promoted above critical" "the priority order" \
  "python3 - <<'PY'
import io,re
p='$J/power/PowerClass.java'
s=io.open(p,encoding='utf-8').read()
s=s.replace('    CRITICAL(\"critical\"),','    COMFORT(\"comfort\"),',1)
s=s.replace('    COMFORT(\"comfort\");','    CRITICAL(\"critical\");',1)
io.open(p,'w',encoding='utf-8').write(s)
PY"

try "the reclaimer loses its class" "no longer declares a power class" \
  "python3 - <<'PY'
import io,re
p='$J/machine/WaterReclaimerEntity.java'
s=io.open(p,encoding='utf-8').read()
s=re.sub(r'    @Override\n    public PowerClass powerClass\(\) \{\n        return PowerClass\.CRITICAL;\n    \}\n\n','',s,count=1)
io.open(p,'w',encoding='utf-8').write(s)
PY"

try "the harvester ranked as industry" "is INDUSTRY, not LOGISTICS" \
  "sed -i 's|return PowerClass.LOGISTICS;|return PowerClass.INDUSTRY;|' $J/machine/HarvesterEntity.java"

try "the grid walk loses its bound" "has lost one of its bounds" \
  "sed -i 's|MAX_CONDUITS|CONDUIT_CEILING|g' $J/power/PowerGrid.java"

try "the grid reads through unloaded chunks" "no longer skips unloaded chunks" \
  "sed -i 's|if (!level.isLoaded(side)) {|if (false) {|' $J/power/PowerGrid.java"

try "the push stops allocating" "no longer allocates through PowerBudget" \
  "sed -i 's|PowerBudget.allocate(source.getEnergyStored(), classes, wants)|wants|' $J/machine/SolarpunkPower.java"

try "the push extracts before offering" "stopped offering before taking" \
  "sed -i 's|source.extractEnergy(taken, false);|source.extractEnergy(grants\[i\], false);|' $J/machine/SolarpunkPower.java"

try "the brownout counter leaves the buffer" "no longer driven by the buffer" \
  "sed -i 's|^                        noteUnpaid();|                        // removed|' $J/machine/SolarpunkMachineEntity.java"

try "the conduit's Java drifts from the table" "the shipped Java registers" \
  "sed -i 's|\"block_class\": \"com.elysium.solarpunk.power.PowerConduitBlock\"|\"block_class\": \"com.elysium.solarpunk.power.ConduitBlock\"|' tools/gen_solarpunk.py"

echo "---- caught $pass, missed $miss"
