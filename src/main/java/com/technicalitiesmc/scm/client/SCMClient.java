package com.technicalitiesmc.scm.client;

import com.google.common.collect.ImmutableMap;
import com.technicalitiesmc.lib.circuit.component.ComponentState;
import com.technicalitiesmc.lib.circuit.component.ComponentType;
import com.technicalitiesmc.lib.client.circuit.ComponentRenderTypes;
import com.technicalitiesmc.scm.SuperCircuitMaker;
import com.technicalitiesmc.scm.client.model.CircuitModel;
import com.technicalitiesmc.scm.client.screen.TimingScreen;
import com.technicalitiesmc.scm.init.SCMBlocks;
import com.technicalitiesmc.scm.init.SCMComponents;
import com.technicalitiesmc.scm.init.SCMMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegistryManager;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = SuperCircuitMaker.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SCMClient {

    private static final Map<ComponentState, BlockState> MODEL_STATES = new IdentityHashMap<>();
    private static final Map<ComponentState, BakedModel> MODELS = new IdentityHashMap<>();

    @SubscribeEvent
    public static void setup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            registerScreens();
            SCMKeyMappings.register();
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

    public static BlockState getBlockState(ComponentState state) {
        return MODEL_STATES.get(state);
    }

    public static BakedModel getModel(ComponentState state) {
        return MODELS.get(state);
    }

}
