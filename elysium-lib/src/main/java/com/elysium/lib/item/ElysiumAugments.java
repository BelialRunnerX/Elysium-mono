package com.elysium.lib.item;

import com.elysium.lib.affix.ElysiumAffixCatalogue;
import com.elysium.lib.affix.ElysiumAffixDefinition;
import com.elysium.lib.affix.ElysiumAffixEffects;
import com.elysium.lib.affix.ElysiumAffixRoll;
import com.elysium.lib.affix.ElysiumItemAffixes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What a sigil does to a piece (Apotheosis' AP-17 and AP-18).
 *
 * <h2>Four, and each undoes a different regret</h2>
 *
 * A sigil is a single use spent on a piece a player has already decided to
 * keep. Each one exists because of a specific way a good piece can be wrong:
 *
 * <ul>
 *   <li><b>Socketing</b> — the piece is right and has nowhere to put a rune.</li>
 *   <li><b>Enhancement</b> — the affixes are right and one of them rolled low.</li>
 *   <li><b>Withdrawal</b> — the rune in it was the wrong rune, and destroying
 *       it to correct that would punish a decision the player has already
 *       paid for.</li>
 *   <li><b>Rebirth</b> — the levels are right and the affixes are not, so the
 *       names change and the strength does not.</li>
 * </ul>
 *
 * None of them adds power the game could not otherwise produce: socketing adds
 * a slot a higher tier would have given, enhancement one level a better roll
 * would have given, rebirth a different set the same roll could have produced.
 * What a sigil buys is aim.
 */
public final class ElysiumAugments {

    private ElysiumAugments() {
    }

    public static final String SOCKETING = "socketing";
    public static final String ENHANCEMENT = "enhancement";
    public static final String WITHDRAWAL = "withdrawal";
    public static final String REBIRTH = "rebirth";

    /** Every sigil, in the order a bench would list them. */
    public static final List<String> ALL = List.of(SOCKETING, ENHANCEMENT, WITHDRAWAL, REBIRTH);

    /**
     * What a sigil did, and what came back out with the piece.
     *
     * A name and a tier rather than an item: the rune <em>items</em> are
     * registered by whichever module ships them, and the library that knows
     * what a rune is has never heard of them. The bench turns this into
     * something the player can hold.
     */
    public record Result(boolean changed, String rune, int runeTier) {

        public static final Result NOTHING = new Result(false, "", 0);

        /** A sigil that changed the piece and gave nothing back. */
        public static Result onlyChanged() {
            return new Result(true, "", 0);
        }

        /** Whether something came back out as well as the piece. */
        public boolean hasRune() {
            return !rune.isEmpty();
        }
    }

    /** Whether this sigil would do anything at all to this piece. */
    public static boolean applies(String sigil, ItemStack stack, int gearTier) {
        return switch (sigil) {
            case SOCKETING -> ElysiumGear.is(stack);
            case ENHANCEMENT -> !raisable(stack).isEmpty();
            case WITHDRAWAL -> !ElysiumSockets.gearData(stack).runes().isEmpty();
            case REBIRTH -> !ElysiumAffixEffects.of(stack).isEmpty();
            default -> false;
        };
    }

    /**
     * Applies a sigil to a piece, in place.
     *
     * @return what came back out, and whether anything happened at all — a
     *         bench must not spend a sigil on a piece it would not change
     */
    public static Result apply(String sigil, ItemStack stack, int worldTier, RandomSource random) {
        return switch (sigil) {
            case SOCKETING -> socket(stack);
            case ENHANCEMENT -> enhance(stack, random);
            case WITHDRAWAL -> withdraw(stack);
            case REBIRTH -> rebirth(stack, worldTier, random);
            default -> Result.NOTHING;
        };
    }

    private static Result socket(ItemStack stack) {
        if (!ElysiumGear.is(stack)) {
            return Result.NOTHING;
        }
        ElysiumSockets.setGearData(stack, ElysiumSockets.gearData(stack).withExtraSocket());
        return Result.onlyChanged();
    }

    /** The affixes on a piece that are below what they were written for. */
    private static List<String> raisable(ItemStack stack) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : ElysiumAffixEffects.of(stack).levels().entrySet()) {
            ElysiumAffixDefinition affix = ElysiumAffixCatalogue.get().byId(entry.getKey());
            if (affix != null && entry.getValue() < affix.maxLevel()) {
                out.add(entry.getKey());
            }
        }
        return out;
    }

    private static Result enhance(ItemStack stack, RandomSource random) {
        List<String> candidates = raisable(stack);
        if (candidates.isEmpty()) {
            return Result.NOTHING;
        }
        String chosen = candidates.get(random.nextInt(candidates.size()));
        ElysiumItemAffixes affixes = ElysiumAffixEffects.of(stack);
        stack.set(ElysiumComponents.AFFIXES.get(), affixes.with(chosen, affixes.level(chosen) + 1));
        return Result.onlyChanged();
    }

    private static Result withdraw(ItemStack stack) {
        ElysiumGearData data = ElysiumSockets.gearData(stack);
        if (data.runes().isEmpty()) {
            return Result.NOTHING;
        }
        int last = data.runes().size() - 1;
        String name = data.runes().get(last);
        int tier = data.tierAt(last);
        ElysiumSockets.setGearData(stack, data.withRuneRemoved(last));
        return new Result(true, name, tier);
    }

    private static Result rebirth(ItemStack stack, int worldTier, RandomSource random) {
        ElysiumItemAffixes affixes = ElysiumAffixEffects.of(stack);
        if (affixes.isEmpty()) {
            return Result.NOTHING;
        }
        // The same number of affixes at the same levels, drawn again. The
        // strength a player earned is theirs; only the names change.
        List<Integer> levels = new ArrayList<>(affixes.levels().values());
        String kind = ElysiumLootCategory.of(stack);
        ElysiumAffixRoll.clear(stack);
        ElysiumItemAffixes rolled = ElysiumItemAffixes.NONE;
        String[] order = {com.elysium.lib.affix.ElysiumAffixRules.PREFIX,
                com.elysium.lib.affix.ElysiumAffixRules.SUFFIX};
        for (int index = 0; index < levels.size(); index++) {
            rolled = ElysiumAffixRoll.draw(rolled, kind, order[index % order.length],
                    worldTier, random);
        }
        // Put the old levels back onto whatever came up, in order.
        ElysiumItemAffixes levelled = ElysiumItemAffixes.NONE;
        int index = 0;
        for (String id : rolled.levels().keySet()) {
            levelled = levelled.with(id, levels.get(Math.min(index++, levels.size() - 1)));
        }
        if (levelled.isEmpty()) {
            return Result.NOTHING;
        }
        stack.set(ElysiumComponents.AFFIXES.get(), levelled);
        return Result.onlyChanged();
    }
}
