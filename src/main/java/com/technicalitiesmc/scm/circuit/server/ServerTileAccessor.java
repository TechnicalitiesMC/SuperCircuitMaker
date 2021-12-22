package com.technicalitiesmc.scm.circuit.server;

import com.mojang.math.Vector3f;
import com.technicalitiesmc.lib.circuit.component.ComponentHarvestContext;
import com.technicalitiesmc.lib.circuit.component.ComponentSlot;
import com.technicalitiesmc.lib.circuit.component.ComponentState;
import com.technicalitiesmc.lib.circuit.component.ComponentType;
import com.technicalitiesmc.lib.math.Vec2i;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.scm.circuit.CircuitAdjacency;
import com.technicalitiesmc.scm.circuit.TileAccessor;
import com.technicalitiesmc.scm.circuit.TilePointer;
import com.technicalitiesmc.scm.circuit.util.ComponentSlotPos;
import com.technicalitiesmc.scm.circuit.util.TileSection;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Consumer;

public class ServerTileAccessor implements TileAccessor {

    private final CircuitTile tile;
    private boolean removed;

    public ServerTileAccessor(CircuitTile tile) {
        this.tile = tile;
    }

    CircuitTile getTile() {
        return tile;
    }

    @Override
    public boolean isAreaEmpty() {
        return tile.getCircuit().isTileAreaEmpty(tile.getPosition());
    }

    @Override
    public void clearArea() {
        tile.getCircuit().clearTileArea(tile.getPosition(), null);
    }

    @Override
    public void visitAreaShapes(Consumer<VoxelShape> consumer) {
        var origin = tile.getPosition();
        var circuit = tile.getCircuit();
        for (var section : TileSection.VALUES) {
            var t = circuit.getTile(origin.offsetNeg(section));
            if (t != null) {
                t.stream(section)
                        .map(ComponentInstance::getShape)
                        .map(section::offsetNeg)
                        .forEach(consumer);
            }
        }
    }

    public void releaseClaim() {
        tile.getCircuit().releaseClaim(tile.getPosition());
    }

    public void clearAndRemove(ComponentHarvestContext context) {
        if (removed) {
            return;
        }
        removed = true;
        tile.getCircuit().clearAreaAndRemoveTile(tile, context);
    }

    public void scheduleTick(Level level) {
        tile.getCircuit().scheduleTick(level);
    }

    @Nullable
    public ComponentInstance get(Vec3i pos, ComponentSlot slot) {
        return tile.getCircuit().get(tile.getPosition().pack(pos), slot);
    }

    @Nullable
    public ComponentInstance tryPut(Vec3i pos, ComponentType type, ComponentType.Factory factory) {
        return tile.getCircuit().tryPut(tile.getPosition().pack(pos), type, factory);
    }

    public InteractionResult use(Vec3i pos, ComponentSlot slot, Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
        return tile.getCircuit().use(tile.getPosition().pack(pos), slot, player, hand, sideHit, hit);
    }

    public void tryHarvest(Vec3i pos, ComponentSlot slot, ComponentHarvestContext context) {
        tile.getCircuit().harvest(tile.getPosition().pack(pos), slot, context);
    }

//    public boolean tryRemove(Vec3i pos, ComponentSlot slot) {
//        return tile.getCircuit().tryRemove(tile.getPosition().pack(pos), slot);
//    }

    public void onInputsUpdated(VecDirectionFlags sides) {
        tile.notifyRedstoneUpdate(sides);
    }

    public CompoundTag describe(CompoundTag tag) {
        tag = tile.describe(tag);
        var adjacency = tile.getCircuit().calculateAdjacencyMap(tile.getPosition());
        var adjIndex = new int[adjacency.length];
        for (int i = 0; i < adjacency.length; i++) {
            adjIndex[i] = adjacency[i].ordinal();
        }
        tag.putIntArray("adjacency", adjIndex);
        return tag;
    }

    public interface Host {

        ServerTileAccessor getAccessor();

        @Nullable
        ServerTileAccessor.Host find(Vec2i offset);

        void updatePointer(TilePointer pointer);

        void syncState(Map<ComponentSlotPos, ComponentState> states, CircuitAdjacency[] adjacency);

        void drop(ItemStack stack);

        void playSound(SoundEvent sound, SoundSource source, float volume, float pitch);

        int getInput(VecDirection side);

        void setOutput(VecDirection side, int value);

    }

}