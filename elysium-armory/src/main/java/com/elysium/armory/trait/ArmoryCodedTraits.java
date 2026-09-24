package com.elysium.armory.trait;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The traits whose behaviour was never data, in either mod: the ones that do
 * something the effect types cannot say (SG-63).
 *
 * <p>Each is still declared in its trait file — with a {@code code} effect — so
 * a player and a datapack can see the trait exists, what it is called and what
 * it is worth; this is only where the behaviour lives.
 */
public final class ArmoryCodedTraits {

    private ArmoryCodedTraits() {
    }

    public static final String ANCIENT = "ancient";
    public static final String LUSTROUS = "lustrous";
    public static final String MAGMATIC = "magmatic";
    public static final String SPOON = "spoon";
    public static final String TURTLE = "turtle";
    public static final String VOID_WARD = "void_ward";
    public static final String FLAMMABLE = "flammable";
    public static final String INDESTRUCTIBLE = "indestructible";
    public static final String BOUNCE = "bounce";
    public static final String MOONWALKER = "moonwalker";
    public static final String SNOW_WALKER = "snow_walker";

    /** Ancient: a quarter more experience per level, from mobs and from blocks. */
    public static int experience(ItemStack tool, int experience) {
        int level = ArmoryTraitEffects.level(tool, ANCIENT);
        return level <= 0 ? experience : experience + (int) Math.floor(experience * 0.25D * level);
    }

    /** Lustrous: mines faster in the light, and only where the tool is the right one. */
    public static float lightBonus(ItemStack tool, Player player) {
        int level = ArmoryTraitEffects.level(tool, LUSTROUS);
        if (level <= 0) {
            return 0.0F;
        }
        BlockPos pos = player.blockPosition();
        int light = Math.max(player.level().getBrightness(net.minecraft.world.level.LightLayer.SKY, pos),
                (int) (player.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos) * 0.75D));
        return (float) (4.0D * level * light / 15.0D);
    }

    /** Magmatic: what a block drops, smelted. */
    public static ItemStack smelt(ServerLevel level, ItemStack drop) {
        return level.getRecipeManager()
                .getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING,
                        new net.minecraft.world.item.crafting.SingleRecipeInput(drop), level)
                .map(recipe -> recipe.value().getResultItem(level.registryAccess()).copyWithCount(drop.getCount()))
                .orElse(ItemStack.EMPTY);
    }

    /** Spoon: a pickaxe that also takes what a shovel would. */
    public static boolean spoonMines(ItemStack tool, BlockState state) {
        return ArmoryTraitEffects.level(tool, SPOON) > 0
                && state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL);
    }

    /** Turtle: a helmet that lets its wearer breathe under water. */
    public static void turtle(Player player) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (ArmoryTraitEffects.level(helmet, TURTLE) > 0 && player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 210, 0, false, false, true));
        }
    }

    /** Void ward: armour that catches its wearer when the world runs out under them. */
    public static void voidWard(Player player) {
        if (player.getY() >= player.level().getMinBuildHeight() - 32) {
            return;
        }
        boolean warded = false;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            warded |= ArmoryTraitEffects.level(player.getItemBySlot(slot), VOID_WARD) > 0;
        }
        if (!warded) {
            return;
        }
        player.teleportTo(player.getX(), player.level().getMinBuildHeight() - 12, player.getZ());
        player.setDeltaMovement(player.getDeltaMovement().x, 20.0D, player.getDeltaMovement().z);
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 3));
        player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 200, 9));
        player.fallDistance = 0.0F;
    }

    /** Flammable: gear that suffers when its wearer burns, and burns well in a furnace. */
    public static void flammable(Player player) {
        if (!player.isOnFire()) {
            return;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (ArmoryTraitEffects.level(stack, FLAMMABLE) > 0) {
                stack.hurtAndBreak(2, player, slot);
            }
        }
    }

    /** Indestructible: gear that never wears at all. */
    public static boolean indestructible(ItemStack stack) {
        return ArmoryTraitEffects.level(stack, INDESTRUCTIBLE) > 0;
    }

    /** Bounce: boots that turn a fall into a bounce. */
    public static boolean bounce(LivingEntity entity) {
        return ArmoryTraitEffects.level(entity.getItemBySlot(EquipmentSlot.FEET), BOUNCE) > 0;
    }

    /** Moonwalker: a softer landing as well as the lighter step its attribute gives. */
    public static float moonwalkerFall(LivingEntity entity, float distance) {
        int level = ArmoryTraitEffects.level(entity.getItemBySlot(EquipmentSlot.FEET), MOONWALKER);
        return level <= 0 ? distance : distance * (float) Math.max(0.0D, 1.0D - 0.15D * level);
    }

    /** Snow walker: powder snow holds up anyone wearing it. */
    public static boolean snowWalker(LivingEntity entity) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (ArmoryTraitEffects.level(entity.getItemBySlot(slot), SNOW_WALKER) > 0) {
                return true;
            }
        }
        return false;
    }

    public static final String WIDEN = "widen";
    public static final String BRILLIANT = "brilliant";
    public static final String CONFETTI = "confetti";
    public static final String RED_CARD = "red_card";

    /**
     * Multi-break was declared in Silent Gear and never written. It is kept
     * here by name so that the trait's file is not a promise nothing keeps:
     * the trait exists, grants nothing, and says so.
     */
    public static final String MULTI_BREAK = "multi_break";

    /** Widen: how much wider a wide tool's square is (SG-33). */
    public static int widen(ItemStack tool) {
        return ArmoryTraitEffects.level(tool, WIDEN);
    }

    /** Brilliant: gold is gold to a piglin, and so is this. */
    public static boolean brilliant(ItemStack stack) {
        return ArmoryTraitEffects.level(stack, BRILLIANT) > 0;
    }

    /**
     * Red card: gear that breaks for good.
     *
     * <p>Everything else stops at one point of durability and stays there,
     * broken but repairable — which is what "broken" means in a mod where a
     * piece is a player's own materials. A piece with this trait is destroyed
     * instead, the way vanilla does it.
     */
    public static boolean breaksForGood(ItemStack stack) {
        return ArmoryTraitEffects.level(stack, RED_CARD) > 0;
    }

    /** Confetti: a kill that goes off. */
    public static void confetti(ServerLevel level, LivingEntity victim, ItemStack weapon) {
        if (ArmoryTraitEffects.level(weapon, CONFETTI) <= 0) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            net.minecraft.world.item.ItemStack rocket =
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET);
            net.minecraft.world.entity.projectile.FireworkRocketEntity firework =
                    new net.minecraft.world.entity.projectile.FireworkRocketEntity(level, rocket,
                            victim.getX(), victim.getEyeY(), victim.getZ(), true);
            level.addFreshEntity(firework);
        }
    }

    /** Jabberwocky: what a diamond ore might turn out to be hiding. */
    public static final List<String> JABBERWOCKY = List.of("minecraft:cat", "minecraft:chicken",
            "minecraft:cod", "minecraft:pufferfish", "minecraft:rabbit", "minecraft:salmon", "minecraft:wolf");

    public static void jabberwocky(ServerLevel level, BlockPos pos, ItemStack tool, BlockState state) {
        if (ArmoryTraitEffects.level(tool, "jabberwocky") <= 0
                || !state.is(TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores/diamond")))) {
            return;
        }
        String id = JABBERWOCKY.get(level.getRandom().nextInt(JABBERWOCKY.size()));
        BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(id))
                .ifPresent(type -> type.spawn(level, pos, net.minecraft.world.entity.MobSpawnType.TRIGGERED));
    }

}
