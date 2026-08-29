package com.elysium.core;

import com.elysium.core.entity.ImperialEnforcer;
import com.elysium.core.entity.UnswornRaider;
import com.elysium.core.item.ElysiumRunes;
import com.elysium.lib.ElysiumHooks;
import com.elysium.lib.character.ElysiumClass;
import com.elysium.lib.character.ElysiumRace;
import com.elysium.lib.entity.ElysiumFaction;
import com.elysium.lib.item.ElysiumRune;
import com.elysium.lib.standing.ElysiumDispatch;
import com.elysium.lib.standing.ElysiumRewards;
import com.elysium.lib.stats.ElysiumStats;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Everything this mod tells the library about itself.
 *
 * <h2>What this class is for</h2>
 *
 * The library ships an engine and no content: it knows how standing accrues,
 * how a dispatch is timed and placed, and how a reward's tier and amount are
 * decided — and it has no items, no mobs and no ore of its own to apply any of
 * that to. Every one of those gaps is a registration, and this is where
 * elysium-core fills them.
 *
 * <b>It is also the worked example.</b> A second Elysium mod adding its own
 * faction, its own ore or its own reward shelf writes a class exactly like this
 * one and calls it from its constructor; nothing here reaches into the library
 * or into this mod in a way another mod could not.
 *
 * <h2>Ordering</h2>
 *
 * Called from {@link Elysium}'s constructor, which is early enough for every
 * registry — they freeze on first read, which happens once the game is running.
 * The races, classes and runes bootstrap first because a reward provider below
 * names a rune, and a rune that has not been registered yet is null.
 */
public final class ElysiumContent {

    private ElysiumContent() {
    }

    /** Per named Imperial Enforcer killed at Hunted. */
    public static final float CROWN_CHANCE = 0.04F;

    public static void register() {
        bootstrapRegistries();
        registerFactions();
        registerDispatchers();
        registerRewards();
        registerOres();

        // The codex is handed out on first join and reopens the character
        // sheet. Supplied rather than assumed: the library falls back to
        // /elysium sheet when no mod offers one.
        ElysiumHooks.setCodex(() -> new ItemStack(Elysium.IMPERIAL_CODEX.get()));
    }

    /**
     * Touching each holder class runs its static registrations.
     *
     * Java only initialises a class when something first uses it, so a table of
     * static {@code register(...)} calls that nobody references never runs at
     * all. These three calls are that reference — the alternative is a subtle
     * bug where the races exist in the source and not in the game.
     */
    private static void bootstrapRegistries() {
        ElysiumStats.bootstrap();
        com.elysium.core.character.ElysiumRaces.bootstrap();
        com.elysium.core.character.ElysiumClasses.bootstrap();
        ElysiumRunes.bootstrap();
    }

    // ------------------------------------------------------------------
    // Who is on whose side
    // ------------------------------------------------------------------

    /**
     * The two mobs this mod adds, and which meter each one moves.
     *
     * Registered after the library's catch-all and therefore consulted before
     * it, so an Imperial Enforcer is Empire rather than merely another hostile.
     * Returning null passes the question along.
     */
    private static void registerFactions() {
        ElysiumFaction.addRule(entity -> {
            if (entity instanceof ImperialEnforcer) {
                return ElysiumFaction.EMPIRE;
            }
            if (entity instanceof UnswornRaider) {
                return ElysiumFaction.UNSWORN;
            }
            return null;
        });

        // Named combatants always pay standing and loot; an ordinary hostile
        // rolls for it.
        ElysiumFaction.addNamedCombatantRule(entity ->
                entity instanceof ImperialEnforcer || entity instanceof UnswornRaider);
    }

    // ------------------------------------------------------------------
    // Who the world sends after you
    // ------------------------------------------------------------------

    /**
     * One dispatcher per faction.
     *
     * The engine owns the timing, the placement, the crowd cap and the dice.
     * All either of these does is build a mob and, once the engine has placed
     * it, equip it for the band — after {@code finalizeSpawn}, which is the
     * whole reason {@code afterPlaced} is a separate hook: the vanilla kit-out
     * would otherwise overwrite the Elysium gear.
     */
    private static void registerDispatchers() {
        ElysiumDispatch.register(new ElysiumDispatch.Dispatcher() {
            @Override
            public ElysiumFaction faction() {
                return ElysiumFaction.EMPIRE;
            }

            @Override
            public Class<? extends Mob> type() {
                return ImperialEnforcer.class;
            }

            @Override
            public Mob create(ServerLevel level, int band) {
                return Elysium.IMPERIAL_ENFORCER.get().create(level);
            }

            @Override
            public void afterPlaced(Mob mob, ServerLevel level, Player player,
                                    BlockPos pos, int band) {
                if (mob instanceof ImperialEnforcer enforcer) {
                    enforcer.equipForBand(band);
                }
            }
        });

        ElysiumDispatch.register(new ElysiumDispatch.Dispatcher() {
            @Override
            public ElysiumFaction faction() {
                return ElysiumFaction.UNSWORN;
            }

            @Override
            public Class<? extends Mob> type() {
                return UnswornRaider.class;
            }

            @Override
            public Mob create(ServerLevel level, int band) {
                return Elysium.UNSWORN_RAIDER.get().create(level);
            }

            @Override
            public void afterPlaced(Mob mob, ServerLevel level, Player player,
                                    BlockPos pos, int band) {
                if (mob instanceof UnswornRaider raider) {
                    raider.equipForBand(band, level.getRandom());
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // What standing pays out
    // ------------------------------------------------------------------

    /**
     * The reward shelves — raw material at the bottom, a rune in the middle, a
     * catalyst or a weapon at the top.
     *
     * Favor picked the tier and Suspicion picked the amount before this is
     * called; all a provider decides is what the item is. Each tier keeps a
     * chance of the tier below it, so the reward still varies once a player is
     * parked at the top — a fixed table stops being a reward and becomes a
     * rate.
     */
    private static void registerRewards() {
        ElysiumRewards.register((tier, random) -> switch (tier) {
            case 3 -> {
                if (random.nextFloat() < 0.12F) {
                    yield new ItemStack(randomWeapon(random));
                }
                yield random.nextFloat() < 0.55F
                        ? new ItemStack(Elysium.ELYSIUM_REFORGE.get())
                        : new ItemStack(randomRune(random));
            }
            case 2 -> random.nextFloat() < 0.60F
                    ? new ItemStack(randomRune(random))
                    : new ItemStack(Elysium.NEUTRONIUM_INGOT.get());
            case 1 -> random.nextFloat() < 0.60F
                    ? new ItemStack(Elysium.NEUTRONIUM_INGOT.get())
                    : new ItemStack(rawMaterial(random));
            default -> new ItemStack(rawMaterial(random));
        });
    }

    private static Item rawMaterial(RandomSource random) {
        return random.nextBoolean()
                ? Elysium.AETHERIUM_INGOT.get()
                : Elysium.VOIDGLASS_INGOT.get();
    }

    private static Item randomRune(RandomSource random) {
        Item[] runes = {
                Elysium.VOIDWARD_RUNE.get(), Elysium.PLASMAFORGE_RUNE.get(),
                Elysium.NEURALSPIKE_RUNE.get(), Elysium.DIMENSIONALSHIFT_RUNE.get(),
                Elysium.KINETICSURGE_RUNE.get(), Elysium.STABILIZER_RUNE.get(),
                Elysium.REFLEX_RUNE.get(), Elysium.BARRIER_RUNE.get(),
                Elysium.PLASMA_CORE_RUNE.get(),
        };
        return runes[random.nextInt(runes.length)];
    }

    private static Item randomWeapon(RandomSource random) {
        Item[] weapons = {
                Elysium.VOIDCUT_BLADE.get(), Elysium.PLASMA_BRAND.get(),
                Elysium.NEURAL_LASH.get(), Elysium.RIFT_EDGE.get(),
                Elysium.KINETIC_MAUL.get(),
        };
        return weapons[random.nextInt(weapons.length)];
    }

    // ------------------------------------------------------------------
    // What the Empire minds you taking
    // ------------------------------------------------------------------

    /**
     * Breaking these earns character experience and Suspicion.
     *
     * Neutronium is the rich one — Singularity-Forged, and the Empire notices.
     * The other two are merely regulated.
     */
    private static void registerOres() {
        // The holders themselves, not their values. A DeferredHolder has no
        // value during mod construction, and calling get() here threw
        // "Trying to access unbound value" on launch. registerOre resolves
        // these the first time a block is broken instead.
        ElysiumHooks.registerOre(Elysium.NEUTRONIUM_ORE, true);
        ElysiumHooks.registerOre(Elysium.AETHERIUM_ORE, false);
        ElysiumHooks.registerOre(Elysium.VOIDGLASS_ORE, false);
    }

    // ------------------------------------------------------------------
    // Referenced so javac keeps the imports honest
    // ------------------------------------------------------------------

    /** @return how many races and classes ended up registered, for the log line */
    public static String summary() {
        return ElysiumRace.REGISTRY.size() + " races, "
                + ElysiumClass.REGISTRY.size() + " classes, "
                + ElysiumRune.REGISTRY.size() + " runes";
    }
}
