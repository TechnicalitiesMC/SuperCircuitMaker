package com.technicalitiesmc.scm.component.digital;

import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSink;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMComponents;
import com.technicalitiesmc.scm.init.SCMItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

public class RandomizerComponent extends DigitalComponentBase<RandomizerComponent> {

    private static final AABB BOUNDS = new AABB(0, 0, 0, 1, 6 / 16D, 1);

    private static final Property<Boolean> PROP_ON = BooleanProperty.create("on");

    private static final VecDirectionFlags OUTPUT_SIDES = VecDirectionFlags.horizontals();

    private static final InterfaceLookup<RandomizerComponent> INTERFACES = InterfaceLookup.<RandomizerComponent>builder()
            .with(RedstoneSource.class, OUTPUT_SIDES, RandomizerComponent::getRedstoneSource)
            .with(RedstoneSink.class, DigitalComponentBase.DEFAULT_INPUT_SIDES, RedstoneSink::instance)
            .build();

    // Internal state
    private boolean previousInput = false;

    // External state
    private boolean state = false;

    public RandomizerComponent(ComponentContext context) {
        super(SCMComponents.RANDOMIZER, context, INTERFACES);
    }

    private RandomizerComponent(ComponentContext context, boolean state) {
        this(context);
        this.state = state;
    }

    @Override
    public CircuitComponent copyRotated(ComponentContext context, Rotation rotation) {
        return new RandomizerComponent(context, state);
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
        return new ItemStack(SCMItems.RANDOMIZER.get());
    }

    @Override
    protected boolean beforeCheckInputs(ComponentEventMap events, boolean tick) {
        // If we're running a scheduled tick, randomize
        if (tick) {
            var input = getInputs() != 0;
            if (input && !previousInput) {
                randomize();
            }
            previousInput = input;
        }
        return true;
    }

    @Override
    protected void onNewInputs(boolean tick, byte newInputs) {
        // If we're generating a new output, schedule an update
        if ((getInputs() == 0) != (newInputs == 0)) {
            scheduleTick(1);
        }
    }

    // Serialization

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag = super.save(tag);
        tag.putBoolean("previous_input", previousInput);
        tag.putBoolean("state", state);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        previousInput = tag.getBoolean("previous_input");
        state = tag.getBoolean("state");
    }

    // Helpers

    private void randomize() {
        var newState = Math.random() > 0.5;
        if (newState == state) {
            return;
        }

        updateExternalState(true, () -> {
            state = newState;
        });
        sendEvent(CircuitEvent.REDSTONE, false, OUTPUT_SIDES);
    }

    private RedstoneSource getRedstoneSource() {
        return state ? RedstoneSource.fullWeak() : RedstoneSource.off();
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
            return new ItemStack(SCMItems.RANDOMIZER.get());
        }

    }

}
