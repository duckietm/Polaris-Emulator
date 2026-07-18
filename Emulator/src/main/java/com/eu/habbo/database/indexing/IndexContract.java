package com.eu.habbo.database.indexing;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class IndexContract {
    private final List<IndexRequirement> requirements;

    public IndexContract(List<IndexRequirement> requirements) {
        Objects.requireNonNull(requirements, "requirements");
        if (requirements.isEmpty()) throw new IllegalArgumentException("Index contract must not be empty");

        Set<String> names = new HashSet<>();
        Set<String> definitions = new HashSet<>();
        for (IndexRequirement requirement : requirements) {
            Objects.requireNonNull(requirement, "requirement");
            if (!names.add(requirement.table() + "." + requirement.name())) {
                throw new IllegalArgumentException(
                        "Duplicate index name in contract: " + requirement.table() + "." + requirement.name());
            }
            if (!definitions.add(requirement.table() + ":" + String.join(",", requirement.columns()))) {
                throw new IllegalArgumentException(
                        "Duplicate index definition in contract: " + requirement.displayName());
            }
        }
        this.requirements = List.copyOf(requirements);
    }

    public List<IndexRequirement> requirements() {
        return requirements;
    }
}
