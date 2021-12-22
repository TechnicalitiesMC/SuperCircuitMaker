package com.technicalitiesmc.scm.circuit.util;

import com.technicalitiesmc.lib.math.Vec2i;
import net.minecraft.core.Vec3i;

import static com.technicalitiesmc.scm.circuit.CircuitHelper.SIZE;

public record TilePos(int x, int z) {

    public static final TilePos ZERO = new TilePos(0, 0);

    public TilePos(int[] values) {
        this(values[0], values[1]);
    }

    public boolean isZero() {
        return x() == 0 && z() == 0;
    }

    public TilePos offset(int x, int z) {
        return new TilePos(x() + x, z() + z);
    }

    public TilePos offset(Vec2i vec) {
        return offset(vec.x(), vec.y());
    }

    public TilePos offset(TileSection section) {
        return offset(section.getXOffset(), section.getZOffset());
    }

    public TilePos offsetNeg(TileSection section) {
        return offset(-section.getXOffset(), -section.getZOffset());
    }

    public Vec3i pack(ComponentPos pos) {
        return pack(pos.x(), pos.y(), pos.z());
    }

    public Vec3i pack(Vec3i pos) {
        return pack(pos.getX(), pos.getY(), pos.getZ());
    }

    public Vec3i pack(int x, int y, int z) {
        return new Vec3i(x() * SIZE + x, y, z() * SIZE + z);
    }

    public Vec2i subtract(TilePos pos) {
        return new Vec2i(x() - pos.x(), z() - pos.z());
    }

    public int[] toArray() {
        return new int[] { x(), z() };
    }

}
