package com.eu.habbo.habbohotel.items.interactions.wired;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A wired save the box refuses with an official localization key, for saves whose method cannot throw the checked
 * {@link com.eu.habbo.messages.incoming.wired.WiredSaveException} (conditions, and boxes that keep their published signature). The save handlers send
 * the key to the editor, with the values of its placeholders.
 */
public class WiredSaveRefusedException extends RuntimeException {
    private final LinkedHashMap<String, String> parameters;

    public WiredSaveRefusedException(String key) {
        this(key, Map.of());
    }

    /** A refusal whose key has placeholders, for example {@code max_usernames} of {@code wiredfurni.error.usernames_count}. */
    public WiredSaveRefusedException(String key, Map<String, String> parameters) {
        super(key);
        this.parameters = parameters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parameters);
    }

    /** The placeholder values of the key, in the order they are sent. */
    public Map<String, String> getParameters() {
        return Collections.unmodifiableMap(this.parameters);
    }
}
