package com.technicalitiesmc.scm.init;

import com.technicalitiesmc.lib.block.TKBlockEntity;
import com.technicalitiesmc.scm.SuperCircuitMaker;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class SCMBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, SuperCircuitMaker.MODID);

    public static final RegistryObject<BlockEntityType<TKBlockEntity>> CIRCUIT = register(SCMBlocks.CIRCUIT);

    public static final RegistryObject<BlockEntityType<TKBlockEntity>> INSPECTOR = register(SCMBlocks.INSPECTOR);

    // Helpers
    private static RegistryObject<BlockEntityType<TKBlockEntity>> register(RegistryObject<Block> block) {
        return register(TKBlockEntity::new, block);
    }

    private static <T extends BlockEntity> RegistryObject<BlockEntityType<T>> register(
            BlockEntityType.BlockEntitySupplier<T> factory,
            RegistryObject<Block> block
    ) {
        var name = block.getId().getPath();
        return REGISTRY.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }

}
