package fr.descendre.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.BitSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Un fichier région 3D. Contient jusqu'à 16³ = 4096 cubes (= 256³ blocs).
 *
 * Format :
 *   [HEADER 16 ko]
 *     Pour chaque cube i ∈ [0, 4096) :
 *       - int (4 octets) : (offset_secteur << 8) | sector_count
 *         offset_secteur = position dans le fichier en secteurs 4 ko
 *         sector_count   = nombre de secteurs 4 ko occupés (0 = cube absent)
 *
 *   [SECTEURS 4 ko]
 *     Pour chaque cube présent :
 *       - int (4 octets) : longueur des données NBT qui suivent
 *       - bytes          : NBT gzippé du cube
 *       - padding 0x00   : jusqu'à la fin du dernier secteur
 *
 * Coordonnées :
 *   - Position d'un cube dans la région : (cube.x & 15, cube.y & 15, cube.z & 15)
 *   - Index dans le header :              ly*256 + lz*16 + lx
 */
public final class Region3DFile implements Closeable {

    public static final int REGION_SIZE = 16;                // 16 cubes par axe
    public static final int CUBES_PER_REGION = REGION_SIZE * REGION_SIZE * REGION_SIZE; // 4096
    public static final int SECTOR_BYTES = 4096;             // 4 ko par secteur
    public static final int HEADER_BYTES = CUBES_PER_REGION * 4; // 16 384 = 4 secteurs
    public static final int HEADER_SECTORS = HEADER_BYTES / SECTOR_BYTES; // 4

    private final Path path;
    private final FileChannel channel;

    /** offsets[i] = (sectorOffset << 8) | sectorCount, ou 0 si cube absent */
    private final int[] offsets = new int[CUBES_PER_REGION];

    /** Bitset des secteurs occupés du fichier (true = occupé) */
    private final BitSet usedSectors = new BitSet();

    public Region3DFile(Path path) throws IOException {
        this.path = path;
        java.nio.file.Files.createDirectories(path.getParent());

        this.channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE
        );

        long fileSize = channel.size();

        if (fileSize < HEADER_BYTES) {
            // Fichier neuf ou tronqué : on écrit un header vide
            ByteBuffer empty = ByteBuffer.allocate(HEADER_BYTES);
            channel.position(0);
            while (empty.hasRemaining()) {
                channel.write(empty);
            }
            channel.force(true);
        } else {
            // Lecture du header existant
            ByteBuffer headerBuf = ByteBuffer.allocate(HEADER_BYTES);
            channel.position(0);
            while (headerBuf.hasRemaining()) {
                if (channel.read(headerBuf) < 0) break;
            }
            headerBuf.flip();
            for (int i = 0; i < CUBES_PER_REGION; i++) {
                offsets[i] = headerBuf.getInt();
            }
        }

        // Reconstitue la carte des secteurs occupés
        usedSectors.set(0, HEADER_SECTORS); // header
        for (int packed : offsets) {
            if (packed == 0) continue;
            int offset = packed >>> 8;
            int count  = packed & 0xFF;
            if (count > 0) {
                usedSectors.set(offset, offset + count);
            }
        }
    }

    /** Index local pour un cube dans cette région. */
    public static int localIndex(int cubeX, int cubeY, int cubeZ) {
        int lx = Math.floorMod(cubeX, REGION_SIZE);
        int ly = Math.floorMod(cubeY, REGION_SIZE);
        int lz = Math.floorMod(cubeZ, REGION_SIZE);
        return (ly * REGION_SIZE + lz) * REGION_SIZE + lx;
    }

    public boolean hasCube(int cubeX, int cubeY, int cubeZ) {
        return offsets[localIndex(cubeX, cubeY, cubeZ)] != 0;
    }

    /** Lit un cube. Retourne null s'il n'existe pas. */
    public synchronized CompoundTag readCube(int cubeX, int cubeY, int cubeZ) throws IOException {
        int idx = localIndex(cubeX, cubeY, cubeZ);
        int packed = offsets[idx];
        if (packed == 0) return null;

        int sectorOffset = packed >>> 8;
        int sectorCount  = packed & 0xFF;

        ByteBuffer buf = ByteBuffer.allocate(sectorCount * SECTOR_BYTES);
        channel.position((long) sectorOffset * SECTOR_BYTES);
        while (buf.hasRemaining()) {
            if (channel.read(buf) < 0) break;
        }
        buf.flip();

        int dataLength = buf.getInt();
        if (dataLength <= 0 || dataLength > buf.remaining()) {
            return null;
        }

        byte[] compressed = new byte[dataLength];
        buf.get(compressed);

        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(compressed));
             DataInputStream dis = new DataInputStream(gz)) {
            return NbtIo.read(dis, NbtAccounter.unlimitedHeap());
        }
    }

    /** Écrit un cube. Réutilise sa place s'il y rentre, sinon en alloue une nouvelle. */
    public synchronized void writeCube(int cubeX, int cubeY, int cubeZ, CompoundTag tag) throws IOException {
        // Sérialisation gzippée en mémoire
        ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
        try (GZIPOutputStream gz = new GZIPOutputStream(baos);
             DataOutputStream dos = new DataOutputStream(gz)) {
            NbtIo.write(tag, dos);
        }
        byte[] compressed = baos.toByteArray();

        int payloadSize  = 4 + compressed.length; // 4 octets pour la longueur + données
        int sectorsNeeded = (payloadSize + SECTOR_BYTES - 1) / SECTOR_BYTES;

        if (sectorsNeeded > 255) {
            throw new IOException("Cube trop gros : " + payloadSize + " octets, max " + (255 * SECTOR_BYTES));
        }

        int idx = localIndex(cubeX, cubeY, cubeZ);
        int oldPacked = offsets[idx];
        int oldOffset = oldPacked >>> 8;
        int oldCount  = oldPacked & 0xFF;

        int newOffset;
        if (oldCount >= sectorsNeeded && oldCount > 0) {
            // Ça rentre dans la place existante : on réutilise
            newOffset = oldOffset;
            // Si le nouveau prend moins de secteurs, on libère la queue
            if (sectorsNeeded < oldCount) {
                usedSectors.clear(oldOffset + sectorsNeeded, oldOffset + oldCount);
            }
        } else {
            // Libère l'ancien emplacement
            if (oldCount > 0) {
                usedSectors.clear(oldOffset, oldOffset + oldCount);
            }
            newOffset = allocateSectors(sectorsNeeded);
            usedSectors.set(newOffset, newOffset + sectorsNeeded);
        }

        // Écriture du payload
        ByteBuffer payload = ByteBuffer.allocate(sectorsNeeded * SECTOR_BYTES);
        payload.putInt(compressed.length);
        payload.put(compressed);
        // padding = bytes laissés à 0 par défaut dans ByteBuffer.allocate
        payload.position(0);
        payload.limit(sectorsNeeded * SECTOR_BYTES);

        channel.position((long) newOffset * SECTOR_BYTES);
        while (payload.hasRemaining()) {
            channel.write(payload);
        }

        // Mise à jour du header
        int newPacked = (newOffset << 8) | sectorsNeeded;
        offsets[idx] = newPacked;

        ByteBuffer headerEntry = ByteBuffer.allocate(4);
        headerEntry.putInt(newPacked);
        headerEntry.flip();
        channel.position((long) idx * 4);
        while (headerEntry.hasRemaining()) {
            channel.write(headerEntry);
        }
    }

    /** Marque un cube comme supprimé (libère ses secteurs). */
    public synchronized void deleteCube(int cubeX, int cubeY, int cubeZ) throws IOException {
        int idx = localIndex(cubeX, cubeY, cubeZ);
        int packed = offsets[idx];
        if (packed == 0) return;

        int offset = packed >>> 8;
        int count  = packed & 0xFF;
        usedSectors.clear(offset, offset + count);

        offsets[idx] = 0;
        ByteBuffer headerEntry = ByteBuffer.allocate(4);
        headerEntry.putInt(0);
        headerEntry.flip();
        channel.position((long) idx * 4);
        while (headerEntry.hasRemaining()) {
            channel.write(headerEntry);
        }
    }

    /** Trouve le premier emplacement libre pouvant accueillir N secteurs contigus. */
    private int allocateSectors(int count) {
        int candidate = HEADER_SECTORS;
        while (true) {
            // Cherche le prochain trou
            int free = usedSectors.nextClearBit(candidate);
            // Vérifie qu'on a `count` secteurs libres consécutifs
            int next = usedSectors.nextSetBit(free);
            if (next == -1 || next - free >= count) {
                return free;
            }
            candidate = next + 1;
        }
    }

    public void flush() throws IOException {
        channel.force(true);
    }

    @Override
    public synchronized void close() throws IOException {
        if (channel.isOpen()) {
            channel.force(true);
            channel.close();
        }
    }

    public Path path() {
        return path;
    }
}