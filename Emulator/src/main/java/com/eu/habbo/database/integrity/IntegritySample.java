package com.eu.habbo.database.integrity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record IntegritySample(Map<String, String> values, long occurrences) {
    public IntegritySample {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        if (occurrences < 1) throw new IllegalArgumentException("occurrences must be positive");
    }
}
