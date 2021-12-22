package com.technicalitiesmc.scm.item;

import com.technicalitiesmc.lib.circuit.placement.ComponentPlacement;
import com.technicalitiesmc.lib.item.TKItem;
import com.technicalitiesmc.scm.SuperCircuitMaker;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;

public class SimpleComponentItem extends TKItem {

    private static final Capability<ComponentPlacement> COMPONENT_PLACEMENT_CAPABILITY = CapabilityManager.get(new CapabilityToken<>(){});

    private final LazyOptional<ComponentPlacement> placement;

    public SimpleComponentItem(ComponentPlacement placement) {
        super(new Properties().tab(SuperCircuitMaker.CREATIVE_TAB));
        this.placement = LazyOptional.of(() -> placement);
        addCapability(COMPONENT_PLACEMENT_CAPABILITY, this::getPlacement);
    }

    private LazyOptional<ComponentPlacement> getPlacement(ItemStack stack) {
        return placement;
    }

}
