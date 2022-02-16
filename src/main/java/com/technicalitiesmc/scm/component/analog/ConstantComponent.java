package com.technicalitiesmc.scm.component.analog;

import com.mojang.math.Vector3f;
import com.technicalitiesmc.lib.circuit.component.CircuitEvent;
import com.technicalitiesmc.lib.circuit.component.ClientComponent;
import com.technicalitiesmc.lib.circuit.component.ComponentContext;
import com.technicalitiesmc.lib.circuit.component.ComponentState;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.lib.util.value.Reference;
import com.technicalitiesmc.scm.SuperCircuitMaker;
import com.technicalitiesmc.scm.component.CircuitComponentBase;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMComponents;
import com.technicalitiesmc.scm.init.SCMItems;
import com.technicalitiesmc.scm.menu.ConstantMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public class ConstantComponent extends CircuitComponentBase<ConstantComponent> {

    private static final VecDirectionFlags OUTPUT_SIDES = VecDirectionFlags.horizontals();

    private static final AABB BOUNDS = new AABB(0, 0, 0, 1, 4 / 16D, 1);

    private static final TranslatableComponent MENU_TITLE = new TranslatableComponent("container." + SuperCircuitMaker.MODID + ".constant");

    private static final InterfaceLookup<ConstantComponent> INTERFACES = InterfaceLookup.<ConstantComponent>builder()
            .with(RedstoneSource.class, OUTPUT_SIDES, ConstantComponent::getRedstoneSource)
            .build();

    // External state
    private int output;

    public ConstantComponent(ComponentContext context) {
        super(SCMComponents.CONSTANT, context, INTERFACES);
    }

    @Override
    public AABB getBoundingBox() {
        return BOUNDS;
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(SCMItems.CONSTANT.get());
    }

    @Override
    public InteractionResult use(Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
        player.openMenu(new SimpleMenuProvider((id, playerInv, $) ->
                new ConstantMenu(id, playerInv, p -> true, Reference.of(() -> output, this::setOutput)),
                MENU_TITLE
        ));
        return InteractionResult.sidedSuccess(false);
    }

    // Serialization

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("output", output);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        output = tag.getInt("output");
    }

    // Helpers

    private void setOutput(int output) {
        updateExternalState(false, () -> {
            this.output = output;
        });
        sendEvent(CircuitEvent.REDSTONE, OUTPUT_SIDES);
    }

    private RedstoneSource getRedstoneSource(VecDirection side) {
        return RedstoneSource.of(0, output);
    }

    public static class Client extends ClientComponent {

        @Override
        public AABB getBoundingBox(ComponentState state) {
            return BOUNDS;
        }

        @Override
        public ItemStack getPickedItem(ComponentState state) {
            return new ItemStack(SCMItems.CONSTANT.get());
        }

        @Override
        public InteractionResult use(ComponentState state, Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
            return InteractionResult.sidedSuccess(true);
        }

    }

}
