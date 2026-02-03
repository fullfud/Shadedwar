package dev.lazurite.lattice.api.player;

import dev.lazurite.lattice.api.point.ViewPoint;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public interface LatticeServerPlayer extends LatticePlayer {
    void setViewPoint(final ViewPoint viewPoint);
    void removeViewPoint();

    /**
     * Set's the {@link ServerPlayer#camera} to {@code entity}.
     * Doesn't prefer {@code entity}'s {@link ViewPoint}.
     */
    void setCameraWithoutViewPoint(final Entity entity);
}
