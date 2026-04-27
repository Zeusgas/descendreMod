package fr.descendre.mixin;

import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(DimensionType.class)
public abstract class DimensionTypeHeightMixin {
    @ModifyConstant(
            method = "*",
            constant = @Constant(intValue = 4064),
            require = 0
    )
    private static int descendre$increaseHeightLimit4064(int original) {
        System.out.println("[Descendre] Patch DimensionType: 4064 -> 32768");
        return 32768;
    }

    @ModifyConstant(
            method = "*",
            constant = @Constant(intValue = 2032),
            require = 0
    )
    private static int descendre$increaseTopLimit2032(int original) {
        System.out.println("[Descendre] Patch DimensionType: 2032 -> 32768");
        return 32768;
    }

    @ModifyConstant(
            method = "*",
            constant = @Constant(intValue = 2031),
            require = 0
    )
    private static int descendre$increaseTopLimit2031(int original) {
        System.out.println("[Descendre] Patch DimensionType: 2031 -> 32767");
        return 32767;
    }

    @ModifyConstant(
            method = "*",
            constant = @Constant(intValue = -2032),
            require = 0
    )
    private static int descendre$decreaseBottomLimitNegative2032(int original) {
        System.out.println("[Descendre] Patch DimensionType: -2032 -> -32768");
        return -32768;
    }
}