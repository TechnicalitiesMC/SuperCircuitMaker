package com.technicalitiesmc.scm.network;

import com.technicalitiesmc.lib.network.Packet;
import com.technicalitiesmc.scm.init.SCMItems;
import com.technicalitiesmc.scm.item.PaletteItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.network.NetworkEvent;

public class PickPaletteColorPacket implements Packet {

    private final DyeColor color;

    public PickPaletteColorPacket(DyeColor color) {
        this.color = color;
    }

    public PickPaletteColorPacket(FriendlyByteBuf buf) {
        this.color = buf.readEnum(DyeColor.class);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(color);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null) {
                return;
            }
            var stack = player.getMainHandItem();
            if (!stack.isEmpty() && stack.is(SCMItems.PALETTE.get())) {
                PaletteItem.setColor(stack, color);
                return;
            }
            stack = player.getOffhandItem();
            if (!stack.isEmpty() && stack.is(SCMItems.PALETTE.get())) {
                PaletteItem.setColor(stack, color);
            }
        });
        return true;
    }

}
