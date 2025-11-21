package com.fullfud.fullfud.core;

import com.fullfud.fullfud.FullfudMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class FullfudCreativeTabs {
    private FullfudCreativeTabs() {
    }

    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FullfudMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> FULLFUD = CREATIVE_TABS.register("main", () ->
        CreativeModeTab.builder()
            .title(Component.literal("Shaded war"))
            .icon(() -> new ItemStack(FullfudRegistries.SHAHED_ITEM.get()))
            .displayItems((parameters, output) ->
                FullfudRegistries.ITEMS.getEntries().stream()
                    .map(RegistryObject::get)
                    .forEach(output::accept)
            )
            .build()
    );

    public static void register(final IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }
}