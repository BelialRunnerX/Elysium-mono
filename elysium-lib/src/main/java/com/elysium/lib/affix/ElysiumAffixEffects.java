package com.elysium.lib.affix;

import com.elysium.lib.effect.ElysiumItemEffects;
import com.elysium.lib.item.ElysiumComponents;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The affixes on a stack, offered to the engine as one of the things that can
 * be attached to an item.
 *
 * <p>This is the whole of what makes an affix work. It states its effects in
 * the shared vocabulary and hands them over at the level it rolled; every
 * handler that already knew how to read a trait's bleed reads an affix's bleed
 * without being told that affixes exist.
 */
public final class ElysiumAffixEffects implements ElysiumItemEffects.Source {

    private static final ElysiumAffixEffects INSTANCE = new ElysiumAffixEffects();

    private ElysiumAffixEffects() {
    }

    /** Registers the affixes as an attachment. Called once, at mod construction. */
    public static void register() {
        ElysiumItemEffects.register("elysiumlib:affixes", INSTANCE);
    }

    public static ElysiumItemAffixes of(ItemStack stack) {
        return stack.getOrDefault(ElysiumComponents.AFFIXES.get(), ElysiumItemAffixes.NONE);
    }

    @Override
    public List<ElysiumItemEffects.Active> effects(ItemStack stack, String kind) {
        ElysiumItemAffixes affixes = of(stack);
        if (affixes.isEmpty()) {
            return List.of();
        }
        List<ElysiumItemEffects.Active> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : affixes.levels().entrySet()) {
            ElysiumAffixDefinition affix = ElysiumAffixCatalogue.get().byId(entry.getKey());
            if (affix == null) {
                continue;                  // an affix from a pack that is no longer loaded
            }
            int level = Math.max(1, Math.min(entry.getValue(), affix.maxLevel()));
            for (JsonObject effect : affix.effects(kind)) {
                out.add(new ElysiumItemEffects.Active(entry.getKey(), level, effect));
            }
        }
        return out;
    }

    @Override
    public int level(ItemStack stack, String name) {
        return of(stack).level(name);
    }
}
