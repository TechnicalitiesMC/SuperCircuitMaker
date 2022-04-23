package com.technicalitiesmc.scm.client;

import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfig {

    public static final ForgeConfigSpec SPEC = configure(new ForgeConfigSpec.Builder()).build();

    public static class Debug {

        public static ForgeConfigSpec.BooleanValue allowHidingComponents;

        private static void configure(ForgeConfigSpec.Builder builder) {
            allowHidingComponents = builder
                    .comment("Allows components to be hidden from a circuit (clientside only) by ")
                    .define("allow_hiding_components", false);
        }

    }

    private static ForgeConfigSpec.Builder configure(ForgeConfigSpec.Builder builder) {
        builder.push("Debug");
        Debug.configure(builder);
        builder.pop();
        return builder;
    }

}
