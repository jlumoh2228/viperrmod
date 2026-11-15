package com.viperr.concert.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.viperr.concert.ViperrMod;
import com.viperr.concert.entity.PerformerEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class PerformerRenderer extends MobRenderer<PerformerEntity, PlayerModel<PerformerEntity>> {
    private final ResourceLocation textureLocation;

    public PerformerRenderer(EntityRendererProvider.Context context, String texturePath) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 1.2F);
        this.textureLocation = new ResourceLocation(ViperrMod.MOD_ID, texturePath);
    }

    @Override
    public ResourceLocation getTextureLocation(PerformerEntity entity) {
        return this.textureLocation;
    }

    @Override
    protected void scale(PerformerEntity entity, PoseStack poseStack, float partialTicks) {
        // Делаем артистов гигантскими (в 2.5 раза больше обычного игрока)
        poseStack.scale(2.5F, 2.5F, 2.5F);
    }

    @Override
    protected void setupRotations(PerformerEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);
        int hype = entity.getHypeLevel();
        float speed = hype == 3 ? 0.75F : (hype == 2 ? 0.55F : 0.42F);
        float amp = hype == 3 ? 0.85F : (hype == 2 ? 0.65F : 0.50F);

        float beat = Mth.sin(ageInTicks * speed);
        float bounce = Math.abs(Mth.sin(ageInTicks * speed * 0.5F));

        poseStack.translate(0.0D, bounce * 0.12D * hype, 0.0D);
        poseStack.mulPose(Axis.ZP.rotationDegrees(beat * 9.0F * amp));

        this.model.head.xRot = beat * amp * 0.8F;
        this.model.head.yRot = Mth.cos(ageInTicks * speed * 0.5F) * 0.4F;
        this.model.rightArm.xRot = -1.6F + beat * amp * 1.6F;
        this.model.leftArm.xRot = -1.6F - beat * amp * 1.6F;
        this.model.rightArm.zRot = 0.35F + Mth.cos(ageInTicks * speed) * 0.35F;
        this.model.leftArm.zRot = -0.35F - Mth.cos(ageInTicks * speed) * 0.35F;
    }
}
