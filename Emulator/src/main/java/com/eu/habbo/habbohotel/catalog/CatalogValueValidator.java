package com.eu.habbo.habbohotel.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class CatalogValueValidator {
    private CatalogValueValidator() {
    }

    public static List<String> validate(Values values) {
        List<String> findings = new ArrayList<>();
        if (values.id() <= 0) add(findings, values.id(), "id must be positive");
        if (values.credits() < 0) add(findings, values.id(), "cost_credits must not be negative");
        if (values.points() < 0) add(findings, values.id(), "cost_points must not be negative");
        if (values.pointsType() < 0) add(findings, values.id(), "points_type must not be negative");
        if (values.amount() <= 0) add(findings, values.id(), "amount must be positive");
        if (values.limitedStack() < 0) add(findings, values.id(), "limited_stack must not be negative");
        if (values.limitedSells() < 0 || values.limitedSells() > values.limitedStack()) {
            add(findings, values.id(), "limited_sells must be between zero and limited_stack");
        }
        return List.copyOf(findings);
    }

    private static void add(List<String> findings, int id, String message) {
        findings.add("catalog item " + id + ": " + message);
    }

    public record Values(int id, int credits, int points, int pointsType, int amount,
                         int limitedStack, int limitedSells) {
        public static Values from(ResultSet result) throws SQLException {
            return new Values(
                    result.getInt("id"),
                    result.getInt("cost_credits"),
                    result.getInt("cost_points"),
                    result.getInt("points_type"),
                    result.getInt("amount"),
                    result.getInt("limited_stack"),
                    result.getInt("limited_sells"));
        }
    }
}
