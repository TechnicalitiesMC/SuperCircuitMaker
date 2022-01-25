package com.technicalitiesmc.scm.component.wire;

import com.mojang.math.Vector3f;
import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSink;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.circuit.interfaces.wire.BundledWire;
import com.technicalitiesmc.lib.circuit.interfaces.wire.RedstoneNetwork;
import com.technicalitiesmc.lib.circuit.interfaces.wire.RedstoneWire;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.lib.util.Utils;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMComponents;
import com.technicalitiesmc.scm.init.SCMItemTags;
import com.technicalitiesmc.scm.init.SCMItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Map;

public class ColoredWireComponent extends HorizontalWireComponentBase<ColoredWireComponent> implements RedstoneWire {

    private static final AABB BOUNDS = new AABB(0, 0, 0, 1, 2 / 16f, 1);

    private static final Property<WireVisualConnectionState> PROP_NEG_X = EnumProperty.create("neg_x", WireVisualConnectionState.class);
    private static final Property<WireVisualConnectionState> PROP_POS_X = EnumProperty.create("pos_x", WireVisualConnectionState.class);
    private static final Property<WireVisualConnectionState> PROP_NEG_Z = EnumProperty.create("neg_z", WireVisualConnectionState.class);
    private static final Property<WireVisualConnectionState> PROP_POS_Z = EnumProperty.create("pos_z", WireVisualConnectionState.class);
    private static final Property<DyeColor> PROP_EXT_COLOR = EnumProperty.create("color", DyeColor.class);
    private static final Property<Integer> PROP_EXT_POWER = IntegerProperty.create("power", 0, 255);

    private static final InterfaceLookup<ColoredWireComponent> INTERFACES = InterfaceLookup.<ColoredWireComponent>builder()
            .with(RedstoneSource.class, ColoredWireComponent::getRedstoneSource)
            .with(RedstoneSink.class, ColoredWireComponent::getRedstoneSink)
            .with(RedstoneWire.class, ColoredWireComponent::getWire)
            .build();

    // Internal state
    private final int[] sideInputs;
    private int input;
    private boolean mustPropagate;
    @Nullable
    private RedstoneNetwork network;

    // External state
    private DyeColor color = DyeColor.LIGHT_GRAY;
    private int power;
    private boolean removed; // Consistent due to only being updated during removal

    public ColoredWireComponent(ComponentContext context) {
        super(SCMComponents.REDSTONE_WIRE, context, INTERFACES);
        this.sideInputs = new int[VecDirection.VALUES.length];
    }

    private ColoredWireComponent(
            ComponentContext context, Map<VecDirection, WireConnectionState> connectionStates,
            int[] sideInputs, int input, boolean mustPropagate, DyeColor color, int power
    ) {
        super(SCMComponents.REDSTONE_WIRE, context, INTERFACES, connectionStates);
        this.sideInputs = Arrays.copyOf(sideInputs, sideInputs.length);
        this.input = input;
        this.mustPropagate = mustPropagate;
        this.color = color;
        this.power = power;
    }

    @Override
    protected ColoredWireComponent makeRotatedCopy(ComponentContext context, Rotation rotation,
                                                   Map<VecDirection, WireConnectionState> connectionStates) {
        return new ColoredWireComponent(context, connectionStates,
                Utils.rotateArray(sideInputs, rotation), input, mustPropagate, color, power);
    }

    @Override
    public ComponentState getState() {
        return super.getState()
                .setValue(PROP_NEG_X, getVisualState(VecDirection.NEG_X))
                .setValue(PROP_POS_X, getVisualState(VecDirection.POS_X))
                .setValue(PROP_NEG_Z, getVisualState(VecDirection.NEG_Z))
                .setValue(PROP_POS_Z, getVisualState(VecDirection.POS_Z))
                .setExtended(PROP_EXT_COLOR, color)
                .setExtended(PROP_EXT_POWER, power);
    }

    @Override
    public AABB getBoundingBox() {
        return BOUNDS;
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(SCMItems.TINY_REDSTONE.get());
    }

    @Override
    protected void updateSignals(ComponentEventMap events, VecDirectionFlags disconnected) {
        // Update the inputs that received changes
        for (var side : events.findAny(CircuitEvent.REDSTONE, CircuitEvent.NEIGHBOR_CHANGED)) {
            var isInput = getState(side) == WireConnectionState.INPUT;
            sideInputs[side.ordinal()] = isInput ? getWeakInput(side, true) : 0;
        }
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
    public InteractionResult use(Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
        // If clicked with a dye, paint
        var stack = player.getItemInHand(hand);
        if (!stack.isEmpty() && stack.getItem() instanceof DyeItem dye) {
            updateExternalState(true, () -> {
                color = dye.getDyeColor();
            });
            return InteractionResult.sidedSuccess(player.level.isClientSide());
        }
        // Fall back to parent handler
        return super.use(player, hand, sideHit, hit);
    }

    @Override
    public void beforeRemove() {
        super.beforeRemove();
        removed = true;
    }

    // Redstone wire

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
                    var neighbor = findNeighborInterface(side, RedstoneWire.class, true);
                    if (neighbor != null) {
                        visitor.accept(neighbor);
                    }
                }
                case BUNDLED_WIRE -> {
                    var neighbor = findNeighborInterface(side, BundledWire.class, true);
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
        sendEvent(CircuitEvent.REDSTONE, true, sides);
    }

    // Serialization

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag = super.save(tag);
        tag.putIntArray("side_inputs", Arrays.copyOf(sideInputs, sideInputs.length));
        tag.putInt("color", color.getId());
        tag.putInt("power", power);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        var inputs = tag.getIntArray("side_inputs");
        System.arraycopy(inputs, 0, sideInputs, 0, sideInputs.length);
        input = 0;
        for (var sideInput : sideInputs) {
            input = Math.max(input, sideInput);
        }
        color = DyeColor.byId(tag.getInt("color"));
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

    private RedstoneWire getWire() {
        return !removed ? this : null;
    }

    public static void createState(ComponentStateBuilder builder) {
        builder.add(PROP_NEG_X, PROP_POS_X, PROP_NEG_Z, PROP_POS_Z);
        builder.addExtended(PROP_EXT_COLOR, PROP_EXT_POWER);
    }

    public static class Client extends ClientComponent {

        @Override
        public AABB getBoundingBox(ComponentState state) {
            return BOUNDS;
        }

        @Override
        public ItemStack getPickedItem(ComponentState state) {
            return new ItemStack(SCMItems.TINY_REDSTONE.get());
        }

        @Override
        public InteractionResult use(ComponentState state, Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
            var stack = player.getItemInHand(hand);
            if (!stack.isEmpty() && (stack.getItem() instanceof DyeItem || stack.is(SCMItemTags.ROTATES_COMPONENTS))) {
                return InteractionResult.sidedSuccess(true);
            }
            return super.use(state, player, hand, sideHit, hit);
        }

        @Override
        public int getTint(ComponentState state, int tintIndex) {
            return switch (tintIndex) {
                case 0 -> state.getExtended(PROP_EXT_COLOR).getFireworkColor();
                case 1 -> {
                    var power = state.getExtended(PROP_EXT_POWER);
                    int minBrightness = 128;
                    int pow = (int) ((power / 255F) * (255 - minBrightness) + minBrightness);
                    yield (pow << 16) | (pow << 8) | pow;
                }
                default -> super.getTint(state, tintIndex);
            };
        }

    }

}
