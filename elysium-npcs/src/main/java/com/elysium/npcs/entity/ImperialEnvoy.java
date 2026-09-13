package com.elysium.npcs.entity;

import com.elysium.lib.standing.ElysiumCommission;
import com.elysium.lib.standing.ElysiumPrices;
import com.elysium.lib.standing.ElysiumRewards;
import com.elysium.lib.standing.ElysiumStanding;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

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
 * <h2>The trade is a merchant screen, and what that cost</h2>
 *
 * This used to be a right-click: one item out of your hand, one item back, and
 * the class documentation here argued the case for it — a court receives you,
 * it does not serve you. It also named the missing piece honestly, that "a
 * browsable list is the obvious later addition". This is that addition, and it
 * replaced the exchange rather than sitting on top of it, because two ways to
 * trade with the same entity is one way too many.
 *
 * What the screen buys is the three things vanilla trading always buys: a list
 * you can read before committing, a cost you can see, and a UI every player
 * already knows how to drive.
 *
 * The middle one was not a luxury. The old exchange took <em>whatever</em> was
 * in your hand and took one of it, so the best thing a player could hand over
 * was the cheapest item they owned: a block of dirt bought the same relic as a
 * diamond. There was no price, so there was no decision, and the optimal play
 * was the least interesting one available. A named cost is what makes a trade a
 * trade.
 *
 * <h2>What did not change, and why that was the hard part</h2>
 *
 * The old design's real argument was never about the screen — it was that
 * <b>a fixed offer list would have been this mod's own item table, drifting
 * against whatever else is installed.</b> That objection is correct and it
 * survives the change, so the offers are not a table. Each one is a
 * {@link ElysiumRewards} roll paid for by an {@link ElysiumPrices} roll, both
 * of which every installed mod contributes to. This mod still names no item on
 * either side of the trade.
 *
 * Rolled once when the envoy arrives rather than on every screen open, so the
 * list is stable for the length of a visit: a player who walks home for the
 * price comes back to the offer they went home for. That is the same reason the
 * old code was careful about <em>when</em> it rolled, kept.
 *
 * <h2>What the screen deliberately does not do</h2>
 *
 * No levels, no progress bar, no restocking. An envoy is not a villager with a
 * career — it is here for twenty minutes and then it is gone, and stock that
 * refilled would remove the only pressure the visit has. {@code maxUses} is
 * small for the same reason: the list is what this envoy brought, not a shop
 * front.
 *
 * Standing still gates the whole screen. Below the office's band they refuse in
 * their own words and the window never opens, which keeps the bands meaning
 * exactly what they meant before.
 *
 * <h2>They do not fight</h2>
 *
 * No attack goal, no target selector, no attack damage attribute. An envoy that
 * fought back would be a mob, and a mob is something a player kills; the whole
 * point of the court is that it is the half of the Empire you can talk to. They
 * are not invulnerable — killing one is possible and costs you standing, which
 * is the correct consequence and needs no special rule.
 */
public class ImperialEnvoy extends PathfinderMob implements Merchant {

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

    /**
     * How long a commission runs. Three in-game days.
     *
     * Long enough that it is a project rather than an errand — the whole point
     * is that you go away and do something — and short enough that it cannot be
     * forgotten about and stumbled over a week later. It deliberately outlasts
     * the envoy's own twenty-minute visit, because the contract is with the
     * Court and any envoy will settle it.
     */
    public static final int kCommissionTicks = 20 * 60 * 20 * 3;

    /** How many times the trade-tier quantity a commission asks for. */
    private static final int kCommissionMultiple = 8;

    /** Top-shelf rolls paid on settlement. */
    private static final int kCommissionPayout = 3;

    /** Standing gained for settling, and lost for letting one lapse. */
    private static final int kCommissionStanding = 12;
    private static final int kCommissionFailure = 8;

    private int ticksHere;

    /** Set when an envoy was placed by the scheduler rather than by a player. */
    private boolean transient_;

    /**
     * What this envoy brought, or null until the first player opens the screen.
     *
     * Null rather than an empty list, because "not rolled yet" and "rolled and
     * came back empty" need to be told apart. Rolling lazily is not a style
     * choice: reward and price providers register during mod setup, and an
     * envoy deserialised from a chunk before some other mod has registered
     * would otherwise be stuck carrying an empty list for the rest of its
     * visit. The same lifecycle rule the rest of this project keeps meeting,
     * in its entity-shaped form.
     */
    @Nullable
    private MerchantOffers offers;

    /** Who has the screen open, for the vanilla merchant contract. */
    @Nullable
    private Player tradingPlayer;

    /**
     * The seed the ledger is drawn from. Zero until the first roll.
     *
     * <b>The ledger is persisted as a seed, not as a list of offers.</b>
     *
     * Vanilla saves a villager's trades through {@code MerchantOffers.CODEC},
     * which needs registry-aware ops and writes every item stack out in full.
     * An envoy does not need any of that, because its offers are not authored —
     * they are a pure function of a seed and whatever providers are installed.
     * Storing the seed re-derives the identical ledger on load in one long,
     * and it cannot drift out of step with the format of the things it names.
     *
     * What a seed cannot carry is how much of the ledger has already been
     * bought, so that is saved separately and replayed. See {@link #pendingUses}.
     */
    private long ledgerSeed;

    /**
     * Uses read from the save, waiting for the ledger to be rolled.
     *
     * They cannot be applied at read time: offers are rolled lazily, because
     * providers register during mod setup and an envoy loaded from a chunk may
     * be read before some other mod has registered. So the counts are parked
     * here and applied the moment the ledger exists.
     */
    @Nullable
    private int[] pendingUses;

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
     * Open the court's ledger, if the Code thinks you are worth one.
     *
     * Two outcomes, and the refusal is the one worth keeping careful:
     *
     * <ul>
     *   <li><b>Not far enough up the meter</b> — they refuse, in their own
     *       words, and no window opens. Refusing silently would read as the
     *       entity being broken rather than as the Code being unimpressed with
     *       you, which is why this was a message before the screen existed and
     *       is still a message now.</li>
     *   <li><b>Received</b> — the trade screen opens on what this envoy
     *       brought. Everything after that is vanilla's, including the part
     *       where a player can look without buying.</li>
     * </ul>
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        EnvoyKind kind = getKind();
        // Crouching asks about the ledger of a different kind: §14.5's court
        // commissions. A separate gesture rather than a row in the trade
        // screen, because a commission is not a trade — it is taken now and
        // settled later, and putting it in a window whose whole grammar is
        // "this for that, immediately" would teach the wrong thing about it.
        if (player.isShiftKeyDown()) {
            return commission(player, kind);
        }

        if (!kind.willDealWith(player)) {
            // copy() first: refusal() is typed Component, and withStyle is a
            // MutableComponent method. Only a real build could tell.
            player.displayClientMessage(kind.refusal().copy()
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        MerchantOffers available = getOffers();
        if (available.isEmpty()) {
            // Nothing wanted to pay out at any of this office's tiers. Saying
            // so is better than opening an empty window, which a player cannot
            // tell from a bug.
            player.displayClientMessage(
                    Component.translatable("elysiumnpcs.nothing_to_give")
                            .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }

        setTradingPlayer(player);
        // Level 1 with the progress bar off: the argument is required by the
        // vanilla screen and means nothing here. See showProgressBar().
        openTradingScreen(player, getDisplayName(), 1);
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------
    // Commissions
    // ------------------------------------------------------------------

    /**
     * Take a contract, report on one, or settle it.
     *
     * Only the two offices that deal at the top shelf issue them, which is what
     * makes a commission the "high-standing endgame sink" §19.2 calls it rather
     * than another thing to do on the way up. Any envoy will <em>settle</em>
     * one, because a contract is with the Court and not with the individual who
     * handed it over — and because a system that required you to find the same
     * wandering entity again would be a system nobody finished.
     */
    private InteractionResult commission(Player player, EnvoyKind kind) {
        ElysiumCommission held = ElysiumCommission.get(player);
        long now = level().getGameTime();

        if (held.isNone()) {
            if (!kind.issuesCommissions()) {
                player.displayClientMessage(
                        Component.translatable("elysiumnpcs.commission.not_my_office")
                                .withStyle(ChatFormatting.GRAY), false);
                return InteractionResult.CONSUME;
            }
            if (!kind.willDealWith(player)) {
                player.displayClientMessage(kind.refusal().copy()
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            return issue(player, kind, now);
        }

        if (held.isExpired(now)) {
            // The Court noticed you did not come back. Losing standing is the
            // failure consequence the reference brief asks for, and it is the
            // only thing that makes the deadline mean anything.
            ElysiumCommission.clear(player);
            if (kind.meter() == EnvoyKind.Meter.SUSPICION) {
                ElysiumStanding.addSuspicion(player, -kCommissionFailure);
            } else {
                ElysiumStanding.addFavor(player, -kCommissionFailure);
            }
            player.displayClientMessage(
                    Component.translatable("elysiumnpcs.commission.expired")
                            .withStyle(ChatFormatting.RED), false);
            return InteractionResult.CONSUME;
        }

        int carried = held.carried(player);
        if (carried < held.required()) {
            player.displayClientMessage(
                    Component.translatable("elysiumnpcs.commission.outstanding",
                            held.required() - carried,
                            describe(held),
                            held.remaining(now) / 20L).withStyle(ChatFormatting.GRAY),
                    false);
            return InteractionResult.CONSUME;
        }

        if (!held.collect(player)) {
            return InteractionResult.CONSUME;
        }
        ElysiumCommission.clear(player);

        // The unusual reward §14.5 asks for: several draws at the top shelf,
        // which is more than any single trade row can pay and is the whole
        // reason to take a contract instead of just shopping.
        int paid = 0;
        for (int i = 0; i < kCommissionPayout; i++) {
            ItemStack reward = ElysiumRewards.roll(ElysiumRewards.MAX_TIER, getRandom());
            if (reward.isEmpty()) {
                continue;
            }
            paid++;
            if (!player.addItem(reward)) {
                player.drop(reward, false);
            }
        }
        if (kind.meter() == EnvoyKind.Meter.SUSPICION) {
            ElysiumStanding.addSuspicion(player, kCommissionStanding);
        } else {
            ElysiumStanding.addFavor(player, kCommissionStanding);
        }
        player.displayClientMessage(
                Component.translatable("elysiumnpcs.commission.settled", paid)
                        .withStyle(ChatFormatting.GOLD), false);
        return InteractionResult.CONSUME;
    }

    /** Draw a contract and hand it over. */
    private InteractionResult issue(Player player, EnvoyKind kind, long now) {
        // What it asks for is a price-tier item, in a quantity no trade would
        // ask for. Reusing the price pool rather than adding a third registry
        // keeps the rule the rest of the court runs on: this mod names no item.
        ItemStack want = ElysiumPrices.roll(ElysiumRewards.MAX_TIER, getRandom());
        if (want.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("elysiumnpcs.commission.nothing_wanted")
                            .withStyle(ChatFormatting.GRAY), false);
            return InteractionResult.CONSUME;
        }

        int required = want.getCount() * kCommissionMultiple;
        ElysiumCommission issued = new ElysiumCommission(
                BuiltInRegistries.ITEM.getKey(want.getItem()).toString(),
                required,
                ElysiumRewards.MAX_TIER,
                now,
                now + kCommissionTicks,
                kind.id());
        ElysiumCommission.set(player, issued);

        player.displayClientMessage(kind.displayName().copy()
                .withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(
                Component.translatable("elysiumnpcs.commission.issued",
                        required, describe(issued),
                        kCommissionTicks / 20L).withStyle(ChatFormatting.YELLOW), false);
        return InteractionResult.CONSUME;
    }

    /** The contract's item, named the way the player's inventory names it. */
    private static Component describe(ElysiumCommission commission) {
        return commission.resolve()
                .map(item -> (Component) new ItemStack(item).getHoverName())
                .orElseGet(() -> Component.translatable(
                        "elysiumnpcs.commission.unknown_item"));
    }

    // ------------------------------------------------------------------
    // The ledger
    // ------------------------------------------------------------------

    /**
     * Build what this envoy brought.
     *
     * One row per {@link EnvoyKind#offerCount()}, each at the tier the office
     * assigns it, each a reward bought with a price — both drawn from the
     * library's provider pools so that every installed mod is on both sides of
     * the trade and this mod names no item on either.
     *
     * A tier that nothing will pay out at is skipped rather than filled with a
     * placeholder. An envoy with three of its five rows missing is a thin
     * ledger, which is the truth; a row offering nothing for something is a
     * bug wearing a trade's clothes.
     */
    private MerchantOffers rollOffers() {
        MerchantOffers rolled = new MerchantOffers();
        EnvoyKind kind = getKind();
        // The ledger's own source, seeded once and saved, so that the same
        // envoy re-derives the same offers after a chunk reload. Using the
        // entity's shared random would make the ledger depend on everything
        // else that had asked it for a number first.
        RandomSource ledger = RandomSource.create(ledgerSeed);
        for (int i = 0; i < kind.offerCount(); i++) {
            int tier = kind.tierForOffer(i);

            ItemStack reward = ElysiumRewards.roll(tier, ledger);
            if (reward.isEmpty()) {
                continue;
            }
            ItemStack price = ElysiumPrices.roll(tier, ledger);
            if (price.isEmpty()) {
                continue;
            }

            // maxUses is small and does not restock: this is what the envoy
            // carried here, not a shop front. The zero is villager XP, which
            // this entity has none of, and the 0.0F is the demand-based price
            // multiplier — an envoy's price does not drift with how much the
            // player has already bought, because there is no career to model.
            rolled.add(new MerchantOffer(
                    new ItemCost(price.getItem(), price.getCount()),
                    reward,
                    usesFor(tier),
                    0,
                    0.0F));
        }
        return rolled;
    }

    /**
     * How many times one row can be bought.
     *
     * Fewer at the top: a relic you can buy four of is not a relic. The
     * cheapest shelf is allowed to be a genuine supply run, which is most of
     * what makes the Sentinel — the only envoy that deals with an unnoticed
     * player — worth finding at all.
     */
    private static int usesFor(int tier) {
        return switch (tier) {
            case 3 -> 1;
            case 2 -> 2;
            case 1 -> 4;
            default -> 8;
        };
    }

    @Override
    public MerchantOffers getOffers() {
        if (offers == null) {
            if (ledgerSeed == 0L) {
                // Drawn once, from the entity's own source, and then kept. The
                // guard is not superstition: zero is this field's "unset", so a
                // ledger that legitimately seeded to zero would be re-rolled on
                // every load and the offers would change under the player.
                do {
                    ledgerSeed = getRandom().nextLong();
                } while (ledgerSeed == 0L);
            }
            offers = rollOffers();
            applyPendingUses();
        }
        return offers;
    }

    /**
     * Replay how much of this ledger was already bought before the save.
     *
     * Applied by calling {@code increaseUses} rather than writing the field,
     * because the field has no setter and adding one would mean a mixin into a
     * vanilla class to save four lines here. A row whose count no longer lines
     * up — because a provider was removed and the ledger came back shorter — is
     * simply left alone, which spends a player nothing and is the safe
     * direction to be wrong in.
     */
    private void applyPendingUses() {
        if (pendingUses == null || offers == null) {
            return;
        }
        int shared = Math.min(pendingUses.length, offers.size());
        for (int i = 0; i < shared; i++) {
            MerchantOffer offer = offers.get(i);
            int used = Math.min(pendingUses[i], offer.getMaxUses());
            for (int u = 0; u < used; u++) {
                offer.increaseUses();
            }
        }
        pendingUses = null;
    }

    @Override
    public void overrideOffers(MerchantOffers value) {
        this.offers = value;
    }

    /**
     * Also where a sold-out row is replaced.
     *
     * §14.4 of the specification says court offers are "rerolled after
     * successful trades", and this is where that happens — on the screen
     * closing rather than at the moment of the trade.
     *
     * <h4>Why not in notifyTrade, where the trade actually is</h4>
     *
     * Because the menu is open at that point and is indexing into the very
     * list a reroll would rewrite. {@code MerchantResultSlot} holds the offer
     * the player is mid-transaction with; swapping it underneath is how a
     * trade completes against one offer and pays out another. Vanilla mutates
     * a villager's offers there too, but only by bumping a counter on an offer
     * it keeps — it never replaces the object.
     *
     * Waiting for the close costs nothing the player can perceive: the row
     * they exhausted still reads as sold out for as long as they are looking
     * at it, and the new one is there when they come back. What it buys is
     * that the server never edits a list the client is holding a copy of.
     *
     * <h4>The row, not the ledger</h4>
     *
     * Only rows that actually sold out are redrawn. Rerolling everything after
     * any trade would punish the one thing a browsable list is for — buying the
     * cheap row first and coming back for the expensive one — by moving the
     * expensive one every time you saved up for it.
     */
    @Override
    public void setTradingPlayer(@Nullable Player player) {
        boolean closing = player == null && this.tradingPlayer != null;
        this.tradingPlayer = player;
        if (closing) {
            refreshSoldOut();
        }
    }

    /**
     * Draw a new offer into every exhausted row.
     *
     * A row that cannot be refilled — because nothing will pay out at its tier
     * any more — is left sold out rather than removed. Removing it would
     * renumber every row below it, and the use counts saved against this
     * ledger are positional.
     */
    private void refreshSoldOut() {
        MerchantOffers current = offers;
        if (current == null || level().isClientSide()) {
            return;
        }
        EnvoyKind kind = getKind();
        for (int i = 0; i < current.size(); i++) {
            if (!current.get(i).isOutOfStock()) {
                continue;
            }
            int tier = kind.tierForOffer(i);
            // A fresh draw from the entity's own source, not the ledger seed.
            // The seed reproduces the ledger an envoy ARRIVED with; what
            // replaces a spent row is new, and deriving it from the same seed
            // would hand back the row that was just bought.
            ItemStack reward = ElysiumRewards.roll(tier, getRandom());
            if (reward.isEmpty()) {
                continue;
            }
            ItemStack price = ElysiumPrices.roll(tier, getRandom());
            if (price.isEmpty()) {
                continue;
            }
            current.set(i, new MerchantOffer(
                    new ItemCost(price.getItem(), price.getCount()),
                    reward,
                    usesFor(tier),
                    0,
                    0.0F));
        }
    }

    @Override
    @Nullable
    public Player getTradingPlayer() {
        return tradingPlayer;
    }

    /**
     * A completed trade, and the Empire notices you made it.
     *
     * This is where the old exchange's standing change moved to, unchanged in
     * meaning: the Empire's officers make you more interesting to the Empire,
     * and the two who stand outside the chain of command earn you credit with
     * the Unsworn instead. Doing it here rather than in the interaction is what
     * makes it fire once per purchase rather than once per window opened.
     */
    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        Player player = getTradingPlayer();
        if (player == null) {
            return;
        }
        if (getKind().meter() == EnvoyKind.Meter.SUSPICION) {
            ElysiumStanding.addSuspicion(player, 3);
        } else {
            ElysiumStanding.addFavor(player, 3);
        }
    }

    /**
     * Called as the player moves items in and out of the trade slots.
     *
     * Nothing to do: an envoy has no ambient reaction to being browsed, and the
     * vanilla villager's use of this hook is to play a sound and shake its
     * head, which is a villager's personality rather than a court's.
     */
    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int value) {
    }

    /**
     * No progress bar.
     *
     * The bar shows progress towards the next career level, and an envoy has no
     * career: it arrives, deals for twenty minutes and leaves. Showing an empty
     * bar would advertise a progression that does not exist.
     */
    @Override
    public boolean showProgressBar() {
        return false;
    }

    /**
     * Not {@code .value()}: {@code SoundEvents.VILLAGER_YES} is a plain
     * {@link SoundEvent}, not a holder. Most of the fields around it in that
     * class <em>are</em> holders, which is why the wrong spelling reads as
     * correct — and the whole class is unresolvable to a compiler without
     * Minecraft on the classpath, so it type-checks as an unknown method on an
     * unknown type and says nothing.
     */
    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean isClientSide() {
        return level().isClientSide();
    }

    /**
     * They do not restock.
     *
     * Vanilla's default already says false; it is written out because it is a
     * design decision here rather than an omission. Stock that refilled would
     * take away the only pressure a twenty-minute visit has.
     */
    @Override
    public boolean canRestock() {
        return false;
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
        if (ledgerSeed != 0L) {
            tag.putLong("Ledger", ledgerSeed);
            MerchantOffers current = offers;
            if (current != null) {
                int[] used = new int[current.size()];
                for (int i = 0; i < current.size(); i++) {
                    used[i] = current.get(i).getUses();
                }
                tag.putIntArray("LedgerUses", used);
            }
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Kind")) {
            setKind(EnvoyKind.byId(tag.getString("Kind")));
        }
        ticksHere = tag.getInt("TicksHere");
        transient_ = tag.getBoolean("Transient");
        // The ledger is not rebuilt here. Providers may not have registered
        // yet; getOffers() does it on first use and applies the counts then.
        ledgerSeed = tag.getLong("Ledger");
        pendingUses = tag.contains("LedgerUses") ? tag.getIntArray("LedgerUses") : null;
        offers = null;
    }
}
