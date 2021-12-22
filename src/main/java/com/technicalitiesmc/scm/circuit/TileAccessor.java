package com.technicalitiesmc.scm.circuit;

import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Consumer;

public interface TileAccessor {

    boolean isAreaEmpty();

    void clearArea();

    void visitAreaShapes(Consumer<VoxelShape> consumer);

}
