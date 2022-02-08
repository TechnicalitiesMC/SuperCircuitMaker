package com.technicalitiesmc.scm.component.misc;

import com.mojang.math.Vector3f;
import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSink;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.circuit.interfaces.wire.BundledWire;
import com.technicalitiesmc.lib.circuit.interfaces.wire.RedstoneWire;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.scm.component.CircuitComponentBase;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMComponents;
import com.technicalitiesmc.scm.init.SCMItemTags;
import com.technicalitiesmc.scm.init.SCMItems;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

import java.util.function.BiFunction;

public class PlatformComponent extends CircuitComponentBase<PlatformComponent> {

    private static final AABB BOUNDS = new AABB(0, 12 / 16D, 0, 1, 1, 1);

    private static final Property<Boolean> PROP_CONDUCTIVE = BooleanProperty.create("conductive");

    private static final InterfaceLookup<PlatformComponent> INTERFACES = InterfaceLookup.<PlatformComponent>builder()
            // Pass through redstone I/O
            .with(RedstoneSource.class, VecDirectionFlags.verticals(), makePassThrough(RedstoneSource.class))
            .with(RedstoneSink.class, VecDirectionFlags.verticals(), makePassThrough(RedstoneSink.class))
            // As well as regular and bundled wires
            .with(RedstoneWire.class, VecDirectionFlags.verticals(), makePassThrough(RedstoneWire.class))
            .with(BundledWire.class, VecDirectionFlags.verticals(), makePassThrough(BundledWire.class))
            .build();

    private boolean conductive = true;

    public PlatformComponent(ComponentContext context) {
        super(SCMComponents.PLATFORM, context, INTERFACES);
    }

    private PlatformComponent(ComponentContext context, boolean conductive) {
        this(context);
        this.conductive = conductive;
    }

    @Override
    public CircuitComponent copyRotated(ComponentContext context, Rotation rotation) {
        return new PlatformComponent(context, conductive);
    }

    @Override
    public ComponentState getState() {
        return super.getState().setValue(PROP_CONDUCTIVE, conductive);
    }

    @Override
    public AABB getBoundingBox() {
        return BOUNDS;
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(SCMItems.PLATFORM.get());
    }

    @Override
    public boolean isTopSolid() {
        return true;
    }

    @Override
    public void receiveEvent(VecDirection side, CircuitEvent event, ComponentEventMap.Builder builder) {
        // Forward events vertically
        if (conductive && side.getAxis() == Direction.Axis.Y) {
            var neighbor = getOppositeNeighbor(side);
            // Skip forwarding the event if there is no direct neighbor
            if (neighbor != null) {
                sendEvent(event, side.getOpposite());
            }
        }
    }

    @Override
    public InteractionResult use(Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
        var stack = player.getItemInHand(hand);
        if (!stack.isEmpty() && stack.is(SCMItemTags.ROTATES_COMPONENTS)) {
            updateExternalState(true, () -> {
                conductive = !conductive;
            });
            sendEvent(CircuitEvent.NEIGHBOR_CHANGED, VecDirectionFlags.verticals());
            sendEvent(CircuitEvent.REDSTONE, VecDirectionFlags.verticals());
            sendEvent(CircuitEvent.BUNDLED_REDSTONE, VecDirectionFlags.verticals());
        }
        return super.use(player, hand, sideHit, hit);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("conductive", conductive);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        this.conductive = tag.getBoolean("conductive");
    }

    // Helpers

    private CircuitComponent getOppositeNeighbor(VecDirection side) {
        return findNeighbor(side.getOpposite());
    }

    private static <T> BiFunction<PlatformComponent, VecDirection, T> makePassThrough(Class<T> type) {
        return (comp, side) -> {
            if (!comp.conductive) {
                return null;
            }
            var neighbor = comp.getOppositeNeighbor(side);
            return neighbor == null ? null : neighbor.getInterface(side, type);
        };
    }

    public static void createState(StateDefinition.Builder<ComponentType, ComponentState> builder) {
        builder.add(PROP_CONDUCTIVE);
    }

    public static class Client extends ClientComponent {

        @Override
        public boolean isTopSolid(ComponentState state) {
            return true;
        }

        @Override
        public AABB getBoundingBox(ComponentState state) {
            return BOUNDS;
        }

        @Override
        public ItemStack getPickedItem(ComponentState state) {
            return new ItemStack(SCMItems.PLATFORM.get());
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

}
