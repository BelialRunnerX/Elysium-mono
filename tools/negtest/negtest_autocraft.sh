#!/usr/bin/env bash
# Autocrafting, broken one way at a time.
#
# Everything the order, the budget and the hands added, defeated deliberately.
# A check nobody has seen fail is a check nobody has seen.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-logistics
R=$M/src/main/java/com/elysium/logistics/rules/LogisticRules.java
S=$M/src/main/java/com/elysium/logistics/block
G=$M/tools/gen_logistics.py
SNAP=/tmp/autocraft_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-logistics)
restore() { rm -rf $ROOT/elysium-logistics && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the order, which is the whole difference between a plan and a factory"
edit $R '            for (Map.Entry<String, Long> entry : new TreeMap<>(runs).entrySet()) {' '            for (Map.Entry<String, Long> entry : runs.entrySet()) {'
expect "an order settled over a hash map" "a different order on a different day"

edit $R '        return placed.size() == runs.size() ? List.copyOf(out) : List.of();' '        return List.copyOf(out);'
expect "a cycle returned as a plan with steps quietly left out" "silently leaves steps out"

edit $R '                        if (runs.containsKey(need) && !placed.contains(need)) {' '                        if (false) {'
expect "a job not held behind the jobs that make its ingredients" "not an order"

edit $R '        List<Job> jobs = refused == null ? order(steps, runs) : List.of();' '        List<Job> jobs = List.of();'
expect "planning that does not order what it planned" "every caller can do it differently"

edit $R '        if (refused == null && jobs.isEmpty() && !runs.isEmpty()) {' '        if (false) {'
expect "the walk and the ordering disagreeing, and a winner being picked" "disagree the answer is to refuse"

echo "the budget"
edit $R '        if (--budget[0] < 0) {' '        if (false) {'
expect "a recipe set shaped like every real one hanging the planner" "stops answering rather than a refusal"

echo "how much goes out at once"
edit $R '        int fits = Math.max(1, STACK / perRun);' '        int fits = Integer.MAX_VALUE;'
expect "one dispatch emptying a warehouse into a machine" "what fits in a slot"

edit $R '        return (int) Math.min(remaining, fits);' '        return fits;'
expect "the last dispatch of a job making more than was asked for" "more than was asked for"

edit $R '    public static final int ASSEMBLE_TICKS = 40;' '    public static final int ASSEMBLE_TICKS = 4;'
expect "a machine that beats a player at their own bench" "the bench is pointless"

echo "what the hands cost"
edit $R '    public static final IndustryRules.Machine ASSEMBLY_CORE =
            new IndustryRules.Machine(256.0F, IndustryRules.Voltage.MEDIUM);' '    public static final IndustryRules.Machine ASSEMBLY_CORE =
            new IndustryRules.Machine(256.0F, IndustryRules.Voltage.LOW);'
expect "autocrafting available the moment a player has any grid" "the age it belongs to is decided by nothing"

edit $R '    public static final IndustryRules.Machine ASSEMBLER =
            new IndustryRules.Machine(96.0F, IndustryRules.Voltage.LOW);' '    public static final IndustryRules.Machine ASSEMBLER =
            new IndustryRules.Machine(96.0F, IndustryRules.Voltage.HIGH);'
expect "hands that cannot be added without rebuilding the wire" "rebuilding the wire"

edit $S/AssemblerBlock.java '            return working() ? LogisticRules.ASSEMBLER : null;' '            return LogisticRules.ASSEMBLER;'
expect "a hall of idle assemblers browning out the factory" "browns out the factory"

edit $S/AssemblerBlock.java '            return working() ? LogisticRules.ASSEMBLER : null;' '            return working()
                    ? new IndustryRules.Machine(96.0F * share(), IndustryRules.Voltage.LOW)
                    : null;'
expect "a machine whose draw depends on what the grid can give it" "never settles"

edit $S/AssemblyCoreBlock.java '            return queue.isEmpty() ? null : LogisticRules.ASSEMBLY_CORE;' '            return queue.isEmpty() ? null
                    : new IndustryRules.Machine(256.0F * share(), IndustryRules.Voltage.MEDIUM);'
expect "a core whose draw depends on what the grid can give it" "never settles"

echo "items, and never making or losing them"
edit $S/AssemblerBlock.java '            Containers.dropContents(level, pos, assembler.spill());' '            assembler.spill();'
expect "a mined assembler eating the batch it was given" "items would vanish"

edit $S/AssemblerBlock.java '                if (!consume()) {' '                if (false) {'
expect "an assembler producing without checking it had the ingredients" "duplication bug"

edit $S/AssemblerBlock.java '            done += IndustryRules.workPerTick(share());' '            done += 100;'
expect "an assembler that does not run on Age II's work model" "the same lesson twice"

edit $S/AssemblyCoreBlock.java '                if (available(warehouse, need.getKey()) < need.getValue()) {
                    return false;
                }' '                if (false) {
                    return false;
                }'
expect "a job one iron short stranding everything else it took" "cannot use it"

edit $S/AssemblyCoreBlock.java '            List<ItemStack> each = new ArrayList<>();
            for (Map.Entry<String, Integer> need : step.needs().entrySet()) {
                ItemStack one = stackOf(need.getKey(), need.getValue());
                if (one.isEmpty()) {
                    give(warehouse, taken);
                    return false;
                }
                each.add(one);
            }' '            List<ItemStack> each = new ArrayList<>();
            for (Map.Entry<String, Integer> need : step.needs().entrySet()) {
                ItemStack one = stackOf(need.getKey(), need.getValue());
                if (one.isEmpty()) {
                    return false;
                }
                each.add(one);
            }'
expect "a failed dispatch leaving the warehouse quietly emptier" "quietly emptier"

echo "the order, obeyed"
edit $S/AssemblyCoreBlock.java '                if (!hand(level, warehouse, assembler, step, batch)) {
                    // The ingredients are not there yet. Wait rather than
                    // skip: the order exists precisely so that what this job
                    // needs is being made by an earlier one.
                    return;
                }' '                if (!hand(level, warehouse, assembler, step, batch)) {
                    continue;
                }'
expect "a core that skips past a job whose ingredients are not ready" "skipping it means it never is"

edit $S/AssemblyCoreBlock.java '            if (!LogisticRules.worthStarting(worked, assemblers.size())) {' '            if (false) {'
expect "a request nobody could finish started anyway" "rather than a sentence"

edit $S/AssemblyCoreBlock.java '            if (!LogisticRules.worthStarting(worked, assemblers.size())) {
                return Component.translatable("elysiumlogistics.core.enormous",
                        String.valueOf(worked.totalRuns()),
                        String.valueOf(Math.max(1, assemblers.size())));
            }
            queue.clear();
            queue.addAll(worked.jobs());' '            queue.clear();
            queue.addAll(worked.jobs());
            if (!LogisticRules.worthStarting(worked, assemblers.size())) {
                return Component.translatable("elysiumlogistics.core.enormous",
                        String.valueOf(worked.totalRuns()),
                        String.valueOf(Math.max(1, assemblers.size())));
            }'
expect "a refused request cancelling the one that was running" "also cancels the one that was running"

echo "the vocabulary, and that it is the same vocabulary twice"
edit $S/CraftingCatalogue.java '        TreeMap<String, String> sorted = new TreeMap<>();' '        java.util.HashMap<String, String> sorted = new java.util.HashMap<>();'
expect "an ingredient standing for whichever item the game listed first" "with nothing changed"

edit $S/CraftingCatalogue.java '        return againstName == null || name.compareTo(againstName) < 0;' '        return againstName == null;'
expect "two recipes for one thing chosen between by luck" "whatever order the recipe manager"

edit $S/CraftingCatalogue.java 'if (needs == null || needs.isEmpty() || needs.containsKey(output)) {' 'if (needs == null || needs.isEmpty()) {'
expect "a recipe that consumes what it makes offered to the planner" "when there was another recipe to use"

edit $S/CraftingCatalogue.java '    private static String representative(Ingredient ingredient) {' '    private static String representativeOf(Ingredient ingredient) {'
expect "no single place where an ingredient is reduced to one item" "depends on the call site"

echo "the terminal, and the hands nobody may reach into"
edit $S/TerminalBlock.java '        List<BlockPos> found = LogisticNetwork.along(level, pos,' '        List<BlockPos> found = java.util.List.of(pos.above(), pos.below(),'
expect "a terminal that has to be next to its core" "no reason for it to be a separate block"

edit $M/src/main/java/com/elysium/logistics/registry/LogisticBlockEntities.java \
  '            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CONTROLLER.get(),
                    (entity, side) -> entity.asOneInventory());' \
  '            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CONTROLLER.get(),
                    (entity, side) -> entity.asOneInventory());
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ASSEMBLER.get(),
                    (entity, side) -> null);'
expect "a hopper able to reach into a machine halfway through a recipe" "halfway through it"

echo "the harness, which proves by execution what the rules above only state"
# The order comes out complete and backwards. Every statement the validator
# names is still there, so only the simulation can catch it — which is the
# whole reason there is a simulation.
edit $R '        return placed.size() == runs.size() ? List.copyOf(out) : List.of();' '        java.util.Collections.reverse(out);
        return placed.size() == runs.size() ? List.copyOf(out) : List.of();'
expect "a complete order, backwards" "which it is made of"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all logistics checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "autocrafting: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
