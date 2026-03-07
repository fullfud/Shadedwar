package com.fullfud.fullfud.client.render;

import com.fullfud.fullfud.common.entity.PlayerDecoyEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.client.renderer.entity.layers.BeeStingerLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.SpinAttackEffectLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDecoyRenderer extends LivingEntityRenderer<PlayerDecoyEntity, PlayerModel<PlayerDecoyEntity>> {
    private static final Map<UUID, ResourceLocation> SKIN_CACHE = new ConcurrentHashMap<>();

    public PlayerDecoyRenderer(final EntityRendererProvider.Context context) {
        this(context, false);
    }

    public PlayerDecoyRenderer(final EntityRendererProvider.Context context, final boolean slim) {
        super(context, new PlayerModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(
            this,
            new HumanoidModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR)),
            context.getModelManager()
        ));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new ArrowLayer<>(context, this));
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        this.addLayer(new ElytraLayer<>(this, context.getModelSet()));
        this.addLayer(new SpinAttackEffectLayer<>(this, context.getModelSet()));
        this.addLayer(new BeeStingerLayer<>(this));
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(final @NotNull PlayerDecoyEntity entity) {
        final GameProfile profile = entity.getGameProfile();
        if (profile == null || profile.getId() == null) {
            return DefaultPlayerSkin.getDefaultSkin();
        }

        final UUID uuid = profile.getId();
        final ResourceLocation cached = SKIN_CACHE.get(uuid);
        if (cached != null) {
            return cached;
        }

        final Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            final PlayerInfo playerInfo = mc.getConnection().getPlayerInfo(uuid);
            if (playerInfo != null) {
                final ResourceLocation skin = playerInfo.getSkinLocation();
                SKIN_CACHE.put(uuid, skin);
                return skin;
            }
        }

        final SkinManager skinManager = mc.getSkinManager();
        skinManager.registerSkins(profile, (type, location, texture) -> {
            if (type == MinecraftProfileTexture.Type.SKIN) {
                SKIN_CACHE.put(uuid, location);
            }
        }, true);
        return DefaultPlayerSkin.getDefaultSkin(uuid);
    }

    @Override
    public void render(final @NotNull PlayerDecoyEntity entity, final float entityYaw, final float partialTicks, final @NotNull PoseStack poseStack, final @NotNull MultiBufferSource buffer, final int packedLight) {
        setModelProperties(entity);
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private void setModelProperties(final PlayerDecoyEntity entity) {
        final PlayerModel<PlayerDecoyEntity> model = this.getModel();
        model.setAllVisible(true);
        final byte modelParts = entity.getPlayerModelParts();
        model.hat.visible = (modelParts & 1) != 0;
        model.jacket.visible = (modelParts & 2) != 0;
        model.leftPants.visible = (modelParts & 4) != 0;
        model.rightPants.visible = (modelParts & 8) != 0;
        model.leftSleeve.visible = (modelParts & 16) != 0;
        model.rightSleeve.visible = (modelParts & 32) != 0;
        model.crouching = entity.isDecoyCrouching();
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        model.rightArmPose = HumanoidModel.ArmPose.EMPTY;

        if (!entity.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
            model.rightArmPose = HumanoidModel.ArmPose.ITEM;
        }
        if (!entity.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
            model.leftArmPose = HumanoidModel.ArmPose.ITEM;
        }
    }

    @Override
    protected boolean shouldShowName(final @NotNull PlayerDecoyEntity entity) {
        return false;
    }
}
