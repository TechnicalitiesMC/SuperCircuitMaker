package com.technicalitiesmc.scm.placement;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.technicalitiesmc.lib.circuit.component.CircuitComponent;
import com.technicalitiesmc.lib.circuit.component.ComponentContext;
import com.technicalitiesmc.lib.circuit.component.ComponentState;
import com.technicalitiesmc.lib.circuit.component.ComponentType;
import com.technicalitiesmc.lib.circuit.placement.ComponentPlacement;
import com.technicalitiesmc.lib.circuit.placement.PlacementContext;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.RegistryObject;

import java.util.*;

public class WirePlacement implements ComponentPlacement {

    private final RegistryObject<ComponentType> component;
    private final Factory factory;
    private final StateFactory stateFactory;

    public WirePlacement(RegistryObject<ComponentType> component, Factory factory, StateFactory stateFactory) {
        this.component = component;
        this.factory = factory;
        this.stateFactory = stateFactory;
    }

    @Override
    public Instance begin() {
        return new Instance();
    }

    @Override
    public Instance deserialize(FriendlyByteBuf buf) {
        var count = buf.readInt();
        var instance = new Instance();
        for (var i = 0; i < count; i++) {
            var pos = buf.readBlockPos();
            var sides = VecDirectionFlags.deserialize(buf.readByte());
            instance.connectionMap.put(pos, sides);
        }
        instance.disconnectOthers = buf.readBoolean();
        return instance;
    }

    private class Instance implements ComponentPlacement.Instance {

        private final Set<Vec3i> uniquePositions = new HashSet<>();
        private final List<Vec3i> positions = new ArrayList<>();
        private final List<VecDirectionFlags> connections = new ArrayList<>();
        private final Map<Vec3i, VecDirectionFlags> connectionMap = new HashMap<>();
        private boolean disconnectOthers;

        @Override
        public boolean tick(PlacementContext.Client context, Vec3i clickedPos, VecDirection clickedFace) {
            disconnectOthers = context.isModifierPressed();
            var pos = clickedPos.offset(clickedFace.getOffset());
            // If the component below is not solid or the wire wouldn't fit here, skip
            if (!context.isTopSolid(pos.below()) || !context.canPlace(pos, component.get())) {
                return !positions.isEmpty();
            }

            var idx = positions.lastIndexOf(pos);
            // If we are on an already visited position
            if (idx != -1) {
                // If it's the last position in our history, update it
                if (idx == positions.size() - 1) {
                    connections.set(idx, computeConnections(context, pos));
                    return true;
                }
                // If we have backtracked up to 3 positions, remove them
                if (idx > positions.size() - 4) {
                    var start = Math.max(0, idx + 1);
                    positions.subList(start, positions.size()).clear();
                    connections.subList(start, connections.size()).clear();
                    uniquePositions.clear();
                    uniquePositions.addAll(positions);
                    return true;
                }
            }

            if (context.getPlayer().isCreative() || uniquePositions.size() >= context.getStack().getCount()) {
                return true;
            }

            // If we are on a new position or too far from the last hit, add it to the history
            uniquePositions.add(pos);
            positions.add(pos);
            connections.add(computeConnections(context, pos));
            return true;
        }

        private VecDirectionFlags computeConnections(PlacementContext.Client context, Vec3i pos) {
            var prevPos = positions.size() >= 2 ? positions.get(positions.size() - 2) : null;
            if (prevPos == null) {
                return VecDirectionFlags.none();
            }
            if (prevPos.distManhattan(pos) != 1) {
                return VecDirectionFlags.none();
            }
            var sideFrom = VecDirection.getNearest(prevPos.subtract(pos));
            if (sideFrom.getAxis() == Direction.Axis.Y) {
                return VecDirectionFlags.none();
            }
            return VecDirectionFlags.of(sideFrom);
        }

        @Override
        public void stopPlacing(PlacementContext.Client context) {
            disconnectOthers = context.isModifierPressed();
        }

        private void coalesce() {
            connectionMap.clear();

            // Collect all connections
            for (var i = 0; i < positions.size(); i++) {
                var pos = positions.get(i);
                var sides = connections.get(i);
                var current = connectionMap.getOrDefault(pos, VecDirectionFlags.none());
                connectionMap.put(pos, current.and(sides));
            }

            // Bridge connections
            connectionMap.forEach((pos, sides) -> {
                for (var side : sides) {
                    var neighborPos = pos.offset(side.getOffset());
                    connectionMap.computeIfPresent(neighborPos, ($, s) -> s.and(side.getOpposite()));
                }
            });
        }

        @Override
        public boolean isValid(PlacementContext.Client context) {
            coalesce();
            return !connectionMap.isEmpty();
        }

        @Override
        public void serialize(FriendlyByteBuf buf) {
            buf.writeInt(connectionMap.size());
            connectionMap.forEach((pos, sides) -> {
                buf.writeBlockPos(new BlockPos(pos));
                buf.writeByte(sides.serialize());
            });
            buf.writeBoolean(disconnectOthers);
        }

        @Override
        public void place(PlacementContext.Server context) {
            if (context.tryPutAll(ctx -> {
                for (var entry : connectionMap.entrySet()) {
                    if (!ctx.at(entry.getKey(), component.get(), c -> factory.create(c, entry.getValue(), disconnectOthers, context.getPlayer()))) {
                        return false;
                    }
                }
                return true;
            })) {
                context.consumeItems(connectionMap.size());
                context.playSound();
            }
        }

        @Override
        public Multimap<Vec3i, ComponentState> getPreviewStates(Player player) {
            coalesce();
            var builder = ImmutableMultimap.<Vec3i, ComponentState>builder();
            connectionMap.forEach((pos, connections) -> {
                builder.put(pos, stateFactory.create(connections, player));
            });
            return builder.build();
        }

    }

    @FunctionalInterface
    public interface Factory {

        CircuitComponent create(ComponentContext context, VecDirectionFlags connections, boolean disconnectOthers, Player player);

    }

    @FunctionalInterface
    public interface StateFactory {

        ComponentState create(VecDirectionFlags connections, Player player);

    }

}
