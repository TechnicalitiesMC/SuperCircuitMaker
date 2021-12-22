package com.technicalitiesmc.scm.circuit.util;

import com.technicalitiesmc.scm.circuit.CircuitHelper;
import net.minecraft.core.Vec3i;

public record UnpackedPos(TilePos tile, ComponentPos pos) {

    public static UnpackedPos of(Vec3i packed) {
        int x = packed.getX(), y = packed.getY(), z = packed.getZ();
        return new UnpackedPos(
                new TilePos(Math.floorDiv(x, CircuitHelper.SIZE), Math.floorDiv(z, CircuitHelper.SIZE)),
                new ComponentPos(Math.floorMod(x, CircuitHelper.SIZE), y, Math.floorMod(z, CircuitHelper.SIZE))
        );
    }

}
