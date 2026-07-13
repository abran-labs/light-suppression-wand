package com.abran.lightsuppression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

record LightSuppressionConfig(Set<Item> wandItems, boolean requireSneaking, boolean operatorOnly, int durabilityCost) {
    private static final String FILE_NAME = "light-suppression-wand.json";
    private static final String DEFAULT_WAND_ITEM = "minecraft:golden_hoe";
    private static final Set<String> KNOWN_FIELDS = Set.of(
            "wand_items", "require_sneaking", "operator_only", "durability_cost"
    );
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    LightSuppressionConfig {
        wandItems = Set.copyOf(wandItems);
    }

    static LightSuppressionConfig load() {
        return load(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME));
    }

    static LightSuppressionConfig load(Path path) {
        Values values = loadValues(path);
        Set<String> itemIds = resolveWandItemIds(values.wandItems(),
                id -> Registries.ITEM.getOptionalValue(id).isPresent());
        Set<Item> items = new HashSet<>();
        for (String itemId : itemIds) {
            Registries.ITEM.getOptionalValue(Identifier.of(itemId)).ifPresent(items::add);
        }
        return new LightSuppressionConfig(items, values.requireSneaking(), values.operatorOnly(), values.durabilityCost());
    }

    static Values loadValues(Path path) {
        if (Files.notExists(path)) {
            writeDefaults(path);
            return defaultValues();
        }

        try {
            JsonElement root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                throw new IllegalArgumentException("root must be an object");
            }
            return parse(root.getAsJsonObject());
        } catch (IOException | RuntimeException exception) {
            LightSuppressionWand.LOGGER.error("Could not read config {}; using defaults", path, exception);
            return defaultValues();
        }
    }

    private static Values parse(JsonObject root) {
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!KNOWN_FIELDS.contains(entry.getKey())) {
                LightSuppressionWand.LOGGER.warn("Ignoring unknown config field '{}'", entry.getKey());
            }
        }

        Set<String> wandItems = root.has("wand_items") ? parseWandItems(root.get("wand_items")) : defaultWandItemIds();
        boolean requireSneaking = root.has("require_sneaking")
                ? requireBoolean("require_sneaking", root.get("require_sneaking")) : true;
        boolean operatorOnly = root.has("operator_only")
                ? requireBoolean("operator_only", root.get("operator_only")) : false;
        int durabilityCost = root.has("durability_cost")
                ? parseDurabilityCost(root.get("durability_cost")) : 0;
        return new Values(wandItems, requireSneaking, operatorOnly, durabilityCost);
    }

    private static Set<String> parseWandItems(JsonElement element) {
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("wand_items must be an array");
        }

        Set<String> itemIds = new HashSet<>();
        JsonArray array = element.getAsJsonArray();
        for (JsonElement entry : array) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("wand_items entries must be strings");
            }
            String itemId = entry.getAsString();
            Identifier id = Identifier.tryParse(itemId);
            if (id == null) {
                LightSuppressionWand.LOGGER.warn("Ignoring invalid wand item ID '{}'", itemId);
                continue;
            }
            itemIds.add(id.toString());
        }

        if (itemIds.isEmpty()) {
            LightSuppressionWand.LOGGER.warn("No valid wand item IDs configured; using {}", DEFAULT_WAND_ITEM);
            return defaultWandItemIds();
        }
        return itemIds;
    }

    static Set<String> resolveWandItemIds(Set<String> itemIds, Predicate<Identifier> registeredItem) {
        Set<String> resolved = new HashSet<>();
        for (String itemId : itemIds) {
            Identifier id = Identifier.of(itemId);
            if (registeredItem.test(id)) {
                resolved.add(itemId);
            } else {
                LightSuppressionWand.LOGGER.warn("Ignoring unknown wand item ID '{}'", itemId);
            }
        }
        if (resolved.isEmpty()) {
            LightSuppressionWand.LOGGER.warn("No valid wand items configured; using {}", DEFAULT_WAND_ITEM);
            return defaultWandItemIds();
        }
        return resolved;
    }

    private static boolean requireBoolean(String name, JsonElement element) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(name + " must be a boolean");
        }
        return element.getAsBoolean();
    }

    private static int parseDurabilityCost(JsonElement element) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("durability_cost must be an integer");
        }
        try {
            int cost = new BigDecimal(element.getAsString()).intValueExact();
            if (cost < 0) {
                LightSuppressionWand.LOGGER.warn("Negative durability_cost {}; using 0", cost);
                return 0;
            }
            return cost;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("durability_cost must be an in-range integer", exception);
        }
    }

    private static Values defaultValues() {
        return new Values(defaultWandItemIds(), true, false, 0);
    }

    private static Set<String> defaultWandItemIds() {
        return Set.of(DEFAULT_WAND_ITEM);
    }

    private static void writeDefaults(Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JsonObject defaults = new JsonObject();
            JsonArray wandItems = new JsonArray();
            wandItems.add(DEFAULT_WAND_ITEM);
            defaults.add("wand_items", wandItems);
            defaults.addProperty("require_sneaking", true);
            defaults.addProperty("operator_only", false);
            defaults.addProperty("durability_cost", 0);
            Files.writeString(path, GSON.toJson(defaults) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
        } catch (IOException exception) {
            LightSuppressionWand.LOGGER.error("Could not create default config {}; using in-memory defaults", path, exception);
        }
    }

    record Values(Set<String> wandItems, boolean requireSneaking, boolean operatorOnly, int durabilityCost) {
        Values {
            wandItems = Set.copyOf(wandItems);
        }
    }
}
