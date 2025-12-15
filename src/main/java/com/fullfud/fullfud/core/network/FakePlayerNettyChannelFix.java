package com.fullfud.fullfud.core.network;

import com.mojang.logging.LogUtils;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FakePlayerNettyChannelFix {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean warned = new AtomicBoolean(false);

    private FakePlayerNettyChannelFix() { }

    public static void ensureChannelPresent(final ServerPlayer player) {
        if (player == null) {
            return;
        }
        final Object listener = player.connection;
        if (listener == null) {
            return;
        }

        final Connection connection = extractConnection(listener);
        if (connection == null) {
            return;
        }

        try {
            if (connection.channel() != null) {
                return;
            }
        } catch (Throwable ignored) {
            return;
        }

        final Channel channel = new EmbeddedChannel();
        if (!setConnectionChannel(connection, channel) && warned.compareAndSet(false, true)) {
            LOGGER.warn("[FULLFUD] Failed to patch fake player Connection.channel (mods may crash when treating FakePlayer as real)");
        }
    }

    private static Connection extractConnection(final Object packetListener) {
        try {
            final Method method = packetListener.getClass().getDeclaredMethod("getConnection");
            if (Connection.class.isAssignableFrom(method.getReturnType()) && method.getParameterCount() == 0) {
                method.setAccessible(true);
                final Object value = method.invoke(packetListener);
                if (value instanceof Connection connection) {
                    return connection;
                }
            }
        } catch (Throwable ignored) {
        }

        for (Class<?> c = packetListener.getClass(); c != null; c = c.getSuperclass()) {
            for (final Field field : c.getDeclaredFields()) {
                if (!Connection.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    final Object value = field.get(packetListener);
                    if (value instanceof Connection connection) {
                        return connection;
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        return null;
    }

    private static boolean setConnectionChannel(final Connection connection, final Channel channel) {
        for (Class<?> c = connection.getClass(); c != null; c = c.getSuperclass()) {
            for (final Field field : c.getDeclaredFields()) {
                if (!Channel.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    final Object current = field.get(connection);
                    if (current != null) {
                        return true;
                    }
                    field.set(connection, channel);
                    return true;
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }
}

