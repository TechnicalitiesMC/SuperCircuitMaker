package com.technicalitiesmc.scm.circuit.client;

import com.technicalitiesmc.lib.circuit.component.ComponentSlot;
import com.technicalitiesmc.lib.circuit.component.ComponentState;
import com.technicalitiesmc.lib.circuit.component.ComponentType;
import com.technicalitiesmc.lib.math.Vec2i;
import com.technicalitiesmc.scm.circuit.CircuitAdjacency;
import com.technicalitiesmc.scm.circuit.CircuitHelper;
import com.technicalitiesmc.scm.circuit.TileAccessor;
import com.technicalitiesmc.scm.circuit.util.TileSection;
import com.technicalitiesmc.scm.circuit.util.ComponentPos;
import com.technicalitiesmc.scm.circuit.util.TilePos;
import com.technicalitiesmc.scm.circuit.util.UnpackedPos;
import com.technicalitiesmc.scm.client.model.CircuitModelData;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;

import static com.technicalitiesmc.scm.circuit.CircuitHelper.*;

public class ClientTile implements TileAccessor {

    private final ComponentState[] components = new ComponentState[TOTAL_POSITIONS];
    private final VoxelShape[] shapes = new VoxelShape[TOTAL_POSITIONS];
    private final CircuitAdjacency[] adjacency = new CircuitAdjacency[4];

    private final Host host;

    public ClientTile(Host host) {
        this.host = host;
        Arrays.fill(adjacency, CircuitAdjacency.NONE);
    }

    @Nullable
    private ClientTile getTile(TileSection section) {
        if (section == TileSection.ALL) {
            return this;
        }
        return host.getClientNeighbor(new Vec2i(-section.getXOffset(), -section.getZOffset()));
    }

    @Nullable
    private ClientTile getTile(TilePos pos) {
        if (pos.isZero()) {
            return this;
        }
        return host.getClientNeighbor(new Vec2i(pos.x(), pos.z()));
    }

    @Override
    public boolean isAreaEmpty() {
        for (var section : TileSection.VALUES) {
            var tile = getTile(section);
            if (tile != null) {
                if (section.getBits().stream().anyMatch(i -> tile.components[i] != null)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void clearArea() {
        for (var section : TileSection.VALUES) {
            var tile = getTile(section);
            if (tile != null) {
                for (int i = 0; i < TOTAL_POSITIONS; i++) {
                    tile.components[i] = null;
                    tile.shapes[i] = null;
                }
            }
        }
    }

    public boolean hasNeighbor(Vec2i offset) {
        return host.getClientNeighbor(offset) != null;
    }

    @Nullable
    public ComponentState getState(Vec3i pos, ComponentSlot slot) {
        var unpacked = UnpackedPos.of(pos);
        var tile = getTile(unpacked.tile());
        return tile != null ? tile.components[getIndex(unpacked.pos(), slot)] : null;
    }

    public boolean canFit(Vec3i pos, ComponentType type) { // TODO: move to accessor
        var unpacked = UnpackedPos.of(pos);
        if (unpacked.pos().y() < 0 || unpacked.pos().y() >= HEIGHT) {
            return false;
        }
        var tile = getTile(unpacked.tile());
        if (tile != null) {
            for (var slot : type.getAllSlots()) {
                if (tile.components[getIndex(unpacked.pos(), slot)] != null) {
                    return false;
                }
            }
        }
        return true;
    }

    public void setState(Vec3i pos, ComponentSlot slot, @Nullable ComponentState state) { // TODO: move to accessor
        var unpacked = UnpackedPos.of(pos);
        if (unpacked.pos().y() < 0 || unpacked.pos().y() >= HEIGHT) {
            return;
        }
        var tile = getTile(unpacked.tile());
        if (tile != null) {
            var index = getIndex(unpacked.pos(), slot);
            tile.components[index] = state;
            tile.shapes[index] = state != null ? CircuitHelper.createShape(state.getBoundingBox(), unpacked.pos(), slot) : null;
            tile.host.onUpdateReceived();
        }
    }

    public void setAdjacency(CircuitAdjacency[] adjacency) {
        System.arraycopy(adjacency, 0, this.adjacency, 0, 4);
        host.onUpdateReceived();
    }

    @Override
    public void visitAreaShapes(Consumer<VoxelShape> consumer) {
        for (var section : TileSection.VALUES) {
            var tile = getTile(section);
            if (tile != null) {
                section.getBits().stream().forEach(i -> {
                    var shape = tile.shapes[i];
                    if (shape != null) {
                        var pos = getPositionFromIndex(i);
                        var newPos = new ComponentPos(
                                pos.pos().x() - SIZE * section.getXOffset(),
                                pos.pos().y(),
                                pos.pos().z() - SIZE * section.getZOffset()
                        );
                        var index = createShapeIndex(newPos, pos.slot());
                        consumer.accept(section.offsetNeg(shape, index));
                    }
                });
            }
        }
    }

    public CircuitModelData getModelData() {
        var list = new ArrayList<Pair<Vec3i, ComponentState>>();
        for (int i = 0; i < TOTAL_POSITIONS; i++) {
            var state = components[i];
            if (state != null) {
                var pos = getPositionFromIndex(i);
                list.add(Pair.of(pos.toAbsolute().pos(), state));
            }
        }
        return new CircuitModelData(list, adjacency);
    }

    public static ClientTile fromDescription(Host host, CompoundTag tag) {
        var tile = new ClientTile(host);

        var indices = tag.getIntArray("indices");
        var components = tag.getList("components", Tag.TAG_COMPOUND);
        for (int i = 0; i < components.size(); i++) {
            var idx = indices[i];
            var state = ComponentState.deserialize(components.getCompound(i));
            if (state != null) {
                var pos = getPositionFromIndex(idx);
                tile.setState(pos.toAbsolute().pos(), pos.slot(), state);
            }
        }

        var adjInt = tag.getIntArray("adjacency");
        for (int i = 0; i < adjInt.length; i++) {
            tile.adjacency[i] = CircuitAdjacency.VALUES[adjInt[i]];
        }

        return tile;
    }

    public interface Host {

        @Nullable
        ClientTile getClientNeighbor(Vec2i offset);

        void onUpdateReceived();

    }

}
