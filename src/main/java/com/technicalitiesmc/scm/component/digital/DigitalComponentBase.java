package com.technicalitiesmc.scm.component.digital;

import com.technicalitiesmc.lib.circuit.component.CircuitEvent;
import com.technicalitiesmc.lib.circuit.component.ComponentContext;
import com.technicalitiesmc.lib.circuit.component.ComponentEventMap;
import com.technicalitiesmc.lib.circuit.component.ComponentType;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.lib.util.Utils;
import com.technicalitiesmc.scm.component.CircuitComponentBase;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.registries.RegistryObject;

public abstract class DigitalComponentBase<T extends DigitalComponentBase<T>> extends CircuitComponentBase<T> {

    public static final VecDirectionFlags DEFAULT_INPUT_SIDES = VecDirectionFlags.horizontals().and(VecDirection.NEG_Y);

    // Internal state
    private byte inputs = 0;

    protected DigitalComponentBase(RegistryObject<ComponentType> type, ComponentContext context, InterfaceLookup<T> interfaceLookup) {
        super(type, context, interfaceLookup);
    }

    protected boolean needsSupport() {
        return true;
    }

    protected final byte getInputs() {
        return inputs;
    }

    protected VecDirectionFlags getInputSides() {
        return DEFAULT_INPUT_SIDES;
    }

    protected boolean beforeCheckInputs(ComponentEventMap events, boolean tick) {
        return true;
    }

    protected abstract void onNewInputs(boolean tick, byte newInputs);

    @Override
    public void onAdded() {
        // Collect the state at all the updated inputs
        var newInputs = computeInputs(inputs, getInputSides());
        onNewInputs(false, newInputs);

        // Internal state updates can be immediate
        inputs = newInputs;
    }

    @Override
    public void update(ComponentEventMap events, boolean tick) {
        // If the support component below is gone, remove this and skip the update
        if (needsSupport() && !ensureSupported(events)) {
            return;
        }

        if (!beforeCheckInputs(events, tick)) {
            return;
        }

        // Going forward we only care about redstone updates coming from the input sides
        var updatedSides = events.findAny(getInputSides(), CircuitEvent.REDSTONE, CircuitEvent.NEIGHBOR_CHANGED);
        if (updatedSides.isEmpty()) {
            return;
        }

        // Collect the state at all the updated inputs
        var newInputs = computeInputs(inputs, updatedSides);
        onNewInputs(tick, newInputs);

        // Internal state updates can be immediate
        inputs = newInputs;
    }

    // Serialization

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag = super.save(tag);
        tag.putByte("inputs", inputs);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inputs = tag.getByte("inputs");
    }

    // Helpers

    protected final void clearAllInputs() {
        inputs = 0;
    }

    protected final void recheckAllInputs() {
        inputs = computeInputs((byte) 0, getInputSides());
    }

    private byte computeInputs(byte inputs, VecDirectionFlags sides) {
        for (var direction : sides) {
            var newInput = getStrongInput(direction) > 0;
            inputs = Utils.set(inputs, direction.ordinal(), newInput);
        }
        return inputs;
    }

}
