package com.technicalitiesmc.scm.component.wire;

import com.mojang.math.Vector3f;
import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.BundledSink;
import com.technicalitiesmc.lib.circuit.interfaces.BundledSource;
import com.technicalitiesmc.lib.circuit.interfaces.wire.BundledWire;
import com.technicalitiesmc.lib.circuit.interfaces.wire.RedstoneNetwork;
import com.technicalitiesmc.lib.circuit.interfaces.wire.RedstoneWire;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMComponents;
import com.technicalitiesmc.scm.init.SCMItemTags;
import com.technicalitiesmc.scm.init.SCMItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Map;

public class BundledWireComponent extends HorizontalWireComponentBase<BundledWireComponent> implements BundledWire, BundledSource {

    private static final AABB BOUNDS = new AABB(0, 0, 0, 1, 2 / 16f, 1);

    public static final Property<WireVisualConnectionState> PROP_NEG_X = EnumProperty.create("neg_x", WireVisualConnectionState.class);
    public static final Property<WireVisualConnectionState> PROP_POS_X = EnumProperty.create("pos_x", WireVisualConnectionState.class);
    public static final Property<WireVisualConnectionState> PROP_NEG_Z = EnumProperty.create("neg_z", WireVisualConnectionState.class);
    public static final Property<WireVisualConnectionState> PROP_POS_Z = EnumProperty.create("pos_z", WireVisualConnectionState.class);

    private static final InterfaceLookup<BundledWireComponent> INTERFACES = InterfaceLookup.<BundledWireComponent>builder()
            .with(BundledSource.class, BundledWireComponent::getBundledSource)
            .with(BundledSink.class, BundledWireComponent::getBundledSink)
            .with(BundledWire.class, BundledWireComponent::getWire)
            .build();

    private final Wire[] wires = Arrays.stream(DyeColor.values()).map(Wire::new).toArray(Wire[]::new);

    // External state
    private boolean removed; // Consistent due to only being updated during removal

    public BundledWireComponent(ComponentContext context) {
        super(SCMComponents.BUNDLED_WIRE, context, INTERFACES);
    }

    @Override
    protected BundledWireComponent makeRotatedCopy(ComponentContext context, Rotation rotation,
                                                   Map<VecDirection, WireConnectionState> connectionStates) {
        return null;
    }

    @Override
    public ComponentState getState() {
        return super.getState()
                .setValue(PROP_NEG_X, getVisualState(VecDirection.NEG_X))
                .setValue(PROP_POS_X, getVisualState(VecDirection.POS_X))
                .setValue(PROP_NEG_Z, getVisualState(VecDirection.NEG_Z))
                .setValue(PROP_POS_Z, getVisualState(VecDirection.POS_Z));
    }

    @Override
    public AABB getBoundingBox() {
        return BOUNDS;
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(SCMItems.TINY_RGB_REDSTONE.get());
    }

    @Override
    protected boolean isBundled() {
        return true;
    }

    @Override
    protected void updateSignals(ComponentEventMap events, VecDirectionFlags disconnected) {
        // Update the inputs that received changes
        for (var side : events.findAny(CircuitEvent.BUNDLED_REDSTONE, CircuitEvent.NEIGHBOR_CHANGED)) {
            var isInput = getState(side) == WireConnectionState.INPUT;
            if (!isInput) {
                disconnected = disconnected.and(side);
                continue;
            }
            var input = getBundledInput(side);
            if (input == null) {
                disconnected = disconnected.and(side);
                continue;
            }
            for (var wire : this.wires) {
                wire.sideInputs[side.ordinal()] = input.getWeakOutput(wire.getColor());
            }
        }

        for (var wire : this.wires) {
            wire.computeInput(disconnected);
        }
    }

    @Override
    protected void invalidateNetworks() {
        for (var wire : wires) {
            wire.invalidateNetwork();
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

        for (var wire : wires) {
            wire.updateSequential();
        }
    }

    @Override
    public void beforeRemove() {
        super.beforeRemove();
        removed = true;
    }

    // Bundled wire

    @Override
    public RedstoneWire get(DyeColor color) {
        return wires[color.getId()];
    }

    // Bundled source

    @Override
    public int getStrongOutput(DyeColor color) {
        return wires[color.getId()].power;
    }

    @Override
    public int getWeakOutput(DyeColor color) {
        return wires[color.getId()].power;
    }

    // Serialization

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag = super.save(tag);
        var wires = new ListTag();
        for (var wire : this.wires) {
            wires.add(wire.save());
        }
        tag.put("wires", wires);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        var wires = tag.getList("wires", Tag.TAG_COMPOUND);
        var i = 0;
        for (var wire : this.wires) {
            wire.load(wires.getCompound(i++));
        }
    }

    // Helpers

    private BundledSource getBundledSource(VecDirection side) {
        if (getState(side) != WireConnectionState.OUTPUT) {
            return null;
        }
        return this;
    }

    private BundledSink getBundledSink(VecDirection side) {
        if (getState(side) != WireConnectionState.INPUT) {
            return null;
        }
        return BundledSink.instance();
    }

    private BundledWire getWire() {
        return !removed ? this : null;
    }

    public static void createState(ComponentStateBuilder builder) {
        builder.add(PROP_NEG_X, PROP_POS_X, PROP_NEG_Z, PROP_POS_Z);
    }

    public static class Client extends ClientComponent {

        @Override
        public AABB getBoundingBox(ComponentState state) {
            return BOUNDS;
        }

        @Override
        public ItemStack getPickedItem(ComponentState state) {
            return new ItemStack(SCMItems.TINY_RGB_REDSTONE.get());
        }

        @Override
        public InteractionResult use(ComponentState state, Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
            var stack = player.getItemInHand(hand);
            if (!stack.isEmpty() && stack.is(SCMItemTags.ROTATES_COMPONENTS)) {
                return InteractionResult.sidedSuccess(true);
            }
            return super.use(state, player, hand, sideHit, hit);
        }

    }

    private class Wire implements RedstoneWire {

        private final DyeColor color;

        // Internal state
        private final int[] sideInputs = new int[VecDirection.VALUES.length];
        private int input;
        private boolean mustPropagate;
        @Nullable
        private RedstoneNetwork network;

        // External state
        private int power;

        private Wire(DyeColor color) {
            this.color = color;
        }

        @Override
        public DyeColor getColor() {
            return color;
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
        public void visit(Visitor visitor) {
            for (var side : VecDirection.VALUES) {
                switch (getState(side)) {
                    case WIRE -> {
                        var neighbor = findNeighborInterface(side, RedstoneWire.class);
                        if (neighbor != null && neighbor.getColor() == color) {
                            visitor.accept(neighbor);
                        }
                    }
                    case BUNDLED_WIRE -> {
                        var neighbor = findNeighborInterface(side, BundledWire.class);
                        if (neighbor != null) {
                            visitor.accept(neighbor.get(color));
                        }
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
            for (var side : VecDirection.VALUES) {
                if (getState(side) == WireConnectionState.OUTPUT) {
                    sides = sides.and(side);
                }
            }
            sendEvent(CircuitEvent.BUNDLED_REDSTONE, true, sides);
        }

        public void computeInput(VecDirectionFlags disconnected) {
            for (var side : disconnected) {
                sideInputs[side.ordinal()] = 0;
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

        public void invalidateNetwork() {
            // If we have a network, invalidate it
            if (network != null) {
                network.invalidate();
            }
        }

        public void updateSequential() {
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

        public CompoundTag save() {
            var tag = new CompoundTag();
            tag.putIntArray("side_inputs", Arrays.copyOf(sideInputs, sideInputs.length));
            tag.putInt("power", power);
            return tag;
        }

        public void load(CompoundTag tag) {
            var inputs = tag.getIntArray("side_inputs");
            System.arraycopy(inputs, 0, sideInputs, 0, sideInputs.length);
            input = 0;
            for (var sideInput : sideInputs) {
                input = Math.max(input, sideInput);
            }
            power = tag.getInt("power");
        }

    }

}
