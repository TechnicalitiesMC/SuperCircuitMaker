package com.technicalitiesmc.scm.component.misc;

import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSink;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSource;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.scm.component.CircuitComponentBase;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;

import java.util.Collections;
import java.util.List;
import java.util.function.ToIntFunction;

public class LevelIOComponent extends CircuitComponentBase<LevelIOComponent> {

    private static final InterfaceLookup<LevelIOComponent> INTERFACES = InterfaceLookup.<LevelIOComponent>builder()
            .with(RedstoneSink.class, VecDirectionFlags.horizontals(), RedstoneSink::instance)
            .with(RedstoneSource.class, VecDirectionFlags.horizontals(), LevelIOComponent::getRedstoneSource)
            .build();

    private final ToIntFunction<VecDirection> inputReader;

    public LevelIOComponent(ComponentContext context, ToIntFunction<VecDirection> inputReader) {
        super(SCMComponents.LEVEL_IO, context, INTERFACES);
        this.inputReader = inputReader;
    }

    public LevelIOComponent(ComponentContext context) {
        this(context, s -> {
            throw new UnsupportedOperationException("Cannot read input from invalid I/O component.");
        });
    }

    @Override
    public CircuitComponent copyRotated(ComponentContext context, Rotation rotation) {
        throw new UnsupportedOperationException("Cannot copy an I/O component.");
    }

    @Override
    public List<ItemStack> getDrops(ServerLevel level, boolean isCreative) {
        return Collections.emptyList();
    }

    @Override
    public void spawnDrops(ComponentHarvestContext context) {
    }

    @Override
    public void harvest(ComponentHarvestContext context) {
    }

    @Override
    public void receiveEvent(VecDirection side, CircuitEvent event, ComponentEventMap.Builder builder) {
    }

    private RedstoneSource getRedstoneSource(VecDirection side) {
        return RedstoneSource.of(0, inputReader.applyAsInt(side.getOpposite()));
    }

}
