package com.elysium.almanac.client;

import com.elysium.almanac.registry.AlmanacAttachments;
import com.elysium.almanac.rules.AlmanacRules;
import com.elysium.lib.client.ElysiumChooser;
import com.elysium.lib.client.ElysiumPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * One chapter: its steps, in the order a player can actually do them.
 *
 * <h2>Sorted by depth, not by declaration</h2>
 *
 * A chapter listed in the order somebody typed it is a chapter a player reads
 * top to bottom and then finds they cannot start the second entry. Sorting by
 * how many steps lie behind each quest puts the ones that are open now at the
 * top, every time, without anybody having to maintain an order by hand.
 */
public class QuestScreen extends ElysiumChooser {

    private final String chapter;
    private final List<AlmanacRules.Quest> shown = new ArrayList<>();

    public QuestScreen(String chapter) {
        super(Component.translatable("elysiumalmanac.chapter." + chapter));
        this.chapter = chapter;
    }

    private static Set<String> done() {
        Player player = Minecraft.getInstance().player;
        return player == null ? Set.of() : AlmanacAttachments.done(player);
    }

    @Override
    protected List<Choice> choices() {
        shown.clear();
        Set<String> finished = done();
        List<AlmanacRules.Quest> quests =
                new ArrayList<>(AlmanacRules.inChapter(chapter));
        quests.sort(Comparator.comparingInt(quest -> AlmanacRules.depthOf(quest.id())));
        List<Choice> rows = new ArrayList<>();
        for (AlmanacRules.Quest quest : quests) {
            shown.add(quest);
            boolean did = finished.contains(quest.id());
            boolean open = did || waiting(quest, finished).isEmpty();
            rows.add(new Choice(
                    Component.translatable("elysiumalmanac.quest." + quest.id()),
                    detail(quest, finished, did),
                    did ? ElysiumPalette.GOOD
                            : open ? ElysiumPalette.ACCENT : ElysiumPalette.ACCENT_DIM,
                    true));
        }
        return rows;
    }

    private static List<String> waiting(AlmanacRules.Quest quest, Set<String> finished) {
        List<String> left = new ArrayList<>();
        for (String need : quest.needs()) {
            if (!finished.contains(need)) {
                left.add(need);
            }
        }
        return left;
    }

    private static Component detail(AlmanacRules.Quest quest, Set<String> finished,
                                    boolean did) {
        if (did) {
            return Component.translatable("elysiumalmanac.quest.done");
        }
        List<String> left = waiting(quest, finished);
        if (!left.isEmpty()) {
            return Component.translatable("elysiumalmanac.quest.after",
                    Component.translatable("elysiumalmanac.quest." + left.get(0)));
        }
        return Component.translatable("elysiumalmanac.quest.now");
    }

    @Override
    protected void chose(int index) {
        if (index >= 0 && index < shown.size()) {
            Minecraft.getInstance().setScreen(new StepScreen(chapter, shown.get(index)));
        }
    }

    @Override
    protected Component status() {
        Set<String> finished = done();
        List<AlmanacRules.Quest> quests = AlmanacRules.inChapter(chapter);
        int did = 0;
        for (AlmanacRules.Quest quest : quests) {
            if (finished.contains(quest.id())) {
                did++;
            }
        }
        return Component.translatable("elysiumalmanac.chapter.progress",
                String.valueOf(did), String.valueOf(quests.size()));
    }

    @Override
    protected Component footer() {
        return Component.translatable("elysiumalmanac.back");
    }

    @Override
    protected void footerPressed() {
        Minecraft.getInstance().setScreen(new ChapterScreen());
    }
}
