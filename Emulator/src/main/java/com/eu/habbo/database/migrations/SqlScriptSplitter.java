package com.eu.habbo.database.migrations;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SqlScriptSplitter {
    private SqlScriptSplitter() {
    }

    public static List<String> split(String sql) {
        Objects.requireNonNull(sql, "sql");

        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        State state = State.NORMAL;
        boolean lineStart = true;
        boolean hasExecutableContent = false;

        for (int index = 0; index < sql.length(); index++) {
            char character = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';

            switch (state) {
                case NORMAL -> {
                    if (lineStart && isDelimiterDirective(sql, index)) {
                        throw new MigrationValidationException(
                                "DELIMITER directives are not supported in migration scripts");
                    }
                    if (character == '-' && next == '-') {
                        current.append(character).append(next);
                        index++;
                        state = State.LINE_COMMENT;
                        lineStart = false;
                    } else if (character == '#') {
                        current.append(character);
                        state = State.LINE_COMMENT;
                        lineStart = false;
                    } else if (character == '/' && next == '*') {
                        current.append(character).append(next);
                        index++;
                        state = State.BLOCK_COMMENT;
                        lineStart = false;
                    } else if (character == '\'') {
                        current.append(character);
                        state = State.SINGLE_QUOTE;
                        hasExecutableContent = true;
                        lineStart = false;
                    } else if (character == '"') {
                        current.append(character);
                        state = State.DOUBLE_QUOTE;
                        hasExecutableContent = true;
                        lineStart = false;
                    } else if (character == '`') {
                        current.append(character);
                        state = State.BACKTICK;
                        hasExecutableContent = true;
                        lineStart = false;
                    } else if (character == ';') {
                        addStatement(statements, current, hasExecutableContent);
                        current.setLength(0);
                        hasExecutableContent = false;
                        lineStart = false;
                    } else {
                        current.append(character);
                        if (!Character.isWhitespace(character)) hasExecutableContent = true;
                        lineStart = updateLineStart(lineStart, character);
                    }
                }
                case SINGLE_QUOTE, DOUBLE_QUOTE, BACKTICK -> {
                    current.append(character);
                    char quote = state.quote;
                    if (character == '\\' && next != '\0') {
                        current.append(next);
                        index++;
                    } else if (character == quote && next == quote) {
                        current.append(next);
                        index++;
                    } else if (character == quote) {
                        state = State.NORMAL;
                    }
                    lineStart = updateLineStart(lineStart, character);
                }
                case LINE_COMMENT -> {
                    current.append(character);
                    if (character == '\n' || character == '\r') state = State.NORMAL;
                    lineStart = updateLineStart(lineStart, character);
                }
                case BLOCK_COMMENT -> {
                    current.append(character);
                    if (character == '*' && next == '/') {
                        current.append(next);
                        index++;
                        state = State.NORMAL;
                        lineStart = false;
                    } else {
                        lineStart = updateLineStart(lineStart, character);
                    }
                }
            }
        }

        if (state != State.NORMAL && state != State.LINE_COMMENT) {
            throw new MigrationValidationException(
                    "Migration SQL ends with an unterminated " + state.description);
        }

        addStatement(statements, current, hasExecutableContent);
        return List.copyOf(statements);
    }

    private static boolean isDelimiterDirective(String sql, int index) {
        String keyword = "delimiter";
        if (index + keyword.length() > sql.length()
                || !sql.regionMatches(true, index, keyword, 0, keyword.length())) {
            return false;
        }
        int boundary = index + keyword.length();
        return boundary == sql.length() || Character.isWhitespace(sql.charAt(boundary));
    }

    private static boolean updateLineStart(boolean lineStart, char character) {
        if (character == '\n' || character == '\r') return true;
        return lineStart && (character == ' ' || character == '\t' || character == '\f');
    }

    private static void addStatement(
            List<String> statements,
            StringBuilder current,
            boolean hasExecutableContent) {
        if (!hasExecutableContent) return;
        String statement = current.toString().strip();
        if (!statement.isEmpty()) statements.add(statement);
    }

    private enum State {
        NORMAL('\0', "statement"),
        SINGLE_QUOTE('\'', "single-quoted string"),
        DOUBLE_QUOTE('"', "double-quoted string"),
        BACKTICK('`', "backtick-quoted identifier"),
        LINE_COMMENT('\0', "line comment"),
        BLOCK_COMMENT('\0', "block comment");

        private final char quote;
        private final String description;

        State(char quote, String description) {
            this.quote = quote;
            this.description = description;
        }
    }
}
