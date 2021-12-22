package com.technicalitiesmc.scm.placement;

import com.technicalitiesmc.lib.circuit.placement.ComponentPlacement;
import com.technicalitiesmc.lib.circuit.placement.PlacementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;

public class PlayerPlacementData {

    private ComponentPlacement.Instance placement;
    private PlacementContext.Client context;
    private BlockPos pos;
    private InteractionHand hand;

    public boolean isPlacing() {
        return placement != null;
    }

    public ComponentPlacement.Instance getPlacement() {
        return placement;
    }

    public PlacementContext.Client getContext() {
        return context;
    }

    public BlockPos getPos() {
        return pos;
    }

    public InteractionHand getHand() {
        return hand;
    }

    public void set(ComponentPlacement.Instance placement, PlacementContext.Client context, BlockPos pos, InteractionHand hand) {
        this.placement = placement;
        this.context = context;
        this.pos = pos;
        this.hand = hand;
    }

    public void reset() {
        set(null, null, null, null);
    }

}
