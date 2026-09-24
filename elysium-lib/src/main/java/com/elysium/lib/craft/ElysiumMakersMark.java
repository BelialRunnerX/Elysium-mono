package com.elysium.lib.craft;

import com.elysium.lib.ElysiumLib;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Who made this, and how well, written on the thing itself.
 *
 * <h2>Why it lives on the stack and not in a ledger</h2>
 *
 * Because the interesting case is the one where the maker is not there. A sword
 * found in a chest four thousand blocks from anywhere should be able to say
 * whose it was; a ledger somewhere would need the maker's world, their login,
 * and a lookup that fails in exactly that situation. The name is stored
 * alongside the id for the same reason — a player who has never met the maker
 * and never will can still read the mark.
 *
 * <h2>Why a data component</h2>
 *
 * It travels with the item through every hopper, chest, trade and death that
 * exists, survives being renamed, and is the only representation the game will
 * keep in sync between server and client for free. NBT on the stack would have
 * done the same job in 1.20 and is not how 1.21 works.
 */
public record ElysiumMakersMark(UUID maker, String name, String quality, long made) {

    public static final Codec<ElysiumMakersMark> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    UUIDUtil.CODEC.fieldOf("maker").forGetter(ElysiumMakersMark::maker),
                    Codec.STRING.fieldOf("name").forGetter(ElysiumMakersMark::name),
                    Codec.STRING.fieldOf("quality").forGetter(ElysiumMakersMark::quality),
                    Codec.LONG.fieldOf("made").forGetter(ElysiumMakersMark::made)
            ).apply(instance, ElysiumMakersMark::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ElysiumMakersMark>
            STREAM_CODEC = StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, ElysiumMakersMark::maker,
                    ByteBufCodecs.STRING_UTF8, ElysiumMakersMark::name,
                    ByteBufCodecs.STRING_UTF8, ElysiumMakersMark::quality,
                    ByteBufCodecs.VAR_LONG, ElysiumMakersMark::made,
                    ElysiumMakersMark::new);

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ElysiumLib.MODID);

    public static final Supplier<DataComponentType<ElysiumMakersMark>> MARK =
            COMPONENTS.register("makers_mark", () ->
                    DataComponentType.<ElysiumMakersMark>builder()
                            .persistent(CODEC)
                            .networkSynchronized(STREAM_CODEC)
                            .build());

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }

    /** The grade, or null for one this build no longer has. */
    public ElysiumQuality grade() {
        return ElysiumQuality.byId(quality);
    }

    /** The mark on a stack, or null if it carries none. */
    public static ElysiumMakersMark of(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : stack.get(MARK.get());
    }

    /**
     * Stamp a stack.
     *
     * Also sets the stack's maximum damage, because a grade that did not change
     * anything would be a label. Only for things that have durability at all —
     * an ingot with a maximum damage of zero is not a badly-made ingot, it is
     * an ingot, and giving it one would make it a broken tool.
     */
    public static void stamp(ItemStack stack, UUID maker, String name,
                             ElysiumQuality grade, long when) {
        if (stack == null || stack.isEmpty() || grade == null) {
            return;
        }
        stack.set(MARK.get(),
                new ElysiumMakersMark(maker, name, grade.id(), when));
        if (stack.isDamageableItem()) {
            stack.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE,
                    grade.durabilityFor(stack.getMaxDamage()));
        }
    }
}
