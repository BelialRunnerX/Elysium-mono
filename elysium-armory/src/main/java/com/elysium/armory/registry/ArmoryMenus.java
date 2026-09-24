package com.elysium.armory.registry;

import com.elysium.armory.ElysiumArmory;
import com.elysium.armory.menu.ArmoryReforgingMenu;
import com.elysium.armory.menu.ArmorySalvagerMenu;
import com.elysium.armory.menu.ArmorySmithingMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The benches' menu types. */
public final class ArmoryMenus {

    private ArmoryMenus() {
    }

    public static final DeferredRegister<MenuType<?>> TYPES =
            DeferredRegister.create(Registries.MENU, ElysiumArmory.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ArmorySmithingMenu>> SMITHING =
            TYPES.register("gear_smithing", () -> IMenuTypeExtension.create(ArmorySmithingMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ArmorySalvagerMenu>> SALVAGER =
            TYPES.register("salvager", () -> IMenuTypeExtension.create(
                    (id, inventory, buf) -> new ArmorySalvagerMenu(id, inventory)));

    public static final DeferredHolder<MenuType<?>, MenuType<ArmoryReforgingMenu>> REFORGING =
            TYPES.register("reforging", () -> IMenuTypeExtension.create(
                    (id, inventory, buf) -> new ArmoryReforgingMenu(id, inventory)));

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
