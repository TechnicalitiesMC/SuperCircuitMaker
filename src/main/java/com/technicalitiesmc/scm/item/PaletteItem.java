package com.technicalitiesmc.scm.item;

import com.technicalitiesmc.lib.item.TKItem;
import com.technicalitiesmc.lib.util.DyeHolder;
import com.technicalitiesmc.scm.SuperCircuitMaker;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;

public class PaletteItem extends TKItem {

    private static final Capability<DyeHolder> DYE_HOLDER_CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {
    });

    public PaletteItem() {
        super(new Properties().tab(SuperCircuitMaker.CREATIVE_TAB).stacksTo(1));
        addCapability(DYE_HOLDER_CAPABILITY, s -> LazyOptional.of(() -> () -> getColor(s)));
    }

    public static DyeColor getColor(ItemStack stack) {
        var tag = stack.getOrCreateTag();
        return DyeColor.byId(tag.getInt("color"));
    }

    public static void setColor(ItemStack stack, DyeColor color) {
        stack.getOrCreateTag().putInt("color", color.getId());
    }

}
