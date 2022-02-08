package com.technicalitiesmc.scm.placement;

import com.technicalitiesmc.lib.circuit.component.ComponentType;
import com.technicalitiesmc.lib.circuit.placement.PlacementContext;
import com.technicalitiesmc.lib.math.VecDirection;
import net.minecraft.core.Vec3i;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

public class NoTopLayerSimplePlacement extends SimplePlacement {

    public NoTopLayerSimplePlacement(RegistryObject<ComponentType> type, boolean tryClickPosition, boolean requiresSolidSurface) {
        super(type, tryClickPosition, requiresSolidSurface);
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
