package com.technicalitiesmc.scm.init;

import com.technicalitiesmc.lib.circuit.placement.ComponentPlacement;
import com.technicalitiesmc.scm.SuperCircuitMaker;
import com.technicalitiesmc.scm.block.CircuitBlock;
import com.technicalitiesmc.scm.circuit.server.CircuitCache;
import com.technicalitiesmc.scm.placement.PlayerPlacementData;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class SCMCapabilities {

    private static final ResourceLocation PLAYER_PLACEMENT_DATA_NAME = new ResourceLocation(SuperCircuitMaker.MODID, "player_placement_data");
    private static final ResourceLocation COMPONENT_PLACEMENT_NAME = new ResourceLocation(SuperCircuitMaker.MODID, "component_placement");
    private static final ResourceLocation CIRCUIT_CACHE_NAME = new ResourceLocation(SuperCircuitMaker.MODID, "circuit_cache");

    private static final Capability<PlayerPlacementData> PLAYER_PLACEMENT_DATA_CAPABILITY = CapabilityManager.get(new CapabilityToken<>(){});
    private static final Capability<ComponentPlacement> COMPONENT_PLACEMENT_CAPABILITY = CapabilityManager.get(new CapabilityToken<>(){});
    private static final Capability<CircuitCache> CIRCUIT_CACHE_CAPABILITY = CapabilityManager.get(new CapabilityToken<>(){});

    public static void onCapabilityRegistration(RegisterCapabilitiesEvent event) {
        event.register(PlayerPlacementData.class);
        event.register(CircuitCache.class);
        event.register(CircuitBlock.Data.class);
    }

    public static void onAttachEntityCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof Player)) {
            return;
        }
        var placementData = LazyOptional.of(PlayerPlacementData::new);
        event.addCapability(PLAYER_PLACEMENT_DATA_NAME, new ICapabilityProvider() {
            @Nonnull
            @Override
            public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
                if (cap == PLAYER_PLACEMENT_DATA_CAPABILITY) {
                    return placementData.cast();
                }
                return LazyOptional.empty();
            }
        });
        event.addListener(placementData::invalidate);
    }

    public static void onAttachItemStackCapabilities(AttachCapabilitiesEvent<ItemStack> event) {
        var p = PLACEMENT_MAP.get(event.getObject().getItem());
        if (p == null) {
            return;
        }

        var placement = LazyOptional.of(() -> p);
        event.addCapability(COMPONENT_PLACEMENT_NAME, new ICapabilityProvider() {
            @Nonnull
            @Override
            public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
                if (cap == COMPONENT_PLACEMENT_CAPABILITY) {
                    return placement.cast();
                }
                return LazyOptional.empty();
            }
        });
        event.addListener(placement::invalidate);
    }

    public static void onAttachLevelCapabilities(AttachCapabilitiesEvent<Level> event) {
        if (!(event.getObject() instanceof ServerLevel sl)) {
            return;
        }

        var cache = LazyOptional.of(() -> new CircuitCache(sl));
        event.addCapability(PLAYER_PLACEMENT_DATA_NAME, new ICapabilityProvider() {
            @Nonnull
            @Override
            public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
                if (cap == CIRCUIT_CACHE_CAPABILITY) {
                    return cache.cast();
                }
                return LazyOptional.empty();
            }
        });
        event.addListener(cache::invalidate);
    }

    private static final Map<Item, ComponentPlacement> PLACEMENT_MAP = new HashMap<>();
    public static void addPlacementCapability(Item item, ComponentPlacement placement) {
        PLACEMENT_MAP.put(item, placement);
    }

}
