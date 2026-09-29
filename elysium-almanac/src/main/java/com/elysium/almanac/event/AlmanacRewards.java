package com.elysium.almanac.event;

import com.elysium.almanac.registry.AlmanacAttachments;
import com.elysium.almanac.rules.AlmanacRules;
import com.elysium.lib.character.ElysiumCharacter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Handing over what a finished step pays.
 *
 * <h2>Once, and only for what was done</h2>
 *
 * The client asks; the server decides. A claim for a step the player has not
 * finished, or has already been paid for, is refused with a reason, and the
 * reward is marked taken <em>before</em> anything is handed over, so two
 * clicks in one tick pay once. Experience goes through the library's one
 * door, {@link ElysiumCharacter#addXp}, so the trinkets that scale experience
 * scale this too.
 *
 * <p>An item no installed module makes is skipped, not a crash: the book
 * survives a module's absence in its rewards as it does in its tasks.
 */
public final class AlmanacRewards {

    private AlmanacRewards() {
    }

    public static final String WHY_UNKNOWN = "elysiumalmanac.claim.unknown";
    public static final String WHY_NOT_DONE = "elysiumalmanac.claim.not_done";
    public static final String WHY_ALREADY = "elysiumalmanac.claim.already";

    /** Pay the reward for a quest. Null when paid; otherwise the key of the reason not. */
    public static String claim(ServerPlayer player, String questId) {
        AlmanacRules.Quest quest = AlmanacRules.quest(questId);
        if (quest == null) {
            return WHY_UNKNOWN;
        }
        if (!AlmanacAttachments.has(player, questId)) {
            return WHY_NOT_DONE;
        }
        if (!AlmanacAttachments.claim(player, questId)) {
            return WHY_ALREADY;
        }
        AlmanacRules.Reward reward = AlmanacRules.rewardOf(questId);
        for (AlmanacRules.Give give : reward.gives()) {
            ResourceLocation key = ResourceLocation.tryParse(give.item());
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                continue;                      // nothing installed makes it
            }
            Item item = BuiltInRegistries.ITEM.get(key);
            int left = give.count();
            while (left > 0) {
                int some = Math.min(left, item.getDefaultMaxStackSize());
                ItemStack stack = new ItemStack(item, some);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                left -= some;
            }
        }
        ElysiumCharacter.addXp(player, reward.xp());
        player.displayClientMessage(Component.translatable("elysiumalmanac.claimed",
                        Component.translatable("elysiumalmanac.quest." + questId),
                        String.valueOf(reward.xp()))
                .withStyle(ChatFormatting.GOLD), false);
        return null;
    }
}
