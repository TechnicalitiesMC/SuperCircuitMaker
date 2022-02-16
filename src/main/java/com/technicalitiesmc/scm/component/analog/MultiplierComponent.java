package com.technicalitiesmc.scm.component.analog;

import com.technicalitiesmc.lib.circuit.component.ComponentContext;
import com.technicalitiesmc.scm.init.SCMComponents;
import com.technicalitiesmc.scm.init.SCMItems;
import net.minecraft.world.item.ItemStack;

public class MultiplierComponent extends OperatorComponentBase {

    public MultiplierComponent(ComponentContext context) {
        super(SCMComponents.MULTIPLIER, context);
    }

    @Override
    protected int[] operate(int main, int secondary) {
        var result = main * secondary;
        return new int[] {
            Math.min(result, 255),
            result > 255 ? 255 : 0
        };
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(SCMItems.MULTIPLIER.get());
    }

}
