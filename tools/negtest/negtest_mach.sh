set -u
cd /tmp/Elysium-mono
BK=/tmp/mach-backup; rm -rf $BK; mkdir -p $BK
pass=0; fail=0
check () {
  rm -rf elysium-solarpunk/tools/__pycache__
  out=$(cd elysium-solarpunk && python3 -W ignore validate.py 2>&1)
  if echo "$out" | grep -q "$2"; then echo "  CAUGHT  $1"; pass=$((pass+1));
  else echo "  MISSED  $1 (wanted: $2)"; echo "$out" | tail -3 | sed 's/^/          /'; fail=$((fail+1)); fi
}
save(){ mkdir -p "$BK/$(dirname "$1")"; cp "$1" "$BK/$1"; }
restore(){ cp "$BK/$1" "$1"; rm -rf elysium-solarpunk/tools/__pycache__; }
G=elysium-solarpunk/tools/gen_solarpunk.py
M=elysium-solarpunk/src/main/java/com/elysium/solarpunk/machine
P=elysium-solarpunk/src/main/java/com/elysium/solarpunk/block/pending

echo "1. a machine is half-graduated: written but still listed as owing work"
# Every machine has graduated, so the MACHINES table is empty (`MACHINES = []`)
# and the condenser is put back into it beside its WORKING row. The anchor used
# to be the table's first row, mycelium_press; when that row went, the replace
# matched nothing and the case tested the clean tree (17 Oct 2026).
save $G
if python3 - <<'PY'
import pathlib
p=pathlib.Path("elysium-solarpunk/tools/gen_solarpunk.py"); t=p.read_text(encoding="utf-8")
old='\nMACHINES = []\n'
assert t.count(old)==1, "pattern missing: MACHINES = []"
t=t.replace(old,
            '\nMACHINES = [\n    {\n        "id": "solar_condenser", "name": "Solar Condenser",\n'
            '        "glow": "bloom", "light": 4,\n'
            '        "owes": "Gathers power from direct skylight.",\n'
            '        "core": "minecraft:daylight_detector", "window": NS + ":bloom_glass",\n'
            '    },\n]\n',1)
p.write_text(t, encoding="utf-8", newline="")
PY
then check "finished and outstanding at once" "cannot be finished and outstanding at once"
else echo "  SETUP FAILED: finished and outstanding at once"; fail=$((fail+1)); fi
restore $G

echo "2. a graduated machine keeps its stub in block/pending/"
# The stub is the one the generator itself writes for a pending machine
# (write_machine_java, with the condenser put in its MACHINES table for the
# call), left behind in block/pending/ after the machine graduated. Until 17 Oct
# 2026 this copied MyceliumPressBlock.java, which went when the last machine
# graduated, and ran the whole generator twice around it, which rewrote 82
# PNGs with this machine's Pillow and left them changed. Nothing here needs the
# generator's other output, so only the stub is written.
if python3 - <<'PY'
import sys
sys.path.insert(0, "elysium-solarpunk/tools")
import gen_solarpunk as g
g.MACHINES = [{"id": "solar_condenser", "name": "Solar Condenser",
               "owes": "Gathers power from direct skylight."}]
g.write_machine_java()
stub = g.JAVA / "block/pending/SolarCondenserBlock.java"
assert stub.is_file() and "extends PendingMachineBlock" in stub.read_text(encoding="utf-8"), \
    "the generator wrote no stub for the condenser"
PY
then check "finished machine left in pending/" "still has a stub in block/pending"
else echo "  SETUP FAILED: finished machine left in pending/"; fail=$((fail+1)); fi
rm -f $P/SolarCondenserBlock.java; rm -rf elysium-solarpunk/tools/__pycache__

echo "3. a working machine has no block entity registered"
save $M/SolarpunkMachines.java
sed -i 's/BLOCK_ENTITIES.register("solar_condenser"/BLOCK_ENTITIES.register("solar_condenser_disabled"/' $M/SolarpunkMachines.java
check "machine that never ticks" "would place, render and never tick"
restore $M/SolarpunkMachines.java

echo "4. the energy capability stops being registered"
save $M/SolarpunkMachines.java
sed -i 's/event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,/event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,/g' $M/SolarpunkMachines.java
check "machines connect to nothing" "connect to nothing"
restore $M/SolarpunkMachines.java

echo "5. the condenser out-produces what it will hand over"
save $M/SolarCondenserEntity.java
sed -i 's/public static final int TRANSFER = 200;/public static final int TRANSFER = 5;/' $M/SolarCondenserEntity.java
check "surplus lost in the buffer" "the surplus is lost"
restore $M/SolarCondenserEntity.java

echo "6. a bioreactor draws more than a condenser can make"
save $M/AlgaeBioreactorEntity.java
sed -i 's/public static final int DRAW = 12;/public static final int DRAW = 60;/' $M/AlgaeBioreactorEntity.java
check "loop does not close" "do not work together"
restore $M/AlgaeBioreactorEntity.java

echo "7. the condenser runs under a roof"
save $M/SolarCondenserEntity.java
sed -i 's/if (!seesSky || skyLight <= 0) {/if (false) {/' $M/SolarCondenserEntity.java
check "roofed condenser generates" "roofed condenser generated"
restore $M/SolarCondenserEntity.java

echo
rm -rf elysium-solarpunk/tools/__pycache__
(cd elysium-solarpunk && python3 -W ignore validate.py >/dev/null 2>&1) && echo "  restored tree passes" || { echo "  RESTORE FAILED"; fail=$((fail+1)); }
echo; echo "$pass caught, $fail missed"
