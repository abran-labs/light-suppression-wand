package com.abran.lightsuppression;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.BlockState;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;

import java.util.BitSet;

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

            // Force light recalculation and sync to clients
            serverWorld.getChunkManager().getLightingProvider().checkBlock(pos);
            sendLightUpdate(serverWorld, pos);

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
                    sendLightUpdate(world, pos);
                }
                manager.clearNeedsRelight();
            }
        });
    }

    /**
     * After the light engine finishes recalculating, send a LightUpdateS2CPacket
     * to all players tracking the chunk. Uses ServerLightingProvider.enqueue() to
     * wait for the async light engine to finish processing.
     */
    private static void sendLightUpdate(ServerWorld world, BlockPos pos) {
        ServerLightingProvider lightingProvider = (ServerLightingProvider) world.getChunkManager().getLightingProvider();
        ChunkPos centerChunk = new ChunkPos(pos);

        // enqueue() returns a future that completes after light engine POST_UPDATE
        lightingProvider.enqueue(centerChunk.x, centerChunk.z).thenRun(() -> {
            world.getServer().execute(() -> {
                int bottomSection = world.getBottomSectionCoord();
                int blockSection = ChunkSectionPos.getSectionCoord(pos.getY());

                BitSet blockLightBits = new BitSet();
                for (int dy = -1; dy <= 1; dy++) {
                    int idx = blockSection + dy - bottomSection;
                    if (idx >= 0) {
                        blockLightBits.set(idx);
                    }
                }

                // Send for 3x3 chunk area to cover cross-chunk light propagation
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        ChunkPos chunkPos = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                        LightUpdateS2CPacket packet = new LightUpdateS2CPacket(
                                chunkPos, lightingProvider, null, blockLightBits
                        );
                        BlockPos chunkCenter = new BlockPos(
                                chunkPos.getStartX() + 8, pos.getY(), chunkPos.getStartZ() + 8
                        );
                        for (ServerPlayerEntity tracking : PlayerLookup.tracking(world, chunkCenter)) {
                            tracking.networkHandler.sendPacket(packet);
                        }
                    }
                }
            });
        });
    }
}
