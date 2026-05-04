package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.vertex.PBRVertexConsumer;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.Sheets;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemRenderer.class)
public class ItemRendererMixins {

    @Inject(method =
        "getArmorGlintConsumer(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;"
            +
            "Z)Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At(value = "HEAD"), cancellable = true)
    private static void redirectGetArmorGlintConsumer(MultiBufferSource provider,
        RenderType layer,
        boolean glint,
        CallbackInfoReturnable<VertexConsumer> cir) {
        VertexConsumer vertexConsumer = provider.getBuffer(layer);

        if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
            if (glint) {
                cir.setReturnValue(new PBRVertexConsumer.GLint(pbrVertexConsumer,
                    RenderType.getArmorEntityGlint()));
            } else {
                cir.setReturnValue(vertexConsumer);
            }
        } else {
            if (glint) {
                cir.setReturnValue(
                    VertexMultiConsumer.union(provider.getBuffer(RenderType.getArmorEntityGlint()),
                        vertexConsumer));
            } else {
                cir.setReturnValue(vertexConsumer);
            }
        }
    }

    @Inject(method =
        "getDynamicDisplayGlintConsumer(Lnet/minecraft/client/renderer/MultiBufferSource;" +
            "Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)"
            +
            "Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At(value = "HEAD"), cancellable = true)
    private static void redirectGetDynamicDisplayGlintConsumer(MultiBufferSource provider,
        RenderType layer,
        PoseStack.Entry entry,
        CallbackInfoReturnable<VertexConsumer> cir) {
        VertexConsumer vertexConsumer = provider.getBuffer(layer);

        if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
            cir.setReturnValue(
                new PBRVertexConsumer.GLintOverlay(pbrVertexConsumer, RenderType.getGlint(), entry,
                    0.0078125F));
        } else {
            cir.setReturnValue(VertexMultiConsumer.union(
                new SheetedDecalTextureGenerator(provider.getBuffer(RenderType.getGlint()),
                    entry,
                    0.0078125F), vertexConsumer));
        }
    }

    @Inject(method =
        "getItemGlintConsumer(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;"
            +
            "ZZ)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
        at = @At(value = "HEAD"),
        cancellable = true)
    private static void redirectGetItemGlintConsumer(MultiBufferSource vertexConsumers,
        RenderType layer,
        boolean solid,
        boolean glint,
        CallbackInfoReturnable<VertexConsumer> cir) {
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(layer);

        if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
            if (glint) {
                RenderType
                    glintRenderLayer =
                    Minecraft.isFabulousGraphicsOrBetter()
                        && layer == Sheets.getItemEntityTranslucentCull() ?
                        RenderType.getGlintTranslucent()
                        : (solid ? RenderType.getGlint() : RenderType.getEntityGlint());

                cir.setReturnValue(
                    new PBRVertexConsumer.GLint(pbrVertexConsumer, glintRenderLayer));
            } else {
                cir.setReturnValue(vertexConsumer);
            }
        } else {
            if (glint) {
                cir.setReturnValue(
                    Minecraft.isFabulousGraphicsOrBetter()
                        && layer == Sheets.getItemEntityTranslucentCull() ?
                        VertexMultiConsumer.union(
                            vertexConsumers.getBuffer(RenderType.getGlintTranslucent()),
                            vertexConsumer) :
                        VertexMultiConsumer.union(vertexConsumers.getBuffer(
                                solid ? RenderType.getGlint() : RenderType.getEntityGlint()),
                            vertexConsumer));
            } else {
                cir.setReturnValue(vertexConsumer);
            }
        }
    }
}
