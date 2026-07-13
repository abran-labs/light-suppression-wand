package com.abran.lightsuppression.mixin;

import com.abran.lightsuppression.LightSuppressionManager;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.chunk.light.ChunkBlockLightProvider;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkBlockLightProvider.class)
@SuppressWarnings("rawtypes")
public abstract class BlockLightProviderMixin extends ChunkLightProvider {

    protected BlockLightProviderMixin() {
        super(null, null);
    }

    @Inject(method = "getLightSourceLuminance", at = @At("RETURN"), cancellable = true)
    private void suppressLight(long blockPos, BlockState blockState, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() <= 0) return;

        BlockView world = this.chunkProvider.getWorld();
        if (world instanceof ServerWorld serverWorld) {
            if (LightSuppressionManager.get(serverWorld).isSuppressed(BlockPos.fromLong(blockPos))) {
                cir.setReturnValue(0);
            }
        }
    }
}
