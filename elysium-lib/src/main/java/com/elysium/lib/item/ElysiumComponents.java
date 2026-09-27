package com.elysium.lib.item;

import com.elysium.lib.ElysiumLib;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom data components. This is the 1.20.5+ replacement for the loose NBT
 * tags the mod used to write directly onto item stacks.
 */
public final class ElysiumComponents {

    private ElysiumComponents() {
    }

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(ElysiumLib.MODID);

    /**
     * Which office a scanner reports its finds to.
     *
     * An integer rather than an enum codec, and deliberately tolerant: a stack
     * that has never been set, or one written by an older version, reads as
     * zero and therefore as the first filing option. The setting is a
     * preference, not a possession — losing it should cost a click, not throw.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>>
            FILING = COMPONENTS.registerComponentType("filing", builder -> builder
                    .persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(
                            net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /**
     * A rune item's tier. Absent on every rune ever dropped before tiers, which
     * reads as tier 1 — see {@link ElysiumRuneStacks#tierOf}. Part of the stack's
     * identity, so a tier-1 and a tier-2 rune never stack together.
     */
    /**
     * The dye a run item wears, by the game's dye name, so a dyed pipe in a
     * chest is the same pipe placed. See {@link com.elysium.lib.run.RunRules}.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>>
            RUN_DYE = COMPONENTS.registerComponentType("run_dye", builder -> builder
                    .persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(
                            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>>
            RUNE_TIER = COMPONENTS.registerComponentType("rune_tier", builder -> builder
                    .persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(
                            net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /**
     * The affixes rolled onto an item, and at what level.
     *
     * Part of the stack's identity: two swords that rolled differently are not
     * the same sword and must not stack together.
     */
    public static final DeferredHolder<DataComponentType<?>,
            DataComponentType<com.elysium.lib.affix.ElysiumItemAffixes>> AFFIXES =
            COMPONENTS.registerComponentType("affixes", builder -> builder
                    .persistent(com.elysium.lib.affix.ElysiumItemAffixes.CODEC)
                    .networkSynchronized(com.elysium.lib.affix.ElysiumItemAffixes.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ElysiumGearData>> GEAR_DATA =
            COMPONENTS.registerComponentType("gear_data", builder -> builder
                    .persistent(ElysiumGearData.CODEC)
                    .networkSynchronized(ElysiumGearData.STREAM_CODEC));

    // There was a second component here, trinket_data, holding a trinket's
    // ascension tier on its own. Its reasoning was that "a trinket has no
    // runes, no reforge rolls and no reforge budget", so sharing GEAR_DATA
    // would be a standing invitation to socket a rune into a ring.
    //
    // That premise is gone. A trinket is a piece of Elysium gear: it is
    // reforgeable and ascendable at the same table by the same rules as
    // armour, it implements ElysiumSocketable like everything else, and
    // socketing a rune into a ring is now a feature rather than an accident
    // waiting to happen. Two components for one idea would have meant two
    // places to read an ascension tier from and a real chance of reading the
    // wrong one.
    //
    // Deleting a registered component is normally a save-breaking act. It is
    // free here only because nothing ever wrote one: no trinket had shipped
    // when this was removed.
}
