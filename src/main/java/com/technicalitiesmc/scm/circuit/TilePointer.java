package com.technicalitiesmc.scm.circuit;

import com.technicalitiesmc.scm.circuit.util.TilePos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public record TilePointer(UUID id, TilePos tilePos) {

    public TilePointer(CompoundTag tag) {
        this(tag.getUUID("id"), new TilePos(tag.getIntArray("tilePos")));
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putUUID("id", id);
        tag.putIntArray("tilePos", tilePos.toArray());
        return tag;
    }

}
