package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.character.ElysiumCharacter;
import com.elysium.lib.character.ElysiumXpRules;
import com.elysium.lib.character.ElysiumXpRules.Source;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every ordinary action pays character experience, by {@link ElysiumXpRules}.
 *
 * <h2>One door</h2>
 *
 * Every event here ends in {@link #pay}, and {@link #pay} is the only place
 * this class calls {@link ElysiumCharacter#addXp}: it is where the cap is
 * consulted, where creative and spectator players are turned away (a creative
 * player breaking a mountain is not playing), and where the window turns.
 * The ledger it keeps is per player, per source, and lives only as long as
 * the server: a cap is about a minute, not about a life, and nothing about it
 * is worth writing to disk.
 *
 * <h2>What is not here</h2>
 *
 * Kills and ore, which {@link ElysiumStandingHandler} has paid since the
 * empire arrived and pays still: 4 for a hostile, 14 for a named combatant,
 * 6 for an ore. A strike pays here on the way to the kill; the kill pays there.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumXpHandler {

    private ElysiumXpHandler() {
    }

    /** How often a player's biome is looked at for exploring. */
    public static final int LOOKS_EVERY = 40;

    /** What a player has been paid this window, and where they have been this session. */
    static final class Ledger {
        long windowStart;
        final int[] spent = new int[Source.values().length];
        final Set<String> biomes = new HashSet<>();
    }

    private static final Map<UUID, Ledger> LEDGERS = new HashMap<>();

    private static Ledger ledgerOf(Player player) {
        return LEDGERS.computeIfAbsent(player.getUUID(), id -> new Ledger());
    }

    /**
     * Pay a source's award, as much of it as the window's cap lets through.
     *
     * @return what was paid, for the tests
     */
    public static int pay(Player player, Source source, int wanted) {
        if (!(player instanceof ServerPlayer) || player.isCreative() || player.isSpectator()
                || wanted <= 0) {
            return 0;
        }
        Ledger ledger = ledgerOf(player);
        long now = player.level().getGameTime();
        if (now - ledger.windowStart >= ElysiumXpRules.WINDOW_TICKS) {
            ledger.windowStart = now;
            java.util.Arrays.fill(ledger.spent, 0);
        }
        int at = source.ordinal();
        int allowed = ElysiumXpRules.allow(ledger.spent[at], ElysiumXpRules.cap(source), wanted);
        if (allowed <= 0) {
            return 0;
        }
        ledger.spent[at] += allowed;
        ElysiumCharacter.addXp(player, allowed);
        return allowed;
    }

    private static void pay(Player player, Source source) {
        pay(player, source, ElysiumXpRules.base(source));
    }

    // ------------------------------------------------------------------
    // The actions
    // ------------------------------------------------------------------

    /** Mining, digging, or a harvest, by what the block was. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) {
            return;
        }
        BlockState state = event.getState();
        float speed = state.getDestroySpeed(event.getLevel(), event.getPos());
        if (speed < 0.0F) {
            return;                            // unbreakable: not broken by a player
        }
        boolean ripe = state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
        pay(player, ElysiumXpRules.forBreak(speed, ripe));
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()) {
            pay(player, Source.BUILD);
        }
    }

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player != null && !player.level().isClientSide() && !event.getCrafting().isEmpty()) {
            pay(player, Source.CRAFT);
        }
    }

    @SubscribeEvent
    public static void onSmelt(PlayerEvent.ItemSmeltedEvent event) {
        Player player = event.getEntity();
        if (player != null && !player.level().isClientSide() && !event.getSmelting().isEmpty()) {
            pay(player, Source.SMELT);
        }
    }

    /** Damage dealt to anything that is not a player: a point per four. */
    @SubscribeEvent
    public static void onStrike(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof Player player) || player.level().isClientSide()
                || event.getEntity() instanceof Player) {
            return;
        }
        int points = ElysiumXpRules.forDamage(event.getNewDamage());
        if (points > 0) {
            pay(player, Source.STRIKE, points);
        }
    }

    @SubscribeEvent
    public static void onFish(ItemFishedEvent event) {
        Player player = event.getEntity();
        if (player != null && !player.level().isClientSide()) {
            pay(player, Source.FISH);
        }
    }

    @SubscribeEvent
    public static void onTame(AnimalTameEvent event) {
        Player player = event.getTamer();
        if (player != null && !player.level().isClientSide()) {
            pay(player, Source.TAME);
        }
    }

    @SubscribeEvent
    public static void onBreed(BabyEntitySpawnEvent event) {
        Player player = event.getCausedByPlayer();
        if (player != null && !player.level().isClientSide()) {
            pay(player, Source.BREED);
        }
    }

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        Player player = event.getEntity();
        if (player != null && !player.level().isClientSide()) {
            pay(player, Source.TRADE);
        }
    }

    @SubscribeEvent
    public static void onEnchant(PlayerEnchantItemEvent event) {
        Player player = event.getEntity();
        if (player != null && !player.level().isClientSide()) {
            pay(player, Source.ENCHANT);
        }
    }

    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        ItemStack eaten = event.getItem();
        if (!eaten.isEmpty() && eaten.has(DataComponents.FOOD)) {
            pay(player, Source.EAT);
        }
    }

    /** A biome stood in for the first time this session. */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || player.tickCount % LOOKS_EVERY != 0) {
            return;
        }
        String biome = player.level().getBiome(player.blockPosition()).unwrapKey()
                .map(key -> key.location().toString()).orElse("");
        if (!biome.isEmpty() && ledgerOf(player).biomes.add(biome)) {
            pay(player, Source.EXPLORE);
        }
    }

    @SubscribeEvent
    public static void onLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        LEDGERS.remove(event.getEntity().getUUID());
    }
}
