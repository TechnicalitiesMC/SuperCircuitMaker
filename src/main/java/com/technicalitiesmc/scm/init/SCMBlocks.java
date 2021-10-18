package com.technicalitiesmc.scm.init;

import com.technicalitiesmc.scm.SuperCircuitMaker;
import com.technicalitiesmc.scm.block.CircuitBlock;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fmllegacy.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public final class SCMBlocks {

    public static final DeferredRegister<Block> REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCKS, SuperCircuitMaker.MODID);

    public static final RegistryObject<Block> CIRCUIT = register("circuit", CircuitBlock::new);

    // Helpers
    private static <T extends Block> RegistryObject<T> register(String name, Supplier<T> factory) {
        return REGISTRY.register(name, factory);
    }
    private static <T extends Block, T1> RegistryObject<T> register(String name, Function<T1, T> factory, T1 arg1) {
        return REGISTRY.register(name, () -> factory.apply(arg1));
    }
    private static <T extends Block, T1, T2> RegistryObject<T> register(String name, BiFunction<T1, T2, T> factory, T1 arg1, T2 arg2) {
        return REGISTRY.register(name, () -> factory.apply(arg1, arg2));
    }

}
