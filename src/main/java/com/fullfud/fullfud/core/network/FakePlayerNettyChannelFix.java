package com.fullfud.fullfud.core.network;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class FakePlayerNettyChannelFix {

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
        setConnectionChannel(connection, channel);
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
