package com.technicalitiesmc.scm.component.digital;

import com.mojang.math.Vector3f;
import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.scm.component.CircuitComponentBase;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

public class ButtonComponent extends CircuitComponentBase<ButtonComponent> {

    private static final AABB BOUNDS = new AABB(0, 0, 0, 1, 6 / 16D, 1);

    private static final Property<Boolean> PROP_ON = BooleanProperty.create("on");

    private static final int TIMEOUT = 10;

    private static final VecDirectionFlags OUTPUT_SIDES = VecDirectionFlags.horizontals().and(VecDirection.NEG_Y);

    private static final InterfaceLookup<ButtonComponent> INTERFACES = InterfaceLookup.<ButtonComponent>builder()
            .with(RedstoneSource.class, OUTPUT_SIDES, ButtonComponent::getRedstoneSource)
            .build();

    // Internal state
    private int counter;

    // External state
    private boolean state;

    public ButtonComponent(ComponentContext context) {
        super(SCMComponents.BUTTON, context, INTERFACES);
    }

    @Override
    public CircuitComponent copyRotated(ComponentContext context, Rotation rotation) {
        return new ButtonComponent(context);
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
    public void update(ComponentEventMap events, boolean tick) {
        if (tick) {
            counter--;
            if (counter == 0) {
                playSound(SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.2F, 0.6F);
                updateExternalState(true, () -> {
                    state = false;
                });
                sendEvent(CircuitEvent.REDSTONE, false, OUTPUT_SIDES);
            } else {
                scheduleTick(1);
            }
        }
    }

    @Override
    public InteractionResult use(Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
        counter = TIMEOUT;
        scheduleTick(1);
        if (!state) {
            playSound(SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.2F, 0.7F);
            updateExternalState(true, () -> {
                state = true;
            });
            sendEvent(CircuitEvent.REDSTONE, false, OUTPUT_SIDES);
        }
        return InteractionResult.sidedSuccess(false);
    }

    // Serialization

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag = super.save(tag);
        tag.putInt("counter", counter);
        tag.putBoolean("state", state);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        counter = tag.getInt("counter");
        state = tag.getBoolean("state");
    }

    // Helpers

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
        public InteractionResult use(ComponentState state, Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
            return InteractionResult.sidedSuccess(true);
        }

    }

}
