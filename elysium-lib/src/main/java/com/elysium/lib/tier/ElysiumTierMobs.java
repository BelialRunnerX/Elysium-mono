package com.elysium.lib.tier;

import com.elysium.lib.ElysiumLib;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;

/**
 * The world's half of a tier: mobs that spawn where high-tier players are
 * standing come out harder.
 *
 * <h2>Every mob, not only this mod's</h2>
 *
 * Elysium's own mobs are scaled by nearby character level already. This is the
 * other thing, and it has to reach vanilla's mobs and other mods' too — a
 * world tier that only hardened the creatures from one mod would be a tier
 * that a player could ignore by fighting anything else.
 *
 * <h2>Once, at spawn</h2>
 *
 * Base values are multiplied rather than modifiers added, which cannot be
 * undone — so it is done exactly once, at the moment a mob is finalised, and a
 * flag on the mob says it has been. A mob that wandered into a deep world
 * keeps the tier it was born at, which is also the fair answer: it did not
 * become tougher for having walked somewhere.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumTierMobs {

    private ElysiumTierMobs() {
    }

    /** How far a mob looks for the players whose standing it is built against. */
    public static final double RANGE = 128.0D;

    /** Set once a mob has been hardened, so it never happens twice. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> HARDENED =
            ElysiumTiers.ATTACHMENTS.register("tier_hardened",
                    () -> AttachmentType.<Boolean>builder(() -> false)
                            .serialize(com.mojang.serialization.Codec.BOOL).build());

    @SubscribeEvent
    public static void onSpawn(FinalizeSpawnEvent event) {
        harden(event.getEntity());
    }

    /** Hardens a mob for the tier of whoever is nearby. Does nothing twice. */
    public static void harden(Mob mob) {
        if (mob.getData(HARDENED.get())) {
            return;
        }
        int tier = tierAround(mob.level(), mob);
        mob.setData(HARDENED.get(), true);
        if (tier <= ElysiumWorldTier.FRONTIER) {
            return;                        // the Frontier hardens nothing
        }
        // The promotion happens here rather than in its own listener because a
        // creature is only built once and this is that moment: two listeners
        // multiplying the same base values would double whatever they agreed
        // about and disagree about the order of everything else.
        com.elysium.lib.elite.ElysiumElites.promote(mob, tier);
        scale(mob, Attributes.MAX_HEALTH, ElysiumTierAugments.mobHealth(tier));
        scale(mob, Attributes.ATTACK_DAMAGE, ElysiumTierAugments.mobDamage(tier));
        scale(mob, Attributes.ARMOR, ElysiumTierAugments.mobArmour(tier));
        // Raising max health does not refill it, so a mob hardened after it was
        // built would arrive at a fraction of its own bar.
        mob.setHealth(mob.getMaxHealth());
    }

    /** The highest world tier standing within range, or the Frontier if nobody is. */
    public static int tierAround(Level level, LivingEntity where) {
        List<Player> nearby = level.getEntitiesOfClass(Player.class,
                new AABB(where.blockPosition()).inflate(RANGE));
        return ElysiumTiers.of(nearby);
    }

    private static void scale(LivingEntity mob, Holder<Attribute> attribute, double multiplier) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance == null) {
            // Not every mob has every attribute. One with no attack damage does
            // not hit things, and giving it some would be a surprise.
            return;
        }
        instance.setBaseValue(instance.getBaseValue() * multiplier);
    }
}
