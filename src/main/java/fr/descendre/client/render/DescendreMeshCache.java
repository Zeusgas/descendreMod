package fr.descendre.client.render;

import fr.descendre.client.DescendreClientCubeCache;
import fr.descendre.world.cube.CubePos;
import fr.descendre.world.cube.DescendreCube;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;

public final class DescendreMeshCache {

    private static final DescendreMeshCache INSTANCE = new DescendreMeshCache();

    public static DescendreMeshCache get() {
        return INSTANCE;
    }

    private final Map<CubePos, DescendreCubeMesh> meshes = new ConcurrentHashMap<>();
    private final Set<CubePos> dirty = ConcurrentHashMap.newKeySet();
    private final Set<CubePos> building = ConcurrentHashMap.newKeySet();
    private final Queue<CompletedBuild> completedQueue = new ConcurrentLinkedQueue<>();

    private final ExecutorService workers = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            r -> {
                Thread t = new Thread(r, "DescendreMeshWorker");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            }
    );

    private int submitsThisFrame = 0;
    private int maxSubmitsPerFrame = 4;

    private record CompletedBuild(CubePos pos, DescendreCubeMesh mesh) {}

    private DescendreMeshCache() {}

    public void beginFrame(int maxSubmits) {
        this.submitsThisFrame = 0;
        this.maxSubmitsPerFrame = Math.max(1, maxSubmits);

        CompletedBuild completed;
        while ((completed = completedQueue.poll()) != null) {
            if (completed.mesh != null) {
                meshes.put(completed.pos, completed.mesh);
            } else {
                meshes.remove(completed.pos);
            }
            building.remove(completed.pos);
            dirty.remove(completed.pos);
        }
    }

    public DescendreCubeMesh getOrBuild(CubePos pos, DescendreClientCubeCache cubeCache) {
        DescendreCubeMesh cached = meshes.get(pos);
        boolean isDirty = dirty.contains(pos);

        if (cached != null && !isDirty) return cached;
        if (building.contains(pos)) return cached;

        DescendreCube cube = cubeCache.getCube(pos);
        if (cube == null) {
            meshes.remove(pos);
            dirty.remove(pos);
            return null;
        }

        if (submitsThisFrame >= maxSubmitsPerFrame) return cached;

        submitsThisFrame++;
        building.add(pos);

        workers.submit(() -> {
            try {
                DescendreCubeMesh built = DescendreCubeMesh.build(cube, cubeCache);
                completedQueue.offer(new CompletedBuild(pos, built));
            } catch (Exception e) {
                System.err.println("[Descendre] Mesh build error @ " + pos + ": " + e.getMessage());
                completedQueue.offer(new CompletedBuild(pos, null));
            }
        });

        return cached;
    }

    public void invalidate(CubePos pos) {
        dirty.add(pos);
        for (Direction dir : Direction.values()) {
            dirty.add(new CubePos(
                    pos.x() + dir.getStepX(),
                    pos.y() + dir.getStepY(),
                    pos.z() + dir.getStepZ()
            ));
        }
    }

    public void invalidateBlock(BlockPos pos) {
        CubePos cubePos = CubePos.fromBlockPos(pos);
        dirty.add(cubePos);

        int lx = pos.getX() & 15;
        int ly = pos.getY() & 15;
        int lz = pos.getZ() & 15;

        if (lx == 0)  dirty.add(new CubePos(cubePos.x() - 1, cubePos.y(), cubePos.z()));
        if (lx == 15) dirty.add(new CubePos(cubePos.x() + 1, cubePos.y(), cubePos.z()));
        if (ly == 0)  dirty.add(new CubePos(cubePos.x(), cubePos.y() - 1, cubePos.z()));
        if (ly == 15) dirty.add(new CubePos(cubePos.x(), cubePos.y() + 1, cubePos.z()));
        if (lz == 0)  dirty.add(new CubePos(cubePos.x(), cubePos.y(), cubePos.z() - 1));
        if (lz == 15) dirty.add(new CubePos(cubePos.x(), cubePos.y(), cubePos.z() + 1));
    }

    public void forget(CubePos pos) {
        meshes.remove(pos);
        dirty.remove(pos);
    }

    public void clear() {
        meshes.clear();
        dirty.clear();
        building.clear();
        completedQueue.clear();
    }

    public void invalidateLight(BlockPos pos) {
        CubePos center = CubePos.fromBlockPos(pos);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    dirty.add(new CubePos(
                            center.x() + dx,
                            center.y() + dy,
                            center.z() + dz
                    ));
                }
            }
        }
    }

    public String stats() {
        return "meshes=" + meshes.size() + " dirty=" + dirty.size() + " building=" + building.size();
    }
}