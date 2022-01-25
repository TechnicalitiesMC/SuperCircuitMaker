package com.technicalitiesmc.scm.circuit.server;

import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.scm.circuit.CircuitHelper;
import com.technicalitiesmc.scm.circuit.util.ComponentPos;
import com.technicalitiesmc.scm.circuit.util.ComponentSlotPos;
import com.technicalitiesmc.scm.circuit.util.TilePos;
import com.technicalitiesmc.scm.circuit.util.TileSection;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static com.technicalitiesmc.scm.circuit.CircuitHelper.*;

public class CircuitTile {

    private final ComponentInstance[] components = new ComponentInstance[TOTAL_POSITIONS];
    private final BitSet componentPositions = new BitSet(TOTAL_POSITIONS);
    private final BitSet occupiedPositions = new BitSet(TOTAL_POSITIONS);
    private final BitSet syncQueue = new BitSet(TOTAL_POSITIONS);
    private final Int2IntMap delegationMap = new Int2IntOpenHashMap();
    private boolean adjacentX, adjacentZ, adjacentCorner;
    private boolean mustCalcX, mustCalcZ, mustCalcCorner;

    private Circuit circuit;
    private TilePos pos;

    public CircuitTile(Circuit circuit, TilePos pos) {
        move(circuit, pos);
    }

    public Circuit getCircuit() {
        return circuit;
    }

    public TilePos getPosition() {
        return pos;
    }

    public void move(Circuit circuit, TilePos pos) {
        this.circuit = circuit;
        this.pos = pos;
        componentPositions.stream().forEach(i -> {
            components[i].updatePosition();
        });
    }

    // Component management

    public Stream<ComponentInstance> stream(TileSection section) {
        var bits = new BitSet();
        bits.or(componentPositions);
        bits.and(section.getBits());
        return bits.stream().mapToObj(i -> components[i]);
    }

    public boolean isEmpty(TileSection section) {
        return switch (section) {
            case ALL -> componentPositions.isEmpty(); // We can assume this (low) cost since it'll be a rare check
            case X_EDGE -> !adjacentX;
            case Z_EDGE -> !adjacentZ;
            case CORNER -> !adjacentCorner;
        };
    }

    public boolean has(ComponentPos pos, ComponentSlot slot) {
        return occupiedPositions.get(getIndex(pos, slot));
    }

    @Nullable
    public ComponentInstance get(ComponentPos pos, ComponentSlot slot) {
        var index = getIndex(pos, slot);
        var instance = components[index];
        if (instance != null){
            return instance;
        }
        return occupiedPositions.get(index) ? components[delegationMap.get(index)] : null;
    }

    public boolean canPut(ComponentPos pos, ComponentType type) {
        for (var slot : type.getAllSlots()) {
            if (has(pos, slot)) {
                return false;
            }
        }
        return true;
    }

    public ComponentInstance put(ComponentPos pos, ComponentType.Factory factory) {
        var info = new ComponentInstance(this, pos, factory);
        var index = getIndex(pos, info.getSlot());
        components[index] = info;
        componentPositions.set(index);
        for (var slot : info.getAllSlots()) {
            var slotIndex = getIndex(pos, slot);
            occupiedPositions.set(slotIndex);
            if (slotIndex != index) {
                delegationMap.put(slotIndex, index);
            }
        }
        syncQueue.set(index);
        circuit.enqueueTileSync(this);
        adjacentX |= pos.isOnXEdge();
        adjacentZ |= pos.isOnZEdge();
        adjacentCorner |= pos.isOnXEdge() && pos.isOnZEdge();
        return info;
    }

    public boolean remove(ComponentPos pos, ComponentSlot slot) {
        var c = get(pos, slot);
        if (c == null) {
            return false;
        }
        var index = getIndex(pos, c.getSlot());
        c.beforeRemove();
        components[index] = null;
        componentPositions.clear(index);
        for (var s : c.getAllSlots()) {
            var slotIndex = getIndex(pos, s);
            occupiedPositions.clear(slotIndex);
            delegationMap.remove(slotIndex);
        }
        syncQueue.set(index);
        circuit.enqueueTileSync(this);
        mustCalcX |= pos.isOnXEdge();
        mustCalcZ |= pos.isOnZEdge();
        mustCalcCorner |= pos.isOnXEdge() && pos.isOnZEdge();
        return true;
    }

    public void clear(TileSection section, @Nullable ComponentHarvestContext context) {
        var cleared = BitSet.valueOf(componentPositions.toLongArray());
        cleared.and(section.getBits());
        cleared.stream().forEach(i -> {
            var c = components[i];
            if (c != null) {
                if (context != null) {
                    c.spawnDrops(context);
                }
                c.beforeRemove();
            }
            components[i] = null;
        });
        componentPositions.andNot(section.getBits());
        occupiedPositions.andNot(section.getBits());
        section.getBits().stream().forEach(delegationMap::remove);
        syncQueue.or(cleared);
        circuit.enqueueTileSync(this);
        switch (section) {
            case ALL -> mustCalcX = mustCalcZ = mustCalcCorner = adjacentX = adjacentZ = adjacentCorner = false;
            case X_EDGE -> mustCalcX = adjacentX = false;
            case Z_EDGE -> mustCalcZ = adjacentZ = false;
            case CORNER -> {
                mustCalcCorner = adjacentCorner = false;
                mustCalcX |= adjacentX;
                mustCalcZ |= adjacentZ;
            }
        }
    }

    public boolean computeAdjacency() {
        var checkSplit = false;
        if (mustCalcX) {
            var prev = adjacentX;
            adjacentX = componentPositions.intersects(TileSection.X_EDGE.getBits());
            checkSplit |= prev && !adjacentX;
            mustCalcX = false;
        }
        if (mustCalcZ) {
            var prev = adjacentZ;
            adjacentZ = componentPositions.intersects(TileSection.Z_EDGE.getBits());
            checkSplit |= prev && !adjacentZ;
            mustCalcZ = false;
        }
        if (mustCalcCorner) {
            var prev = adjacentCorner;
            adjacentCorner = componentPositions.intersects(TileSection.CORNER.getBits());
            checkSplit |= prev && !adjacentCorner;
            mustCalcCorner = false;
        }
        return checkSplit;
    }

    // Redstone signals

    public void notifyRedstoneUpdate(VecDirectionFlags sides) {
        for (var side : sides) {
            var componentsInArea = new BitSet(TOTAL_POSITIONS);
            componentsInArea.or(OUTPUT_SETS[side.getHorizontalIndex()]);
            componentsInArea.and(componentPositions);
            componentsInArea.stream().forEach(i -> {
                var ci = components[i];
                if (ci != null) {
                    circuit.enqueueEventAt(ci.getPosition(), ci.getSlot(), side, CircuitEvent.REDSTONE);
                }
            });
        }
    }

    public int calculateOutput(VecDirection side) {
        var componentsInArea = new BitSet(TOTAL_POSITIONS);
        componentsInArea.or(OUTPUT_SETS[side.getHorizontalIndex()]);
        componentsInArea.and(componentPositions);

        return componentsInArea.stream().map(i -> {
            var ci = components[i];
            return ci != null ? ci.getStrongOutput(side) : 0;
        }).max().orElse(0);
    }

    // Syncing

    public void markForUpdate(ComponentPos pos, ComponentSlot slot) {
        syncQueue.set(getIndex(pos, slot));
        circuit.enqueueTileSync(this);
    }

    public void clearSyncQueue() {
        syncQueue.clear();
    }

    public Map<ComponentSlotPos, ComponentState> getAndClearSyncQueue() {
        if (syncQueue.isEmpty()) {
            return Collections.emptyMap();
        }
        var map = new HashMap<ComponentSlotPos, ComponentState>();
        syncQueue.stream().mapToObj(CircuitHelper::getPositionFromIndex).forEach(p -> {
            var info = get(p.pos(), p.slot());
            map.put(p, info != null ? info.getState() : null);
        });
        syncQueue.clear();
        return map;
    }

    // Serialization

    public CompoundTag describe(CompoundTag tag) {
        var indices = componentPositions.stream().toArray();
        tag.putIntArray("indices", indices);

        var components = new ListTag();
        for (var index : indices) {
            var state = this.components[index].getState();
            components.add(state.serialize(new CompoundTag()));
        }
        tag.put("components", components);

        return tag;
    }

    public CompoundTag save(CompoundTag tag) {
        var indices = componentPositions.stream().toArray();
        tag.putIntArray("indices", indices);

        var components = new ListTag();
        for (var index : indices) {
            components.add(this.components[index].save(new CompoundTag()));
        }
        tag.put("components", components);

        return tag;
    }

    public static CircuitTile load(Circuit circuit, TilePos pos, CompoundTag tag) {
        var tile = new CircuitTile(circuit, pos);

        var indices = tag.getIntArray("indices");
        var components = tag.getList("components", Tag.TAG_COMPOUND);
        for (int i = 0; i < components.size(); i++) {
            var index = indices[i];
            var cPos = getPositionFromIndex(index);
            var component = ComponentInstance.load(tile, cPos.pos(), components.getCompound(i));
            if (component != null) {
                tile.components[index] = component;
                tile.componentPositions.set(index);
                for (var slot : component.getAllSlots()) {
                    var slotIndex = getIndex(cPos.pos(), slot);
                    tile.occupiedPositions.set(slotIndex);
                    if (slotIndex != index) {
                        tile.delegationMap.put(slotIndex, index);
                    }
                }
            }
        }

        tile.mustCalcX = tile.mustCalcZ = tile.mustCalcCorner = true;
        tile.computeAdjacency();
        return tile;
    }

    private static final BitSet[] OUTPUT_SETS = new BitSet[4];

    static {
        for (var side : VecDirectionFlags.horizontals()) {
            var outputs = new BitSet(TOTAL_POSITIONS);
            for (int i = 0; i < SIZE_MINUS_ONE; i++) {
                var pos = new ComponentPos(
                        side.getAxis() != Direction.Axis.X ? i : (side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? SIZE - 2 : 0),
                        0,
                        side.getAxis() != Direction.Axis.Z ? i : (side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? SIZE - 2 : 0)
                );
                outputs.set(getIndex(pos, ComponentSlot.DEFAULT));
            }
            OUTPUT_SETS[side.getHorizontalIndex()] = outputs;
        }
    }

}
