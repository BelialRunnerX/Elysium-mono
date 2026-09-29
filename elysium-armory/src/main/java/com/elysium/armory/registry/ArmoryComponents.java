package com.elysium.armory.registry;

import com.elysium.armory.ElysiumArmory;
import com.elysium.armory.item.ArmoryGearParts;
import com.elysium.armory.item.ArmoryGearStats;
import com.elysium.armory.item.ArmoryGearTraits;
import com.elysium.armory.item.ArmoryMarks;
import com.elysium.armory.item.ArmoryTint;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The two components a piece of modular gear carries: what it is made of, and what that makes it. */
public final class ArmoryComponents {

    private ArmoryComponents() {
    }

    public static final DeferredRegister<DataComponentType<?>> TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ElysiumArmory.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ArmoryGearParts>> PARTS =
            TYPES.register("parts", () -> DataComponentType.<ArmoryGearParts>builder()
                    .persistent(ArmoryGearParts.CODEC)
                    .networkSynchronized(ArmoryGearParts.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ArmoryGearStats>> STATS =
            TYPES.register("stats", () -> DataComponentType.<ArmoryGearStats>builder()
                    .persistent(ArmoryGearStats.CODEC)
                    .networkSynchronized(ArmoryGearStats.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ArmoryTint>> TINT =
            TYPES.register("tint", () -> DataComponentType.<ArmoryTint>builder()
                    .persistent(ArmoryTint.CODEC)
                    .networkSynchronized(ArmoryTint.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ArmoryGearTraits>> TRAITS =
            TYPES.register("traits", () -> DataComponentType.<ArmoryGearTraits>builder()
                    .persistent(ArmoryGearTraits.CODEC)
                    .networkSynchronized(ArmoryGearTraits.STREAM_CODEC)
                    .build());

    /**
     * What has been done to a part beyond making it: its grade, its charge,
     * and the synergy its materials earned. Part of the stack's identity, so a
     * graded part never stacks with a plain one.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ArmoryMarks>> MARKS =
            TYPES.register("marks", () -> DataComponentType.<ArmoryMarks>builder()
                    .persistent(ArmoryMarks.CODEC)
                    .networkSynchronized(ArmoryMarks.STREAM_CODEC)
                    .build());

    /**
     * What a plan book has been taught.
     *
     * Its own component rather than reusing {@code parts}: a book holds a list
     * of plans and a piece of gear holds what it is made of, and one component
     * meaning two things is how a tooltip ends up reading "Sword Blade" on a
     * book.
     */
    public static final DeferredHolder<DataComponentType<?>,
            DataComponentType<com.elysium.armory.item.ArmoryPlanBook>> PLANS =
            TYPES.register("plans", () -> DataComponentType
                    .<com.elysium.armory.item.ArmoryPlanBook>builder()
                    .persistent(com.elysium.armory.item.ArmoryPlanBook.CODEC)
                    .networkSynchronized(
                            com.elysium.armory.item.ArmoryPlanBook.STREAM_CODEC)
                    .build());

    /**
     * What a reforge cost for the affixes a piece carries, in essence. Salvage
     * pays those affixes back one essence a level and never more than this,
     * so rerolling a cheap piece and taking it apart cannot print essence
     * ({@link com.elysium.armory.property.ArmorySalvageRules#essence(int, int)}).
     * Found affixes have no price, and a piece that carries them has no mark.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> REFORGED =
            TYPES.register("reforged", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
