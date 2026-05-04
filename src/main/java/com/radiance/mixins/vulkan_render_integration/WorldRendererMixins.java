package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.UnsafeManager;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.proxy.world.PlayerProxy;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGameRendererExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ILightMapManagerExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IOverlayTextureExt;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.WorldBorderRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureManager;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.Tuple;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class WorldRendererMixins {

    @Shadow
    private ClientLevel world;

    @Final
    @Shadow
    private Minecraft client;

    @Final
    @Shadow
    private EntityRenderDispatcher entityRenderDispatcher;

    @Final
    @Shadow
    private BlockEntityRenderDispatcher blockEntityRenderDispatcher;

    @Shadow
    private ViewArea chunks;

    @Shadow
    private Frustum frustum;

    @Final
    @Shadow
    private List<Entity> renderedEntities;

    @Shadow
    private int renderedEntitiesCount;

    @Shadow
    private double lastCameraPitch;

    @Shadow
    private double lastCameraYaw;

    @Final
    @Shadow
    private ObjectArrayList<SectionRenderDispatcher.BuiltChunk> builtChunks;

    @Shadow
    @Final
    private Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions;

    @Shadow
    @Final
    private Set<BlockEntity> noCullingBlockEntities;

    @Shadow
    @Final
    private WeatherEffectRenderer weatherRendering;

    @Shadow
    @Final
    private WorldBorderRenderer worldBorderRendering;

    @Shadow
    private int ticks;
    @Shadow
    @Final
    private CloudRenderer cloudRenderer;
    // endregion

    // region <init>
    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/client/renderer/SkyRenderer"))
    private SkyRenderer cancelNewSkyRendering() {
        return UnsafeManager.INSTANCE.allocateInstance(SkyRenderer.class);
    }
    // endregion

    @Redirect(method = "scheduleTerrainUpdate()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;scheduleTerrainUpdate()V"))
    public void cancelTerrainUpdateWithChunkRenderingDataPreparer(
        SectionOcclusionGraph instance) {

    }

    // region <close>
    @Redirect(method = "close()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SkyRenderer;close()V"))
    public void cancelSkyRenderingClose(SkyRenderer instance) {

    }

    @Redirect(method = "reload(Lnet/minecraft/server/packs/resources/ResourceManager;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;loadEntityOutlinePostProcessor()V"))
    public void cancelReloadWithResourceManager(LevelRenderer instance) {

    }

    @Redirect(method = "reload()V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/SectionOcclusionGraph;setStorage"
            + "(Lnet/minecraft/client/renderer/ViewArea;)V"))
    public void cancelReloadWithChunkRenderingDataPreparerSetStorage(
        SectionOcclusionGraph instance, ViewArea storage) {

    }

    @Redirect(method = "getEntitiesToRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Ljava/util/List;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isThirdPerson()Z"))
    public boolean enablePlayerRendererInFirstPlayer(Camera instance) {
        return true;
    }

    @Redirect(method = "getEntitiesToRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Ljava/util/List;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"))
    public <E extends Entity> boolean loosenEntityFiltering(EntityRenderDispatcher instance,
        E entity, Frustum frustum, double x, double y, double z) {
        Vec3 vec3d = entity.getPos().subtract(new Vec3(x, y, z));
        double distance = vec3d.length();
        if (distance < 16 * 3) {
            return true;
        }
        return this.entityRenderDispatcher.shouldRender(entity, frustum, x, y, z);
    }

    // region <render>
    @Shadow
    protected abstract void setupTerrain(Camera camera, Frustum frustum, boolean hasForcedFrustum,
        boolean spectator);

    @Shadow
    protected abstract boolean getEntitiesToRender(Camera camera, Frustum frustum,
        List<Entity> output);

    @Shadow
    protected abstract boolean canDrawEntityOutlines();

    @Shadow
    protected abstract void applyFrustum(Frustum frustum);

    @Shadow
    protected abstract boolean isSkyDark(float tickDelta);

    @Shadow
    protected abstract boolean hasBlindnessOrDarkness(Camera camera);

    @Inject(method =
        "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;"
            + "ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", at = @At("HEAD"), cancellable = true)
    public void redirectRender(GraphicsResourceAllocator allocator, DeltaTracker tickCounter,
        boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
        Matrix4f effectedRotationMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        PlayerProxy.setCameraPos(camera.getPos());

        float f = tickCounter.getTickDelta(false);
        RenderSystem.setShaderGameTime(this.world.getTime(), f);
        this.blockEntityRenderDispatcher.configure(this.world, camera, this.client.crosshairTarget);
        this.entityRenderDispatcher.configure(this.world, camera, this.client.targetedEntity);

        this.world.runQueuedChunkUpdates();
        this.world.getChunkManager().getLightingProvider().doLightUpdates();

        Frustum frustum = this.frustum;

        Vec3 vec3d = camera.getPos();
        double x = vec3d.getX();
        double y = vec3d.getY();
        double z = vec3d.getZ();

        this.setupTerrain(camera, frustum, false, false);

        boolean renderEntityOutline = this.getEntitiesToRender(camera, frustum,
            this.renderedEntities);

        Matrix4f viewMatrix = new Matrix4f(
            ((IGameRendererExt) gameRenderer).radiance$getRotationMatrix());
        Matrix4f effectedViewMatrix = new Matrix4f(effectedRotationMatrix);

        // fog
        float h = gameRenderer.getViewDistance();
        boolean bl2 = this.client.world.getDimensionEffects()
            .useThickFog(Mth.floor(x), Mth.floor(y))
            || this.client.inGameHud.getBossBarHud().shouldThickenFog();
        Vector4f vector4f = FogRenderer.getFogColor(camera, f, this.client.world,
            this.client.options.getClampedViewDistance(), gameRenderer.getSkyDarkness(f));
        FogParameters fog = FogRenderer.applyFog(camera, FogRenderer.FogType.FOG_TERRAIN,
            vector4f, h, bl2, f);

        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        OverlayTexture overlayTexture = gameRenderer.getOverlayTexture();
        int overlayTextureID = ((IOverlayTextureExt) overlayTexture).radiance$getTexture()
            .getGlId();
        int endSkyTextureID = textureManager.getTexture(TheEndPortalRenderer.SKY_TEXTURE)
            .getGlId();
        int endPortalTextureID = textureManager.getTexture(
            TheEndPortalRenderer.PORTAL_TEXTURE).getGlId();
        ILightMapManagerExt lightMapManagerExt = (ILightMapManagerExt) (gameRenderer.getLightmapTextureManager());
        BufferProxy.updateWorldUniform(camera, viewMatrix, effectedViewMatrix, projectionMatrix,
            overlayTextureID, fog, world, endSkyTextureID, endPortalTextureID,
            lightMapManagerExt.radiance$getTextureId());

        // Sky
        float tickDelta = tickCounter.getTickDelta(false);
        float skyAngle = this.world.getSkyAngle(tickDelta);
        int baseColor = this.world.getSkyColor(camera.getPos(), tickDelta);

        DimensionSpecialEffects dimensionEffects = this.world.getDimensionEffects();
        int horizonColor = dimensionEffects.getSkyColor(skyAngle);

        PoseStack matrixStack = new PoseStack();
        matrixStack.push();
        matrixStack.multiply(Axis.POSITIVE_Y.rotationDegrees(-90.0F));
        matrixStack.multiply(Axis.POSITIVE_X.rotationDegrees(skyAngle * 360.0F));
        Matrix4f rotationMatrix = matrixStack.peek().getPositionMatrix();
        Vector3f sunDirection = rotationMatrix.transformPosition(0, 1, 0, new Vector3f())
            .normalize();
        matrixStack.pop();

        boolean hasBlindnessOrDarkness = this.hasBlindnessOrDarkness(camera);

        int submersionType = camera.getSubmersionType().ordinal();

        int moonPhase = this.world.getMoonPhase();

        float rainGradient = this.world.getRainGradient(tickDelta);

        int sunTextureID = textureManager.getTexture(SkyRenderer.SUN_TEXTURE).getGlId();

        int moonTextureID = textureManager.getTexture(SkyRenderer.MOON_PHASES_TEXTURE).getGlId();

        BufferProxy.updateSkyUniform(ARGB.getRedFloat(baseColor),
            ARGB.getGreenFloat(baseColor), ARGB.getBlueFloat(baseColor),
            ARGB.getRedFloat(horizonColor), ARGB.getGreenFloat(horizonColor),
            ARGB.getBlueFloat(horizonColor), ARGB.getAlphaFloat(horizonColor), sunDirection,
            dimensionEffects.getSkyType().ordinal(), dimensionEffects.isSunRisingOrSetting(skyAngle),
            this.isSkyDark(tickDelta), hasBlindnessOrDarkness, submersionType, moonPhase,
            rainGradient, sunTextureID, moonTextureID);

        BufferProxy.updateMapping();

        // Entities
        EntityProxy.queueEntitiesBuild(camera, renderedEntities, this.entityRenderDispatcher,
            tickCounter, canDrawEntityOutlines());

        Tuple<List<StorageVertexConsumerProvider>, EntityProxy.EntityRenderDataList> crumblingRenderData = EntityProxy.queueBlockEntitiesRebuild(
            chunks, this.noCullingBlockEntities, blockBreakingProgressions,
            blockEntityRenderDispatcher, tickDelta);
        EntityProxy.queueCrumblingRebuild(camera, blockBreakingProgressions,
            this.client.getBlockRenderManager(), this.world, crumblingRenderData.getLeft(),
            crumblingRenderData.getRight());

        EntityProxy.queueParticleRebuild(camera, tickDelta, frustum);

        if (renderBlockOutline) {
            EntityProxy.queueTargetBlockOutlineRebuild(camera, world);
        }

        EntityProxy.queueWeatherBuild(this.weatherRendering, this.worldBorderRendering, this.world,
            camera, this.ticks, tickDelta);

        // clouds
        CloudStatus cloudRenderMode = this.client.options.getCloudRenderModeValue();
        if (cloudRenderMode != CloudStatus.OFF) {
            float k = this.world.getDimensionEffects().getCloudsHeight();
            if (!Float.isNaN(k)) {
                float ticks = (float) this.ticks + f;
                int color = this.world.getCloudsColor(f);
                float cloudHeight = k + 0.33F;
                this.cloudRenderer.renderClouds(color, cloudRenderMode, cloudHeight, null, null,
                    camera.getPos(), ticks);
            }
        }

        // Chunks
        ChunkProxy.setStorage(chunks);
        ChunkProxy.rebuild(camera);

        this.renderedEntities.clear();

        ci.cancel();
    }
    // endregion

    // region <setWorld>
    @Redirect(method = "setWorld(Lnet/minecraft/client/multiplayer/ClientLevel;)V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/SectionOcclusionGraph;setStorage"
            + "(Lnet/minecraft/client/renderer/ViewArea;)V"))
    public void cancelSetWorldChunkRenderingDataPreparerSetStorage(
        SectionOcclusionGraph instance, ViewArea storage) {

    }
    // endregion

    //region <setupTerrain>
    @Inject(method = "setupTerrain(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;setCameraPosition(Lnet/minecraft/world/phys/Vec3;)V", shift = At.Shift.AFTER), cancellable = true)
    public void cancelCullAndUpdateWithChunkRenderingDataPreparer(Camera camera, Frustum frustum,
        boolean hasForcedFrustum, boolean spectator, CallbackInfo ci, @Local ProfilerFiller profiler) {
//        PlayerProxy.setCameraPos(camera.getPos());
        profiler.pop();
        ci.cancel();
    }
    //endregion

    // region <addBuiltChunk>
    @Redirect(method = "addBuiltChunk(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;)V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/SectionOcclusionGraph;schedulePropagationFrom"
            + "(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;)V"))
    public void cancelPropagateWithChunkRenderingDataPreparer(SectionOcclusionGraph instance,
        SectionRenderDispatcher.BuiltChunk builtChunk) {

    }
    // endregion

    // region <onChunkUnload>
    @Redirect(method = "onChunkUnload(J)V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/SectionOcclusionGraph;schedulePropagationFrom"
            + "(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;)V"))
    public void cancelPropagateUnloadWithChunkRenderingDataPreparer(
        SectionOcclusionGraph instance, SectionRenderDispatcher.BuiltChunk builtChunk) {

    }
    // endregion

    // region <scheduleNeighborUpdates>
    @Redirect(method = "scheduleNeighborUpdates(Lnet/minecraft/world/level/ChunkPos;)V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/SectionOcclusionGraph;addNeighbors(Lnet/minecraft/world/level/ChunkPos;)"
            + "V"))
    public void cancelNeighborUpdatesWithChunkRenderingDataPreparer(
        SectionOcclusionGraph instance, ChunkPos chunkPos) {

    }
    // endregion

    // region <isRenderingReady>
    @Inject(method = "isRenderingReady(Lnet/minecraft/core/BlockPos;)Z", at = @At(value = "HEAD"), cancellable = true)
    public void redirectIsRenderingReady(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        SectionRenderDispatcher.BuiltChunk builtChunk = chunks.getRenderedChunk(pos);

        if (builtChunk == null) {
            cir.setReturnValue(false);
        } else if (builtChunk.data.get().isEmpty(null)) {
            cir.setReturnValue(true);
        } else if (builtChunk.data.get() == ChunkProxy.PROCESSED) {
            cir.setReturnValue(ChunkProxy.isChunkReady(builtChunk));
        }
    }
    // endregion

    // region <>
    @Inject(method = "getCompletedChunkCount()I", at = @At(value = "HEAD"), cancellable = true)
    public void fixGetCompletedChunkCount(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ChunkProxy.builtChunkNum - 54); // 54 + 10 = 64
    }
    // endregion
}
