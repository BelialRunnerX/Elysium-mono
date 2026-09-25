package com.elysium.atelier.registry;

import com.elysium.atelier.ElysiumAtelier;
import com.elysium.atelier.rules.AtelierRules;
import com.elysium.atelier.rules.JoinRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One item per block, and a tab that lists them by material.
 *
 * <p>By material and not by shape, because a player who has opened this tab
 * has decided what their house is made of and is now choosing what to put in
 * it — and two hundred blocks sorted the other way is two hundred blocks
 * nobody can find anything in.
 */
public final class AtelierItems {

    private AtelierItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumAtelier.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumAtelier.MODID);

    public static final Map<String, DeferredItem<BlockItem>> ALL = register();

    private static Map<String, DeferredItem<BlockItem>> register() {
        Map<String, DeferredItem<BlockItem>> made = new LinkedHashMap<>();
        for (Map<String, net.neoforged.neoforge.registries.DeferredBlock<
                net.minecraft.world.level.block.Block>> group
                : java.util.List.of(AtelierBlocks.ALL, AtelierBlocks.OPENINGS,
                        AtelierBlocks.PIECES, AtelierBlocks.COURSES,
                        AtelierBlocks.FITTINGS, AtelierBlocks.CLOTH,
                        AtelierBlocks.DIAGONALS)) {
            for (Map.Entry<String, net.neoforged.neoforge.registries.DeferredBlock<
                    net.minecraft.world.level.block.Block>> entry : group.entrySet()) {
                made.put(entry.getKey(), ITEMS.registerSimpleBlockItem(entry.getValue()));
            }
        }
        // A block with no item is a block a player can only place with a
        // command, which in a decoration mod is a block that does not exist.
        int blocks = AtelierBlocks.ALL.size() + AtelierBlocks.OPENINGS.size()
                + AtelierBlocks.PIECES.size() + AtelierBlocks.COURSES.size()
                + AtelierBlocks.FITTINGS.size() + AtelierBlocks.CLOTH.size()
                + AtelierBlocks.DIAGONALS.size();
        if (made.size() != blocks) {
            throw new IllegalStateException("there are " + blocks + " blocks and "
                    + made.size() + " items");
        }
        return Map.copyOf(made);
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "atelier", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumatelier"))
                    .icon(() -> new ItemStack(ALL.get(
                            AtelierRules.MATERIALS.get(0).name() + "_chair").get()))
                    .displayItems((parameters, output) -> {
                        // By material, then shape, then the joining kinds, so
                        // a player choosing what their house is made of finds
                        // everything that can be made of it together.
                        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
                            for (AtelierRules.Shape shape : AtelierRules.SHAPES) {
                                output.accept(ALL.get(material.name() + "_"
                                        + shape.name()).get());
                            }
                            for (String kind : JoinRules.KINDS) {
                                DeferredItem<BlockItem> opening =
                                        ALL.get(material.name() + "_" + kind);
                                // The game already has most wooden openings,
                                // so a wooden material simply has none of ours.
                                if (opening != null) {
                                    output.accept(opening.get());
                                }
                            }
                            for (com.elysium.atelier.rules.ArchRules.Piece piece
                                    : com.elysium.atelier.rules.ArchRules.PIECES) {
                                output.accept(ALL.get(material.name() + "_"
                                        + piece.name()).get());
                            }
                        }
                    })
                    .build());
}
