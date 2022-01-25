package com.technicalitiesmc.scm.component;

import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.math.VecDirection;
import net.minecraft.core.Direction;
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
            if (!isTopSolid(VecDirection.NEG_Y.getOffset())) {
                scheduleRemoval();
                return false;
            }
        }
        return true;
    }

    // Helpers

    @Nullable
    protected final CircuitComponent findNeighbor(VecDirection direction) {
        if (direction.getAxis() != Direction.Axis.Y) {
            return getNeighbor(direction, ComponentSlot.DEFAULT);
        }
        var slot = getType().getSlot();
        var dir = direction.getAxisDirection();
        return getComponentAt(slot.getOffsetTowards(dir), slot.next(dir));
    }

    @Nullable
    protected final <V> V findNeighborInterface(VecDirection direction, Class<V> type) {
        var neighbor = findNeighbor(direction);
        return neighbor != null ? neighbor.getInterface(direction.getOpposite(), type) : null;
    }

    protected final int getStrongInput(VecDirection direction) {
        var redstoneSource = findNeighborInterface(direction, RedstoneSource.class);
        return redstoneSource != null ? redstoneSource.getStrongOutput() : 0;
    }

    protected final int getWeakInput(VecDirection direction) {
        var redstoneSource = findNeighborInterface(direction, RedstoneSource.class);
        return redstoneSource != null ? redstoneSource.getWeakOutput() : 0;
    }

}
