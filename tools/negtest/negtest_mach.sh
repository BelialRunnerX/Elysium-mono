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
save $G
python3 - <<'PY'
import pathlib
p=pathlib.Path("elysium-solarpunk/tools/gen_solarpunk.py"); t=p.read_text()
t=t.replace('MACHINES = [\n    {\n        "id": "mycelium_press"',
            'MACHINES = [\n    {\n        "id": "solar_condenser", "name": "Solar Condenser",\n'
            '        "glow": "bloom", "light": 4,\n'
            '        "owes": "Gathers power from direct skylight.",\n'
            '        "core": "minecraft:daylight_detector", "window": NS + ":bloom_glass",\n'
            '    },\n    {\n        "id": "mycelium_press"',1)
p.write_text(t)
PY
check "finished and outstanding at once" "cannot be finished and outstanding at once"
restore $G

echo "2. a graduated machine keeps its stub in block/pending/"
save $G
python3 - <<'PY'
import pathlib
p=pathlib.Path("elysium-solarpunk/tools/gen_solarpunk.py"); t=p.read_text()
t=t.replace('''    if directory.is_dir():
        for stale in directory.glob("*Block.java"):''','''    if False and directory.is_dir():
        for stale in directory.glob("*Block.java"):''',1)
p.write_text(t)
PY
python3 elysium-solarpunk/tools/gen_solarpunk.py >/dev/null 2>&1
cp $P/SolarCondenserBlock.java /tmp/stub.java 2>/dev/null || true
python3 - <<'PY'
import pathlib,shutil
src=pathlib.Path("elysium-solarpunk/src/main/java/com/elysium/solarpunk/block/pending")
tpl=(src/"MyceliumPressBlock.java").read_text()
(src/"SolarCondenserBlock.java").write_text(tpl.replace("MyceliumPressBlock","SolarCondenserBlock"))
PY
check "finished machine left in pending/" "still has a stub in block/pending"
rm -f $P/SolarCondenserBlock.java; restore $G; python3 elysium-solarpunk/tools/gen_solarpunk.py >/dev/null 2>&1

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
