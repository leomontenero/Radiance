package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.sugar.Local;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGameRendererExt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.ProjectionType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixins implements IGameRendererExt {

    @Shadow
    @Final
    public ItemInHandRenderer firstPersonRenderer;
    @Mutable
    @Final
    @Shadow
    private LightTexture lightmapTextureManager;
    @Final
    @Shadow
    private Minecraft client;
    @Final
    @Shadow
    private CrossFrameResourcePool pool;
    @Shadow
    @Final
    private RenderBuffers buffers;
    @Shadow
    @Final
    private Camera camera;
    @Unique
    private Matrix4f viewMatrix;

    @Shadow
    public abstract Matrix4f getBasicProjectionMatrix(float fovDegrees);

    @Shadow
    protected abstract float getFov(Camera camera, float tickDelta, boolean changingFov);

    @Inject(method = "renderBlur()V", at = @At(value = "HEAD"), cancellable = true)
    public void redirectRenderBlur(CallbackInfo ci) {
        float f = this.client.options.getMenuBackgroundBlurrinessValue();

        //if (this.client.world == null && this.client.currentScreen != null && !(f < 1.0F)) {
        if (!(f < 1.0F)) {
            BufferProxy.updateOverlayPostUniform(f);
            RendererProxy.postBlur();
        }

        ci.cancel();
    }

    @Redirect(method = "renderWorld(Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;mul(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;", remap = false))
    public Matrix4f cancelPTimesB(Matrix4f instance, Matrix4fc right) {
        return instance;
    }

    @Redirect(method = "renderWorld(Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;"
                    +
                    "Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;"
                    +
                    "Lnet/minecraft/client/renderer/GameRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    public void performBTimesV(LevelRenderer instance,
        GraphicsResourceAllocator allocator,
        DeltaTracker tickCounter,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        Matrix4f viewMatrix,
        Matrix4f projectionMatrix,
        @Local boolean shouldRenderBlockOutline,
        @Local PoseStack matrixStack) {
        Matrix4f
            B =
            new Matrix4f(matrixStack.peek()
                .getPositionMatrix());
        this.viewMatrix = new Matrix4f(viewMatrix);
        viewMatrix = new Matrix4f(B.mul(viewMatrix));
        instance.render(this.pool, tickCounter, shouldRenderBlockOutline, camera, gameRenderer,
            viewMatrix, projectionMatrix);
    }

    @Inject(method = "renderWorld(Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "TAIL"))
    public void buildEntities(DeltaTracker renderTickCounter, CallbackInfo ci) {
        EntityProxy.build();
    }

    @Redirect(method = "renderWorld(Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;beginWrite(Z)V"))
    public void cancelFramebufferBeginWrite(RenderTarget instance, boolean setViewport) {

    }

    @Inject(method = "renderWorld(Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "TAIL"))
    public void fuseWorld(DeltaTracker renderTickCounter, CallbackInfo ci) {
        RendererProxy.fuseWorld();
    }

    @Inject(method = "renderHand(Lnet/minecraft/client/Camera;FLorg/joml/Matrix4f;)V", at = @At(value = "HEAD"), cancellable = true)
    public void redirectRenderHand(Camera camera, float tickDelta, Matrix4f matrix4f,
        CallbackInfo ci) {
        float worldFov = this.getFov(camera, tickDelta, true);
        float handFov = this.getFov(camera, tickDelta, false);
        float handProjectionScale =
            (float) (Math.tan(Math.toRadians(worldFov * 0.5F)) /
                Math.tan(Math.toRadians(handFov * 0.5F)));
        EntityProxy.queueHandRebuild(buffers, tickDelta, firstPersonRenderer,
            handProjectionScale);
        ci.cancel();
    }

    @Redirect(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;beginWrite(Z)V"))
    public void cancelRenderFramebufferBeginWrite(RenderTarget instance, boolean setViewport) {

    }

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At(value = "HEAD"))
    public void shouldRenderWorld(DeltaTracker tickCounter, boolean tick, CallbackInfo ci) {
        RendererProxy.shouldRenderWorld(
            !this.client.skipGameRender && client.isFinishedLoading() && tick
                && client.world != null);
    }

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;"
                    + "Lnet/minecraft/client/DeltaTracker;)V"))
    public void renderFirstPersonOverlaysWithGuiProjection(DeltaTracker tickCounter,
        boolean tick, CallbackInfo ci, @Local GuiGraphics drawContext) {
        float tickDelta = tickCounter.getTickDelta(true);
        com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix();
        com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(
            this.getBasicProjectionMatrix(this.getFov(this.camera, tickDelta, false)),
            ProjectionType.PERSPECTIVE);
        Matrix4fStack modelViewStack = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        MultiBufferSource.Immediate immediate = MultiBufferSource.immediate(
            new ByteBufferBuilder(1536));
        try {
            ScreenEffectRenderer.renderOverlays(this.client, new PoseStack(), immediate);
            immediate.draw();
        } finally {
            modelViewStack.popMatrix();
            com.mojang.blaze3d.systems.RenderSystem.restoreProjectionMatrix();
        }
    }

    @Override
    public Matrix4f radiance$getRotationMatrix() {
        return viewMatrix;
    }

    @Redirect(method = "updateWorldIcon(Ljava/nio/file/Path;)V",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/Screenshot;takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;)"
                    +
                    "Lcom/mojang/blaze3d/platform/NativeImage;"))
    public NativeImage redirectScreenshot(RenderTarget framebuffer) {
        return RendererProxy.takeScreenshotWithoutUI();
    }
}
