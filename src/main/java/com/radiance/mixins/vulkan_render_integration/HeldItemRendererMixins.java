package com.radiance.mixins.vulkan_render_integration;

import com.google.common.base.MoreObjects;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IHeldItemRendererExt;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ItemInHandRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixins implements IHeldItemRendererExt {

    @Shadow
    private ItemStack mainHand;

    @Shadow
    private ItemStack offHand;

    @Shadow
    private float equipProgressMainHand;

    @Shadow
    private float prevEquipProgressMainHand;

    @Shadow
    private float equipProgressOffHand;

    @Shadow
    private float prevEquipProgressOffHand;

    @Shadow
    protected abstract void renderFirstPersonItem(AbstractClientPlayer player,
        float tickDelta,
        float pitch,
        InteractionHand hand,
        float swingProgress,
        ItemStack item,
        float equipProgress,
        PoseStack matrices,
        MultiBufferSource vertexConsumers,
        int light);

    @Override
    public void radiance$renderItem(float tickDelta,
        PoseStack matrices,
        MultiBufferSource vertexConsumers,
        LocalPlayer player,
        int light) {
        float f = player.getHandSwingProgress(tickDelta);
        InteractionHand hand = MoreObjects.firstNonNull(player.preferredHand, InteractionHand.MAIN_HAND);
        float g = player.getLerpedPitch(tickDelta);
        ItemInHandRenderer.HandRenderType handRenderType = ItemInHandRenderer.getHandRenderType(player);
        float h = Mth.lerp(tickDelta, player.lastRenderPitch, player.renderPitch);
        float i = Mth.lerp(tickDelta, player.lastRenderYaw, player.renderYaw);
        matrices.multiply(
            Axis.POSITIVE_X.rotationDegrees((player.getPitch(tickDelta) - h) * 0.1F));
        matrices.multiply(
            Axis.POSITIVE_Y.rotationDegrees((player.getYaw(tickDelta) - i) * 0.1F));
        if (handRenderType.renderMainHand) {
            float j = hand == InteractionHand.MAIN_HAND ? f : 0.0F;
            float k = 1.0F - Mth.lerp(tickDelta, this.prevEquipProgressMainHand,
                this.equipProgressMainHand);
            this.renderFirstPersonItem(player, tickDelta, g, InteractionHand.MAIN_HAND, j, this.mainHand, k,
                matrices, vertexConsumers, light);
        }

        if (handRenderType.renderOffHand) {
            float j = hand == InteractionHand.OFF_HAND ? f : 0.0F;
            float k = 1.0F - Mth.lerp(tickDelta, this.prevEquipProgressOffHand,
                this.equipProgressOffHand);
            this.renderFirstPersonItem(player, tickDelta, g, InteractionHand.OFF_HAND, j, this.offHand, k,
                matrices, vertexConsumers, light);
        }
    }
}
