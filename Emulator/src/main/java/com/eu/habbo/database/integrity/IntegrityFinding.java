package com.eu.habbo.database.integrity;

import java.util.List;
import java.util.Objects;

public record IntegrityFinding(
        String checkId,
        IntegrityFindingType type,
        IntegrityCheckSource source,
        long affectedRows,
        long groups,
        List<IntegritySample> samples,
        String description) {

    public IntegrityFinding {
        checkId = IntegrityIdentifiers.checkId(checkId);
        type = Objects.requireNonNull(type, "type");
        source = Objects.requireNonNull(source, "source");
        if (affectedRows < 1 || groups < 1) {
            throw new IllegalArgumentException("A finding must affect at least one row and group");
        }
        samples = List.copyOf(samples);
        description = IntegrityIdentifiers.description(description);
    }
}
