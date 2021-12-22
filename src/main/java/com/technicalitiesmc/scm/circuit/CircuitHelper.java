package com.technicalitiesmc.scm.circuit;

import com.technicalitiesmc.lib.circuit.component.ComponentSlot;
import com.technicalitiesmc.lib.math.IndexedShape;
import com.technicalitiesmc.scm.circuit.util.ComponentPos;
import com.technicalitiesmc.scm.circuit.util.ComponentSlotPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CircuitHelper {

    public static final int SIZE = 8;
    public static final int SIZE_MINUS_ONE = SIZE - 1;
    public static final int SIZE_PLUS_ONE = SIZE + 1;
    public static final int HEIGHT = 4;
    public static final int SLOTS = ComponentSlot.VALUES.length;
    public static final int TOTAL_POSITIONS = SIZE * SIZE * HEIGHT * SLOTS;

    public static ComponentSlotPos resolvePositionFromShapeIndex(int index) {
        var slot = Math.floorMod(index, SLOTS);
        index = Math.floorDiv(index, SLOTS);
        var z = Math.floorMod(index, SIZE_PLUS_ONE) - 1;
        index = Math.floorDiv(index, SIZE_PLUS_ONE);
        var x = Math.floorMod(index, SIZE_PLUS_ONE) - 1;
        var y = Math.floorDiv(index, SIZE_PLUS_ONE);
        return new ComponentSlotPos(x, y, z, ComponentSlot.VALUES[slot]);
    }

    public static int createShapeIndex(ComponentPos pos, ComponentSlot slot) {
        return ((pos.y() * SIZE_PLUS_ONE + pos.x() + 1) * SIZE_PLUS_ONE + pos.z() + 1) * SLOTS + slot.ordinal();
    }

    public static VoxelShape createShape(AABB aabb, ComponentPos pos, ComponentSlot slot) {
        return IndexedShape.create(createShapeIndex(pos, slot), scaleAndTranslate(aabb, pos));
    }

    private static AABB scaleAndTranslate(AABB aabb, ComponentPos pos) {
        return new AABB(
                aabb.minX / SIZE, aabb.minY / SIZE, aabb.minZ / SIZE,
                aabb.maxX / SIZE, aabb.maxY / SIZE, aabb.maxZ / SIZE
        ).move((pos.x() + 0.5) / SIZE, (pos.y() + 1d) / SIZE, (pos.z() + 0.5) / SIZE);
    }

    public static int getIndex(ComponentPos pos, ComponentSlot slot) {
        return pos.x() + SIZE * (pos.z() + SIZE * (pos.y() + HEIGHT * slot.ordinal()));
    }

    public static ComponentSlotPos getPositionFromIndex(int index) {
        var x = index % SIZE;
        index /= SIZE;
        var z = index % SIZE;
        index /= SIZE;
        var y = index % HEIGHT;
        var slot = index / HEIGHT;
        return new ComponentSlotPos(x, y, z, ComponentSlot.VALUES[slot]);
    }

}
