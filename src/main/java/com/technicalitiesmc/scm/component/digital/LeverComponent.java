package com.technicalitiesmc.scm.component.digital;

import com.mojang.math.Vector3f;
import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSink;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMComponents;
import com.technicalitiesmc.scm.init.SCMItemTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

public class LeverComponent extends DigitalComponentBase<LeverComponent> {

    private static final AABB BOUNDS1 = new AABB(0, 0, 2 / 16D, 1, 6 / 16D, 14 / 16D);
    private static final AABB BOUNDS2 = new AABB(2 / 16D, 0, 0, 14 / 16D, 6 / 16D, 1);

    private static final Property<Boolean> PROP_ROTATED = BooleanProperty.create("rotated");
    private static final Property<Boolean> PROP_ON = BooleanProperty.create("on");

    private static final VecDirectionFlags INPUT_SIDES_1 = VecDirectionFlags.of(VecDirection.NEG_X, VecDirection.POS_X, VecDirection.NEG_Y);
    private static final VecDirectionFlags OUTPUT_SIDES_1 = VecDirectionFlags.of(VecDirection.NEG_Z, VecDirection.POS_Z);

    private static final VecDirectionFlags INPUT_SIDES_2 = VecDirectionFlags.of(VecDirection.NEG_Z, VecDirection.POS_Z, VecDirection.NEG_Y);
    private static final VecDirectionFlags OUTPUT_SIDES_2 = VecDirectionFlags.of(VecDirection.NEG_X, VecDirection.POS_X);

    private static final InterfaceLookup<LeverComponent> INTERFACES = InterfaceLookup.<LeverComponent>builder()
            .with(RedstoneSink.class, LeverComponent::getRedstoneSink)
            .with(RedstoneSource.class, LeverComponent::getRedstoneSource)
            .build();

    // Internal state
    private boolean previousInput = false;

    // External state
    private boolean rotation = false;
    private boolean state = true;

    public LeverComponent(ComponentContext context) {
        super(SCMComponents.LEVER, context, INTERFACES);
    }

    private LeverComponent(ComponentContext context, boolean rotation, boolean state) {
        this(context);
        this.rotation = rotation;
        this.state = state;
    }

    private int getRotation() {
        return (state ? 2 : 0) + (rotation ? 1 : 0);
    }

    public void setRotation(int rotation) {
        updateExternalState(true, () -> {
            this.rotation = rotation % 2 == 1;
            this.state = rotation >= 2;
        });
    }

    @Override
    public CircuitComponent copyRotated(ComponentContext context, Rotation rotation) {
        var rotate = rotation.ordinal() % 2 == 1;
        var flip = rotation.ordinal() >= 2;
        return new LeverComponent(context, rotate != this.rotation, state != flip);
    }

    @Override
    public ComponentState getState() {
        return super.getState()
                .setValue(PROP_ROTATED, rotation)
                .setValue(PROP_ON, state);
    }

    @Override
    public AABB getBoundingBox() {
        return rotation ? BOUNDS2 : BOUNDS1;
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(Items.LEVER);
    }

    @Override
    protected VecDirectionFlags getInputSides() {
        return choose(INPUT_SIDES_1, INPUT_SIDES_2);
    }

    private VecDirectionFlags getOutputSides() {
        return choose(OUTPUT_SIDES_1, OUTPUT_SIDES_2);
    }

    @Override
    protected boolean beforeCheckInputs(ComponentEventMap events, boolean tick) {
        // If we're running a scheduled tick, toggle
        if (tick) {
            var input = getInputs() != 0;
            if (input && !previousInput) {
                toggle();
            }
            previousInput = input;
        }
        return true;
    }

    @Override
    protected void onNewInputs(boolean tick, byte newInputs) {
        // If we're toggling, schedule an update
        if ((getInputs() == 0) != (newInputs == 0)) {
            scheduleTick(1);
        }
    }

    @Override
    public InteractionResult use(Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
        if (player.getItemInHand(hand).is(SCMItemTags.WRENCHES)) {
            rotate();
        } else {
            toggle();
        }
        return InteractionResult.sidedSuccess(false);
    }

    // Serialization

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag = super.save(tag);
        tag.putBoolean("previous_input", previousInput);
        tag.putBoolean("state", state);
        tag.putBoolean("rotation", rotation);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        previousInput = tag.getBoolean("previous_input");
        state = tag.getBoolean("state");
        rotation = tag.getBoolean("rotation");
    }

    // Helpers

    private void toggle() {
        var newState = !state;

        var pitch = newState ? 0.7F : 0.6F;
        playSound(SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.2F, pitch);

        updateExternalState(true, () -> {
            state = newState;
        });
        sendEvent(CircuitEvent.REDSTONE, getOutputSides());
    }

    public void rotate() {
        setRotation((getRotation() + 1) % 4);
        sendEvent(CircuitEvent.REDSTONE, VecDirectionFlags.horizontals());
        sendEvent(CircuitEvent.NEIGHBOR_CHANGED, VecDirectionFlags.horizontals());
    }

    private <T> T choose(T first, T second) {
        return rotation ? first : second;
    }

    private RedstoneSink getRedstoneSink(VecDirection side) {
        if (!getInputSides().has(side)) {
            return null;
        }
        return RedstoneSink.instance();
    }

    private RedstoneSource getRedstoneSource(VecDirection side) {
        if (!getOutputSides().has(side)) {
            return null;
        }
        return side.isPositive() == state ? RedstoneSource.fullWeak() : RedstoneSource.off();
    }

    public static void createState(StateDefinition.Builder<ComponentType, ComponentState> builder) {
        builder.add(PROP_ROTATED, PROP_ON);
    }

    public static class Client extends ClientComponent {

        @Override
        public AABB getBoundingBox(ComponentState state) {
            return state.getValue(PROP_ROTATED) ? BOUNDS2 : BOUNDS1;
        }

        @Override
        public ItemStack getPickedItem(ComponentState state) {
            return new ItemStack(Items.LEVER);
        }

        @Override
        public InteractionResult use(ComponentState state, Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
            return InteractionResult.sidedSuccess(true);
        }

    }

}
