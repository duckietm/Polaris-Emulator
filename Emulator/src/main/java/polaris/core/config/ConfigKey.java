package polaris.core.config;

import java.util.List;

public record ConfigKey(
        String name,
        ValueType type,
        String defaultValue,
        String environmentAlias,
        Source source,
        boolean restartRequired,
        boolean liveReload,
        List<String> deprecatedAliases,
        String description) {

    public ConfigKey {
        deprecatedAliases = List.copyOf(deprecatedAliases);
    }

    public void validate(String value) {
        switch (this.type) {
            case BOOLEAN -> {
                String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
                if (!normalized.equals("true")
                        && !normalized.equals("false")
                        && !normalized.equals("1")
                        && !normalized.equals("0")) {
                    throw new IllegalArgumentException("expected true, false, 1, or 0");
                }
            }
            case INTEGER -> Integer.parseInt(value.trim());
            case LONG -> Long.parseLong(value.trim());
            case DOUBLE -> Double.parseDouble(value.trim());
            case STRING -> {
                // Every string is valid; subsystem binders may impose a
                // narrower domain validator.
            }
        }
    }

    public enum ValueType {
        STRING,
        BOOLEAN,
        INTEGER,
        LONG,
        DOUBLE
    }

    public enum Source {
        STARTUP,
        DATABASE,
        ENVIRONMENT,
        PLUGIN
    }
}
