package com.elysium.core.block;

import com.elysium.lib.claim.ElysiumClaim;
import com.elysium.lib.claim.ElysiumClaims;
import com.elysium.lib.standing.ElysiumStanding;
import com.elysium.lib.tier.ElysiumTiers;
import com.elysium.lib.tier.ElysiumWorldTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Registry Beacon: the block that claims ground.
 *
 * <h2>What the specification asks for</h2>
 *
 * §B.2 lists it among the Command machines — <blockquote>Registry Beacon | 3 |
 * Command | Claims planet and anchors Imperial file.</blockquote> — and §8.3
 * puts it at the third rung of the base ladder, at Homestead, which is the
 * moment a camp becomes somewhere you live.
 *
 * §14.2 says what it costs: a permanent Suspicion floor rather than points that
 * decay. §14.3 and §E.1 say what happens when it dies, in the same words twice
 * — <blockquote>beacon destruction releases claim but does not erase
 * constructed world.</blockquote>
 *
 * <h2>Claiming is an act, not a side effect of placing a block</h2>
 *
 * A beacon placed is dormant. It claims when a player uses it.
 *
 * The alternative — claiming on placement — is one fewer click and worse in
 * every other way. The cost of a claim is a permanent floor under the meter
 * that decides whether the Empire besieges you, and levying that because
 * somebody mis-clicked while rearranging a chest room is a trap. It also gives
 * the block nothing to do: a machine you never operate is scenery. Making the
 * claim an act means the beacon can be crafted, carried, placed and positioned
 * before anyone commits to anything, which is how every other consequential
 * thing in this mod already works.
 *
 * <h2>The block state is cosmetic and the save data is the truth</h2>
 *
 * {@link #CLAIMED} exists so that a claimed beacon lights up. Nothing decides
 * anything from it. Every question — is this claimed, by whom, how large — is
 * put to {@link ElysiumClaims}, which is the only thing that can answer
 * correctly when the two disagree.
 *
 * They can disagree: a block state lives in a chunk and a claim lives in
 * dimension data, and a save restored from a partial backup can have one
 * without the other. Using the block state as a cache that is only ever
 * <em>displayed</em> means that divergence is a beacon that looks wrong for as
 * long as nobody touches it, and is corrected the first time anybody does —
 * rather than a beacon that <em>is</em> wrong.
 */
public class RegistryBeaconBlock extends Block {

    /** Lit when the ground is filed. Display only — see the class note. */
    public static final BooleanProperty CLAIMED = BooleanProperty.create("claimed");

    public RegistryBeaconBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                // Tough, and blast resistance well above stone. §14.3's
                // register actions place their waves around this block, so it
                // is literally what a siege is aimed at — and a siege target an
                // early creeper can delete would make the failure row fire by
                // accident far more often than by defeat.
                .strength(6.0F, 1800.0F)
                .requiresCorrectToolForDrops()
                .lightLevel(state -> state.getValue(CLAIMED) ? 10 : 0)
                .sound(SoundType.ANVIL));
        registerDefaultState(stateDefinition.any().setValue(CLAIMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CLAIMED);
    }

    // ------------------------------------------------------------------

    /**
     * Using a Registry Beacon item on a claimed beacon ascends it.
     *
     * Anything else in hand falls through to {@link #useWithoutItem}, so
     * right-clicking with a pickaxe still reports the claim rather than doing
     * nothing.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state,
                                              Level level, BlockPos pos,
                                              Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!held.is(asItem())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer)) {
            return ItemInteractionResult.SUCCESS;
        }
        ElysiumClaims claims = ElysiumClaims.of(server);
        ElysiumClaim existing = claims.anchored(pos);
        if (existing == null) {
            // Not claimed yet. Ascending an unclaimed beacon would be a player
            // spending beacons on nothing, so send them to the claim first.
            player.displayClientMessage(Component.translatable(
                            "elysium.claim.ascend.unclaimed")
                    .withStyle(ChatFormatting.RED), false);
            return ItemInteractionResult.CONSUME;
        }
        if (!existing.owner().equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable(
                            "elysium.claim.not_yours", existing.ownerName())
                    .withStyle(ChatFormatting.RED), false);
            return ItemInteractionResult.CONSUME;
        }
        ascend(server, claims, pos, player, existing, held);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer)) {
            return InteractionResult.SUCCESS;
        }

        ElysiumClaims claims = ElysiumClaims.of(server);
        ElysiumClaim existing = claims.anchored(pos);

        if (existing == null) {
            stake(server, claims, pos, player, state);
            return InteractionResult.CONSUME;
        }

        // Someone else's. Reported rather than silently doing nothing, because
        // a button that does nothing is indistinguishable from a bug.
        if (!existing.owner().equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable(
                            "elysium.claim.not_yours", existing.ownerName())
                    .withStyle(ChatFormatting.RED), false);
            return InteractionResult.CONSUME;
        }

        report(player, existing);
        return InteractionResult.CONSUME;
    }

    private void stake(ServerLevel level, ElysiumClaims claims, BlockPos pos,
                       Player player, BlockState state) {
        ElysiumClaim claim = claims.stake(player, pos, ElysiumClaim.DEFAULT_RADIUS);
        level.setBlock(pos, state.setValue(CLAIMED, true), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS,
                1.0F, 1.0F);
        anchorRespawn(level, pos, player);

        // §14.2: the floor applies from the moment the file is opened, not
        // from the next decay tick two minutes later.
        int floor = claim.floor();
        int current = ElysiumStanding.getSuspicion(player);
        if (current < floor) {
            ElysiumStanding.addSuspicion(player, floor - current,
                    ElysiumStanding.CAUSE_CLAIM);
        }

        player.displayClientMessage(Component.translatable(
                        "elysium.claim.staked", claim.radius(), floor)
                .withStyle(ChatFormatting.GOLD), false);

        absorbWorks(level, player, claim);
    }

    /**
     * The base lifecycle: a works built wild and then registered stops being
     * watched. The claim takes over from here, at its own floor.
     *
     * Called on staking and on every ascension, because a claim that grows
     * over a neighbouring works has registered it just as surely as one
     * planted on top of it.
     */
    private static void absorbWorks(ServerLevel level, Player player,
                                    ElysiumClaim claim) {
        int cleared = com.elysium.lib.works.ElysiumWorks.of(level).registered(claim);
        if (cleared > 0) {
            player.displayClientMessage(Component.translatable(
                            "elysium.works.registered", cleared)
                    .withStyle(ChatFormatting.GOLD), false);
        }
    }

    /**
     * A claim is where you come back to.
     *
     * <h3>The hole this closes</h3>
     *
     * Claiming, as built, was <b>pure cost</b>: a permanent Suspicion floor, a
     * siege target, and nothing in return. A rational player would never plant
     * one, which would have made the whole of §14.2 and §14.3 content nobody
     * opts into — the worst way for a system to fail, because it works
     * perfectly and is never seen.
     *
     * §F.1's glossary says what the other half is, and it is not a bonus
     * invented to balance the cost: <blockquote>Claim: Registry Beacon
     * ownership state with <b>respawn</b>/persistence/Empire
     * consequences.</blockquote> §22 lists the same thing among what claim
     * status changes. It was always part of what a claim is.
     *
     * <h3>Why this and not a bed</h3>
     *
     * It behaves like a respawn anchor rather than a bed: forced, so it works
     * in any dimension and does not care about the time of day, and it does not
     * consume anything. A base you have filed with the Empire is somewhere you
     * are <em>registered</em>; being sent back there is the literal meaning of
     * the word.
     *
     * A struck claim leaves the respawn position pointing at a block that is no
     * longer a beacon. That is deliberately not cleaned up here: vanilla
     * already handles a respawn position whose block has gone — the player
     * lands at world spawn and is told their respawn point was missing or
     * obstructed, which is exactly the right message and exactly the right
     * moment to receive it. Clearing it eagerly would swap a clear explanation
     * for a silent one.
     */
    private void anchorRespawn(ServerLevel level, BlockPos pos, Player player) {
        if (player instanceof ServerPlayer server) {
            server.setRespawnPosition(level.dimension(), pos, 0.0F, true, false);
        }
    }

    /**
     * A beacon used on a claimed beacon ascends the claim.
     *
     * <h3>Why a second beacon and not a new item</h3>
     *
     * Gear ascends by feeding it another of itself — {@link
     * com.elysium.core.item.ElysiumGearAscension} is built on exactly that, and
     * a player who has ascended a blade already knows the verb. A dedicated
     * "claim upgrade" item would be a second vocabulary for the same idea, and
     * the beacon is already the expensive thing that means territory.
     *
     * <h3>Why the price rises and does not double</h3>
     *
     * Gear doubles, because reaching tier <i>n</i> costing 2^<i>n</i> pieces is
     * the only thing limiting an uncapped ladder whose reward compounds. A
     * claim's radius is linear — sixty-four a tier, flat — so a doubling price
     * would be a wall two tiers in rather than a ladder.
     *
     * But the reward is not as flat as the radius reads: ground is area, and
     * the second tier covers three times what the first did. So the price rises
     * with the tier rather than staying at one, and one beacon buys the first
     * tier, two the second, and so on. Triangular price, quadratic reward — the
     * player is still gaining, and is not gaining for free.
     */
    private InteractionResult ascend(ServerLevel level, ElysiumClaims claims,
                                     BlockPos pos, Player player,
                                     ElysiumClaim existing, ItemStack held) {
        int price = existing.ascension() + 1;
        if (held.getCount() < price) {
            player.displayClientMessage(Component.translatable(
                            "elysium.claim.ascend.price", price,
                            existing.ascension() + 1)
                    .withStyle(ChatFormatting.RED), false);
            return InteractionResult.CONSUME;
        }

        ElysiumClaim grown = claims.ascend(pos);
        if (grown == null) {
            return InteractionResult.CONSUME;    // struck between the two reads
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(price);
        }
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT,
                SoundSource.BLOCKS, 1.0F, 1.0F + 0.1F * grown.ascension());

        // The floor moves with the claim, and applies now rather than at the
        // next decay tick — the same rule staking follows.
        int floor = grown.floor();
        int current = ElysiumStanding.getSuspicion(player);
        if (current < floor) {
            ElysiumStanding.addSuspicion(player, floor - current,
                    ElysiumStanding.CAUSE_CLAIM);
        }

        player.displayClientMessage(Component.translatable(
                        "elysium.claim.ascended", grown.ascension(),
                        grown.radius(), floor)
                .withStyle(ChatFormatting.AQUA), false);
        noticed(player, grown);
        absorbWorks(level, player, grown);
        return InteractionResult.CONSUME;
    }

    /**
     * The base's ascension counts as a world tier, from now on.
     *
     * Through {@link ElysiumTiers#lift} rather than a fourth term in the tier
     * arithmetic, and the difference matters. A term would have to be read
     * every time anything asked what tier a player is — which is every loot
     * roll and every mob that spawns — and answering it means walking every
     * loaded dimension's claim map looking for this player's beacons. A floor
     * is written once, here, at the moment the player pays for it, and is
     * free to read forever after.
     *
     * <p>It also says the right thing. A floor never falls, and neither does
     * an ascension: the claim survives the beacon being broken, and the Empire
     * "does not un-record it when you knock it down". A player who ascends
     * four times and then dismantles the whole fortress has still built it.
     *
     * <p>Called from the report path as well as the ascend path, so a world
     * that was already ascended before this existed catches up the first time
     * its owner right-clicks their own beacon.
     */
    private void noticed(Player player, ElysiumClaim claim) {
        int tier = ElysiumWorldTier.tierForAscension(claim.ascension());
        if (ElysiumTiers.lift(player, tier)) {
            player.displayClientMessage(Component.translatable(
                            "elysium.claim.world_tier", tier,
                            Component.translatable(ElysiumWorldTier.nameKey(tier)))
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
    }

    private void report(Player player, ElysiumClaim claim) {
        noticed(player, claim);
        player.displayClientMessage(Component.translatable(
                        "elysium.claim.report", claim.structures(), claim.floor(),
                        claim.radius())
                .withStyle(ChatFormatting.YELLOW), false);
    }

    // ------------------------------------------------------------------

    /**
     * The beacon is gone.
     *
     * On {@code onRemove} rather than on {@code playerWillDestroy}, because
     * §14.3's failure row is "beacon destroyed" and does not care who did it. A
     * Praetor breaching the roof, a creeper, a piston, {@code /setblock} — each
     * is the same event, and hooking the player-destroy path would have made
     * exactly the one case the specification is about (the Empire breaks your
     * beacon) the one case that did nothing.
     *
     * The guard on the block changing is vanilla's own idiom: {@code onRemove}
     * also fires when a block state changes in place, and striking the claim
     * every time this beacon lights up would be a claim that could not be made.
     *
     * Nothing here touches a block. §E.1's invariant is the point — the walls,
     * the floors and the machines are all still there. They are simply nobody's.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            ElysiumClaim struck = ElysiumClaims.of(server).strike(pos);
            if (struck != null) {
                ServerPlayer owner = server.getServer().getPlayerList()
                        .getPlayer(struck.owner());
                if (owner != null) {
                    owner.displayClientMessage(Component.translatable(
                                    "elysium.claim.struck")
                            .withStyle(ChatFormatting.RED), false);
                }
                server.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.BLOCKS, 1.0F, 0.6F);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
