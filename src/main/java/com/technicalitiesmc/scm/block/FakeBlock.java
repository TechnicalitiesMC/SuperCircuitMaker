package com.technicalitiesmc.scm.block;

import net.minecraft.core.Registry;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.util.Map;

public class FakeBlock extends AirBlock {

    public FakeBlock() {
        super(BlockBehaviour.Properties.copy(Blocks.AIR));
        try {
            var holders = ObfuscationReflectionHelper.getPrivateValue((Class) Registry.BLOCK.getClass(), Registry.BLOCK, "holders");
            var map = (Map<Block, ?>) ObfuscationReflectionHelper.getPrivateValue((Class) holders.getClass(), holders, "holders");
            map.remove(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create fake block.", e);
        }
    }

}
