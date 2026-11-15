package com.srth.plugin.ActionItems;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RoleplayFurniMenuRegistry {
    private static final int MAX_MENU_BUTTONS = 100;

    private final Map<String, Menu> menus;

    private RoleplayFurniMenuRegistry(Map<String, Menu> menus) {
        this.menus = Map.copyOf(menus);
    }

    public static RoleplayFurniMenuRegistry empty() {
        return new RoleplayFurniMenuRegistry(Map.of());
    }

    public static RoleplayFurniMenuRegistry load(Path path) throws IOException {
        JsonElement rootElement = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));

        if (!rootElement.isJsonObject()) {
            throw new IllegalArgumentException("Roleplay furni menu configuration must be a JSON object.");
        }

        JsonElement menusElement = rootElement.getAsJsonObject().get("menus");

        if (menusElement == null || !menusElement.isJsonObject()) {
            throw new IllegalArgumentException(
                    "Roleplay furni menu configuration must contain an object named 'menus'.");
        }

        Map<String, Menu> menus = new LinkedHashMap<>();
        JsonObject menuObjects = menusElement.getAsJsonObject();

        for (Map.Entry<String, JsonElement> entry : menuObjects.entrySet()) {
            String furniName = normalizeName(entry.getKey());

            if (furniName.isEmpty() || !entry.getValue().isJsonObject()) {
                throw new IllegalArgumentException(
                        "Each roleplay furni menu must be an object with a non-empty furni name.");
            }

            JsonObject menuObject = entry.getValue().getAsJsonObject();
            String title = optionalString(menuObject, "title", entry.getKey());
            JsonElement buttonsElement = menuObject.get("buttons");

            if (buttonsElement == null || !buttonsElement.isJsonArray()) {
                throw new IllegalArgumentException("Menu '" + entry.getKey() + "' must contain a 'buttons' array.");
            }

            JsonArray buttonObjects = buttonsElement.getAsJsonArray();

            if (buttonObjects.size() > MAX_MENU_BUTTONS) {
                throw new IllegalArgumentException(
                        "Menu '" + entry.getKey() + "' cannot define more than " + MAX_MENU_BUTTONS + " buttons.");
            }

            List<Button> buttons = new ArrayList<>(buttonObjects.size());

            for (JsonElement buttonElement : buttonObjects) {
                if (!buttonElement.isJsonObject()) {
                    throw new IllegalArgumentException(
                            "Every button in menu '" + entry.getKey() + "' must be an object.");
                }

                JsonObject buttonObject = buttonElement.getAsJsonObject();
                String label = requiredString(buttonObject, "label", entry.getKey());
                String actionId = requiredString(buttonObject, "actionId", entry.getKey());

                buttons.add(new Button(label, actionId));
            }

            if (buttons.isEmpty()) {
                throw new IllegalArgumentException("Menu '" + entry.getKey() + "' must define at least one button.");
            }

            if (menus.putIfAbsent(furniName, new Menu(title, List.copyOf(buttons))) != null) {
                throw new IllegalArgumentException("Duplicate roleplay furni menu name: " + entry.getKey());
            }
        }

        return new RoleplayFurniMenuRegistry(menus);
    }

    public Menu getMenu(String furniName) {
        if (furniName == null || furniName.isBlank()) {
            return null;
        }

        return this.menus.get(normalizeName(furniName));
    }

    private static String requiredString(JsonObject object, String key, String menuName) {
        String value = optionalString(object, key, "");

        if (value.isBlank()) {
            throw new IllegalArgumentException("Menu '" + menuName + "' requires a non-empty '" + key + "'.");
        }

        return value;
    }

    private static String optionalString(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);

        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            return fallback;
        }

        String value = element.getAsString().trim();

        return value.isEmpty() ? fallback : value;
    }

    private static String normalizeName(String furniName) {
        return furniName.trim().toLowerCase(Locale.ROOT);
    }

    public record Button(String label, String actionId) {}

    public record Menu(String title, List<Button> buttons) {}
}
