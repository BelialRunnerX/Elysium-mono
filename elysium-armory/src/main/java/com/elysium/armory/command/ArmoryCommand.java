package com.elysium.armory.command;

import com.elysium.armory.ElysiumArmory;
import com.elysium.armory.item.ArmoryGear;
import com.elysium.armory.item.ArmoryGearLogic;
import com.elysium.armory.item.ArmoryGearParts;
import com.elysium.armory.item.ArmoryGearStats;
import com.elysium.armory.item.ArmoryGearTraits;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /elysium armory properties} — what the thing in your hand is made of.
 *
 * <h2>Why this exists rather than a stat lock</h2>
 *
 * Silent Gear's SG-8 pairs a debug dump with a <em>stat lock</em>: a way to pin
 * a computed number so that rebuilding the piece cannot change it. The dump is
 * here and the lock is deliberately not, for the reason this repository gives
 * every time the question comes up: a lock is a second source of truth for a
 * number the parts already decide, and the two disagree the moment a material
 * is edited. What a lock is actually for is answering "why is this sword's
 * damage 9.4", and that is a question a printout answers better — it shows the
 * working rather than freezing the result.
 *
 * <p>The other half of Silent Gear's answer is already here and has been since
 * the materials landed: {@code MaterialHarness} computes any build outside a
 * game, so "what would this come to" needs neither a world nor a lock.
 *
 * <h2>What it prints</h2>
 *
 * The shape, the tier, every part and the materials in it, every computed
 * property, and every trait with its level. Nothing is summarised and nothing
 * is rounded away: this is the view that makes a disagreement between the data
 * and the item visible, so it says exactly what the item says.
 */
@EventBusSubscriber(modid = ElysiumArmory.MODID)
public final class ArmoryCommand {

    private ArmoryCommand() {
    }

    /** How many properties are printed before the list becomes a wall. */
    public static final int LINES = 24;

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(root());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("elysium").then(Commands.literal("armory")
                .then(Commands.literal("properties")
                        .executes(context -> properties(context.getSource()))));
    }

    private static int properties(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("elysiumarmory.properties.nobody"));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof ArmoryGear gear)) {
            source.sendSuccess(() -> Component.translatable(
                            "elysiumarmory.properties.nothing")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        ArmoryGearParts parts = ArmoryGearLogic.partsOf(held);
        ArmoryGearStats stats = ArmoryGearLogic.statsOf(held);
        ArmoryGearTraits traits = held.getOrDefault(
                com.elysium.armory.registry.ArmoryComponents.TRAITS.get(),
                ArmoryGearTraits.NONE);

        source.sendSuccess(() -> Component.translatable(
                        "elysiumarmory.properties.header", held.getHoverName(),
                        gear.gearType(),
                        stats.tier().isEmpty() ? "-" : stats.tier())
                .withStyle(ChatFormatting.GOLD), false);

        if (parts.entries().isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                            "elysiumarmory.properties.unbuilt")
                    .withStyle(ChatFormatting.RED), false);
        }
        for (ArmoryGearParts.Entry entry : parts.entries()) {
            source.sendSuccess(() -> Component.translatable(
                            "elysiumarmory.properties.part", entry.part(),
                            String.join(", ", entry.materials()))
                    .withStyle(ChatFormatting.WHITE), false);
        }

        int shown = 0;
        for (Map.Entry<String, Double> value : sorted(stats.values())) {
            if (shown++ >= LINES) {
                break;
            }
            source.sendSuccess(() -> Component.translatable(
                            "elysiumarmory.properties.value", value.getKey(),
                            trim(value.getValue()))
                    .withStyle(ChatFormatting.AQUA), false);
        }
        for (Map.Entry<String, Integer> trait : traits.levels().entrySet()) {
            source.sendSuccess(() -> Component.translatable(
                            "elysiumarmory.properties.trait", trait.getKey(),
                            trait.getValue())
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
        source.sendSuccess(() -> Component.translatable(
                        "elysiumarmory.properties.wear",
                        held.getMaxDamage() - held.getDamageValue(), held.getMaxDamage(),
                        ArmoryGearLogic.isBroken(held) ? 1 : 0)
                .withStyle(ChatFormatting.GRAY), false);
        return stats.values().size();
    }

    /** By name, so two runs of the command can be compared line by line. */
    private static List<Map.Entry<String, Double>> sorted(Map<String, Double> values) {
        List<Map.Entry<String, Double>> out = new ArrayList<>(values.entrySet());
        out.sort(Map.Entry.comparingByKey());
        return out;
    }

    /**
     * A number as a player would write it.
     *
     * Two decimals, and the trailing zeros taken off, because "9.4" and
     * "9.40" being different strings is the kind of thing that makes two
     * printouts look different when they are not.
     */
    private static String trim(double value) {
        String text = String.format(java.util.Locale.ROOT, "%.2f", value);
        if (text.contains(".")) {
            text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return text;
    }
}
