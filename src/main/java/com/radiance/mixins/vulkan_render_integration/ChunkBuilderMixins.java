package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderExt;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SectionRenderDispatcher.class)
public class ChunkBuilderMixins implements IChunkBuilderExt {

    @Final
    @Shadow
    SectionCompiler sectionBuilder;

    @Final
    @Shadow
    SectionBufferBuilderPack buffers;

    @Shadow
    ClientLevel world;

    @Override
    public SectionCompiler radiance$getSectionBuilder() {
        return sectionBuilder;
    }

    @Override
    public ClientLevel radiance$getWorld() {
        return world;
    }

    @Override
    public SectionBufferBuilderPack radiance$getBuffers() {
        return buffers;
    }
}
