package fr.descendre.mixin;

import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(DimensionType.class)
public abstract class DimensionTypeCodecConstantsMixin {
    @ModifyConstant(
            method = "createDimensionCodec",
            constant = @Constant(intValue = 4064),
            require = 0
    )
    private static int descendre$replaceMaxCodecHeight(int original) {
        System.out.println("[Descendre] DimensionType constant patch: 4064 -> 32768");
        return 32768;
    }

    @ModifyConstant(
            method = "createDimensionCodec",
            constant = @Constant(intValue = 16),
            require = 0
    )
    private static int descendre$replaceMinHeight(int original) {
        System.out.println("[Descendre] DimensionType constant patch: 16 -> 1");
        return 1;
    }

    @ModifyConstant(
            method = "createDimensionCodec",
            constant = @Constant(intValue = -2032),
            require = 0
    )
    private static int descendre$replaceMinY(int original) {
        System.out.println("[Descendre] DimensionType constant patch: -2032 -> -32768");
        return -32768;
    }

    @ModifyConstant(
            method = "createDimensionCodec",
            constant = @Constant(intValue = 2031),
            require = 0
    )
    private static int descendre$replaceMaxY(int original) {
        System.out.println("[Descendre] DimensionType constant patch: 2031 -> 32767");
        return 32767;
    }

    @ModifyConstant(
            method = "createDimensionCodec",
            constant = @Constant(intValue = 2032),
            require = 0
    )
    private static int descendre$replaceTopLimit(int original) {
        System.out.println("[Descendre] DimensionType constant patch: 2032 -> 32768");
        return 32768;
    }
}