package com.technicalitiesmc.scm.component;

import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.math.VecDirection;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
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

    @Nullable
    protected final CircuitComponent findNeighbor(VecDirection direction, boolean adjacentOnly) {
        if (direction.getAxis() != Direction.Axis.Y) {
            return getNeighbor(direction, ComponentSlot.DEFAULT);
        }

        var dir = direction.getAxisDirection();
        var slot = getType().getSlot();
        var pos = Vec3i.ZERO;
        do {
            pos = pos.offset(slot.getOffsetTowards(dir));
            var next = slot.next(dir);
            var component = getComponentAt(pos, next);
            if (component != null) {
                return component;
            }
            slot = next;
        } while (!adjacentOnly && slot != ComponentSlot.DEFAULT);

        return null;
    }

    @Nullable
    protected final <V> V findNeighborInterface(VecDirection direction, Class<V> type, boolean adjacentOnly) {
        var neighbor = findNeighbor(direction, adjacentOnly);
        return neighbor != null ? neighbor.getInterface(direction.getOpposite(), type) : null;
    }

    protected final int getStrongInput(VecDirection direction, boolean adjacentOnly) {
        var redstoneSource = findNeighborInterface(direction, RedstoneSource.class, adjacentOnly);
        return redstoneSource != null ? redstoneSource.getStrongOutput() : 0;
    }

    protected final int getWeakInput(VecDirection direction, boolean adjacentOnly) {
        var redstoneSource = findNeighborInterface(direction, RedstoneSource.class, adjacentOnly);
        return redstoneSource != null ? redstoneSource.getWeakOutput() : 0;
    }

}
