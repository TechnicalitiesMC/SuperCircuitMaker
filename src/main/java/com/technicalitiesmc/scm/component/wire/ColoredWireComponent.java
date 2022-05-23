package com.technicalitiesmc.scm.component.wire;

import com.mojang.math.Vector3f;
import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSink;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.circuit.interfaces.wire.*;
import com.technicalitiesmc.lib.init.TKLibItemTags;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.lib.util.Utils;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMComponents;
import com.technicalitiesmc.scm.init.SCMItems;
import com.technicalitiesmc.scm.network.PickPaletteColorPacket;
import com.technicalitiesmc.scm.network.SCMNetworkHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;

public class ColoredWireComponent extends HorizontalWireComponentBase<ColoredWireComponent> implements RedstoneWire {

    private static final AABB BOUNDS = new AABB(0, 0, 0, 1, 2 / 16f, 1);

    public static final Property<WireConnectionState.Visual> PROP_NEG_X = EnumProperty.create("neg_x", WireConnectionState.Visual.class);
    public static final Property<WireConnectionState.Visual> PROP_POS_X = EnumProperty.create("pos_x", WireConnectionState.Visual.class);
    public static final Property<WireConnectionState.Visual> PROP_NEG_Z = EnumProperty.create("neg_z", WireConnectionState.Visual.class);
    public static final Property<WireConnectionState.Visual> PROP_POS_Z = EnumProperty.create("pos_z", WireConnectionState.Visual.class);
    public static final Property<DyeColor> PROP_EXT_COLOR = EnumProperty.create("color", DyeColor.class);
    private static final Property<Integer> PROP_EXT_POWER = IntegerProperty.create("power", 0, 255);

    private static final InterfaceLookup<ColoredWireComponent> INTERFACES = InterfaceLookup.<ColoredWireComponent>builder()
            .with(RedstoneSource.class, ColoredWireComponent::getRedstoneSource)
            .with(RedstoneSink.class, ColoredWireComponent::getRedstoneSink)
            .with(Wire.class, c -> c)
            .with(RedstoneWire.class, c -> c)
            .with(BundledWire.class, VecDirectionFlags.verticals(), ColoredWireComponent::getBundledWire)
            .build();

    // Internal state
    private final int[] sideInputs = new int[VecDirection.VALUES.length];
    private final Conductor conductor = new Conductor();

    // External state
    private DyeColor color = DyeColor.LIGHT_GRAY;
    private int power;

    public ColoredWireComponent(ComponentContext context) {
        super(SCMComponents.REDSTONE_WIRE, context, INTERFACES);
    }

    public ColoredWireComponent(ComponentContext context, DyeColor color) {
        this(context);
        this.color = color;
    }

    @Override
    public ComponentState getState() {
        return super.getState()
                .setValue(PROP_NEG_X, getState(VecDirection.NEG_X).getVisualState())
                .setValue(PROP_POS_X, getState(VecDirection.POS_X).getVisualState())
                .setValue(PROP_NEG_Z, getState(VecDirection.NEG_Z).getVisualState())
                .setValue(PROP_POS_Z, getState(VecDirection.POS_Z).getVisualState())
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
    protected WireConnectionState getNextState(VecDirection side, WireConnectionState state, CircuitComponent neighbor, boolean forced) {
        var nextState = WireUtils.getNextState(side, state, neighbor, RedstoneSource.class, RedstoneSink.class);
        if (!forced && nextState == WireConnectionState.WIRE) {
            var rsWire = neighbor.getInterface(side.getOpposite(), RedstoneWire.class);
            if (rsWire != null && rsWire.getColor() != null && rsWire.getColor() != color) {
                return WireConnectionState.FORCE_DISCONNECTED;
            }
        }
        return nextState;
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
            sideInputs[side.ordinal()] = isInput ? getInput(side) : 0;
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
        // First check if it's a redstone source
        var redstoneSource = neighbor.getInterface(side.getOpposite(), RedstoneSource.class);
        if (redstoneSource != null) {
            return redstoneSource.getWeakOutput();
        }
        // If not, check if it's a bundled wire
        var wire = neighbor.getInterface(side.getOpposite(), BundledWire.class);
        if (wire != null) {
            var conductor = wire.getConductor(color);
            return conductor != null ? conductor.getPower() : 0;
        }
        return 0;
    }

    @Override
    public void updateSequential() {
        super.updateSequential();
        conductor.doSequentialUpdate();
    }

    @Override
    public InteractionResult use(Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
        // If clicked with a dye, paint
        var stack = player.getItemInHand(hand);
        var dyeColor = !stack.isEmpty() ? Utils.getDyeColor(stack) : null;
        if (dyeColor != null) {
            updateExternalState(true, () -> {
                color = dyeColor;
            });
            conductor.invalidateNetwork();
            return InteractionResult.sidedSuccess(player.level.isClientSide());
        }
        // Fall back to parent handler
        return super.use(player, hand, sideHit, hit);
    }

    // Redstone wire

    @Override
    public DyeColor getColor() {
        return color;
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
        tag.putInt("color", color.getId());
        tag.putInt("power", power);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        var inputs = tag.getIntArray("side_inputs");
        System.arraycopy(inputs, 0, sideInputs, 0, sideInputs.length);
        conductor.setInputOnLoad(Arrays.stream(sideInputs).max().orElse(0));
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

    private BundledWire getBundledWire(VecDirection side) {
        if (getState(side.getOpposite()) != WireConnectionState.WIRE) {
            return null;
        }
        var neighbor = findConnectionTarget(side.getOpposite());
        if (neighbor == null) {
            return null;
        }
        var neighborBundled = neighbor.getInterface(side, BundledWire.class);
        if (neighborBundled == null) {
            return null;
        }
        return color -> color == this.color ? conductor : neighborBundled.getConductor(color);
    }

    public static void createState(ComponentStateBuilder builder) {
        builder.add(PROP_NEG_X, PROP_POS_X, PROP_NEG_Z, PROP_POS_Z);
        builder.addExtended(PROP_EXT_COLOR, PROP_EXT_POWER);
    }

    public final class Conductor extends RedstoneConductor {

        @Override
        public int getPower() {
            return power;
        }

        @Override
        public void visit(RedstoneConductor.Visitor visitor) {
            for (var side : VecDirection.VALUES) {
                if (getStateInternal(side) == WireConnectionState.WIRE) {
                    var neighbor = findConnectionTarget(side);
                    if (neighbor == null) {
                        continue;
                    }
                    var rsWire = neighbor.getInterface(side.getOpposite(), RedstoneWire.class);
                    if (rsWire != null) {
                        visitor.accept(rsWire.getConductor());
                        continue;
                    }
                    var bundledWire = neighbor.getInterface(side.getOpposite(), BundledWire.class);
                    if (bundledWire != null) {
                        visitor.accept(bundledWire.getConductor(color));
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
            return new ItemStack(SCMItems.TINY_REDSTONE.get());
        }

        @Override
        public void onPicking(ComponentState state, Player player) {
            var stack = player.getOffhandItem();
            if (!stack.isEmpty() && stack.is(SCMItems.PALETTE.get())) {
                SCMNetworkHandler.sendToServer(new PickPaletteColorPacket(state.getExtended(PROP_EXT_COLOR)));
            }
        }

        @Override
        public InteractionResult use(ComponentState state, Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
            var stack = player.getItemInHand(hand);
            if (!stack.isEmpty() && (stack.is(TKLibItemTags.TOOLS_WRENCH) || Utils.getDyeColor(stack) != null)) {
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
