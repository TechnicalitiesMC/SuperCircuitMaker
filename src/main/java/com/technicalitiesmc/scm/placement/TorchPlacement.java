package com.technicalitiesmc.scm.placement;

import com.technicalitiesmc.lib.circuit.component.ComponentType;
import com.technicalitiesmc.lib.circuit.placement.PlacementContext;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.scm.init.SCMComponents;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;

public class TorchPlacement extends SinglePlacement<SimplePlacement.State> {

    public TorchPlacement() {
        super(false);
    }

    private ComponentType bottom() {
        return SCMComponents.TORCH_BOTTOM.get();
    }

    private ComponentType top() {
        return SCMComponents.TORCH_TOP.get();
    }

    @Nullable
    @Override
    protected SimplePlacement.State tryCaptureState(PlacementContext.Client context, Vec3i pos, VecDirection clickedFace) {
        if (context.isTopSolid(pos.below()) && context.canPlace(pos, bottom()) && context.canPlace(pos.above(), top())) {
            return new SimplePlacement.State(pos);
        }
        return null;
    }

    @Override
    protected void serializeState(FriendlyByteBuf buf, SimplePlacement.State state) {
        state.serialize(buf);
    }

    @Override
    protected SimplePlacement.State deserializeState(FriendlyByteBuf buf) {
        return SimplePlacement.State.deserialize(buf);
    }

    @Override
    protected void put(PlacementContext.Server context, SimplePlacement.State state) {
        if (context.tryPutAll(ctx -> {
            return ctx.at(state.pos(), bottom()) &&
                ctx.at(state.pos().above(), top());
        })) {
            context.consumeItems(1);
            context.playSound();
        }
    }

}
