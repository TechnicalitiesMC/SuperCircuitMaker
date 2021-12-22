package com.technicalitiesmc.scm.placement;

import com.technicalitiesmc.lib.circuit.placement.ComponentPlacement;
import com.technicalitiesmc.lib.circuit.placement.PlacementContext;
import com.technicalitiesmc.lib.math.VecDirection;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;

public abstract class SinglePlacement<S> implements ComponentPlacement {

    private final boolean tryClickPosition;

    protected SinglePlacement(boolean tryClickPosition) {
        this.tryClickPosition = tryClickPosition;
    }

    @Nullable
    protected abstract S tryCaptureState(PlacementContext.Client context, Vec3i pos, VecDirection clickedFace);

    protected abstract void serializeState(FriendlyByteBuf buf, S state);

    protected abstract S deserializeState(FriendlyByteBuf buf);

    protected abstract void put(PlacementContext.Server context, S state);

    @Override
    public Instance begin() {
        return new Instance();
    }

    @Override
    public Instance deserialize(FriendlyByteBuf buf) {
        return new Instance(buf);
    }

    private class Instance implements ComponentPlacement.Instance {

        private S state;

        public Instance() {
        }

        public Instance(FriendlyByteBuf buf) {
            this.state = deserializeState(buf);
        }

        @Override
        public boolean tick(PlacementContext.Client context, Vec3i clickedPos, VecDirection clickedFace) {
            // Attempt to capture placement at the clicked position
            if (tryClickPosition) {
                state = tryCaptureState(context, clickedPos, clickedFace);
            }
            if (state == null) {
                state = tryCaptureState(context, clickedPos.offset(clickedFace.getOffset()), clickedFace);
            }
            return false;
        }

        @Override
        public void stopPlacing(PlacementContext.Client context) {
            // NO-OP
        }

        @Override
        public boolean isValid(PlacementContext.Client context) {
            // Only valid if the state is non-null
            return state != null;
        }

        @Override
        public void serialize(FriendlyByteBuf buf) {
            serializeState(buf, state);
        }

        @Override
        public void place(PlacementContext.Server context) {
            put(context, state);
        }

    }

}
