package com.elysium.agronomy.registry;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.rules.BeeRules;
import com.elysium.agronomy.rules.FishRules;
import com.elysium.agronomy.rules.TackleRules;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * What a rod has fitted, and which bee a jar holds.
 *
 * <p>Strings rather than indices, and the rules' own names: a hook is kept as
 * {@code "iron_hook"} because that is what {@link TackleRules} calls it, and
 * a jar holds {@code "meadow"} because that is what {@link BeeRules} calls
 * the bee. An index would mean something different the day a hook is added
 * in the middle of the list, and every rod in every world would quietly be
 * fitted with the wrong one.
 */
public final class AgronomyComponents {

    private AgronomyComponents() {
    }

    public static final DeferredRegister<DataComponentType<?>> TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ElysiumAgronomy.MODID);

    private static DeferredHolder<DataComponentType<?>, DataComponentType<String>> word(
            String name) {
        return TYPES.register(name, () -> DataComponentType.<String>builder()
                .persistent(Codec.STRING)
                .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                .build());
    }

    /** The hook on a rod, by the rules' name for it. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>>
            HOOK = word("hook");

    /** The bobber on a rod. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>>
            BOBBER = word("bobber");

    /** The family of bait on a rod. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>>
            BAIT = word("bait");

    /** How much of it. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>>
            BAIT_COUNT = TYPES.register("bait_count", () -> DataComponentType.<Integer>builder()
            .persistent(Codec.INT)
            .networkSynchronized(ByteBufCodecs.VAR_INT)
            .build());

    /** Which bee a jar, or a bee item in a hive, is. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>>
            SPECIES = word("species");

    // ------------------------------------------------------------------
    // Reading them back
    // ------------------------------------------------------------------

    /** The path of one of our items, or empty for anything else. */
    public static String ourId(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        ResourceLocation where = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return where.getNamespace().equals(ElysiumAgronomy.MODID) ? where.getPath() : "";
    }

    /** The hook fitted to a rod, or the bare end of the line. */
    public static TackleRules.Hook hookOn(ItemStack rod) {
        String id = rod.get(HOOK.get());
        if (id != null) {
            for (TackleRules.Hook one : TackleRules.HOOKS) {
                if (one.id().equals(id)) {
                    return one;
                }
            }
        }
        return TackleRules.BARE;
    }

    /** The bobber fitted to a rod, or none. */
    public static TackleRules.Bobber bobberOn(ItemStack rod) {
        String id = rod.get(BOBBER.get());
        if (id != null) {
            for (TackleRules.Bobber one : TackleRules.BOBBERS) {
                if (one.id().equals(id)) {
                    return one;
                }
            }
        }
        return TackleRules.PLAIN;
    }

    /** Everything fitted to a rod, as the rules see it. */
    public static TackleRules.Tackle tackleOn(ItemStack rod) {
        return new TackleRules.Tackle(hookOn(rod), bobberOn(rod));
    }

    /** The family of bait on a rod, or {@link FishRules#NOTHING}. */
    public static String baitOn(ItemStack rod) {
        String family = rod.get(BAIT.get());
        return family != null && baitCount(rod) > 0 ? family : FishRules.NOTHING;
    }

    /** How much bait is on a rod. */
    public static int baitCount(ItemStack rod) {
        Integer count = rod.get(BAIT_COUNT.get());
        return count == null ? 0 : Math.max(0, count);
    }

    /** Which bee is in a jar or a bee item, or null for an empty one. */
    public static String speciesIn(ItemStack stack) {
        String species = stack.get(SPECIES.get());
        return species != null && BeeRules.bee(species) != null ? species : null;
    }
}
