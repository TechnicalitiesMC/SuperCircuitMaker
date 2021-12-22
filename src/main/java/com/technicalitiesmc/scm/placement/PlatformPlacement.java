package com.technicalitiesmc.scm.placement;

import com.technicalitiesmc.lib.circuit.placement.PlacementContext;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.scm.init.SCMComponents;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

public class PlatformPlacement extends SimplePlacement {

    public PlatformPlacement() {
        super(SCMComponents.PLATFORM, true, false);
    }

    @Nullable
    @Override
    protected State tryCaptureState(PlacementContext.Client context, Vec3i pos, VecDirection clickedFace) {
        if (!context.isWithinBounds(pos.above())) {
            return null;
        }
        return super.tryCaptureState(context, pos, clickedFace);
    }

}
