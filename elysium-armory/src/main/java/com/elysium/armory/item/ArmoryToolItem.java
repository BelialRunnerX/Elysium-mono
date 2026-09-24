package com.elysium.armory.item;

import com.elysium.armory.property.ArmoryVanilla;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * The tools with a habit of their own: a sickle that reaps a patch, a
 * prospector's hammer that listens to the rock ahead of it.
 *
 * <p>Everything that happens while <em>breaking</em> a block — a hammer taking
 * a face at a time, a saw taking a tree — is in {@link
 * com.elysium.armory.event.ArmoryToolEvents}, because that is where the game
 * says a block was broken. This class is what the tools do when they are used.
 */
public class ArmoryToolItem extends ArmoryGearItem {

    /** How far a sickle reaps around the block it is used on. */
    public static final int SICKLE_RADIUS = 2;

    /** Durability a sickle spends on a reaping, and a prospector on a scan. */
    public static final int SICKLE_COST = 3;
    public static final int PROSPECTOR_COST = 2;

    /** How far a prospector's hammer listens, and how long before it can listen again. */
    public static final int PROSPECTOR_RANGE = 16;
    public static final int PROSPECTOR_COOLDOWN = 20;

    /** What a prospector's hammer is listening for. */
    public static final TagKey<Block> PROSPECTOR_TARGETS = TagKey.create(
            net.minecraft.core.registries.Registries.BLOCK,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "ores"));

    public ArmoryToolItem(String gearType, Properties properties) {
        super(gearType, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        InteractionResult trait = ArmoryGearLogic.useOn(context);
        if (trait != InteractionResult.PASS) {
            return trait;                   // a trait that places or fills answers first
        }
        return switch (gearType()) {
            case "sickle" -> reap(level, context.getClickedPos(), player, context.getItemInHand());
            case "prospector_hammer" -> prospect(level, context.getClickedPos(), context.getClickedFace(),
                    player, context.getItemInHand());
            default -> ArmoryGearLogic.useOn(context);
        };
    }

    /** Harvests every grown crop in a square around the block, and sows it again. */
    private InteractionResult reap(Level level, BlockPos centre, Player player, ItemStack stack) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        int reaped = 0;
        for (int dx = -SICKLE_RADIUS; dx <= SICKLE_RADIUS; dx++) {
            for (int dz = -SICKLE_RADIUS; dz <= SICKLE_RADIUS; dz++) {
                BlockPos pos = centre.offset(dx, 0, dz);
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
                    continue;
                }
                // Everything it drops but the seed it is sown again with.
                Block.dropResources(state, level, pos, null, player, stack);
                level.setBlock(pos, crop.getStateForAge(0), Block.UPDATE_ALL);
                reaped++;
            }
        }
        if (reaped > 0) {
            stack.hurtAndBreak(SICKLE_COST, player, EquipmentSlot.MAINHAND);
            level.playSound(null, centre, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** Listens along the face for ore, and says what is there. */
    private InteractionResult prospect(Level level, BlockPos from, Direction facing,
                                       Player player, ItemStack stack) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.PASS;
        }
        List<Component> found = new ArrayList<>();
        for (int i = 1; i <= PROSPECTOR_RANGE; i++) {
            BlockPos pos = from.relative(facing, i);
            BlockState state = level.getBlockState(pos);
            if (state.is(PROSPECTOR_TARGETS) || state.is(Blocks.ANCIENT_DEBRIS)) {
                Component name = state.getBlock().getName();
                if (found.stream().noneMatch(c -> c.getString().equals(name.getString()))) {
                    found.add(name);
                }
            }
        }
        player.getCooldowns().addCooldown(this, PROSPECTOR_COOLDOWN);
        stack.hurtAndBreak(PROSPECTOR_COST, player, EquipmentSlot.MAINHAND);
        if (found.isEmpty()) {
            player.displayClientMessage(Component.translatable("elysiumarmory.prospector.nothing"), true);
        } else {
            Component list = Component.empty();
            for (int i = 0; i < found.size(); i++) {
                list = i == 0 ? found.get(i).copy() : ((net.minecraft.network.chat.MutableComponent) list)
                        .append(", ").append(found.get(i));
            }
            player.displayClientMessage(Component.translatable("elysiumarmory.prospector.finds", list), true);
        }
        return InteractionResult.CONSUME;
    }

    /** A dagger leaves its target able to be hit again sooner than anything else does. */
    @Override
    public boolean hurtEnemy(ItemStack stack, net.minecraft.world.entity.LivingEntity target,
                             net.minecraft.world.entity.LivingEntity attacker) {
        if (gearType().equals("dagger")) {
            target.invulnerableTime = (int) (target.invulnerableTime * ArmoryVanilla.DAGGER_INVULNERABILITY);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    /** Hoes, mattocks and paxels till; machetes and sickles cut plants faster. */
    public boolean tills() {
        return gearType().equals("hoe") || gearType().equals("mattock") || gearType().equals("paxel");
    }

    public static boolean isPlant(BlockState state) {
        return state.is(BlockTags.CROPS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.REPLACEABLE_BY_TREES)
                || state.hasProperty(BlockStateProperties.AGE_7);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        ArmoryGearLogic.tick(this, stack, level);
    }
}
