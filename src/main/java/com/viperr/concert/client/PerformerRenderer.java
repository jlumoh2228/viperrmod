package com.viperr.concert.client;

import com.mojang.blaze3d.vertex.PoseStack;
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
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.textureLocation = new ResourceLocation(ViperrMod.MOD_ID, texturePath);
    }

    @Override
    public ResourceLocation getTextureLocation(PerformerEntity entity) {
        return this.textureLocation;
    }

    @Override
    protected void setupRotations(PerformerEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);
        int hype = entity.getHypeLevel();
        float speed = hype == 3 ? 0.65F : (hype == 2 ? 0.45F : 0.25F);
        float amp = hype == 3 ? 0.55F : (hype == 2 ? 0.35F : 0.15F);

        float beat = Mth.sin(ageInTicks * speed);
        this.model.head.xRot = beat * amp * 0.6F;
        this.model.rightArm.xRot = -1.2F + beat * amp * 1.4F;
        this.model.leftArm.xRot = -1.2F - beat * amp * 1.4F;
        this.model.rightArm.zRot = 0.25F + Mth.cos(ageInTicks * speed) * 0.2F;
        this.model.leftArm.zRot = -0.25F - Mth.cos(ageInTicks * speed) * 0.2F;
    }
}
