package com.technicalitiesmc.scm;

import com.technicalitiesmc.scm.circuit.server.CircuitCache;
import com.technicalitiesmc.scm.init.*;
import com.technicalitiesmc.scm.network.SCMNetworkHandler;
import com.technicalitiesmc.scm.placement.LeverPlacement;
import com.technicalitiesmc.scm.placement.TorchPlacement;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(SuperCircuitMaker.MODID)
public class SuperCircuitMaker {

    public static final String MODID = "supercircuitmaker";

    public static final CreativeModeTab CREATIVE_TAB = new CreativeModeTab(MODID) {
        public ItemStack makeIcon() {
            return new ItemStack(SCMItems.TINY_REDSTONE.get());
        }
    };

    public SuperCircuitMaker() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::setup);

        SCMBlocks.REGISTRY.register(bus);
        SCMBlockEntities.REGISTRY.register(bus);
        SCMItems.REGISTRY.register(bus);
        SCMComponents.REGISTRY.register(bus);
        SCMMenus.REGISTRY.register(bus);
        SCMSoundEvents.REGISTRY.register(bus);

        bus.addListener(SCMCapabilities::onCapabilityRegistration);
        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, SCMCapabilities::onAttachEntityCapabilities);
        MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class, SCMCapabilities::onAttachItemStackCapabilities);
        MinecraftForge.EVENT_BUS.addGenericListener(Level.class, SCMCapabilities::onAttachLevelCapabilities);

        MinecraftForge.EVENT_BUS.addListener(CircuitCache::tick);
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SCMNetworkHandler.registerPackets();

            SCMCapabilities.addPlacementCapability(Items.REDSTONE_TORCH, new TorchPlacement());
            SCMCapabilities.addPlacementCapability(Items.LEVER, new LeverPlacement());
        });
    }

}
