package com.technicalitiesmc.scm.component.digital;

import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSink;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

public class LampComponent extends DigitalComponentBase<LampComponent> {

    private static final AABB BOUNDS = new AABB(0, 0, 0, 1, 6 / 16D, 1);

    private static final Property<Boolean> PROP_ON = BooleanProperty.create("on");

    private static final InterfaceLookup<LampComponent> INTERFACES = InterfaceLookup.<LampComponent>builder()
            .with(RedstoneSink.class, DigitalComponentBase.DEFAULT_INPUT_SIDES, RedstoneSink::instance)
            .build();

    // External state
    private boolean state = false;

    public LampComponent(ComponentContext context) {
        super(SCMComponents.LAMP, context, INTERFACES);
    }

    private LampComponent(ComponentContext context, boolean state) {
        this(context);
        this.state = state;
    }

    @Override
    public CircuitComponent copyRotated(ComponentContext context, Rotation rotation) {
        return new LampComponent(context, state);
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
    protected void onNewInputs(boolean tick, byte newInputs) {
        // If we're toggling, schedule an update
        var newState = newInputs != 0;
        if (state != newState) {
            updateExternalState(true, () -> {
                state = newState;
            });
        }
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

    public static void createState(StateDefinition.Builder<ComponentType, ComponentState> builder) {
        builder.add(PROP_ON);
    }

    public static class Client extends ClientComponent {

        @Override
        public AABB getBoundingBox(ComponentState state) {
            return BOUNDS;
        }

    }

}
