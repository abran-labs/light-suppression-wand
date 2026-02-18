package com.abran.lightsuppression;

import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LightSuppressionManager extends PersistentState {
    private final Set<BlockPos> suppressedPositions = new HashSet<>();
    private boolean needsRelight = false;

    public LightSuppressionManager() {
    }

    private LightSuppressionManager(List<Long> positions) {
        for (long l : positions) {
            suppressedPositions.add(BlockPos.fromLong(l));
        }
        if (!suppressedPositions.isEmpty()) {
            needsRelight = true;
        }
    }

    public static final Codec<LightSuppressionManager> CODEC = Codec.LONG.listOf()
            .fieldOf("suppressed")
            .codec()
            .xmap(
                    LightSuppressionManager::new,
                    manager -> manager.suppressedPositions.stream()
                            .map(BlockPos::asLong)
                            .collect(Collectors.toList())
            );

    public static final PersistentStateType<LightSuppressionManager> TYPE = new PersistentStateType<>(
            "light_suppression_wand",
            LightSuppressionManager::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    public static LightSuppressionManager get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE);
    }

    /**
     * @return true if now suppressed, false if now restored
     */
    public boolean toggle(BlockPos pos) {
        pos = pos.toImmutable();
        if (suppressedPositions.remove(pos)) {
            markDirty();
            return false;
        } else {
            suppressedPositions.add(pos);
            markDirty();
            return true;
        }
    }

    public boolean isSuppressed(BlockPos pos) {
        return suppressedPositions.contains(pos);
    }

    public void remove(BlockPos pos) {
        if (suppressedPositions.remove(pos.toImmutable())) {
            markDirty();
        }
    }

    public boolean needsRelight() {
        return needsRelight;
    }

    public Set<BlockPos> getSuppressedPositions() {
        return suppressedPositions;
    }

    public void clearNeedsRelight() {
        needsRelight = false;
    }
}
