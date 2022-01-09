package com.technicalitiesmc.scm.placement;

import com.technicalitiesmc.lib.circuit.component.ComponentType;
import com.technicalitiesmc.lib.circuit.placement.PlacementContext;
import com.technicalitiesmc.lib.math.VecDirection;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;

public class SimplePlacement extends SinglePlacement<SimplePlacement.State> {

    private final RegistryObject<ComponentType> type;
    private final boolean requiresSolidSurface;

    public SimplePlacement(RegistryObject<ComponentType> type, boolean tryClickPosition, boolean requiresSolidSurface) {
        super(tryClickPosition);
        this.type = type;
        this.requiresSolidSurface = requiresSolidSurface;
    }

    @Nullable
    @Override
    protected State tryCaptureState(PlacementContext.Client context, Vec3i pos, VecDirection clickedFace) {
        if ((!requiresSolidSurface || context.isTopSolid(pos.below())) && context.canPlace(pos, type.get())) {
            return new State(pos);
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
        if (context.tryPut(state.pos(), type.get())) {
            context.consumeItems(1);
            context.playSound();
        }
    }

    public static record State(Vec3i pos) {

        public void serialize(FriendlyByteBuf buf) {
            buf.writeInt(pos().getX()).writeInt(pos().getY()).writeInt(pos().getZ());
        }

        public static State deserialize(FriendlyByteBuf buf) {
            var pos = new Vec3i(buf.readInt(), buf.readInt(), buf.readInt());
            return new State(pos);
        }

    }

}
