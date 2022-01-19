package com.technicalitiesmc.scm.circuit.server;

import com.technicalitiesmc.scm.SuperCircuitMaker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CircuitCache {

    private static final Capability<CircuitCache> CIRCUIT_CACHE_CAPABILITY = CapabilityManager.get(new CapabilityToken<>(){});

    private final ServerLevel level;
    private Map<UUID, Circuit> circuits = new HashMap<>(); // TODO: (N/U) Make final

    public CircuitCache(ServerLevel level) {
        this.level = level;
    }

    Circuit createUncached(UUID id) {
        var circuit = new Circuit(this, id, false);
        level.getDataStorage().set(getPath(id), circuit);
        return circuit;
    }

    Circuit getOrCreate(UUID id) {
        return circuits.computeIfAbsent(id, $ -> {
            return level.getDataStorage().computeIfAbsent(
                    t -> Circuit.load(this, id, t),
                    () -> new Circuit(this, id, true),
                    getPath(id)
            );
        });
    }

    private void tick() {
        // Evict unloaded circuits from memory
        var saved = new boolean[]{ false };
        circuits.values().removeIf(c -> {
            if (!c.isLoaded()) {
                var storage = level.getDataStorage();
                if (!saved[0]) {
                    saved[0] = true;
                    storage.save();
                }
                storage.cache.remove(getPath(c.getId()));
                return true;
            }
            return false;
        });

        // Try split all circuits as needed
        circuits = circuits.values().stream()
                .filter(c -> !c.isInvalid())
                .map(Circuit::maybeSplit)
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(Circuit::getId, Function.identity()));

        // Update valid circuits and evict invalid ones from memory
        circuits.values().removeIf(c -> {
            if (c.isInvalid()) {
                level.getDataStorage().set(getPath(c.getId()), null);
                return true;
            }
            c.update();
            return false;
        });
    }

    private static String getPath(UUID id) {
        return SuperCircuitMaker.MODID + "/circuit/" + id.toString().replace("-", "");
    }

    private static CircuitCache get(MinecraftServer server) {
        var level = server.overworld();
        return level.getCapability(CIRCUIT_CACHE_CAPABILITY).orElse(null);
    }

    public static Circuit getOrCreate(MinecraftServer server, UUID id) {
        return get(server).getOrCreate(id);
    }

    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        var server = ServerLifecycleHooks.getCurrentServer();
        get(server).tick();
    }

}
