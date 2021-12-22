package com.technicalitiesmc.scm.init;

import com.technicalitiesmc.scm.SuperCircuitMaker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nonnull;

public class SCMSoundEvents {

    public static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister.create(SoundEvent.class, SuperCircuitMaker.MODID);

    public static final RegistryObject<SoundEvent> COMPONENT_PLACE = register("component_place", "component.place");

    @Nonnull
    private static RegistryObject<SoundEvent> register(String name, String path) {
        return REGISTRY.register(name, () -> new SoundEvent(new ResourceLocation(SuperCircuitMaker.MODID, path)));
    }

}
