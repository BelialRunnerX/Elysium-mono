package com.elysium.lib.contract;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.discovery.ElysiumDiscoveries;
import com.elysium.lib.entity.ElysiumFaction;
import com.elysium.lib.standing.ElysiumStanding;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The board: what a player is holding, and what happens to it.
 *
 * <h2>Why expiry is resolved lazily</h2>
 *
 * Nothing ticks contracts. Every read sweeps the held list first, applies the
 * consequence for anything the office has given up on, and returns what is
 * left. That means a player who logs off with two hours on the clock and comes
 * back a week later is told what they missed the moment they ask, with nothing
 * having run in between — and it means there is no per-player timer to get
 * wrong, leak, or forget to cancel on logout.
 *
 * The cost is that the penalty lands when the player next interacts rather than
 * at the stroke of the deadline. For a standing meter that is indistinguishable
 * from the outside, and it buys the whole of the above.
 */
public final class ElysiumContracts {

    private ElysiumContracts() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ElysiumLib.MODID);

    public static final Supplier<AttachmentType<List<ElysiumContract>>> HELD =
            ATTACHMENTS.register("contracts", () ->
                    AttachmentType.builder(
                                    (Supplier<List<ElysiumContract>>) ArrayList::new)
                            .serialize(ElysiumContract.LIST_CODEC)
                            .copyOnDeath()
                            .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    /** The ledger's reason string for contract work. */
    public static final String CAUSE_CONTRACT = "contract";

    /** And for having let one lapse. */
    public static final String CAUSE_LAPSED = "contract_lapsed";

    // ------------------------------------------------------------------
    // Reading, with expiry applied
    // ------------------------------------------------------------------

    /**
     * What this player is holding, after the office has given up on anything
     * out of time.
     *
     * Every other method goes through this, so there is exactly one place an
     * expiry can be missed, and it is the place everything reads.
     */
    public static List<ElysiumContract> held(Player player) {
        if (player == null || player.level().isClientSide()) {
            return List.of();
        }
        long now = player.level().getGameTime();
        List<ElysiumContract> all = player.getData(HELD.get());
        List<ElysiumContract> kept = new ArrayList<>(all.size());
        List<ElysiumContract> lapsed = new ArrayList<>();
        for (ElysiumContract contract : all) {
            if (contract.expired(now)) {
                lapsed.add(contract);
            } else {
                kept.add(contract);
            }
        }
        if (lapsed.isEmpty()) {
            return kept;
        }
        player.setData(HELD.get(), kept);
        for (ElysiumContract contract : lapsed) {
            punish(player, contract);
        }
        return kept;
    }

    /**
     * The standing consequence of letting one lapse.
     *
     * On the issuer's own meter, in the direction that means "we are less
     * pleased with you": the Empire's regard falls, and so does the Unsworn's.
     * Failing an Empire contract does not raise Suspicion — being unreliable is
     * not the same as being seditious, and conflating them would mean a player
     * who missed a delivery gets hunted.
     */
    private static void punish(Player player, ElysiumContract contract) {
        // The same meter either way, and that is not an oversight: Favor is
        // how well you are thought of by whoever you are dealing with, and
        // being unreliable costs that with both offices equally. Which office
        // issued it is carried on the contract so that a lapse can be *named*
        // — "the Empire has written you off" — not so that the two can be
        // punished differently.
        int cost = contract.penalty();
        ElysiumStanding.addFavor(player, -cost, CAUSE_LAPSED);
        player.displayClientMessage(Component.translatable(
                        "elysium.contract.lapsed", describe(contract), cost)
                .withStyle(ChatFormatting.RED), false);
    }

    // ------------------------------------------------------------------
    // Taking one
    // ------------------------------------------------------------------

    /** Whether this player has room for another. */
    public static boolean hasRoom(Player player) {
        return held(player).size() < ElysiumTerms.MAX_HELD;
    }

    /**
     * Write a new contract for this player, from the office they are standing
     * at.
     *
     * The subject is drawn from what the mod already knows the player can get
     * at — a delivery asks for one of this mod's materials, a cull names a
     * faction, a survey asks for a catalogue total above what they already
     * have. That last one matters: a survey the player has already finished
     * would pay out on the spot, which is not a contract.
     */
    public static ElysiumContract offer(Player player, boolean empire,
                                        RandomSource random) {
        ElysiumContractKind[] kinds = ElysiumContractKind.values();
        ElysiumContractKind kind = kinds[random.nextInt(kinds.length)];
        int quantity = ElysiumTerms.quantityFor(
                ElysiumTerms.MIN_QUANTITY
                        + random.nextInt(ElysiumTerms.MAX_QUANTITY
                                - ElysiumTerms.MIN_QUANTITY + 1));

        String target = "";
        int done = 0;
        if (kind == ElysiumContractKind.DELIVER) {
            target = DELIVERABLES[random.nextInt(DELIVERABLES.length)];
        } else if (kind == ElysiumContractKind.CULL) {
            // Never the issuer's own. An office does not pay you to kill its
            // people, and being asked to would be the mod contradicting the
            // standing system in front of the player.
            target = (empire ? ElysiumFaction.UNSWORN : ElysiumFaction.EMPIRE).name();
        } else {
            // A survey counts from where the player already is. The baseline
            // goes in the target field rather than in a field of its own,
            // because it is the contract's subject — "this many more than the
            // two hundred you already had" — and a fourth number on the record
            // would be one every other kind carried and never used.
            //
            // Without it a survey is finished the moment it is taken by anybody
            // who has been playing a while, which is not a contract.
            target = Integer.toString(ElysiumDiscoveries.catalogued(player));
        }

        return new ElysiumContract(kind.id(), target, quantity, done,
                player.level().getGameTime(),
                ElysiumTerms.deadlineFor(kind, quantity),
                ElysiumTerms.payFor(kind, quantity),
                ElysiumTerms.standingFor(kind, quantity), empire);
    }

    /**
     * What a delivery may ask for.
     *
     * Vanilla materials on purpose. An office asking for this mod's own
     * neutronium would be asking a new player for something they have not
     * found yet, and the first contract anybody sees would be one they cannot
     * take.
     */
    public static final String[] DELIVERABLES = {
            "minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:coal",
            "minecraft:leather", "minecraft:wheat", "minecraft:glass",
    };

    /** Take a contract, if there is room. */
    public static boolean take(Player player, ElysiumContract contract) {
        if (!hasRoom(player)) {
            return false;
        }
        List<ElysiumContract> all = new ArrayList<>(held(player));
        all.add(contract);
        player.setData(HELD.get(), all);
        return true;
    }

    // ------------------------------------------------------------------
    // Working on one
    // ------------------------------------------------------------------

    /**
     * Credit a kill against any cull this player is holding.
     *
     * Every matching contract, not just the first. Two culls of the same
     * faction are two contracts about the same work, and paying only one of
     * them would be a rule nobody could guess.
     */
    public static void killed(Player player, ElysiumFaction faction) {
        if (player == null || faction == null || player.level().isClientSide()) {
            return;
        }
        advance(player, ElysiumContractKind.CULL, faction.name(), 1);
    }

    /** Bring a survey up to date with the player's catalogue. */
    public static void surveyed(Player player) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        int catalogued = ElysiumDiscoveries.catalogued(player);
        List<ElysiumContract> all = new ArrayList<>(held(player));
        boolean changed = false;
        for (int i = 0; i < all.size(); i++) {
            ElysiumContract contract = all.get(i);
            if (!ElysiumContractKind.CATALOGUE.id().equals(contract.kind())) {
                continue;
            }
            // Progress is what has been catalogued since the contract was
            // taken: the total now, less the baseline written into the target
            // when it was issued.
            int since = Math.max(0, catalogued - baselineOf(contract));
            if (since > contract.done()) {
                all.set(i, contract.advancedBy(since - contract.done()));
                changed = true;
            }
        }
        if (changed) {
            player.setData(HELD.get(), all);
        }
    }

    /**
     * The catalogue total a survey was written against.
     *
     * Unreadable baselines read as zero, which makes an old or corrupted
     * survey easier rather than impossible — the right way round for something
     * a player cannot fix.
     */
    public static int baselineOf(ElysiumContract contract) {
        try {
            return Math.max(0, Integer.parseInt(contract.target()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static void advance(Player player, ElysiumContractKind kind,
                                String target, int by) {
        List<ElysiumContract> all = new ArrayList<>(held(player));
        boolean changed = false;
        for (int i = 0; i < all.size(); i++) {
            ElysiumContract contract = all.get(i);
            if (!kind.id().equals(contract.kind())
                    || !target.equals(contract.target())
                    || contract.complete()) {
                continue;
            }
            all.set(i, contract.advancedBy(by));
            changed = true;
        }
        if (changed) {
            player.setData(HELD.get(), all);
        }
    }

    // ------------------------------------------------------------------
    // Handing one in
    // ------------------------------------------------------------------

    /**
     * Try to settle every contract this player can settle, at this office.
     *
     * @return how many were settled
     */
    public static int settle(Player player, boolean empire) {
        List<ElysiumContract> all = new ArrayList<>(held(player));
        List<ElysiumContract> kept = new ArrayList<>(all.size());
        int settled = 0;
        for (ElysiumContract contract : all) {
            if (contract.empire() != empire || !ready(player, contract)) {
                kept.add(contract);
                continue;
            }
            collect(player, contract);
            pay(player, contract);
            settled++;
        }
        if (settled > 0) {
            player.setData(HELD.get(), kept);
        }
        return settled;
    }

    /** Whether the work is done, counting the inventory for a delivery. */
    public static boolean ready(Player player, ElysiumContract contract) {
        if (ElysiumContractKind.DELIVER.id().equals(contract.kind())) {
            return countHeld(player, contract.target()) >= contract.quantity();
        }
        return contract.complete();
    }

    private static int countHeld(Player player, String itemId) {
        int found = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && matches(stack, itemId)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    private static boolean matches(ItemStack stack, String itemId) {
        net.minecraft.resources.ResourceLocation key =
                net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stack.getItem());
        return key != null && key.toString().equals(itemId);
    }

    /** Take the goods a delivery was for. Nothing for the other kinds. */
    private static void collect(Player player, ElysiumContract contract) {
        if (!ElysiumContractKind.DELIVER.id().equals(contract.kind())) {
            return;
        }
        int owed = contract.quantity();
        for (ItemStack stack : player.getInventory().items) {
            if (owed <= 0) {
                return;
            }
            if (!stack.isEmpty() && matches(stack, contract.target())) {
                int taken = Math.min(owed, stack.getCount());
                stack.shrink(taken);
                owed -= taken;
            }
        }
    }

    private static void pay(Player player, ElysiumContract contract) {
        if (contract.pay() > 0) {
            ItemStack emeralds = new ItemStack(Items.EMERALD, contract.pay());
            if (!player.getInventory().add(emeralds)) {
                player.drop(emeralds, false);
            }
        }
        ElysiumStanding.addFavor(player, contract.standing(), CAUSE_CONTRACT);
        player.displayClientMessage(Component.translatable(
                        "elysium.contract.settled", describe(contract),
                        contract.pay(), contract.standing())
                .withStyle(ChatFormatting.GREEN), false);
    }

    // ------------------------------------------------------------------
    // Saying what one is
    // ------------------------------------------------------------------

    /** One contract, in a sentence. */
    public static Component describe(ElysiumContract contract) {
        ElysiumContractKind kind = contract.resolve();
        return Component.translatable(
                kind == null ? "elysium.contract.kind.unknown"
                        : kind.translationKey() + ".line",
                contract.quantity(), subject(contract));
    }

    private static Component subject(ElysiumContract contract) {
        if (contract.target().isEmpty()) {
            return Component.empty();
        }
        if (ElysiumContractKind.CATALOGUE.id().equals(contract.kind())) {
            return Component.empty();       // the target is a baseline, not a thing
        }
        if (ElysiumContractKind.CULL.id().equals(contract.kind())) {
            return Component.translatable(
                    "elysium.contract.faction."
                            + contract.target().toLowerCase(java.util.Locale.ROOT));
        }
        return Component.translatable(
                net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .get(net.minecraft.resources.ResourceLocation
                                .parse(contract.target()))
                        .getDescriptionId());
    }
}
