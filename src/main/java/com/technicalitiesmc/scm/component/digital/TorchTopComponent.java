package com.technicalitiesmc.scm.component.digital;

import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.scm.component.CircuitComponentBase;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMComponents;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

public class TorchTopComponent extends CircuitComponentBase<TorchTopComponent> {

    private static final AABB BOUNDS = new AABB(3 / 16D, 0, 3 / 16D, 13 / 16D, 9 / 16D, 13 / 16D);

    static final VecDirectionFlags OUTPUT_SIDES = VecDirectionFlags.horizontals().and(VecDirection.POS_Y);

    private static final InterfaceLookup<TorchTopComponent> INTERFACES = InterfaceLookup.<TorchTopComponent>builder()
            .with(RedstoneSource.class, OUTPUT_SIDES, TorchTopComponent::getRedstoneSource)
            .build();

    public TorchTopComponent(ComponentContext context) {
        super(SCMComponents.TORCH_TOP, context, INTERFACES);
    }

    @Override
    public CircuitComponent copyRotated(ComponentContext context, Rotation rotation) {
        return new TorchTopComponent(context);
    }

    @Override
    public AABB getBoundingBox() {
        return BOUNDS;
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(Items.REDSTONE_TORCH);
    }

    private RedstoneSource getRedstoneSource(VecDirection direction) {
        var below = getNeighbor(VecDirection.NEG_Y, ComponentSlot.DEFAULT);
        if (below instanceof TorchBottomComponent bottom) {
            return bottom.state ? RedstoneSource.full(direction == VecDirection.POS_Y) : RedstoneSource.off();
        }
        return null;
    }

    @Override
    public void harvest(ComponentHarvestContext context) {
        var below = getNeighbor(VecDirection.NEG_Y, ComponentSlot.DEFAULT);
        if (below instanceof TorchBottomComponent) {
            below.spawnDrops(context);
        }
        // Clear both this and the component below
        removeComponentAt(Vec3i.ZERO, ComponentSlot.DEFAULT, true);
        removeComponentAt(VecDirection.NEG_Y.getOffset(), ComponentSlot.DEFAULT, true);
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
