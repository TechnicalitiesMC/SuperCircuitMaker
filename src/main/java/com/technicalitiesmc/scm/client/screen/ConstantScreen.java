package com.technicalitiesmc.scm.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.technicalitiesmc.lib.client.screen.TKMenuScreen;
import com.technicalitiesmc.scm.SuperCircuitMaker;
import com.technicalitiesmc.scm.menu.ConstantMenu;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ConstantScreen extends TKMenuScreen<ConstantMenu> {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(SuperCircuitMaker.MODID, "textures/gui/timing.png"); // TODO: make unique
    private static final Component[] BUTTON_TEXT = {
            new TranslatableComponent("container." + SuperCircuitMaker.MODID + ".constant.dec_20"),
            new TranslatableComponent("container." + SuperCircuitMaker.MODID + ".constant.dec_1"),
            new TranslatableComponent("container." + SuperCircuitMaker.MODID + ".constant.inc_1"),
            new TranslatableComponent("container." + SuperCircuitMaker.MODID + ".constant.inc_20")
    };

    private Button[] buttons = new Button[4];

    public ConstantScreen(ConstantMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, BACKGROUND);
        imageWidth = 228;
        imageHeight = 50;
        titleLabelY = 8;
    }

    @Override
    protected void init() {
        super.init();

        for (int i = 0; i < 4; i++) {
            var j = i;
            buttons[i] = addRenderableWidget(new Button(leftPos + 8 + 54 * i, topPos + 22, 50, 20, BUTTON_TEXT[i], b -> {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, j);
            }));
        }
    }

    @Override
    protected void renderLabels(PoseStack p_97808_, int p_97809_, int p_97810_) {
        var output = menu.getOutput();
        var fullTitle = title.copy().append("" + output);
        font.draw(p_97808_, fullTitle, (imageWidth - font.width(fullTitle)) / 2f, titleLabelY, 0x404040);
    }

}
