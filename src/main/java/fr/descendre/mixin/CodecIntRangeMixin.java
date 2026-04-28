package fr.descendre.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Codec.class, remap = false)
public interface CodecIntRangeMixin {
    @Inject(
            method = "intRange(II)Lcom/mojang/serialization/Codec;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void descendre$widenCodecIntRange(
            int minInclusive,
            int maxInclusive,
            CallbackInfoReturnable<Codec<Integer>> cir
    ) {
        if (minInclusive == 0 && maxInclusive == 4064) {
            System.out.println("[Descendre] Codec.intRange patch: [0:4064] -> [0:32768]");
            cir.setReturnValue(descendre$range(0, 32768));
            return;
        }

        if (minInclusive == 16 && maxInclusive == 4064) {
            System.out.println("[Descendre] Codec.intRange patch: [16:4064] -> [16:32768]");
            cir.setReturnValue(descendre$range(16, 32768));
            return;
        }

        if (minInclusive == -2032 && maxInclusive == 2031) {
            System.out.println("[Descendre] Codec.intRange patch: [-2032:2031] -> [-32768:32767]");
            cir.setReturnValue(descendre$range(-32768, 32767));
        }
    }

    private static Codec<Integer> descendre$range(int minInclusive, int maxInclusive) {
        return Codec.INT.validate(value -> {
            if (value >= minInclusive && value <= maxInclusive) {
                return DataResult.success(value);
            }

            return DataResult.error(() ->
                    "Value " + value + " outside of Descendre range ["
                            + minInclusive + ":" + maxInclusive + "]"
            );
        });
    }
}