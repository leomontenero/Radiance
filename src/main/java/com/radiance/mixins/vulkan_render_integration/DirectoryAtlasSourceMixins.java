package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.texture.AuxiliaryTextures;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.renderer.texture.atlas.SpriteSource.SpriteRegions;
import net.minecraft.client.renderer.texture.atlas.sources.DirectoryLister;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DirectoryLister.class)
public class DirectoryAtlasSourceMixins {

    @Final
    @Shadow
    private String source;

    @Final
    @Shadow
    private String prefix;

    @Inject(method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/client/renderer/texture/atlas/SpriteSource$Output;)V", at = @At(value = "HEAD"), cancellable = true)
    public void cancelPBRLoad(ResourceManager resourceManager, SpriteRegions regions,
        CallbackInfo ci) {
        FileToIdConverter resourceFinder = new FileToIdConverter("textures/" + this.source, ".png");
        Map<Identifier, Resource> resources = resourceFinder.findResources(resourceManager);
        for (Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier identifier = entry.getKey();
            Resource resource = entry.getValue();

            Identifier identifier2 = resourceFinder.toResourceId(identifier)
                .withPrefixedPath(this.prefix);
            if (AuxiliaryTextures.shouldSkipAtlasSprite(resourceManager, identifier2)) {
                continue;
            }
            regions.add(identifier2, resource);
        }

        ci.cancel();
    }
}
