package com.technicalitiesmc.scm.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.technicalitiesmc.scm.SuperCircuitMaker;
import com.technicalitiesmc.scm.client.SCMKeyMappings;
import com.technicalitiesmc.scm.network.PickPaletteColorPacket;
import com.technicalitiesmc.scm.network.SCMNetworkHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.item.DyeColor;

public class PaletteScreen extends Screen {

    private static final String TITLE = "menu." + SuperCircuitMaker.MODID + ".palette";
    private static final int GRID_SIZE = 26;
    private static final int SQUARE_SIZE = 20;
    private static final int UNSELECTED_SHRINK = 2;
    public static final int TOTAL_SIZE = 3 * GRID_SIZE + SQUARE_SIZE;

    private DyeColor originalColor;
    private DyeColor color;

    public PaletteScreen(DyeColor defaultColor) {
        super(new TranslatableComponent(TITLE));
        this.originalColor = defaultColor;
        this.color = defaultColor;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
        SCMNetworkHandler.sendToServer(new PickPaletteColorPacket(color));
    }

    @Override
    public void tick() {
        if (SCMKeyMappings.OPEN_PALETTE.getKey().getType() == InputConstants.Type.KEYSYM &&
                !InputConstants.isKeyDown(minecraft.getWindow().getWindow(), SCMKeyMappings.OPEN_PALETTE.getKey().getValue())) {
            onClose();
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        poseStack.pushPose();
        poseStack.translate((width - TOTAL_SIZE) / 2f, (height - TOTAL_SIZE) / 2f, 0);
        drawPalette(poseStack, color, getBlitOffset());
        poseStack.popPose();
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        var x0 = (width - TOTAL_SIZE) / 2;
        var y0 = (height - TOTAL_SIZE) / 2;

        if (mouseX < x0 - 8 || mouseX >= x0 + TOTAL_SIZE + 8 || mouseY < y0 - 8 || mouseY >= y0 + TOTAL_SIZE + 8) {
            color = originalColor;
            return;
        }

        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                int minX = x0 + x * GRID_SIZE, minY = y0 + y * GRID_SIZE;
                int maxX = minX + SQUARE_SIZE, maxY = minY + SQUARE_SIZE;
                if (mouseX >= minX - UNSELECTED_SHRINK && mouseX < maxX + UNSELECTED_SHRINK && mouseY >= minY - UNSELECTED_SHRINK && mouseY < maxY + UNSELECTED_SHRINK) {
                    color = DyeColor.byId(y * 4 + x);
                }
            }
        }
    }

    public static void drawPalette(PoseStack poseStack, DyeColor selectedColor, int blitOffset) {
        fillGradient(poseStack,
                -8,
                -8,
                TOTAL_SIZE + 8,
                TOTAL_SIZE + 8,
                0x7010100F,
                0x8010100F,
                blitOffset
        );

        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                var color = DyeColor.byId(y * 4 + x);
                var tint = color.getFireworkColor() | 0xFF000000;
                var selected = color == selectedColor;

                int minX = x * GRID_SIZE, minY = y * GRID_SIZE;
                int maxX = minX + SQUARE_SIZE, maxY = minY + SQUARE_SIZE;

                fillGradient(poseStack,
                        minX - (selected ? UNSELECTED_SHRINK : 0),
                        minY - (selected ? UNSELECTED_SHRINK : 0),
                        maxX + (selected ? UNSELECTED_SHRINK : 0),
                        maxY + (selected ? UNSELECTED_SHRINK : 0),
                        selected ? 0xC0A0A0AF : 0xC010100F,
                        selected ? 0xD0A0A0AF : 0xD010100F,
                        blitOffset
                );
                fillGradient(poseStack,
                        minX + (selected ? 0 : UNSELECTED_SHRINK),
                        minY + (selected ? 0 : UNSELECTED_SHRINK),
                        maxX - (selected ? 0 : UNSELECTED_SHRINK),
                        maxY - (selected ? 0 : UNSELECTED_SHRINK),
                        tint,
                        tint,
                        blitOffset
                );
            }
        }
    }

}
