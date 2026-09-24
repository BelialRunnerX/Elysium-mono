package com.elysium.core.item;

import com.elysium.lib.discovery.ElysiumDiscoveries;
import com.elysium.lib.discovery.ElysiumDiscoveryKind;
import com.elysium.lib.discovery.ElysiumFiling;
import com.elysium.lib.item.ElysiumComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import com.elysium.lib.research.ElysiumResearch;
import com.elysium.lib.research.ElysiumUnlock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Field Scanner: the item that turns looking at something into a record.
 *
 * <h2>One item, three answers</h2>
 *
 * §17.3 makes filing a repeated choice rather than a one-off: the Empire pays
 * more and notices you, the Unsworn pay less and like you for it, and telling
 * nobody pays nothing. The scanner carries which of those it is set to, and
 * sneaking cycles it.
 *
 * Putting the choice on the tool rather than asking each time is the whole
 * reason this can be a "repeated low-stakes lesson". A prompt per scan would
 * make cataloguing a rabbit a decision with a dialogue box, which is how a
 * small recurring choice becomes an annoyance somebody automates away in their
 * head. A setting a player changes when their situation changes — Marked and
 * lying low, or flush and wanting the money — is the same decision at the right
 * frequency.
 *
 * <h2>What is worth scanning</h2>
 *
 * The kind is read off the thing itself rather than from a list this file
 * keeps: anything alive is fauna, anything in the ores tag is a mineral,
 * anything that grows is flora, anything with a block entity is a structure.
 * A mod that adds a creature gets it catalogued the day it is installed, and
 * nothing here has to be told.
 */
public class ElysiumScannerItem extends Item {

    public ElysiumScannerItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
                .durability(256));
    }

    /**
     * Which office this scanner reports to.
     *
     * Stored as an ordinal in the stack's own tag rather than as a data
     * component, so an old scanner picked up out of a chest reads as
     * {@link ElysiumFiling#EMPIRE} — the first value — instead of failing to
     * parse. The setting is a preference, not a possession; losing it costs a
     * click.
     */
    public static ElysiumFiling filingOf(ItemStack stack) {
        Integer ordinal = stack.get(ElysiumComponents.FILING.get());
        ElysiumFiling[] all = ElysiumFiling.values();
        // floorMod rather than a range check: a hand-edited stack holding a
        // negative or enormous value reads as a real option instead of
        // throwing in the middle of a tooltip.
        return all[Math.floorMod(ordinal == null ? 0 : ordinal, all.length)];
    }

    private static void setFiling(ItemStack stack, ElysiumFiling filing) {
        stack.set(ElysiumComponents.FILING.get(), filing.ordinal());
    }

    /** Sneak-use in the air cycles where discoveries are reported. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(held);
        }
        if (!level.isClientSide()) {
            ElysiumFiling next = filingOf(held).next();
            setFiling(held, next);
            player.displayClientMessage(Component.translatable(
                    "elysium.scanner.filing_set", next.getDisplayName()), true);
        }
        return InteractionResultHolder.success(held);
    }

    /** Scanning a creature. */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target,
                                                  InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        scan(player, stack, ElysiumDiscoveryKind.FAUNA,
                ElysiumDiscoveries.idOf(target.getType()),
                target.getType().getDescription(),
                taxonomy(player, target));
        return InteractionResult.CONSUME;
    }

    /** Scanning a block. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockState state = level.getBlockState(context.getClickedPos());
        ElysiumDiscoveryKind kind = kindOf(state);
        if (kind == null) {
            player.displayClientMessage(Component.translatable(
                            "elysium.scanner.nothing")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return InteractionResult.CONSUME;
        }
        scan(player, context.getItemInHand(), kind,
                ElysiumDiscoveries.idOf(state.getBlock()),
                state.getBlock().getName(),
                survey(player, kind, context.getClickedPos().getY()));
        return InteractionResult.CONSUME;
    }

    /**
     * What kind of discovery a block is, or null if it is not one.
     *
     * Read from tags and from what the block is, in order of specificity. The
     * order matters: a block entity in a cave is a structure rather than a
     * mineral, and an ore is a mineral even though it is also stone.
     */
    public static ElysiumDiscoveryKind kindOf(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof EntityBlock) {
            return ElysiumDiscoveryKind.STRUCTURE;
        }
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS)
                || state.is(BlockTags.SAPLINGS)) {
            return ElysiumDiscoveryKind.FLORA;
        }
        // The vanilla ore tags, plus whatever a mod has added to them.
        if (state.is(net.minecraft.tags.BlockTags.COAL_ORES)
                || state.is(net.minecraft.tags.BlockTags.IRON_ORES)
                || state.is(net.minecraft.tags.BlockTags.COPPER_ORES)
                || state.is(net.minecraft.tags.BlockTags.GOLD_ORES)
                || state.is(net.minecraft.tags.BlockTags.REDSTONE_ORES)
                || state.is(net.minecraft.tags.BlockTags.LAPIS_ORES)
                || state.is(net.minecraft.tags.BlockTags.DIAMOND_ORES)
                || state.is(net.minecraft.tags.BlockTags.EMERALD_ORES)) {
            return ElysiumDiscoveryKind.MINERAL;
        }
        return null;
    }

    /**
     * Record one find, pay for it, and say what happened.
     *
     * The message is the feature. A player who is told "catalogued, +5 emeralds,
     * +1 Suspicion" learns the whole of §17.3 in one line and can decide
     * differently next time; one who is told nothing learns that the scanner
     * sometimes makes a noise.
     */
    private void scan(Player player, ItemStack stack, ElysiumDiscoveryKind kind,
                      String what, Component name, Component detail) {
        ElysiumFiling filing = filingOf(stack);
        ElysiumDiscoveries.Result result =
                ElysiumDiscoveries.record(player, kind, what, filing);

        if (!result.isNew()) {
            player.displayClientMessage(Component.translatable(
                            "elysium.scanner.known", name)
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }

        // Only a new find costs the scanner anything. Re-scanning a known thing
        // is free, so a player checking whether they have already catalogued
        // something is not punished for asking.
        stack.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.4F);

        player.displayClientMessage(Component.translatable(
                        "elysium.scanner.found", name, kind.getDisplayName())
                .withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(Component.translatable(
                        "elysium.scanner.filed", filing.getDisplayName(),
                        result.pay(), result.suspicion(), result.favor())
                .withStyle(filing.getColour()), false);
        if (detail != null) {
            player.displayClientMessage(
                    detail.copy().withStyle(ChatFormatting.GRAY), false);
        }
    }

    // ------------------------------------------------------------------
    // What research buys, here
    // ------------------------------------------------------------------
    //
    // Two extra lines, each behind a research band. Both are information the
    // scanner could always have printed and deliberately did not: the point of
    // the research tracks is that a player who has done the work reads more off
    // the same instrument, not that they carry a better one.
    //
    // Both return null when unearned, and null prints nothing. A player who has
    // not studied geology is not told that there is a line they are missing —
    // the readout is simply shorter, and gets longer one day.

    /** Geology: where this vein sits, for somebody who knows what that means. */
    private static Component survey(Player player, ElysiumDiscoveryKind kind, int y) {
        if (kind != ElysiumDiscoveryKind.MINERAL
                || !ElysiumResearch.has(player, ElysiumUnlock.SURVEY_DEPTH)) {
            return null;
        }
        return Component.translatable("elysium.scanner.survey", y);
    }

    /**
     * Xenobiology: what this creature is and how much of it there is.
     *
     * Health and category, read off the creature itself rather than from a
     * table, so a mob from any mod answers on the day it is installed — the
     * same rule the scanner already follows for deciding what kind of thing it
     * is looking at.
     */
    private static Component taxonomy(Player player, LivingEntity target) {
        if (!ElysiumResearch.has(player, ElysiumUnlock.FIELD_TAXONOMY)) {
            return null;
        }
        return Component.translatable("elysium.scanner.taxonomy",
                Component.translatable("elysium.mobcategory."
                        + target.getType().getCategory().getName()),
                (int) target.getMaxHealth());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("elysium.scanner.reports_to",
                filingOf(stack).getDisplayName()));
        tooltip.add(Component.translatable("elysium.scanner.cycle")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
