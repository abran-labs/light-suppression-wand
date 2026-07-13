package com.abran.lightsuppression;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightSuppressionConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void absentFileCreatesDefaults() throws IOException {
        Path configPath = tempDir.resolve("nested/light-suppression-wand.json");

        LightSuppressionConfig.Values config = LightSuppressionConfig.loadValues(configPath);

        assertDefaults(config);
        assertTrue(Files.exists(configPath));
        JsonObject generated = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
        assertEquals("minecraft:golden_hoe", generated.getAsJsonArray("wand_items").get(0).getAsString());
        assertTrue(generated.get("require_sneaking").getAsBoolean());
        assertFalse(generated.get("operator_only").getAsBoolean());
        assertEquals(0, generated.get("durability_cost").getAsInt());
    }

    @Test
    void validMultiItemConfigLoads() throws IOException {
        Path configPath = write("""
                {"wand_items":["minecraft:golden_hoe","minecraft:diamond_hoe"],"require_sneaking":false,"operator_only":true,"durability_cost":3}
                """);

        LightSuppressionConfig.Values config = LightSuppressionConfig.loadValues(configPath);

        assertEquals(2, config.wandItems().size());
        assertTrue(config.wandItems().contains("minecraft:golden_hoe"));
        assertTrue(config.wandItems().contains("minecraft:diamond_hoe"));
        assertFalse(config.requireSneaking());
        assertTrue(config.operatorOnly());
        assertEquals(3, config.durabilityCost());
    }

    @Test
    void missingFieldsUseDefaults() throws IOException {
        LightSuppressionConfig.Values config = LightSuppressionConfig.loadValues(write("{\"operator_only\":true}"));

        assertTrue(config.wandItems().contains("minecraft:golden_hoe"));
        assertTrue(config.requireSneaking());
        assertTrue(config.operatorOnly());
        assertEquals(0, config.durabilityCost());
    }

    @Test
    void invalidOrUnknownItemsFallBackToGoldenHoe() throws IOException {
        LightSuppressionConfig.Values config = LightSuppressionConfig.loadValues(write("{\"wand_items\":[\"bad id\",\"minecraft:not_an_item\"]}"));
        assertEquals(Set.of("minecraft:not_an_item"), config.wandItems());

        Set<String> resolved = LightSuppressionConfig.resolveWandItemIds(config.wandItems(), id -> false);

        assertEquals(Set.of("minecraft:golden_hoe"), resolved);
    }

    @Test
    void mixedRegisteredAndUnknownItemsKeepRegisteredItems() {
        Set<String> resolved = LightSuppressionConfig.resolveWandItemIds(
                Set.of("minecraft:golden_hoe", "example:missing"),
                id -> id.equals(Identifier.of("minecraft:golden_hoe"))
        );

        assertEquals(Set.of("minecraft:golden_hoe"), resolved);
    }

    @Test
    void negativeDurabilityClampsToZero() throws IOException {
        LightSuppressionConfig.Values config = LightSuppressionConfig.loadValues(write("{\"durability_cost\":-1}"));

        assertEquals(0, config.durabilityCost());
    }

    @Test
    void malformedConfigIsUnchangedAndUsesDefaults() throws IOException {
        Path configPath = write("{");

        LightSuppressionConfig.Values config = LightSuppressionConfig.loadValues(configPath);

        assertDefaults(config);
        assertEquals("{", Files.readString(configPath));
    }

    @Test
    void wrongTypesUseCompleteDefaultsAndUnknownKeysAreIgnored() throws IOException {
        LightSuppressionConfig.Values config = LightSuppressionConfig.loadValues(write("{\"wand_items\":\"minecraft:diamond_hoe\",\"require_sneaking\":false,\"unused\":true}"));

        assertDefaults(config);
    }

    private Path write(String contents) throws IOException {
        Path configPath = tempDir.resolve("light-suppression-wand.json");
        Files.writeString(configPath, contents);
        return configPath;
    }

    private static void assertDefaults(LightSuppressionConfig.Values config) {
        assertEquals(1, config.wandItems().size());
        assertTrue(config.wandItems().contains("minecraft:golden_hoe"));
        assertTrue(config.requireSneaking());
        assertFalse(config.operatorOnly());
        assertEquals(0, config.durabilityCost());
    }
}
