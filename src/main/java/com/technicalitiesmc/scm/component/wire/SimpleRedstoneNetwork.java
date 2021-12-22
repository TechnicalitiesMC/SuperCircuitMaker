package com.technicalitiesmc.scm.component.wire;

import com.technicalitiesmc.lib.circuit.interfaces.wire.RedstoneNetwork;
import com.technicalitiesmc.lib.circuit.interfaces.wire.RedstoneWire;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public class SimpleRedstoneNetwork implements RedstoneNetwork {

    public static void build(RedstoneWire wire) {
        var network = new SimpleRedstoneNetwork();
        var queue = new ArrayDeque<RedstoneWire>();
        queue.add(wire);
        network.wires.add(wire);
        wire.setNetwork(network);
        while (!queue.isEmpty()) {
            queue.pop().visit((w) -> {
                if (network.wires.add(w)) {
                    queue.add(w);
                    w.setNetwork(network);
                }
            });
        }
    }

    private final Set<RedstoneWire> wires = new HashSet<>();

    @Override
    public void propagate() {
        var input = 0;
        for (var wire : wires) {
            input = Math.max(input, wire.getInput());
        }
        for (var wire : wires) {
            wire.updateAndNotify(input);
        }
    }

    @Override
    public void invalidate() {
        wires.forEach(RedstoneWire::clearNetwork);
    }

}
