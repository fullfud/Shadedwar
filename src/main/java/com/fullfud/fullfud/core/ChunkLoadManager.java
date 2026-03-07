package com.fullfud.fullfud.core;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkLoadManager {
    private static final TicketType<Integer> DRONE_TICKET = TicketType.create("fullfud_drone", Integer::compareTo, 40);
    private static final long STALE_THRESHOLD_MS = 5000L;
    private static final Map<Integer, TicketData> ACTIVE_TICKETS = new ConcurrentHashMap<>();

    private ChunkLoadManager() {
    }

    public static void ensureChunksLoaded(final ServerLevel level, final int entityId, final ChunkPos pos, final int radius) {
        if (level == null || pos == null) {
            return;
        }

        final int safeRadius = Math.max(1, radius);
        final TicketData existing = ACTIVE_TICKETS.get(entityId);
        if (existing != null) {
            if (existing.level == level && existing.pos.equals(pos) && existing.radius == safeRadius) {
                existing.lastUpdate = System.currentTimeMillis();
                return;
            }
            removeTicket(existing.level, existing.pos, existing.radius, entityId);
        }

        final ServerChunkCache chunkSource = level.getChunkSource();
        chunkSource.addRegionTicket(DRONE_TICKET, pos, safeRadius, entityId);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.getChunk(pos.x + dx, pos.z + dz);
            }
        }

        ACTIVE_TICKETS.put(entityId, new TicketData(level, pos, safeRadius));
    }

    public static void releaseChunks(final ServerLevel level, final int entityId) {
        final TicketData existing = ACTIVE_TICKETS.remove(entityId);
        if (existing != null) {
            removeTicket(existing.level, existing.pos, existing.radius, entityId);
        }
    }

    public static void clearLevel(final ServerLevel level) {
        ACTIVE_TICKETS.entrySet().removeIf(entry -> {
            final TicketData data = entry.getValue();
            if (data.level != level) {
                return false;
            }
            removeTicket(data.level, data.pos, data.radius, entry.getKey());
            return true;
        });
    }

    public static void cleanupStaleTickets() {
        final long now = System.currentTimeMillis();
        ACTIVE_TICKETS.entrySet().removeIf(entry -> {
            final TicketData data = entry.getValue();
            if (now - data.lastUpdate <= STALE_THRESHOLD_MS) {
                return false;
            }
            removeTicket(data.level, data.pos, data.radius, entry.getKey());
            return true;
        });
    }

    private static void removeTicket(final ServerLevel level, final ChunkPos pos, final int radius, final int entityId) {
        if (level == null || pos == null) {
            return;
        }

        try {
            level.getChunkSource().removeRegionTicket(DRONE_TICKET, pos, radius, entityId);
        } catch (Throwable ignored) {
        }
    }

    private static final class TicketData {
        private final ServerLevel level;
        private final ChunkPos pos;
        private final int radius;
        private volatile long lastUpdate;

        private TicketData(final ServerLevel level, final ChunkPos pos, final int radius) {
            this.level = level;
            this.pos = pos;
            this.radius = radius;
            this.lastUpdate = System.currentTimeMillis();
        }
    }
}
