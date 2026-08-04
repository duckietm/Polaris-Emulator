package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CatalogSqlTokenizer {
    public List<String> statements(String sql) {
        Objects.requireNonNull(sql, "sql");
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int index = 0; index < sql.length(); index++) {
            char c = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';
            if (lineComment) {
                if (c == '\n' || c == '\r') {
                    lineComment = false;
                    current.append(' ');
                }
                continue;
            }
            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false;
                    index++;
                    current.append(' ');
                }
                continue;
            }
            if (quoted) {
                current.append(c);
                if (c == '\\' && next != '\0') current.append(sql.charAt(++index));
                else if (c == '\'' && next == '\'') current.append(sql.charAt(++index));
                else if (c == '\'') quoted = false;
                continue;
            }
            if (c == '\'') {
                quoted = true;
                current.append(c);
            } else if (c == '-' && next == '-') {
                lineComment = true;
                index++;
            } else if (c == '/' && next == '*' && index + 2 < sql.length() && sql.charAt(index + 2) == '!') {
                throw new IllegalArgumentException("Executable SQL comments are forbidden");
            } else if (c == '/' && next == '*') {
                blockComment = true;
                index++;
            } else if (c == ';') {
                add(statements, current);
            } else current.append(c);
        }
        if (quoted || blockComment) throw new IllegalArgumentException("Unterminated SQL string or comment");
        add(statements, current);
        return List.copyOf(statements);
    }

    private static void add(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        current.setLength(0);
        if (!statement.isEmpty()) statements.add(statement);
    }
}
