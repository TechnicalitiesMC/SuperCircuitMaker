package com.technicalitiesmc.scm.component.analog;

import com.technicalitiesmc.lib.circuit.component.ComponentContext;
import com.technicalitiesmc.scm.init.SCMComponents;
import com.technicalitiesmc.scm.init.SCMItems;
import net.minecraft.world.item.ItemStack;

public class DividerComponent extends OperatorComponentBase {

    public DividerComponent(ComponentContext context) {
        super(SCMComponents.DIVIDER, context);
    }

    @Override
    protected int[] operate(int main, int secondary) {
        if (secondary == 0) {
            return new int[2];
        }
        return new int[] {
            main / secondary,
            main % secondary
        };
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(SCMItems.DIVIDER.get());
    }

}
