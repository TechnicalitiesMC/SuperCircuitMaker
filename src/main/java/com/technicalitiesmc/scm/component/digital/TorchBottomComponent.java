package com.technicalitiesmc.scm.component.digital;

import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSink;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMComponents;
import com.technicalitiesmc.scm.init.SCMItems;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

public class TorchBottomComponent extends DigitalComponentBase<TorchBottomComponent> {

    private static final Property<Boolean> PROP_ON = BooleanProperty.create("on");

//    private static final AABB BOUNDS = new AABB(3 / 16D, 0, 3 / 16D, 13 / 16D, 1 + 9 / 16D, 13 / 16D);
    private static final AABB BOUNDS = new AABB(3 / 16D, 0, 3 / 16D, 13 / 16D, 1, 13 / 16D);

    private static final VecDirectionFlags OUTPUT_SIDES = VecDirectionFlags.horizontals();

    private static final InterfaceLookup<TorchBottomComponent> INTERFACES = InterfaceLookup.<TorchBottomComponent>builder()
            .with(RedstoneSource.class, OUTPUT_SIDES, TorchBottomComponent::getRedstoneSource)
            .with(RedstoneSink.class, DigitalComponentBase.DEFAULT_INPUT_SIDES, RedstoneSink::instance)
            .build();

    // External state
    boolean state = true;

    public TorchBottomComponent(ComponentContext context) {
        super(SCMComponents.TORCH_BOTTOM, context, INTERFACES);
    }

    public TorchBottomComponent(ComponentContext context, boolean state) {
        this(context);
        this.state = state;
    }

    @Override
    public CircuitComponent copyRotated(ComponentContext context, Rotation rotation) {
        return new TorchBottomComponent(context, state);
    }

    @Override
    public ComponentState getState() {
        return super.getState().setValue(PROP_ON, state);
    }

    @Override
    public AABB getBoundingBox() {
        return BOUNDS;
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(Items.REDSTONE_TORCH);
    }

    @Override
    protected boolean needsSupport() {
        return true;
    }

    @Override
    protected boolean beforeCheckInputs(ComponentEventMap events, boolean tick) {
        // If we're running a scheduled tick, try to update the state and notify neighbors
        if (tick) {
            var newState = computeState(getInputs());
            if (newState != state) {
                updateExternalState(true, () -> {
                    state = newState;
                });
                sendEvent(CircuitEvent.REDSTONE, false, OUTPUT_SIDES);
                sendEventAt(VecDirection.POS_Y.getOffset(), CircuitEvent.REDSTONE, false, TorchTopComponent.OUTPUT_SIDES);
            }
        }
        return true;
    }

    @Override
    protected void onNewInputs(boolean tick, byte newInputs) {
        // If we need to toggle, schedule an update for next tick
        if (state != computeState(newInputs)) {
            scheduleTick(1);
        }
    }

    @Override
    public void harvest(ComponentHarvestContext context) {
        spawnDrops(context);
        // Clear both this and the component above
        removeComponentAt(Vec3i.ZERO, ComponentSlot.DEFAULT, true);
        removeComponentAt(VecDirection.POS_Y.getOffset(), ComponentSlot.DEFAULT, true);
    }

    // Serialization

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag = super.save(tag);
        tag.putBoolean("state", state);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        state = tag.getBoolean("state");
    }

    // Helpers

    private RedstoneSource getRedstoneSource() {
        return state ? RedstoneSource.fullWeak() : RedstoneSource.off();
    }

    private static boolean computeState(byte inputs) {
        return inputs == 0;
    }

    public static void createState(StateDefinition.Builder<ComponentType, ComponentState> builder) {
        builder.add(PROP_ON);
    }

    public static class Client extends ClientComponent {

        @Override
        public AABB getBoundingBox(ComponentState state) {
            return BOUNDS;
        }

        @Override
        public ItemStack getPickedItem(ComponentState state) {
            return new ItemStack(Items.REDSTONE_TORCH);
        }

    }

}
