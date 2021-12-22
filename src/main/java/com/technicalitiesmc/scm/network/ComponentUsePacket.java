package com.technicalitiesmc.scm.network;

import com.mojang.math.Vector3f;
import com.technicalitiesmc.lib.circuit.component.ComponentSlot;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.network.Packet;
import com.technicalitiesmc.scm.block.CircuitBlock;
import com.technicalitiesmc.scm.circuit.server.ServerTileAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.network.NetworkEvent;

public class ComponentUsePacket implements Packet {

    private static final Capability<CircuitBlock.Data> DATA_CAPABILITY = CapabilityManager.get(new CapabilityToken<>(){});

    private final BlockPos pos;
    private final Vec3i componentPos;
    private final ComponentSlot slot;
    private final InteractionHand hand;
    private final VecDirection sideHit;
    private final Vector3f hitPos;

    public ComponentUsePacket(BlockPos pos, Vec3i componentPos, ComponentSlot slot, InteractionHand hand, VecDirection sideHit, Vector3f hitPos) {
        this.pos = pos;
        this.componentPos = componentPos;
        this.slot = slot;
        this.hand = hand;
        this.sideHit = sideHit;
        this.hitPos = hitPos;
    }

    public ComponentUsePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.componentPos = buf.readBlockPos();
        this.slot = buf.readEnum(ComponentSlot.class);
        this.hand = buf.readEnum(InteractionHand.class);
        this.sideHit = buf.readEnum(VecDirection.class);
        this.hitPos = new Vector3f(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBlockPos(new BlockPos(componentPos));
        buf.writeEnum(slot);
        buf.writeEnum(hand);
        buf.writeEnum(sideHit);
        buf.writeFloat(hitPos.x()).writeFloat(hitPos.y()).writeFloat(hitPos.z());
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
                sta.use(componentPos, slot, context.getSender(), hand, sideHit, hitPos);
            }
        });
        return true;
    }

}
