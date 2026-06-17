package com.eu.habbo.util;

/**
 * Escapes the LIKE wildcards {@code %} and {@code _} (and the escape char itself)
 * in user-supplied search input, so they are matched literally instead of acting
 * as wildcards. Prevents wildcard-driven over-broad matches and the expensive
 * full-scans an attacker could trigger with a query like {@code "%"}. Uses
 * MariaDB's default escape character {@code \}.
 */
public final class SqlLikeEscaper {

    private SqlLikeEscaper() {
    }

    public static String escape(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
