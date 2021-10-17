package com.technicalitiesmc.scm.block;

import com.technicalitiesmc.lib.block.TKBlock;
import com.technicalitiesmc.lib.block.multipart.BlockSlot;
import com.technicalitiesmc.lib.block.multipart.FaceSlot;
import com.technicalitiesmc.lib.block.multipart.Multipart;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Material;

import javax.annotation.Nullable;

public class CircuitBlock extends TKBlock implements Multipart {

    public static final Property<Direction> DIRECTION = DirectionProperty.create("face");

    public CircuitBlock() {
        super(Properties.of(Material.STONE));
    }

    @Nullable
    @Override
    public BlockSlot getSlot(BlockState state) {
        return FaceSlot.of(state.getValue(DIRECTION));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DIRECTION);
    }

}
