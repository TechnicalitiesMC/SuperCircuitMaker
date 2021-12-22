package com.technicalitiesmc.scm.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.technicalitiesmc.lib.client.screen.TKMenuScreen;
import com.technicalitiesmc.scm.SuperCircuitMaker;
import com.technicalitiesmc.scm.menu.TimingMenu;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class TimingScreen extends TKMenuScreen<TimingMenu> {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(SuperCircuitMaker.MODID, "textures/gui/timing.png");
    private static final Component[] BUTTON_TEXT = {
            new TranslatableComponent("container." + SuperCircuitMaker.MODID + ".timing.dec_10s"),
            new TranslatableComponent("container." + SuperCircuitMaker.MODID + ".timing.dec_1s"),
            new TranslatableComponent("container." + SuperCircuitMaker.MODID + ".timing.inc_1s"),
            new TranslatableComponent("container." + SuperCircuitMaker.MODID + ".timing.inc_10s"),
            new TranslatableComponent("container." + SuperCircuitMaker.MODID + ".timing.dec_10t"),
            new TranslatableComponent("container." + SuperCircuitMaker.MODID + ".timing.dec_1t"),
            new TranslatableComponent("container." + SuperCircuitMaker.MODID + ".timing.inc_1t"),
            new TranslatableComponent("container." + SuperCircuitMaker.MODID + ".timing.inc_10t")
    };

    private Button[] buttons = new Button[8];

    public TimingScreen(TimingMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, BACKGROUND);
        imageWidth = 228;
        imageHeight = 50;
        titleLabelY = 8;
    }

    @Override
    protected void init() {
        super.init();

        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 4; j++) {
                var idx = i * 4 + j;
                buttons[idx] = addRenderableWidget(new Button(leftPos + 8 + 54 * j, topPos + 22, 50, 20, BUTTON_TEXT[idx], b -> {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, idx);
                }));
                buttons[idx].visible = i == 0;
            }
        }

        updateState();
    }

    @Override
    protected void renderLabels(PoseStack p_97808_, int p_97809_, int p_97810_) {
        var delay = menu.getDelay();
        String text;
        if (!hasShiftDown()) {
            text = (delay / 20) + (((delay % 20) / 20f) + "s").substring(1);
        } else {
            text = delay + "t";
        }
        var fullTitle = title.copy().append(text);
        font.draw(p_97808_, fullTitle, (imageWidth - font.width(fullTitle)) / 2f, titleLabelY, 0x404040);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateState();
    }

    private void updateState() {
        boolean shift = hasShiftDown();
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].visible = (i >= 4) == shift;
        }
    }

}
