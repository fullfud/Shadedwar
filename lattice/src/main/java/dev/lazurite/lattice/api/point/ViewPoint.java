package dev.lazurite.lattice.api.point;

public interface ViewPoint extends Point3D {
    float getYRot();
    float getXRot();

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    default boolean shouldRenderPlayer() {
        return false;
    }

    default boolean unregistersWithNoViewers() {
        return true;
    }

}
