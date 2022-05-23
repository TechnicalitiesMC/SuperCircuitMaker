package com.technicalitiesmc.scm.component.wire;

import com.mojang.math.Vector3f;
import com.technicalitiesmc.lib.circuit.component.CircuitComponent;
import com.technicalitiesmc.lib.circuit.component.ComponentContext;
import com.technicalitiesmc.lib.circuit.component.ComponentEventMap;
import com.technicalitiesmc.lib.circuit.component.ComponentType;
import com.technicalitiesmc.lib.init.TKLibItemTags;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.lib.util.Utils;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

public abstract class HorizontalWireComponentBase<T extends HorizontalWireComponentBase<T>> extends WireComponentBase<T> {

    protected HorizontalWireComponentBase(RegistryObject<ComponentType> type, ComponentContext context, InterfaceLookup<T> interfaceLookup) {
        super(type, context, interfaceLookup);
    }

    @Override
    protected VecDirectionFlags getConnectableSides() {
        return VecDirectionFlags.all();
    }

    @Nullable
    @Override
    protected CircuitComponent findConnectionTarget(VecDirection side) {
        return findNeighbor(side);
    }

    @Override
    public void update(ComponentEventMap events, boolean tick) {
        // If the support component below is gone, remove this and skip the update
        if (!ensureSupported(events)) {
            return;
        }
        // If all is good, continue with the update
        super.update(events, tick);
    }

    @Override
    public InteractionResult use(Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
        var stack = player.getItemInHand(hand);
        if (!stack.isEmpty() && stack.is(TKLibItemTags.TOOLS_WRENCH)) {
            // Calculate which side the player is interacting with
            var side = sideHit.getAxis() != Direction.Axis.Y ? sideHit :
                    Utils.calculatePlanarDirection(hit.x() - 0.5f, hit.z() - 0.5f);
            // Compute the new state
            var newState = computeNewState(side, true);
            if (newState != getState(side)) {
                setStateInternal(side, newState);
                updateSignals(VecDirectionFlags.of(side));
                return InteractionResult.sidedSuccess(player.level.isClientSide());
            }
            return InteractionResult.PASS;
        }
        return super.use(player, hand, sideHit, hit);
    }

}
