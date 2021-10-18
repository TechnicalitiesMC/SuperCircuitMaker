package com.technicalitiesmc.scm;

import com.technicalitiesmc.scm.init.SCMBlocks;
import com.technicalitiesmc.scm.init.SCMItems;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(SuperCircuitMaker.MODID)
public class SuperCircuitMaker {

    public static final String MODID = "supercircuitmaker";

    public static final CreativeModeTab CREATIVE_TAB = new CreativeModeTab(-1, MODID) {
        public ItemStack makeIcon() {
            return new ItemStack(SCMItems.CIRCUIT.get());
        }
    };

    public SuperCircuitMaker() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::setup);

        SCMBlocks.REGISTRY.register(bus);
        SCMItems.REGISTRY.register(bus);
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
//            TKLibNetworkHandler.registerPackets();
        });
    }

}
