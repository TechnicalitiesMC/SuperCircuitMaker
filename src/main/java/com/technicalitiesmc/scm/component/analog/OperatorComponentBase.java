package com.technicalitiesmc.scm.component.analog;

import com.mojang.math.Vector3f;
import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSink;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.scm.component.CircuitComponentBase;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMItemTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.RegistryObject;

public abstract class OperatorComponentBase extends CircuitComponentBase<OperatorComponentBase> {

    public static final VecDirectionFlags INPUT_SIDES = VecDirectionFlags.horizontals().and(VecDirection.NEG_Y);
    private static final VecDirectionFlags OUTPUT_SIDES = VecDirectionFlags.horizontals();

    private static final AABB BOUNDS = new AABB(0, 0, 0, 1, 4 / 16D, 1);

    private static final Property<VecDirection> PROP_DIRECTION = EnumProperty.create("direction", VecDirection.class,
            VecDirection.NEG_X, VecDirection.POS_X, VecDirection.NEG_Z, VecDirection.POS_Z);

    private static final InterfaceLookup<OperatorComponentBase> INTERFACES = InterfaceLookup.<OperatorComponentBase>builder()
            .with(RedstoneSource.class, OUTPUT_SIDES, OperatorComponentBase::getRedstoneSource)
            .with(RedstoneSink.class, INPUT_SIDES, RedstoneSink::instance)
            .build();

    // External state
    private VecDirection direction = VecDirection.NEG_X;
    private int output, extra;

    public OperatorComponentBase(RegistryObject<ComponentType> type, ComponentContext context) {
        super(type, context, INTERFACES);
    }

    protected abstract int[] operate(int main, int secondary);

    @Override
    public ComponentState getState() {
        return super.getState().setValue(PROP_DIRECTION, direction);
    }

    @Override
    public AABB getBoundingBox() {
        return BOUNDS;
    }

    public int getExtraOutput() {
        return extra;
    }

    @Override
    public void onAdded() {
        super.onAdded();
        scheduleTick(1);
    }

    @Override
    public void update(ComponentEventMap events, boolean tick) {
        // If the support component below is gone, remove this and skip the update
        if (!ensureSupported(events)) {
            return;
        }

        // If we're running a scheduled tick, capture value
        if (tick) {
            var newValues = operate(getMainInput(), getSecondaryInput());
            updateExternalState(false, () -> {
                output = newValues[0];
                extra = newValues[1];
            });
            sendEvent(CircuitEvent.REDSTONE, OUTPUT_SIDES);
        }

        // If we get a new signal, enqueue a tick
        if (!events.findAny(CircuitEvent.NEIGHBOR_CHANGED, CircuitEvent.REDSTONE).onlyIn(INPUT_SIDES).isEmpty()) {
            scheduleTick(1);
        }
    }

    @Override
    public InteractionResult use(Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
        var stack = player.getItemInHand(hand);
        if (!stack.isEmpty() && stack.is(SCMItemTags.ROTATES_COMPONENTS)) {
            var newDirection =direction.applyY(Rotation.CLOCKWISE_90);
            updateExternalState(true, () -> {
                direction = newDirection;
            });
            sendEvent(CircuitEvent.NEIGHBOR_CHANGED, OUTPUT_SIDES);
            scheduleTick(1);
            return InteractionResult.sidedSuccess(false);
        }
        return super.use(player, hand, sideHit, hit);
    }

    // Serialization

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("direction", direction.ordinal());
        tag.putInt("output", output);
        tag.putInt("extra", extra);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        direction = VecDirection.VALUES[tag.getInt("direction")];
        output = tag.getInt("output");
        extra = tag.getInt("extra");
    }

    // Helpers

    private int getMainInput() {
        return getStrongInput(direction);
    }

    private int getSecondaryInput() {
        return INPUT_SIDES.except(direction).stream(VecDirection.class)
                .mapToInt(this::getStrongInput)
                .max().orElse(0);
    }

    private RedstoneSource getRedstoneSource(VecDirection side) {
        if (side == direction) {
            return null;
        }
        return RedstoneSource.of(0, output);
    }

    public static void createState(StateDefinition.Builder<ComponentType, ComponentState> builder) {
        builder.add(PROP_DIRECTION);
    }

    public static class Client extends ClientComponent {

        private final RegistryObject<Item> item;

        public Client(RegistryObject<Item> item) {
            this.item = item;
        }

        @Override
        public AABB getBoundingBox(ComponentState state) {
            return BOUNDS;
        }

        @Override
        public ItemStack getPickedItem(ComponentState state) {
            return new ItemStack(item.get());
        }

        @Override
        public InteractionResult use(ComponentState state, Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
            var stack = player.getItemInHand(hand);
            if (!stack.isEmpty() && stack.is(SCMItemTags.ROTATES_COMPONENTS)) {
                return InteractionResult.sidedSuccess(true);
            }
            return super.use(state, player, hand, sideHit, hit);
        }

    }

}
