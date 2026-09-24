package com.elysium.enchanting.registry;

import com.elysium.enchanting.ElysiumEnchanting;
import com.elysium.enchanting.menu.ArcaneTableMenu;
import com.elysium.enchanting.menu.InfusionMenu;
import com.elysium.enchanting.menu.ScriptoriumMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Arcane Table's menu type. */
public final class EnchantingMenus {

    private EnchantingMenus() {
    }

    public static final DeferredRegister<MenuType<?>> TYPES =
            DeferredRegister.create(Registries.MENU, ElysiumEnchanting.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ArcaneTableMenu>> ARCANE_TABLE =
            TYPES.register("arcane_table", () -> IMenuTypeExtension.create(ArcaneTableMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<InfusionMenu>> INFUSION_BASIN =
            TYPES.register("infusion_basin", () -> IMenuTypeExtension.create(InfusionMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ScriptoriumMenu>> SCRIPTORIUM =
            TYPES.register("scriptorium", () -> IMenuTypeExtension.create(ScriptoriumMenu::new));

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
