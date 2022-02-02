package com.technicalitiesmc.scm.circuit.server;

import com.mojang.math.Vector3f;
import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.math.Vec2i;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;
import com.technicalitiesmc.lib.util.Utils;
import com.technicalitiesmc.scm.circuit.CircuitAdjacency;
import com.technicalitiesmc.scm.circuit.TilePointer;
import com.technicalitiesmc.scm.circuit.util.AbsoluteSlotPos;
import com.technicalitiesmc.scm.circuit.util.TilePos;
import com.technicalitiesmc.scm.circuit.util.TileSection;
import com.technicalitiesmc.scm.circuit.util.UnpackedPos;
import com.technicalitiesmc.scm.component.misc.LevelIOComponent;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectRBTreeMap;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import static com.technicalitiesmc.scm.circuit.CircuitHelper.*;

public class Circuit extends SavedData {

    private final CircuitCache cache;
    private final UUID id;

    private final Map<TilePos, CircuitTile> tiles = new HashMap<>();
    private final Map<TilePos, ServerTileAccessor.Host> tileClaims = new HashMap<>();
    private final List<ComponentInstance> addedComponents = new ArrayList<>();
    private Map<AbsoluteSlotPos, ComponentEventMap.Builder> eventQueues = new HashMap<>();
    private final Set<AbsoluteSlotPos> queuedRemovals = new HashSet<>();
    private final Set<AbsoluteSlotPos> queuedSequentialUpdates = new HashSet<>();
    private final Set<AbsoluteSlotPos> queuedStateUpdates = new HashSet<>();
    private final Long2ObjectMap<Set<AbsoluteSlotPos>> queuedTicks = new Long2ObjectRBTreeMap<>();
    private final Set<CircuitTile> queuedTileSyncs = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<TilePos, VecDirectionFlags> queuedOutputs = new HashMap<>();
    private final Set<Level> tickLevels = new HashSet<>();
    private long currentTime = 0;
    private boolean invalid = false;
    private boolean shouldTrySplit = false;

    public Circuit(CircuitCache cache, UUID id, boolean initialize) {
        this.cache = cache;
        this.id = id;
        if (!initialize) {
            return;
        }
        // Add a tile at (0, 0)
        tiles.put(TilePos.ZERO, new CircuitTile(this, TilePos.ZERO));
    }

    public UUID getId() {
        return id;
    }

    public boolean isInvalid() {
        return invalid;
    }

    public boolean isLoaded() {
        return !tileClaims.isEmpty();
    }

    // Tile claims

    @Nullable
    public ServerTileAccessor claim(TilePos pos, ServerTileAccessor.Host host) {
        var tile = tiles.get(pos);
        if (tile == null || tileClaims.containsKey(pos)) {
            return null;
        }
        tileClaims.put(pos, host);
        return new ServerTileAccessor(tile);
    }

    public void releaseClaim(TilePos pos) {
        tileClaims.remove(pos);
    }

    // Tile management

    private ServerTileAccessor.Host getOrScoutHost(TilePos pos) {
        var host = tileClaims.get(pos);
        if (host != null || tileClaims.isEmpty()) {
            return host;
        }
        var entry = tileClaims.entrySet().iterator().next();
        var relative = pos.subtract(entry.getKey());
        return entry.getValue().find(relative);
    }

    private boolean hasTile(TilePos pos) {
        return tiles.containsKey(pos);
    }

    @Nullable
    CircuitTile getTile(TilePos pos) {
        return tiles.get(pos);
    }

    @Nullable
    private CircuitTile getOrScoutTile(TilePos pos) {
        var tile = getTile(pos);
        if (tile != null || tileClaims.isEmpty()) {
            return tile;
        }
        var entry = tileClaims.entrySet().iterator().next();
        var relative = pos.subtract(entry.getKey());
        var host = entry.getValue().find(relative);
        return host != null ? host.getAccessor().getTile() : null;
    }

    private boolean visitTileAreaWhile(TilePos pos, BiPredicate<CircuitTile, TileSection> predicate) {
        for (var section : TileSection.VALUES) {
            var tile = getTile(pos.offsetNeg(section));
            if (tile != null && !predicate.test(tile, section)) {
                return false;
            }
        }
        return true;
    }

    boolean isTileAreaEmpty(TilePos pos) {
        return visitTileAreaWhile(pos, CircuitTile::isEmpty);
    }

    void clearTileArea(TilePos pos, @Nullable ComponentHarvestContext context) {
        visitTileAreaWhile(pos, (t, s) -> {
            t.clear(s, context);
            return true;
        });
        for (int i = -1; i < SIZE_PLUS_ONE; i++) {
            for (int j = 0; j < HEIGHT; j++) {
                for (var slot : ComponentSlot.VALUES) {
                    enqueueEventAt(pos.pack(-2, j, i), slot, VecDirection.POS_X, CircuitEvent.NEIGHBOR_CHANGED);
                    enqueueEventAt(pos.pack(SIZE, j, i), slot, VecDirection.NEG_X, CircuitEvent.NEIGHBOR_CHANGED);
                    enqueueEventAt(pos.pack(i, j, -2), slot, VecDirection.POS_Z, CircuitEvent.NEIGHBOR_CHANGED);
                    enqueueEventAt(pos.pack(i, j, SIZE), slot, VecDirection.NEG_Z, CircuitEvent.NEIGHBOR_CHANGED);
                }
            }
        }
    }

    void clearAreaAndRemoveTile(CircuitTile tile, ComponentHarvestContext context) {
        var pos = tile.getPosition();
        clearTileArea(pos, context);
        releaseClaim(pos);
        tiles.remove(pos);
        queuedTileSyncs.remove(tile);
        shouldTrySplit = true;
    }

    // Component management

    boolean isValidPosition(Vec3i pos) {
        var unpacked = UnpackedPos.of(pos);
        var tile = getTile(unpacked.tile());
        if (tile == null) {
            return false;
        }
        // If we are on the edge or corner, ensure we have a neighbor
        var section = unpacked.pos().getSection();
        return section == TileSection.ALL || getTile(unpacked.tile().offset(section)) != null;
    }

    @Nullable
    ComponentInstance get(Vec3i pos, ComponentSlot slot) {
        var unpacked = UnpackedPos.of(pos);
        // Ensure the position isn't out of bounds
        if (unpacked.pos().y() < 0 || unpacked.pos().y() >= HEIGHT) {
            return null;
        }
        var tile = getTile(unpacked.tile());
        if (tile == null) {
            // If we are on an edge and there is no tile where requested but there is a neighbor tile
            if (unpacked.pos().y() == 0 && unpacked.pos().isOnXEdge() != unpacked.pos().isOnZEdge()) {
                var neighborPos = unpacked.tile().offset(unpacked.pos().getSection());
                if (hasTile(neighborPos)) {
                    return new ComponentInstance(null, unpacked.pos(), ctx -> {
                        return new LevelIOComponent(ctx, side -> {
                            var host = getOrScoutHost(neighborPos);
                            return host != null ? host.getInput(side) : 0;
                        });
                    });
                }
            }
            return null;

        }
        var ci = tile.get(unpacked.pos(), slot);
        // If we are on an edge and there is a tile where requested and there is no neighbor tile
        if (ci == null && unpacked.pos().y() == 0 && unpacked.pos().isOnXEdge() != unpacked.pos().isOnZEdge() &&
                !hasTile(unpacked.tile().offset(unpacked.pos().getSection()))) {
            // Return a component that will do I/O at this tile position
            return new ComponentInstance(null, unpacked.pos(), ctx -> {
                return new LevelIOComponent(ctx, side -> {
                    var host = getOrScoutHost(unpacked.tile());
                    return host != null ? host.getInput(side) : 0;
                });
            });
        }
        return ci;
    }

    @Nullable
    Supplier<ComponentInstance> tryPutLater(Vec3i pos, ComponentType type, ComponentType.Factory factory) {
        var unpacked = UnpackedPos.of(pos);
        // Ensure the position isn't out of bounds
        if (unpacked.pos().y() < 0 || unpacked.pos().y() >= HEIGHT) {
            return null;
        }

        var tasks = new ArrayList<Runnable>();

        // If there is no tile or we can't put the component there, abort
        var tile = getTile(unpacked.tile());
        if (tile == null || !tile.canPut(unpacked.pos(), type)) {
            var neighbor = getOrScoutTile(unpacked.tile());
            if (neighbor == null || neighbor.getCircuit() == this || !neighbor.canPut(unpacked.pos(), type)) {
                return null;
            }
            var offset = unpacked.tile().subtract(neighbor.getPosition());
            tasks.add(() -> absorb(neighbor.getCircuit(), offset));
            tile = neighbor;
        }

        // If we are on the edge or corner, find the current or prospective neighbor
        var section = unpacked.pos().getSection();
        if (section != TileSection.ALL) {
            int xOff = section.getXOffset(), zOff = section.getZOffset();
            var neighbors = new CircuitTile[xOff + 1][zOff + 1];
            for (int i = 0; i <= xOff; i++) {
                for (int j = 0; j <= zOff; j++) {
                    if (i != 0 || j != 0) {
                        var neighborPos = unpacked.tile().offset(i, j);
                        var neighbor = neighbors[i][j] = getOrScoutTile(neighborPos);
                        // If there is no neighbor, we can't place the component - abort
                        if (neighbor == null) {
                            tasks.forEach(Runnable::run);
                            return null;
                        }
                    }
                }
            }
            for (int i = 0; i <= xOff; i++) {
                for (int j = 0; j <= zOff; j++) {
                    if (i != 0 || j != 0) {
                        var neighborPos = unpacked.tile().offset(i, j);
                        var neighbor = neighbors[i][j];
                        // If the neighbor belongs to another circuit, absorb it
                        if (neighbor.getCircuit() != this) {
                            var offset = neighborPos.subtract(neighbor.getPosition());
                            tasks.add(() -> absorb(neighbor.getCircuit(), offset));
                        }
                    }
                }
            }
        }

        var theTile = tile;
        return () -> {
            // Run all enqueued tasks
            tasks.forEach(Runnable::run);

            // Finally, put component down
            setDirty();
            var instance = theTile.put(unpacked.pos(), factory);
            if (instance.getType() != type) {
                throw new IllegalStateException("Attempted to place a mismatched component type.");
            }
            addedComponents.add(instance);
            sendEvent(pos, instance.getSlot(), CircuitEvent.NEIGHBOR_CHANGED, VecDirectionFlags.all());
            return instance;
        };
    }

    @Nullable
    ComponentInstance tryPut(Vec3i pos, ComponentType type, ComponentType.Factory factory) {
        var adder = tryPutLater(pos, type, factory);
        return adder == null ? null : adder.get();
    }

    void harvest(Vec3i pos, ComponentSlot slot, ComponentHarvestContext context) {
        var c = get(pos, slot);
        if (c != null) {
            c.harvest(context);
        }
    }

    boolean tryRemove(Vec3i pos, ComponentSlot slot) {
        var unpacked = UnpackedPos.of(pos);
        var tile = getTile(unpacked.tile());
        if (tile == null) {
            return false;
        }
        var component = tile.get(unpacked.pos(), slot);
        if (component != null) {
            if (tile.remove(unpacked.pos(), slot)) {
                setDirty();
                return true;
            }
        }
        return false;
    }

    public void scheduleRemoval(Vec3i pos, ComponentSlot slot) {
        queuedRemovals.add(new AbsoluteSlotPos(pos, slot));
    }

    InteractionResult use(Vec3i pos, ComponentSlot slot, Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
        var c = get(pos, slot);
        if (c != null) {
            return c.use(player, hand, sideHit, hit);
        }
        return InteractionResult.PASS;
    }

    void sendEvent(Vec3i pos, ComponentSlot slot, CircuitEvent event, VecDirectionFlags directions) {
        for (var direction : directions) {
            if (direction.getAxis() != Direction.Axis.Y) {
                var p = pos.offset(direction.getOffset());
                for (ComponentSlot s : ComponentSlot.VALUES) {
                    enqueueEventAt(p, s, direction.getOpposite(), event);
                }
            } else {
                // Visit every component along the way within this position, as well as every component in the next position over
                var dir = direction.getAxisDirection();
                var currentSlot = slot;
                var offset = Vec3i.ZERO;
                do {
                    offset = offset.offset(currentSlot.getOffsetTowards(dir));
                    if (offset.distManhattan(Vec3i.ZERO) > 1){
                        break;
                    }
                    var next = currentSlot.next(dir);
                    var component = get(pos.offset(offset), next);
                    if (component != null) {
                        enqueueEventAt(pos.offset(offset), next, direction.getOpposite(), event);
                    }
                    currentSlot = next;
                } while (true);
            }
        }
    }

    void enqueueEventAt(Vec3i pos, ComponentSlot slot, VecDirection side, CircuitEvent event) {
        var c = get(pos, slot);
        if (c == null) {
            var builder = eventQueues.computeIfAbsent(new AbsoluteSlotPos(pos, slot), $ -> new ComponentEventMap.Builder());
            builder.add(side, event);
            return;
        }
        if (!c.isLevelIOComponent()) {
            var builder = eventQueues.computeIfAbsent(new AbsoluteSlotPos(pos, slot), $ -> new ComponentEventMap.Builder());
            c.receiveEvent(side, event, builder);
            return;
        }
        if (!side.getAxis().isHorizontal() || !(event == CircuitEvent.REDSTONE || event == CircuitEvent.NEIGHBOR_CHANGED)) {
            return;
        }

        var unpacked = UnpackedPos.of(pos);
        // If on a corner or not on an edge, skip
        if (unpacked.pos().isOnXEdge() == unpacked.pos().isOnZEdge()) {
            return;
        }

        var tile = getTile(unpacked.tile());
        var neighborPos = unpacked.tile().offset(unpacked.pos().getSection());
        var neighbor = getTile(neighborPos);

        // If one of the tiles lands out of bounds but the other one doesn't, queue the output
        if ((tile == null) != (neighbor == null)) {
            var eventPos = !side.isPositive() ? unpacked.tile() : neighborPos;
            var sides = queuedOutputs.getOrDefault(eventPos, VecDirectionFlags.none());
            queuedOutputs.put(eventPos, sides.and(side.getOpposite()));
        }
    }

    void enqueueSequentialUpdate(Vec3i pos, ComponentSlot slot) {
        queuedSequentialUpdates.add(new AbsoluteSlotPos(pos, slot));
    }

    void enqueueStateUpdate(Vec3i pos, ComponentSlot slot) {
        queuedStateUpdates.add(new AbsoluteSlotPos(pos, slot));
    }

    void enqueueTick(Vec3i pos, ComponentSlot slot, int delay) {
        if (delay <= 0) {
            throw new IllegalArgumentException("Delay cannot be less than or equal to 0.");
        }
        var components = queuedTicks.computeIfAbsent(currentTime + delay, $ -> new HashSet<>());
        components.add(new AbsoluteSlotPos(pos, slot));
    }

    void playSound(Vec3i pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
        var unpacked = UnpackedPos.of(pos);
        var host = tileClaims.get(unpacked.tile());
        if (host != null) {
            host.playSound(sound, source, volume, pitch);
        }
    }

    // Syncing

    void enqueueTileSync(CircuitTile tile) {
        queuedTileSyncs.add(tile);
    }

    CircuitAdjacency[] calculateAdjacencyMap(TilePos pos) {
        var nx = getTile(pos.offset(-1, 0)) != null;
        var nz = getTile(pos.offset(0, -1)) != null;
        var px = getTile(pos.offset(1, 0)) != null;
        var pz = getTile(pos.offset(0, 1)) != null;

        var adjacency = new CircuitAdjacency[4];
        adjacency[0] = nx && nz ?
                (getTile(pos.offset(-1, -1)) != null ? CircuitAdjacency.BOTH_FULL : CircuitAdjacency.BOTH_PARTIAL) :
                nx ? CircuitAdjacency.HORIZONTAL : nz ? CircuitAdjacency.VERTICAL : CircuitAdjacency.NONE;
        adjacency[1] = px && nz ?
                (getTile(pos.offset(1, -1)) != null ? CircuitAdjacency.BOTH_FULL : CircuitAdjacency.BOTH_PARTIAL) :
                px ? CircuitAdjacency.HORIZONTAL : nz ? CircuitAdjacency.VERTICAL : CircuitAdjacency.NONE;
        adjacency[2] = nx && pz ?
                (getTile(pos.offset(-1, 1)) != null ? CircuitAdjacency.BOTH_FULL : CircuitAdjacency.BOTH_PARTIAL) :
                nx ? CircuitAdjacency.HORIZONTAL : pz ? CircuitAdjacency.VERTICAL : CircuitAdjacency.NONE;
        adjacency[3] = px && pz ?
                (getTile(pos.offset(1, 1)) != null ? CircuitAdjacency.BOTH_FULL : CircuitAdjacency.BOTH_PARTIAL) :
                px ? CircuitAdjacency.HORIZONTAL : pz ? CircuitAdjacency.VERTICAL : CircuitAdjacency.NONE;
        return adjacency;
    }

    private void sendTile(CircuitTile tile) {
        var pos = tile.getPosition();
        var host = tileClaims.get(pos);
        if (host == null) {
            // Nobody is around, so clear the sync queue
            tile.clearSyncQueue();
            return;
        }

        var adjacency = calculateAdjacencyMap(pos);
        var states = tile.getAndClearSyncQueue();
        host.syncState(states, adjacency);
    }

    // Adjacency

    private void absorb(Circuit other, Vec2i offset) {
        if (other == this) {
            return;
        }
        other.tiles.forEach((pos, tile) -> {
            var newPos = pos.offset(offset);
            tile.move(this, newPos);
            tiles.put(newPos, tile);
            var host = other.getOrScoutHost(pos);
            if (host != null) {
                host.updatePointer(new TilePointer(id, newPos));
            }
        });
        other.tileClaims.forEach((pos, host) -> {
            var newPos = pos.offset(offset);
            tileClaims.put(newPos, host);
        });
        other.eventQueues.forEach((pos, builder) -> {
            var newPos = pos.offset(offset);
            eventQueues.put(newPos, builder);
        });
        for (var pos : other.queuedSequentialUpdates) {
            var newPos = pos.offset(offset);
            queuedSequentialUpdates.add(newPos);
        }
        for (var pos : other.queuedStateUpdates) {
            var newPos = pos.offset(offset);
            queuedStateUpdates.add(newPos);
        }
        other.queuedTicks.forEach((delay, components) -> {
            var time = delay - other.currentTime + currentTime;
            var queuedComponents = queuedTicks.computeIfAbsent(time, $ -> new HashSet<>());
            for (var pos : components) {
                queuedComponents.add(pos.offset(offset));
            }
        });
        queuedTileSyncs.addAll(other.queuedTileSyncs);
        other.invalid = true;
        // Force update all tiles
        queuedTileSyncs.addAll(tiles.values());
    }

    Set<Circuit> maybeSplit() {
        // Update the adjacency of every tile
        for (var tile : tiles.values()) {
            var mightSplit = tile.computeAdjacency();
            shouldTrySplit |= mightSplit;
        }
        // If there are no changes that would result in a potential split, return
        if (!shouldTrySplit) {
            return Collections.singleton(this);
        }
        shouldTrySplit = false;

        class Group {
            private final Set<CircuitTile> members = new HashSet<>();
        }
        var groups = Utils.<Group>newIdentityHashSet();
        var mappings = new HashMap<CircuitTile, Group>();
        for (var tile : tiles.values()) {
            var group = mappings.computeIfAbsent(tile, p -> {
                var newGroup = new Group();
                groups.add(newGroup);
                newGroup.members.add(tile);
                return newGroup;
            });
            for (var section : TileSection.NEIGHBORS) {
                if (tile.isEmpty(section)) {
                    continue;
                }

                var neighborPos = tile.getPosition().offset(section);
                var neighbor = tiles.get(neighborPos);
                if (neighbor == null) {
                    continue;
                }

                var neighborGroup = mappings.get(neighbor);
                if (neighborGroup == group) {
                    continue;
                }
                if (neighborGroup == null) {
                    group.members.add(neighbor);
                    mappings.put(neighbor, group);
                    continue;
                }

                group.members.addAll(neighborGroup.members);
                for (var member : neighborGroup.members) {
                    mappings.put(member, group);
                }
                groups.remove(neighborGroup);
            }
        }

        // What???
        if (groups.size() == 0) {
            invalid = true;
            // Return this, but after invalidating so it gets evicted
            return Collections.singleton(this);
        }

        // If there's only one group, that's this circuit so there's no need to do anything
        if (groups.size() == 1) {
            return Collections.singleton(this);
        }

        // Assemble circuits out of each group
        var circuits = new HashSet<Circuit>();
        for (var group : groups) {
            var id = UUID.randomUUID();
            var circuit = cache.createUncached(id);
            circuit.currentTime = currentTime;
            for (var tile : group.members) {
                var pos = tile.getPosition();
                tile.move(circuit, pos);
                circuit.tiles.put(pos, tile);
                var host = tileClaims.get(pos);
                if (host != null) {
                    circuit.tileClaims.put(pos, host);
                } else {
                    host = getOrScoutHost(pos);
                }
                if (host != null) {
                    host.updatePointer(new TilePointer(id, pos));
                }
                // Force update tile
                circuit.queuedTileSyncs.add(tile);
            }
            circuit.eventQueues.putAll(eventQueues); // TODO: Optimize and add safeguards
            queuedTicks.forEach((ticks, components) -> { // TODO: Optimize
                circuit.queuedTicks.put(ticks, new HashSet<>(components));
            });
            circuit.queuedSequentialUpdates.addAll(queuedSequentialUpdates);
            circuit.queuedStateUpdates.addAll(queuedStateUpdates);
            circuit.tickLevels.addAll(tickLevels); // TODO: Optimize
            circuits.add(circuit);
        }

        invalid = true;
        // Add this, but after invalidating so it gets evicted
        circuits.add(this);
        return circuits;
    }

    // Updates

    void scheduleTick(Level level) {
        tickLevels.add(level);
    }

    /**
     * Returns true if invalid
     */
    void update() { // TODO: Clean up this whole thing
        var levels = tickLevels.size();
        var firstLevel = tickLevels.stream().findFirst();
        tickLevels.clear();
        // If it's been queued up in multiple levels or has already ticked, skip
        if (levels != 1) {
            return;
        }

        var updatesThisTick = queuedTicks.remove(currentTime);
        if (updatesThisTick == null) {
            updatesThisTick = Collections.emptySet();
        }
        final var updatesThisTick2 = updatesThisTick;

        // Fire any queued state updates ahead of the tick
        for (var pos : queuedStateUpdates) {
            var component = get(pos.pos(), pos.slot());
            if (component != null) {
                component.updateExternalState();
            }
        }
        queuedStateUpdates.clear();

        // Fire any queued sequential updates ahead of the tick
        for (var pos : queuedSequentialUpdates) {
            var component = get(pos.pos(), pos.slot());
            if (component != null) {
                component.updateSequential();
            }
        }
        queuedSequentialUpdates.clear();

        // Fire any queued state updates ahead of the tick
        for (var pos : queuedStateUpdates) {
            var component = get(pos.pos(), pos.slot());
            if (component != null) {
                component.updateExternalState();
            }
        }
        queuedStateUpdates.clear();

        // Notify all newly added components
        addedComponents.forEach(ComponentInstance::onAdded);
        addedComponents.clear();

        // Fire any queued state updates again
        for (var pos : queuedStateUpdates) {
            var component = get(pos.pos(), pos.slot());
            if (component != null) {
                component.updateExternalState();
            }
        }
        queuedStateUpdates.clear();

        var level = (ServerLevel) firstLevel.get();

        // As long as we have ticks or events queued up
        while (!updatesThisTick2.isEmpty() || !eventQueues.isEmpty()) {
            // Copy over the event queues so we don't modify them
            var eventQueues = this.eventQueues;
            this.eventQueues = new HashMap<>();

            // Update every component with queued up events
            eventQueues.forEach((pos, events) -> {
                var tick = updatesThisTick2.remove(pos);
                var component = get(pos.pos(), pos.slot());
                if (component != null) {
                    component.update(events.build(), tick);
                }
            });

            // Update every component with queued up ticks
            for (var pos : updatesThisTick2) {
                var component = get(pos.pos(), pos.slot());
                if (component != null) {
                    component.update(ComponentEventMap.empty(), true);
                }
            }

            // Fire sequential updates
            for (var pos : queuedSequentialUpdates) {
                var component = get(pos.pos(), pos.slot());
                if (component != null) {
                    component.updateSequential();
                }
            }

            // Fire state updates
            for (var pos : queuedStateUpdates) {
                var component = get(pos.pos(), pos.slot());
                if (component != null) {
                    component.updateExternalState();
                }
            }

            // Remove components
            for (var pos : queuedRemovals) {
                var component = get(pos.pos(), pos.slot());
                if (component != null) {
                    var unpacked = UnpackedPos.of(pos.pos());
                    var host = tileClaims.get(unpacked.tile());
                    if (host == null && !tileClaims.isEmpty()) {
                        host = tileClaims.values().iterator().next();
                    }
                    var ctx = host != null ? ComponentHarvestContext.drop(level, host::drop) : ComponentHarvestContext.dummy(level);
                    component.harvest(ctx);
                }
            }

            // Clear update lists
            updatesThisTick.clear();
            queuedSequentialUpdates.clear();
            queuedStateUpdates.clear();
            queuedRemovals.clear();

            // Ensure the circuit gets saved
            setDirty();
        }

        // Sync tiles
        queuedTileSyncs.forEach(this::sendTile);
        queuedTileSyncs.clear();

        // Recalculate world outputs
        queuedOutputs.forEach((pos, sides) -> {
            var tile = getTile(pos);
            if (tile == null) {
                return; // Something has gone wrong here
            }
            var host = getOrScoutHost(pos);
            if (host == null) {
                return; // Something has gone wrong here
            }

            for (var side : sides) {
                var output = tile.calculateOutput(side);
                host.setOutput(side, output);
            }
        });
        queuedOutputs.clear();

        // Increase the tick counter
        currentTime++;
    }

    // Serialization

    @Override
    public void save(File file) {
        file.getParentFile().mkdirs();
        super.save(file);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        var tiles = new ListTag();
        this.tiles.forEach((pos, tile) -> {
            var t = new CompoundTag();
            t.putIntArray("pos", pos.toArray());
            t.put("tile", tile.save(new CompoundTag()));
            tiles.add(t);
        });
        tag.put("tiles", tiles);

        var eventQueues = new ListTag();
        this.eventQueues.forEach((pos, builder) -> {
            var t = new CompoundTag();
            t.putIntArray("pos", pos.toArray());
            t.putByteArray("events", builder.build().serialize());
            eventQueues.add(t);
        });
        tag.put("event_queues", eventQueues);

        var queuedTicks = new ListTag();
        this.queuedTicks.forEach((tick, components) -> {
            var t = new CompoundTag();
            t.putLong("tick", tick);
            var l = new ListTag();
            for (var pos : components) {
                l.add(new IntArrayTag(pos.toArray()));
            }
            t.put("components", l);
            queuedTicks.add(t);
        });
        tag.put("queued_ticks", queuedTicks);

        var queuedSequentialUpdates = new ListTag();
        this.queuedSequentialUpdates.forEach(pos -> {
            queuedSequentialUpdates.add(new IntArrayTag(pos.toArray()));
        });
        tag.put("queued_sequential_updates", queuedSequentialUpdates);

        var queuedStateUpdates = new ListTag();
        this.queuedStateUpdates.forEach(pos -> {
            queuedStateUpdates.add(new IntArrayTag(pos.toArray()));
        });
        tag.put("queued_state_updates", queuedStateUpdates);

        tag.putLong("current_time", currentTime);
        return tag;
    }

    static Circuit load(CircuitCache cache, UUID id, CompoundTag tag) {
        var circuit = new Circuit(cache, id, false);

        var tiles = tag.getList("tiles", Tag.TAG_COMPOUND);
        for (int i = 0; i < tiles.size(); i++) {
            var t = tiles.getCompound(i);
            var pos = new TilePos(t.getIntArray("pos"));
            var tile = CircuitTile.load(circuit, pos, t.getCompound("tile"));
            circuit.tiles.put(pos, tile);
        }

        var eventQueues = tag.getList("event_queues", Tag.TAG_COMPOUND);
        for (int i = 0; i < eventQueues.size(); i++) {
            var t = eventQueues.getCompound(i);
            var pos = new AbsoluteSlotPos(t.getIntArray("pos"));
            var events = ComponentEventMap.Builder.deserialize(t.getByteArray("events"));
            circuit.eventQueues.put(pos, events);
        }

        var queuedTicks = tag.getList("queued_ticks", Tag.TAG_COMPOUND);
        for (int i = 0; i < queuedTicks.size(); i++) {
            var t = queuedTicks.getCompound(i);
            var tick = t.getLong("tick");
            var components = new HashSet<AbsoluteSlotPos>();
            var l = t.getList("components", Tag.TAG_INT_ARRAY);
            for (int j = 0; j < l.size(); j++) {
                components.add(new AbsoluteSlotPos(l.getIntArray(j)));
            }
            circuit.queuedTicks.put(tick, components);
        }

        var queuedSequentialUpdates = tag.getList("queued_sequential_updates", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < queuedSequentialUpdates.size(); i++) {
            var arr = queuedSequentialUpdates.getIntArray(i);
            circuit.queuedSequentialUpdates.add(new AbsoluteSlotPos(arr));
        }

        var queuedStateUpdates = tag.getList("queued_state_updates", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < queuedStateUpdates.size(); i++) {
            var arr = queuedStateUpdates.getIntArray(i);
            circuit.queuedStateUpdates.add(new AbsoluteSlotPos(arr));
        }

        circuit.currentTime = tag.getLong("current_time");
        return circuit;
    }

}
