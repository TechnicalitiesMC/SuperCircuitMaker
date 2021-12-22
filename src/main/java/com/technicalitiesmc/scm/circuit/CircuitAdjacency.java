package com.technicalitiesmc.scm.circuit;

public enum CircuitAdjacency {
    NONE,
    BOTH_FULL,
    VERTICAL,
    HORIZONTAL,
    BOTH_PARTIAL;

    public static CircuitAdjacency[] VALUES = values();
}
