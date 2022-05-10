package com.technicalitiesmc.scm.component.wire;

import com.mojang.math.Vector3f;
import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.BundledSink;
import com.technicalitiesmc.lib.circuit.interfaces.BundledSource;
import com.technicalitiesmc.lib.circuit.interfaces.wire.*;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMComponents;
import com.technicalitiesmc.scm.init.SCMItemTags;
import com.technicalitiesmc.scm.init.SCMItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;

public class BundledWireComponent extends HorizontalWireComponentBase<BundledWireComponent> implements BundledWire {

    private static final DyeColor[] COLORS = DyeColor.values();
    private static final int[] NO_INPUT = new int[COLORS.length];

    private static final AABB BOUNDS = new AABB(0, 0, 0, 1, 2 / 16f, 1);

    public static final Property<WireConnectionState.Visual> PROP_NEG_X = EnumProperty.create("neg_x", WireConnectionState.Visual.class);
    public static final Property<WireConnectionState.Visual> PROP_POS_X = EnumProperty.create("pos_x", WireConnectionState.Visual.class);
    public static final Property<WireConnectionState.Visual> PROP_NEG_Z = EnumProperty.create("neg_z", WireConnectionState.Visual.class);
    public static final Property<WireConnectionState.Visual> PROP_POS_Z = EnumProperty.create("pos_z", WireConnectionState.Visual.class);

    private static final InterfaceLookup<BundledWireComponent> INTERFACES = InterfaceLookup.<BundledWireComponent>builder()
            .with(BundledSource.class, BundledWireComponent::getBundledSource)
            .with(BundledSink.class, BundledWireComponent::getBundledSink)
            .with(Wire.class, c -> c)
            .with(BundledWire.class, c -> c)
            .build();

    // Internal state
    private final int[][] sideInputs = new int[VecDirection.VALUES.length][COLORS.length];
    private final Conductor[] conductors = new Conductor[COLORS.length];

    // External state
    private final int[] power = new int[COLORS.length];

    public BundledWireComponent(ComponentContext context) {
        super(SCMComponents.BUNDLED_WIRE, context, INTERFACES);
        Arrays.setAll(conductors, i -> new Conductor(COLORS[i]));
    }

    @Override
    public ComponentState getState() {
        return super.getState()
                .setValue(PROP_NEG_X, getState(VecDirection.NEG_X).getVisualState())
                .setValue(PROP_POS_X, getState(VecDirection.POS_X).getVisualState())
                .setValue(PROP_NEG_Z, getState(VecDirection.NEG_Z).getVisualState())
                .setValue(PROP_POS_Z, getState(VecDirection.POS_Z).getVisualState());
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
    protected WireConnectionState getNextState(VecDirection side, WireConnectionState state, CircuitComponent neighbor, boolean forced) {
        return WireUtils.getNextState(side, state, neighbor, BundledSource.class, BundledSink.class);
    }

    @Override
    protected boolean isValidState(VecDirection side, WireConnectionState state, CircuitComponent neighbor) {
        return WireUtils.isValidState(side, state, neighbor, BundledSource.class, BundledSink.class);
    }

    @Override
    protected void onStateTransition(VecDirection side, WireConnectionState prevState, WireConnectionState newState) {
        // Notify of a neighbor change regardless
        sendEvent(CircuitEvent.NEIGHBOR_CHANGED, side);
        // If the connection is/was an output, notify a redstone update too
        if (prevState == WireConnectionState.OUTPUT || newState == WireConnectionState.OUTPUT) {
            sendEvent(CircuitEvent.REDSTONE, side);
        }
        // If the connection is/was a wire, invalidate the network
        if (prevState == WireConnectionState.WIRE || newState == WireConnectionState.WIRE) {
            for (var conductor : conductors) {
                conductor.invalidateNetwork();
            }
        }
        // If the neighbor is a wire, update its connection state too
        var neighbor = findConnectionTarget(side);
        if (neighbor != null) {
            var wire = neighbor.getInterface(side.getOpposite(), Wire.class);
            if (wire != null) {
                wire.setState(side.getOpposite(), newState.getOpposite());
            }
        }
    }

    @Override
    protected void updateSignals(VecDirectionFlags sides) {
        // Check all the updated sides
        for (var side : sides) {
            var isInput = getStateInternal(side) == WireConnectionState.INPUT;
            sideInputs[side.ordinal()] = isInput ? getInput(side) : NO_INPUT;
        }
        // Compute the new total input and update conductor
        for (int i = 0; i < COLORS.length; i++) {
            var newInput = 0;
            for (var sideInput : sideInputs) {
                newInput = Math.max(newInput, sideInput[i]);
            }
            conductors[i].setInput(newInput);
        }
    }

    private int[] getInput(VecDirection side) {
        var neighbor = findConnectionTarget(side);
        if (neighbor == null) {
            return NO_INPUT;
        }
        // First check if it's a bundled source
        var bundledSource = neighbor.getInterface(side.getOpposite(), BundledSource.class);
        if (bundledSource != null) {
            return bundledSource.getWeakOutput();
        }
        // If not, check if it's a colored wire
        var wire = neighbor.getInterface(side.getOpposite(), RedstoneWire.class);
        if (wire != null) {
            var color = wire.getColor();
            if (color != null) {
                var signal = new int[COLORS.length];
                signal[color.getId()] = wire.getConductor().getPower();
                return signal;
            }
        }
        return NO_INPUT;
    }

    @Override
    public void updateSequential() {
        super.updateSequential();
        for (var conductor : conductors) {
            conductor.doSequentialUpdate();
        }
    }

    // Redstone wire

    @Override
    public RedstoneConductor getConductor(DyeColor color) {
        return conductors[color.getId()];
    }

    // Serialization

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag = super.save(tag);
        var inputs = new ListTag();
        for (var side : sideInputs) {
            inputs.add(new IntArrayTag(side));
        }
        tag.put("side_inputs", inputs);
        tag.putIntArray("power", power);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("wires")) {
            var wires = tag.getList("wires", Tag.TAG_COMPOUND);
            for (int i = 0; i < COLORS.length; i++) {
                var t = wires.getCompound(i);
                var inputs = t.getIntArray("side_inputs");
                for (var side : VecDirection.VALUES) {
                    sideInputs[side.ordinal()][i] = inputs[side.ordinal()];
                }
                power[i] = t.getInt("power");
            }
        } else {
            var inputs = tag.getList("side_inputs", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < inputs.size(); i++) {
                sideInputs[i] = inputs.getIntArray(i);
            }
            var pow = tag.getIntArray("power");
            System.arraycopy(pow, 0, power, 0, pow.length);
        }
    }

    // Helpers

    private BundledSource getBundledSource(VecDirection side) {
        if (getState(side) != WireConnectionState.OUTPUT) {
            return null;
        }
        return BundledSource.of(power, power);
    }

    private BundledSink getBundledSink(VecDirection side) {
        if (getState(side) != WireConnectionState.INPUT) {
            return null;
        }
        return BundledSink.instance();
    }

    public static void createState(ComponentStateBuilder builder) {
        builder.add(PROP_NEG_X, PROP_POS_X, PROP_NEG_Z, PROP_POS_Z);
    }

    public final class Conductor extends RedstoneConductor {

        private final DyeColor color;

        public Conductor(DyeColor color) {
            this.color = color;
        }

        @Override
        public int getPower() {
            return power[color.getId()];
        }

        @Override
        public void visit(Visitor visitor) {
            for (var side : VecDirection.VALUES) {
                if (getStateInternal(side) == WireConnectionState.WIRE) {
                    var neighbor = findConnectionTarget(side);
                    if (neighbor == null) {
                        continue;
                    }
                    var bundledWire = neighbor.getInterface(side.getOpposite(), BundledWire.class);
                    if (bundledWire != null) {
                        visitor.accept(bundledWire.getConductor(color));
                        continue;
                    }
                    var rsWire = neighbor.getInterface(side.getOpposite(), RedstoneWire.class);
                    if (rsWire != null && rsWire.getColor() == color) {
                        visitor.accept(rsWire.getConductor());
                    }
                }
            }
        }

        @Override
        public void onPropagated(int newPower) {
            // Update power level
            updateExternalState(true, () -> {
                power[color.getId()] = newPower;
            });

            // Notify neighbors
            var sides = VecDirectionFlags.none();
            for (var side : VecDirection.VALUES) {
                if (getStateInternal(side) == WireConnectionState.OUTPUT) {
                    sides = sides.and(side);
                }
            }
            sendEvent(CircuitEvent.REDSTONE, sides);
        }

        @Override
        public void scheduleSequentialUpdate() {
            scheduleSequential();
        }

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
            if (!stack.isEmpty() && stack.is(SCMItemTags.WRENCHES)) {
                return InteractionResult.sidedSuccess(true);
            }
            return super.use(state, player, hand, sideHit, hit);
        }

    }

}
