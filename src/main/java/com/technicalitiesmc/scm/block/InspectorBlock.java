package com.technicalitiesmc.scm.block;

import com.technicalitiesmc.lib.block.BlockComponentData;
import com.technicalitiesmc.lib.block.TKBlock;
import com.technicalitiesmc.lib.block.component.BlockData;
import com.technicalitiesmc.scm.SuperCircuitMaker;
import com.technicalitiesmc.scm.init.SCMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.Random;

/*
 * Basically copy-pasted from the observer block.
 * Hope nothing breaks lol.
 */
public class InspectorBlock extends TKBlock.WithEntity {

    private static final String LANG_LEARNED = "msg." + SuperCircuitMaker.MODID + ".inspector.learned";
    private static final String LANG_MODE_STATE = "msg." + SuperCircuitMaker.MODID + ".inspector.mode_state";
    private static final String LANG_MODE_BLOCK = "msg." + SuperCircuitMaker.MODID + ".inspector.mode_block";

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private final BlockData<Data> data = addComponent("data", ctx -> new BlockData<>(ctx, Data::new));

    public InspectorBlock() {
        super(
                BlockBehaviour.Properties.of(Material.STONE)
                        .strength(3.0F)
                        .requiresCorrectToolForDrops()
                        .isRedstoneConductor(($, $$, $$$) -> false),
                SCMBlockEntities.INSPECTOR
        );
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite().getOpposite());
    }

    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    public void tick(BlockState state, ServerLevel level, BlockPos pos, Random random) {
        if (state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), 2);
        } else {
            level.setBlock(pos, state.setValue(POWERED, true), 2);
            level.scheduleTick(pos, this, 2);
        }
        updateNeighborsInFront(level, pos, state);
    }

    public BlockState updateShape(BlockState state, Direction side, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(FACING) == side) {
            checkUpdate(level, pos, state, neighborState);
        }
        return super.updateShape(state, side, neighborState, level, pos, neighborPos);
    }

    private void checkUpdate(LevelAccessor level, BlockPos pos, BlockState state, BlockState neighborState) {
        var data = this.data.at(level, pos, state);
        if (data != null && !state.getValue(POWERED) && data.checkBlock(neighborState)) {
            startSignal(level, pos);
        }
    }

    private void startSignal(LevelAccessor level, BlockPos pos) {
        if (!level.isClientSide() && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 2);
        }
    }

    protected void updateNeighborsInFront(Level level, BlockPos pos, BlockState state) {
        var direction = state.getValue(FACING);
        var neighborPos = pos.relative(direction.getOpposite());
        level.neighborChanged(neighborPos, this, pos);
        level.updateNeighborsAtExceptFromFacing(neighborPos, this, direction);
    }

    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, @Nullable Direction direction) {
        return state.getValue(FACING) == direction;
    }

    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getSignal(level, pos, side);
    }

    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(POWERED) && state.getValue(FACING) == side ? 15 : 0;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        var data = this.data.at(level, pos, state);
        return data != null && data.found ? 15 : 0;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!state.is(oldState.getBlock())) {
            checkUpdate(level, pos, state, level.getBlockState(pos.relative(state.getValue(FACING))));
        }
    }

    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && state.getValue(POWERED) && level.getBlockTicks().hasScheduledTick(pos, this)) {
                updateNeighborsInFront(level, pos, state.setValue(POWERED, false));
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isCrouching()) {
            if (!level.isClientSide()) {
                var data = this.data.at(level, pos, state);
                if (data != null) {
                    var neighborState = level.getBlockState(pos.relative(state.getValue(FACING)));
                    if (data.matchedState != neighborState) {
                        data.setMatchedState(neighborState);
                        player.displayClientMessage(new TranslatableComponent(LANG_LEARNED), true);
                    } else {
                        data.toggleMode();
                        player.displayClientMessage(new TranslatableComponent(data.mode ? LANG_MODE_BLOCK : LANG_MODE_STATE), true);
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    private static class Data extends BlockComponentData {

        private BlockState matchedState;
        private boolean mode = false;
        private boolean found = false;

        protected Data(Context context) {
            super(context);
        }

        private void setMatchedState(BlockState state) {
            matchedState = state;
            markUnsaved();
            checkBlock();
        }

        private void toggleMode() {
            mode = !mode;
            markUnsaved();
            checkBlock();
        }

        private void checkBlock() {
            var state = getLevel().getBlockState(getBlockPos().relative(getBlockState().getValue(FACING)));
            checkBlock(state);
        }

        private boolean checkBlock(BlockState state) {
            var found = mode ? state.getBlock() == matchedState.getBlock() : state == matchedState;
            if (found != this.found) {
                this.found = found;
                getLevel().updateNeighbourForOutputSignal(getBlockPos(), getBlockState().getBlock());
            }
            return found;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            if (matchedState != null) {
                tag.putInt("matched_state", Block.BLOCK_STATE_REGISTRY.getId(matchedState));
            }
            tag.putBoolean("mode", mode);
            tag.putBoolean("found", found);
            return super.save(tag);
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            if (tag.contains("matched_state")) {
                matchedState = Block.BLOCK_STATE_REGISTRY.byId(tag.getInt("matched_state"));
            }
            mode = tag.getBoolean("mode");
            found = tag.getBoolean("found");
        }

    }

}
