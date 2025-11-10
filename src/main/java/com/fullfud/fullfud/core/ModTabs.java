package com.fullfud.fullfud.core;

import com.fullfud.fullfud.FullfudMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModTabs {
    private ModTabs() {
    }

    private static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FullfudMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> FULLFUD_TAB = TABS.register("main", () ->
        CreativeModeTab.builder()
            .title(Component.translatable("creativetab.fullfud"))
            .icon(() -> new ItemStack(FullfudRegistries.SHAHED_ITEM.get()))
            .displayItems((parameters, output) -> {
                output.accept(FullfudRegistries.SHAHED_ITEM.get());
                output.accept(FullfudRegistries.MONITOR_ITEM.get());
                output.accept(FullfudRegistries.FPV_DRONE_ITEM.get());
                output.accept(FullfudRegistries.FPV_CONTROLLER_ITEM.get());
                output.accept(FullfudRegistries.FPV_GOGGLES_ITEM.get());
            })
            .build()
    );

    public static void register(final IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
