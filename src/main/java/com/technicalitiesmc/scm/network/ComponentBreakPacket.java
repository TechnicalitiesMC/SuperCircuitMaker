package com.technicalitiesmc.scm.network;

import com.technicalitiesmc.lib.circuit.component.ComponentHarvestContext;
import com.technicalitiesmc.lib.circuit.component.ComponentSlot;
import com.technicalitiesmc.lib.network.Packet;
import com.technicalitiesmc.scm.block.CircuitBlock;
import com.technicalitiesmc.scm.circuit.server.ServerTileAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.network.NetworkEvent;

public class ComponentBreakPacket implements Packet {

    private static final Capability<CircuitBlock.Data> DATA_CAPABILITY = CapabilityManager.get(new CapabilityToken<>(){});

    private final BlockPos pos;
    private final Vec3i componentPos;
    private final ComponentSlot slot;

    public ComponentBreakPacket(BlockPos pos, Vec3i componentPos, ComponentSlot slot) {
        this.pos = pos;
        this.componentPos = componentPos;
        this.slot = slot;
    }

    public ComponentBreakPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.componentPos = buf.readBlockPos();
        this.slot = buf.readEnum(ComponentSlot.class);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBlockPos(new BlockPos(componentPos));
        buf.writeEnum(slot);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            var level = context.getSender().getLevel();
            var entity = level.getBlockEntity(pos);
            if (entity == null) {
                return;
            }
            var data = entity.getCapability(DATA_CAPABILITY).orElse(null);
            if (data == null) {
                return;
            }
            var accessor = data.getOrCreateAccessor();
            if (accessor instanceof ServerTileAccessor sta) {
                sta.tryHarvest(componentPos, slot, ComponentHarvestContext.forPlayer(context.getSender()));
            }
        });
        return true;
    }

}
