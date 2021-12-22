package com.technicalitiesmc.scm.circuit.util;

import static com.technicalitiesmc.scm.circuit.CircuitHelper.SIZE_MINUS_ONE;

public record ComponentPos(int x, int y, int z) {

    public boolean isOnXEdge() {
        return x() == SIZE_MINUS_ONE;
    }

    public boolean isOnZEdge() {
        return z() == SIZE_MINUS_ONE;
    }

    public TileSection getSection() {
        var xEdge = isOnXEdge();
        var zEdge = isOnZEdge();
        return xEdge ? zEdge ? TileSection.CORNER : TileSection.X_EDGE : zEdge ? TileSection.Z_EDGE : TileSection.ALL;
    }

}
