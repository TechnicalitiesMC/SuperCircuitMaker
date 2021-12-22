package com.technicalitiesmc.scm.component;

import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.math.VecDirection;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;

public abstract class CircuitComponentBase<T extends CircuitComponentBase<T>> extends CircuitComponent {

    private final InterfaceLookup<T> interfaceLookup;

    protected CircuitComponentBase(RegistryObject<ComponentType> type, ComponentContext context, InterfaceLookup<T> interfaceLookup) {
        super(type.get(), context);
        this.interfaceLookup = interfaceLookup;
    }

    @Nullable
    @Override
    public <V> V getInterface(VecDirection side, Class<V> type) {
        return interfaceLookup.get((T) this, side, type);
    }

    protected boolean ensureSupported(ComponentEventMap events) {
        if (events.hasAny(VecDirection.NEG_Y, CircuitEvent.NEIGHBOR_CHANGED)) {
            var neighbor = getNeighbor(VecDirection.NEG_Y, ComponentSlot.SUPPORT);
            if (neighbor == null || !neighbor.isTopSolid()) {
                scheduleRemoval();
                return false;
            }
        }
        return true;
    }

    // Helpers

    protected final int getStrongInput(VecDirection direction, boolean adjacentOnly) {
        var redstoneSource = getNeighborInterface(direction, RedstoneSource.class, adjacentOnly);
        return redstoneSource != null ? redstoneSource.getStrongOutput() : 0;
    }

    protected final int getWeakInput(VecDirection direction, boolean adjacentOnly) {
        var redstoneSource = getNeighborInterface(direction, RedstoneSource.class, adjacentOnly);
        return redstoneSource != null ? redstoneSource.getWeakOutput() : 0;
    }

}
