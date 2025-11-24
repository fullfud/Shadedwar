package com.fullfud.fullfud.core;

import com.fullfud.fullfud.FullfudMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class FullfudCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FullfudMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> FULLFUD_TAB = CREATIVE_MODE_TABS.register("fullfud_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.literal("Shaded war"))
                    .icon(() -> new ItemStack(FullfudRegistries.SHAHED_ITEM.get()))
                    .displayItems((pParameters, pOutput) -> {
                        FullfudRegistries.ITEMS.getEntries().forEach(regObj -> {
                            pOutput.accept(regObj.get());
                        });
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}