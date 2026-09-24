package com.elysium.comforts.registry;

import com.elysium.comforts.ElysiumComforts;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * What each player has left in a container they have already opened.
 *
 * <h2>Why this is the only thing kept</h2>
 *
 * A container's roll is a <em>function</em> of the container and the player,
 * so there is nothing to remember about who has opened what: a player who
 * comes back in a year gets exactly what they left, with no record of them
 * anywhere. What cannot be derived is what they have already <b>taken</b>, and
 * that is what this holds — and it lets go of a player the moment their copy
 * is empty, so the one table that does exist empties itself.
 */
public final class ComfortAttachments {

    private ComfortAttachments() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
                    ElysiumComforts.MODID);

    /**
     * On a container: player id to what is left of their copy.
     *
     * <p>A compound of lists rather than anything cleverer, because it is
     * written by the game's own item codec and read by nothing but this
     * module, and a bespoke format here would be one more thing to get wrong
     * on a world that has been through an update.
     */
    public static final Supplier<AttachmentType<CompoundTag>> SHARES =
            ATTACHMENTS.register("shares", () -> AttachmentType
                    .<CompoundTag>builder(() -> new CompoundTag())
                    .serialize(CompoundTag.CODEC)
                    .build());
}
