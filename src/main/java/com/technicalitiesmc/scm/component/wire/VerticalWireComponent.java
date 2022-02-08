package com.technicalitiesmc.scm.component.wire;

import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSink;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.circuit.interfaces.wire.WireConnectionState;
import com.technicalitiesmc.lib.circuit.interfaces.wire.RedstoneConductor;
import com.technicalitiesmc.lib.circuit.interfaces.wire.RedstoneWire;
import com.technicalitiesmc.lib.circuit.interfaces.wire.Wire;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMComponents;
import com.technicalitiesmc.scm.init.SCMItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class VerticalWireComponent extends VerticalWireComponentBase<VerticalWireComponent> implements RedstoneWire {

    private static final AABB BOUNDS = new AABB(6 / 16f, 0, 6 / 16f, 10 / 16f, 1, 10 / 16f);

    private static final Property<Integer> PROP_EXT_POWER = IntegerProperty.create("power", 0, 255);

    private static final InterfaceLookup<VerticalWireComponent> INTERFACES = InterfaceLookup.<VerticalWireComponent>builder()
            .with(RedstoneSource.class, VerticalWireComponent::getRedstoneSource)
            .with(RedstoneSink.class, VerticalWireComponent::getRedstoneSink)
            .with(Wire.class, c -> c)
            .with(RedstoneWire.class, c -> c)
            .build();

    // Internal state
    private final int[] sideInputs = new int[2];
    private final Conductor conductor = new Conductor();

    // External state
    private int power;

    public VerticalWireComponent(ComponentContext context) {
        super(SCMComponents.VERTICAL_WIRE, context, INTERFACES);
    }

    @Override
    public ComponentState getState() {
        return super.getState().setExtended(PROP_EXT_POWER, power);
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
    protected WireConnectionState getNextState(VecDirection side, WireConnectionState state, CircuitComponent neighbor, boolean forced) {
        return WireUtils.getNextState(side, state, neighbor, RedstoneSource.class, RedstoneSink.class);
    }

    @Override
    protected boolean isValidState(VecDirection side, WireConnectionState state, CircuitComponent neighbor) {
        return WireUtils.isValidState(side, state, neighbor, RedstoneSource.class, RedstoneSink.class);
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
            conductor.invalidateNetwork();
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
            sideInputs[side.getAxisDirection().ordinal()] = isInput ? getInput(side) : 0;
        }
        // Compute the new total input and update conductor
        var newInput = Arrays.stream(sideInputs).max().orElse(0);
        conductor.setInput(newInput);
    }

    private int getInput(VecDirection side) {
        var neighbor = findConnectionTarget(side);
        if (neighbor == null) {
            return 0;
        }
        // Only receive from a redstone source
        var redstoneSource = neighbor.getInterface(side.getOpposite(), RedstoneSource.class);
        return redstoneSource != null ? redstoneSource.getWeakOutput() : 0;
    }

    @Override
    public void updateSequential() {
        super.updateSequential();
        conductor.doSequentialUpdate();
    }

    // Redstone wire

    @Nullable
    @Override
    public DyeColor getColor() {
        return null;
    }

    @Override
    public RedstoneConductor getConductor() {
        return conductor;
    }

    // Serialization

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag = super.save(tag);
        tag.putIntArray("side_inputs", Arrays.copyOf(sideInputs, sideInputs.length));
        tag.putInt("power", power);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (!tag.contains("side_inputs")) {
            return; // Has no data, so skip
        }
        var inputs = tag.getIntArray("side_inputs");
        System.arraycopy(inputs, 0, sideInputs, 0, inputs.length);
        conductor.setInputOnLoad(Arrays.stream(sideInputs).max().orElse(0));
        power = tag.getInt("power");
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

    public static void createState(ComponentStateBuilder builder) {
        builder.addExtended(PROP_EXT_POWER);
    }

    public final class Conductor extends RedstoneConductor {

        @Override
        public int getPower() {
            return power;
        }

        @Override
        public void visit(Visitor visitor) {
            for (var side : VecDirection.VALUES) {
                if (getStateInternal(side) == WireConnectionState.WIRE) {
                    var neighbor = findConnectionTarget(side);
                    if (neighbor == null) {
                        continue;
                    }
                    var rsWire = neighbor.getInterface(side.getOpposite(), RedstoneWire.class);
                    if (rsWire != null) {
                        visitor.accept(rsWire.getConductor());
                    }
                }
            }
        }

        @Override
        public void onPropagated(int newPower) {
            // Update power level
            updateExternalState(true, () -> {
                power = newPower;
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
            return new ItemStack(SCMItems.REDSTONE_STICK.get());
        }

        @Override
        public int getTint(ComponentState state, int tintIndex) {
            if (tintIndex == 0) {
                var power = state.getExtended(PROP_EXT_POWER);
                int minBrightness = 128;
                int pow = (int) ((power / 255F) * (255 - minBrightness) + minBrightness);
                return (pow << 16) | (pow << 8) | pow;
            }
            return super.getTint(state, tintIndex);
        }

    }

}
