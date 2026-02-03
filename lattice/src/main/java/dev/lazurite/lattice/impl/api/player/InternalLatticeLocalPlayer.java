package dev.lazurite.lattice.impl.api.player;

import dev.lazurite.lattice.api.player.LatticeLocalPlayer;
import dev.lazurite.lattice.api.point.ViewPoint;

public interface InternalLatticeLocalPlayer extends LatticeLocalPlayer {
    void setViewPointEntityId(final int id);
    int getViewPointEntityId();

    void setViewPoint(final ViewPoint viewPoint);
}
