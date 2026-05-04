package com.radiance.mixin_related.extensions.vulkan_render_integration;

import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.multiplayer.ClientLevel;

public interface IChunkBuilderExt {

    SectionCompiler radiance$getSectionBuilder();

    ClientLevel radiance$getWorld();

    SectionBufferBuilderPack radiance$getBuffers();
}
