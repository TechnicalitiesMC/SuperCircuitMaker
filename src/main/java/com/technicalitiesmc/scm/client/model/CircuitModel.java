package com.technicalitiesmc.scm.client.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.mojang.math.Matrix4f;
import com.mojang.math.Transformation;
import com.technicalitiesmc.lib.circuit.component.ComponentState;
import com.technicalitiesmc.lib.client.circuit.ComponentRenderTypes;
import com.technicalitiesmc.scm.circuit.CircuitAdjacency;
import com.technicalitiesmc.scm.client.SCMClient;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.model.QuadTransformer;
import net.minecraftforge.client.model.data.IDynamicBakedModel;
import net.minecraftforge.client.model.data.IModelData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

public class CircuitModel implements IDynamicBakedModel {

    private final BakedModel parent;

    public CircuitModel(BakedModel parent) {
        this.parent = parent;
    }

    private List<BakedQuad> getParentQuads(@Nullable BlockState state, @Nullable Direction side, @Nonnull Random rand, @Nonnull IModelData extraData) {
        var renderType = MinecraftForgeClient.getRenderType();
        if (renderType != null && renderType != RenderType.solid()) {
            return Collections.emptyList();
        }

        if (side != null) {
            return parent.getQuads(state, side, rand);
        }

        var data = extraData.getData(CircuitModelData.PROPERTY);
        CircuitAdjacency[] adjacency;
        if (data != null) {
            adjacency = data.getAdjacency();
        } else {
            adjacency = new CircuitAdjacency[] {
                    CircuitAdjacency.NONE, CircuitAdjacency.NONE,
                    CircuitAdjacency.NONE, CircuitAdjacency.NONE
            };
        }

        var quads = ImmutableList.<BakedQuad>builder();

        for (var quad : parent.getQuads(state, side, rand)) {
            if (!quad.isTinted()) {
                quads.add(quad);
                continue;
            }
            var corner = quad.getTintIndex() % 4;
            var adj = quad.getTintIndex() / 4;
            if (adjacency[corner] != CircuitAdjacency.VALUES[adj]) {
                continue;
            }
            // Make sure to remove the tint
            quads.add(new BakedQuad(quad.getVertices(), -1, quad.getDirection(), quad.getSprite(), quad.isShade()));
        }

        return quads.build();
    }

    @Nonnull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @Nonnull Random rand, @Nonnull IModelData extraData) {
        if (side != null) {
            return getParentQuads(state, side, rand, extraData);
        }

        var data = extraData.getData(CircuitModelData.PROPERTY);
        if (data == null || data.getStates().isEmpty()) {
            return getParentQuads(state, side, rand, extraData);
        }

        var quads = ImmutableList.<BakedQuad>builder();
        quads.addAll(getParentQuads(state, side, rand, extraData));

        var renderType = MinecraftForgeClient.getRenderType();
        processComponentGeometry(data.getStates(), renderType, rand, quads::add);

        return quads.build();
    }

    public static void processComponentGeometry(Multimap<Vec3i, ComponentState> states, RenderType renderType, Random rand, Consumer<BakedQuad> consumer) {
        states.forEach((pos, componentState) -> {
            if (!ComponentRenderTypes.shouldRender(componentState.getComponentType(), renderType)) {
                return;
            }

            var matrix = Matrix4f.createScaleMatrix(1 / 8f, 1 / 8f, 1 / 8f);
            matrix.multiply(Matrix4f.createTranslateMatrix(pos.getX() + 0.5f, pos.getY() + 1, pos.getZ() + 0.5f));
            var transformer = new QuadTransformer(new Transformation(matrix));

            var rawState = componentState.getRawState();
            var blockState = SCMClient.getBlockState(rawState);
            var model = SCMClient.getModel(rawState);
            var transformed = transformer.processMany(model.getQuads(blockState, null, rand));
            for (var quad : transformed) {
                if (!quad.isTinted()) {
                    consumer.accept(quad);
                    continue;
                }
                var tint = componentState.getTint(quad.getTintIndex());
                consumer.accept(new BakedQuad(quad.getVertices(), tint, quad.getDirection(), quad.getSprite(), quad.isShade()));
            }
        });
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return parent.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return parent.getParticleIcon();
    }

    @Override
    public ItemOverrides getOverrides() {
        return parent.getOverrides();
    }

}
