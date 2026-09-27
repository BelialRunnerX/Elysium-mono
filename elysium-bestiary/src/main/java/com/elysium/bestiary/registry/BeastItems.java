package com.elysium.bestiary.registry;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.rules.BeastRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/** One spawn egg per beast, and a tab to find them in. */
public final class BeastItems {

    private BeastItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumBestiary.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumBestiary.MODID);

    /** The two colours of each egg, one per family and one per tier. */
    private static final Map<String, Integer> FAMILY_COLOUR = Map.of(
            BeastRules.PROWLER, 0x6B4A7A,
            BeastRules.BRUTE, 0x7A5230,
            BeastRules.WIGHT, 0x3E5A62);

    private static final int[] TIER_COLOUR = {0xC8BEE0, 0x9A86C8, 0x6E56A8};

    public static final Map<String, DeferredItem<DeferredSpawnEggItem>> EGGS = register();

    private static Map<String, DeferredItem<DeferredSpawnEggItem>> register() {
        Map<String, DeferredItem<DeferredSpawnEggItem>> made = new LinkedHashMap<>();
        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            int background = FAMILY_COLOUR.getOrDefault(beast.family(), 0x555555);
            int highlight = TIER_COLOUR[Math.min(TIER_COLOUR.length - 1,
                    Math.max(0, beast.tier() - 1))];
            made.put(beast.id(), ITEMS.register(beast.id() + "_spawn_egg",
                    () -> new DeferredSpawnEggItem(
                            () -> BeastEntities.ALL.get(beast.id()).get(),
                            background, highlight, new Item.Properties())));
        }
        // Same count, same reason: an egg missing is a beast a player cannot
        // put in front of themselves to look at, and nothing else would say so.
        if (made.size() != BeastRules.BEASTS.size()) {
            throw new IllegalStateException("the bestiary has "
                    + BeastRules.BEASTS.size() + " beasts and " + made.size()
                    + " spawn egg(s) were registered");
        }
        return Map.copyOf(made);
    }

    /**
     * One egg per denizen, court member, kindred and warded creature, each
     * roster counted on its own. These four rosters shipped with an egg's
     * model and name and no egg, so 127 creatures could not be put in front
     * of a player to look at (the catalogue test, 27 September 2026).
     */
    public static final Map<String, DeferredItem<DeferredSpawnEggItem>> DENIZEN_EGGS =
            registerRoster("denizen", com.elysium.bestiary.rules.DenizenRules.DENIZENS.stream()
                    .map(com.elysium.bestiary.rules.DenizenRules.Denizen::id).toList(),
                    id -> com.elysium.bestiary.registry.DenizenEntities.ALL.get(id).get(), 0x4E5A3C, 0xC2B280);
    public static final Map<String, DeferredItem<DeferredSpawnEggItem>> COURT_EGGS =
            registerRoster("court", com.elysium.bestiary.rules.CourtRules.MEMBERS.stream()
                    .map(com.elysium.bestiary.rules.CourtRules.Member::id).toList(),
                    id -> com.elysium.bestiary.registry.CourtEntities.ALL.get(id).get(), 0x3A2E5C, 0xE0C060);
    public static final Map<String, DeferredItem<DeferredSpawnEggItem>> KIN_EGGS =
            registerRoster("kindred", com.elysium.bestiary.rules.KindredRules.KINDRED.stream()
                    .map(com.elysium.bestiary.rules.KindredRules.Kin::id).toList(),
                    id -> com.elysium.bestiary.registry.KinEntities.ALL.get(id).get(), 0x5C3A2E, 0xD08A60);
    public static final Map<String, DeferredItem<DeferredSpawnEggItem>> WARD_EGGS =
            registerRoster("warded", com.elysium.bestiary.rules.WardRules.WARDED.stream()
                    .map(com.elysium.bestiary.rules.WardRules.Warded::id).toList(),
                    id -> com.elysium.bestiary.registry.WardEntities.ALL.get(id).get(), 0x2E4A5C, 0x9AD0E0);

    private static Map<String, DeferredItem<DeferredSpawnEggItem>> registerRoster(
            String roster, java.util.List<String> ids,
            java.util.function.Function<String, net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>> type,
            int background, int highlight) {
        Map<String, DeferredItem<DeferredSpawnEggItem>> made = new LinkedHashMap<>();
        for (String id : ids) {
            made.put(id, ITEMS.register(id + "_spawn_egg",
                    () -> new DeferredSpawnEggItem(() -> type.apply(id), background, highlight,
                            new Item.Properties())));
        }
        if (made.size() != ids.size()) {
            throw new IllegalStateException("the " + roster + " roster has " + ids.size()
                    + " creatures and " + made.size() + " spawn egg(s) were registered");
        }
        return Map.copyOf(made);
    }

    /** One egg per companion, counted on its own for the same reason. */
    public static final Map<String, DeferredItem<DeferredSpawnEggItem>>
            COMPANION_EGGS = registerCompanions();

    private static Map<String, DeferredItem<DeferredSpawnEggItem>>
            registerCompanions() {
        Map<String, DeferredItem<DeferredSpawnEggItem>> made = new LinkedHashMap<>();
        for (com.elysium.bestiary.rules.CompanionRules.Companion one
                : com.elysium.bestiary.rules.CompanionRules.COMPANIONS) {
            made.put(one.id(), ITEMS.register(one.id() + "_spawn_egg",
                    () -> new DeferredSpawnEggItem(
                            () -> com.elysium.bestiary.registry.CompanionEntities
                                    .ALL.get(one.id()).get(),
                            0x6E7F5C, 0xD6C48A, new Item.Properties())));
        }
        if (made.size()
                != com.elysium.bestiary.rules.CompanionRules.COMPANIONS.size()) {
            throw new IllegalStateException("the rules name "
                    + com.elysium.bestiary.rules.CompanionRules.COMPANIONS.size()
                    + " companion(s) and " + made.size()
                    + " spawn egg(s) were registered");
        }
        return Map.copyOf(made);
    }

    /**
     * One egg per myth, on the same register as the beasts'.
     *
     * <p>Their own map rather than mixed into the beasts', because the two
     * rosters are counted separately and a count that covers both is a count
     * that passes while one of them is half empty.
     */
    public static final Map<String, DeferredItem<DeferredSpawnEggItem>> MYTH_EGGS =
            registerMyths();

    private static Map<String, DeferredItem<DeferredSpawnEggItem>> registerMyths() {
        Map<String, DeferredItem<DeferredSpawnEggItem>> made = new LinkedHashMap<>();
        for (com.elysium.bestiary.rules.MythRules.Myth myth
                : com.elysium.bestiary.rules.MythRules.MYTHS) {
            int background = 0x2B2B33;
            int highlight = TIER_COLOUR[Math.min(TIER_COLOUR.length - 1,
                    Math.max(0, myth.tier() - 1))];
            made.put(myth.id(), ITEMS.register(myth.id() + "_spawn_egg",
                    () -> new DeferredSpawnEggItem(
                            () -> com.elysium.bestiary.registry.MythEntities.ALL
                                    .get(myth.id()).get(),
                            background, highlight, new Item.Properties())));
        }
        if (made.size() != com.elysium.bestiary.rules.MythRules.MYTHS.size()) {
            throw new IllegalStateException("the rules name "
                    + com.elysium.bestiary.rules.MythRules.MYTHS.size()
                    + " myth(s) and " + made.size()
                    + " spawn egg(s) were registered");
        }
        return Map.copyOf(made);
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "bestiary", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumbestiary"))
                    .icon(() -> new ItemStack(EGGS.get(
                            BeastRules.BEASTS.get(0).id()).get()))
                    .displayItems((parameters, output) -> {
                        for (BeastRules.Beast beast : BeastRules.BEASTS) {
                            output.accept(EGGS.get(beast.id()).get());
                        }
                        for (com.elysium.bestiary.rules.MythRules.Myth myth
                                : com.elysium.bestiary.rules.MythRules.MYTHS) {
                            output.accept(MYTH_EGGS.get(myth.id()).get());
                        }
                        for (com.elysium.bestiary.rules.CompanionRules.Companion one
                                : com.elysium.bestiary.rules.CompanionRules.COMPANIONS) {
                            output.accept(COMPANION_EGGS.get(one.id()).get());
                        }
                    })
                    .build());
}
