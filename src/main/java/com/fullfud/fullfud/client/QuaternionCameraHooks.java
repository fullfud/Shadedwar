package com.fullfud.fullfud.client;

import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import com.fullfud.fullfud.common.item.FpvGogglesItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

public final class QuaternionCameraHooks {
    private QuaternionCameraHooks() {
    }

    public static boolean applyDroneCamera(final PoseStack poseStack, final Camera camera, final float partialTick) {
        if (poseStack == null || camera == null) {
            return false;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return false;
        }

        final FpvDroneEntity drone;
        if (camera.getEntity() instanceof FpvDroneEntity cameraDrone) {
            drone = cameraDrone;
        } else {
            drone = FpvClientHandler.resolveActiveControlledDrone(minecraft);
        }
        if (drone == null) {
            return false;
        }

        final UUID controller = drone.getControllerId();
        if (controller == null || !controller.equals(minecraft.player.getUUID())) {
            return false;
        }
        if (!hasLinkedGoggles(minecraft, drone)) {
            return false;
        }

        final Quaternionf cameraQuaternion = FpvClientHandler.resolveRenderCameraQuaternion(drone, partialTick);
        final Vector3f forward = new Vector3f(0.0F, 0.0F, 1.0F);
        final Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
        final Vector3f localX = new Vector3f(1.0F, 0.0F, 0.0F);
        cameraQuaternion.transform(forward);
        cameraQuaternion.transform(up);
        cameraQuaternion.transform(localX);

        final Matrix3f viewMatrix = new Matrix3f();
        viewMatrix.setColumn(0, -localX.x, -localX.y, -localX.z);
        viewMatrix.setColumn(1, up.x, up.y, up.z);
        viewMatrix.setColumn(2, -forward.x, -forward.y, -forward.z);

        final Quaternionf poseRotation = viewMatrix.getNormalizedRotation(new Quaternionf());
        poseStack.mulPose(poseRotation);

        camera.rotation().set(cameraQuaternion);
        camera.getLookVector().set(forward);
        camera.getUpVector().set(up);
        camera.getLeftVector().set(localX);
        return true;
    }

    private static boolean hasLinkedGoggles(final Minecraft minecraft, final FpvDroneEntity drone) {
        if (minecraft == null || minecraft.player == null || drone == null) {
            return false;
        }
        final ItemStack head = minecraft.player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(head.getItem() instanceof FpvGogglesItem)) {
            return false;
        }
        return FpvGogglesItem.getLinked(head).map(drone.getUUID()::equals).orElse(true);
    }
}
