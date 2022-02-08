package com.technicalitiesmc.scm.network;

import com.technicalitiesmc.lib.circuit.component.ComponentType;
import com.technicalitiesmc.lib.circuit.placement.ComponentPlacement;
import com.technicalitiesmc.lib.circuit.placement.PlacementContext;
import com.technicalitiesmc.lib.network.Packet;
import com.technicalitiesmc.scm.block.CircuitBlock;
import com.technicalitiesmc.scm.circuit.server.ComponentInstance;
import com.technicalitiesmc.scm.circuit.server.ServerTileAccessor;
import com.technicalitiesmc.scm.init.SCMSoundEvents;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ComponentPlacePacket implements Packet {

    private static final Capability<CircuitBlock.Data> DATA_CAPABILITY = CapabilityManager.get(new CapabilityToken<>(){});
    private static final Capability<ComponentPlacement> COMPONENT_PLACEMENT_CAPABILITY = CapabilityManager.get(new CapabilityToken<>(){});

    private final BlockPos pos;
    private final InteractionHand hand;
    private final byte[] placementData;

    public ComponentPlacePacket(BlockPos pos, InteractionHand hand, ComponentPlacement.Instance placement) {
        this.pos = pos;
        this.hand = hand;

        var buf = Unpooled.buffer();
        placement.serialize(new FriendlyByteBuf(buf));
        this.placementData = Arrays.copyOf(buf.array(), buf.writerIndex());
        buf.release();
    }

    public ComponentPlacePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.hand = buf.readEnum(InteractionHand.class);
        this.placementData = buf.readByteArray();
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(hand);
        buf.writeByteArray(placementData);
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

            var item = context.getSender().getItemInHand(hand);
            var cap = item.getCapability(COMPONENT_PLACEMENT_CAPABILITY).orElse(null);

            var buf = Unpooled.wrappedBuffer(placementData);
            var placement = cap.deserialize(new FriendlyByteBuf(buf));
            buf.release();

            var accessor = data.getAccessor();
            placement.place(new SimpleServerContext(context.getSender(), accessor, level, pos, item, context.getSender().isCreative()));
        });
        return true;
    }

    private static class SimpleServerContext implements PlacementContext.Server {

        private final Player player;
        private final ServerTileAccessor accessor;
        private final ServerLevel level;
        private final BlockPos pos;
        private final ItemStack item;
        private final boolean isCreative;

        private SimpleServerContext(Player player, ServerTileAccessor accessor, ServerLevel level, BlockPos pos, ItemStack item, boolean isCreative) {
            this.player = player;
            this.accessor = accessor;
            this.level = level;
            this.pos = pos;
            this.item = item;
            this.isCreative = isCreative;
        }

        @Override
        public Player getPlayer() {
            return player;
        }

        @Override
        public boolean tryPut(Vec3i pos, ComponentType type, ComponentType.Factory factory) {
            return accessor.tryPut(pos, type, factory) != null;
        }

        @Override
        public boolean tryPutAll(Predicate<PlacementContext.MultiPlacementContext> function) {
            var attempts = new ArrayList<Supplier<ComponentInstance>>();
            var success = function.test((pos, type, factory) -> {
                var attempt = accessor.tryPutLater(pos, type, factory);
                if (attempt == null) {
                    return false;
                } else {
                    attempts.add(attempt);
                    return true;
                }
            });
            if (!success || attempts.isEmpty()) {
                return false;
            }
            attempts.forEach(Supplier::get);
            return true;
        }

        @Override
        public void consumeItems(int count) {
            if (!isCreative) {
                item.shrink(count);
            }
        }

        @Override
        public void playSound() {
            var pitch = 0.85F + (float) (Math.random() * 0.05);
            level.playSound(null, pos, SCMSoundEvents.COMPONENT_PLACE.get(), SoundSource.BLOCKS, 0.1f, pitch);
        }

    }

}
