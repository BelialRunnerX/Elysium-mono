#!/usr/bin/env bash
# The runs: one rule for every pipe, cable, wire, conduit, bus and channel.
# Each case breaks one thing the rule or a module's use of it promises, and
# the library's or the module's validator has to say so.
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
L=$ROOT/elysium-lib
S=$ROOT/elysium-space
LR=$L/src/main/java/com/elysium/lib/run
SS=$S/src/main/java/com/elysium/space
SA=$S/src/main/resources/assets/elysiumspace
SD=$S/src/main/resources/data/elysiumspace
SNAP=/tmp/runs_snap.tar
# Python on Windows cannot see a /tmp path; hand it the folders the way it spells them.
export RUNS_SA="$(cygpath -m "$SA" 2>/dev/null || echo "$SA")"
export RUNS_SD="$(cygpath -m "$SD" 2>/dev/null || echo "$SD")"
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-lib elysium-space art/runs.py)
restore() { rm -rf $ROOT/elysium-lib $ROOT/elysium-space && (cd $ROOT && tar xf $SNAP); }
out_lib() { (cd $L && python3 validate.py 2>&1); }
out_space() { (cd $S && python3 validate.py 2>&1); }
expect() { local which="$1" name="$2" want="$3" got
  if [ "$which" = lib ]; then got="$(out_lib)"; else got="$(out_space)"; fi
  if [[ "$got" == *"$want"* ]]; then echo "  caught : $name"; pass=$((pass+1));
  else echo "  MISSED : $name"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, 'anchor not found in ' + p
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "== the rule"
# a plate too small to be a cue
edit $LR/RunRules.java 'public static final int PLATE = 10;' 'public static final int PLATE = 4;'
expect lib "a plate no wider than the arm" "FAIL"
# a dye that joins another dye
edit $LR/RunRules.java 'return da.isEmpty() || db.isEmpty() || da.equals(db);' 'return true;'
expect lib "red joining blue" "FAIL"
# an undyed run losing its own colour
edit $LR/RunRules.java 'return isDye(dye) ? DYE_RGB.get(dye) : own;' 'return isDye(dye) ? DYE_RGB.get(dye) : 0xFFFFFF;'
expect lib "an undyed plate painted white" "FAIL"

echo "== the helper"
# dyeing for free in survival
edit $LR/Runs.java '        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }' ''
expect lib "dyeing a run costs nothing" "dyeing a run is free in survival"
# a dye change nobody hears about
edit $LR/Runs.java '        level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        relinkAround(level, pos, device);' '        level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);'
expect lib "a dyed run keeping stale arms" "does not tell its neighbours"
# the tints registered for nobody
edit $LR/RunColors.java '            event.register(Runs::tint, block);' ''
expect lib "tints registered for no run" "dye would show nowhere"

echo "== a module's run"
# the pipe's blockstate without a plate towards a device
python3 - <<'PYX'
import json, pathlib, os
p = pathlib.Path(os.environ["RUNS_SA"]) / "blockstates/pipe.json"
d = json.loads(p.read_text())
d["multipart"] = [part for part in d["multipart"] if not str(part.get("apply", {}).get("model", "")).endswith("_plate")]
p.write_text(json.dumps(d, indent=2))
PYX
expect space "a pipe with no plate against a device" "draws no plate towards a device"
# the plate the wrong size on disk
python3 - <<'PYX'
import json, pathlib, os
p = pathlib.Path(os.environ["RUNS_SA"]) / "models/block/pipe_plate.json"
d = json.loads(p.read_text())
d["elements"][0]["from"][0] = 6
p.write_text(json.dumps(d, indent=2))
PYX
expect space "a plate smaller than the rule" "RunRules asks for"
# a body face without its tint, so a dye would not colour it
python3 - <<'PYX'
import json, pathlib, os
p = pathlib.Path(os.environ["RUNS_SA"]) / "models/block/cable_arm.json"
d = json.loads(p.read_text())
d["elements"][0]["faces"]["north"].pop("tintindex", None)
p.write_text(json.dumps(d, indent=2))
PYX
expect space "an arm a dye cannot colour" "so a dye would not colour it"
# a dye recipe missing
rm -f $SD/recipe/pipe_dyed_red.json
expect space "a pipe that cannot be dyed red" "cannot be dyed red at a crafting table"
# a recipe that makes a plain pipe from a dye
python3 - <<'PYX'
import json, pathlib, os
p = pathlib.Path(os.environ["RUNS_SD"]) / "recipe/cable_dyed_blue.json"
d = json.loads(p.read_text())
d["result"].pop("components")
p.write_text(json.dumps(d, indent=2))
PYX
expect space "a dye recipe that forgets the dye" "does not wear the dye"
# the block deciding its own links
edit $SS/machine/ConduitBlock.java 'return state.setValue(RunSides.BY_SIDE.get(side), Runs.link(level, pos, side, this, this::device));' 'return state;'
expect space "a conduit that never relinks" "Runs.link("
# the block dropping its dye when picked
edit $SS/machine/ConduitBlock.java 'return Runs.clone(super.getCloneItemStack(state, target, level, pos, player), level, pos);' 'return super.getCloneItemStack(state, target, level, pos, player);'
expect space "a picked pipe forgetting its dye" "loses its dye when picked"
# a run nobody declared
edit $SS/ElysiumSpace.java '        com.elysium.lib.run.Runs.declare(SpaceBlocks.PIPE, 0x9AB0C8);' ''
expect space "a pipe with no colour of its own" "is not declared a run"
# a block entity that forgets the dye
edit $SS/machine/ConduitBlockEntity.java 'public abstract class ConduitBlockEntity extends MachineBlockEntity implements RunDyed {' 'public abstract class ConduitBlockEntity extends MachineBlockEntity {'
expect space "a conduit forgetting its dye" "does not carry it"

# a cable the game calls not solid, so water washes it away
edit $SS/machine/ConduitBlock.java 'super(Runs.properties(properties));' 'super(properties);'
expect space "a cable water washes away" "is not solid to the game"

echo "runs: $pass caught, $fail missed"
rm -f $SNAP
[ $fail -eq 0 ]
