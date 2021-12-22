package com.technicalitiesmc.scm.circuit.util;

import com.technicalitiesmc.lib.circuit.component.ComponentSlot;
import com.technicalitiesmc.lib.math.Vec2i;
import net.minecraft.core.Vec3i;

import static com.technicalitiesmc.scm.circuit.CircuitHelper.SIZE;

public record AbsoluteSlotPos(Vec3i pos, ComponentSlot slot) {

    public AbsoluteSlotPos(int x, int y, int z, ComponentSlot slot) {
        this(new Vec3i(x, y, z), slot);
    }

    public AbsoluteSlotPos(int[] array) {
        this(array[0], array[1], array[2], ComponentSlot.VALUES[array[3]]);
    }

    public AbsoluteSlotPos offset(Vec2i offset) {
        return new AbsoluteSlotPos(pos().getX() + offset.x() * SIZE, pos.getY(), pos.getZ() + offset.y() * SIZE, slot());
    }

    public int[] toArray() {
        return new int[] { pos().getX(), pos().getY(), pos().getZ(), slot().ordinal() };
    }

}
