package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.VertexSorting;
import com.radiance.client.vertex.PBRVertexConsumer;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.Map;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionCompiler.class)
public abstract class SectionBuilderMixins {

    @Final
    @Shadow
    private BlockRenderDispatcher blockRenderManager;

    @Final
    @Shadow
    private BlockEntityRenderDispatcher blockEntityRenderDispatcher;

    @Shadow
    protected abstract <E extends BlockEntity> void addBlockEntity(SectionCompiler.RenderData data,
        E blockEntity);

    @Inject(method =
        "build(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;"
            +
            "Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;)"
            +
            "Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;", at = @At(value = "HEAD"), cancellable = true)
    public void redirectBuild(SectionPos sectionPos,
        RenderChunkRegion renderRegion,
        VertexSorting vertexSorter,
        SectionBufferBuilderPack allocatorStorage,
        CallbackInfoReturnable<SectionCompiler.RenderData> cir) {
        SectionCompiler.RenderData renderData = new SectionCompiler.RenderData();
        BlockPos blockPos = sectionPos.getMinPos();
        BlockPos blockPos2 = blockPos.add(15, 15, 15);
        VisGraph chunkOcclusionDataBuilder = new VisGraph();
        PoseStack matrixStack = new PoseStack();
        ModelBlockRenderer.enableBrightnessCache();
        Map<RenderType, PBRVertexConsumer>
            map =
            new Reference2ObjectArrayMap<>(RenderType.getBlockLayers()
                .size());
        RandomSource random = RandomSource.create();

        for (BlockPos blockPos3 : BlockPos.iterate(blockPos, blockPos2)) {
            BlockState blockState = renderRegion.getBlockState(blockPos3);
            if (blockState.isOpaqueFullCube()) {
                chunkOcclusionDataBuilder.markClosed(blockPos3);
            }

            if (blockState.hasBlockEntity()) {
                BlockEntity blockEntity = renderRegion.getBlockEntity(blockPos3);
                if (blockEntity != null) {
                    this.addBlockEntity(renderData, blockEntity);
                }
            }

            FluidState fluidState = blockState.getFluidState();
            if (!fluidState.isEmpty()) {
                RenderType renderLayer = ItemBlockRenderTypes.getFluidLayer(fluidState);
                PBRVertexConsumer bufferBuilder = this.beginBufferBuilding(map, allocatorStorage,
                    renderLayer);
                this.blockRenderManager.renderFluid(blockPos3, renderRegion, bufferBuilder,
                    blockState, fluidState);
            }

            if (blockState.getRenderType() == RenderShape.MODEL) {
                RenderType renderLayer = ItemBlockRenderTypes.getBlockLayer(blockState);
                PBRVertexConsumer bufferBuilder = this.beginBufferBuilding(map, allocatorStorage,
                    renderLayer);
                matrixStack.push();
                matrixStack.translate((float) SectionPos.getLocalCoord(blockPos3.getX()),
                    (float) SectionPos.getLocalCoord(blockPos3.getY()),
                    (float) SectionPos.getLocalCoord(blockPos3.getZ()));
                this.blockRenderManager.renderBlock(blockState, blockPos3, renderRegion,
                    matrixStack, bufferBuilder, true, random);
                matrixStack.pop();
            }
        }

        for (Map.Entry<RenderType, PBRVertexConsumer> entry : map.entrySet()) {
            RenderType renderLayer2 = entry.getKey();
            MeshData
                builtBuffer =
                entry.getValue()
                    .endNullable();
            if (builtBuffer != null) {
                renderData.buffers.put(renderLayer2, builtBuffer);
            }
        }

        ModelBlockRenderer.disableBrightnessCache();
        renderData.chunkOcclusionData = chunkOcclusionDataBuilder.build();
        cir.setReturnValue(renderData);
    }

    @Unique
    private PBRVertexConsumer beginBufferBuilding(Map<RenderType, PBRVertexConsumer> builders,
        SectionBufferBuilderPack allocatorStorage,
        RenderType layer) {
        PBRVertexConsumer pbrVertexConsumer = builders.get(layer);
        if (pbrVertexConsumer == null) {
            ByteBufferBuilder bufferAllocator = allocatorStorage.get(layer);
            pbrVertexConsumer = new PBRVertexConsumer(bufferAllocator, layer);
            builders.put(layer, pbrVertexConsumer);
        }

        return pbrVertexConsumer;
    }
}
