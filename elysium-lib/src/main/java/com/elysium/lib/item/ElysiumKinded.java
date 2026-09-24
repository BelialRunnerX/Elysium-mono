package com.elysium.lib.item;

/**
 * An item that knows what kind of thing it is.
 *
 * Modular gear is a plain {@code Item} carrying parts — a sword and a pickaxe
 * are the same class, and no amount of looking at the class will tell them
 * apart. Rather than have the library guess, the item says so, in the
 * vocabulary of {@link ElysiumGearTree}.
 *
 * <p>Open to other mods on purpose: anything that implements this is placed in
 * the tree exactly where it says it belongs, and every affix, gem, rune and
 * trait that names that kind finds it.
 */
public interface ElysiumKinded {

    /**
     * A kind from {@link ElysiumGearTree}, such as {@code "sword"} or
     * {@code "hammer"}. A name the tree does not know is ignored, and the item
     * falls back to being read the way any other item would be.
     */
    String gearKind();
}
