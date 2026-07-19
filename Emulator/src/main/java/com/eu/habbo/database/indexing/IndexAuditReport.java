package com.eu.habbo.database.indexing;

import java.util.List;

public record IndexAuditReport(
        int requiredIndexes,
        int coveredRequirements,
        List<String> missingRequirements,
        List<String> redundantCandidates) {

    public IndexAuditReport {
        missingRequirements = List.copyOf(missingRequirements);
        redundantCandidates = List.copyOf(redundantCandidates);
    }

    public boolean isComplete() {
        return missingRequirements.isEmpty();
    }
}
