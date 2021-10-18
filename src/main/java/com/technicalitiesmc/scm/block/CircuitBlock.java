package com.technicalitiesmc.scm.block;

import com.technicalitiesmc.lib.block.TKBlock;
import com.technicalitiesmc.lib.block.multipart.BlockSlot;
import com.technicalitiesmc.lib.block.multipart.FaceSlot;
import com.technicalitiesmc.lib.block.multipart.Multipart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class CircuitBlock extends TKBlock implements Multipart {

    public static final Property<Direction> DIRECTION = DirectionProperty.create("face");

    public CircuitBlock() {
        super(Properties.of(Material.STONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DIRECTION);
    }

    @Nullable
    @Override
    public BlockSlot getSlot(BlockState state) {
        return FaceSlot.of(state.getValue(DIRECTION));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(DIRECTION, context.getClickedFace().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BOUNDS[state.getValue(DIRECTION).ordinal()];
    }

    private static final VoxelShape[] BOUNDS = {
            Block.box(0, 0, 0, 16, 2, 16),
            Block.box(0, 14, 0, 16, 16, 16),
            Block.box(0, 0, 0, 16, 16, 2),
            Block.box(0, 0, 14, 16, 16, 16),
            Block.box(0, 0, 0, 2, 16, 16),
            Block.box(14, 0, 0, 16, 16, 16)
    };

}
