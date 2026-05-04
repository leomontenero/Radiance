package com.radiance.client.texture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;

public class AuxiliaryTextureReloader implements PreparableReloadListener {

    @Override
    public CompletableFuture<Void> reload(Synchronizer synchronizer, ResourceManager manager,
        Executor prepareExecutor, Executor applyExecutor) {
        return AuxiliaryTextures.prepareDecodedImagesAsync(manager, prepareExecutor)
            .thenCompose(synchronizer::whenPrepared)
            .thenAcceptAsync(AuxiliaryTextures::applyPreparedImages, applyExecutor);
    }
}
