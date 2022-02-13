package com.technicalitiesmc.scm.component.digital;

import com.mojang.math.Vector3f;
import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSink;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.util.Utils;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMComponents;
import com.technicalitiesmc.scm.init.SCMItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

public class LampComponent extends DigitalComponentBase<LampComponent> {

    private static final AABB BOUNDS = new AABB(0, 0, 0, 1, 6 / 16D, 1);

    private static final Property<Boolean> PROP_ON = BooleanProperty.create("on");
    private static final Property<DyeColor> PROP_COLOR = EnumProperty.create("color", DyeColor.class);

    private static final InterfaceLookup<LampComponent> INTERFACES = InterfaceLookup.<LampComponent>builder()
            .with(RedstoneSink.class, DigitalComponentBase.DEFAULT_INPUT_SIDES, RedstoneSink::instance)
            .build();

    // External state
    private boolean state = false;
    private DyeColor color = DyeColor.WHITE;

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
        return super.getState().setValue(PROP_ON, state).setValue(PROP_COLOR, color);
    }

    @Override
    public AABB getBoundingBox() {
        return BOUNDS;
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(SCMItems.LAMP.get());
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

    @Override
    public InteractionResult use(Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
        // If clicked with a dye, paint
        var stack = player.getItemInHand(hand);
        var dyeColor = !stack.isEmpty() ? Utils.getDyeColor(stack) : null;
        if (dyeColor != null) {
            updateExternalState(true, () -> {
                color = dyeColor;
            });
            return InteractionResult.sidedSuccess(player.level.isClientSide());
        }
        // Fall back to parent handler
        return super.use(player, hand, sideHit, hit);
    }

    // Serialization

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag = super.save(tag);
        tag.putBoolean("state", state);
        tag.putInt("color", color.getId());
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        state = tag.getBoolean("state");
        color = DyeColor.byId(tag.getInt("color"));
    }

    // Helpers

    public static void createState(StateDefinition.Builder<ComponentType, ComponentState> builder) {
        builder.add(PROP_ON, PROP_COLOR);
    }

    public static class Client extends ClientComponent {

        @Override
        public AABB getBoundingBox(ComponentState state) {
            return BOUNDS;
        }

        @Override
        public ItemStack getPickedItem(ComponentState state) {
            return new ItemStack(SCMItems.LAMP.get());
        }

        @Override
        public InteractionResult use(ComponentState state, Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
            var stack = player.getItemInHand(hand);
            if (!stack.isEmpty() && Utils.getDyeColor(stack) != null) {
                return InteractionResult.sidedSuccess(true);
            }
            return super.use(state, player, hand, sideHit, hit);
        }

        @Override
        public int getTint(ComponentState state, int tintIndex) {
            if (tintIndex == 0) {
                return state.getValue(PROP_COLOR).getFireworkColor();
            }
            return super.getTint(state, tintIndex);
        }

    }

}
