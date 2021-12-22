package com.technicalitiesmc.scm.circuit.util;

import com.technicalitiesmc.lib.circuit.component.ComponentSlot;

public record ComponentSlotPos(ComponentPos pos, ComponentSlot slot) {

    public ComponentSlotPos(int x, int y, int z, ComponentSlot slot) {
        this(new ComponentPos(x, y, z), slot);
    }

    public AbsoluteSlotPos toAbsolute() {
        return new AbsoluteSlotPos(pos().x(), pos().y(), pos().z(), slot());
    }

}
