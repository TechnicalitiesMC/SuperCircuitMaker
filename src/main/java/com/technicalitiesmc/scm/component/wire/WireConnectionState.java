package com.technicalitiesmc.scm.component.wire;

import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSink;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.circuit.interfaces.wire.BundledWire;
import com.technicalitiesmc.lib.circuit.interfaces.wire.RedstoneWire;

public enum WireConnectionState {
    DISCONNECTED(null, WireVisualConnectionState.DISCONNECTED),
    FORCE_DISCONNECTED(null, WireVisualConnectionState.DISCONNECTED),
    WIRE(RedstoneWire.class, WireVisualConnectionState.ANODE),
    BUNDLED_WIRE(BundledWire.class, WireVisualConnectionState.ANODE),
    INPUT(RedstoneSource.class, WireVisualConnectionState.CATHODE),
    OUTPUT(RedstoneSink.class, WireVisualConnectionState.ANODE);

    public static final WireConnectionState[] VALUES = values();

    private final Class<?> targetInterface;
    private final WireVisualConnectionState visualState;

    WireConnectionState(Class<?> targetInterface, WireVisualConnectionState visualState) {
        this.targetInterface = targetInterface;
        this.visualState = visualState;
    }

    public Class<?> getTargetInterface() {
        return targetInterface;
    }

    public WireConnectionState getOpposite() {
        return switch (this) {
            case INPUT -> OUTPUT;
            case OUTPUT -> INPUT;
            default -> this;
        };
    }

    public WireVisualConnectionState getVisualState() {
        return visualState;
    }

    public boolean isConnected() {
        return this != DISCONNECTED && this != FORCE_DISCONNECTED;
    }

    public boolean isWire() {
        return this == WIRE || this == BUNDLED_WIRE;
    }
}
