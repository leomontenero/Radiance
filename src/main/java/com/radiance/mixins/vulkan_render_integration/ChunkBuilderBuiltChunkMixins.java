package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderBuiltChunkExt;
import java.util.stream.Collector;
import java.util.stream.Stream;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionRenderDispatcher.BuiltChunk.class)
public class ChunkBuilderBuiltChunkMixins implements IChunkBuilderBuiltChunkExt {

    @Shadow
    @Final
    SectionRenderDispatcher field_20833;

    @Unique
    public SectionRenderDispatcher radiance$getChunkBuilder() {
        return field_20833;
    }

    @Redirect(method = "<init>",
        at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"))
    private Object cancelCollect(Stream<?> stream, Collector<?, ?, ?> collector) {
        return null;
    }

    @Inject(method = "clear()V", at = @At(value = "TAIL"))
    private void addToRebuildGridClear(CallbackInfo ci) {
        SectionRenderDispatcher.BuiltChunk self = (SectionRenderDispatcher.BuiltChunk) (Object) this;
        ChunkProxy.enqueueRebuild(self);
    }

    @Inject(method = "scheduleRebuild(Z)V", at = @At(value = "TAIL"))
    private void addToRebuildGridScheduleRebuild(CallbackInfo ci) {
        SectionRenderDispatcher.BuiltChunk self = (SectionRenderDispatcher.BuiltChunk) (Object) this;
        ChunkProxy.enqueueRebuild(self);
    }

    @Inject(method = "setSectionPos(J)V", at = @At(value = "TAIL"))
    private void syncNativeChunkSlot(long sectionPos, CallbackInfo ci) {
        SectionRenderDispatcher.BuiltChunk self = (SectionRenderDispatcher.BuiltChunk) (Object) this;
        ChunkProxy.relocateSingle(self.index, self.getOrigin().getX(), self.getOrigin().getY(),
            self.getOrigin().getZ());
    }

    @Inject(method = "delete()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;clear()V",
            shift = At.Shift.AFTER),
        cancellable = true)
    public void cancelVertexConsumerDelete(CallbackInfo ci) {
        ci.cancel();
    }
}
