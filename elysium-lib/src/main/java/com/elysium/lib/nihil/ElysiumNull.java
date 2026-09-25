package com.elysium.lib.nihil;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.attribute.ElysiumAttributeValues;
import com.elysium.lib.element.ElysiumElements;
import com.elysium.lib.hazard.ElysiumHazard;
import com.elysium.lib.hazard.ElysiumWards;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * The Null field: a sixth hazard, the gauge it fills, and the ward against it.
 *
 * <p>Registered here rather than in {@code ElysiumHazards}, which is the
 * specification's five and is checked to stay five, one per element. The Null
 * field shares the Void element with radiation on purpose: both are the same
 * armour's answer, and a player who has sealed one has sealed the other. Its
 * own damage per second is zero, so the hazard tick never hurts for it
 * directly — everything it does goes through the gauge, which is what makes it
 * a field rather than a fire.
 *
 * <p>Nothing in the library sources the field. A world that has one registers
 * an {@code ElysiumHazardField.Source} that reports its intensity for
 * {@link #NULL}, exactly as elysium-space does for vacuum, and from there
 * wards, gear, rooms and claims answer it without a line of new code.
 *
 * <p>Four attributes feed it: {@code null_capacity} is the top of the gauge,
 * {@code null_resilience} the seconds between accrual ticks,
 * {@code null_resistance} a share stopped before protection is counted, and
 * {@code null_damage} what an attacker's blows add to the gauge. The arithmetic
 * is {@link ElysiumNullRules}.
 */
public final class ElysiumNull {

    private ElysiumNull() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, path);
    }

    /** The field. Zero damage of its own: the gauge is the damage. */
    public static final ElysiumHazard NULL = ElysiumHazard.register(id("null"), ElysiumElements.VOID, 0.0F);

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ElysiumLib.MODID);

    /** How saturated a player is, in points against their capacity. Not kept across death: a collapse is the end of it. */
    public static final Supplier<AttachmentType<Float>> SATURATION =
            ATTACHMENTS.register("null_saturation", () -> AttachmentType.<Float>builder(() -> 0.0F)
                    .serialize(Codec.FLOAT).build());

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, ElysiumLib.MODID);

    /**
     * The ward, Valoria's Nihility Protection: the field cannot accrue while it
     * runs, and it turns aside a share of every blow that leaks back into the
     * gauge. Bound to the hazard at registration so the two cannot drift.
     */
    public static final DeferredHolder<MobEffect, MobEffect> WARD = EFFECTS.register("null_ward", NullWard::new);

    static {
        ElysiumWards.bind(NULL, WARD);
    }

    /** What happens at the top. A world module may switch this to the Nullspire's extraction. */
    private static volatile ElysiumNullRules.Failsafe failsafe = ElysiumNullRules.Failsafe.COLLAPSE;

    public static ElysiumNullRules.Failsafe failsafe() {
        return failsafe;
    }

    public static void setFailsafe(ElysiumNullRules.Failsafe chosen) {
        failsafe = chosen == null ? ElysiumNullRules.Failsafe.COLLAPSE : chosen;
    }

    /** The top of a player's gauge, from the null_capacity attribute. */
    public static float capacity(LivingEntity entity) {
        double stored = ElysiumAttributeValues.flat(entity, "null_capacity");
        return stored <= 0.0D ? ElysiumNullRules.DEFAULT_CAPACITY : (float) stored;
    }

    public static float saturation(Player player) {
        return player.getData(SATURATION);
    }

    public static float ratio(Player player) {
        return ElysiumNullRules.ratio(saturation(player), capacity(player));
    }

    public static void set(Player player, float value) {
        player.setData(SATURATION, ElysiumNullRules.after(0.0F, value, capacity(player)));
    }

    /** Moves the gauge, held inside zero and the player's capacity; the new value. */
    public static float add(Player player, float delta) {
        float after = ElysiumNullRules.after(saturation(player), delta, capacity(player));
        player.setData(SATURATION, after);
        return after;
    }

    /** Call from the library's constructor. */
    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        EFFECTS.register(modEventBus);
    }

    /** Touching the class registers the hazard and binds the ward. */
    public static void bootstrap() {
        // The static initialisers above are the registration.
    }

    /**
     * BENEFICIAL, so milk and every "cure" leaves it alone, and the Void
     * element's colour so the icon says which field it answers. It carries no
     * modifier and no tick: the accrual tick reads it as a ward, and the damage
     * handler reads its level for the shield.
     */
    private static final class NullWard extends MobEffect {
        private NullWard() {
            super(MobEffectCategory.BENEFICIAL, 0x733D9C);
        }
    }
}
