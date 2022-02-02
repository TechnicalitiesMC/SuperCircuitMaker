package com.technicalitiesmc.scm.component.wire;

import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.scm.component.CircuitComponentBase;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.component.misc.LevelIOComponent;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.registries.RegistryObject;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

public abstract class WireComponentBase<T extends WireComponentBase<T>> extends CircuitComponentBase<T> {

    private static final WireConnectionState[] CONNECTION_PRIORITIES = {
            WireConnectionState.BUNDLED_WIRE,
            WireConnectionState.WIRE,
            WireConnectionState.OUTPUT,
            WireConnectionState.INPUT
    };

    // Internal state
    private final Map<VecDirection, WireConnectionState> connectionStates = new EnumMap<>(VecDirection.class);

    // External state
    private final Map<VecDirection, WireVisualConnectionState> connectionVisualStates = new EnumMap<>(VecDirection.class);

    protected WireComponentBase(RegistryObject<ComponentType> type, ComponentContext context, InterfaceLookup<T> interfaceLookup) {
        super(type, context, interfaceLookup);
        for (var side : getConnectableSides()) {
            connectionStates.put(side, WireConnectionState.DISCONNECTED);
            connectionVisualStates.put(side, WireVisualConnectionState.DISCONNECTED);
        }
    }

    protected WireComponentBase(
            RegistryObject<ComponentType> type, ComponentContext context, InterfaceLookup<T> interfaceLookup,
            Map<VecDirection, WireConnectionState> connectionStates
    ) {
        this(type, context, interfaceLookup);
        this.connectionStates.putAll(connectionStates);
        connectionStates.forEach((d, s) -> this.connectionVisualStates.put(d, s.getVisualState()));
    }

    protected abstract VecDirectionFlags getConnectableSides();

    protected abstract T makeRotatedCopy(ComponentContext context, Rotation rotation, Map<VecDirection, WireConnectionState> connectionStates);

    protected abstract boolean isBundled();

    protected abstract void updateSignals(ComponentEventMap events, VecDirectionFlags disconnected);

    protected abstract void invalidateNetworks();

    @Nullable
    protected CircuitComponent getConnectionTarget(VecDirection side) {
        return findNeighbor(side);
    }

    protected WireConnectionState[] getConnectionPriorities() {
        return CONNECTION_PRIORITIES;
    }

    @Override
    public CircuitComponent copyRotated(ComponentContext context, Rotation rotation) {
        return makeRotatedCopy(context, rotation, getConnectionStates()); // TODO: [URGENT] actually rotate
    }

    @Override
    public void onAdded() {
        updateConnections(getConnectableSides(), true);

        var events = new ComponentEventMap.Builder();
        events.add(getConnectableSides(), CircuitEvent.REDSTONE, CircuitEvent.BUNDLED_REDSTONE);
        updateSignals(events.build(), VecDirectionFlags.none());
    }

    @Override
    public void beforeRemove() {
        super.beforeRemove();
        invalidateNetworks();
        for (var side : getConnectableSides()) {
            if (getState(side).isWire() && getConnectionTarget(side) instanceof WireComponentBase<?> wire) {
                wire.setState(side.getOpposite(), WireConnectionState.DISCONNECTED);
            }
        }
    }

    @Override
    public void update(ComponentEventMap events, boolean tick) {
        var updates = events.findAny(CircuitEvent.NEIGHBOR_CHANGED).onlyIn(getConnectableSides());
        var pair = updateConnections(updates, false);
        var disconnected = pair.getLeft();
        var recalculateNetwork = pair.getRight();

        if (recalculateNetwork) {
            invalidateNetworks();
        }

        updateSignals(events, disconnected);
    }

    private Pair<VecDirectionFlags, Boolean> updateConnections(VecDirectionFlags neighborUpdates, boolean connectToWires) {
        var disconnected = VecDirectionFlags.none();
        var recalculateNetwork = false;

        // For each side where we received an update
        for (var side : neighborUpdates) {
            var state = getState(side);
            var neighbor = getConnectionTarget(side);
            var neighborSide = side.getOpposite();

            if (neighbor instanceof LevelIOComponent || (!connectToWires && side.getAxis() != Direction.Axis.Y && neighbor instanceof WireComponentBase<?>)) {
                continue;
            }

            // If there is no neighbor
            if (neighbor == null || (state.isWire() && neighbor.getInterface(neighborSide, state.getTargetInterface(isBundled())) == null)) {
                // And it wasn't disconnected
                if (state != WireConnectionState.DISCONNECTED) {
                    // If it was connected to a wire, schedule a network recalculation
                    recalculateNetwork |= state.isWire();
                    // Set state to disconnected
                    setState(side, WireConnectionState.DISCONNECTED);
                    disconnected = disconnected.and(side);
                }
                // Nothing else to do without a neighbor
                continue;
            }

            // If it was connected
            var disconnecting = false;
            if (state.isConnected()) {
                // Find out to what
                var previousConnection = neighbor.getInterface(neighborSide, state.getTargetInterface(isBundled()));

                // If it is not connected anymore
                if (previousConnection == null) {
                    // Mark for disconnection
                    disconnecting = true;
                }
            }

            // If it's not connected
            if (state == WireConnectionState.DISCONNECTED || disconnecting) {
                // Go through the connection priority list
                for (var potentialState : getConnectionPriorities()) {
                    // If we find a matching interface
                    var itf = neighbor.getInterface(neighborSide, potentialState.getTargetInterface(isBundled()));
                    if (itf != null) {
                        // If we are connecting to a wire, schedule a network recalculation
                        recalculateNetwork |= potentialState.isWire();
                        // Set connection state
                        setState(side, potentialState);
                        state = potentialState;
                        break;
                    }
                }
                // If a connection was made, we're done
                if (state.isConnected()) {
                    if (neighbor instanceof WireComponentBase<?> wire) {
                        wire.updateConnections(VecDirectionFlags.of(side.getOpposite()), true);
                    }
                    continue;
                }

                // Otherwise, if we are disconnecting
                if (disconnecting) {
                    // Set state to disconnected
                    setState(side, WireConnectionState.DISCONNECTED);
                    disconnected = disconnected.and(side);
                }
            }
        }

        return Pair.of(disconnected, recalculateNetwork);
    }

    protected Map<VecDirection, WireConnectionState> getConnectionStates() {
        return connectionStates;
    }

    protected final WireConnectionState getState(VecDirection side) {
        return connectionStates.getOrDefault(side, WireConnectionState.DISCONNECTED);
    }

    protected final WireVisualConnectionState getVisualState(VecDirection side) {
        return connectionVisualStates.getOrDefault(side, WireVisualConnectionState.DISCONNECTED);
    }

    protected void setState(VecDirection side, WireConnectionState state) {
        var previousState = connectionStates.put(side, state);
        if ((previousState != null && previousState.isWire()) || state.isWire()) {
            invalidateNetworks();
        }
        updateExternalState(true, () -> {
            connectionVisualStates.put(side, state.getVisualState());
        });
        sendEvent(CircuitEvent.NEIGHBOR_CHANGED, true, side);
    }

    // Serialization

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag = super.save(tag);
        var states = new int[VecDirection.VALUES.length];
        connectionStates.forEach((dir, state) -> {
            states[dir.ordinal()] = state.ordinal();
        });
        tag.putIntArray("connection_states", states);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        var states = tag.getIntArray("connection_states");
        for (var dir : getConnectableSides()) {
            var state = WireConnectionState.VALUES[states[dir.ordinal()]];
            connectionStates.put(dir, state);
            connectionVisualStates.put(dir, state.getVisualState());
        }
    }

}
