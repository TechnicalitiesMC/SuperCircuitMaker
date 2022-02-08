package com.technicalitiesmc.scm.component.wire;

import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.scm.component.CircuitComponentBase;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.component.misc.LevelIOComponent;
import com.technicalitiesmc.lib.circuit.interfaces.wire.WireConnectionState;
import com.technicalitiesmc.lib.circuit.interfaces.wire.Wire;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.Arrays;

public abstract class WireComponentBase<T extends WireComponentBase<T>> extends CircuitComponentBase<T> implements Wire {

    // External state
    private final WireConnectionState[] connections = new WireConnectionState[VecDirection.VALUES.length];
    private final WireConnectionState[] connectionsInternal = new WireConnectionState[VecDirection.VALUES.length];

    protected WireComponentBase(RegistryObject<ComponentType> type, ComponentContext context, InterfaceLookup<T> interfaceLookup) {
        super(type, context, interfaceLookup);
        Arrays.fill(connections, WireConnectionState.DISCONNECTED);
        Arrays.fill(connectionsInternal, WireConnectionState.DISCONNECTED);
    }

    protected abstract VecDirectionFlags getConnectableSides();

    @Nullable
    protected abstract CircuitComponent findConnectionTarget(VecDirection side);

    protected abstract WireConnectionState getNextState(VecDirection side, WireConnectionState currentState, CircuitComponent neighbor, boolean forced);

    protected abstract boolean isValidState(VecDirection side, WireConnectionState state, CircuitComponent neighbor);

    protected abstract void onStateTransition(VecDirection side, WireConnectionState prevState, WireConnectionState newState);

    protected abstract void updateSignals(VecDirectionFlags sides);

    protected final WireConnectionState getState(VecDirection side) {
        return connections[side.ordinal()];
    }

    protected final WireConnectionState getStateInternal(VecDirection side) {
        return connectionsInternal[side.ordinal()];
    }

    protected final void setStateInternal(VecDirection side, WireConnectionState newState) {
        var prevState = getStateInternal(side);
        setState(side, newState);
        onStateTransition(side, prevState, newState);
    }

    @Override
    public void setState(VecDirection side, WireConnectionState state) {
        connectionsInternal[side.ordinal()] = state;
        updateExternalState(true, () -> {
            connections[side.ordinal()] = state;
        });
    }

    @Override
    public void onAdded() {
        super.onAdded();
        var sides = getConnectableSides();
        computeConnections(sides, sides);
        scheduleSequential();
    }

    @Override
    public void update(ComponentEventMap events, boolean tick) {
        var neighborUpdates = events.findAny(CircuitEvent.NEIGHBOR_CHANGED).onlyIn(getConnectableSides());
        var redstoneUpdates = events.findAny(CircuitEvent.REDSTONE).onlyIn(getConnectableSides());
        computeConnections(neighborUpdates, redstoneUpdates);
    }

    private void computeConnections(VecDirectionFlags neighborUpdates, VecDirectionFlags redstoneUpdates) {
        // Update connection states on all affected sides
        var changedSides = VecDirectionFlags.none();
        for (var side : neighborUpdates) {
            var newState = computeNewState(side, false);
            if (newState != getStateInternal(side)) {
                setStateInternal(side, newState);
                changedSides = changedSides.and(side);
            }
        }

        // Also update signal states on all affected sides
        var signalUpdates = changedSides.and(redstoneUpdates);
        if (!signalUpdates.isEmpty()) {
            updateSignals(signalUpdates);
        }
    }

    protected final WireConnectionState computeNewState(VecDirection side, boolean forceTransition) {
        var currentState = getStateInternal(side);
        var neighbor = findConnectionTarget(side);

        // If not forcefully transitioning state and the neighbor is an I/O component, avoid connecting
        if (!forceTransition && neighbor instanceof LevelIOComponent) {
            return WireConnectionState.DISCONNECTED;
        }

        // If the current state is force-disconnected
        if (currentState == WireConnectionState.FORCE_DISCONNECTED && !forceTransition) {
            // If we don't have a neighbor anymore, set to disconnected, otherwise stay force-disconnected
            return neighbor == null ? WireConnectionState.DISCONNECTED : currentState;
        }

        // If we have no neighbor, disconnect
        if (neighbor == null) {
            return WireConnectionState.DISCONNECTED;
        }

        // If the current state isn't sustainable or we must force a transition
        if (forceTransition || currentState == WireConnectionState.DISCONNECTED || !isValidState(side, currentState, neighbor)) {
            // Attempt a state transition
            var newState = getNextState(side, forceTransition ? currentState : WireConnectionState.DISCONNECTED, neighbor, forceTransition);
            if (forceTransition && newState == WireConnectionState.DISCONNECTED) {
                return WireConnectionState.FORCE_DISCONNECTED;
            }
            return newState;
        }

        return currentState;
    }

    // Serialization

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag = super.save(tag);
        var states = new int[VecDirection.VALUES.length];
        for (int i = 0; i < connections.length; i++) {
            states[i] = connections[i].serialize();
        }
        tag.putIntArray("connection_states", states);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        var states = tag.getIntArray("connection_states");
        for (int i = 0; i < connections.length; i++) {
            connectionsInternal[i] = connections[i] = WireConnectionState.deserialize(states[i]);
        }
    }

}
