package fr.descendre.client.render;

import fr.descendre.client.DescendreClientCubeCache;
import fr.descendre.world.cube.CubePos;
import fr.descendre.world.cube.DescendreCube;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache global des meshes des cubes côté client.
 *
 * - getOrBuild : retourne le mesh d'un cube. Le construit si pas encore fait
 *   ou si invalidé.
 * - invalidate : marque un cube (et ses voisins, car le culling change !) comme
 *   à reconstruire au prochain rendu.
 * - forget : oublie complètement un cube (cube déchargé).
 */
public final class DescendreMeshCache {

    private int buildsThisFrame = 0;
    private int maxBuildsPerFrame = 4;

    private static final DescendreMeshCache INSTANCE = new DescendreMeshCache();

    public static DescendreMeshCache get() {
        return INSTANCE;
    }

    /** Map des meshes calculés. Si une entrée n'existe pas, le mesh sera construit à la demande. */
    private final Map<CubePos, DescendreCubeMesh> meshes = new ConcurrentHashMap<>();

    /**
     * Set des cubes à reconstruire (mesh sale ou inexistant).
     * On utilise un set pour dédupliquer : si on invalide 50× le même cube avant le prochain render,
     * on ne le construit qu'une fois.
     */
    private final Set<CubePos> dirty = ConcurrentHashMap.newKeySet();

    private DescendreMeshCache() {}

    /**
     * Récupère (ou construit) le mesh d'un cube.
     * Retourne null si le cube n'existe pas dans le cache client.
     */
    public DescendreCubeMesh getOrBuild(CubePos pos, DescendreClientCubeCache cubeCache) {
        DescendreCubeMesh mesh = meshes.get(pos);
        if (mesh != null && !dirty.contains(pos)) {
            return mesh;
        }

        DescendreCube cube = cubeCache.getCube(pos);
        if (cube == null) {
            meshes.remove(pos);
            dirty.remove(pos);
            return null;
        }

        // Si on a déjà atteint le budget de build pour cette frame,
// on garde l'ancien mesh s'il existe. Sinon on ne rend rien pour l'instant.
        if (buildsThisFrame >= maxBuildsPerFrame) {
            return mesh;
        }

        buildsThisFrame++;

        DescendreCubeMesh built = DescendreCubeMesh.build(cube, cubeCache);
        meshes.put(pos, built);
        dirty.remove(pos);
        return built;
    }

    /**
     * Marque un cube comme à reconstruire au prochain rendu.
     * Marque aussi ses 6 voisins (parce que le culling de leurs faces partagées peut changer).
     */
    public void invalidate(CubePos pos) {
        dirty.add(pos);
        for (Direction dir : Direction.values()) {
            CubePos neighbor = new CubePos(
                    pos.x() + dir.getStepX(),
                    pos.y() + dir.getStepY(),
                    pos.z() + dir.getStepZ()
            );
            dirty.add(neighbor);
        }
    }

    /**
     * Invalidation ciblée par bloc. Utilisé quand un seul bloc change.
     * On invalide le cube qui contient ce bloc, et seulement les voisins de cube
     * correspondant aux faces du bloc qui touchent un bord (pas tous les 6).
     */
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

    /** Oublie complètement un cube (déchargement par le ticker côté serveur). */
    public void forget(CubePos pos) {
        meshes.remove(pos);
        dirty.remove(pos);
    }

    /** Vide tout (déconnexion). */
    public void clear() {
        meshes.clear();
        dirty.clear();
    }

    public void beginFrame(int maxBuilds) {
        this.buildsThisFrame = 0;
        this.maxBuildsPerFrame = Math.max(1, maxBuilds);
    }

}