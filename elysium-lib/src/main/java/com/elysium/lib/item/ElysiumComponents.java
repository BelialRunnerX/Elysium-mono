package com.elysium.lib.item;

import com.elysium.lib.ElysiumLib;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom data components. This is the 1.20.5+ replacement for the loose NBT
 * tags the mod used to write directly onto item stacks.
 */
public final class ElysiumComponents {

    private ElysiumComponents() {
    }

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(ElysiumLib.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ElysiumGearData>> GEAR_DATA =
            COMPONENTS.registerComponentType("gear_data", builder -> builder
                    .persistent(ElysiumGearData.CODEC)
                    .networkSynchronized(ElysiumGearData.STREAM_CODEC));

    // There was a second component here, trinket_data, holding a trinket's
    // ascension tier on its own. Its reasoning was that "a trinket has no
    // runes, no reforge rolls and no reforge budget", so sharing GEAR_DATA
    // would be a standing invitation to socket a rune into a ring.
    //
    // That premise is gone. A trinket is a piece of Elysium gear: it is
    // reforgeable and ascendable at the same table by the same rules as
    // armour, it implements ElysiumSocketable like everything else, and
    // socketing a rune into a ring is now a feature rather than an accident
    // waiting to happen. Two components for one idea would have meant two
    // places to read an ascension tier from and a real chance of reading the
    // wrong one.
    //
    // Deleting a registered component is normally a save-breaking act. It is
    // free here only because nothing ever wrote one: no trinket had shipped
    // when this was removed.
}
