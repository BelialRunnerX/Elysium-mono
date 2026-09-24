package com.elysium.space.registry;

import com.elysium.space.ElysiumSpace;
import com.elysium.space.machine.MachineMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SpaceMenus {

    private SpaceMenus() {
    }

    public static final DeferredRegister<MenuType<?>> TYPES = DeferredRegister.create(Registries.MENU, ElysiumSpace.MODID);

    /** Every machine's screen. */
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> MACHINE =
            TYPES.register("machine", () -> IMenuTypeExtension.create(MachineMenu::new));
}
