package com.eu.habbo.database.observability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class SlowQuerySql {

    private static final Pattern NUMERIC_LITERAL = Pattern.compile(
            "(?<![A-Za-z0-9_$])(?:0[xX][0-9A-Fa-f]+|[-+]?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)(?![A-Za-z0-9_$])");

    private SlowQuerySql() {
    }

    public static String sanitize(String sql, int maxLength) {
        if (sql == null || sql.isBlank()) {
            return "<unknown>";
        }

        StringBuilder result = new StringBuilder(Math.min(sql.length(), maxLength));
        boolean pendingSpace = false;

        for (int index = 0; index < sql.length();) {
            if (result.length() >= maxLength + 64) {
                break;
            }
            char current = sql.charAt(index);

            if (Character.isWhitespace(current)) {
                pendingSpace = result.length() > 0;
                index++;
                continue;
            }

            if (current == '#') {
                index = skipLineComment(sql, index + 1);
                pendingSpace = result.length() > 0;
                continue;
            }

            if (current == '-' && index + 1 < sql.length() && sql.charAt(index + 1) == '-') {
                index = skipLineComment(sql, index + 2);
                pendingSpace = result.length() > 0;
                continue;
            }

            if (current == '/' && index + 1 < sql.length() && sql.charAt(index + 1) == '*') {
                index = skipBlockComment(sql, index + 2);
                pendingSpace = result.length() > 0;
                continue;
            }

            if (pendingSpace) {
                result.append(' ');
                pendingSpace = false;
            }

            if (current == '\'' || current == '"') {
                result.append('?');
                index = skipQuotedLiteral(sql, index + 1, current);
                continue;
            }

            if (current == '`') {
                index = copyQuotedIdentifier(sql, index, result);
                continue;
            }

            result.append(current);
            index++;
        }

        String sanitized = NUMERIC_LITERAL.matcher(result.toString().strip()).replaceAll("?");
        if (sanitized.length() <= maxLength) {
            return sanitized;
        }

        return sanitized.substring(0, maxLength - 3).stripTrailing() + "...";
    }

    public static String fingerprint(String sql) {
        return fingerprintSanitized(sanitize(sql, 8_192));
    }

    static String fingerprintSanitized(String sanitizedSql) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sanitizedSql.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static int skipLineComment(String sql, int index) {
        while (index < sql.length() && sql.charAt(index) != '\n' && sql.charAt(index) != '\r') {
            index++;
        }
        return index;
    }

    private static int skipBlockComment(String sql, int index) {
        while (index + 1 < sql.length()) {
            if (sql.charAt(index) == '*' && sql.charAt(index + 1) == '/') {
                return index + 2;
            }
            index++;
        }
        return sql.length();
    }

    private static int skipQuotedLiteral(String sql, int index, char quote) {
        while (index < sql.length()) {
            char current = sql.charAt(index++);
            if (current == '\\' && index < sql.length()) {
                index++;
                continue;
            }
            if (current == quote) {
                if (index < sql.length() && sql.charAt(index) == quote) {
                    index++;
                    continue;
                }
                return index;
            }
        }
        return index;
    }

    private static int copyQuotedIdentifier(String sql, int index, StringBuilder result) {
        result.append('`');
        index++;
        while (index < sql.length()) {
            char current = sql.charAt(index++);
            result.append(current);
            if (current == '`') {
                if (index < sql.length() && sql.charAt(index) == '`') {
                    result.append('`');
                    index++;
                    continue;
                }
                break;
            }
        }
        return index;
    }
}
