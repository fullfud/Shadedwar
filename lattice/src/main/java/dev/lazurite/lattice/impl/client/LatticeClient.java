package dev.lazurite.lattice.impl.client;

import dev.lazurite.lattice.api.point.ViewPoint;
import dev.lazurite.lattice.impl.api.player.InternalLatticeLocalPlayer;
import dev.lazurite.lattice.impl.network.SetViewPointPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

public final class LatticeClient {

    public static void init() {

        MinecraftForge.EVENT_BUS.addListener(LatticeClient::onEntityJoinLevel);
        MinecraftForge.EVENT_BUS.addListener(LatticeClient::onClientTick);
    }

    private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof LocalPlayer localPlayer) {
            ((InternalLatticeLocalPlayer) localPlayer).setViewPointEntityId(localPlayer.getId());
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        final var localPlayer = Minecraft.getInstance().player;
        final var clientLevel = Minecraft.getInstance().level;
        if (localPlayer == null || clientLevel == null) {
            return;
        }

        final var internalLatticeLocalPlayer = (InternalLatticeLocalPlayer) localPlayer;
        final var localPlayerId = localPlayer.getId();
        final var viewPointEntityId = internalLatticeLocalPlayer.getViewPointEntityId();

        if (viewPointEntityId != localPlayerId) {
            final var viewPoint = internalLatticeLocalPlayer.getViewPoint();

            if (viewPoint instanceof Entity entity) {
                if (viewPointEntityId != entity.getId()) {
                    final var viewPointEntity = clientLevel.getEntity(viewPointEntityId);

                    if (viewPointEntity != null) {
                        Minecraft.getInstance().setCameraEntity(viewPointEntity);
                    }
                }
            } else {
                internalLatticeLocalPlayer.setViewPointEntityId(localPlayerId);
            }
        }
    }

    public static void handleSetViewPointPacket(SetViewPointPacket msg) {
        final var localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null) {
            return;
        }
        final var internalLatticeLocalPlayer = (InternalLatticeLocalPlayer) localPlayer;

        if (msg.isEntity()) {
            final var clientLevel = localPlayer.level();
            internalLatticeLocalPlayer.setViewPointEntityId(msg.getEntityId());

            final var entity = clientLevel.getEntity(msg.getEntityId());
            if (entity != null) {
                internalLatticeLocalPlayer.setViewPoint((ViewPoint) entity);
            }
        } else {
            // TODO handle non-entity viewPoints
            internalLatticeLocalPlayer.setViewPointEntityId(localPlayer.getId());
        }
    }
}
