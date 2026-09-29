#!/usr/bin/env bash
# The brick machines in a player's hands, and the coke, broken one way at a time.
#
# The survival pass of 29 September 2026 found that what a coke oven or a blast
# furnace made came out only when the controller was broken, that a controller
# took whatever it was handed (a brick placed against it while building jammed
# it), and that the blast furnace made steel of iron alone, so the coke it is
# said to burn was burned by nothing. Each case undoes one piece of the fix.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-industry
S=$M/src/main/java/com/elysium/industry
R=$M/src/main/resources
G=$M/tools/gen_industry.py
B=$S/block/FurnaceBlock.java
F=$S/rules/FurnaceRules.java
SNAP=/tmp/industry3_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 300 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit is made."
  (cd $M && timeout 300 python3 validate.py 2>&1 | tail -5)
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-industry)
restore() { rm -rf $ROOT/elysium-industry && (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
out() { (cd $M && timeout 300 python3 validate.py 2>&1); }
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
regen() { (cd $M && ELYSIUM_INDUSTRY_OUT=$M python3 tools/gen_industry.py >/dev/null) || { echo "GENERATOR BROKE"; exit 2; }; }

echo "an empty hand takes what a formed controller made"
edit $B '            ItemStack made = state.getValue(FORMED) ? furnace.take() : ItemStack.EMPTY;' '            ItemStack made = ItemStack.EMPTY;'
expect "an empty hand that takes nothing from a formed controller" "takes nothing out of it, so what a coke"

edit $B '            } else {
                player.displayClientMessage(furnace.saying(level, pos, state), false);
            }' '            }'
expect "an empty hand with nothing to take told nothing" "is told nothing, so a"

edit $B '                return items.extractItem(FurnaceRules.OUTPUT, made.getCount(), false);' '                return items.extractItem(FurnaceRules.INPUT, made.getCount(), false);'
expect "an empty hand that takes the input rather than what was made" "does not hand over the output slot first"

edit $B '            if (!stuck.isEmpty() && FurnaceRules.slotFor(shape().id(), idOf(stuck),
                    inTag(stuck)) != FurnaceRules.INPUT) {' '            if (false) {'
expect "a brick jammed in the input before the fix, and nothing to empty it" "leaves a thing no job takes in the input for good"

echo "a thing with no job there passes through"
edit $B '            if (slot == FurnaceRules.REFUSED) {
                return false;
            }' '            if (false) {
                return false;
            }'
expect "an offer that takes whatever it is handed" "offer takes whatever it is handed"

edit $B '            if (!items.insertItem(slot, held.copyWithCount(1), false).isEmpty()) {' '            if (!items.insertItem(FurnaceRules.INPUT, held.copyWithCount(1), false).isEmpty()) {'
expect "coke handed to a blast furnace landing in its input" "offer puts a thing somewhere other than the slot"

edit $B '            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;' '            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;'
expect "a brick used on the controller handed to the empty-hand path" "does not pass through it, so a brick"

echo "a blast burns its coke, and waits without it"
edit $B '            if (!FurnaceRules.mayFire(firing, fuelHeld())) {
                return;
            }
            done++;' '            done++;'
expect "a blast furnace that fires without asking for coke" "a job starts without asking for its fuel"

edit $B '            fuel.shrink(FurnaceRules.burnedBy(firing));' '            fuel.shrink(0);'
expect "a lump of coke that fires for ever" "keeps its coke when the steel comes out"

edit $B '            return burns == null || fuel.isEmpty() || !burns.item().equals(idOf(fuel))
                    ? 0 : fuel.getCount();' '            return 64;'
expect "a fire that always reads full" "is not read out of the fuel slot"

edit $B '            Containers.dropContents(level, pos, furnace.spill());' '            Containers.dropContents(level, pos, new SimpleContainer(
                    furnace.items().getStackInSlot(0), furnace.items().getStackInSlot(1)));'
expect "a broken blast furnace that loses its coke" "a broken controller drops less than it holds"

edit $B '                super.setSize(Math.max(size, FurnaceRules.SLOTS));' '                super.setSize(size);'
expect "a controller saved with two slots loading with two" "can load with fewer than FurnaceRules.SLOTS slots"

edit $G '    "elysiumindustry.furnace.unfuelled":
        "Waiting for %s: a job burns %s, and it holds %s.",
' ''
regen
expect "a blast furnace with no words for waiting on coke" "a blast furnace waiting for coke says it is firing"

edit $F '            new Fuel("blast_furnace", "elysiumindustry:coke", COKE_PER_BLAST));' '            new Fuel("blast_furnace", "elysiumindustry:coke", 1));'
expect "a blast furnace whose coke is a number of its own" "does not burn coke at COKE_PER_BLAST a job"

echo "coke in every furnace, for as long as the rules say"
edit $F '    public static final int COKE_BURN = 3_200;' '    public static final int COKE_BURN = 3_000;'
expect "coke's burn time moved in the rules and not in the fuel map" "coke is no furnace fuel at FurnaceRules.COKE_BURN"

edit $G '          {"values": {f"{NS}:{COKE}": {"burn_time": COKE_BURN}}})' '          {"values": {f"{NS}:{COKE}": {"burn_time": 1600}}})'
regen
expect "a fuel map that burns coke for a number of its own" "coke is no furnace fuel at FurnaceRules.COKE_BURN"

rm -f $R/data/neoforge/data_maps/item/furnace_fuels.json
expect "coke that is no furnace fuel at all" "coke is no furnace fuel at FurnaceRules.COKE_BURN"

# Cut short to its first line and a half. truncate rather than a redirect, so
# that tools/dryrun.py neuters it like the rm above.
truncate -s 12 $R/data/neoforge/data_maps/item/furnace_fuels.json
expect "a fuel map that is not JSON, read as no fuel rather than a crash" "coke is no furnace fuel at FurnaceRules.COKE_BURN"

edit $G '          {"values": {f"{NS}:{COKE}": {"burn_time": COKE_BURN}}})' '          {"replace": True, "values": {f"{NS}:{COKE}": {"burn_time": COKE_BURN}}})'
regen
expect "a fuel map that throws away coal and logs" "which throws away every fuel"

edit $G '    fuels = os.path.join(OUT, "src/main/resources/data/neoforge/data_maps/item")' '    fuels = os.path.join(DATA, "data_maps/item")'
regen
expect "a fuel map under this module's namespace, which fills nothing" "fills data maps named"

echo "the rules under them, run by the harness"
edit $F '        return jobFor(shape, item, inTag) != null ? INPUT : REFUSED;' '        return INPUT;'
expect "a controller that takes a brick into its input" "which it has no job for, so it jams"

edit $F '            if (one.input().startsWith("#") ? inTag.test(one.input().substring(1))' '            if (one.input().startsWith("#") ? false'
expect "a coke oven that cannot see a log's tag" "refuses a log, which it chars by its tag"

edit $F '        return fuelHeld >= burnedBy(job);' '        return true;'
expect "a blast furnace that fires on no coke" "a blast furnace with no coke in it fires anyway"

edit $F '        return fuel == null ? 0 : fuel.perJob();' '        return 0;'
expect "a blast that burns no coke" "a blast burns 0 coke and the rules say"

edit $F '            new Fuel("blast_furnace", "elysiumindustry:coke", COKE_PER_BLAST));' '            new Fuel("blast_furnace", "minecraft:charcoal", COKE_PER_BLAST));'
expect "a blast furnace that burns something the oven does not make" "burns something other than what the coke oven makes"

edit $F '            new Fuel("blast_furnace", "elysiumindustry:coke", COKE_PER_BLAST));' '            new Fuel("blast_furnace", "elysiumindustry:coke", COKE_PER_BLAST),
            new Fuel("coke_oven", "minecraft:coal_block", 1));'
expect "a coke oven that wants fuel before it has made any" "the coke oven wants fuel, so the first lump"

edit $F '    public static final int FUEL = 2;' '    public static final int FUEL = 1;'
expect "a fuel slot that is the output slot" "are not three slots of three"

edit $F '    public static final int REFUSED = -1;' '    public static final int REFUSED = 0;'
expect "a refusal that answers the input slot" "refusing a thing answers a slot a controller has"

edit $F '            if (!machines.contains(one.shape())) {' '            if (false) {'
expect "a fuel check that cannot report a fuel for no machine" "a fuel for a machine this module does not have is not reported"

edit $F '            if (!burning.add(one.shape())) {' '            if (false) {'
expect "a fuel check that cannot report two fuels in one slot" "a machine with two fuels and one fuel slot is not reported"

edit $F '            if (!made) {' '            if (false) {'
expect "a fuel check that cannot report a fuel nothing makes" "a fuel nothing makes is not reported"

edit $F '                    if (job.shape().equals(one.shape())) {' '                    if (false) {'
expect "a fuel check that cannot report an oven burning what it makes" "a machine that burns the fuel it makes is not reported"

edit $F '                if (job.shape().equals(one.shape()) && job.input().equals(one.item())) {' '                if (false) {'
expect "a fuel check that cannot report a machine burning what it fires" "a machine that burns what it fires is not reported"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all industry checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | tail -3 | head -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "industry3: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
