package com.fullfud.fullfud;

import com.fullfud.fullfud.client.FpvClientHandler;
import com.fullfud.fullfud.client.ShahedClientHandler; 
import com.fullfud.fullfud.core.FullfudCreativeTabs;
import com.fullfud.fullfud.core.FullfudRegistries;
import com.fullfud.fullfud.core.network.FullfudNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import software.bernie.geckolib.GeckoLib;

@Mod(FullfudMod.MOD_ID)
public class FullfudMod {
    public static final String MOD_ID = "fullfud";
    private static final Logger LOGGER = LogUtils.getLogger();

    public FullfudMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        GeckoLib.initialize();

        FullfudRegistries.register(modEventBus);
        FullfudCreativeTabs.register(modEventBus);

        modEventBus.addListener(this::onCommonSetup);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            modEventBus.addListener(this::onClientSetup);
            
            FpvClientHandler.registerClientEvents(modEventBus);
            ShahedClientHandler.registerClientEvents(modEventBus);
        });
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FullfudNetwork.init();
        });
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        FpvClientHandler.onClientSetup(event);
        ShahedClientHandler.onClientSetup(event);
    }
}