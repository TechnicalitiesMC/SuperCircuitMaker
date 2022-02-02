package com.technicalitiesmc.scm.init;

import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.scm.SuperCircuitMaker;
import com.technicalitiesmc.scm.component.wire.BundledWireComponent;
import com.technicalitiesmc.scm.component.wire.ColoredWireComponent;
import com.technicalitiesmc.scm.component.wire.WireConnectionState;
import com.technicalitiesmc.scm.component.wire.WireVisualConnectionState;
import com.technicalitiesmc.scm.item.ScrewdriverItem;
import com.technicalitiesmc.scm.item.SimpleComponentItem;
import com.technicalitiesmc.scm.placement.PlatformPlacement;
import com.technicalitiesmc.scm.placement.SimplePlacement;
import com.technicalitiesmc.scm.placement.WirePlacement;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.function.Function;
import java.util.function.Supplier;

public final class SCMItems {

    public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, SuperCircuitMaker.MODID);

    public static final RegistryObject<BlockItem> CIRCUIT = register(SCMBlocks.CIRCUIT);

    public static final RegistryObject<BlockItem> INSPECTOR = register(SCMBlocks.INSPECTOR);

    public static final RegistryObject<Item> TINY_REDSTONE = register("tiny_redstone", () -> {
        return new SimpleComponentItem(new WirePlacement(SCMComponents.REDSTONE_WIRE, (context, connections, disconnectOthers) -> {
            var connectionStates = new EnumMap<VecDirection, WireConnectionState>(VecDirection.class);
//            if (disconnectOthers) {
//                for (var side : VecDirectionFlags.horizontals()) {
//                    if (!connections.has(side)) {
//                        connectionStates.put(side, WireConnectionState.FORCE_DISCONNECTED);
//                    }
//                }
//            }
            return new ColoredWireComponent(context, connectionStates);
        }, connections -> {
            return SCMComponents.REDSTONE_WIRE.get().getDefaultState()
                    .setValue(ColoredWireComponent.PROP_NEG_X, connections.has(VecDirection.NEG_X) ? WireVisualConnectionState.ANODE : WireVisualConnectionState.DISCONNECTED)
                    .setValue(ColoredWireComponent.PROP_POS_X, connections.has(VecDirection.POS_X) ? WireVisualConnectionState.ANODE : WireVisualConnectionState.DISCONNECTED)
                    .setValue(ColoredWireComponent.PROP_NEG_Z, connections.has(VecDirection.NEG_Z) ? WireVisualConnectionState.ANODE : WireVisualConnectionState.DISCONNECTED)
                    .setValue(ColoredWireComponent.PROP_POS_Z, connections.has(VecDirection.POS_Z) ? WireVisualConnectionState.ANODE : WireVisualConnectionState.DISCONNECTED);
        }));
    });
    public static final RegistryObject<Item> REDSTONE_STICK = register("redstone_stick", () -> {
        return new SimpleComponentItem(new SimplePlacement(SCMComponents.VERTICAL_WIRE, true, false));
    });

    public static final RegistryObject<Item> TINY_RGB_REDSTONE = register("tiny_rgb_redstone", () -> {
//        return new SimpleComponentItem(new SimplePlacement(SCMComponents.BUNDLED_WIRE, false, true));
        return new SimpleComponentItem(new WirePlacement(SCMComponents.BUNDLED_WIRE, (context, connections, disconnectOthers) -> {
            var connectionStates = new EnumMap<VecDirection, WireConnectionState>(VecDirection.class);
//            if (disconnectOthers) {
//                for (var side : VecDirectionFlags.horizontals()) {
//                    if (!connections.has(side)) {
//                        connectionStates.put(side, WireConnectionState.FORCE_DISCONNECTED);
//                    }
//                }
//            }
            return new BundledWireComponent(context, connectionStates);
        }, connections -> {
            return SCMComponents.BUNDLED_WIRE.get().getDefaultState()
                    .setValue(BundledWireComponent.PROP_NEG_X, connections.has(VecDirection.NEG_X) ? WireVisualConnectionState.ANODE : WireVisualConnectionState.DISCONNECTED)
                    .setValue(BundledWireComponent.PROP_POS_X, connections.has(VecDirection.POS_X) ? WireVisualConnectionState.ANODE : WireVisualConnectionState.DISCONNECTED)
                    .setValue(BundledWireComponent.PROP_NEG_Z, connections.has(VecDirection.NEG_Z) ? WireVisualConnectionState.ANODE : WireVisualConnectionState.DISCONNECTED)
                    .setValue(BundledWireComponent.PROP_POS_Z, connections.has(VecDirection.POS_Z) ? WireVisualConnectionState.ANODE : WireVisualConnectionState.DISCONNECTED);
        }));
    });

    public static final RegistryObject<Item> RANDOMIZER = register("randomizer", () -> {
        return new SimpleComponentItem(new SimplePlacement(SCMComponents.RANDOMIZER, false, true));
    });
    public static final RegistryObject<Item> DELAY = register("delay", () -> {
        return new SimpleComponentItem(new SimplePlacement(SCMComponents.DELAY, false, true));
    });
    public static final RegistryObject<Item> PULSAR = register("pulsar", () -> {
        return new SimpleComponentItem(new SimplePlacement(SCMComponents.PULSAR, false, true));
    });

    public static final RegistryObject<Item> LAMP = register("lamp", () -> {
        return new SimpleComponentItem(new SimplePlacement(SCMComponents.LAMP, false, true));
    });

    public static final RegistryObject<Item> BUTTON = register("button", () -> {
        return new SimpleComponentItem(new SimplePlacement(SCMComponents.BUTTON, false, true));
    });

    public static final RegistryObject<Item> PLATFORM = register("platform", () -> {
        return new SimpleComponentItem(new PlatformPlacement());
    });

    public static final RegistryObject<Item> SCREWDRIVER = register("screwdriver", ScrewdriverItem::new);

    // Helpers
    private static <T extends Item> RegistryObject<T> register(String name, Supplier<T> factory) {
        return REGISTRY.register(name, factory);
    }

    private static <T extends Item> RegistryObject<T> register(RegistryObject<? extends Block> block, Function<Block, T> factory) {
        return register(block.getId().getPath(), () -> (T) factory.apply(block.get()));
    }

    private static RegistryObject<BlockItem> register(RegistryObject<Block> block) {
        return register(block, b -> new BlockItem(b, new Item.Properties().tab(SuperCircuitMaker.CREATIVE_TAB)));
    }

}
