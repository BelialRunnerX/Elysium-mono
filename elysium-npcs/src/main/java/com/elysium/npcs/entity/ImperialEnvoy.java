package com.elysium.npcs.entity;

import com.elysium.lib.standing.ElysiumRewards;
import com.elysium.lib.standing.ElysiumStanding;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A member of the Imperial court, met rather than fought.
 *
 * <h2>What an envoy is</h2>
 *
 * A wandering trader with a name. One arrives near a player who has climbed far
 * enough up one of the standing meters, stays for a while, deals with anyone
 * who has something to offer, and leaves. Which of the five arrives, and what
 * they are worth dealing with for, follows from {@link EnvoyKind}.
 *
 * <h2>Why the trade is a right-click and not a merchant screen</h2>
 *
 * Vanilla's trading is {@code Merchant}, {@code MerchantOffer} and a container
 * screen, and it buys three things: a browsable list, a price that rises with
 * use, and a UI a player already knows. What it costs is a large API surface
 * for a mod whose trades have exactly one axis — how far up the meter you are.
 *
 * A tribute in the hand and an answer in return says the same thing in one
 * method, reads as a court rather than as a shop, and has the property that
 * matters here: **what you get is decided at the moment you offer**, by the
 * library's reward system, so every mod that has registered rewards is in the
 * pool. A fixed offer list would have been this mod's own item table, drifting
 * against whatever else is installed.
 *
 * The trade-off is real and worth naming: there is no way to see what an envoy
 * will give before you give them something. That is deliberate for a court —
 * you are not shopping, you are being received — but a browsable list is the
 * obvious later addition, and it would sit on top of this rather than replace
 * it.
 *
 * <h2>They do not fight</h2>
 *
 * No attack goal, no target selector, no attack damage attribute. An envoy that
 * fought back would be a mob, and a mob is something a player kills; the whole
 * point of the court is that it is the half of the Empire you can talk to. They
 * are not invulnerable — killing one is possible and costs you standing, which
 * is the correct consequence and needs no special rule.
 */
public class ImperialEnvoy extends PathfinderMob {

    /**
     * Which of the five this is, as a string.
     *
     * A string rather than an ordinal, for the reason ElysiumMob gives about
     * variants: an ordinal is a promise that the declaration order never
     * changes, and reordering an enum would silently turn every saved envoy
     * into a different person.
     */
    private static final EntityDataAccessor<String> KIND =
            SynchedEntityData.defineId(ImperialEnvoy.class, EntityDataSerializers.STRING);

    /**
     * How long an envoy stays before leaving, in ticks.
     *
     * They leave rather than persist because a court that accumulates is a
     * court standing in a field. Twenty minutes is long enough to walk back to
     * a base and return with something to offer, which is the interaction this
     * is for.
     */
    public static final int VISIT_TICKS = 20 * 60 * 20;

    private int ticksHere;

    /** Set when an envoy was placed by the scheduler rather than by a player. */
    private boolean transient_;

    public ImperialEnvoy(EntityType<? extends ImperialEnvoy> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        // Looking at the player is most of what makes something read as a
        // person rather than as furniture, and it is one goal.
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(KIND, EnvoyKind.SENTINEL.id());
    }

    public EnvoyKind getKind() {
        return EnvoyKind.byId(entityData.get(KIND));
    }

    public void setKind(EnvoyKind kind) {
        entityData.set(KIND, kind.id());
        setCustomName(kind.displayName());
        setCustomNameVisible(true);
    }

    public void setTransient(boolean value) {
        this.transient_ = value;
    }

    // ------------------------------------------------------------------
    // The trade
    // ------------------------------------------------------------------

    /**
     * A tribute in the hand, and an answer from the envoy's own office.
     *
     * The three outcomes are all deliberate:
     *
     * <ul>
     *   <li><b>Empty hand</b> — they tell you what they are and go back to
     *       looking at the horizon. A right-click that does nothing at all is
     *       indistinguishable from a broken entity.</li>
     *   <li><b>Not far enough up the meter</b> — they refuse, in their own
     *       words, and keep the tribute nowhere. Refusing silently would read
     *       as the trade being broken rather than as the Code being unimpressed
     *       with you.</li>
     *   <li><b>Accepted</b> — one item leaves your hand, one arrives, and the
     *       meter they read moves a little. Dealing with the Empire is itself a
     *       thing the Empire notices.</li>
     * </ul>
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        EnvoyKind kind = getKind();
        ItemStack offered = player.getItemInHand(hand);

        if (offered.isEmpty()) {
            player.displayClientMessage(kind.displayName().copy()
                    .withStyle(ChatFormatting.GOLD), true);
            return InteractionResult.CONSUME;
        }

        if (!kind.willDealWith(player)) {
            // copy() first: refusal() is typed Component, and withStyle is a
            // MutableComponent method. The displayName() call above had this
            // right; this one did not, and only a real build could tell.
            player.displayClientMessage(kind.refusal().copy()
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        ItemStack payment = ElysiumRewards.roll(kind.rewardTier(), random);
        if (payment.isEmpty()) {
            // No mod offered a reward at this tier. Saying so is better than
            // taking the tribute and giving nothing back, which is what a
            // silent failure here would look like from the other side.
            player.displayClientMessage(
                    Component.translatable("elysiumnpcs.nothing_to_give")
                            .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }

        offered.shrink(1);
        if (!player.addItem(payment)) {
            player.drop(payment, false);
        }

        // Dealing with the court is itself noticed. The Empire's officers make
        // you more interesting to the Empire; the two who stand outside it earn
        // you credit with the Unsworn instead.
        if (kind.meter() == EnvoyKind.Meter.SUSPICION) {
            ElysiumStanding.addSuspicion(player, 3);
        } else {
            ElysiumStanding.addFavor(player, 3);
        }
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() || !transient_) {
            return;
        }
        // A visitor leaves. Counted on the entity rather than scheduled
        // elsewhere so it survives the chunk unloading with the envoy in it.
        if (++ticksHere >= VISIT_TICKS) {
            discard();
        }
    }

    /**
     * A placed envoy is not despawned by distance, because it despawns on its
     * own clock. Leaving it to the vanilla rule would mean an envoy that
     * vanishes the moment the player walks away to fetch something to trade,
     * which is the one moment they must not.
     */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Kind", getKind().id());
        tag.putInt("TicksHere", ticksHere);
        tag.putBoolean("Transient", transient_);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Kind")) {
            setKind(EnvoyKind.byId(tag.getString("Kind")));
        }
        ticksHere = tag.getInt("TicksHere");
        transient_ = tag.getBoolean("Transient");
    }
}
