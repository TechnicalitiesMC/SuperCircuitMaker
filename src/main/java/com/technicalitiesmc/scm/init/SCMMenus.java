package com.technicalitiesmc.scm.init;

import com.technicalitiesmc.scm.SuperCircuitMaker;
import com.technicalitiesmc.scm.menu.DelayMenu;
import com.technicalitiesmc.scm.menu.PulsarMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class SCMMenus {

    public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.CONTAINERS, SuperCircuitMaker.MODID);

    public static final RegistryObject<MenuType<DelayMenu>> DELAY = register("delay", DelayMenu::new);
    public static final RegistryObject<MenuType<PulsarMenu>> PULSAR = register("pulsar", PulsarMenu::new);

    // Helpers
    private static <T extends AbstractContainerMenu> RegistryObject<MenuType<T>> register(String name, MenuType.MenuSupplier<T> factory) {
        return REGISTRY.register(name, () -> new MenuType<>(factory));
    }

}
