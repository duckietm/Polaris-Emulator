package com.eu.habbo.habbohotel.catalog;

public enum CatalogPageType {

    NORMAL,

    BUILDER,

    BOTH;

    public static CatalogPageType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return NORMAL;
        }

        return switch (value.trim().toUpperCase()) {
            case "BUILDERS_CLUB", "BUILDER", "BC" -> BUILDER;
            case "BOTH" -> BOTH;
            default -> NORMAL;
        };
    }

    public boolean matches(CatalogPageType requestedType) {
        if (this == BOTH || requestedType == BOTH) {
            return true;
        }

        return this == requestedType;
    }
}
