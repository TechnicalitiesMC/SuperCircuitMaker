package com.technicalitiesmc.scm;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(SuperCircuitMaker.MODID)
public class SuperCircuitMaker {

    public static final String MODID = "supercircuitmaker";

    public SuperCircuitMaker() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::setup);

//        bus.addListener(TKLibCapabilities::onCapabilityRegistration);
//
//        TKLibMenus.REGISTRY.register(bus);
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
//            TKLibNetworkHandler.registerPackets();
        });
    }

}
