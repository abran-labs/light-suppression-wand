package com.abran.lightsuppression.mixin;

import com.abran.lightsuppression.LightSuppressionManager;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {

    @Inject(method = "onBlockStateChanged", at = @At("HEAD"))
    private void cleanupSuppression(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        if (oldState.getLuminance() > 0 && newState.getLuminance() == 0) {
            ServerWorld self = (ServerWorld) (Object) this;
            LightSuppressionManager manager = LightSuppressionManager.get(self);
            if (manager.isSuppressed(pos)) {
                manager.remove(pos);
            }
        }
    }
}
