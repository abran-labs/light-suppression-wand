package com.abran.lightsuppression;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

public class LightSuppressionWand implements ModInitializer {
    @Override
    public void onInitialize() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!player.isSneaking()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!player.getStackInHand(hand).isOf(Items.GOLDEN_HOE)) return ActionResult.PASS;

            ServerWorld serverWorld = (ServerWorld) world;
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);

            LightSuppressionManager manager = LightSuppressionManager.get(serverWorld);

            // Allow toggle if block emits light OR is already suppressed
            if (state.getLuminance() == 0 && !manager.isSuppressed(pos)) {
                return ActionResult.PASS;
            }

            boolean suppressed = manager.toggle(pos);

            // Force light recalculation
            serverWorld.getChunkManager().getLightingProvider().checkBlock(pos);

            // Feedback
            if (suppressed) {
                player.sendMessage(Text.literal("Light Suppressed!").formatted(Formatting.GOLD), true);
                serverWorld.playSound(null, pos, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.0f, 1.0f);
            } else {
                player.sendMessage(Text.literal("Light Restored!").formatted(Formatting.GREEN), true);
                serverWorld.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.0f, 1.0f);
            }
            serverWorld.spawnParticles(ParticleTypes.END_ROD,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    10, 0.3, 0.3, 0.3, 0.05);

            return ActionResult.SUCCESS;
        });

        // Re-apply suppressions after world load (handles chunk lighting on restart)
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            LightSuppressionManager manager = LightSuppressionManager.get(world);
            if (manager.needsRelight()) {
                for (BlockPos pos : manager.getSuppressedPositions()) {
                    world.getChunkManager().getLightingProvider().checkBlock(pos);
                }
                manager.clearNeedsRelight();
            }
        });
    }
}
