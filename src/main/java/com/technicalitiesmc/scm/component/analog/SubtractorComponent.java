package com.technicalitiesmc.scm.component.analog;

import com.technicalitiesmc.lib.circuit.component.ComponentContext;
import com.technicalitiesmc.scm.init.SCMComponents;
import com.technicalitiesmc.scm.init.SCMItems;
import net.minecraft.world.item.ItemStack;

public class SubtractorComponent extends OperatorComponentBase {

    public SubtractorComponent(ComponentContext context) {
        super(SCMComponents.SUBTRACTOR, context);
    }

    @Override
    protected int[] operate(int main, int secondary) {
        var result = main - secondary;
        return new int[] {
            Math.max(0, result),
            result < 0 ? Math.min(-result, 255) : 0
        };
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(SCMItems.SUBTRACTOR.get());
    }

}
