package com.elysium.lib.effect;

import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a stack does, gathered from everything attached to it.
 *
 * <h2>The seam</h2>
 *
 * Every handler asks one question — "what does this stack have of this kind of
 * effect, and at what strength" — and it used to be asked of traits alone.
 * Affixes, gems and runes are the same question asked of a different
 * attachment, so the question is asked here and the attachments answer.
 *
 * <p>A module registers a {@link Source} at load: the armory registers the
 * traits a piece was built with, the library registers the affixes rolled onto
 * it. Neither knows the other exists, and a handler written once acts on both.
 *
 * <h2>Levels</h2>
 *
 * An {@link Active} carries the level of whatever granted it, already capped at
 * what that thing was written for. Handlers multiply by it and should not
 * otherwise care where it came from — a bleed from a trait and a bleed from an
 * affix are the same bleed.
 */
public final class ElysiumItemEffects {

    private ElysiumItemEffects() {
    }

    /** One effect, with the level of the thing that carries it. */
    public record Active(String source, int level, JsonObject effect) {

        public double number(String key, double fallback) {
            return effect.has(key) ? effect.get(key).getAsDouble() : fallback;
        }

        public String text(String key, String fallback) {
            return effect.has(key) ? effect.get(key).getAsString() : fallback;
        }

        public boolean flag(String key, boolean fallback) {
            return effect.has(key) ? effect.get(key).getAsBoolean() : fallback;
        }
    }

    /** Something that can be attached to an item and state effects. */
    public interface Source {

        /** Every effect of one kind this attachment puts on this stack. */
        List<Active> effects(ItemStack stack, String kind);

        /**
         * The strength of one named thing on this stack — the level of a trait,
         * the roll of an affix — or zero if this attachment does not carry it.
         *
         * A set bonus is the case that needs it: "three pieces with this trait"
         * has to be answerable about a piece other than the one whose effect is
         * being applied. An attachment that has nothing to say answers zero.
         */
        default int level(ItemStack stack, String name) {
            return 0;
        }
    }

    private static final Map<String, Source> SOURCES = new LinkedHashMap<>();

    /**
     * Registers an attachment, replacing one of the same name.
     *
     * Named rather than anonymous so that registering twice — which is what a
     * reload or a mistaken second call looks like — replaces rather than
     * doubles every effect the attachment grants.
     */
    public static void register(String name, Source source) {
        SOURCES.put(name, source);
    }

    /** The attachments registered, in registration order. */
    public static List<String> sources() {
        return List.copyOf(SOURCES.keySet());
    }

    /** Every effect of one kind on this stack, from every attachment. */
    public static List<Active> of(ItemStack stack, String kind) {
        if (stack.isEmpty() || SOURCES.isEmpty()) {
            return List.of();
        }
        List<Active> out = new ArrayList<>();
        for (Source source : SOURCES.values()) {
            out.addAll(source.effects(stack, kind));
        }
        return out;
    }

    /**
     * The strength of one named thing on this stack, from whichever attachment
     * carries it. Zero when nothing does.
     */
    public static int level(ItemStack stack, String name) {
        int best = 0;
        for (Source source : SOURCES.values()) {
            best = Math.max(best, source.level(stack, name));
        }
        return best;
    }

    /** Whether the stack carries any effect of a kind: the cheap check a tick can afford. */
    public static boolean has(ItemStack stack, String kind) {
        for (Source source : SOURCES.values()) {
            if (!source.effects(stack, kind).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
