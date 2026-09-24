package com.elysium.almanac.client;

import com.elysium.almanac.registry.AlmanacAttachments;
import com.elysium.almanac.rules.AlmanacRules;
import com.elysium.lib.client.ElysiumChooser;
import com.elysium.lib.client.ElysiumPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The contents page: every chapter, and how far through it you are.
 *
 * <h2>What it shows and what it hides</h2>
 *
 * A chapter nobody could have started yet is shown greyed rather than hidden,
 * because "there is more, and it is not for you yet" is the single most useful
 * thing a guide can say to somebody on their first evening. Hiding it would
 * make the book look finished when it is not.
 *
 * <p>A chapter about a module that is not installed is a different matter
 * entirely: it is shown with nothing in it, because none of its quests can
 * name anything real. That is not a decision this screen makes — it falls out
 * of the tasks being strings looked up at runtime.
 */
public class ChapterScreen extends ElysiumChooser {

    private final List<AlmanacRules.Chapter> shown = new ArrayList<>();

    public ChapterScreen() {
        super(Component.translatable("elysiumalmanac.book"));
    }

    private static Set<String> done() {
        Player player = Minecraft.getInstance().player;
        return player == null ? Set.of() : AlmanacAttachments.done(player);
    }

    @Override
    protected List<Choice> choices() {
        shown.clear();
        Set<String> finished = done();
        List<Choice> rows = new ArrayList<>();
        for (AlmanacRules.Chapter chapter : AlmanacRules.CHAPTERS) {
            shown.add(chapter);
            float through = AlmanacRules.through(chapter.id(), finished);
            boolean open = AlmanacRules.started(chapter.id(), finished);
            int count = AlmanacRules.inChapter(chapter.id()).size();
            int did = Math.round(through * count);
            Component detail = Component.translatable("elysiumalmanac.chapter.progress",
                    String.valueOf(did), String.valueOf(count));
            int accent = through >= 1.0F ? ElysiumPalette.GOOD
                    : open ? ElysiumPalette.ACCENT : ElysiumPalette.ACCENT_DIM;
            rows.add(new Choice(
                    Component.translatable("elysiumalmanac.chapter." + chapter.id()),
                    detail, accent, open));
        }
        return rows;
    }

    @Override
    protected void chose(int index) {
        if (index < 0 || index >= shown.size()) {
            return;
        }
        Minecraft.getInstance().setScreen(new QuestScreen(shown.get(index).id()));
    }

    @Override
    protected Component status() {
        Set<String> finished = done();
        return Component.translatable("elysiumalmanac.book.status",
                String.valueOf(finished.size()),
                String.valueOf(AlmanacRules.QUESTS.size()));
    }

    @Override
    protected Component nothing() {
        return Component.translatable("elysiumalmanac.book.empty");
    }
}
