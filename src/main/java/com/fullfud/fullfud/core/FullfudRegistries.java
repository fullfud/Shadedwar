package com.fullfud.fullfud.core;

import com.fullfud.fullfud.FullfudMod;
import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import com.fullfud.fullfud.common.entity.RebEmitterEntity;
import com.fullfud.fullfud.common.entity.ShahedColor;
import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import com.fullfud.fullfud.common.entity.ShahedLauncherEntity;
import com.fullfud.fullfud.common.item.MonitorItem;
import com.fullfud.fullfud.common.item.RebBatteryItem;
import com.fullfud.fullfud.common.item.RebEmitterItem;
import com.fullfud.fullfud.common.item.ShahedLauncherItem;
import com.fullfud.fullfud.common.item.ShahedDroneItem;
import com.fullfud.fullfud.common.item.FpvDroneItem;
import com.fullfud.fullfud.common.item.FpvControllerItem;
import com.fullfud.fullfud.common.item.FpvGogglesItem;
import com.fullfud.fullfud.common.menu.ShahedMonitorMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class FullfudRegistries {
    private FullfudRegistries() {
    }

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FullfudMod.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, FullfudMod.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES, FullfudMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, FullfudMod.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, FullfudMod.MOD_ID);

    public static final RegistryObject<Item> MONITOR_ITEM = ITEMS.register("monitor_control", () ->
        new MonitorItem(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> SHAHED_ITEM = ITEMS.register("shahed_136", () ->
        new ShahedDroneItem(new Item.Properties().stacksTo(1), ShahedColor.WHITE)
    );

    public static final RegistryObject<Item> SHAHED_BLACK_ITEM = ITEMS.register("shahed_136_black", () ->
        new ShahedDroneItem(new Item.Properties().stacksTo(1), ShahedColor.BLACK)
    );

    public static final RegistryObject<Item> FPV_DRONE_ITEM = ITEMS.register("fpv_drone", () ->
        new FpvDroneItem(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> FPV_CONTROLLER_ITEM = ITEMS.register("fpv_controller", () ->
        new FpvControllerItem(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> FPV_GOGGLES_ITEM = ITEMS.register("fpv_goggles", () ->
        new FpvGogglesItem(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> SHAHED_LAUNCHER_ITEM = ITEMS.register("shahed_launcher", () ->
        new ShahedLauncherItem(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> REB_EMITTER_ITEM = ITEMS.register("reb_emitter", () ->
        new RebEmitterItem(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> REB_BATTERY_ITEM = ITEMS.register("reb_battery", () ->
        new RebBatteryItem(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<EntityType<ShahedDroneEntity>> SHAHED_ENTITY = ENTITY_TYPES.register("shahed_136", () ->
        EntityType.Builder.<ShahedDroneEntity>of(ShahedDroneEntity::new, MobCategory.MISC)
            .sized(3.0F, 1.0F)
            .clientTrackingRange(256)
            .updateInterval(1)
            .setShouldReceiveVelocityUpdates(true)
            .build(resource("shahed_136").toString())
    );

    public static final RegistryObject<EntityType<FpvDroneEntity>> FPV_DRONE_ENTITY = ENTITY_TYPES.register("fpv_drone", () ->
        EntityType.Builder.<FpvDroneEntity>of(FpvDroneEntity::new, MobCategory.MISC)
            .sized(0.9F, 0.35F)
            .clientTrackingRange(256)
            .updateInterval(1)
            .setShouldReceiveVelocityUpdates(true)
            .build(resource("fpv_drone").toString())
    );

    public static final RegistryObject<EntityType<ShahedLauncherEntity>> SHAHED_LAUNCHER_ENTITY = ENTITY_TYPES.register("shahed_launcher", () ->
        EntityType.Builder.<ShahedLauncherEntity>of(ShahedLauncherEntity::new, MobCategory.MISC)
            .sized(1.0F, 1.0F)
            .clientTrackingRange(64)
            .updateInterval(10)
            .build(resource("shahed_launcher").toString())
    );

    public static final RegistryObject<EntityType<RebEmitterEntity>> REB_EMITTER_ENTITY = ENTITY_TYPES.register("reb_emitter", () ->
        EntityType.Builder.<RebEmitterEntity>of(RebEmitterEntity::new, MobCategory.MISC)
            .sized(1.0F, 1.0F)
            .clientTrackingRange(64)
            .updateInterval(10)
            .build(resource("reb_emitter").toString())
    );

    public static final RegistryObject<MenuType<ShahedMonitorMenu>> SHAHED_MONITOR_MENU = MENU_TYPES.register("shahed_monitor", () ->
        IForgeMenuType.create(ShahedMonitorMenu::new)
    );
    public static final RegistryObject<SoundEvent> SHAHED_ENGINE_START = SOUND_EVENTS.register("shahed.engine_start",
        () -> SoundEvent.createVariableRangeEvent(resource("shahed.engine_start"))
    );
    public static final RegistryObject<SoundEvent> SHAHED_ENGINE_LOOP = SOUND_EVENTS.register("shahed.engine_loop",
        () -> SoundEvent.createVariableRangeEvent(resource("shahed.engine_loop"))
    );
    public static final RegistryObject<SoundEvent> SHAHED_ENGINE_END = SOUND_EVENTS.register("shahed.engine_end",
        () -> SoundEvent.createVariableRangeEvent(resource("shahed.engine_end"))
    );
    public static final RegistryObject<SoundEvent> REB_STATIC_NOISE = SOUND_EVENTS.register("reb.static",
        () -> SoundEvent.createVariableRangeEvent(resource("reb.static"))
    );

    public static void register(final IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
    }

    private static ResourceLocation resource(final String name) {
        return new ResourceLocation(FullfudMod.MOD_ID, name);
    }
}
