package com.elysium.lib.discovery;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.standing.ElysiumStanding;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import com.elysium.lib.research.ElysiumDeed;
import com.elysium.lib.research.ElysiumResearch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What a player has catalogued, and what it paid them.
 *
 * <h2>First discovery only</h2>
 *
 * A thing is worth something the first time and nothing afterwards. §17.3 calls
 * it "first full discovery", and without that rule the scanner is a money
 * printer pointed at a wheat field: the reward has to be for finding, not for
 * clicking.
 *
 * The consequence is that the set has to be persisted and has to survive death,
 * because a catalogue that resets is a catalogue nobody finishes — and worse,
 * one that pays out again, which turns dying into an income.
 *
 * <h2>Why the catalogue records what was found and not where</h2>
 *
 * A discovery is an entity type or a block, not a position. §17.4's cartography
 * — heat maps, markers — is a separate, proposed feature, and storing
 * coordinates here would be inventing half of it in the wrong place. It would
 * also mean a save file that grows with every rabbit a player ever looked at.
 */
public final class ElysiumDiscoveries {

    private ElysiumDiscoveries() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ElysiumLib.MODID);

    /**
     * Everything this player has catalogued, as "kind/id" strings.
     *
     * A set of strings rather than a registry of records, because it has to
     * round-trip through a save without caring whether the mod that added the
     * thing is still installed. A player who removes a mod should not lose
     * their whole catalogue, and should not crash loading it either.
     */
    private static final Codec<Set<String>> CATALOGUE_CODEC =
            Codec.STRING.listOf().xmap(
                    list -> (Set<String>) new LinkedHashSet<>(list),
                    set -> new java.util.ArrayList<>(set));

    public static final Supplier<AttachmentType<Set<String>>> CATALOGUE =
            ATTACHMENTS.register("catalogue", () ->
                    AttachmentType.builder(
                                    (Supplier<Set<String>>) LinkedHashSet::new)
                            .serialize(CATALOGUE_CODEC)
                            .copyOnDeath()
                            .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    /** The catalogue key for one find. */
    public static String keyFor(ElysiumDiscoveryKind kind, String what) {
        return kind.getId() + "/" + what;
    }

    public static boolean known(Player player, ElysiumDiscoveryKind kind, String what) {
        return player.getData(CATALOGUE.get()).contains(keyFor(kind, what));
    }

    public static int catalogued(Player player) {
        return player.getData(CATALOGUE.get()).size();
    }

    /**
     * Record a find and pay for it.
     *
     * @return what happened, so the caller can tell the player without
     *         recomputing any of it
     */
    public static Result record(Player player, ElysiumDiscoveryKind kind,
                                String what, ElysiumFiling filing) {
        String key = keyFor(kind, what);
        Set<String> catalogue = new LinkedHashSet<>(player.getData(CATALOGUE.get()));
        if (!catalogue.add(key)) {
            return Result.ALREADY_KNOWN;
        }
        player.setData(CATALOGUE.get(), catalogue);

        // Catalogued either way. Filing privately still counts, because the
        // catalogue is a record of what this player has seen and the Empire
        // does not get to decide that.
        // Catalogued, and therefore learned from. Credited here rather than in
        // the scanner because this is the one place a find is known to be new —
        // and a research track that paid for re-scanning the same cow would
        // stop describing what a player has done and start describing how
        // patient they are.
        ElysiumDeed deed = ElysiumDeed.forScanOf(kind.getId());
        if (deed != null) {
            ElysiumResearch.credit(player, deed, what);
        }

        // And any survey the player is holding moves with it. Here rather than
        // in the scanner, for the same reason the research credit is here:
        // this is the one place a find is known to be new.
        com.elysium.lib.contract.ElysiumContracts.surveyed(player);

        int pay = filing.payFor(kind);
        int suspicion = filing.suspicionFor(kind);
        int favor = filing.favorFor(kind);

        if (pay > 0) {
            ItemStack emeralds = new ItemStack(Items.EMERALD, pay);
            if (!player.getInventory().add(emeralds)) {
                player.drop(emeralds, false);
            }
        }
        if (suspicion > 0) {
            ElysiumStanding.addSuspicion(player, suspicion, CAUSE_FILED);
        }
        if (favor > 0) {
            ElysiumStanding.addFavor(player, favor, CAUSE_FILED);
        }
        return new Result(true, pay, suspicion, favor);
    }

    /** §14.2's ledger needs a reason string for everything that moves a meter. */
    public static final String CAUSE_FILED = "filed_discovery";

    /** What one scan did. */
    public record Result(boolean isNew, int pay, int suspicion, int favor) {

        static final Result ALREADY_KNOWN = new Result(false, 0, 0, 0);
    }

    /** The registry id of an entity type or block, as a catalogue key. */
    public static String idOf(net.minecraft.world.entity.EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }

    public static String idOf(net.minecraft.world.level.block.Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }
}
