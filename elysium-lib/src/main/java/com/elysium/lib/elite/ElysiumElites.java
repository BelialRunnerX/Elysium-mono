package com.elysium.lib.elite;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.tier.ElysiumTierMobs;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;

/**
 * Promoting a creature at the moment it spawns.
 *
 * <h2>Once, and only at spawn</h2>
 *
 * Augments multiply base attribute values, which cannot be undone, so this
 * happens exactly once and a flag on the creature records that it has. It runs
 * beside the world tier's own hardening and for the same reason: a mob is only
 * ever built once, and the moment it is built is the only safe place to change
 * what it is made of.
 *
 * <h2>Names</h2>
 *
 * An elite is named after what is wrong with it — "Fleet Zombie" rather than
 * "Elite Zombie" — because the augment is what the player has to fight and the
 * rank is only how many of them there are. An invader is named as well as
 * marked, and shows its name above its head, because a creature that can
 * casually end a run should not have to be inspected to be recognised.
 */
public final class ElysiumElites {

    private ElysiumElites() {
    }

    /** What a creature was promoted to, kept so nothing promotes it twice. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> RANK =
            com.elysium.lib.tier.ElysiumTiers.ATTACHMENTS.register("elite_rank",
                    () -> AttachmentType.<String>builder(() -> "")
                            .serialize(com.mojang.serialization.Codec.STRING).build());

    /** The rank this creature was promoted to, or ordinary. */
    public static ElysiumEliteRules.Rank rankOf(Mob mob) {
        String name = mob.getData(RANK.get());
        if (name.isEmpty()) {
            return ElysiumEliteRules.Rank.ORDINARY;
        }
        try {
            return ElysiumEliteRules.Rank.valueOf(name);
        } catch (IllegalArgumentException e) {
            return ElysiumEliteRules.Rank.ORDINARY;
        }
    }

    /** Whether this creature has already been through here. */
    public static boolean decided(Mob mob) {
        return !mob.getData(RANK.get()).isEmpty();
    }

    /**
     * Rolls a rank for a creature and dresses it accordingly.
     *
     * Called from the world tier's own hardening, which already runs once at
     * spawn and already knows the tier.
     */
    public static void promote(Mob mob, int worldTier) {
        if (decided(mob)) {
            return;
        }
        ElysiumEliteRules.Rank rank = ElysiumEliteRules.roll(worldTier,
                mob.getRandom()::nextDouble);
        if (rank != ElysiumEliteRules.Rank.ORDINARY
                && !com.elysium.lib.api.ElysiumEvents.firePromote(mob, worldTier,
                        rank.name())) {
            // Somebody said no. Marked ordinary and decided, so the same
            // creature is not asked about again on the next spawn attempt.
            rank = ElysiumEliteRules.Rank.ORDINARY;
        }
        mob.setData(RANK.get(), rank.name());
        if (rank == ElysiumEliteRules.Rank.ORDINARY) {
            return;
        }
        List<ElysiumMobAugment> augments = ElysiumMobAugments.get().draw(
                ElysiumEliteRules.augments(rank, worldTier), worldTier, mob.getRandom());
        if (augments.isEmpty()) {
            // A pack with no augments produces no elites, which is better than
            // a creature promoted to nothing in particular.
            mob.setData(RANK.get(), ElysiumEliteRules.Rank.ORDINARY.name());
            return;
        }
        for (ElysiumMobAugment augment : augments) {
            apply(mob, augment);
        }
        name(mob, rank, augments);
        if (rank == ElysiumEliteRules.Rank.INVADER) {
            mob.setCustomNameVisible(true);
        }
        mob.setHealth(mob.getMaxHealth());
        ElysiumLib.LOGGER.debug("promoted {} to {} with {} augment(s)",
                mob.getType().getDescriptionId(), rank, augments.size());
    }

    private static void apply(Mob mob, ElysiumMobAugment augment) {
        for (ElysiumMobAugment.Attribute wanted : augment.attributes()) {
            Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE
                    .getHolder(ResourceKey.create(Registries.ATTRIBUTE,
                            ResourceLocation.parse(wanted.attribute()))).orElse(null);
            if (attribute == null) {
                continue;                   // an attribute from a mod that is not here
            }
            AttributeInstance instance = mob.getAttribute(attribute);
            if (instance == null) {
                continue;                   // not every creature has every attribute
            }
            instance.setBaseValue(instance.getBaseValue() * wanted.factor());
        }
        for (String id : augment.effects()) {
            Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT
                    .getHolder(ResourceKey.create(Registries.MOB_EFFECT,
                            ResourceLocation.parse(id))).orElse(null);
            if (effect != null) {
                // For as long as it lives: an augment that wore off mid-fight
                // would be a creature that stops being what it is.
                mob.addEffect(new MobEffectInstance(effect, -1, 0, false, false, false));
            }
        }
    }

    /** "Fleet Zombie": what is wrong with it, then what it is. */
    private static void name(Mob mob, ElysiumEliteRules.Rank rank,
                             List<ElysiumMobAugment> augments) {
        MutableComponent named = Component.empty();
        if (rank == ElysiumEliteRules.Rank.INVADER) {
            named.append(Component.translatable(ElysiumEliteRules.nameKey(rank))).append(" ");
        }
        named.append(Component.translatable(augments.get(0).nameKey())).append(" ");
        named.append(mob.getType().getDescription());
        mob.setCustomName(named);
    }

    /**
     * Makes this creature an invader outright, whatever the roll would have
     * said.
     *
     * For the one case where the world has already decided: something a player
     * deliberately let out. A cage that produced an ordinary zombie because
     * its finder was early in the game would be a disappointment rather than a
     * decision.
     */
    public static void release(Mob mob, int worldTier) {
        if (decided(mob)) {
            return;
        }
        ElysiumEliteRules.Rank rank = ElysiumEliteRules.Rank.INVADER;
        mob.setData(RANK.get(), rank.name());
        List<ElysiumMobAugment> augments = ElysiumMobAugments.get().draw(
                ElysiumEliteRules.augments(rank, worldTier), worldTier, mob.getRandom());
        if (augments.isEmpty()) {
            mob.setData(RANK.get(), ElysiumEliteRules.Rank.ORDINARY.name());
            return;
        }
        for (ElysiumMobAugment augment : augments) {
            apply(mob, augment);
        }
        name(mob, rank, augments);
        mob.setCustomNameVisible(true);
        mob.setHealth(mob.getMaxHealth());
    }

    /** What a kill on this creature is worth, as a multiple. */
    public static double lootMultiplier(Mob mob) {
        return ElysiumEliteRules.lootMultiplier(rankOf(mob));
    }

    /** Kept so the tier hardening and this stay in step about when a mob is built. */
    public static boolean hardened(Mob mob) {
        return mob.getData(ElysiumTierMobs.HARDENED.get());
    }
}
