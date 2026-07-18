package com.eu.habbo.messages.contracts;

import java.util.List;
import java.util.Map;

sealed interface WireSchema permits ScalarSchema, ListSchema, OptionalSchema, VariantSchema {
}

record ScalarSchema(String type, String name) implements WireSchema {
}

record ListSchema(String countType, List<WireSchema> item) implements WireSchema {
    ListSchema {
        item = List.copyOf(item);
    }
}

record OptionalSchema(String controller, List<WireSchema> fields) implements WireSchema {
    OptionalSchema {
        fields = List.copyOf(fields);
    }
}

record VariantSchema(String discriminator, Map<String, List<WireSchema>> branches) implements WireSchema {
    VariantSchema {
        branches = branches.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> List.copyOf(entry.getValue())));
    }
}
