package fr.descendre.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Rendu skybox de l'arbre central lointain.
 *
 * Géométrie :
 *  - Tronc : cylindre 16 faces, rayon 60, Y=0 à Y=2300
 *  - Branches : 12 branches (2 anneaux) partant du haut du tronc
 *  - Feuillage : grosse sphère écrasée verticalement (plus large que haute), centrée à Y=3500
 *    couvre Y=2200 à Y=5000
 *
 * Position monde : XZ=(0, 0).
 *
 * Rendu seulement quand la caméra est à plus de 200 blocs horizontalement.
 */
public final class DescendreSkyboxArbre {

    private static final DescendreSkyboxArbre INSTANCE = new DescendreSkyboxArbre();

    public static DescendreSkyboxArbre get() {
        return INSTANCE;
    }

    // ===== Paramètres géométriques =====

    private static final double TREE_X = 0.0;
    private static final double TREE_Z = 0.0;

    /** Tronc : un cylindre vertical. */
    private static final double TRUNK_RADIUS = 60.0;
    private static final double TRUNK_BOTTOM_Y = 0.0;
    private static final double TRUNK_TOP_Y = 2300.0;
    private static final int TRUNK_SEGMENTS = 12;

    /** Branches : connectent le tronc au feuillage. */
    private static final int BRANCH_COUNT_PER_RING = 6;
    private static final int BRANCH_RING_COUNT = 2;
    private static final double[] BRANCH_RING_Y = { 1800.0, 2200.0 };
    private static final double BRANCH_LENGTH = 700.0;
    private static final double BRANCH_RISE = 800.0;
    private static final double BRANCH_BASE_RADIUS = 20.0;
    private static final double BRANCH_TIP_RADIUS = 5.0;
    private static final int BRANCH_SEGMENTS = 6;

    /** Feuillage : sphère écrasée verticalement (look "boule d'arbre"). */
    private static final double FOLIAGE_CENTER_Y = 3500.0;
    private static final double FOLIAGE_RADIUS = 1400.0;
    /** Scale vertical : <1.0 = plus large que haut (look de chêne). 1.0 = sphère parfaite. */
    private static final double FOLIAGE_VERTICAL_SCALE = 1.07;  // donne haut Y≈5000, bas Y≈2000
    private static final int FOLIAGE_LATITUDES = 12;
    private static final int FOLIAGE_LONGITUDES = 18;

    /** Distance horizontale minimale pour rendre le skybox. */
    private static final double MIN_RENDER_DIST = 200.0;
    private static final double MIN_RENDER_DIST_SQ = MIN_RENDER_DIST * MIN_RENDER_DIST;

    // ===== Mesh statique =====

    private record SkyboxQuad(
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            float nx, float ny, float nz,
            boolean isFoliage
    ) {}

    private List<SkyboxQuad> quads;

    private DescendreSkyboxArbre() {}

    private void ensureBuilt() {
        if (quads != null) return;
        quads = new ArrayList<>();
        buildTrunk();
        buildBranches();
        buildFoliageSphere();
    }

    /** Construit un cylindre fermé entre deux centres avec deux rayons différents. */
    private void buildCylinder(
            double cx1, double cy1, double cz1, double r1,
            double cx2, double cy2, double cz2, double r2,
            int segments, boolean isFoliage
    ) {
        double ax = cx2 - cx1;
        double ay = cy2 - cy1;
        double az = cz2 - cz1;
        double axisLen = Math.sqrt(ax * ax + ay * ay + az * az);
        if (axisLen < 1e-6) return;
        ax /= axisLen; ay /= axisLen; az /= axisLen;

        // Deux vecteurs perpendiculaires à l'axe
        double ux, uy, uz;
        if (Math.abs(ay) > 0.9) {
            // axe presque vertical : ux = axe × (1,0,0)
            ux = 0; uy = 0; uz = 1;
            double crossX = ay * uz - az * uy;
            double crossY = az * ux - ax * uz;
            double crossZ = ax * uy - ay * ux;
            double crossLen = Math.sqrt(crossX*crossX + crossY*crossY + crossZ*crossZ);
            ux = crossX / crossLen;
            uy = crossY / crossLen;
            uz = crossZ / crossLen;
        } else {
            ux = az;
            uy = 0;
            uz = -ax;
            double uLen = Math.sqrt(ux*ux + uy*uy + uz*uz);
            ux /= uLen; uy /= uLen; uz /= uLen;
        }
        double vx = ay * uz - az * uy;
        double vy = az * ux - ax * uz;
        double vz = ax * uy - ay * ux;

        for (int i = 0; i < segments; i++) {
            double a1 = (Math.PI * 2 * i) / segments;
            double a2 = (Math.PI * 2 * (i + 1)) / segments;
            double cos1 = Math.cos(a1), sin1 = Math.sin(a1);
            double cos2 = Math.cos(a2), sin2 = Math.sin(a2);

            double rx1 = ux * cos1 + vx * sin1;
            double ry1 = uy * cos1 + vy * sin1;
            double rz1 = uz * cos1 + vz * sin1;
            double rx2 = ux * cos2 + vx * sin2;
            double ry2 = uy * cos2 + vy * sin2;
            double rz2 = uz * cos2 + vz * sin2;

            float p1x = (float) (cx1 + rx1 * r1);
            float p1y = (float) (cy1 + ry1 * r1);
            float p1z = (float) (cz1 + rz1 * r1);
            float p2x = (float) (cx1 + rx2 * r1);
            float p2y = (float) (cy1 + ry2 * r1);
            float p2z = (float) (cz1 + rz2 * r1);
            float p3x = (float) (cx2 + rx2 * r2);
            float p3y = (float) (cy2 + ry2 * r2);
            float p3z = (float) (cz2 + rz2 * r2);
            float p4x = (float) (cx2 + rx1 * r2);
            float p4y = (float) (cy2 + ry1 * r2);
            float p4z = (float) (cz2 + rz1 * r2);

            float nx = (float) ((rx1 + rx2) * 0.5);
            float ny = (float) ((ry1 + ry2) * 0.5);
            float nz = (float) ((rz1 + rz2) * 0.5);

            quads.add(new SkyboxQuad(
                    p1x, p1y, p1z,
                    p2x, p2y, p2z,
                    p3x, p3y, p3z,
                    p4x, p4y, p4z,
                    nx, ny, nz,
                    isFoliage
            ));
        }
    }

    private void buildTrunk() {
        buildCylinder(
                TREE_X, TRUNK_BOTTOM_Y, TREE_Z, TRUNK_RADIUS,
                TREE_X, TRUNK_TOP_Y, TREE_Z, TRUNK_RADIUS,
                TRUNK_SEGMENTS,
                false
        );
    }

    private void buildBranches() {
        for (int ring = 0; ring < BRANCH_RING_COUNT; ring++) {
            double startY = BRANCH_RING_Y[ring];
            double angleOffset = (ring == 0) ? 0.0 : Math.PI / BRANCH_COUNT_PER_RING;

            for (int b = 0; b < BRANCH_COUNT_PER_RING; b++) {
                double angle = angleOffset + (Math.PI * 2 * b) / BRANCH_COUNT_PER_RING;
                double dirX = Math.cos(angle);
                double dirZ = Math.sin(angle);

                double baseX = TREE_X + dirX * TRUNK_RADIUS;
                double baseY = startY;
                double baseZ = TREE_Z + dirZ * TRUNK_RADIUS;

                double tipX = TREE_X + dirX * (TRUNK_RADIUS + BRANCH_LENGTH);
                double tipY = startY + BRANCH_RISE;
                double tipZ = TREE_Z + dirZ * (TRUNK_RADIUS + BRANCH_LENGTH);

                buildCylinder(
                        baseX, baseY, baseZ, BRANCH_BASE_RADIUS,
                        tipX, tipY, tipZ, BRANCH_TIP_RADIUS,
                        BRANCH_SEGMENTS,
                        false
                );
            }
        }
    }

    /** Sphère écrasée verticalement (look "couronne d'arbre"). */
    private void buildFoliageSphere() {
        double centerX = TREE_X;
        double centerY = FOLIAGE_CENTER_Y;
        double centerZ = TREE_Z;
        double radius = FOLIAGE_RADIUS;

        for (int lat = 0; lat < FOLIAGE_LATITUDES; lat++) {
            double phi1 = (Math.PI * lat) / FOLIAGE_LATITUDES;
            double phi2 = (Math.PI * (lat + 1)) / FOLIAGE_LATITUDES;

            // Scale vertical pour une forme oblate
            double y1 = centerY + radius * FOLIAGE_VERTICAL_SCALE * Math.cos(phi1);
            double r1 = radius * Math.sin(phi1);
            double y2 = centerY + radius * FOLIAGE_VERTICAL_SCALE * Math.cos(phi2);
            double r2 = radius * Math.sin(phi2);

            for (int lon = 0; lon < FOLIAGE_LONGITUDES; lon++) {
                double theta1 = (Math.PI * 2 * lon) / FOLIAGE_LONGITUDES;
                double theta2 = (Math.PI * 2 * (lon + 1)) / FOLIAGE_LONGITUDES;

                double cos1 = Math.cos(theta1), sin1 = Math.sin(theta1);
                double cos2 = Math.cos(theta2), sin2 = Math.sin(theta2);

                float vx1 = (float) (centerX + cos1 * r1);
                float vz1 = (float) (centerZ + sin1 * r1);
                float vx2 = (float) (centerX + cos2 * r1);
                float vz2 = (float) (centerZ + sin2 * r1);
                float vx3 = (float) (centerX + cos2 * r2);
                float vz3 = (float) (centerZ + sin2 * r2);
                float vx4 = (float) (centerX + cos1 * r2);
                float vz4 = (float) (centerZ + sin1 * r2);

                double midTheta = (theta1 + theta2) * 0.5;
                double midPhi = (phi1 + phi2) * 0.5;
                float nx = (float) (Math.cos(midTheta) * Math.sin(midPhi));
                float ny = (float) Math.cos(midPhi);
                float nz = (float) (Math.sin(midTheta) * Math.sin(midPhi));

                quads.add(new SkyboxQuad(
                        vx1, (float) y1, vz1,
                        vx2, (float) y1, vz2,
                        vx3, (float) y2, vz3,
                        vx4, (float) y2, vz4,
                        nx, ny, nz,
                        true
                ));
            }
        }
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
        double dx = TREE_X - cameraPos.x;
        double dz = TREE_Z - cameraPos.z;
        if (dx * dx + dz * dz < MIN_RENDER_DIST_SQ) return;

        ensureBuilt();

        Minecraft mc = Minecraft.getInstance();
        var modelManager = mc.getModelManager();
        TextureAtlasSprite logSprite = modelManager
                .getBlockModelShaper()
                .getParticleIcon(Blocks.OAK_LOG.defaultBlockState());
        TextureAtlasSprite leafSprite = modelManager
                .getBlockModelShaper()
                .getParticleIcon(Blocks.OAK_LEAVES.defaultBlockState());

        if (logSprite == null || leafSprite == null) return;

        VertexConsumer solidConsumer = bufferSource.getBuffer(Sheets.solidBlockSheet());
        VertexConsumer cutoutConsumer = bufferSource.getBuffer(Sheets.cutoutBlockSheet());

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Matrix4f pose = poseStack.last().pose();
        int packedLight = (15 << 20) | (15 << 4);
        int packedOverlay = 0;

        for (SkyboxQuad quad : quads) {
            VertexConsumer consumer = quad.isFoliage ? cutoutConsumer : solidConsumer;
            TextureAtlasSprite sprite = quad.isFoliage ? leafSprite : logSprite;

            float u0 = sprite.getU0();
            float u1 = sprite.getU1();
            float v0 = sprite.getV0();
            float v1 = sprite.getV1();

            consumer.addVertex(pose, quad.x1, quad.y1, quad.z1)
                    .setColor(1.0f, 1.0f, 1.0f, 1.0f)
                    .setUv(u0, v0)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(quad.nx, quad.ny, quad.nz);
            consumer.addVertex(pose, quad.x2, quad.y2, quad.z2)
                    .setColor(1.0f, 1.0f, 1.0f, 1.0f)
                    .setUv(u1, v0)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(quad.nx, quad.ny, quad.nz);
            consumer.addVertex(pose, quad.x3, quad.y3, quad.z3)
                    .setColor(1.0f, 1.0f, 1.0f, 1.0f)
                    .setUv(u1, v1)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(quad.nx, quad.ny, quad.nz);
            consumer.addVertex(pose, quad.x4, quad.y4, quad.z4)
                    .setColor(1.0f, 1.0f, 1.0f, 1.0f)
                    .setUv(u0, v1)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(quad.nx, quad.ny, quad.nz);
        }

        poseStack.popPose();
    }
}