package com.technicalitiesmc.scm.init;

import com.technicalitiesmc.scm.SuperCircuitMaker;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fmllegacy.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Function;
import java.util.function.Supplier;

public final class SCMItems {

    public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, SuperCircuitMaker.MODID);

    public static final RegistryObject<BlockItem> CIRCUIT = register(SCMBlocks.CIRCUIT);

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
