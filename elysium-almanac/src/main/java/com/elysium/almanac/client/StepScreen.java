package com.elysium.almanac.client;

import com.elysium.almanac.network.AlmanacNetwork;
import com.elysium.almanac.registry.AlmanacAttachments;
import com.elysium.almanac.rules.AlmanacRules;
import com.elysium.lib.client.ElysiumChooser;
import com.elysium.lib.client.ElysiumPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * One step: what it asks for, what it is for, and what it pays.
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
 *
 * <h2>The one button</h2>
 *
 * The last row is the reward, and it is the only row in the book that does
 * anything when pressed. A step is finished by doing it; a reward is taken by
 * asking, so that a player who finished a step by accident down a mine is not
 * showered with iron there. The row says what it pays, whether it can be taken
 * yet, and afterwards that it was.
 */
public class StepScreen extends ElysiumChooser {

    private final String chapter;
    private final AlmanacRules.Quest quest;

    /** What the reward row last showed, so a claim answered by the sync redraws it. */
    private boolean shownClaimed;

    public StepScreen(String chapter, AlmanacRules.Quest quest) {
        super(Component.translatable("elysiumalmanac.quest." + quest.id()));
        this.chapter = chapter;
        this.quest = quest;
    }

    private static Player player() {
        return Minecraft.getInstance().player;
    }

    private boolean done() {
        Player player = player();
        return player != null && AlmanacAttachments.has(player, quest.id());
    }

    private boolean claimed() {
        Player player = player();
        return player != null && AlmanacAttachments.claimed(player, quest.id());
    }

    @Override
    protected List<Choice> choices() {
        List<Choice> rows = new ArrayList<>();
        boolean did = done();
        for (AlmanacRules.Task task : quest.tasks()) {
            rows.add(new Choice(label(task), detail(task, did),
                    did ? ElysiumPalette.GOOD : accent(task), false));
        }
        boolean paid = claimed();
        shownClaimed = paid;
        AlmanacRules.Reward reward = AlmanacRules.rewardOf(quest.id());
        Component state = paid ? Component.translatable("elysiumalmanac.reward.claimed")
                : did ? Component.translatable("elysiumalmanac.reward.claim")
                : Component.translatable("elysiumalmanac.reward.later");
        rows.add(new Choice(rewardLabel(reward), state,
                paid ? ElysiumPalette.GOOD : did ? ElysiumPalette.ACCENT : ElysiumPalette.ACCENT_DIM,
                did && !paid));
        return rows;
    }

    private static Component rewardLabel(AlmanacRules.Reward reward) {
        MutableComponent label = Component.translatable("elysiumalmanac.reward",
                String.valueOf(reward.xp()));
        for (AlmanacRules.Give give : reward.gives()) {
            label.append(Component.literal(", " + give.count() + " × "))
                    .append(named(give.item()));
        }
        return label;
    }

    private static Component label(AlmanacRules.Task task) {
        String count = String.valueOf(task.count());
        return switch (task.kind()) {
            case READ -> Component.translatable("elysiumalmanac.task.read");
            case LEVEL -> Component.translatable("elysiumalmanac.task.level", count);
            case WEB -> Component.translatable("elysiumalmanac.task.web", count);
            case HAVE -> Component.translatable("elysiumalmanac.task.have", count, subject(task));
            case PLACE -> task.count() > 1
                    ? Component.translatable("elysiumalmanac.task.place_many", count, subject(task))
                    : Component.translatable("elysiumalmanac.task.place", subject(task));
            case VISIT -> Component.translatable("elysiumalmanac.task.visit", subject(task));
            case SLAY -> Component.translatable("elysiumalmanac.task.slay", count, subject(task));
            case CRAFT -> Component.translatable("elysiumalmanac.task.craft", count, subject(task));
            case BREAK -> Component.translatable("elysiumalmanac.task.break", count, subject(task));
            case USE -> Component.translatable("elysiumalmanac.task.use", subject(task));
            case EAT -> Component.translatable("elysiumalmanac.task.eat", count, subject(task));
            case SMELT -> Component.translatable("elysiumalmanac.task.smelt", count, subject(task));
        };
    }

    /** The thing a task is about: a tag as "any <its name>", an id as what the game calls it. */
    private static Component subject(AlmanacRules.Task task) {
        if (task.tag()) {
            String path = task.subject().substring(task.subject().indexOf(':') + 1);
            path = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
            return Component.translatable("elysiumalmanac.task.any", path);
        }
        if (task.kind() == AlmanacRules.Kind.VISIT) {
            return Component.translatable("elysiumalmanac.world." + task.what()
                    .replace(':', '.'));
        }
        return named(task.what());
    }

    /**
     * What an id is called here.
     *
     * The registries rather than a translation key, so an item another module
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
        if (key != null && BuiltInRegistries.BLOCK.containsKey(key)) {
            return BuiltInRegistries.BLOCK.get(key).getName();
        }
        if (key != null && BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
            return BuiltInRegistries.ENTITY_TYPE.get(key).getDescription();
        }
        return Component.translatable("elysiumalmanac.task.missing", id);
    }

    private static boolean real(AlmanacRules.Task task) {
        if (task.tag() || task.what().isEmpty() || task.kind() == AlmanacRules.Kind.VISIT) {
            return true;
        }
        ResourceLocation key = ResourceLocation.tryParse(task.what());
        return key != null && (BuiltInRegistries.ITEM.containsKey(key)
                || BuiltInRegistries.BLOCK.containsKey(key)
                || BuiltInRegistries.ENTITY_TYPE.containsKey(key));
    }

    private static int accent(AlmanacRules.Task task) {
        return real(task) ? ElysiumPalette.ACCENT : ElysiumPalette.ACCENT_DIM;
    }

    private Component detail(AlmanacRules.Task task, boolean did) {
        if (did) {
            return Component.translatable("elysiumalmanac.quest.done");
        }
        Player player = player();
        if (player != null && task.kind().counted()) {
            int have = task.kind() == AlmanacRules.Kind.SLAY
                    ? AlmanacAttachments.slain(player, task.what())
                    : AlmanacAttachments.count(player, task);
            if (have > 0 && task.count() > 1) {
                return Component.translatable("elysiumalmanac.task.so_far",
                        String.valueOf(Math.min(have, task.count())), String.valueOf(task.count()));
            }
        }
        return Component.translatable("elysiumalmanac.why." + quest.id());
    }

    /** The reward row asks the server; nothing else here does anything when pressed. */
    @Override
    protected void chose(int index) {
        if (index != quest.tasks().size()) {
            return;
        }
        if (done() && !claimed()) {
            PacketDistributor.sendToServer(new AlmanacNetwork.AlmanacClaim(quest.id()));
        }
    }

    /** The sync answers a claim; when it lands, the row changes. */
    @Override
    public void tick() {
        super.tick();
        if (claimed() != shownClaimed) {
            rebuildWidgets();
        }
    }

    @Override
    protected Component status() {
        boolean did = done();
        if (did && !claimed()) {
            return Component.translatable("elysiumalmanac.quest.reward_waiting");
        }
        return did ? Component.translatable("elysiumalmanac.quest.done")
                : Component.translatable("elysiumalmanac.quest.now");
    }

    @Override
    protected int panelWidth() {
        return 300;
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
