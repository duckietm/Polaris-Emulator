package com.eu.habbo.habbohotel.items;

import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ItemInteractionRegistry {
    private final Map<String, ItemInteraction> interactionsByName = new LinkedHashMap<>();
    private final Map<Class<? extends HabboItem>, ItemInteraction> interactionsByType = new LinkedHashMap<>();

    boolean add(ItemInteraction interaction) {
        String key = normalize(interaction.getName());
        ItemInteraction previous = interactionsByName.get(key);
        if (previous != null) {
            throw new IllegalStateException("Built-in interaction key already registered: " + interaction.getName());
        }

        interactionsByName.put(key, interaction);
        if (interaction.getType() != null) {
            interactionsByType.putIfAbsent(interaction.getType(), interaction);
        }
        return true;
    }

    void addChecked(ItemInteraction itemInteraction) {
        ItemInteraction sameName = interactionsByName.get(normalize(itemInteraction.getName()));
        ItemInteraction sameType =
                itemInteraction.getType() == null ? null : interactionsByType.get(itemInteraction.getType());
        ItemInteraction conflict = sameName != null ? sameName : sameType;
        if (conflict != null) {
            throw new RuntimeException("Interaction Types must be unique. An class with type: "
                    + conflict.getClass().getName() + " was already added OR the key: "
                    + conflict.getName() + " is already in use.");
        }

        add(itemInteraction);
    }

    ItemInteraction find(Class<? extends HabboItem> type) {
        return interactionsByType.get(type);
    }

    ItemInteraction find(String type) {
        return interactionsByName.get(normalize(type));
    }

    List<String> sortedNames() {
        List<String> names = new ArrayList<>();
        for (ItemInteraction interaction : interactionsByName.values()) {
            names.add(interaction.getName());
        }
        Collections.sort(names);
        return names;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
