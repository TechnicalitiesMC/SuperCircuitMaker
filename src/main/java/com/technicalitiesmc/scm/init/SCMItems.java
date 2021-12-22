package com.technicalitiesmc.scm.init;

import com.technicalitiesmc.scm.SuperCircuitMaker;
import com.technicalitiesmc.scm.item.ScrewdriverItem;
import com.technicalitiesmc.scm.item.SimpleComponentItem;
import com.technicalitiesmc.scm.placement.PlatformPlacement;
import com.technicalitiesmc.scm.placement.SimplePlacement;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Function;
import java.util.function.Supplier;

public final class SCMItems {

    public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, SuperCircuitMaker.MODID);

    public static final RegistryObject<BlockItem> CIRCUIT = register(SCMBlocks.CIRCUIT);

    public static final RegistryObject<Item> TINY_REDSTONE = register("tiny_redstone", () -> {
        return new SimpleComponentItem(new SimplePlacement(SCMComponents.REDSTONE_WIRE, false, true));
    });
    public static final RegistryObject<Item> REDSTONE_STICK = register("redstone_stick", () -> {
        return new SimpleComponentItem(new SimplePlacement(SCMComponents.VERTICAL_WIRE, true, false));
    });

    public static final RegistryObject<Item> RANDOMIZER = register("randomizer", () -> {
        return new SimpleComponentItem(new SimplePlacement(SCMComponents.RANDOMIZER, false, true));
    });
    public static final RegistryObject<Item> DELAY = register("delay", () -> {
        return new SimpleComponentItem(new SimplePlacement(SCMComponents.DELAY, false, true));
    });
    public static final RegistryObject<Item> PULSAR = register("pulsar", () -> {
        return new SimpleComponentItem(new SimplePlacement(SCMComponents.PULSAR, false, true));
    });

    public static final RegistryObject<Item> LAMP = register("lamp", () -> {
        return new SimpleComponentItem(new SimplePlacement(SCMComponents.LAMP, false, true));
    });

    public static final RegistryObject<Item> BUTTON = register("button", () -> {
        return new SimpleComponentItem(new SimplePlacement(SCMComponents.BUTTON, false, true));
    });

    public static final RegistryObject<Item> PLATFORM = register("platform", () -> {
        return new SimpleComponentItem(new PlatformPlacement());
    });

    public static final RegistryObject<Item> SCREWDRIVER = register("screwdriver", ScrewdriverItem::new);

    // Helpers
    private static <T extends Item> RegistryObject<T> register(String name, Supplier<T> factory) {
        return REGISTRY.register(name, factory);
    }

    private static <T extends Item> RegistryObject<T> register(RegistryObject<? extends Block> block, Function<Block, T> factory) {
        return register(block.getId().getPath(), () -> (T) factory.apply(block.get()));
    }

    private static RegistryObject<BlockItem> register(RegistryObject<Block> block) {
        return register(block, b -> new BlockItem(b, new Item.Properties().tab(SuperCircuitMaker.CREATIVE_TAB)));
    }

}
