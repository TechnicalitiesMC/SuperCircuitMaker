package com.technicalitiesmc.scm.circuit.server;

import com.google.common.base.Suppliers;
import com.mojang.math.Vector3f;
import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.scm.circuit.CircuitHelper;
import com.technicalitiesmc.scm.circuit.util.ComponentPos;
import com.technicalitiesmc.scm.component.misc.LevelIOComponent;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ComponentInstance {

    private static final Supplier<IForgeRegistry<ComponentType>> TYPE_REGISTRY = Suppliers.memoize(() -> {
        return RegistryManager.ACTIVE.getRegistry(ComponentType.class);
    });

    private final CircuitTile tile;
    private final ComponentPos pos;
    private final CircuitComponent component;
    private final List<Runnable> externalStateUpdates = new ArrayList<>();
    private Vec3i absolutePos;
    private ComponentState state;
    private VoxelShape shape;

    public ComponentInstance(CircuitTile tile, ComponentPos pos, ComponentType.Factory factory) {
        this.tile = tile;
        this.pos = pos;
        this.component = factory.create(new Context());
        updatePosition();
        updateState();
    }

    public ComponentType getType() {
        return component.getType();
    }

    public ComponentSlot getSlot() {
        return component.getType().getSlot();
    }

    public ComponentSlot[] getAllSlots() {
        return component.getType().getAllSlots();
    }

    public ComponentState getState() {
        return state;
    }

    public VoxelShape getShape() {
        return shape;
    }

    public Vec3i getPosition() {
        return absolutePos;
    }

    public boolean isLevelIOComponent() {
        return component instanceof LevelIOComponent;
    }

    private boolean updateState() {
        var newState = component.getState();
        var changed = !newState.equals(state);
        state = newState;
        shape = CircuitHelper.createShape(component.getBoundingBox(), pos, getSlot());
        return changed;
    }

    public void updatePosition() {
        this.absolutePos = tile != null ? tile.getPosition().pack(pos) : null;
    }

    public void onAdded() {
        component.onAdded();
    }

    public void beforeRemove() {
        component.beforeRemove();
    }

    public void update(ComponentEventMap events, boolean tick) {
        component.update(events, tick);
    }

    public void updateSequential() {
        component.updateSequential();
    }

    public void updateExternalState() {
        externalStateUpdates.forEach(Runnable::run);
        externalStateUpdates.clear();
    }

    public InteractionResult use(Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
        return component.use(player, hand, sideHit, hit);
    }

    public void spawnDrops(ComponentHarvestContext context) {
        component.spawnDrops(context);
    }

    public void harvest(ComponentHarvestContext context) {
        component.harvest(context);
    }

    public void receiveEvent(VecDirection side, CircuitEvent event, ComponentEventMap.Builder builder) {
        component.receiveEvent(side, event, builder);
    }

    public int getStrongOutput(VecDirection side) {
        var source = component.getInterface(side, RedstoneSource.class);
        return source != null ? source.getStrongOutput() : 0;
    }

    public ItemStack getPickedItem() {
        return component.getPickedItem();
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putString("type", getType().getRegistryName().toString());
        tag.put("component", component.save(new CompoundTag()));
        return tag;
    }

    @Nullable
    public static ComponentInstance load(CircuitTile tile, ComponentPos pos, CompoundTag tag) {
        var typeName = new ResourceLocation(tag.getString("type"));
        var type = TYPE_REGISTRY.get().getValue(typeName);
        if (type == null) {
            return null;
        }
        var data = tag.getCompound("component");
        return new ComponentInstance(tile, pos, ctx -> {
            var component = type.create(ctx);
            component.load(data);
            return component;
        });
    }

    private class Context implements ComponentContext {

        private Circuit getCircuit() {
            return tile.getCircuit();
        }

        @Override
        public boolean isValidPosition(Vec3i offset) {
            var pos = absolutePos.offset(offset);
            return getCircuit().isValidPosition(pos);
        }

        @Override
        public boolean isTopSolid(Vec3i offset) {
            var pos = absolutePos.offset(offset);
            if (pos.getY() == -1) {
                return true;
            }
            var ci = getCircuit().get(pos, ComponentSlot.SUPPORT);
            return ci != null && ci.component.isTopSolid();
        }

        @Override
        public CircuitComponent getComponentAt(Vec3i offset, ComponentSlot slot) {
            var pos = absolutePos.offset(offset);
            var ci = getCircuit().get(pos, slot);
            return ci != null ? ci.component : null;
        }

        @Override
        public void removeComponentAt(Vec3i offset, ComponentSlot slot, boolean notify) {
            var pos = absolutePos.offset(offset);
            var circuit = getCircuit();
            if (circuit.tryRemove(pos, slot) && notify) {
                circuit.sendEvent(pos, slot, CircuitEvent.NEIGHBOR_CHANGED, VecDirectionFlags.all());
            }
        }

        @Override
        public void scheduleRemoval() {
            getCircuit().scheduleRemoval(absolutePos, getSlot());
        }

        @Override
        public void sendEventAt(Vec3i offset, ComponentSlot slot, CircuitEvent event, VecDirectionFlags directions) {
            var pos = absolutePos.offset(offset);
            getCircuit().sendEvent(pos, slot, event, directions);
        }

        @Override
        public void updateExternalState(boolean reRender, Runnable action) {
            if (component == null || !getCircuit().isTicking()) {
                action.run();
                if (component != null && reRender && updateState()) {
                    tile.markForUpdate(pos, getSlot());
                }
                return;
            }

            getCircuit().enqueueStateUpdate(absolutePos, getSlot());

            externalStateUpdates.add(action);
            if (reRender) {
                externalStateUpdates.add(() -> {
                    if (updateState()) {
                        tile.markForUpdate(pos, getSlot());
                    }
                });
            }
        }

        @Override
        public void scheduleSequential() {
            getCircuit().enqueueSequentialUpdate(absolutePos, getSlot());
        }

        @Override
        public void scheduleTick(int delay) {
            getCircuit().enqueueTick(absolutePos, getSlot(), delay);
        }

        @Override
        public void playSound(SoundEvent sound, SoundSource source, float volume, float pitch) {
            getCircuit().playSound(absolutePos, sound, source, volume, pitch);
        }

    }

}
