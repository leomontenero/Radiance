package com.radiance.client.proxy.world;

import static com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS;
import static org.lwjgl.system.MemoryUtil.memAddress;

import com.mojang.blaze3d.vertex.VertexSorting;
import com.radiance.client.constant.Constants;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderBuiltChunkExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderExt;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;

public class ChunkProxy {

    public static final SectionRenderDispatcher.ChunkData PROCESSED = new SectionRenderDispatcher.ChunkData() {
        @Override
        public boolean isVisibleThrough(Direction from, Direction to) {
            return false;
        }
    };
    public static final SectionRenderDispatcher.ChunkData TERRAIN_EMPTY = new SectionRenderDispatcher.ChunkData() {
        @Override
        public boolean isVisibleThrough(Direction from, Direction to) {
            return false;
        }
    };
    private static final Map<Integer, SectionRenderDispatcher.BuiltChunk> rebuildQueue = new ConcurrentHashMap<>();
    private static final java.util.Set<Integer> forcedRebuildIndices = ConcurrentHashMap.newKeySet();
    private static final List<Future<?>> rebuildTasks = new ArrayList<>();
    private static ViewArea currentStorage = null;
    private static boolean pendingRebuildAll = false;
    private static int numChunkRebuildThreads = getChunkRebuildThreadCount();
    private static final int numImportantChunkRebuildThreads = 1;
    private static int numNormalChunkRebuildThreads = Math.max(1,
        numChunkRebuildThreads - numImportantChunkRebuildThreads);
    private static final ExecutorService
        importantChunkRebuildExecutor =
        Executors.newFixedThreadPool(numImportantChunkRebuildThreads, r -> {
            Thread thread = new Thread(r);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });
    private static final ThreadLocal<SectionBufferBuilderPack>
        blockBufferAllocatorStorageThreadLocal =
        ThreadLocal.withInitial(SectionBufferBuilderPack::new);
    public static int builtChunkNum = 0;
    private static ExecutorService backgroundChunkRebuildExecutor = Executors.newFixedThreadPool(
        numNormalChunkRebuildThreads, r -> {
            Thread thread = new Thread(r);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });

    public static native void initNative(int numChunks, int sizeX, int sizeY, int sizeZ,
        int bottomSectionCoord);

    public static native void updateSectionPosNative(int sectionX, int sectionY, int sectionZ);

    public static void init(int numChunks, int sizeX, int sizeY, int sizeZ,
        int bottomSectionCoord) {
        clear();
        initNative(numChunks, sizeX, sizeY, sizeZ, bottomSectionCoord);
    }

    public static void updateSectionPos(SectionPos sectionPos) {
        updateSectionPosNative(sectionPos.getSectionX(), sectionPos.getSectionY(),
            sectionPos.getSectionZ());
    }

    public static void setStorage(ViewArea storage) {
        currentStorage = storage;
        if (currentStorage != null && pendingRebuildAll) {
            pendingRebuildAll = false;
            queueRebuildAll(currentStorage);
        }
    }

    private static int getChunkRebuildThreadCount() {
        int expectedBufferTotal = RenderType.getBlockLayers()
            .stream()
            .mapToInt(RenderType::getExpectedBufferSize)
            .sum();
        int memoryLimited = Math.max(1,
            (int) (Runtime.getRuntime().maxMemory() * 0.3) / (expectedBufferTotal * 4) - 1);
        int userThreads = Options.chunkBuildingThreads;
        return Math.max(2,
            Math.min(userThreads, Math.min(Options.getMaxChunkBuildingThreads(), memoryLimited)));
    }

    public static AutoCloseable scopedBlockBufferAllocatorStorage() {
        final SectionBufferBuilderPack s = blockBufferAllocatorStorageThreadLocal.get();
        s.reset();
        return s::clear;
    }

    public static void clear() {
        waitImportantChunkRebuild();

        backgroundChunkRebuildExecutor.shutdown();
        try {
            backgroundChunkRebuildExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        numChunkRebuildThreads = getChunkRebuildThreadCount();
        numNormalChunkRebuildThreads = Math.max(1,
            numChunkRebuildThreads - numImportantChunkRebuildThreads);
        backgroundChunkRebuildExecutor = Executors.newFixedThreadPool(numNormalChunkRebuildThreads,
            r -> {
                Thread thread = new Thread(r);
                thread.setPriority(Thread.NORM_PRIORITY);
                return thread;
            });

        rebuildQueue.clear();
        forcedRebuildIndices.clear();
        rebuildTasks.clear();
        currentStorage = null;
        pendingRebuildAll = false;
    }

    public static void enqueueRebuild(SectionRenderDispatcher.BuiltChunk chunk) {
        rebuildQueue.put(chunk.index, chunk);
    }

    public static void rebuildAll() {
        if (currentStorage == null || currentStorage.chunks == null) {
            pendingRebuildAll = true;
            return;
        }

        queueRebuildAll(currentStorage);
    }

    private static void queueRebuildAll(ViewArea storage) {
        if (storage == null || storage.chunks == null) {
            pendingRebuildAll = true;
            return;
        }

        for (SectionRenderDispatcher.BuiltChunk builtChunk : storage.chunks) {
            if (builtChunk == null) {
                continue;
            }
            forcedRebuildIndices.add(builtChunk.index);
            builtChunk.scheduleRebuild(true);
            enqueueRebuild(builtChunk);
        }
    }

    public static void rebuild(Camera camera) {

        BlockPos blockPos = camera.getBlockPos();
        for (SectionRenderDispatcher.BuiltChunk builtChunk : rebuildQueue.values()) {
            boolean forced = forcedRebuildIndices.remove(builtChunk.index);
            if (builtChunk.needsRebuild() && (forced || builtChunk.shouldBuild())) {
                builtChunk.cancelRebuild();

                BlockPos
                    chunkCenterPos =
                    builtChunk.getOrigin()
                        .add(8, 8, 8);
                boolean isImportant = chunkCenterPos.getSquaredDistance(blockPos) < 768.0
                    || builtChunk.needsImportantRebuild();

                if (isImportant) {
                    Future<?> rebuildTask = importantChunkRebuildExecutor.submit(() -> {
                        rebuildSingle(builtChunk, true);
                    });
                    rebuildTasks.add(rebuildTask);
                } else {
                    backgroundChunkRebuildExecutor.execute(() -> {
                        rebuildSingle(builtChunk, false);
                    });
                }
            }
        }

        rebuildQueue.clear();
    }

    public static void waitImportantChunkRebuild() {
        if (rebuildTasks.isEmpty()) {
            return;
        }

        for (Future<?> rebuildTask : rebuildTasks) {
            try {
                rebuildTask.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        rebuildTasks.clear();
    }

    private static void rebuildSingle(SectionRenderDispatcher.BuiltChunk builtChunk, boolean important) {
        try (var scope = scopedBlockBufferAllocatorStorage()) {
            RenderRegionCache chunkRendererRegionBuilder = new RenderRegionCache();
            IChunkBuilderBuiltChunkExt builtChunkExt = (IChunkBuilderBuiltChunkExt) builtChunk;
            SectionRenderDispatcher chunkBuilder = builtChunkExt.radiance$getChunkBuilder();
            IChunkBuilderExt chunkBuilderExt = (IChunkBuilderExt) chunkBuilder;
            RenderChunkRegion
                chunkRendererRegion =
                chunkRendererRegionBuilder.build(chunkBuilderExt.radiance$getWorld(),
                    SectionPos.from(builtChunk.getSectionPos()));

            if (chunkRendererRegion == null) {
                invalidateSingle(builtChunk.index);
                builtChunk.data.set(SectionRenderDispatcher.ChunkData.EMPTY);
                return;
            }

            SectionBufferBuilderPack storage = blockBufferAllocatorStorageThreadLocal.get();
            rebuildSingle(chunkRendererRegion, chunkBuilder, chunkBuilderExt, builtChunk, storage,
                important);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void rebuildSingle(RenderChunkRegion chunkRendererRegion,
        SectionRenderDispatcher chunkBuilder,
        IChunkBuilderExt chunkBuilderExt,
        SectionRenderDispatcher.BuiltChunk builtChunk,
        SectionBufferBuilderPack storage,
        boolean important) {

        SectionPos chunkSectionPos = SectionPos.from(builtChunk.getOrigin());

        Vec3 vec3d = chunkBuilder.getCameraPosition();
        // TODO: cancel out the sort operation in section builder
        VertexSorting
            vertexSorter =
            VertexSorting.byDistance((float) (vec3d.x - builtChunk.getOrigin()
                    .getX()),
                (float) (vec3d.y - builtChunk.getOrigin()
                    .getY()),
                (float) (vec3d.z - builtChunk.getOrigin()
                    .getZ()));

        SectionCompiler.RenderData renderData =
            ((IChunkBuilderExt) chunkBuilder).radiance$getSectionBuilder()
                .build(chunkSectionPos, chunkRendererRegion, vertexSorter, storage);

        Map<RenderType, MeshData> buffers = renderData.buffers;
        builtChunk.setNoCullingBlockEntities(renderData.noCullingBlockEntities);

        if (buffers.isEmpty()) {
            SectionRenderDispatcher.ChunkData chunkData = new SectionRenderDispatcher.ChunkData() {
                @Override
                public List<BlockEntity> getBlockEntities() {
                    return renderData.blockEntities;
                }

                @Override
                public boolean isVisibleThrough(Direction from, Direction to) {
                    return renderData.chunkOcclusionData.isVisibleThrough(from, to);
                }

                @Override
                public boolean isEmpty(RenderType layer) {
                    return true;
                }
            };
            builtChunk.data.set(chunkData);
            builtChunkNum++;

            invalidateSingle(builtChunk.index);
        } else {
            SectionRenderDispatcher.ChunkData chunkData = new SectionRenderDispatcher.ChunkData() {
                @Override
                public List<BlockEntity> getBlockEntities() {
                    return renderData.blockEntities;
                }

                @Override
                public boolean isVisibleThrough(Direction from, Direction to) {
                    return renderData.chunkOcclusionData.isVisibleThrough(from, to);
                }

                @Override
                public boolean isEmpty(RenderType layer) {
                    return layer == null || !buffers.containsKey(layer);
                }
            };
            builtChunk.data.set(chunkData);
            builtChunkNum++;

            ByteBuffer geometryTypeBB = null;
            ByteBuffer geometryGroupNameBB = null;
            ByteBuffer geometryTextureBB = null;
            ByteBuffer vertexFormatBB = null;
            ByteBuffer vertexCountBB = null;
            ByteBuffer verticesBB = null;
            List<ByteBuffer> geometryGroupNameBuffers = new ArrayList<>(buffers.size());

            try {
                int geometryTypeSize = buffers.size() * Integer.BYTES;
                geometryTypeBB = MemoryUtil.memAlloc(geometryTypeSize);
                long geometryTypeAddr = memAddress(geometryTypeBB);
                int geometryTypeBaseAddr = 0;

                int geometryGroupNameSize = buffers.size() * Long.BYTES;
                geometryGroupNameBB = MemoryUtil.memAlloc(geometryGroupNameSize);
                long geometryGroupNameAddr = memAddress(geometryGroupNameBB);
                int geometryGroupNameBaseAddr = 0;

                int geometryTextureSize = buffers.size() * Integer.BYTES;
                geometryTextureBB = MemoryUtil.memAlloc(geometryTextureSize);
                long geometryTextureAddr = memAddress(geometryTextureBB);
                int geometryTextureBaseAddr = 0;

                int vertexFormatSize = buffers.size() * Integer.BYTES;
                vertexFormatBB = MemoryUtil.memAlloc(vertexFormatSize);
                long vertexFormatAddr = memAddress(vertexFormatBB);
                int vertexFormatBaseAddr = 0;

                int vertexCountSize = buffers.size() * Integer.BYTES;
                vertexCountBB = MemoryUtil.memAlloc(vertexCountSize);
                long vertexCountAddr = memAddress(vertexCountBB);
                int vertexCountBaseAddr = 0;

                int verticesSize = buffers.size() * Long.BYTES;
                verticesBB = MemoryUtil.memAlloc(verticesSize);
                long verticesAddr = memAddress(verticesBB);
                int verticesBaseAddr = 0;

                for (Map.Entry<RenderType, MeshData> entry : buffers.entrySet()) {
                    RenderType renderLayer = entry.getKey();
                    assert renderLayer.getDrawMode() == QUADS;

                    MeshData vertexBuffer = entry.getValue();
                    BufferProxy.BufferInfo vertexBufferInfo = BufferProxy.getBufferInfo(
                        vertexBuffer.getBuffer());
                    assert vertexBuffer.getDrawParameters()
                        .indexCount() == vertexBuffer.getDrawParameters()
                        .vertexCount() / 4 * 6;

                    TextureManager
                        textureManager =
                        Minecraft.getInstance()
                            .getTextureManager();

                    int
                        geometryTypeID =
                        Constants.GeometryTypes.getGeometryType(renderLayer, true)
                            .getValue();
                    int
                        geometryTextureID =
                        textureManager.getTexture(
                                ((RenderType.MultiPhase) renderLayer).phases.texture.getId()
                                    .orElse(MissingTextureAtlasSprite.getMissingSpriteId()))
                            .getGlId();
                    int vertexFormatID = Constants.VertexFormats.getValue(
                        vertexBuffer.getDrawParameters()
                            .format());

                    geometryTypeBB.putInt(geometryTypeBaseAddr, geometryTypeID);
                    geometryTypeBaseAddr += Integer.BYTES;

                    ByteBuffer geometryGroupNameBuffer = MemoryUtil.memUTF8(renderLayer.name, true);
                    geometryGroupNameBuffers.add(geometryGroupNameBuffer);
                    geometryGroupNameBB.putLong(geometryGroupNameBaseAddr,
                        memAddress(geometryGroupNameBuffer));
                    geometryGroupNameBaseAddr += Long.BYTES;

                    geometryTextureBB.putInt(geometryTextureBaseAddr, geometryTextureID);
                    geometryTextureBaseAddr += Integer.BYTES;

                    vertexFormatBB.putInt(vertexFormatBaseAddr, vertexFormatID);
                    vertexFormatBaseAddr += Integer.BYTES;

                    vertexCountBB.putInt(vertexCountBaseAddr,
                        vertexBuffer.getDrawParameters()
                            .vertexCount());
                    vertexCountBaseAddr += Integer.BYTES;

                    verticesBB.putLong(verticesBaseAddr, vertexBufferInfo.addr());
                    verticesBaseAddr += Long.BYTES;
                }

                rebuildSingle(builtChunk.getOrigin()
                        .getX(),
                    builtChunk.getOrigin()
                        .getY(),
                    builtChunk.getOrigin()
                        .getZ(),
                    builtChunk.index,
                    buffers.size(),
                    geometryTypeAddr,
                    geometryGroupNameAddr,
                    geometryTextureAddr,
                    vertexFormatAddr,
                    vertexCountAddr,
                    verticesAddr,
                    important);
            } finally {
                if (geometryTypeBB != null) {
                    MemoryUtil.memFree(geometryTypeBB);
                }
                if (geometryGroupNameBB != null) {
                    MemoryUtil.memFree(geometryGroupNameBB);
                }
                if (geometryTextureBB != null) {
                    MemoryUtil.memFree(geometryTextureBB);
                }
                if (vertexFormatBB != null) {
                    MemoryUtil.memFree(vertexFormatBB);
                }
                if (vertexCountBB != null) {
                    MemoryUtil.memFree(vertexCountBB);
                }
                if (verticesBB != null) {
                    MemoryUtil.memFree(verticesBB);
                }
                for (ByteBuffer geometryGroupNameBuffer : geometryGroupNameBuffers) {
                    MemoryUtil.memFree(geometryGroupNameBuffer);
                }
            }
        }

        for (Map.Entry<RenderType, MeshData> entry : buffers.entrySet()) {
            entry.getValue()
                .close();
        }
    }

    private static native void rebuildSingle(int originX,
        int originY,
        int originZ,
        long index,
        int size,
        long geometryTypes,
        long geometryGroupNames,
        long geometryTextures,
        long vertexFormats,
        long vertexCounts,
        long vertices,
        boolean important);

    public static native boolean isChunkReady(long index);

    public static boolean isChunkReady(SectionRenderDispatcher.BuiltChunk builtChunk) {
        return isChunkReady(builtChunk.index);
    }

    public static native void relocateSingle(long index, int originX, int originY, int originZ);

    public static native void invalidateSingle(long index);
}
