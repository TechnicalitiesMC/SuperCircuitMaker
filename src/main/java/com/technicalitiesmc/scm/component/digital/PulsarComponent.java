package com.technicalitiesmc.scm.component.digital;

import com.mojang.math.Vector3f;
import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSink;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.lib.util.value.Reference;
import com.technicalitiesmc.scm.SuperCircuitMaker;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMComponents;
import com.technicalitiesmc.scm.init.SCMItems;
import com.technicalitiesmc.scm.menu.PulsarMenu;
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

public class PulsarComponent extends DigitalComponentBase<PulsarComponent> {

    private static final AABB BOUNDS = new AABB(0, 0, 0, 1, 6 / 16D, 1);

    private static final TranslatableComponent MENU_TITLE = new TranslatableComponent("container." + SuperCircuitMaker.MODID + ".pulsar");

    private static final int DEFAULT_DELAY = 20;

    private static final Property<Boolean> PROP_ON = BooleanProperty.create("on");

    private static final VecDirectionFlags OUTPUT_SIDES = VecDirectionFlags.horizontals();

    private static final InterfaceLookup<PulsarComponent> INTERFACES = InterfaceLookup.<PulsarComponent>builder()
            .with(RedstoneSource.class, OUTPUT_SIDES, PulsarComponent::getRedstoneSource)
            .with(RedstoneSink.class, DigitalComponentBase.DEFAULT_INPUT_SIDES, RedstoneSink::instance)
            .build();

    // Internal state
    private boolean input;

    // External state
    private int delay = DEFAULT_DELAY;
    private int counter = 0;

    public PulsarComponent(ComponentContext context) {
        super(SCMComponents.PULSAR, context, INTERFACES);
    }

    private PulsarComponent(ComponentContext context, int delay, int counter) {
        this(context);
        this.delay = delay;
        this.counter = counter;
    }

    private void setDelay(int ticks) {
        updateExternalState(false, () -> {
            delay = ticks;
            counter = 0;
        });
    }

    @Override
    public CircuitComponent copyRotated(ComponentContext context, Rotation rotation) {
        return new PulsarComponent(context, delay, counter);
    }

    @Override
    public ComponentState getState() {
        return super.getState().setValue(PROP_ON, counter == delay - 1);
    }

    @Override
    public AABB getBoundingBox() {
        return BOUNDS;
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(SCMItems.PULSAR.get());
    }

    @Override
    public void onAdded() {
        super.onAdded();
        scheduleTick(1);
    }

    @Override
    protected boolean beforeCheckInputs(ComponentEventMap events, boolean tick) {
        // If we're running a scheduled tick, capture value and advance
        if (tick) {
            var next = (counter + 1) % delay;
            var willBeOn = next == delay - 1;
            var notify = next == 0 || willBeOn;
            updateExternalState(true, () -> {
                counter = next;
            });
            if (notify) {
                sendEvent(CircuitEvent.REDSTONE, OUTPUT_SIDES);
            }
            if (!input || willBeOn) {
                scheduleTick(1);
            }
        }
        return true;
    }

    @Override
    protected void onNewInputs(boolean tick, byte newInputs) {
        if ((getInputs() == 0) != (newInputs == 0)) {
            input = newInputs != 0;
            if (!input) {
                counter = 0;
                scheduleTick(1);
            }
        }
    }

    @Override
    public InteractionResult use(Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
        player.openMenu(new SimpleMenuProvider((id, playerInv, $) ->
                new PulsarMenu(id, playerInv, p -> true, Reference.of(() -> delay, this::setDelay)),
                MENU_TITLE
        ));
        return InteractionResult.sidedSuccess(false);
    }

    // Serialization

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag = super.save(tag);
        tag.putBoolean("input", input);
        tag.putInt("delay", delay);
        tag.putInt("counter", counter);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        input = tag.getBoolean("input");
        delay = tag.getInt("delay");
        counter = tag.getInt("counter");
    }

    // Helpers

    private RedstoneSource getRedstoneSource() {
        return counter == delay - 1 ? RedstoneSource.fullWeak() : RedstoneSource.off();
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
            return new ItemStack(SCMItems.PULSAR.get());
        }

        @Override
        public InteractionResult use(ComponentState state, Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
            return InteractionResult.sidedSuccess(true);
        }

    }

}
