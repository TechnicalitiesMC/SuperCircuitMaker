package com.technicalitiesmc.scm.component.wire;

import net.minecraft.util.StringRepresentable;

public enum WireVisualConnectionState implements StringRepresentable {
    DISCONNECTED("disconnected"),
    ANODE("anode"),
    CATHODE("cathode");

    private final String name;

    WireVisualConnectionState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

}
