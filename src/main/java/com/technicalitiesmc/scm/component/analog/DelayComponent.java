package com.technicalitiesmc.scm.component.analog;

import com.mojang.math.Vector3f;
import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSink;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.lib.util.value.Reference;
import com.technicalitiesmc.scm.SuperCircuitMaker;
import com.technicalitiesmc.scm.component.CircuitComponentBase;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMComponents;
import com.technicalitiesmc.scm.init.SCMItems;
import com.technicalitiesmc.scm.menu.DelayMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;

public class DelayComponent extends CircuitComponentBase<DelayComponent> {

    public static final VecDirectionFlags INPUT_SIDES = VecDirectionFlags.horizontals().and(VecDirection.NEG_Y);
    private static final VecDirectionFlags OUTPUT_SIDES = VecDirectionFlags.horizontals();

    private static final AABB BOUNDS = new AABB(0, 0, 0, 1, 6 / 16D, 1);

    private static final TranslatableComponent MENU_TITLE = new TranslatableComponent("container." + SuperCircuitMaker.MODID + ".delay");

    private static final int DEFAULT_DELAY = 20;

    private static final Property<Boolean> PROP_ON = BooleanProperty.create("on");

    private static final InterfaceLookup<DelayComponent> INTERFACES = InterfaceLookup.<DelayComponent>builder()
            .with(RedstoneSource.class, OUTPUT_SIDES, DelayComponent::getRedstoneSource)
            .with(RedstoneSink.class, INPUT_SIDES, RedstoneSink::instance)
            .build();

    // Internal state
    private int[] values = new int[DEFAULT_DELAY + 1];
    private int next = 0;

    // External state
    private int delay = DEFAULT_DELAY;
    private int counter = 0;

    public DelayComponent(ComponentContext context) {
        super(SCMComponents.DELAY, context, INTERFACES);
    }

    private DelayComponent(ComponentContext context, int[] values, int delay, int counter) {
        this(context);
        this.values = Arrays.copyOf(values, values.length);
        this.delay = delay;
        this.counter = counter;
    }

    private int getNextIndex() {
        return (counter + 1) % (delay + 1);
    }

    private void setDelay(int ticks) {
        var current = values[getNextIndex()];
        updateExternalState(false, () -> {
            delay = ticks;
            values = new int[ticks + 1];
            values[1] = current; // Persist current state
            counter = 0;
        });
    }

    @Override
    public CircuitComponent copyRotated(ComponentContext context, Rotation rotation) {
        return new DelayComponent(context, values, delay, counter);
    }

    @Override
    public ComponentState getState() {
        return super.getState().setValue(PROP_ON, values[getNextIndex()] != 0);
    }

    @Override
    public AABB getBoundingBox() {
        return BOUNDS;
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(SCMItems.DELAY.get());
    }

    @Override
    public void onAdded() {
        super.onAdded();
        scheduleTick(1);
    }

    @Override
    public void update(ComponentEventMap events, boolean tick) {
        // If we're running a scheduled tick, capture value
        if (tick) {
            values[counter] = getInput();
            var nextIndex = getNextIndex();
            updateExternalState(true, () -> {
                counter = nextIndex;
            });
            sendEvent(CircuitEvent.REDSTONE, OUTPUT_SIDES);
            scheduleTick(1);
        }
    }

    private int getInput() {
        return INPUT_SIDES.stream(VecDirection.class)
                .mapToInt(this::getWeakInput)
                .max().orElse(0);
    }

    @Override
    public InteractionResult use(Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
        player.openMenu(new SimpleMenuProvider((id, playerInv, $) ->
                new DelayMenu(id, playerInv, p -> true, Reference.of(() -> delay, this::setDelay)),
                MENU_TITLE
        ));
        return InteractionResult.sidedSuccess(false);
    }

    // Serialization

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag = super.save(tag);
        tag.putIntArray("values", Arrays.copyOf(values, values.length));
        tag.putInt("next", next);
        tag.putInt("delay", delay);
        tag.putInt("counter", counter);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        values = tag.getIntArray("values");
        next = tag.getInt("next");
        delay = tag.getInt("delay");
        counter = tag.getInt("counter");
    }

    // Helpers

    private RedstoneSource getRedstoneSource() {
        return RedstoneSource.of(0, values[getNextIndex()]);
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
            return new ItemStack(SCMItems.DELAY.get());
        }

        @Override
        public InteractionResult use(ComponentState state, Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
            return InteractionResult.sidedSuccess(true);
        }

    }

}
