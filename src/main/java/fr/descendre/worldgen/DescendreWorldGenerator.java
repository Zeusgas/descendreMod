package fr.descendre.worldgen;

import fr.descendre.world.cube.CubePos;
import fr.descendre.world.cube.DescendreCube;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/**
 * Générateur procédural de la dimension Descendre.
 *
 * Important : on ne génère jamais toute la map d'un coup. Le ticker Descendre
 * demande un cube 16x16x16, puis cette classe calcule uniquement les blocs de
 * ce cube. La map est centrée en 0,0, avec un sol à Y=0, un arbre-monde au
 * centre, et une coque de feuilles qui forme un dôme autour de la map.
 */
public final class DescendreWorldGenerator {
    public static final int MAP_RADIUS = 4000;
    public static final int MAP_RADIUS_SQ = MAP_RADIUS * MAP_RADIUS;

    public static final int TREE_HEIGHT = 5000;

    private static final int GROUND_TOP_Y = 0;
    private static final int GROUND_DIRT_MIN_Y = -4;
    private static final int GROUND_STONE_MIN_Y = -48;

    // Tronc : Y=0 à Y=2500 (hauteur réduite par rapport à TREE_HEIGHT)
    private static final int TRUNK_TOP_Y = 2500;
    private static final int TRUNK_BASE_RADIUS = 420;
    private static final int TRUNK_TOP_RADIUS = 90;

    // Feuillage : sphère centrée à Y=3750, rayon 1250 (au lieu de dôme couvrant la map)
    private static final double FOLIAGE_CENTER_X = 0.0;
    private static final double FOLIAGE_CENTER_Y = 3750.0;
    private static final double FOLIAGE_CENTER_Z = 0.0;
    private static final double FOLIAGE_RADIUS = 1250.0;
    private static final double FOLIAGE_RADIUS_SQ = FOLIAGE_RADIUS * FOLIAGE_RADIUS;


    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState WOOD = Blocks.OAK_WOOD.defaultBlockState();
    private static final BlockState LEAVES = Blocks.OAK_LEAVES.defaultBlockState()
            .setValue(LeavesBlock.PERSISTENT, true);

    private static final DomeBranch[] BRANCHES = createBranches();
    private static final Root[] ROOTS = createRoots();

    private DescendreWorldGenerator() {}

    /** Génération active uniquement dans les dimensions du namespace descendre. */
    public static boolean isEnabledFor(ServerLevel level) {
        String id = level.dimension().identifier().toString();
        return id.equals("descendre:void") || id.startsWith("descendre:");
    }

    /**
     * Test rapide au niveau cube. Il évite de parcourir 4096 blocs quand un cube
     * est clairement dans l'air vide.
     */
    public static boolean mayContainGeneratedBlocks(CubePos pos) {
        int minX = pos.x() << 4;
        int minY = pos.y() << 4;
        int minZ = pos.z() << 4;
        int maxX = minX + 15;
        int maxY = minY + 15;
        int maxZ = minZ + 15;

        double minDistSq = distanceSqFromOriginToBoxXZ(minX, maxX, minZ, maxZ);
        double maxDist = maxDistanceFromOriginToBoxXZ(minX, maxX, minZ, maxZ);

        // Sol de la map : disque rayon 4000, surface exactement à Y=0.
        if (minDistSq <= MAP_RADIUS_SQ && rangesOverlap(minY, maxY, GROUND_STONE_MIN_Y, GROUND_TOP_Y)) {
            return true;
        }

        // Tronc central.
        int trunkSearchRadius = TRUNK_BASE_RADIUS + 80;
        if (minDistSq <= trunkSearchRadius * trunkSearchRadius && rangesOverlap(minY, maxY, 0, TRUNK_TOP_Y)) {
            return true;
        }

        // Racines au sol : test par boîtes englobantes, pas juste rayon global.
        if (rangesOverlap(minY, maxY, -16, 120)
                && mayContainRoot(minX, maxX, minY, maxY, minZ, maxZ)) {
            return true;
        }

        // Branches principales : test par boîtes englobantes.
        // Avant, toute la map en Y 450..5000 était considérée candidate, ce qui
        // faisait générer/scanner énormément de cubes vides.
        if (rangesOverlap(minY, maxY, 450, TREE_HEIGHT)
                && mayContainBranch(minX, maxX, minY, maxY, minZ, maxZ)) {
            return true;
        }

        return false;

    }

    /** Génère un cube complet. Retourne null si le cube est entièrement vide. */
    public static DescendreCube generateCube(CubePos pos) {
        if (!mayContainGeneratedBlocks(pos)) {
            return null;
        }

        DescendreCube cube = new DescendreCube(pos);
        boolean hasBlocks = false;

        int baseX = pos.x() << 4;
        int baseY = pos.y() << 4;
        int baseZ = pos.z() << 4;

        for (int ly = 0; ly < 16; ly++) {
            int y = baseY + ly;
            for (int lz = 0; lz < 16; lz++) {
                int z = baseZ + lz;
                for (int lx = 0; lx < 16; lx++) {
                    int x = baseX + lx;

                    BlockState state = generatedStateAt(x, y, z);
                    if (state == null || state.isAir()) continue;

                    cube.setLocal(lx, ly, lz, state);
                    hasBlocks = true;
                }
            }
        }

        if (!hasBlocks) {
            return null;
        }

        // Les cubes naturels ne sont pas sauvegardés tant que le joueur ne les modifie pas.
        cube.markSaved();
        return cube;
    }

    private static BlockState generatedStateAt(int x, int y, int z) {
        if (y > TREE_HEIGHT || y < GROUND_STONE_MIN_Y) return null;

        // Bois prioritaire : tronc, racines, branches.
        if (isTrunkWall(x, y, z)) {
            return WOOD;
        }
        if (isInsideRoot(x, y, z)) {
            return WOOD;
        }
        if (isInsideBranch(x, y, z)) {
            return WOOD;
        }

        // Dôme/coque de feuilles autour de toute la map.
        // On évite le bruit 3D quand le cube est clairement en dehors de la zone du dôme.
        // Sphère de feuilles. Le test isInsideLeafDome a son propre early-out par AABB.
        if (isInsideLeafDome(x, y, z)) {
            return LEAVES;
        }

        // Sol : surface exactement en Y=0 dans le disque de rayon 4000.
        if (isGround(x, y, z)) {
            if (y == GROUND_TOP_Y) return GRASS;
            if (y >= GROUND_DIRT_MIN_Y) return DIRT;
            return STONE;
        }

        return null;
    }

    private static boolean isGround(int x, int y, int z) {
        if (y < GROUND_STONE_MIN_Y || y > GROUND_TOP_Y) return false;
        return (long)x * (long)x + (long)z * (long)z <= MAP_RADIUS_SQ;
    }

    /** Épaisseur de la coque du tronc en blocs. Le tronc n'est plein que sur cette épaisseur. */
    private static final double TRUNK_WALL_THICKNESS = 20.0;

    private static boolean isTrunkWall(int x, int y, int z) {
        if (y < 0 || y > TRUNK_TOP_Y) return false;
        double dx = x - trunkCenterX(y);
        double dz = z - trunkCenterZ(y);
        double distSq = dx * dx + dz * dz;
        double radius = trunkRadiusAt(y);
        // Coque : on est dans le tronc si on est entre (radius - thickness) et radius
        double innerRadius = Math.max(0.0, radius - TRUNK_WALL_THICKNESS);
        return distSq <= radius * radius && distSq >= innerRadius * innerRadius;
    }


    private static double trunkRadiusAt(int y) {
        double t = clamp(y / (double) TRUNK_TOP_Y, 0.0, 1.0);
        double radius = TRUNK_TOP_RADIUS
                + (TRUNK_BASE_RADIUS - TRUNK_TOP_RADIUS) * Math.pow(1.0 - t, 1.7);

        // Renflements lents pour éviter un cône parfait.
        radius += Math.sin(y * 0.006) * 24.0;
        radius += Math.sin(y * 0.021) * 8.0;

        return Math.max(radius, TRUNK_TOP_RADIUS);
    }

    private static double trunkCenterX(int y) {
        return Math.sin(y * 0.0018) * 45.0;
    }

    private static double trunkCenterZ(int y) {
        return Math.cos(y * 0.0021) * 45.0;
    }

    /** Sphère de feuilles : tout point dans la sphère est candidat à devenir feuille, avec bruit. */
    /** Coques concentriques de feuilles : on génère N coques pour simuler le volume sans rien remplir. */
    private static final double[] FOLIAGE_SHELL_RADII = { 1250.0, 1100.0, 950.0, 800.0 };
    /** Épaisseur de chaque coque. */
    private static final double FOLIAGE_SHELL_THICKNESS = 18.0;

    private static boolean isInsideLeafDome(int x, int y, int z) {
        double dx = x - FOLIAGE_CENTER_X;
        double dy = y - FOLIAGE_CENTER_Y;
        double dz = z - FOLIAGE_CENTER_Z;
        double distSq = dx * dx + dy * dy + dz * dz;
        // Early-out : si on est hors de la coque la plus extérieure
        double outerR = FOLIAGE_SHELL_RADII[0];
        if (distSq > outerR * outerR) return false;
        double dist = Math.sqrt(distSq);

        // Trouve la coque correspondante : on est "dedans" si on est à <thickness/2 d'un des radii
        boolean inAnyShell = false;
        for (double shellRadius : FOLIAGE_SHELL_RADII) {
            if (Math.abs(dist - shellRadius) < FOLIAGE_SHELL_THICKNESS) {
                inAnyShell = true;
                break;
            }
        }
        if (!inAnyShell) return false;

        // Bruit pour donner un aspect organique aux coques
        double large = valueNoise3D(x * 0.004, y * 0.004, z * 0.004, 11L);
        double detail = valueNoise3D(x * 0.025, y * 0.025, z * 0.025, 29L);

        // Seuil bas : on garde la plupart des blocs des coques (sauf bord granuleux)
        return large + detail * 0.4 > -0.15;
    }

    private static boolean isInsideBranch(int x, int y, int z) {
        for (DomeBranch b : BRANCHES) {
            if (y < b.minY || y > b.maxY) continue;

            double progress = progressOnSegment(x, y, z, b.sx, b.sy, b.sz, b.ex, b.ey, b.ez);
            if (progress < 0.0 || progress > 1.0) continue;

            double radius = b.radius * Math.pow(1.0 - progress, 0.82) + 5.0;
            double distSq = distanceSqToSegment(x, y, z, b.sx, b.sy, b.sz, b.ex, b.ey, b.ez);

            if (distSq <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    private static boolean mayContainBranch(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        for (DomeBranch b : BRANCHES) {
            if (maxX < b.minX || minX > b.maxX) continue;
            if (maxY < b.minY || minY > b.maxY) continue;
            if (maxZ < b.minZ || minZ > b.maxZ) continue;
            return true;
        }
        return false;
    }

    private static boolean mayContainRoot(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        for (Root r : ROOTS) {
            if (maxX < r.minX || minX > r.maxX) continue;
            if (maxY < r.minY || minY > r.maxY) continue;
            if (maxZ < r.minZ || minZ > r.maxZ) continue;
            return true;
        }
        return false;
    }

    private static boolean isInsideRoot(int x, int y, int z) {
        if (y < -16 || y > 120) return false;

        for (Root r : ROOTS) {
            double progress = progressOnSegment(x, y, z, 0.0, 18.0, 0.0, r.ex, r.ey, r.ez);
            if (progress < 0.0 || progress > 1.0) continue;

            double radius = r.radius * Math.pow(1.0 - progress, 0.68) + 8.0;
            double distSq = distanceSqToSegment(x, y, z, 0.0, 18.0, 0.0, r.ex, r.ey, r.ez);

            if (distSq <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    private static DomeBranch[] createBranches() {
        Random random = new Random(34723981L);
        // 12 branches sortant du haut du tronc, qui vont vers la sphère de feuilles
        DomeBranch[] result = new DomeBranch[12];

        for (int i = 0; i < result.length; i++) {
            double angle = (Math.PI * 2.0 / result.length) * i;
            angle += random.nextDouble() * 0.3 - 0.15;

            // Branches sortent du tronc à différentes hauteurs entre Y=1800 et Y=2400
            int startY = 1800 + random.nextInt(600);
            double startRadius = trunkRadiusAt(startY) * 0.85;

            double sx = trunkCenterX(startY) + Math.cos(angle) * startRadius;
            double sy = startY;
            double sz = trunkCenterZ(startY) + Math.sin(angle) * startRadius;

            // Extrémité dans la sphère de feuilles (rayon horizontal modéré, Y vers le centre)
            double endHorizDist = 600.0 + random.nextDouble() * 500.0;
            double ex = Math.cos(angle + random.nextDouble() * 0.2 - 0.1) * endHorizDist;
            double ez = Math.sin(angle + random.nextDouble() * 0.2 - 0.1) * endHorizDist;
            // Branches montent vers la sphère
            double ey = startY + 600.0 + random.nextDouble() * 400.0;

            // Branches plus fines que dans l'ancienne version (vrai arbre)
            double radius = 22.0 + random.nextDouble() * 18.0;
            result[i] = new DomeBranch(sx, sy, sz, ex, ey, ez, radius);
        }

        return result;
    }

    private static Root[] createRoots() {
        Random random = new Random(982451653L);
        Root[] result = new Root[32];

        for (int i = 0; i < result.length; i++) {
            double angle = (Math.PI * 2.0 / result.length) * i;
            angle += random.nextDouble() * 0.28 - 0.14;

            double length = 2100.0 + random.nextDouble() * 1750.0;
            double ex = Math.cos(angle) * length;
            double ez = Math.sin(angle) * length;
            double ey = -2.0 + random.nextDouble() * 22.0;
            double radius = 42.0 + random.nextDouble() * 58.0;

            result[i] = new Root(ex, ey, ez, radius);
        }

        return result;
    }

    private static double progressOnSegment(
            double px, double py, double pz,
            double sx, double sy, double sz,
            double ex, double ey, double ez
    ) {
        double vx = ex - sx;
        double vy = ey - sy;
        double vz = ez - sz;
        double lenSq = vx * vx + vy * vy + vz * vz;
        if (lenSq <= 1.0E-9) return -1.0;

        return ((px - sx) * vx + (py - sy) * vy + (pz - sz) * vz) / lenSq;
    }

    private static double distanceSqToSegment(
            double px, double py, double pz,
            double sx, double sy, double sz,
            double ex, double ey, double ez
    ) {
        double t = clamp(progressOnSegment(px, py, pz, sx, sy, sz, ex, ey, ez), 0.0, 1.0);

        double cx = sx + (ex - sx) * t;
        double cy = sy + (ey - sy) * t;
        double cz = sz + (ez - sz) * t;

        double dx = px - cx;
        double dy = py - cy;
        double dz = pz - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean rangesOverlap(int aMin, int aMax, int bMin, int bMax) {
        return aMax >= bMin && bMax >= aMin;
    }

    private static double distanceSqFromOriginToBoxXZ(int minX, int maxX, int minZ, int maxZ) {
        double dx = 0.0;
        if (maxX < 0) dx = -maxX;
        else if (minX > 0) dx = minX;

        double dz = 0.0;
        if (maxZ < 0) dz = -maxZ;
        else if (minZ > 0) dz = minZ;

        return dx * dx + dz * dz;
    }

    private static double maxDistanceFromOriginToBoxXZ(int minX, int maxX, int minZ, int maxZ) {
        double x = Math.max(Math.abs(minX), Math.abs(maxX));
        double z = Math.max(Math.abs(minZ), Math.abs(maxZ));
        return Math.sqrt(x * x + z * z);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double valueNoise3D(double x, double y, double z, long salt) {
        int x0 = fastFloor(x);
        int y0 = fastFloor(y);
        int z0 = fastFloor(z);

        double fx = smoothstep(x - x0);
        double fy = smoothstep(y - y0);
        double fz = smoothstep(z - z0);

        double c000 = hashNoise(x0, y0, z0, salt);
        double c100 = hashNoise(x0 + 1, y0, z0, salt);
        double c010 = hashNoise(x0, y0 + 1, z0, salt);
        double c110 = hashNoise(x0 + 1, y0 + 1, z0, salt);
        double c001 = hashNoise(x0, y0, z0 + 1, salt);
        double c101 = hashNoise(x0 + 1, y0, z0 + 1, salt);
        double c011 = hashNoise(x0, y0 + 1, z0 + 1, salt);
        double c111 = hashNoise(x0 + 1, y0 + 1, z0 + 1, salt);

        double x00 = lerp(c000, c100, fx);
        double x10 = lerp(c010, c110, fx);
        double x01 = lerp(c001, c101, fx);
        double x11 = lerp(c011, c111, fx);

        double y0v = lerp(x00, x10, fy);
        double y1v = lerp(x01, x11, fy);

        return lerp(y0v, y1v, fz);
    }

    private static int fastFloor(double value) {
        int i = (int)value;
        return value < i ? i - 1 : i;
    }

    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double hashNoise(int x, int y, int z, long salt) {
        long h = salt;
        h ^= x * 0x9E3779B97F4A7C15L;
        h ^= y * 0xC2B2AE3D27D4EB4FL;
        h ^= z * 0x165667B19E3779F9L;
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;

        // Convertit 53 bits aléatoires en [-1, 1].
        double unit = ((h >>> 11) & ((1L << 53) - 1)) / (double)(1L << 53);
        return unit * 2.0 - 1.0;
    }

    private static final class DomeBranch {
        final double sx;
        final double sy;
        final double sz;
        final double ex;
        final double ey;
        final double ez;
        final double radius;
        final int minX;
        final int maxX;
        final int minY;
        final int maxY;
        final int minZ;
        final int maxZ;

        DomeBranch(double sx, double sy, double sz, double ex, double ey, double ez, double radius) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.ex = ex;
            this.ey = ey;
            this.ez = ez;
            this.radius = radius;
            double margin = radius + 24.0;
            this.minX = (int)Math.floor(Math.min(sx, ex) - margin);
            this.maxX = (int)Math.ceil(Math.max(sx, ex) + margin);
            this.minY = (int)Math.floor(Math.min(sy, ey) - margin);
            this.maxY = (int)Math.ceil(Math.max(sy, ey) + margin);
            this.minZ = (int)Math.floor(Math.min(sz, ez) - margin);
            this.maxZ = (int)Math.ceil(Math.max(sz, ez) + margin);
        }
    }

    private static final class Root {
        final double ex;
        final double ey;
        final double ez;
        final double radius;
        final int minX;
        final int maxX;
        final int minY;
        final int maxY;
        final int minZ;
        final int maxZ;

        Root(double ex, double ey, double ez, double radius) {
            this.ex = ex;
            this.ey = ey;
            this.ez = ez;
            this.radius = radius;
            double margin = radius + 24.0;
            this.minX = (int)Math.floor(Math.min(0.0, ex) - margin);
            this.maxX = (int)Math.ceil(Math.max(0.0, ex) + margin);
            this.minY = (int)Math.floor(Math.min(18.0, ey) - margin);
            this.maxY = (int)Math.ceil(Math.max(18.0, ey) + margin);
            this.minZ = (int)Math.floor(Math.min(0.0, ez) - margin);
            this.maxZ = (int)Math.ceil(Math.max(0.0, ez) + margin);
        }
    }
}