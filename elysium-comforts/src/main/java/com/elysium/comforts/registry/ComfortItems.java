package com.elysium.comforts.registry;

import com.elysium.comforts.ElysiumComforts;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The things you carry: a crate, the post, and every block this module makes.
 *
 * <p>Counted rather than listed, at the bottom of {@link #rests()}. A pack that
 * registers thirty-one of thirty-two blocks works until somebody dyes the
 * thirty-second, and that is a month later and a bug report nobody can
 * reproduce. Counting turns it into a game that does not start.
 */
public final class ComfortItems {

    private ComfortItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumComforts.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumComforts.MODID);

    public static final DeferredItem<BlockItem> GRAVE =
            ITEMS.registerSimpleBlockItem(ComfortBlocks.GRAVE);

    public static final DeferredItem<com.elysium.comforts.item.TraderItem> TRADER =
            ITEMS.register("trader_crate", () ->
                    new com.elysium.comforts.item.TraderItem(
                            new net.minecraft.world.item.Item.Properties()));

    /** One block item per place to sleep, and the rope. */
    public static final java.util.Map<String, DeferredItem<BlockItem>> RESTS = rests();

    private static java.util.Map<String, DeferredItem<BlockItem>> rests() {
        java.util.Map<String, DeferredItem<BlockItem>> made =
                new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, ?> one
                : ComfortBlocks.RESTS.entrySet()) {
            made.put(one.getKey(), ITEMS.registerSimpleBlockItem(
                    ComfortBlocks.RESTS.get(one.getKey())));
        }
        made.put(com.elysium.comforts.rules.RestRules.ROPE,
                ITEMS.registerSimpleBlockItem(ComfortBlocks.ROPE));
        for (String one : ComfortBlocks.CHIMES.keySet()) {
            made.put(one, ITEMS.registerSimpleBlockItem(ComfortBlocks.CHIMES.get(one)));
        }
        for (String one : ComfortBlocks.STATIONS.keySet()) {
            made.put(one, ITEMS.registerSimpleBlockItem(ComfortBlocks.STATIONS.get(one)));
        }
        for (String one : ComfortBlocks.PIGEONHOLES.keySet()) {
            made.put(one, ITEMS.registerSimpleBlockItem(
                    ComfortBlocks.PIGEONHOLES.get(one)));
        }
        made.put("mailbox", ITEMS.registerSimpleBlockItem(ComfortBlocks.MAILBOX));
        for (String one : ComfortBlocks.FITTINGS.keySet()) {
            made.put(one, ITEMS.registerSimpleBlockItem(ComfortBlocks.FITTINGS.get(one)));
        }
        int wanted = com.elysium.comforts.rules.RestRules.roster().size()
                + com.elysium.comforts.rules.ChimeRules.roster().size()
                + com.elysium.comforts.rules.StationRules.STATIONS.size()
                + com.elysium.comforts.rules.PostRules.pigeonholes().size()
                + com.elysium.comforts.rules.LightRules.FITTINGS.size()
                - com.elysium.comforts.rules.LightRules.ALREADY.size()
                + 1;
        if (made.size() != wanted) {
            throw new IllegalStateException("the rules ask for " + wanted
                    + " thing(s) to carry and " + made.size() + " were registered");
        }
        return java.util.Map.copyOf(made);
    }

        /**
     * Every kind of mail, walked out of the rules.
     *
     * <p>Including the two that are never posted — the empty box you fill and
     * the lost mail that turns up when something did not arrive. Both are items
     * a player holds, so both are registered; neither costs postage, and
     * {@link com.elysium.comforts.rules.PostRules#faultsInPosted} is what holds
     * those two facts together.
     */
    public static final java.util.Map<String,
            DeferredItem<com.elysium.comforts.item.MailItem>> MAIL = mail();

    private static java.util.Map<String,
            DeferredItem<com.elysium.comforts.item.MailItem>> mail() {
        java.util.Map<String, DeferredItem<com.elysium.comforts.item.MailItem>> made =
                new java.util.LinkedHashMap<>();
        for (com.elysium.comforts.rules.PostRules.Mail one
                : com.elysium.comforts.rules.PostRules.MAIL) {
            made.put(one.id(), ITEMS.register(one.id(),
                    () -> new com.elysium.comforts.item.MailItem(
                            new net.minecraft.world.item.Item.Properties(), one.id())));
        }
        if (made.size() != com.elysium.comforts.rules.PostRules.MAIL.size()) {
            throw new IllegalStateException("the post has "
                    + com.elysium.comforts.rules.PostRules.MAIL.size()
                    + " kind(s) of mail and " + made.size() + " were registered");
        }
        return java.util.Map.copyOf(made);
    }

    /** The card a name goes on. */
    public static final DeferredItem<com.elysium.comforts.item.AddressTagItem>
            ADDRESS_TAG = ITEMS.register("address_tag", () ->
            new com.elysium.comforts.item.AddressTagItem(
                    new net.minecraft.world.item.Item.Properties()));

    /** What presses a symbol into the wax. */
    public static final DeferredItem<net.minecraft.world.item.Item> SEAL_STAMP =
            ITEMS.register("seal_stamp", () -> new net.minecraft.world.item.Item(
                    new net.minecraft.world.item.Item.Properties().stacksTo(1)));

    /** One egg per bird, so a creative player can find them. */
    public static final java.util.Map<String, DeferredItem<
            net.neoforged.neoforge.common.DeferredSpawnEggItem>> EGGS = eggs();

    private static java.util.Map<String, DeferredItem<
            net.neoforged.neoforge.common.DeferredSpawnEggItem>> eggs() {
        java.util.Map<String, DeferredItem<
                net.neoforged.neoforge.common.DeferredSpawnEggItem>> made =
                new java.util.LinkedHashMap<>();
        for (com.elysium.comforts.rules.PostRules.Bird bird
                : com.elysium.comforts.rules.PostRules.BIRDS) {
            made.put(bird.id(), ITEMS.register(bird.id() + "_spawn_egg", () ->
                    new net.neoforged.neoforge.common.DeferredSpawnEggItem(
                            () -> ComfortEntities.ALL.get(bird.id()).get(),
                            0x9A9A9A, 0x3F3F3F,
                            new net.minecraft.world.item.Item.Properties())));
        }
        if (made.size() != com.elysium.comforts.rules.PostRules.BIRDS.size()) {
            throw new IllegalStateException("the post has "
                    + com.elysium.comforts.rules.PostRules.BIRDS.size()
                    + " bird(s) and " + made.size() + " egg(s) were registered");
        }
        return java.util.Map.copyOf(made);
    }

public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "comforts", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumcomforts"))
                    .icon(() -> new ItemStack(GRAVE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(GRAVE.get());
                        output.accept(TRADER.get());
                        for (DeferredItem<BlockItem> one : RESTS.values()) {
                            output.accept(one.get());
                        }
                        for (DeferredItem<com.elysium.comforts.item.MailItem> one
                                : MAIL.values()) {
                            output.accept(one.get());
                        }
                        output.accept(ADDRESS_TAG.get());
                        output.accept(SEAL_STAMP.get());
                        for (DeferredItem<net.neoforged.neoforge.common
                                .DeferredSpawnEggItem> one : EGGS.values()) {
                            output.accept(one.get());
                        }
                    })
                    .build());
}