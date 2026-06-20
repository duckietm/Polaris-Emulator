package com.eu.habbo.messages;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RuntimeValidationReport {

    private final List<RuntimeValidationIssue> errors = new ArrayList<>();

    public void addError(String message) {
        this.errors.add(new RuntimeValidationIssue(message));
    }

    public void merge(RuntimeValidationReport report) {
        this.errors.addAll(report.errors);
    }

    public boolean hasErrors() {
        return !this.errors.isEmpty();
    }

    public List<RuntimeValidationIssue> errors() {
        return Collections.unmodifiableList(this.errors);
    }

    public void logErrors(Logger logger, String label) {
        if (!this.hasErrors()) {
            return;
        }

        logger.error("{} failed with {} error(s):", label, this.errors.size());

        for (RuntimeValidationIssue issue : this.errors) {
            logger.error(" - {}", issue.message());
        }
    }
}
