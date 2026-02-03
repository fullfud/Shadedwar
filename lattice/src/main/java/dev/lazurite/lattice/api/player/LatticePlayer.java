package dev.lazurite.lattice.api.player;

import dev.lazurite.lattice.api.point.ViewPoint;

public interface LatticePlayer {
    ViewPoint getViewPoint();
    boolean isViewPointSelf();
}
