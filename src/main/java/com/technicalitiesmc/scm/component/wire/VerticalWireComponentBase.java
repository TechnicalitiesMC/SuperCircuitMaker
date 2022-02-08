package com.technicalitiesmc.scm.component.wire;

import com.technicalitiesmc.lib.circuit.component.CircuitComponent;
import com.technicalitiesmc.lib.circuit.component.ComponentContext;
import com.technicalitiesmc.lib.circuit.component.ComponentSlot;
import com.technicalitiesmc.lib.circuit.component.ComponentType;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

public abstract class VerticalWireComponentBase<T extends VerticalWireComponentBase<T>> extends WireComponentBase<T> {

    protected VerticalWireComponentBase(RegistryObject<ComponentType> type, ComponentContext context, InterfaceLookup<T> interfaceLookup) {
        super(type, context, interfaceLookup);
    }

    @Override
    protected VecDirectionFlags getConnectableSides() {
        return VecDirectionFlags.verticals();
    }

    @Nullable
    @Override
    protected CircuitComponent findConnectionTarget(VecDirection side) {
        // Try direct adjacent first
        var neighbor = getSibling(side.isPositive() ? ComponentSlot.SUPPORT : ComponentSlot.DEFAULT);
        if (neighbor != null) {
            return neighbor;
        }
        // Then one away
        neighbor = getComponentAt(side.getOffset(), side.isPositive() ? ComponentSlot.DEFAULT : ComponentSlot.SUPPORT);
        if (neighbor != null) {
            return neighbor;
        }
        // Finally the overlay
        return getComponentAt(side.getOffset(), ComponentSlot.OVERLAY);
    }

}
