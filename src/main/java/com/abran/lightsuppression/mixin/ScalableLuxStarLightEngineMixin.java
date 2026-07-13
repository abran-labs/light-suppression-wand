package com.abran.lightsuppression.mixin;

import com.abran.lightsuppression.LightSuppressionManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.chunk.ChunkProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "ca.spottedleaf.starlight.common.light.StarLightEngine")
public class ScalableLuxStarLightEngineMixin {

    @Shadow
    @Final
    protected BlockPos.Mutable mutablePos1;

    @WrapOperation(
            method = "performLightDecrease",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;getLuminance()I"),
            require = 0
    )
    private int wrapDecreaseEmission(BlockState state, Operation<Integer> original,
            @Local(argsOnly = true, ordinal = 0) ChunkProvider lightAccess) {
        int emission = original.call(state);
        if (emission <= 0) return emission;
        BlockView world = lightAccess.getWorld();
        if (world instanceof ServerWorld serverWorld) {
            if (LightSuppressionManager.get(serverWorld).isSuppressed(
                    new BlockPos(mutablePos1.getX(), mutablePos1.getY(), mutablePos1.getZ()))) {
                return 0;
            }
        }
        return emission;
    }
}
