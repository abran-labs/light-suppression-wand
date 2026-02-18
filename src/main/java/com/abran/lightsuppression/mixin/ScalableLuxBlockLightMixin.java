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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "ca.spottedleaf.starlight.common.light.BlockStarLightEngine")
public class ScalableLuxBlockLightMixin {

    @WrapOperation(
            method = "checkBlock",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;getLuminance()I"),
            require = 0
    )
    private int wrapCheckBlockEmission(BlockState state, Operation<Integer> original,
            @Local(argsOnly = true, ordinal = 0) ChunkProvider lightAccess,
            @Local(argsOnly = true, ordinal = 0) int worldX,
            @Local(argsOnly = true, ordinal = 1) int worldY,
            @Local(argsOnly = true, ordinal = 2) int worldZ) {
        int emission = original.call(state);
        if (emission <= 0) return emission;
        BlockView world = lightAccess.getWorld();
        if (world instanceof ServerWorld serverWorld) {
            if (LightSuppressionManager.get(serverWorld).isSuppressed(new BlockPos(worldX, worldY, worldZ))) {
                return 0;
            }
        }
        return emission;
    }

    @WrapOperation(
            method = "calculateLightValue",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;getLuminance()I"),
            require = 0
    )
    private int wrapCalculateLightEmission(BlockState state, Operation<Integer> original,
            @Local(argsOnly = true, ordinal = 0) ChunkProvider lightAccess,
            @Local(argsOnly = true, ordinal = 0) int worldX,
            @Local(argsOnly = true, ordinal = 1) int worldY,
            @Local(argsOnly = true, ordinal = 2) int worldZ) {
        int emission = original.call(state);
        if (emission <= 0) return emission;
        BlockView world = lightAccess.getWorld();
        if (world instanceof ServerWorld serverWorld) {
            if (LightSuppressionManager.get(serverWorld).isSuppressed(new BlockPos(worldX, worldY, worldZ))) {
                return 0;
            }
        }
        return emission;
    }

    @Inject(method = "getSources", at = @At("RETURN"), cancellable = true, require = 0)
    private void filterSuppressedSources(CallbackInfoReturnable<List<BlockPos>> cir,
            @Local(argsOnly = true, ordinal = 0) ChunkProvider lightAccess) {
        BlockView world = lightAccess.getWorld();
        if (world instanceof ServerWorld serverWorld) {
            LightSuppressionManager manager = LightSuppressionManager.get(serverWorld);
            List<BlockPos> sources = cir.getReturnValue();
            if (sources.stream().anyMatch(manager::isSuppressed)) {
                List<BlockPos> filtered = new ArrayList<>(sources);
                filtered.removeIf(manager::isSuppressed);
                cir.setReturnValue(filtered);
            }
        }
    }
}
