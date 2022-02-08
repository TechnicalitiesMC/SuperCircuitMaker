package com.technicalitiesmc.scm.client;

import com.technicalitiesmc.lib.util.Utils;
import com.technicalitiesmc.scm.SuperCircuitMaker;
import com.technicalitiesmc.scm.block.CircuitBlock;
import com.technicalitiesmc.scm.client.screen.PaletteScreen;
import com.technicalitiesmc.scm.init.SCMItems;
import com.technicalitiesmc.scm.item.PaletteItem;
import com.technicalitiesmc.scm.placement.ComponentPlacementHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.DrawSelectionEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SuperCircuitMaker.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SCMClientEventHandler {

    private static int busyTimer = 0;
    private static boolean partial = false;
    private static KeyMapping partialMapping;
    private static InteractionHand partialHand;

    @SubscribeEvent
    public static void onClickInput(InputEvent.ClickInputEvent event) {
        if (busyTimer > 0) {
            event.setCanceled(true);
            return;
        }
        var minecraft = Minecraft.getInstance();
        if (!(minecraft.hitResult instanceof BlockHitResult hit)) {
            return;
        }
        var state = Utils.resolveHit(minecraft.level, hit);
        if (!(state.getBlock() instanceof CircuitBlock block)) {
            return;
        }

        InteractionResult result;
        if (event.isUseItem()) {
            if (partial) {
                result = InteractionResult.CONSUME_PARTIAL;
            } else {
                result = minecraft.player.isCrouching() ? InteractionResult.PASS : block.onClientUse(state, minecraft.level, hit.getBlockPos(), minecraft.player, event.getHand(), hit);
            }
            if (result == InteractionResult.PASS) {
                result = ComponentPlacementHandler.onClientUse(state, minecraft.level, hit.getBlockPos(), minecraft.player, event.getHand(), hit);
            }
            if (result == InteractionResult.CONSUME_PARTIAL) {
                partial = true;
                partialMapping = event.getKeyMapping();
                partialHand = event.getHand();
            }
        } else if (event.isAttack()) {
            result = block.onClientClicked(state, minecraft.level, hit.getBlockPos(), minecraft.player, event.getHand(), hit);
        } else if (event.isPickBlock()) {
            CircuitBlock.picking = true;
            return;
        } else {
            return;
        }
        if (result == InteractionResult.PASS) {
            return;
        }
        event.setCanceled(result.consumesAction());
        event.setSwingHand(result.shouldSwing());
        if (result.consumesAction() && result != InteractionResult.CONSUME_PARTIAL) {
            busyTimer = 5;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        CircuitBlock.picking = false;
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (busyTimer > 0) {
            busyTimer--;
        }

        if (!partial) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !(minecraft.hitResult instanceof BlockHitResult hit)) {
            return;
        }
        var state = Utils.resolveHit(minecraft.level, hit);
        if (!(state.getBlock() instanceof CircuitBlock)) {
            return;
        }

        if (partialMapping.isDown()) {
            var result = ComponentPlacementHandler.onClientUse(state, minecraft.level, hit.getBlockPos(), minecraft.player, partialHand, hit);
            if (result != InteractionResult.CONSUME_PARTIAL) {
                partial = false;
                busyTimer = 5;
            }
        } else {
            if (ComponentPlacementHandler.onClientStopUsing(minecraft.level, minecraft.player) || busyTimer > 0) {
                partial = false;
                busyTimer = 2;
            }
        }
    }

    @SubscribeEvent
    public static void onDrawBlockHighlight(DrawSelectionEvent.HighlightBlock event) {
        var minecraft = Minecraft.getInstance();
        if (!(minecraft.hitResult instanceof BlockHitResult hit)) {
            return;
        }
        var state = Utils.resolveHit(minecraft.level, hit);
        if (!(state.getBlock() instanceof CircuitBlock)) {
            return;
        }

        if (ComponentPlacementHandler.onDrawBlockHighlight(minecraft.level, minecraft.player, event.getMultiBufferSource(), event.getPoseStack(), event.getPartialTicks())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTickPalette(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (Screen.hasAltDown() && mc.screen == null) {
            var stack = mc.player.getMainHandItem();
            if (!stack.isEmpty() && stack.is(SCMItems.PALETTE.get())) {
                mc.setScreen(new PaletteScreen(PaletteItem.getColor(stack)));
                return;
            }
            stack = mc.player.getOffhandItem();
            if (!stack.isEmpty() && stack.is(SCMItems.PALETTE.get())) {
                mc.setScreen(new PaletteScreen(PaletteItem.getColor(stack)));
            }
        }
    }

    @SubscribeEvent
    public static void onDrawPaletteOverlay(RenderGameOverlayEvent.Post event) {
        // TODO: Migrate to overlay?
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }

        var stack = mc.player.getMainHandItem();
        if (!stack.isEmpty() && stack.is(SCMItems.PALETTE.get())) {
            var poseStack = event.getMatrixStack();
            poseStack.pushPose();
            poseStack.translate(
                    mc.getWindow().getGuiScaledWidth() - PaletteScreen.TOTAL_SIZE * 0.75f - 24,
                    mc.getWindow().getGuiScaledHeight() - PaletteScreen.TOTAL_SIZE * 0.75f - 24,
                    0
            );
            poseStack.scale(0.75f, 0.75f, 1f);
            PaletteScreen.drawPalette(event.getMatrixStack(), PaletteItem.getColor(stack), 0);
            poseStack.popPose();
            return;
        }

        stack = mc.player.getOffhandItem();
        if (!stack.isEmpty() && stack.is(SCMItems.PALETTE.get())) {
            var poseStack = event.getMatrixStack();
            poseStack.pushPose();
            poseStack.translate(
                    24,
                    mc.getWindow().getGuiScaledHeight() - PaletteScreen.TOTAL_SIZE * 0.75f - 24,
                    0
            );
            poseStack.scale(0.75f, 0.75f, 1f);
            PaletteScreen.drawPalette(event.getMatrixStack(), PaletteItem.getColor(stack), 0);
            poseStack.popPose();
        }
    }

}
