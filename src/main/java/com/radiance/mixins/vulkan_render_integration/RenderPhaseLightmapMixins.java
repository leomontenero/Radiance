package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderStateShard.Lightmap.class)
public class RenderPhaseLightmapMixins extends RenderPhaseMixins {

    @Inject(method = "<init>(Z)V", at = @At(value = "TAIL"))
    public void resetActionToDoNothing(boolean lightmap, CallbackInfo ci) {
        if (!lightmap) {
            setBeginAction(() -> {
            });
            setEndAction(() -> {
            });
            return;
        }

        setBeginAction(() -> Minecraft.getInstance()
            .gameRenderer
            .getLightmapTextureManager()
            .enable());
        setEndAction(() -> Minecraft.getInstance()
            .gameRenderer
            .getLightmapTextureManager()
            .disable());
    }
}
