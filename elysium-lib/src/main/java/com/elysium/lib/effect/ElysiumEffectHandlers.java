package com.elysium.lib.effect;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The item effects that happen while gear is used: what it does to what it
 * hits, what it does for whoever is wearing it, and what it does to the blocks
 * and drops around it.
 *
 * <h2>Written once, for everything</h2>
 *
 * These were the trait handlers, and traits were the only thing that could
 * reach them. They read {@link ElysiumItemEffects} now, which gathers effects
 * from every attachment a stack has — the traits it was built with, the
 * affixes rolled onto it, the gems set into it, the runes socketed in it — so
 * a bleed from an affix is the same bleed as a bleed from a trait, down to the
 * duration, and nothing had to be written twice to make that so.
 *
 * <p>Every effect is read from its own data file, so a datapack can change what
 * Sharp is worth without touching a class. The event handlers decide
 * <em>when</em>; this decides <em>what</em>.
 */
public final class ElysiumEffectHandlers {

    private ElysiumEffectHandlers() {
    }

    /** Extra damage against the kind of thing a trait is written for (SG-61's extra_damage). */
    public static float extraDamage(ItemStack weapon, LivingEntity target) {
        float bonus = 0.0F;
        for (ElysiumItemEffects.Active active : ElysiumItemEffects.of(weapon, "extra_damage")) {
            if (applies(active, target)) {
                bonus += active.number("per_level", 0.0D) * active.level();
            }
        }
        return bonus;
    }

    private static boolean applies(ElysiumItemEffects.Active active, LivingEntity target) {
        String against = active.text("against", "");
        return switch (against) {
            case "high_health" -> target.getMaxHealth() >= 21.0F;
            case "fire_immune" -> target.fireImmune();
            case "aquatic" -> target.getType().is(TagKey.create(Registries.ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath("c", "aquatic")))
                    || target.canDrownInFluidType(net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value());
            case "tagged" -> target.getType().is(TagKey.create(Registries.ENTITY_TYPE,
                    ResourceLocation.parse(active.text("tag", "minecraft:undead"))));
            default -> false;
        };
    }

    /** Effects a hit leaves on the target (SG-61's target_effect). */
    public static void onHit(ItemStack weapon, LivingEntity target, LivingEntity attacker) {
        for (ElysiumItemEffects.Active active : ElysiumItemEffects.of(weapon, "target_effect")) {
            Holder<MobEffect> effect = effect(active.text("effect", ""));
            if (effect == null) {
                continue;
            }
            int ticks = (int) (active.number("ticks_per_level", 0.0D) * active.level());
            if (ticks > 0) {
                target.addEffect(new MobEffectInstance(effect, ticks), attacker);
            }
        }
        for (ElysiumItemEffects.Active active : ElysiumItemEffects.of(weapon, "wind_blast")) {
            windBlast(target, active.level());
        }
    }

    /** A blow that throws everything around it back, as a wind charge does. */
    private static void windBlast(LivingEntity target, int level) {
        Level level1 = target.level();
        double radius = 1.2D * level;
        for (LivingEntity other : level1.getEntitiesOfClass(LivingEntity.class,
                new AABB(target.blockPosition()).inflate(radius))) {
            Vec3 push = other.position().subtract(target.position()).normalize().scale(1.22D);
            other.push(push.x, 0.3D, push.z);
            other.hurtMarked = true;
        }
    }

    /** What a worn or held piece does for its owner (SG-61's wielder_effect). */
    public static void wielderEffects(Player player) {
        List<ItemStack> worn = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                worn.add(stack);
            }
        }
        for (ItemStack stack : worn) {
            for (ElysiumItemEffects.Active active : ElysiumItemEffects.of(stack, "wielder_effect")) {
                for (var element : active.effect().getAsJsonArray("targets")) {
                    apply(player, worn, stack, active, element.getAsJsonObject());
                }
            }
        }
    }

    private static void apply(Player player, List<ItemStack> worn, ItemStack source,
                              ElysiumItemEffects.Active active, com.google.gson.JsonObject target) {
        Holder<MobEffect> effect = effect(target.get("effect").getAsString());
        if (effect == null) {
            return;
        }
        var levels = target.getAsJsonArray("levels");
        String mode = target.has("mode") ? target.get("mode").getAsString() : "trait_level";
        int index;
        if (mode.equals("piece_count")) {
            int pieces = 0;
            for (ItemStack stack : worn) {
                if (ElysiumItemEffects.level(stack, active.source()) > 0) {
                    pieces++;
                }
            }
            index = Math.min(pieces, levels.size()) - 1;
        } else if (mode.equals("full_set_only")) {
            int pieces = 0;
            for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                if (ElysiumItemEffects.level(player.getItemBySlot(slot), active.source()) > 0) {
                    pieces++;
                }
            }
            index = pieces >= 4 ? 0 : -1;
        } else {
            index = Math.min(active.level(), levels.size()) - 1;
        }
        if (index < 0 || index >= levels.size()) {
            return;
        }
        int amplifier = levels.get(index).getAsInt() - 1;
        if (amplifier < 0) {
            return;
        }
        int duration = target.has("duration") ? target.get("duration").getAsInt() : 38;
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false, true));
    }

    /** Effects a piece clears while it is worn (SG-61's cancel_effects). */
    public static void cancelEffects(Player player, ItemStack stack) {
        for (ElysiumItemEffects.Active active : ElysiumItemEffects.of(stack, "cancel_effects")) {
            for (var element : active.effect().getAsJsonArray("effects")) {
                Holder<MobEffect> effect = effect(element.getAsString());
                if (effect != null && player.hasEffect(effect)) {
                    player.removeEffect(effect);
                }
            }
        }
    }

    /** Damage of a kind a piece turns aside (SG-61's negate_damage). */
    public static float negate(ItemStack stack, net.minecraft.world.damagesource.DamageSource source, float amount) {
        float left = amount;
        for (ElysiumItemEffects.Active active : ElysiumItemEffects.of(stack, "negate_damage")) {
            TagKey<net.minecraft.world.damagesource.DamageType> tag = TagKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.parse(active.text("tag", "")));
            if (source.is(tag)) {
                double scale = Math.min(1.0D, active.number("scale_per_level", 0.0D) * active.level());
                left *= (float) (1.0D - scale);
            }
        }
        return left;
    }

    /** Mining speed on the blocks a trait is written for (SG-61's block_mining_speed). */
    public static float miningSpeed(ItemStack stack, BlockState state, float speed) {
        float out = speed;
        for (ElysiumItemEffects.Active active : ElysiumItemEffects.of(stack, "mining_speed")) {
            TagKey<net.minecraft.world.level.block.Block> tag = TagKey.create(Registries.BLOCK,
                    ResourceLocation.parse(active.text("tag", "")));
            if (state.is(tag)) {
                out *= (float) (1.0D + active.number("per_level", 0.0D) * active.level());
            }
        }
        return out;
    }

    /** Whether a durability event is made worse or better (SG-61's durability). */
    public static int durability(ItemStack stack, int amount, net.minecraft.util.RandomSource random) {
        int out = amount;
        for (ElysiumItemEffects.Active active : ElysiumItemEffects.of(stack, "durability")) {
            double chance = active.number("chance_per_level", 0.0D) * active.level();
            if (random.nextDouble() < chance) {
                out += (int) active.number("amount", 0.0D);
            }
        }
        return Math.max(0, out);
    }

    /** Gear that mends itself (SG-61's self_repair). */
    public static void selfRepair(ItemStack stack, net.minecraft.util.RandomSource random, long gameTime) {
        for (ElysiumItemEffects.Active active : ElysiumItemEffects.of(stack, "self_repair")) {
            int interval = (int) active.number("interval", 20.0D);
            if (interval <= 0 || gameTime % interval != 0 || stack.getDamageValue() <= 0) {
                continue;
            }
            if (random.nextDouble() < active.number("chance_per_level", 0.0D) * active.level()) {
                int repair = (int) Math.max(1.0D, active.number("amount_per_level", 1.0D) * active.level());
                stack.setDamageValue(Math.max(0, stack.getDamageValue() - repair));
            }
        }
    }

    /** Items pulled toward whoever carries the piece (SG-61's item_magnet). */
    public static void magnet(Player player, ItemStack stack) {
        if (player.isShiftKeyDown()) {
            return;                             // crouching turns a magnet off, as it always did
        }
        for (ElysiumItemEffects.Active active : ElysiumItemEffects.of(stack, "item_magnet")) {
            double range = active.number("range", 2.0D) + active.level();
            double pull = active.number("pull", 0.06D);
            TagKey<net.minecraft.world.item.Item> only = active.effect().has("items")
                    ? TagKey.create(Registries.ITEM, ResourceLocation.parse(active.text("items", ""))) : null;
            for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class,
                    player.getBoundingBox().inflate(range))) {
                if (only != null && !item.getItem().is(only)) {
                    continue;
                }
                Vec3 toward = player.position().add(0.0D, 0.5D, 0.0D).subtract(item.position());
                item.setDeltaMovement(item.getDeltaMovement().add(toward.normalize().scale(pull)));
            }
        }
    }

    /** Extra drops a trait shakes loose (SG-61's bonus_drops). */
    public static List<ItemStack> bonusDrops(ItemStack tool, List<ItemStack> drops,
                                             net.minecraft.util.RandomSource random) {
        List<ItemStack> extra = new ArrayList<>();
        for (ElysiumItemEffects.Active active : ElysiumItemEffects.of(tool, "bonus_drops")) {
            TagKey<net.minecraft.world.item.Item> ingredient = TagKey.create(Registries.ITEM,
                    ResourceLocation.parse(active.text("ingredient", "")));
            double chance = active.number("chance_per_level", 0.0D) * active.level();
            double multiplier = active.number("multiplier", 0.0D);
            for (ItemStack drop : drops) {
                if (!drop.is(ingredient) || random.nextDouble() >= chance) {
                    continue;
                }
                int count = (int) Math.round(drop.getCount() * multiplier);
                if (count > 0) {
                    extra.add(drop.copyWithCount(count));
                }
            }
        }
        return extra;
    }

    /**
     * Synergy (SG-61's synergy_multiplier): what a trait adds to how well the
     * materials in a compound work together.
     *
     * <p>Compound materials themselves are P8; this is the reader, so a trait
     * that states a synergy bonus is carried rather than silently ignored until
     * the alloy forge lands.
     */
    public static double synergyBonus(ItemStack stack, double synergy) {
        double bonus = 0.0D;
        for (ElysiumItemEffects.Active active : ElysiumItemEffects.of(stack, "synergy")) {
            double min = active.number("min", Double.NEGATIVE_INFINITY);
            double max = active.number("max", Double.POSITIVE_INFINITY);
            if (synergy > min && synergy < max) {
                bonus += active.number("per_level", 0.0D) * active.level();
            }
        }
        return bonus;
    }

    /** A block a trait places where it is used (SG-61's block_placer). */
    public static net.minecraft.world.level.block.Block placedBlock(ElysiumItemEffects.Active active) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(active.text("block", "minecraft:air")));
    }

    private static Holder<MobEffect> effect(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return null;
        }
        return BuiltInRegistries.MOB_EFFECT.getHolder(ResourceKey.create(Registries.MOB_EFFECT, key)).orElse(null);
    }

}
