package fr.descendre.storage;

import fr.descendre.world.cube.CubeMap;
import fr.descendre.world.cube.CubePos;
import fr.descendre.world.cube.DescendreCube;
import net.minecraft.nbt.CompoundTag;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Façade haut niveau de la persistance Descendre, par dimension.
 *
 * Combine :
 *  - CubeMap (cubes en RAM, déjà existant)
 *  - Region3DStorage (fichiers .r3d sur disque)
 *  - CubeSerializer (NBT)
 *
 * Cycle de vie :
 *  - getCubeOrLoad : essaie RAM, sinon charge depuis disque
 *  - saveAllDirty   : flush sur disque tous les cubes modifiés
 *  - close          : flush + ferme les fichiers ouverts
 */
public final class CubeStorage {

    private final CubeMap map;
    private final Region3DStorage regionStorage;

    public CubeStorage(CubeMap map, Path dimensionDir) {
        this.map = map;
        this.regionStorage = new Region3DStorage(dimensionDir);
    }

    /**
     * Retourne le cube en RAM, et tente de le charger depuis le disque s'il n'y est pas.
     * Retourne null si le cube n'existe ni en RAM ni sur disque.
     */
    public DescendreCube getCubeOrLoad(CubePos pos) {
        DescendreCube cube = map.getCube(pos);
        if (cube != null) return cube;

        try {
            CompoundTag tag = regionStorage.readCube(pos);
            if (tag == null) return null;
            DescendreCube loaded = CubeSerializer.deserialize(tag);
            map.putCube(loaded);
            return loaded;
        } catch (IOException e) {
            System.err.println("[Descendre] Erreur lecture cube " + pos + " : " + e.getMessage());
            return null;
        }
    }

    /** Sauvegarde un cube spécifique sur disque (même s'il n'est pas dirty). */
    public void saveCube(DescendreCube cube) {
        try {
            if (cube.isEmpty()) {
                // Cube vide : on le supprime du disque pour ne pas garder de fichiers fantômes
                regionStorage.deleteCube(cube.pos());
            } else {
                CompoundTag tag = CubeSerializer.serialize(cube);
                regionStorage.writeCube(cube.pos(), tag);
            }
            cube.markSaved();
        } catch (IOException e) {
            System.err.println("[Descendre] Erreur écriture cube " + cube.pos() + " : " + e.getMessage());
        }
    }

    /** Sauvegarde tous les cubes RAM marqués "dirty". À appeler périodiquement et au save du monde. */
    public int saveAllDirty() {
        int saved = 0;
        for (DescendreCube cube : map.allCubes()) {
            if (cube.isDirty()) {
                saveCube(cube);
                saved++;
            }
        }
        regionStorage.flushAll();
        return saved;
    }

    /** Force flush sans fermer (appeler à autosave Minecraft). */
    public void flush() {
        saveAllDirty();
    }

    /** Sauvegarde tout puis ferme les fichiers. À appeler au stop du serveur. */
    public void close() {
        saveAllDirty();
        regionStorage.closeAll();
    }
}