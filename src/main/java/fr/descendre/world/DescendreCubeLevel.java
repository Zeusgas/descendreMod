package fr.descendre.world;

import fr.descendre.world.cube.CubeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper de ServerLevel qui redirige les lectures/écritures de blocs vers le CubeMap
 * pour les positions hors range vanilla. Les autres opérations (entités, lumière, etc.)
 * passent par le ServerLevel réel.
 *
 * Utilisé par DescendreNetwork pour appeler BlockItem.useOn() en faisant croire à vanilla
 * qu'il interagit avec un Level normal, alors qu'on stocke en réalité dans CubeMap.
 *
 * Implémentation : on n'étend pas ServerLevel (impossible sans gros patches), on créé
 * une classe qui intercepte les méthodes critiques via un mixin sur ServerLevel,
 * en s'appuyant sur un ThreadLocal qui dit "on est en mode cubic".
 *
 * Approche choisie : ThreadLocal "context cubic" qui marque que pour cette opération
 * synchrone, les setBlock/getBlockState doivent passer par CubeMap.
 */
public final class DescendreCubeLevel {

    /** Marqueur ThreadLocal : si non-null, on est dans un contexte de placement cubic. */
    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

    public static final class Context {
        public final ServerLevel level;
        public final CubeMap map;
        /** Cache des blocs modifiés pendant l'opération (pour cohérence des lookups successifs). */
        public final Map<BlockPos, BlockState> dirtyOverride = new HashMap<>();

        public Context(ServerLevel level, CubeMap map) {
            this.level = level;
            this.map = map;
        }
    }

    private DescendreCubeLevel() {}

    /** Active le contexte cubic pour la durée d'une opération. */
    public static void runWithContext(ServerLevel level, CubeMap map, Runnable action) {
        Context prev = CONTEXT.get();
        CONTEXT.set(new Context(level, map));
        try {
            action.run();
        } finally {
            CONTEXT.set(prev);
        }
    }

    /** Retourne le contexte actuel ou null si on n'est pas dans un placement cubic. */
    public static Context current() {
        return CONTEXT.get();
    }

    public static boolean isActive() {
        return CONTEXT.get() != null;
    }

    /** Appelé par les mixins pour intercepter getBlockState. */
    public static BlockState getBlockState(BlockPos pos) {
        Context ctx = CONTEXT.get();
        if (ctx == null) return null;

        // Vérifie d'abord les modifs en cours (placement multi-bloc comme une porte)
        BlockState dirty = ctx.dirtyOverride.get(pos.immutable());
        if (dirty != null) return dirty;

        BlockState cubic = ctx.map.getBlock(pos);
        if (cubic != null && !cubic.isAir()) return cubic;
        return null; // null = laisser le vrai level répondre
    }

    /** Appelé par les mixins pour intercepter setBlock. */
    public static boolean setBlock(BlockPos pos, BlockState state) {
        Context ctx = CONTEXT.get();
        if (ctx == null) return false;

        BlockPos immutable = pos.immutable();
        ctx.dirtyOverride.put(immutable, state);
        ctx.map.setBlock(immutable, state);
        return true;
    }
}