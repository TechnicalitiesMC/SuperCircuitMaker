package com.technicalitiesmc.scm.component.wire;

import com.technicalitiesmc.lib.circuit.interfaces.BundledSink;
import com.technicalitiesmc.lib.circuit.interfaces.BundledSource;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSink;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.circuit.interfaces.wire.BundledWire;
import com.technicalitiesmc.lib.circuit.interfaces.wire.RedstoneWire;

public enum WireConnectionState {
    DISCONNECTED(null, null, WireVisualConnectionState.DISCONNECTED),
    FORCE_DISCONNECTED(null, null, WireVisualConnectionState.DISCONNECTED),
    WIRE(RedstoneWire.class, RedstoneWire.class, WireVisualConnectionState.ANODE),
    BUNDLED_WIRE(BundledWire.class, BundledWire.class, WireVisualConnectionState.ANODE),
    INPUT(RedstoneSource.class, BundledSource.class, WireVisualConnectionState.CATHODE),
    OUTPUT(RedstoneSink.class, BundledSink.class, WireVisualConnectionState.ANODE);

    public static final WireConnectionState[] VALUES = values();

    private final Class<?> redstoneTargetInterface, bundledTargetInterface;
    private final WireVisualConnectionState visualState;

    WireConnectionState(Class<?> redstoneTargetInterface, Class<?> bundledTargetInterface, WireVisualConnectionState visualState) {
        this.redstoneTargetInterface = redstoneTargetInterface;
        this.bundledTargetInterface = bundledTargetInterface;
        this.visualState = visualState;
    }

    public Class<?> getTargetInterface(boolean bundled) {
        return bundled ? bundledTargetInterface : redstoneTargetInterface;
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
