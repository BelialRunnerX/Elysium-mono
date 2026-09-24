package com.elysium.agronomy.registry;

import com.elysium.agronomy.ElysiumAgronomy;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Every different thing a player has eaten.
 *
 * <h2>Why a set and not a count</h2>
 *
 * The whole mechanic is <em>variety</em>: thirty different foods, not thirty
 * meals. A counter would reward standing in a wheat field, which is the exact
 * behaviour the bonus exists to discourage. So the ids are kept, the size of
 * the set is the number the rules take, and eating a second loaf of bread
 * changes nothing.
 *
 * <h2>And why it survives death</h2>
 *
 * Because having eaten something is a fact about your life, not a possession.
 * Losing ten hearts' worth of it to a creeper would make the mechanic a
 * punishment for dying rather than a reward for exploring, and the player
 * would stop eating anything new in dangerous places, which is backwards.
 */
public final class DietAttachments {

    private DietAttachments() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
                    ElysiumAgronomy.MODID);

    /** Every different food this player has eaten, oldest first. */
    public static final Supplier<AttachmentType<List<String>>> EATEN =
            ATTACHMENTS.register("eaten", () -> AttachmentType
                    .<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf())
                    .sync(StreamCodec.of(
                            (buffer, value) -> ByteBufCodecs.STRING_UTF8
                                    .apply(ByteBufCodecs.list())
                                    .encode((RegistryFriendlyByteBuf) buffer, value),
                            buffer -> ByteBufCodecs.STRING_UTF8
                                    .apply(ByteBufCodecs.list())
                                    .decode((RegistryFriendlyByteBuf) buffer)))
                    .copyOnDeath()
                    .build());

    /** Everything this player has eaten. */
    public static List<String> eaten(Player player) {
        return List.copyOf(player.getData(EATEN.get()));
    }

    /** How many different things they have eaten. */
    public static int variety(Player player) {
        return eaten(player).size();
    }

    /**
     * Record one more food. Returns whether it was a new one.
     *
     * <p>A set behind a list, so the order a player found things in is kept
     * for the book to show and the duplicate still counts for nothing.
     */
    public static boolean ate(Player player, String food) {
        Set<String> had = new LinkedHashSet<>(player.getData(EATEN.get()));
        if (!had.add(food)) {
            return false;
        }
        player.setData(EATEN.get(), List.copyOf(new ArrayList<>(had)));
        return true;
    }

    /** Whether they have eaten this one. */
    public static boolean hasEaten(Player player, String food) {
        return player.getData(EATEN.get()).contains(food);
    }
}
