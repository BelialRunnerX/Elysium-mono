package com.elysium.lib.medical;

import com.elysium.lib.ElysiumLib;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Where a player's wounds live, and what they do to the player.
 *
 * <h2>Not copied on death</h2>
 *
 * Every other standing attachment in the library survives death, and this one
 * deliberately does not. A wound that outlived the player would be the start
 * of a death spiral — respawn weakened, die sooner, respawn weaker — and death
 * already costs a player what death costs. Coming back whole is the one mercy
 * the respawn screen is for.
 *
 * <h2>Transient modifiers, re-applied</h2>
 *
 * The penalties are transient attribute modifiers, recomputed from the
 * attachment rather than saved alongside it. So there is one record of a wound
 * — the attachment — and the modifier can never disagree with it: a stale
 * modifier from an old save, a wound healed while offline, a kind removed in a
 * later version all resolve on the next recovery step.
 */
public final class ElysiumMedical {

    private ElysiumMedical() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ElysiumLib.MODID);

    public static final Supplier<AttachmentType<ElysiumInjury>> INJURY =
            ATTACHMENTS.register("injury", () -> AttachmentType
                    .<ElysiumInjury>builder(() -> ElysiumInjury.NONE)
                    .serialize(ElysiumInjury.CODEC)
                    .build());

    public static ElysiumInjury of(Player player) {
        return player.getData(INJURY.get());
    }

    public static void set(Player player, ElysiumInjury injury) {
        player.setData(INJURY.get(), injury);
        apply(player, injury);
    }

    /** The attribute each kind takes from. The one place that knows. */
    public static Holder<Attribute> attributeOf(ElysiumWoundKind kind) {
        return switch (kind) {
            case LACERATION -> Attributes.MAX_HEALTH;
            case FRACTURE -> Attributes.MOVEMENT_SPEED;
            case BURN -> Attributes.BLOCK_BREAK_SPEED;
            case CONCUSSION -> Attributes.ATTACK_SPEED;
        };
    }

    /**
     * How each kind's penalty is applied.
     *
     * Health is flat — two points a wound is a heart a wound, which a player
     * can count on the bar. The rest are proportions of the base value, so a
     * fracture slows a sprinting player and a crouching one by the same share.
     */
    public static AttributeModifier.Operation operationOf(ElysiumWoundKind kind) {
        return kind == ElysiumWoundKind.LACERATION
                ? AttributeModifier.Operation.ADD_VALUE
                : AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
    }

    private static ResourceLocation modifierId(ElysiumWoundKind kind) {
        return ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID,
                "wound/" + kind.id());
    }

    /** Makes the player's attributes agree with their injury. Idempotent. */
    public static void apply(Player player, ElysiumInjury injury) {
        for (ElysiumWoundKind kind : ElysiumWoundKind.values()) {
            AttributeInstance attribute = player.getAttribute(attributeOf(kind));
            if (attribute == null) {
                continue;
            }
            ResourceLocation id = modifierId(kind);
            double penalty = ElysiumMedicine.penaltyFor(kind, injury.count(kind));
            if (penalty == 0.0D) {
                attribute.removeModifier(id);
            } else {
                attribute.addOrUpdateTransientModifier(
                        new AttributeModifier(id, penalty, operationOf(kind)));
            }
        }
        // A laceration can lower the maximum below the health a player has;
        // clamp so the bar never shows more than it holds.
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }
}
