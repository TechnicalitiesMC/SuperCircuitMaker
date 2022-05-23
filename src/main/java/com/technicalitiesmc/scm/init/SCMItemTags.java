package com.technicalitiesmc.scm.init;

import com.technicalitiesmc.scm.SuperCircuitMaker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class SCMItemTags {

    public static final TagKey<Item> SHOWS_CIRCUIT_GRID = ItemTags.create(new ResourceLocation(SuperCircuitMaker.MODID, "shows_circuit_grid"));
    public static final TagKey<Item> DBG_HIDES_COMPONENTS = ItemTags.create(new ResourceLocation(SuperCircuitMaker.MODID, "dbg_hides_components"));

}
