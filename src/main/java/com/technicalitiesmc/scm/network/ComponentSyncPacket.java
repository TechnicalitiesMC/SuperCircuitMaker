package com.technicalitiesmc.scm.network;

import com.technicalitiesmc.lib.circuit.component.ComponentSlot;
import com.technicalitiesmc.lib.circuit.component.ComponentState;
import com.technicalitiesmc.lib.network.Packet;
import com.technicalitiesmc.scm.block.CircuitBlock;
import com.technicalitiesmc.scm.circuit.CircuitAdjacency;
import com.technicalitiesmc.scm.circuit.client.ClientTile;
import com.technicalitiesmc.scm.circuit.util.ComponentPos;
import com.technicalitiesmc.scm.circuit.util.ComponentSlotPos;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;

public class ComponentSyncPacket implements Packet {

    private static final Capability<CircuitBlock.Data> DATA_CAPABILITY = CapabilityManager.get(new CapabilityToken<>(){});

    private final BlockPos pos;
    private final Map<ComponentSlotPos, ComponentState> states;
    private final CircuitAdjacency[] adjacency;

    public ComponentSyncPacket(BlockPos pos, Map<ComponentSlotPos, ComponentState> states, CircuitAdjacency[] adjacency) {
        this.pos = pos;
        this.states = states;
        this.adjacency = adjacency;
    }

    public ComponentSyncPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.states = new HashMap<>();
        this.adjacency = new CircuitAdjacency[4];

        for (int i = 0; i < adjacency.length; i++) {
            adjacency[i] = buf.readEnum(CircuitAdjacency.class);
        }

        var entries = buf.readShort();
        for (int i = 0; i < entries; i++) {
            var pos = new ComponentPos(buf.readInt(), buf.readInt(), buf.readInt());
            var slot = buf.readEnum(ComponentSlot.class);
            var state = buf.readBoolean() ? ComponentState.deserialize(buf) : null;
            states.put(new ComponentSlotPos(pos, slot), state);
        }
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        for (var adj : adjacency) {
            buf.writeEnum(adj);
        }
        buf.writeShort(states.size());
        states.forEach((pos, state) -> {
            buf.writeInt(pos.pos().x()).writeInt(pos.pos().y()).writeInt(pos.pos().z());
            buf.writeEnum(pos.slot());
            buf.writeBoolean(state != null);
            if (state != null) {
                state.serialize(buf);
            }
        });
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            var level = Minecraft.getInstance().level;
            var entity = level.getBlockEntity(pos);
            if (entity == null) {
                return;
            }

            var data = entity.getCapability(DATA_CAPABILITY).orElse(null);
            if (data == null) {
                return;
            }
            var accessor = data.getOrCreateAccessor();
            if (accessor instanceof ClientTile ct) {
                states.forEach((pos, state) -> {
                    ct.setState(pos.toAbsolute().pos(), pos.slot(), state);
                });
                ct.setAdjacency(adjacency);
            }
        });
        return true;
    }

}
