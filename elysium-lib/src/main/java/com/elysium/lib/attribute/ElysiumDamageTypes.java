package com.elysium.lib.attribute;

import com.elysium.lib.ElysiumLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * The damage types the combat attributes deal (SPEC_CLONE P0.2). They are
 * JSON in {@code data/elysiumlib/damage_type/}. This class only holds their
 * keys and builds sources from them.
 *
 * <p>Tagging follows Apotheosis: bleeding, detonation, fire and cold bypass
 * armour; detonation, fire and cold are magic; current-health damage is
 * physical. {@link #NON_PHYSICAL} groups every kind of damage that is not a
 * weapon hit, so a resistance can name all of them at once.
 */
public final class ElysiumDamageTypes {

    private ElysiumDamageTypes() {
    }

    public static final ResourceKey<DamageType> BLEEDING = key("bleeding");
    public static final ResourceKey<DamageType> COLD = key("cold_damage");
    public static final ResourceKey<DamageType> CURRENT_HP = key("current_hp_damage");
    public static final ResourceKey<DamageType> DETONATION = key("detonation");
    public static final ResourceKey<DamageType> FIRE = key("fire_damage");

    public static final TagKey<DamageType> NON_PHYSICAL = TagKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, "is_non_physical"));

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, name));
    }

    /** A source of {@code type} caused by {@code attacker}, or unattributed when null. */
    public static DamageSource source(Level level, ResourceKey<DamageType> type, Entity attacker) {
        var holder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(type);
        return attacker == null ? new DamageSource(holder) : new DamageSource(holder, attacker);
    }
}
