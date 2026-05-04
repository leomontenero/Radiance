package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.util.TriState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderType.class)
public class RenderLayerMixins {

    @Shadow
    @Final
    @Mutable
    private static RenderType LIGHTNING;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void replaceLightning(CallbackInfo ci) {
        LIGHTNING =
            RenderType.of("lightning",
                DefaultVertexFormat.POSITION_TEXTURE_COLOR,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                RenderType.MultiPhaseParameters.builder()
                    .program(RenderType.LIGHTNING_PROGRAM)
                    .writeMaskState(RenderType.ALL_MASK)
                    .transparency(RenderType.LIGHTNING_TRANSPARENCY)
                    .target(RenderType.WEATHER_TARGET)
                    .texture(new RenderStateShard.Texture(
                        Identifier.ofVanilla("textures/block/lightning.png"),
                        TriState.FALSE,
                        false))
                    .build(false));
    }
}
