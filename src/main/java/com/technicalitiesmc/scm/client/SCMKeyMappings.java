package com.technicalitiesmc.scm.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.technicalitiesmc.scm.SuperCircuitMaker;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class SCMKeyMappings {

    public static final KeyMapping OPEN_PALETTE = new KeyMapping(
            "key." + SuperCircuitMaker.MODID + ".open_palette",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_LEFT_ALT),
            "key." + SuperCircuitMaker.MODID
    );

    public static final KeyMapping COMPONENT_PLACEMENT_MODIFIER = new KeyMapping(
            "key." + SuperCircuitMaker.MODID + ".component_placement_modifier",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_LEFT_CONTROL),
            "key." + SuperCircuitMaker.MODID
    );

    public static void register() {
        ClientRegistry.registerKeyBinding(OPEN_PALETTE);
        ClientRegistry.registerKeyBinding(COMPONENT_PLACEMENT_MODIFIER);
    }

}
