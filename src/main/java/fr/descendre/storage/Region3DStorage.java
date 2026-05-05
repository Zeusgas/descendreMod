package fr.descendre.storage;

import fr.descendre.world.cube.CubePos;
import net.minecraft.nbt.CompoundTag;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gère tous les fichiers .r3d pour une dimension.
 *
 * - Convertit un CubePos en fichier région (via division entière par 16).
 * - Garde un cache LRU des fichiers ouverts pour limiter les file descriptors.
 * - Ferme/flush automatiquement les fichiers les moins utilisés.
 */
public final class Region3DStorage {

    /** Combien de fichiers .r3d on garde ouverts en parallèle. */
    private static final int MAX_OPEN_FILES = 64;

    private final Path baseDir;

    /**
     * Cache LRU : la map garde l'ordre d'insertion + accès, on évince la plus vieille
     * quand on dépasse MAX_OPEN_FILES.
     */
    private final LinkedHashMap<RegionKey, Region3DFile> openFiles =
            new LinkedHashMap<>(16, 0.75f, true);

    public Region3DStorage(Path baseDir) {
        this.baseDir = baseDir;
    }

    /** Calcule la clé région pour un CubePos donné. */
    private static RegionKey regionOf(CubePos cubePos) {
        return new RegionKey(
                Math.floorDiv(cubePos.x(), Region3DFile.REGION_SIZE),
                Math.floorDiv(cubePos.y(), Region3DFile.REGION_SIZE),
                Math.floorDiv(cubePos.z(), Region3DFile.REGION_SIZE)
        );
    }

    private Path pathFor(RegionKey key) {
        return baseDir.resolve("r." + key.rx + "." + key.ry + "." + key.rz + ".r3d");
    }

    /** Ouvre (ou récupère du cache) le fichier région pour un CubePos. */
    private synchronized Region3DFile getOrOpen(CubePos cubePos) throws IOException {
        RegionKey key = regionOf(cubePos);
        Region3DFile file = openFiles.get(key);
        if (file != null) return file;

        file = new Region3DFile(pathFor(key));
        openFiles.put(key, file);

        // Évict si on dépasse la limite
        while (openFiles.size() > MAX_OPEN_FILES) {
            Map.Entry<RegionKey, Region3DFile> oldest = openFiles.entrySet().iterator().next();
            try {
                oldest.getValue().close();
            } catch (IOException ignored) {}
            openFiles.remove(oldest.getKey());
        }

        return file;
    }

    /** Lit le NBT d'un cube. Retourne null s'il n'existe pas. */
    public synchronized CompoundTag readCube(CubePos pos) throws IOException {
        Region3DFile file = getOrOpen(pos);
        return file.readCube(pos.x(), pos.y(), pos.z());
    }

    /** Écrit le NBT d'un cube. */
    public synchronized void writeCube(CubePos pos, CompoundTag tag) throws IOException {
        Region3DFile file = getOrOpen(pos);
        file.writeCube(pos.x(), pos.y(), pos.z(), tag);
    }

    /** Supprime un cube du fichier région. */
    public synchronized void deleteCube(CubePos pos) throws IOException {
        Region3DFile file = getOrOpen(pos);
        file.deleteCube(pos.x(), pos.y(), pos.z());
    }

    /** Force le flush + ferme tous les fichiers ouverts. À appeler au stop du serveur. */
    public synchronized void closeAll() {
        for (Region3DFile file : openFiles.values()) {
            try {
                file.close();
            } catch (IOException ignored) {}
        }
        openFiles.clear();
    }

    /** Flush tous les fichiers sans les fermer. À appeler à la sauvegarde du monde. */
    public synchronized void flushAll() {
        for (Region3DFile file : openFiles.values()) {
            try {
                file.flush();
            } catch (IOException ignored) {}
        }
    }

    /** Clé d'une région dans la map. */
    private record RegionKey(int rx, int ry, int rz) {}
}