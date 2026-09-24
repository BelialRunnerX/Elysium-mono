package com.elysium.solarpunk.power;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One rule, on the wall, in a block.
 *
 * <h2>Why one rule per block and not a screen with a list</h2>
 *
 * A rule editor is a menu, a container, a packet, a screen class and a texture,
 * and at the end of all that a player has a list they can only read by opening
 * it. A row of governors along the conduit is the same list, readable from
 * across the room, rearrangeable with a pickaxe, and it costs none of that.
 *
 * It is also how the rest of Minecraft works. A repeater holds one delay; a
 * comparator holds one mode; nobody has ever wanted a repeater configuration
 * screen. Automation built out of blocks stays in the world where the base is,
 * rather than behind a key.
 *
 * <h2>Why the rule is two integers and not two enums</h2>
 *
 * A blockstate enum property needs {@code StringRepresentable}, which is a
 * Minecraft interface, and putting it on {@link PowerClass} or
 * {@link PowerCondition} would drag both out of the harness's reach — they are
 * executed outside a world, and the validator enforces that they import
 * nothing. Two integer properties cost one {@code byIndex} call at the two
 * places that read them, and keep the whole vocabulary testable.
 *
 * <h2>Cycled by clicking, read by clicking</h2>
 *
 * Right-click cycles the condition, sneak-right-click cycles what is held, and
 * either one says the whole rule back in a sentence. There is no mode where a
 * player has set something and does not know what.
 */
public class PowerGovernorBlock extends Block {

    /** Which {@link PowerCondition}, by index. */
    public static final IntegerProperty CONDITION =
            IntegerProperty.create("condition", 0, PowerCondition.values().length - 1);

    /** Which {@link PowerClass} is the highest one held, by index. */
    public static final IntegerProperty HOLD =
            IntegerProperty.create("hold", 0, PowerClass.values().length - 1);

    public PowerGovernorBlock(Properties properties) {
        super(properties);
        // A fresh governor holds the lowest-priority class only, always. The
        // safest possible default: it does something visible (the lamps stop)
        // without being able to stop anybody's water supply the moment it is
        // placed, which is what defaulting to CRITICAL would have done.
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(CONDITION, PowerCondition.ALWAYS.ordinal())
                .setValue(HOLD, PowerClass.COMFORT.ordinal()));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONDITION, HOLD);
    }

    /** The rule this block currently states. */
    public static PowerRule ruleOf(BlockState state) {
        return new PowerRule(
                PowerCondition.byIndex(state.getValue(CONDITION)),
                PowerClass.values()[Math.floorMod(state.getValue(HOLD),
                        PowerClass.values().length)]);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockState next;
        if (player.isShiftKeyDown()) {
            next = state.setValue(HOLD,
                    Math.floorMod(state.getValue(HOLD) + 1,
                            PowerClass.values().length));
        } else {
            next = state.setValue(CONDITION,
                    Math.floorMod(state.getValue(CONDITION) + 1,
                            PowerCondition.values().length));
        }
        level.setBlock(pos, next, Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS,
                0.4F, 1.6F);
        say(player, next);
        return InteractionResult.CONSUME;
    }

    /**
     * State the whole rule, not the half that changed.
     *
     * Saying "Night" alone would be the commonest kind of configuration bug
     * made pleasant: the player reads the half they just set, assumes the other
     * half is what they meant, and finds out three in-game days later that
     * their kiln has been stopped since Tuesday.
     */
    private static void say(Player player, BlockState state) {
        PowerRule rule = ruleOf(state);
        player.displayClientMessage(Component.translatable(
                        "elysiumsolarpunk.power.governor.rule",
                        Component.translatable(rule.when().translationKey()),
                        Component.translatable(rule.hold().translationKey()))
                .withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(Component.translatable(
                        "elysiumsolarpunk.power.governor.hint")
                .withStyle(ChatFormatting.DARK_GRAY), true);
    }
}
