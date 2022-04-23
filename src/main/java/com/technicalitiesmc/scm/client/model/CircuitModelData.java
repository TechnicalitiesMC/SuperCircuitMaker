package com.technicalitiesmc.scm.client.model;

import com.google.common.collect.Multimap;
import com.technicalitiesmc.lib.circuit.component.ComponentState;
import com.technicalitiesmc.scm.circuit.CircuitAdjacency;
import net.minecraft.core.Vec3i;
import net.minecraftforge.client.model.data.ModelProperty;

public class CircuitModelData {

    public static final ModelProperty<CircuitModelData> PROPERTY = new ModelProperty<>();

    private final Multimap<Vec3i, ComponentState> states;
    private final CircuitAdjacency[] adjacency;
    private boolean hideComponents;

    public CircuitModelData(Multimap<Vec3i, ComponentState> states, CircuitAdjacency[] adjacency, boolean hideComponents) {
        this.states = states;
        this.adjacency = adjacency;
        this.hideComponents = hideComponents;
    }

    public Multimap<Vec3i, ComponentState> getStates() {
        return states;
    }

    public CircuitAdjacency[] getAdjacency() {
        return adjacency;
    }

    public boolean shouldHideComponents() {
        return hideComponents;
    }

}
