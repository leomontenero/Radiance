package com.radiance.client.vertex;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;

@Environment(EnvType.CLIENT)
public class StorageVertexConsumerProvider implements MultiBufferSource {

    protected final Map<RenderType, VertexConsumer> pending = new HashMap<>();
    protected final Map<RenderType, ByteBufferBuilder> allocated = new HashMap<>();

    private int size = 0;

    public StorageVertexConsumerProvider(int size) {
        this.size = size;
    }

    private static void assignBufferBuilder(
        Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder> builderStorage,
        RenderType layer) {
        builderStorage.put(layer, new ByteBufferBuilder(layer.getExpectedBufferSize()));
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderLayer) {
        VertexConsumer vertexConsumer = this.pending.get(renderLayer);

        if (vertexConsumer == null) {
            ByteBufferBuilder bufferAllocator = new ByteBufferBuilder(size);
            allocated.put(renderLayer, bufferAllocator);

            VertexFormat.Mode drawMode = renderLayer.getDrawMode();
            VertexFormat vertexFormat = renderLayer.getVertexFormat();

            if (drawMode == VertexFormat.Mode.QUADS) {
                vertexConsumer = new PBRVertexConsumer(bufferAllocator, renderLayer);
            } else {
                vertexConsumer = new BufferBuilder(bufferAllocator, drawMode, vertexFormat);
            }
            this.pending.put(renderLayer, vertexConsumer);
        }
        return vertexConsumer;
    }

    public Map<RenderType, VertexConsumer> getLayers() {
        return this.pending;
    }

    public void close() {
        for (Map.Entry<RenderType, ByteBufferBuilder> entry : this.allocated.entrySet()) {
            entry.getValue()
                .close();
        }
        this.pending.clear();
    }
}