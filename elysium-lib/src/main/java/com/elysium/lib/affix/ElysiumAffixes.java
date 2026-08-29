package com.elysium.lib.affix;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.element.ElysiumElement;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Elysium affix table.
 *
 * Two families live here:
 *
 * <ul>
 *   <li>psionic affixes, one per element, granted by the gear itself and scaled
 *       by tier;</li>
 *   <li>rune affixes, which now live on the rune itself — see
 *       {@code ElysiumRune#getAffix} — so that a rune added by another mod
 *       carries its own bonus rather than needing an entry here.</li>
 * </ul>
 *
 * Both are applied through an item's attribute component, which is how
 * gear-driven attributes work in 1.21.1.
 */
public final class ElysiumAffixes {

    private ElysiumAffixes() {
    }

    private static final Map<ElysiumElement, ElysiumPsionicAffix> ELEMENT_AFFIXES =
            new LinkedHashMap<>();

    /**
     * Attaches a psionic affix to an element.
     *
     * Called by whoever registers the element — the library for its five, an
     * add-on for its own. An element with no affix simply grants none, which is
     * a legal thing for an element to be.
     */
    public static void register(ElysiumPsionicAffix affix) {
        ELEMENT_AFFIXES.put(affix.getElement(), affix);
    }

    /** @return the psionic affix for an element, or null for {@code NONE} */
    @Nullable
    public static ElysiumPsionicAffix forElement(ElysiumElement element) {
        return element == null ? null : ELEMENT_AFFIXES.get(element);
    }

    public static Collection<ElysiumPsionicAffix> all() {
        return ELEMENT_AFFIXES.values();
    }

    /**
     * Called only when Apotheosis is installed.
     *
     * Elysium's affixes are self-contained, so there is nothing that must
     * happen here for the mod to work. Registering these affixes into
     * Apotheosis' own pool means touching its classes, which would make it a
     * hard dependency — that is deliberately not done. When you want that
     * integration, add Apotheosis as a {@code compileOnly} dependency and put
     * the bridging code in a separate class that is only loaded from here.
     */
    public static void onApotheosisPresent() {
        ElysiumLib.LOGGER.info("Apotheosis detected - Elysium gear is affix-compatible; "
                + "native psionic affixes remain active");
    }
}
