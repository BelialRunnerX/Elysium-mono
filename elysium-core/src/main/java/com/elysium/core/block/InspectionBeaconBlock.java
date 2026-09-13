package com.elysium.core.block;

import com.elysium.lib.entity.ElysiumFaction;
import com.elysium.lib.entity.ElysiumSquads;
import com.elysium.lib.imperial.ElysiumInspection;
import com.elysium.lib.imperial.ElysiumInspections;
import com.elysium.lib.standing.ElysiumStanding;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * An Inspection Beacon: the Empire standing over your mine, writing it down.
 *
 * <h2>What the specification asks for</h2>
 *
 * §14.5: <blockquote>Inspection beacons that report nearby high-volume
 * extraction until disabled.</blockquote> §27.1 gives it a trigger — "registered
 * trade, claim, station activity, suspicion band" — and a list of responses, of
 * which this admits two: <b>cooperate</b>, and <b>resist</b>.
 *
 * <h2>It is not a punishment, it is a price</h2>
 *
 * A post doubles what extraction inside its range costs. It forbids nothing and
 * blocks nothing, which is the difference §14.5 is careful about elsewhere —
 * interdiction is supposed to create "alternate problems instead of
 * hard-locking the player", and the same reasoning applies here. A player who
 * keeps mining is making a trade they can see the terms of.
 *
 * <h2>Breaking it is the point, and it costs the right currency</h2>
 *
 * Destroying Imperial equipment is not a loophole in the Suspicion system; it
 * is the most conspicuous thing on the list. So resisting works — the reporting
 * stops the moment the block is gone — and it answers.
 *
 * <h3>The version that did not work, and why</h3>
 *
 * The first cut priced resistance as Suspicion alone: a one-off charge, set
 * against the doubled rate you would otherwise pay. That is an arithmetic
 * comparison, and the arithmetic is not close. Simulated over a mixed vein, a
 * flat charge of ten is beaten by cooperating for about <b>four ore blocks</b>,
 * after which breaking the post is simply correct and stays correct forever —
 * the doubled rate accrues per block and the charge does not. Worse, Suspicion
 * caps at a hundred, so for anyone mining hard the "cooperate" branch saturates
 * the meter within minutes and stops being a cost at all.
 *
 * Two options where one always wins is not a decision, and no flat number fixes
 * it: a charge large enough to beat twenty blocks of mining is punitive for
 * somebody who breaks the post on sight, which is the one case that should be
 * cheap.
 *
 * <h3>So resistance is not priced in Suspicion</h3>
 *
 * It is priced in <b>enforcement</b>. Breaking a post files the small charge
 * above and brings an Imperial band down on whoever did it, through the same
 * roaming-band machinery everything else uses.
 *
 * That works because the two costs are not commensurable, so neither can
 * dominate the other by arithmetic. Paying the rate is a number. Fighting is a
 * risk, and what it is worth depends on things the mod cannot put on a scale:
 * how you are geared, whether you are eighty blocks down a shaft with one way
 * out, whether your inventory is full of ore you would rather not drop. A
 * player in diamond on the surface breaks every post they see. A player at
 * half health in a deep mine pays the rate and comes back later. Both are
 * right, and they are right for reasons the player can feel rather than
 * compute.
 *
 * The band is sent through {@code ElysiumSquads}, so it obeys the crowd cap and
 * the suppressors like any other — resistance does not get to drop a squad on
 * somebody already fighting six, or through an announced siege. A refusal is
 * allowed; the charge still lands.
 *
 * <h2>It is the Empire's, so it drops nothing</h2>
 *
 * No loot table entry, and it cannot be crafted. A player who could collect
 * these would have a supply of a block whose only function is to tax them,
 * which is not a thing anybody wants; and one who could place them would be
 * able to put an Imperial sensor inside somebody else's base on a server.
 */
public class InspectionBeaconBlock extends Block {

    /**
     * The one-off Suspicion charge for destroying one.
     *
     * Small, and deliberately not the interesting half of the price. See the
     * class note on why the real cost is the band the Empire sends.
     */
    public static final int RESISTING = 10;

    public InspectionBeaconBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                // Softer than the Registry Beacon and with ordinary blast
                // resistance. This one is *meant* to be broken — a sensor that
                // took a diamond pickaxe and four minutes would turn "resist"
                // into "do not bother", which deletes one of §27.1's two
                // implemented responses.
                .strength(2.0F, 6.0F)
                .lightLevel(state -> 9)
                .sound(SoundType.COPPER));
    }

    /**
     * Where the Empire puts one, given somewhere the player was digging.
     *
     * On the surface above the mine rather than in it. Partly because a beacon
     * buried in a shaft is one the player walks past without seeing — and a
     * tax nobody can find is just an unexplained number — and partly because
     * placing blocks inside a player's excavation is how automated placement
     * ends up inside somebody's base.
     *
     * <b>It never replaces anything.</b> Every candidate must be air standing
     * on something solid. A placement that overwrote a block would be this
     * feature damaging a player's build on a timer, which no amount of
     * fictional justification makes acceptable.
     *
     * @return where it went, or null if there was nowhere safe
     */
    @Nullable
    public static BlockPos plant(ServerLevel level, Player watching, BlockPos near) {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            int dx = random.nextInt(33) - 16;
            int dz = random.nextInt(33) - 16;
            BlockPos surface = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    near.offset(dx, 0, dz));

            // Within reporting range of the mine, or the post watches nothing.
            // A deep mine under a tall mountain can put the surface further
            // away than the sensor reaches, and planting anyway would be a
            // beacon that costs the player nothing and explains nothing.
            if (!near.closerThan(surface, ElysiumInspection.RANGE)) {
                continue;
            }
            if (level.getBlockState(surface).isAir()
                    && level.getBlockState(surface.above()).isAir()
                    && !level.getBlockState(surface.below()).isAir()) {
                level.setBlockAndUpdate(surface,
                        com.elysium.core.Elysium.INSPECTION_BEACON.get()
                                .defaultBlockState());
                level.playSound(null, surface, SoundEvents.BEACON_POWER_SELECT,
                        SoundSource.BLOCKS, 1.0F, 0.7F);
                return surface;
            }
        }
        return null;
    }

    /** What it is watching, and for how much longer. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ElysiumInspection post = ElysiumInspections.of(server).at(pos);
        if (post == null) {
            // Lapsed and swept, or left over from a world edit. Saying so is
            // better than saying nothing: the block is inert and the player
            // should know they can ignore it.
            player.displayClientMessage(Component.translatable(
                            "elysium.inspection.lapsed")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable(
                        "elysium.inspection.reporting",
                        ElysiumInspection.RANGE,
                        ElysiumInspection.MULTIPLIER,
                        post.minutesLeft(server.getGameTime()))
                .withStyle(ChatFormatting.RED), false);
        return InteractionResult.CONSUME;
    }

    /**
     * §27.1's "resist".
     *
     * On {@code onRemove}, like the Registry Beacon and for the same reason: it
     * does not matter who broke it. The charge, though, only lands on a player
     * — a creeper that flattens a post has not resisted anything, and billing
     * the nearest person for it would be the system inventing a crime.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
                                        Player player) {
        if (level instanceof ServerLevel server && !player.isCreative()) {
            ElysiumInspection post = ElysiumInspections.of(server).at(pos);
            if (post != null && !post.hasLapsed(server.getGameTime())) {
                ElysiumStanding.addSuspicion(player, RESISTING,
                        ElysiumStanding.CAUSE_RESISTED);
                // The real price. See the class note: a number and a fight are
                // not commensurable, which is the only reason this is a
                // decision rather than a calculation.
                int sent = ElysiumSquads.send(server, player, server.getRandom(),
                        ElysiumFaction.EMPIRE);
                player.displayClientMessage(Component.translatable(
                                sent > 0
                                        ? "elysium.inspection.resisted"
                                        : "elysium.inspection.resisted_quiet",
                                RESISTING)
                        .withStyle(ChatFormatting.GOLD), false);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * The record follows the block, however it went.
     *
     * Separate from {@code playerWillDestroy} above because the two answer
     * different questions: that one is "did somebody resist", this one is "is
     * this post still reporting". A creeper, a piston or {@code /setblock} all
     * stop the reporting and none of them is resistance.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            if (ElysiumInspections.of(server).remove(pos) != null) {
                server.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.BLOCKS, 1.0F, 1.2F);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
