package com.technicalitiesmc.scm.client.model;

import com.technicalitiesmc.lib.circuit.component.ComponentState;
import com.technicalitiesmc.scm.circuit.CircuitAdjacency;
import net.minecraft.core.Vec3i;
import net.minecraftforge.client.model.data.ModelProperty;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class CircuitModelData {

    public static final ModelProperty<CircuitModelData> PROPERTY = new ModelProperty<>();

    private final List<Pair<Vec3i, ComponentState>> states;
    private final CircuitAdjacency[] adjacency;

    public CircuitModelData(List<Pair<Vec3i, ComponentState>> states, CircuitAdjacency[] adjacency) {
        this.states = states;
        this.adjacency = adjacency;
    }

    public List<Pair<Vec3i, ComponentState>> getStates() {
        return states;
    }

    public CircuitAdjacency[] getAdjacency() {
        return adjacency;
    }

}
