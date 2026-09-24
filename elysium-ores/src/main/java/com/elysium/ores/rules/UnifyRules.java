package com.elysium.ores.rules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which of a pack's four kinds of steel is <em>the</em> steel.
 *
 * <h2>The problem</h2>
 *
 * A large pack contains the same material several times over. In the pack this
 * was written against there are seven uranium ores, six lead ores, and steel
 * ingots from five different mods, each with its own recipes, its own machine
 * that accepts only its own, and its own slot in the player's storage. Nothing
 * is broken; everything is duplicated. The player pays for it in inventory
 * space and in the constant small question of which lead this machine wanted.
 *
 * <h2>What unification is, here</h2>
 *
 * Three things, and deliberately not a fourth.
 *
 * <ol>
 *   <li><b>A canonical.</b> One item per family, chosen by a stated priority
 *       of mods rather than by whichever mod loaded first.</li>
 *   <li><b>Complete tags.</b> Every variant is added to the family's {@code c:}
 *       tag, so a recipe written against the tag accepts all of them. Most
 *       modern recipes are written against the tag; the ones that are not are
 *       the reason for the third thing.</li>
 *   <li><b>A way in.</b> A one-for-one conversion from every variant to the
 *       canonical, so a player who ends up holding the wrong lead is one
 *       crafting step from the right one.</li>
 * </ol>
 *
 * <p>The fourth thing — rewriting other mods' recipes so the variants stop
 * being produced at all — is what most unifiers do, and this one does not.
 * It requires reaching inside recipe objects this mod did not write, of
 * classes it cannot know, and the failure mode is a recipe that silently
 * produces the wrong item or no item in a save the player has already built
 * on. Everything here is additive: no recipe is edited, nothing is
 * unregistered, and removing this module leaves a world that still works.
 *
 * <h2>The ambiguity guard</h2>
 *
 * Tags are written by people and sometimes wrong. In the scanned pack,
 * {@code oritech:biosteel_ingot} sits in {@code c:ingots/steel} as well as
 * {@code c:ingots/biosteel}. A unifier that trusted the tag would offer to
 * convert biosteel into steel, which destroys it. So a variant that answers to
 * more than one family of the same kind is never converted and never treated as
 * a candidate for canonical — see {@link #ambiguous}. The cost of the guard is
 * one unconverted item; the cost of not having it is a player's material.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class UnifyRules {

    private UnifyRules() {
    }

    /**
     * The order in which mods are asked to supply a canonical.
     *
     * Vanilla first, always: an iron ingot is the iron ingot, and a pack where
     * it is not would be a pack that has broken every recipe anyone has ever
     * memorised. Elysium's own next, because this module can promise they
     * exist. After that the ordering is by how much of the pack's economy a mod
     * owns — a material that a large tech mod's machines consume by the
     * thousand should be that mod's item, so the common case needs no
     * conversion at all.
     *
     * <p>A namespace absent from this list is not excluded; it is simply last,
     * and ties among the unlisted are broken by name so the answer is the same
     * on every load. That determinism matters more than the ordering itself: a
     * canonical that changed between sessions would move the player's items
     * around behind their back.
     */
    public static final List<String> PRIORITY = List.of(
            "minecraft",
            "elysium", "elysiumlib", "elysiumspace", "elysiumores",
            "mekanism",
            "immersiveengineering",
            "create",
            "tfmg",
            "oritech",
            "silentgear",
            "bigreactors",
            "powah",
            "createaddition",
            "createbigcannons",
            "createdeco",
            "mysticalagriculture",
            "mysticalagradditions");

    /** Where a namespace stands; lower is better, and the unlisted stand last. */
    public static int rank(String namespace) {
        int at = PRIORITY.indexOf(namespace == null ? "" : namespace);
        return at < 0 ? PRIORITY.size() : at;
    }

    /** The namespace half of {@code namespace:path}, or "" if there is none. */
    public static String namespaceOf(String id) {
        if (id == null) {
            return "";
        }
        int colon = id.indexOf(':');
        return colon < 0 ? "" : id.substring(0, colon);
    }

    /**
     * The canonical of a family, given every id that answers to it.
     *
     * By {@link #rank}, then by name. Null for a family nothing supplies,
     * which is the normal answer for most families in most packs and is not an
     * error: it means this pack does not have that material, so there is
     * nothing to unify and no ore to generate.
     */
    public static String canonical(Collection<String> candidates) {
        String best = null;
        for (String id : candidates) {
            if (id == null || id.isEmpty()) {
                continue;
            }
            if (best == null || better(id, best)) {
                best = id;
            }
        }
        return best;
    }

    /** Whether {@code a} should be preferred over {@code b}. */
    public static boolean better(String a, String b) {
        int ra = rank(namespaceOf(a));
        int rb = rank(namespaceOf(b));
        if (ra != rb) {
            return ra < rb;
        }
        return a.compareTo(b) < 0;
    }

    /**
     * Whether an item answers to more than one family of the same kind.
     *
     * The guard described in the class documentation. Takes the families this
     * item was found in; two or more means hands off.
     */
    public static boolean ambiguous(Collection<String> familiesContaining) {
        return familiesContaining.size() > 1;
    }

    /**
     * The conversions a family is owed: every variant that is not the
     * canonical, in a stable order, with the ambiguous ones left out.
     *
     * @param members  every id in the family's tag
     * @param canonical the id chosen by {@link #canonical}
     * @param ambiguousIds ids that answer to another family too
     */
    public static List<String> conversions(Collection<String> members,
                                           String canonical,
                                           Collection<String> ambiguousIds) {
        List<String> out = new ArrayList<>();
        for (String id : members) {
            if (id == null || id.isEmpty() || id.equals(canonical)) {
                continue;
            }
            if (ambiguousIds.contains(id)) {
                continue;
            }
            out.add(id);
        }
        out.sort(String::compareTo);
        return List.copyOf(out);
    }

    /**
     * The whole answer for one kind of thing: family name to canonical.
     *
     * Families with nothing in them are left out rather than mapped to null, so
     * a caller iterating the result never has to ask whether an entry means
     * anything.
     */
    public static Map<String, String> canonicals(Map<String, ? extends Collection<String>> families) {
        Map<String, String> out = new LinkedHashMap<>();
        List<String> names = new ArrayList<>(families.keySet());
        names.sort(String::compareTo);
        for (String name : names) {
            String chosen = canonical(families.get(name));
            if (chosen != null) {
                out.put(name, chosen);
            }
        }
        return out;
    }

    /**
     * Whether a family is worth touching at all.
     *
     * One supplier is not a duplication problem; it is just a material. Doing
     * nothing there keeps the conversion recipe list to the families that
     * actually have the problem, which in the scanned pack is forty-five of
     * them rather than two hundred and sixty.
     */
    public static boolean worthUnifying(Collection<String> members) {
        if (members.size() < 2) {
            return false;
        }
        String first = null;
        for (String id : members) {
            String ns = namespaceOf(id);
            if (first == null) {
                first = ns;
            } else if (!first.equals(ns)) {
                return true;
            }
        }
        return false;
    }
}
