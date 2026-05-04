package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.UnsafeManager;
import com.radiance.client.option.Options;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.AuxiliaryTextureReloader;
import com.radiance.client.proxy.world.ChunkProxy;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.TimerQuery;
import com.mojang.blaze3d.pipeline.MainTarget;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftClientMixins {

    @Shadow
    @Final
    private Window window;

    @Shadow
    private ReloadableResourceManager resourceManager;

    //region <isAmbientOcclusionEnabled>
    @Inject(method = "isAmbientOcclusionEnabled()Z", at = @At(value = "HEAD"), cancellable = true)
    private static void disableAmbientOcclusion(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
    // endregion

    // region <init>
    @Redirect(method = "<init>(Lnet/minecraft/client/main/GameConfig;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;initRenderer(IZ)V"))
    public void initRenderer(int debugVerbosity, boolean debugSync) {
        long stackSize = 512 * 1024 * 1024; // 32MB
        Runnable myRunnable = () -> {
            RendererProxy.initRenderer(window);
            Pipeline.collectNativeModules();
        };

        Thread myThread = new Thread(null, myRunnable, "", stackSize);
        myThread.start();
        try {
            myThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Pipeline.loadPipeline();
        Pipeline.build();
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/main/GameConfig;)V",
        at = @At(value = "NEW", target = "com/mojang/blaze3d/pipeline/MainTarget"))
    public MainTarget cancelNewFramebuffer(int width, int height) {
        return UnsafeManager.INSTANCE.allocateInstance(MainTarget.class);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/main/GameConfig;)V",
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;resourceManager:Lnet/minecraft/server/packs/resources/ReloadableResourceManager;",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER))
    private void registerAuxiliaryTextureReloader(GameConfig args, CallbackInfo ci) {
        this.resourceManager.registerReloader(new AuxiliaryTextureReloader());
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/main/GameConfig;)V",
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;framebuffer:Lcom/mojang/blaze3d/pipeline/RenderTarget;",
            opcode = org.objectweb.asm.Opcodes.PUTFIELD))
    public void writeNullFramebuffer(Minecraft instance, RenderTarget value) {
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/main/GameConfig;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;setClearColor(FFFF)V"))
    public void cancelSetClearColor(RenderTarget instance, float r, float g, float b, float a) {

    }

    @Redirect(method = "<init>(Lnet/minecraft/client/main/GameConfig;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;clear()V"))
    public void cancelClear(RenderTarget instance) {

    }

    @Redirect(method = "<init>",
        at = @At(value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;textureWidth:I",
            ordinal = 0))
    public int redirectFramebufferTextureWidth(RenderTarget framebuffer) {
        return this.window.getFramebufferWidth();
    }

    @Redirect(method = "<init>",
        at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;textureHeight:I"),
        require = 0)
    public int redirectFramebufferTextureHeight(RenderTarget framebuffer) {
        return this.window.getFramebufferHeight();
    }
    // endregion

    // region <render>
    @Redirect(method = "render(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;beginWrite(Z)V"))
    public void cancelFramebufferBeginWrite(RenderTarget instance, boolean setViewport) {

    }

    @Redirect(method = "render(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;endWrite()V"))
    public void cancelFramebufferEndWrite(RenderTarget instance, boolean setViewport) {
        ChunkProxy.waitImportantChunkRebuild();
        synchronized (TextureProxy.class) {
            RendererProxy.submitCommandAndPresent();
            RendererProxy.acquireContext();
        }
    }

    @Redirect(method = "render(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;draw(II)V"))
    public void cancelFramebufferDraw(RenderTarget instance, int width, int height) {

    }

    @Redirect(method = "render(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"))
    public void disableFPSLimit(int fps) {

    }

    @Redirect(method = "render(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/TimerQuery;getInstance()Ljava/util/Optional;"))
    public Optional<TimerQuery> disableGLTimerInstance() {
        return Optional.empty();
    }
    // endregion

    // region <onResolutionChanged>
    @Redirect(method = "onResolutionChanged()V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;resize(II)V"))
    public void cancelFramebufferResize(RenderTarget instance, int width, int height) {

    }
    // endregion

    // region <close>
    @Inject(method = "close()V", at = @At(value = "HEAD"))
    public void cancelShaderLoaderClose(CallbackInfo ci) {
        Options.overwriteConfig();
    }
    //endregion

    // region <scheduleStop>
    @Inject(method = "scheduleStop()V", at = @At(value = "TAIL"))
    public void close(CallbackInfo ci) {
        RendererProxy.close();
    }
    // endregion

    // region <disconnect>
    @Redirect(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;render(Z)V"))
    public void cancelRenderAfterStop(Minecraft instance, boolean tick) {

    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V",
        at = @At(value = "HEAD"))
    public void resetBuiltChunkNum(Screen disconnectionScreen, boolean transferring,
        CallbackInfo ci) {
        ChunkProxy.builtChunkNum = 0;
    }
    // endregion
}
