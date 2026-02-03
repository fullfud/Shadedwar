package dev.lazurite.lattice.impl;

import dev.lazurite.lattice.impl.client.LatticeClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod(LatticeCommon.MOD_ID)
public final class LatticeCommon {

    public static final String MOD_ID = "lattice";

    public LatticeCommon() {
        Networking.init();
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> LatticeClient::init);
    }
}
