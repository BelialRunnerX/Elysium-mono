package com.elysium.armory.block;

import com.elysium.armory.craft.ArmoryGrading;
import com.elysium.armory.property.ArmoryMaterialScience;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Material Grader (Silent Gear's SG-81): a part worked up a grade, for a
 * price and a risk.
 *
 * <h2>The price is in the other hand</h2>
 *
 * A gem in the off hand is what a grading costs, and it is spent whether the
 * roll succeeds or not — that is what makes grading a decision rather than a
 * button. The part goes in the main hand and comes back changed, or comes back
 * Crude, which is the risk the first attempt carries.
 */
public class ArmoryMaterialGraderBlock extends Block {

    /** What a grading costs: one gem, of any kind a pack calls a gem. */
    public static final TagKey<Item> CATALYSTS =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "gems"));

    public ArmoryMaterialGraderBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        ItemStack payment = player.getItemInHand(hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!ArmoryGrading.canGrade(held)) {
            if (!level.isClientSide() && !held.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("elysiumarmory.grader.nothing"), true);
            }
            return ItemInteractionResult.CONSUME;
        }
        if (!payment.is(CATALYSTS)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("elysiumarmory.grader.needs_gem"), true);
            }
            return ItemInteractionResult.CONSUME;
        }
        if (!level.isClientSide()) {
            ItemStack graded = ArmoryGrading.grade(held, player.getRandom());
            if (graded.isEmpty()) {
                return ItemInteractionResult.CONSUME;
            }
            // The gem is spent either way. A grading that only cost something
            // when it worked would be a free attempt with extra steps.
            payment.shrink(1);
            held.shrink(1);
            player.getInventory().placeItemBackInInventory(graded);
            int grade = graded.getOrDefault(
                    com.elysium.armory.registry.ArmoryComponents.MARKS.get(),
                    com.elysium.armory.item.ArmoryMarks.PLAIN).grade();
            player.displayClientMessage(Component.translatable(
                    grade == ArmoryMaterialScience.CRUDE
                            ? "elysiumarmory.grader.crude" : "elysiumarmory.grader.graded",
                    Component.literal(ArmoryMaterialScience.gradeName(grade))), true);
        }
        level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.7F, 1.1F);
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
}
