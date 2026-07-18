package com.eu.habbo.tools.furni;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemsBaseSqlDumpReader {
    private static final String INSERT = "insert into `items_base`";

    private ItemsBaseSqlDumpReader() {
    }

    public static List<FurniConsistencyValidator.ItemBase> read(String sql) {
        Map<Integer, FurniConsistencyValidator.ItemBase> items = new LinkedHashMap<>();
        String lower = sql.toLowerCase(Locale.ROOT);
        int searchFrom = 0;
        while (true) {
            int insert = lower.indexOf(INSERT, searchFrom);
            if (insert < 0) break;
            int columnsStart = sql.indexOf('(', insert + INSERT.length());
            int columnsEnd = columnsStart < 0 ? -1 : sql.indexOf(')', columnsStart + 1);
            if (columnsEnd < 0) break;
            int values = lower.indexOf("values", columnsEnd);
            if (values < 0) break;

            List<String> columns = splitColumns(sql.substring(columnsStart + 1, columnsEnd));
            Map<String, Integer> indexes = new HashMap<>();
            for (int index = 0; index < columns.size(); index++) indexes.put(columns.get(index), index);
            requireColumns(indexes);

            int cursor = values + "values".length();
            while (cursor < sql.length()) {
                cursor = skipWhitespaceAndCommas(sql, cursor);
                if (cursor >= sql.length() || sql.charAt(cursor) == ';') {
                    cursor++;
                    break;
                }
                if (sql.charAt(cursor) != '(') {
                    throw new IllegalArgumentException("invalid items_base VALUES tuple near offset " + cursor);
                }
                ParsedTuple tuple = parseTuple(sql, cursor + 1);
                List<String> fields = tuple.fields();
                FurniConsistencyValidator.ItemBase item = new FurniConsistencyValidator.ItemBase(
                        integer(fields, indexes, "id"),
                        integer(fields, indexes, "sprite_id"),
                        string(fields, indexes, "item_name"),
                        string(fields, indexes, "type"),
                        string(fields, indexes, "interaction_type"));
                items.put(item.id(), item);
                cursor = tuple.nextIndex();
            }
            searchFrom = Math.max(cursor, columnsEnd + 1);
        }
        return List.copyOf(items.values());
    }

    private static List<String> splitColumns(String value) {
        List<String> columns = new ArrayList<>();
        for (String column : value.split(",")) {
            columns.add(column.strip().replace("`", "").toLowerCase(Locale.ROOT));
        }
        return columns;
    }

    private static ParsedTuple parseTuple(String sql, int cursor) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        while (cursor < sql.length()) {
            char value = sql.charAt(cursor++);
            if (quoted) {
                if (value == '\\' && cursor < sql.length()) {
                    field.append(sql.charAt(cursor++));
                } else if (value == '\'' && cursor < sql.length() && sql.charAt(cursor) == '\'') {
                    field.append('\'');
                    cursor++;
                } else if (value == '\'') {
                    quoted = false;
                } else {
                    field.append(value);
                }
                continue;
            }
            if (value == '\'') {
                quoted = true;
            } else if (value == ',') {
                fields.add(field.toString().strip());
                field.setLength(0);
            } else if (value == ')') {
                fields.add(field.toString().strip());
                return new ParsedTuple(List.copyOf(fields), cursor);
            } else {
                field.append(value);
            }
        }
        throw new IllegalArgumentException("unterminated items_base VALUES tuple");
    }

    private static int skipWhitespaceAndCommas(String sql, int cursor) {
        while (cursor < sql.length()) {
            char value = sql.charAt(cursor);
            if (!Character.isWhitespace(value) && value != ',') break;
            cursor++;
        }
        return cursor;
    }

    private static int integer(List<String> fields, Map<String, Integer> indexes, String column) {
        return Integer.parseInt(fields.get(indexes.get(column)));
    }

    private static String string(List<String> fields, Map<String, Integer> indexes, String column) {
        String value = fields.get(indexes.get(column));
        return value.equalsIgnoreCase("NULL") ? "" : value;
    }

    private static void requireColumns(Map<String, Integer> indexes) {
        for (String column : List.of("id", "sprite_id", "item_name", "type", "interaction_type")) {
            if (!indexes.containsKey(column)) throw new IllegalArgumentException("items_base dump is missing " + column);
        }
    }

    private record ParsedTuple(List<String> fields, int nextIndex) {
    }
}
