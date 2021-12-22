package com.technicalitiesmc.scm.client;

import com.google.common.collect.ImmutableMap;
import com.technicalitiesmc.lib.circuit.component.ComponentState;
import com.technicalitiesmc.lib.circuit.component.ComponentType;
import com.technicalitiesmc.lib.client.circuit.ComponentRenderTypes;
import com.technicalitiesmc.lib.util.Utils;
import com.technicalitiesmc.scm.SuperCircuitMaker;
import com.technicalitiesmc.scm.block.CircuitBlock;
import com.technicalitiesmc.scm.client.model.CircuitModel;
import com.technicalitiesmc.scm.client.screen.TimingScreen;
import com.technicalitiesmc.scm.init.SCMBlocks;
import com.technicalitiesmc.scm.init.SCMComponents;
import com.technicalitiesmc.scm.init.SCMMenus;
import com.technicalitiesmc.scm.placement.ComponentPlacementHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegistryManager;

import java.util.*;

@Mod.EventBusSubscriber(modid = SuperCircuitMaker.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SCMClient {

    private static final Map<ComponentState, BlockState> MODEL_STATES = new IdentityHashMap<>();
    private static final Map<ComponentState, BakedModel> MODELS = new IdentityHashMap<>();

    private static int busyTimer = 0;
    private static boolean clicked = false, wasClicked = false;

    @SubscribeEvent
    public static void setup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MinecraftForge.EVENT_BUS.addListener(SCMClient::onClickInput);
            MinecraftForge.EVENT_BUS.addListener(SCMClient::onClientTick);
            registerScreens();
        });
    }

    private static void registerScreens() {
        MenuScreens.register(SCMMenus.DELAY.get(), TimingScreen::new);
        MenuScreens.register(SCMMenus.PULSAR.get(), TimingScreen::new);
    }

    @SubscribeEvent
    public static void onModelRegistryInit(ModelRegistryEvent event) {
        var models = ModelBakery.STATIC_DEFINITIONS;
        var immutable = models instanceof ImmutableMap;
        if (immutable) {
            models = new HashMap<>(models);
        }

        // This is how Minecraft itself does it for things that don't have a block state (item frames)
        var componentTypes = RegistryManager.ACTIVE.getRegistry(ComponentType.class);
        for (var componentType : componentTypes) {
            var name = componentType.getRegistryName();
            var modelName = new ResourceLocation(name.getNamespace(), "scmcomponent/" + name.getPath());
            var fakeBlock = new AirBlock(BlockBehaviour.Properties.copy(Blocks.AIR)) {
                @Override
                protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
                    componentType.getStateDefinition().getProperties().forEach(builder::add);
                }
            };
            var fakeDefinition = new StateDefinition.Builder<Block, BlockState>(fakeBlock)
                    .add(componentType.getStateDefinition().getProperties().toArray(Property[]::new))
                    .create(Block::defaultBlockState, BlockState::new);
            models.put(modelName, fakeDefinition);

            for (var state : componentType.getStateDefinition().getPossibleStates()) {
                var blockState = state.getValues().entrySet().stream()
                        .reduce(fakeDefinition.any(), SCMClient::setProperty, (a, b) -> b);
                MODEL_STATES.put(state, blockState);
            }
        }

        if (immutable) {
            ModelBakery.STATIC_DEFINITIONS = ImmutableMap.copyOf(models);
        }

        Minecraft.getInstance().getBlockColors().register(($, $$, $$$, idx) -> idx, SCMBlocks.CIRCUIT.get()); // Identity map tints
        ItemBlockRenderTypes.setRenderLayer(SCMBlocks.CIRCUIT.get(), ComponentRenderTypes.getRequestedTypes()::contains);
        ComponentRenderTypes.setRenderType(SCMComponents.TORCH_BOTTOM.get(), RenderType.cutout());
    }

    private static <T extends Comparable<T>> BlockState setProperty(BlockState state, Map.Entry<Property<?>, Comparable<?>> entry) {
        return state.setValue((Property<T>) entry.getKey(), (T) entry.getValue());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onModelBake(ModelBakeEvent event) {
        for (var state : SCMBlocks.CIRCUIT.get().getStateDefinition().getPossibleStates()) {
            var location = BlockModelShaper.stateToModelLocation(state);
            var model = event.getModelRegistry().get(location);
            var wrapped = new CircuitModel(model);
            event.getModelRegistry().put(location, wrapped);
        }

        var shaper = event.getModelManager().getBlockModelShaper();
        shaper.rebuildCache();

        MODELS.clear();
        MODEL_STATES.forEach((componentState, blockState) -> {
            var name = componentState.getComponentType().getRegistryName();
            var variant = BlockModelShaper.statePropertiesToString(componentState.getValues());
            var model = event.getModelRegistry().get(new ModelResourceLocation(
                    name.getNamespace(), "scmcomponent/" + name.getPath(), variant
            ));
            if (model == null) {
                model = event.getModelManager().getMissingModel();
            }
            MODELS.put(componentState, model);
        });
    }

    private static void onClickInput(InputEvent.ClickInputEvent event) {
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
            result = minecraft.player.isCrouching() ? InteractionResult.PASS : block.onClientUse(state, minecraft.level, hit.getBlockPos(), minecraft.player, event.getHand(), hit);
            if (result == InteractionResult.PASS) {
                result = ComponentPlacementHandler.onClientUse(state, minecraft.level, hit.getBlockPos(), minecraft.player, event.getHand(), hit);
            }
        } else if (event.isAttack()) {
            result = block.onClientClicked(state, minecraft.level, hit.getBlockPos(), minecraft.player, event.getHand(), hit);
        } else {
            return;
        }
        if (result == InteractionResult.PASS) {
            return;
        }
        if (result.consumesAction()) {
            clicked = true;
        }
        event.setCanceled(result.consumesAction());
        event.setSwingHand(result.shouldSwing());
        if (result.consumesAction() && result != InteractionResult.CONSUME_PARTIAL) {
            busyTimer = 5;
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        busyTimer--;

        if (!clicked && wasClicked) {
            var minecraft = Minecraft.getInstance();
            if (ComponentPlacementHandler.onClientStopUsing(minecraft.level, minecraft.player) || busyTimer > 0) {
                busyTimer = 2; // TODO: re-visit because ordering of this event and the click event is wrong
            }
        }
        wasClicked = clicked;
        clicked = false;
    }

    public static BlockState getBlockState(ComponentState state) {
        return MODEL_STATES.get(state);
    }

    public static BakedModel getModel(ComponentState state) {
        return MODELS.get(state);
    }

}
