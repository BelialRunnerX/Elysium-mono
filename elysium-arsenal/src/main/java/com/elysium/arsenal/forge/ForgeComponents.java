package com.elysium.arsenal.forge;

import com.elysium.arsenal.ElysiumArsenal;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.mojang.serialization.Codec;

/**
 * Which grade a piece of gear rolled, kept on the gear.
 *
 * <p>A string rather than an index. An index is smaller and is also a number
 * that means something different the moment a grade is inserted into the
 * middle of the roster — every sword in every world silently becoming a
 * different grade, with nothing anywhere to say so.
 */
public final class ForgeComponents {

    private ForgeComponents() {
    }

    public static final DeferredRegister<DataComponentType<?>> TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE,
                    ElysiumArsenal.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>>
            GRADE = TYPES.register("grade", () ->
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /**
     * Which artefact has been set into a piece of gear, and how deeply.
     *
     * <p>A component rather than an enchantment. An artefact that were an
     * enchantment would compete with enchanting for the same table, the same
     * levels and the same anvil cap — and the whole point of digging one up is
     * that it is a second way of improving gear, reached by doing something
     * else entirely.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>>
            ARTIFACT = TYPES.register("artifact", () ->
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>>
            ARTIFACT_LEVEL = TYPES.register("artifact_level", () ->
            DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** Which artefact is in this stack, or null. */
    public static String artifactIn(ItemStack stack) {
        return stack.get(ARTIFACT.get());
    }

    /** How deeply, or nothing. */
    public static int artifactLevel(ItemStack stack) {
        Integer level = stack.get(ARTIFACT_LEVEL.get());
        return level == null ? 0 : level;
    }

    /** Set an artefact into a stack at a level. */
    public static void setArtifact(ItemStack stack, String name, int level) {
        stack.set(ARTIFACT.get(), name);
        stack.set(ARTIFACT_LEVEL.get(), level);
    }

    /** The grade on this stack, or null if it has never been graded. */
    public static String gradeOn(ItemStack stack) {
        return stack.get(GRADE.get());
    }

    /** Put a grade on a stack. */
    public static void setGrade(ItemStack stack, String grade) {
        stack.set(GRADE.get(), grade);
    }
}
