package com.technicalitiesmc.scm.init;

import com.technicalitiesmc.scm.SuperCircuitMaker;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.item.Item;

public class SCMItemTags {

    public static final Tag.Named<Item> SHOWS_CIRCUIT_GRID = ItemTags.bind(SuperCircuitMaker.MODID + ":shows_circuit_grid");
    public static final Tag.Named<Item> ROTATES_COMPONENTS = ItemTags.bind(SuperCircuitMaker.MODID + ":rotates_components");

}
