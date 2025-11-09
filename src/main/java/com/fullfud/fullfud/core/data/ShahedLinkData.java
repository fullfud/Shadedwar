package com.fullfud.fullfud.core.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ShahedLinkData extends SavedData {
    private static final String DATA_NAME = "fullfud_shahed_links";
    private final Map<UUID, UUID> droneOwners = new HashMap<>();

    public ShahedLinkData() {
    }

    public ShahedLinkData(final CompoundTag tag) {
        final ListTag list = tag.getList("Links", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("Drone") && entry.hasUUID("Owner")) {
                droneOwners.put(entry.getUUID("Drone"), entry.getUUID("Owner"));
            }
        }
    }

    public static ShahedLinkData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ShahedLinkData::new, ShahedLinkData::new, DATA_NAME);
    }

    public void link(final UUID droneId, final UUID ownerId) {
        droneOwners.put(droneId, ownerId);
        setDirty();
    }

    public void unlink(final UUID droneId) {
        if (droneOwners.remove(droneId) != null) {
            setDirty();
        }
    }

    public Optional<UUID> owner(final UUID droneId) {
        return Optional.ofNullable(droneOwners.get(droneId));
    }

    @Override
    public CompoundTag save(final CompoundTag tag) {
        final ListTag list = new ListTag();
        for (final Map.Entry<UUID, UUID> entry : droneOwners.entrySet()) {
            final CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("Drone", entry.getKey());
            entryTag.putUUID("Owner", entry.getValue());
            list.add(entryTag);
        }
        tag.put("Links", list);
        return tag;
    }
}
