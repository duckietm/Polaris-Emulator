package com.eu.habbo.habbohotel.catalog.versioning;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Set;
import java.util.TreeSet;

public final class CatalogChangeFieldDiff {
    private CatalogChangeFieldDiff() {}

    public static java.util.List<String> fields(CatalogChangeEntry change) {
        JsonObject before = object(change.beforeJson());
        JsonObject after = object(change.afterJson());
        Set<String> names = new TreeSet<>();
        names.addAll(before.keySet());
        names.addAll(after.keySet());
        return names.stream()
                .filter(name -> !java.util.Objects.equals(before.get(name), after.get(name)))
                .toList();
    }

    private static JsonObject object(String json) {
        if (json == null || json.isBlank()) return new JsonObject();
        JsonElement value = JsonParser.parseString(json);
        return value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }
}
