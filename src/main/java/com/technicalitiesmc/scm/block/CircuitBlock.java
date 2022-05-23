package com.technicalitiesmc.scm.block;

import com.mojang.math.Vector3f;
import com.technicalitiesmc.lib.block.BlockComponentData;
import com.technicalitiesmc.lib.block.CustomBlockHighlight;
import com.technicalitiesmc.lib.block.TKBlock;
import com.technicalitiesmc.lib.block.TKBlockEntity;
import com.technicalitiesmc.lib.block.component.BlockData;
import com.technicalitiesmc.lib.block.multipart.BlockSlot;
import com.technicalitiesmc.lib.block.multipart.FaceSlot;
import com.technicalitiesmc.lib.block.multipart.Multipart;
import com.technicalitiesmc.lib.circuit.component.ComponentHarvestContext;
import com.technicalitiesmc.lib.circuit.component.ComponentSlot;
import com.technicalitiesmc.lib.circuit.component.ComponentState;
import com.technicalitiesmc.lib.math.*;
import com.technicalitiesmc.lib.util.Utils;
import com.technicalitiesmc.scm.circuit.CircuitAdjacency;
import com.technicalitiesmc.scm.circuit.CircuitHelper;
import com.technicalitiesmc.scm.circuit.TileAccessor;
import com.technicalitiesmc.scm.circuit.TilePointer;
import com.technicalitiesmc.scm.circuit.client.ClientTile;
import com.technicalitiesmc.scm.circuit.server.CircuitCache;
import com.technicalitiesmc.scm.circuit.server.ServerTileAccessor;
import com.technicalitiesmc.scm.circuit.util.ComponentPos;
import com.technicalitiesmc.scm.circuit.util.ComponentSlotPos;
import com.technicalitiesmc.scm.circuit.util.TilePos;
import com.technicalitiesmc.scm.client.ClientConfig;
import com.technicalitiesmc.scm.client.model.CircuitModelData;
import com.technicalitiesmc.scm.init.SCMBlockEntities;
import com.technicalitiesmc.scm.init.SCMBlocks;
import com.technicalitiesmc.scm.init.SCMItemTags;
import com.technicalitiesmc.scm.network.ComponentBreakPacket;
import com.technicalitiesmc.scm.network.ComponentSyncPacket;
import com.technicalitiesmc.scm.network.ComponentUsePacket;
import com.technicalitiesmc.scm.network.SCMNetworkHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.model.data.ModelDataMap;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.technicalitiesmc.scm.circuit.CircuitHelper.SIZE;
import static com.technicalitiesmc.scm.circuit.CircuitHelper.SIZE_MINUS_ONE;

public class CircuitBlock extends TKBlock.WithEntity implements Multipart, CustomBlockHighlight {

    private static final Capability<CircuitBlock.Data> DATA_CAPABILITY = CapabilityManager.get(new CapabilityToken<>(){});

    private static final AABB CLICK_TILE_AABB = new AABB(0, 15 / 16D, 0, 1, 1, 1);

    public static final Property<Direction> DIRECTION = DirectionProperty.create("face");

    public static boolean picking;
    public static VoxelShape boundingBoxOverride;

    private final BlockData<Data> data = addComponent("circuit", BlockData.of(Data::new));

    public CircuitBlock() {
        super(
                Properties.of(Material.STONE)
                        .strength(3.0F, 6.0F)
                        .requiresCorrectToolForDrops(),
                SCMBlockEntities.CIRCUIT
        );
    }

    @Nullable
    @Override
    public Object getInterface(Class<?> itf) {
        if (itf == CustomBlockHighlight.class) {
            return this;
        }
        return super.getInterface(itf);
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
        return defaultBlockState().setValue(DIRECTION, Direction.DOWN);
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, @Nullable Direction direction) {
        return direction != null && direction.getAxis() != Direction.Axis.Y;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        if (side.getAxis() == Direction.Axis.Y) {
            return 0;
        }
        var data = this.data.at(level, pos, state);
        return data != null ? data.outputs[side.getOpposite().get2DDataValue()] / 17 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getSignal(state, level, pos, side);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighborPos, boolean unknown) {
        super.neighborChanged(state, level, pos, block, neighborPos, unknown);
        var data = this.data.at(level, pos, state);
        if (data == null) {
            return;
        }
        var sides = VecDirectionFlags.none();
        for (var side : Direction.Plane.HORIZONTAL) {
            var input = level.getSignal(pos.relative(side), side) * 17;
            var dir = VecDirection.fromDirection(side);
            var hIndex = dir.getHorizontalIndex();
            if (input != data.inputs[hIndex]) {
                sides = sides.and(dir);
            }
            data.inputs[hIndex] = input;
        }
        if (sides.isEmpty()) {
            return;
        }
        var accessor = data.getAccessor();
        accessor.onInputsUpdated(sides);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        var shape = getShape(state, level, pos, CollisionContext.of(player));
        var partialTickTime = 0;
        var start = player.getEyePosition(partialTickTime);
        var end = player.getViewVector(partialTickTime).multiply(10, 10, 10); // If we're here, we can definitely reach it
        var hit = shape.clip(start, start.add(end), pos);
        if (hit != null) {
            var hitPos = resolveHit(hit);
            if (hitPos != null && hitPos.pos().y() != -1) {
                var data = this.data.at(level, pos, state);
                if (data != null) {
                    var accessor = data.getOrCreateAccessor();
                    if (accessor instanceof ServerTileAccessor sta) {
                        var component = sta.get(hitPos.toAbsolute().pos(), hitPos.slot());
                        if (component != null) {
                            return component.getPickedItem();
                        }
                    } else if (accessor instanceof ClientTile ct) {
                        var componentState = ct.getState(hitPos.toAbsolute().pos(), hitPos.slot());
                        if (componentState != null) {
                            if (picking) {
                                componentState.onPicking(player);
                                picking = false;
                            }
                            return componentState.getPickedItem();
                        }
                    }
                }
            }
        }
        return new ItemStack(this);
    }

    private VoxelShape getBaseShape(BlockState state) {
        return BOUNDS[state.getValue(DIRECTION).ordinal()];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getBaseShape(state);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return getBaseShape(state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (boundingBoxOverride != null) {
            return boundingBoxOverride;
        }
        var data = this.data.at(level, pos, state);
        var baseShape = getBaseShape(state);
        if (data == null || data.hideComponents) {
            return baseShape;
        }
        var accessor = data.getOrCreateAccessor();
        if (accessor == null) {
            return baseShape;
        }

        var shapes = new ArrayList<VoxelShape>();
        shapes.add(baseShape);
        accessor.visitAreaShapes(shapes::add);
        if (accessor instanceof ClientTile ct && context instanceof EntityCollisionContext ecc && ecc.getEntity() instanceof Player p &&
                p.getItemInHand(InteractionHand.MAIN_HAND).is(SCMItemTags.SHOWS_CIRCUIT_GRID)) {
            var nx = ct.hasNeighbor(Vec2i.NEG_X);
            var px = ct.hasNeighbor(Vec2i.POS_X);
            var nz = ct.hasNeighbor(Vec2i.NEG_Y);
            var pz = ct.hasNeighbor(Vec2i.POS_Y);
            shapes.add(GRIDS[0]);
            if (nx) shapes.add(GRIDS[1]);
            if (px) shapes.add(GRIDS[2]);
            if (nz) shapes.add(GRIDS[3]);
            if (pz) shapes.add(GRIDS[4]);
            if (nx && nz && ct.hasNeighbor(Vec2i.NEG_X_NEG_Y)) shapes.add(GRIDS[5]);
            if (nx && pz && ct.hasNeighbor(Vec2i.NEG_X_POS_Y)) shapes.add(GRIDS[6]);
            if (px && pz && ct.hasNeighbor(Vec2i.POS_X_POS_Y)) shapes.add(GRIDS[7]);
            if (px && nz && ct.hasNeighbor(Vec2i.POS_X_NEG_Y)) shapes.add(GRIDS[8]);
        }
        return MergedShape.of(baseShape, shapes);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        var data = this.data.at(level, pos, state);
        if (data != null) {
            var accessor = data.getOrCreateAccessor();
            if (accessor != null && !accessor.isAreaEmpty() && !player.isCrouching()) {
                return 0;
            }
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide() && !moving) {
            var data = this.data.at(level, pos, state);
            if (data != null) {
                var accessor = data.getAccessor();
                accessor.clearAndRemove(ComponentHarvestContext.drop((ServerLevel) level, data::drop));
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        var data = this.data.at(level, pos, state);
        var shouldRemove = data == null;
        if (!shouldRemove) {
            var accessor = data.getOrCreateAccessor();
            shouldRemove = accessor == null || accessor.isAreaEmpty();
        }
        shouldRemove |= player.isCrouching();
        if (shouldRemove && !level.isClientSide() && data != null) {
            var accessor = data.getAccessor();
            accessor.clearAndRemove(ComponentHarvestContext.forPlayer(player));
        }
        return shouldRemove && super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public boolean isStickyBlock(BlockState state) {
        return true;
    }

    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        return other.is(this) && state.getValue(DIRECTION) == other.getValue(DIRECTION);
    }

    public InteractionResult onClientUse(BlockState state, ClientLevel level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        var hitPos = resolveHit(hit);
        if (hitPos == null || hitPos.pos().y() == -1) {
            if (ClientConfig.Debug.allowHidingComponents.get() &&
                    player.getMainHandItem().is(SCMItemTags.DBG_HIDES_COMPONENTS)
            ) {
                var data = this.data.at(level, pos, state);
                if (data != null) {
                    data.hideComponents = !data.hideComponents;
                    data.onUpdateReceived();
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        var data = this.data.at(level, pos, state);
        if (data == null || !(data.getOrCreateAccessor() instanceof ClientTile ct)) {
            return InteractionResult.PASS;
        }

        var s = ct.getState(hitPos.toAbsolute().pos(), hitPos.slot());
        if (s == null) {
            return InteractionResult.PASS;
        }

        var scaledHit = hit.getLocation()
                .subtract(Vec3.atLowerCornerOf(hit.getBlockPos()))
                .add(1 / 16D, -2 / 16D, 1 / 16D).scale(8);
        var hitVec = new Vector3f(
                (float) scaledHit.x() % 1,
                (float) scaledHit.y() % 1,
                (float) scaledHit.z() % 1
        );
        var sideHit = VecDirection.fromDirection(hit.getDirection());

        SCMNetworkHandler.sendToServer(new ComponentUsePacket(pos, hitPos.toAbsolute().pos(), hitPos.slot(), hand, sideHit, hitVec));
        return s.use(player, hand, sideHit, hitVec);
    }

    public InteractionResult onClientClicked(BlockState state, ClientLevel level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        var hitPos = resolveHit(hit);
        if (hitPos == null || hitPos.pos().y() < 0) {
            return InteractionResult.PASS;
        }

        SCMNetworkHandler.sendToServer(new ComponentBreakPacket(pos, hitPos.toAbsolute().pos(), hitPos.slot()));
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : this::tickEntity;
    }

    private void tickEntity(Level level, BlockPos pos, BlockState blockState, BlockEntity entity) {
        if (!(entity instanceof TKBlockEntity e)) {
            return;
        }
        var data = e.get(this.data);
        if (data != null && data.getOrCreateAccessor() instanceof ServerTileAccessor sta) {
            sta.scheduleTick(level);
        }
    }

    @Nullable
    public static ComponentSlotPos resolveHit(BlockHitResult blockHit) {
        if (!(blockHit instanceof IndexedBlockHitResult hit) || !hit.hasIndex()) {
            return null;
        }
        return CircuitHelper.resolvePositionFromShapeIndex(hit.getIndex());
    }

    public static class Data extends BlockComponentData<BlockData<?>> implements ServerTileAccessor.Host, ClientTile.Host {

        private final LazyOptional<Data> self = LazyOptional.of(() -> this);

        // Server
        @Nullable
        private TilePointer tilePointer;
        private final int[] inputs = new int[4];
        private final int[] outputs = new int[4];

        @Deprecated // Temporary, clientside-only
        private boolean hideComponents;

        @Nullable
        private TileAccessor accessor;

        public Data(Context context) {
            super(context);
        }

        @Nullable
        private MinecraftServer getServer() {
            var level = getLevel();
            return level != null ? level.getServer() : null;
        }

        @Nullable
        public TileAccessor getOrCreateAccessor() {
            return getOrCreateAccessor(getServer());
        }

        @Nullable
        public TileAccessor getOrCreateAccessor(@Nullable MinecraftServer server) {
            if (accessor != null) {
                return accessor;
            }
            if (server == null) {
                return accessor = new ClientTile(this);
            }
            if (tilePointer == null) {
                tilePointer = new TilePointer(UUID.randomUUID(), TilePos.ZERO);
            }
            var circuit = CircuitCache.getOrCreate(server, tilePointer.id());
            accessor = circuit.claim(tilePointer.tilePos(), this);
            if (accessor != null) {
                return accessor;
            }
            tilePointer = null;
            return getOrCreateAccessor(server);
        }

        @Override
        public void onChunkUnloaded() {
            super.onChunkUnloaded();
            if (accessor instanceof ServerTileAccessor sta) {
                sta.releaseClaim();
            }
        }

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            if (cap == DATA_CAPABILITY) {
                return self.cast();
            }
            return super.getCapability(cap, side);
        }

        @Override
        public void addModelData(ModelDataMap.Builder builder) {
            var accessor = getOrCreateAccessor(null);
            if (accessor instanceof ClientTile ct) {
                builder.withInitial(CircuitModelData.PROPERTY, ct.getModelData(hideComponents));
            }
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag = super.save(tag);
            if (tilePointer != null) {
                tag.put("pointer", tilePointer.save(new CompoundTag()));
            }
            tag.putIntArray("inputs", inputs);
            tag.putIntArray("outputs", outputs);
            return tag;
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            tilePointer = tag.contains("pointer") ? new TilePointer(tag.getCompound("pointer")) : null;
            var inputs = tag.getIntArray("inputs");
            System.arraycopy(inputs, 0, this.inputs, 0, inputs.length);
            var outputs = tag.getIntArray("outputs");
            System.arraycopy(outputs, 0, this.outputs, 0, outputs.length);
        }

        @Override
        public CompoundTag saveDescription(CompoundTag tag) {
            tag = super.saveDescription(tag);
            tag.put("tile", getAccessor().describe(new CompoundTag()));
            return tag;
        }

        @Override
        public void loadDescription(CompoundTag tag) {
            super.loadDescription(tag);
            accessor = ClientTile.fromDescription(this, tag.getCompound("tile"));
        }

        @Nullable
        private Data getCircuitData(Vec2i offset) {
            var pos = getBlockPos().offset(offset.x(), 0, offset.y());
            var entity = getLevel().getBlockEntity(pos);
            if (entity == null) {
                return null;
            }
            return entity.getCapability(DATA_CAPABILITY).orElse(null);
        }

        @Override
        public ServerTileAccessor getAccessor() {
            return getOrCreateAccessor(getServer()) instanceof ServerTileAccessor sta ? sta : null;
        }

        @Nullable
        @Override
        public ServerTileAccessor.Host find(Vec2i offset) {
            return getCircuitData(offset);
        }

        @Override
        public void updatePointer(TilePointer pointer) {
            this.tilePointer = pointer;
            markUnsaved();
        }

        @Override
        public void syncState(Map<ComponentSlotPos, ComponentState> states, CircuitAdjacency[] adjacency) {
            var server = getServer();
            if (server == null) {
                return;
            }

            var packet = new ComponentSyncPacket(getBlockPos(), new HashMap<>(states), adjacency);
            SCMNetworkHandler.broadcastToClientsWatching(packet, (ServerLevel) getLevel(), new ChunkPos(getBlockPos()));
        }

        @Override
        public void drop(ItemStack stack) {
            Utils.dropItemAt(getLevel(), getBlockPos(), stack);
        }

        @Override
        public void playSound(SoundEvent sound, SoundSource source, float volume, float pitch) {
            var level = getLevel();
            var pos = getBlockPos();
            level.playSound(null, pos, sound, source, volume, pitch);
        }

        @Override
        public int getInput(VecDirection side) {
            return inputs[side.getHorizontalIndex()];
        }

        @Override
        public void setOutput(VecDirection side, int value) {
            var dir = Direction.fromAxisAndDirection(side.getAxis(), side.getAxisDirection());
            outputs[dir.get2DDataValue()] = value;
            getLevel().updateNeighborsAt(getBlockPos(), SCMBlocks.CIRCUIT.get());
            getLevel().updateNeighborsAt(getBlockPos().relative(dir), SCMBlocks.CIRCUIT.get());
        }

        @Nullable
        @Override
        public ClientTile getClientNeighbor(Vec2i offset) {
            var data = getCircuitData(offset);
            return data != null ? (ClientTile) data.getOrCreateAccessor(null) : null;
        }

        @Override
        public void onUpdateReceived() {
            // Mark data updated and trigger a re-render
            markDataUpdated();
            if (getLevel() instanceof ClientLevel level) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 8);
            }
        }

    }

    private static final VoxelShape[] BOUNDS = {
            box(0, 0, 0, 16, 2, 16),
            box(0, 14, 0, 16, 16, 16),
            box(0, 0, 0, 16, 16, 2),
            box(0, 0, 14, 16, 16, 16),
            box(0, 0, 0, 2, 16, 16),
            box(14, 0, 0, 16, 16, 16)
    };
    private static final VoxelShape[] GRIDS = new VoxelShape[]{
            makeGrid(0, SIZE_MINUS_ONE, 0, SIZE_MINUS_ONE, -1), // Center
            makeGrid(-1, 0, 0, SIZE_MINUS_ONE, -1), // Negative x
            makeGrid(SIZE_MINUS_ONE, SIZE, 0, SIZE_MINUS_ONE, -1), // Positive x
            makeGrid(0, SIZE_MINUS_ONE, -1, 0, -1), // Negative z
            makeGrid(0, SIZE_MINUS_ONE, SIZE_MINUS_ONE, SIZE, -1), // Positive z
            makeGrid(-1, 0, -1, 0, -1), // NX NZ
            makeGrid(-1, 0, SIZE_MINUS_ONE, SIZE, -1), // NX PZ
            makeGrid(SIZE_MINUS_ONE, SIZE, SIZE_MINUS_ONE, SIZE, -1), // PX PZ
            makeGrid(SIZE_MINUS_ONE, SIZE, -1, 0, -1), // PX NZ
    };

    public static VoxelShape makeGrid(int startX, int endX, int startZ, int endZ, int yPos) {
        var shapes = new ArrayList<VoxelShape>();
        for (int x = startX; x < endX; x++) {
            for (int z = startZ; z < endZ; z++) {
                shapes.add(CircuitHelper.createShape(CLICK_TILE_AABB, new ComponentPos(x, yPos, z), ComponentSlot.DEFAULT));
            }
        }
        return MergedShape.ofMerged(shapes);
    }

}
