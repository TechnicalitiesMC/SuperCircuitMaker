package com.technicalitiesmc.scm.placement;

import com.technicalitiesmc.lib.circuit.component.ComponentType;
import com.technicalitiesmc.lib.circuit.placement.PlacementContext;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.scm.component.digital.LeverComponent;
import com.technicalitiesmc.scm.init.SCMComponents;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;

public class LeverPlacement extends SinglePlacement<LeverPlacement.State> {

    public LeverPlacement() {
        super(false);
    }

    private ComponentType lever() {
        return SCMComponents.LEVER.get();
    }

    @Nullable
    @Override
    protected State tryCaptureState(PlacementContext.Client context, Vec3i pos, VecDirection clickedFace) {
        if (context.isTopSolid(pos.below()) && context.canPlace(pos, lever())) {
            return new State(pos, context.getHorizontalFacing().getHorizontalIndex());
        }
        return null;
    }

    @Override
    protected void serializeState(FriendlyByteBuf buf, State state) {
        state.serialize(buf);
    }

    @Override
    protected State deserializeState(FriendlyByteBuf buf) {
        return State.deserialize(buf);
    }

    @Override
    protected void put(PlacementContext.Server context, State state) {
        context.tryPut(state.pos(), lever(), ctx -> {
            var lever = new LeverComponent(ctx);
            lever.setRotation(state.rotation());
            return lever;
        });
        context.consumeItems(1);
        context.playSound();
    }

    public static record State(Vec3i pos, int rotation) {

        public void serialize(FriendlyByteBuf buf) {
            buf.writeInt(pos().getX()).writeInt(pos().getY()).writeInt(pos().getZ());
            buf.writeInt(rotation());
        }

        public static State deserialize(FriendlyByteBuf buf) {
            var pos = new Vec3i(buf.readInt(), buf.readInt(), buf.readInt());
            var rotation = buf.readInt();
            return new State(pos, rotation);
        }

    }

}
