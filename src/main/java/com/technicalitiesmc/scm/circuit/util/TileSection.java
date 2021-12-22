package com.technicalitiesmc.scm.circuit.util;

import com.technicalitiesmc.lib.circuit.component.ComponentSlot;
import com.technicalitiesmc.lib.math.IndexedShape;
import com.technicalitiesmc.scm.circuit.CircuitHelper;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.BitSet;
import java.util.stream.Stream;

import static com.technicalitiesmc.scm.circuit.CircuitHelper.*;

public enum TileSection {
    ALL(0, 0) {
        @Override
        public VoxelShape offsetNeg(VoxelShape shape) {
            return shape;
        }
    },
    X_EDGE(1, 0),
    Z_EDGE(0, 1),
    CORNER(1, 1);

    public static final TileSection[] VALUES = values();
    public static final TileSection[] NEIGHBORS = { X_EDGE, Z_EDGE, CORNER };

    static {
        ALL.bits.set(0, TOTAL_POSITIONS);
        for (var slot : ComponentSlot.VALUES) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int i = 0; i < SIZE; i++) {
                    X_EDGE.bits.set(getIndex(new ComponentPos(SIZE_MINUS_ONE, y, i), slot));
                    Z_EDGE.bits.set(getIndex(new ComponentPos(i, y, SIZE_MINUS_ONE), slot));
                }
                CORNER.bits.set(getIndex(new ComponentPos(SIZE_MINUS_ONE, y, SIZE_MINUS_ONE), slot));
            }
        }
    }

    private final BitSet bits = new BitSet(CircuitHelper.TOTAL_POSITIONS);
    private final int xOffset, zOffset;

    TileSection(int xOffset, int zOffset) {
        this.xOffset = xOffset;
        this.zOffset = zOffset;
    }

    public BitSet getBits() {
        return bits;
    }

    public int getXOffset() {
        return xOffset;
    }

    public int getZOffset() {
        return zOffset;
    }

    public VoxelShape offsetNeg(VoxelShape shape) {
        return shape.move(-xOffset, 0, -zOffset);
    }

    public VoxelShape offsetNeg(VoxelShape shape, int newIndex) {
        var bounds = shape.bounds().move(-xOffset, 0, -zOffset);
        return IndexedShape.create(newIndex, bounds);
    }

    public Stream<ComponentSlotPos> stream() {
        return bits.stream().mapToObj(CircuitHelper::getPositionFromIndex);
    }

}
