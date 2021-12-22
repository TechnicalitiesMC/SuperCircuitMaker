package com.technicalitiesmc.scm.network;

import com.technicalitiesmc.lib.network.Packet;
import com.technicalitiesmc.scm.SuperCircuitMaker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Function;

public class SCMNetworkHandler {

    private static SimpleChannel INSTANCE;
    private static int ID = 0;

    public static void registerPackets() {
        INSTANCE = NetworkRegistry.newSimpleChannel(new ResourceLocation(SuperCircuitMaker.MODID, "main"),
                () -> "1.0",
                s -> true,
                s -> true);

        register(ComponentSyncPacket.class, ComponentSyncPacket::new);
        register(ComponentPlacePacket.class, ComponentPlacePacket::new);
        register(ComponentBreakPacket.class, ComponentBreakPacket::new);
        register(ComponentUsePacket.class, ComponentUsePacket::new);
    }

    private static <T extends Packet> void register(Class<T> type, Function<FriendlyByteBuf, T> decoder) {
        INSTANCE.messageBuilder(type, ID++)
                .encoder(Packet::encode)
                .decoder(decoder)
                .consumer((first, second) -> first.handle(second.get()) || false)
                .add();
    }

    public static void sendToClient(Packet packet, ServerPlayer player) {
        INSTANCE.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void broadcastToClientsWatching(Packet packet, ServerLevel level, ChunkPos pos) {
        level.getChunkSource().chunkMap.getPlayers(pos, false).forEach(player -> {
            sendToClient(packet, player);
        });
    }

    public static void sendToServer(Packet packet) {
        INSTANCE.sendToServer(packet);
    }

}
