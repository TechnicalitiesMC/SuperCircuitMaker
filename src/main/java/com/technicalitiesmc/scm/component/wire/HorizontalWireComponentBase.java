package com.technicalitiesmc.scm.component.wire;

import com.mojang.math.Vector3f;
import com.technicalitiesmc.lib.circuit.component.CircuitEvent;
import com.technicalitiesmc.lib.circuit.component.ComponentContext;
import com.technicalitiesmc.lib.circuit.component.ComponentEventMap;
import com.technicalitiesmc.lib.circuit.component.ComponentType;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.lib.util.Utils;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMItemTags;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;

public abstract class HorizontalWireComponentBase<T extends HorizontalWireComponentBase<T>> extends WireComponentBase<T> {

    private static final WireConnectionState[] CYCLE_PRIORITIES = {
            WireConnectionState.BUNDLED_WIRE,
            WireConnectionState.WIRE,
            WireConnectionState.OUTPUT,
            WireConnectionState.INPUT,
            WireConnectionState.FORCE_DISCONNECTED
    };

    protected HorizontalWireComponentBase(RegistryObject<ComponentType> type, ComponentContext context, InterfaceLookup<T> interfaceLookup) {
        super(type, context, interfaceLookup);
    }

    protected HorizontalWireComponentBase(
            RegistryObject<ComponentType> type, ComponentContext context, InterfaceLookup<T> interfaceLookup,
            Map<VecDirection, WireConnectionState> connectionStates
    ) {
        super(type, context, interfaceLookup, connectionStates);
    }

    @Override
    protected VecDirectionFlags getConnectableSides() {
        return VecDirectionFlags.all();
    }

    @Override
    public void update(ComponentEventMap events, boolean tick) {
        // If the support component below is gone, remove this and skip the update
        if (!ensureSupported(events)) {
            return;
        }

        super.update(events, tick);
    }

    @Override
    public InteractionResult use(Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
        // If clicked with a stick
        var stack = player.getItemInHand(hand);
        if (!stack.isEmpty() && stack.is(SCMItemTags.ROTATES_COMPONENTS)) {
            // Calculate which side the player is interacting with
            var side = sideHit.getAxis() != Direction.Axis.Y ? sideHit :
                    Utils.calculatePlanarDirection(hit.x() - 0.5f, hit.z() - 0.5f);

            var state = getState(side);
            var newState = state;
            var neighbor = findNeighbor(side);

            // If there is no neighbor
            if (neighbor == null) {
                // Set the state to disconnected
                newState = WireConnectionState.DISCONNECTED;
            } else {
                // Otherwise, cycle to the next valid state
                newState = Utils.cycleConditionally(CYCLE_PRIORITIES, state, candidate -> {
                    // Valid if disconnected, or we find a matching interface
                    return !candidate.isConnected()
                            || (candidate == WireConnectionState.INPUT && neighbor instanceof HorizontalWireComponentBase<?>)
                            || neighbor.getInterface(side.getOpposite(), candidate.getTargetInterface()) != null;
                });
            }
            // If we have found a new state, update it
            if (newState != state) {
                manuallySetState(side, newState);

                if (neighbor instanceof HorizontalWireComponentBase<?> wire) {
                    wire.setState(side.getOpposite(), newState.getOpposite());
                }

                return InteractionResult.sidedSuccess(player.level.isClientSide());
            }
        }

        return super.use(player, hand, sideHit, hit);
    }

    private void manuallySetState(VecDirection side, WireConnectionState newState) {
        setState(side, newState);

        var events = ComponentEventMap.empty();
        var disconnected = VecDirectionFlags.none();
        if (newState.isConnected()) {
            var builder = new ComponentEventMap.Builder();
            builder.add(side, CircuitEvent.REDSTONE, CircuitEvent.BUNDLED_REDSTONE);
            events = builder.build();
        } else {
            disconnected = VecDirectionFlags.of(side);
        }
        updateSignals(events, disconnected);
    }

}
