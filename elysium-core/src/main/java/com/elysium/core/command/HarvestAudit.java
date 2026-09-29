package com.elysium.core.command;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /elysium audit harvest}: every block the pack registers that needs a
 * tool has a tool tagged to mine it, and none a player can place is one they
 * can never break again.
 *
 * <h2>Why this is asked of the running game</h2>
 *
 * A block that {@code requiresCorrectToolForDrops()} drops nothing unless the
 * tool breaking it is tagged for it (the base game's {@code mineable/*} tags).
 * Leave it out of every tag and a player breaks it, sees it vanish, and holds
 * nothing: the space module wrote its pickaxe tag three times over, each
 * write replacing the last, and forty-seven stones, ores, metal blocks and
 * machines could not be harvested in survival (29 September 2026). Whether a
 * block needs a tool is a line of Java in its properties and whether it is
 * tagged is data from a dozen generators, so the only place both are known at
 * once is the game itself. This asks it, for every block of every module.
 */
public final class HarvestAudit {

    private HarvestAudit() {
    }

    /** Every module of the pack registers under a namespace that begins with this. */
    public static final String OURS = "elysium";

    /** How many offenders are named in the answer before the rest are counted. */
    public static final int NAMED = 12;

    /**
     * Where the whole list goes, under the server's own directory: chat and
     * RCON show a dozen names, and six hundred blocks in twelve modules
     * (29 September 2026) need every name to be fixed by anyone.
     */
    public static final String LIST_FILE = "logs/harvest-audit.txt";

    public static int run(CommandSourceStack source) {
        List<String> untagged = new ArrayList<>();
        List<String> unbreakable = new ArrayList<>();
        int asked = 0;
        int needTools = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().startsWith(OURS)) {
                continue;
            }
            asked++;
            BlockState state = block.defaultBlockState();
            if (state.requiresCorrectToolForDrops()) {
                needTools++;
                if (!(state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_AXE)
                        || state.is(BlockTags.MINEABLE_WITH_SHOVEL) || state.is(BlockTags.MINEABLE_WITH_HOE))) {
                    untagged.add(id.toString());
                }
            }
            // A block a player can place and never break is a block that stays
            // where it was put for ever: a portal the world lights is fine, an
            // item that places bedrock is not.
            Item item = block.asItem();
            if (item != Items.AIR && state.getDestroySpeed(EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO) < 0.0F) {
                unbreakable.add(id.toString());
            }
        }
        final int total = asked;
        final int tooled = needTools;
        writeList(source, untagged, unbreakable);
        if (untagged.isEmpty() && unbreakable.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("elysium.audit.harvest.clean",
                    String.valueOf(total), String.valueOf(tooled)).withStyle(ChatFormatting.GREEN), false);
            return 0;
        }
        if (!untagged.isEmpty()) {
            String named = String.join(", ", untagged.subList(0, Math.min(NAMED, untagged.size())))
                    + (untagged.size() > NAMED ? " ..." : "");
            source.sendFailure(Component.translatable("elysium.audit.harvest.untagged",
                    String.valueOf(untagged.size()), String.valueOf(tooled), named));
        }
        if (!unbreakable.isEmpty()) {
            String named = String.join(", ", unbreakable.subList(0, Math.min(NAMED, unbreakable.size())))
                    + (unbreakable.size() > NAMED ? " ..." : "");
            source.sendFailure(Component.translatable("elysium.audit.harvest.unbreakable",
                    String.valueOf(unbreakable.size()), named));
        }
        return untagged.size() + unbreakable.size();
    }

    /** The whole answer, one block a line, where a person or a script can read it all. */
    private static void writeList(CommandSourceStack source, List<String> untagged, List<String> unbreakable) {
        List<String> lines = new ArrayList<>();
        lines.add("# /elysium audit harvest: " + untagged.size() + " untagged, " + unbreakable.size() + " unbreakable");
        for (String id : untagged) {
            lines.add("untagged " + id);
        }
        for (String id : unbreakable) {
            lines.add("unbreakable " + id);
        }
        Path file = source.getServer().getServerDirectory().resolve(LIST_FILE);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            source.sendFailure(Component.translatable("elysium.audit.harvest.unwritten", file.toString(), e.getMessage()));
        }
    }
}
