package com.elysium.almanac.client;

import com.elysium.almanac.registry.AlmanacAttachments;
import com.elysium.almanac.rules.AlmanacRules;
import com.elysium.lib.client.ElysiumChooser;
import com.elysium.lib.client.ElysiumPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * One step: what it asks for, and what it is for.
 *
 * <h2>Why the tasks are the list</h2>
 *
 * Because "what exactly do I have to do" is the only question a player is
 * asking by the time they have opened a quest, and answering it with a
 * paragraph they have to read is answering a different question. The
 * explanation is one line above; the list below is the answer.
 *
 * <p>A task naming something that does not exist in this install is shown as
 * such rather than hidden, because a chapter about a module somebody removed
 * should say so instead of quietly pretending to be finishable.
 */
public class StepScreen extends ElysiumChooser {

    private final String chapter;
    private final AlmanacRules.Quest quest;

    public StepScreen(String chapter, AlmanacRules.Quest quest) {
        super(Component.translatable("elysiumalmanac.quest." + quest.id()));
        this.chapter = chapter;
        this.quest = quest;
    }

    @Override
    protected List<Choice> choices() {
        List<Choice> rows = new ArrayList<>();
        Player player = Minecraft.getInstance().player;
        boolean did = player != null && AlmanacAttachments.has(player, quest.id());
        for (AlmanacRules.Task task : quest.tasks()) {
            rows.add(new Choice(label(task), detail(task, did),
                    did ? ElysiumPalette.GOOD : accent(task), false));
        }
        return rows;
    }

    private static Component label(AlmanacRules.Task task) {
        if (task.kind() == AlmanacRules.Kind.READ) {
            return Component.translatable("elysiumalmanac.task.read");
        }
        Component what = named(task.what());
        return switch (task.kind()) {
            case HAVE -> Component.translatable("elysiumalmanac.task.have",
                    String.valueOf(task.count()), what);
            case PLACE -> Component.translatable("elysiumalmanac.task.place", what);
            case VISIT -> Component.translatable("elysiumalmanac.task.visit", what);
            default -> what;
        };
    }

    /**
     * What a task's subject is called here.
     *
     * The registry rather than a translation key, so an item another module
     * renamed is named the way that module names it — and something no
     * installed module supplies says so, which is the whole reason a chapter
     * can be about a module somebody has removed.
     */
    private static Component named(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key != null && BuiltInRegistries.ITEM.containsKey(key)) {
            Item item = BuiltInRegistries.ITEM.get(key);
            return new ItemStack(item).getHoverName();
        }
        return Component.translatable("elysiumalmanac.task.missing", id);
    }

    private static int accent(AlmanacRules.Task task) {
        ResourceLocation key = ResourceLocation.tryParse(task.what());
        boolean real = task.kind() != AlmanacRules.Kind.HAVE
                || (key != null && BuiltInRegistries.ITEM.containsKey(key));
        return real ? ElysiumPalette.ACCENT : ElysiumPalette.ACCENT_DIM;
    }

    private Component detail(AlmanacRules.Task task, boolean did) {
        if (did) {
            return Component.translatable("elysiumalmanac.quest.done");
        }
        return Component.translatable("elysiumalmanac.why." + quest.id());
    }

    /**
     * Nothing. A step's rows are what it asks for, not things to pick.
     *
     * The chooser is a list widget as much as a menu, and a page whose rows
     * did something when clicked would be a page a player clicks around on
     * looking for the button that turns the quest in — which is exactly the
     * interaction this book does not have.
     */
    @Override
    protected void chose(int index) {
    }

    @Override
    protected Component status() {
        Player player = Minecraft.getInstance().player;
        boolean did = player != null && AlmanacAttachments.has(player, quest.id());
        return did ? Component.translatable("elysiumalmanac.quest.done")
                : Component.translatable("elysiumalmanac.quest.now");
    }

    @Override
    protected Component footer() {
        return Component.translatable("elysiumalmanac.back");
    }

    @Override
    protected void footerPressed() {
        Minecraft.getInstance().setScreen(new QuestScreen(chapter));
    }
}
