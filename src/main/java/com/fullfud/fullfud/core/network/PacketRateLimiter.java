package com.fullfud.fullfud.core.network;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class PacketRateLimiter {
    private static final Map<Key, State> STATES = new HashMap<>();
    private static long lastCleanupTick;

    private PacketRateLimiter() { }

    public static boolean allow(final ServerPlayer sender, final String key, final int maxPerTick, final int maxPerSecond) {
        if (sender == null || sender.serverLevel() == null) {
            return false;
        }
        if (key == null || key.isBlank()) {
            return false;
        }
        if (maxPerTick <= 0 || maxPerSecond <= 0) {
            return false;
        }

        final long tick = sender.serverLevel().getGameTime();
        cleanupIfNeeded(tick);

        final Key k = new Key(sender.getUUID(), key);
        State state = STATES.get(k);
        if (state == null) {
            state = new State();
            state.windowStartTick = tick;
            state.lastTick = tick;
            STATES.put(k, state);
        }

        state.lastSeenTick = tick;

        if (tick != state.lastTick) {
            state.lastTick = tick;
            state.countThisTick = 0;
        }

        if (tick - state.windowStartTick >= 20) {
            state.windowStartTick = tick;
            state.countThisWindow = 0;
        }

        if (state.countThisTick >= maxPerTick) {
            return false;
        }
        if (state.countThisWindow >= maxPerSecond) {
            return false;
        }

        state.countThisTick++;
        state.countThisWindow++;
        return true;
    }

    private static void cleanupIfNeeded(final long currentTick) {
        if (currentTick - lastCleanupTick < 200) {
            return;
        }
        lastCleanupTick = currentTick;

        final long expiry = 20L * 60L;
        final Iterator<Map.Entry<Key, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<Key, State> entry = it.next();
            if (currentTick - entry.getValue().lastSeenTick > expiry) {
                it.remove();
            }
        }
    }

    private record Key(UUID playerId, String key) { }

    private static final class State {
        long lastTick;
        int countThisTick;
        long windowStartTick;
        int countThisWindow;
        long lastSeenTick;
    }
}

