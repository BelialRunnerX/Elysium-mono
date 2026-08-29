package com.elysium.core.character;

import com.elysium.lib.character.ElysiumPassive;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * A passive with a name and a description, and nothing else decided.
 *
 * Every passive needs those two, and nothing else about the interface is
 * mandatory — so this exists purely so the nine classes and six races below can
 * say what they are in one line and then override only the hooks they use.
 */
public abstract class CorePassive implements ElysiumPassive {

    private final String key;
    private final ChatFormatting colour;

    protected CorePassive(String key, ChatFormatting colour) {
        this.key = "elysium.passive." + key;
        this.colour = colour;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(key).withStyle(colour);
    }

    @Override
    public Component getDescription() {
        return Component.translatable(key + ".desc").withStyle(ChatFormatting.DARK_GRAY);
    }
}
