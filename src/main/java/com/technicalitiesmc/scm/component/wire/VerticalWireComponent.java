package com.technicalitiesmc.scm.component.wire;

import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSink;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.circuit.interfaces.wire.RedstoneNetwork;
import com.technicalitiesmc.lib.circuit.interfaces.wire.RedstoneWire;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.lib.util.Utils;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMComponents;
import com.technicalitiesmc.scm.init.SCMItems;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Map;

public class VerticalWireComponent extends VerticalWireComponentBase<VerticalWireComponent> implements RedstoneWire {

    private static final WireConnectionState[] CONNECTION_PRIORITIES = {
            WireConnectionState.WIRE,
            WireConnectionState.OUTPUT,
            WireConnectionState.INPUT
    };

    private static final AABB BOUNDS = new AABB(5/16f, 0, 5/16f, 11/16f, 1, 11/16f);

    private static final InterfaceLookup<VerticalWireComponent> INTERFACES = InterfaceLookup.<VerticalWireComponent>builder()
            .with(RedstoneSource.class, VecDirectionFlags.verticals(), VerticalWireComponent::getRedstoneSource)
            .with(RedstoneSink.class, VecDirectionFlags.verticals(), VerticalWireComponent::getRedstoneSink)
            .with(RedstoneWire.class, VecDirectionFlags.verticals(), VerticalWireComponent::getWire)
            .build();

    // Internal state
    private final int[] sideInputs;
    private int input;
    private boolean mustPropagate;
    @Nullable
    private RedstoneNetwork network;

    // External state
    private int power;
    private boolean removed; // Consistent due to only being updated during removal

    public VerticalWireComponent(ComponentContext context) {
        super(SCMComponents.VERTICAL_WIRE, context, INTERFACES);
        this.sideInputs = new int[2];
    }

    protected VerticalWireComponent(
            ComponentContext context, Map<VecDirection, WireConnectionState> connectionStates,
            int[] sideInputs, int input, boolean mustPropagate, int power
    ) {
        super(SCMComponents.VERTICAL_WIRE, context, INTERFACES, connectionStates);
        this.sideInputs = Arrays.copyOf(sideInputs, sideInputs.length);
        this.input = input;
        this.mustPropagate = mustPropagate;
        this.power = power;
    }

    @Override
    protected VerticalWireComponent makeRotatedCopy(ComponentContext context, Rotation rotation,
                                                   Map<VecDirection, WireConnectionState> connectionStates) {
        return new VerticalWireComponent(context, connectionStates,
                Utils.rotateArray(sideInputs, rotation), input, mustPropagate, power);
    }

    @Override
    protected WireConnectionState[] getConnectionPriorities() {
        return CONNECTION_PRIORITIES;
    }

    @Override
    public AABB getBoundingBox() {
        return BOUNDS;
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(SCMItems.REDSTONE_STICK.get());
    }

    @Override
    protected boolean isBundled() {
        return false;
    }

    @Override
    protected void updateSignals(ComponentEventMap events, VecDirectionFlags disconnected) {
        // Update the inputs that received changes
        for (var side : events.findAny(CircuitEvent.REDSTONE, CircuitEvent.NEIGHBOR_CHANGED)) {
            var isInput = getState(side) == WireConnectionState.INPUT;
            sideInputs[side.getAxisDirection().ordinal()] = isInput ? getWeakInput(side) : 0;
        }
        for (var side : disconnected) {
            sideInputs[side.getAxisDirection().ordinal()] = 0;
        }

        // Compute the new total input
        var newInput = 0;
        for (var sideInput : sideInputs) {
            newInput = Math.max(newInput, sideInput);
        }

        // If the input has changed and either
        //  - It's turning off and this wire was potentially contributing to the network's value (input == power)
        //  - The new input would contribute to the current signal (newInput > power)
        if (newInput != input && ((input == power && newInput < power) || newInput > power)) {
            // Schedule a propagation pass
            mustPropagate = true;
            scheduleSequential();
        }
        // Update the input regardless of if it results in a network update or not
        input = newInput;
    }

    @Override
    protected void invalidateNetworks() {
        // If we have a network, invalidate it
        if (network != null) {
            network.invalidate();
        }
        if (!removed) {
            scheduleSequential();
        }
    }

    @Override
    public void updateSequential() {
        if (removed) {
            return;
        }

        // If we don't have a network, build it and force propagation
        // The network will be assigned via the wire interface so no further
        // network reconstructions will happen during this sequential update
        if (network == null) {
            SimpleRedstoneNetwork.build(this);
            mustPropagate = true;
        }
        // Propagate if needed
        // This flag will be set to false when the wire gets a new power level
        // so no further propagations will happen during this sequential update
        if (network != null && mustPropagate) {
            network.propagate();
        }
    }

    @Override
    public void beforeRemove() {
        super.beforeRemove();
        removed = true;
    }

    // Redstone wire

    @Nullable
    @Override
    public DyeColor getColor() {
        return null;
    }

    @Override
    public void clearNetwork() {
        network = null;
        // Schedule a sequential update so that the network gets rebuilt
        if (!removed) {
            scheduleSequential();
            mustPropagate = true;
        }
    }

    @Override
    public void setNetwork(RedstoneNetwork network) {
        this.network = network;
    }

    @Override
    public void visit(RedstoneWire.Visitor visitor) {
        for (var side : VecDirectionFlags.verticals()) {
            if (getState(side) == WireConnectionState.WIRE) {
                var neighbor = getConnectionTarget(side);
                if (neighbor == null) {
                    continue;
                }
                var itf = neighbor.getInterface(side.getOpposite(), RedstoneWire.class);
                if (itf != null) {
                    visitor.accept(itf);
                }
            }
        }
    }

    @Override
    public int getInput() {
        return input;
    }

    @Override
    public void updateAndNotify(int newPower) {
        // If another wire causes a propagation, we don't need to do it ourselves anymore
        mustPropagate = false;

        // Update power level and notify neighbors
        updateExternalState(true, () -> {
            power = newPower;
        });

        var sides = VecDirectionFlags.none();
        for (var side : VecDirectionFlags.verticals()) {
            if (getState(side) == WireConnectionState.OUTPUT) {
                sides = sides.and(side);
            }
        }
        sendEvent(CircuitEvent.REDSTONE, sides);
    }

    // Helpers

    private RedstoneSource getRedstoneSource(VecDirection side) {
        if (getState(side) != WireConnectionState.OUTPUT) {
            return null;
        }
        return RedstoneSource.of(power, power);
    }

    private RedstoneSink getRedstoneSink(VecDirection side) {
        if (getState(side) != WireConnectionState.INPUT) {
            return null;
        }
        return RedstoneSink.instance();
    }

    private RedstoneWire getWire() {
        return !removed ? this : null;
    }

    public static class Client extends ClientComponent {

        @Override
        public AABB getBoundingBox(ComponentState state) {
            return BOUNDS;
        }

        @Override
        public ItemStack getPickedItem(ComponentState state) {
            return new ItemStack(SCMItems.REDSTONE_STICK.get());
        }

    }

}
