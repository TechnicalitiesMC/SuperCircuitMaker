package com.technicalitiesmc.scm.component.wire;

import com.technicalitiesmc.lib.circuit.component.CircuitComponent;
import com.technicalitiesmc.lib.circuit.interfaces.wire.WireConnectionState;
import com.technicalitiesmc.lib.circuit.interfaces.wire.Wire;
import com.technicalitiesmc.lib.math.VecDirection;

public class WireUtils {

    // Transitions:
    //  - disconnected -> wire/output/input(/disconnected)
    //  - wire/output -> input/disconnected
    //  - input -> disconnected
    public static WireConnectionState getNextState(VecDirection side, WireConnectionState state, CircuitComponent neighbor, Class<?> sourceClass, Class<?> sinkClass) {
        // If we are currently disconnected, try switching to output/wire mode
        if (state.isDisconnected()) {
            if (neighbor.getInterface(side.getOpposite(), Wire.class) != null) {
                return WireConnectionState.WIRE;
            } else if (neighbor.getInterface(side.getOpposite(), sinkClass) != null) {
                return WireConnectionState.OUTPUT;
            }
        }
        // If we aren't in input mode already, and the neighbor is either a redstone source or another wire, connect
        if (state != WireConnectionState.INPUT && (neighbor.getInterface(side.getOpposite(), Wire.class) != null || neighbor.getInterface(side.getOpposite(), sourceClass) != null)) {
            return WireConnectionState.INPUT;
        }
        // If all fails, disconnect
        return WireConnectionState.DISCONNECTED;
    }

    public static boolean isValidState(VecDirection side, WireConnectionState state, CircuitComponent neighbor, Class<?> sourceClass, Class<?> sinkClass) {
        if (state == WireConnectionState.DISCONNECTED || state == WireConnectionState.FORCE_DISCONNECTED) {
            return true;
        }
        if (neighbor == null) {
            return false;
        }
        if (neighbor.getInterface(side.getOpposite(), Wire.class) != null) {
            return true;
        }
        return switch (state) {
            case INPUT -> neighbor.getInterface(side.getOpposite(), sourceClass) != null;
            case OUTPUT -> neighbor.getInterface(side.getOpposite(), sinkClass) != null;
            default -> false;
        };
    }

}
