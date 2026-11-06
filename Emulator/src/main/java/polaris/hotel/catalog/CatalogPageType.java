package polaris.hotel.catalog;

public enum CatalogPageType {
    NORMAL,

    BUILDER,

    BOTH;

    public static CatalogPageType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return NORMAL;
        }

        switch (value.trim().toUpperCase()) {
            case "BUILDERS_CLUB":
            case "BUILDER":
            case "BC":
                return BUILDER;
            case "BOTH":
                return BOTH;
            case "NORMAL":
            default:
                return NORMAL;
        }
    }

    public boolean matches(CatalogPageType requestedType) {
        if (this == BOTH || requestedType == BOTH) {
            return true;
        }

        return this == requestedType;
    }
}
