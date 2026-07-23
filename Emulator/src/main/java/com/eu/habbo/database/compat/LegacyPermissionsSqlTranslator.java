package com.eu.habbo.database.compat;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates legacy SQL written against the old single permissions table into
 * the normalized Polaris schema (permission_ranks + permission_definitions).
 *
 * Old plugins register command permissions like this:
 *
 *   ALTER TABLE `permissions` ADD `cmd_randomword` ENUM('0','1') NOT NULL DEFAULT '0'
 *   UPDATE permissions SET cmd_randomword = '1' WHERE id >= 5
 *   SELECT cmd_randomword FROM permissions WHERE id = 7
 *
 * which becomes, respectively, an INSERT into permission_definitions, a CASE
 * update of the rank_&lt;id&gt; columns, and a pivoted SELECT. Statements that
 * only touch rank metadata (rank_name, badge, ...) are redirected to
 * permission_ranks 1:1.
 *
 * Only active when the normalized schema is in use; on a legacy database every
 * statement passes through untouched.
 */
public class LegacyPermissionsSqlTranslator implements LegacySqlTranslator {

    static final String LEGACY_TABLE = "permissions";
    static final String DEFINITIONS_TABLE = "permission_definitions";
    static final String RANKS_TABLE = "permission_ranks";

    private static final String REGISTERED_COMMENT = "Registered by a legacy plugin through the Polaris legacy bridge";

    /**
     * Columns that lived on the legacy permissions table but describe the rank
     * itself; they moved to permission_ranks unchanged.
     */
    private static final Set<String> RANK_METADATA_COLUMNS = Set.of(
            "id", "rank_name", "hidden_rank", "badge", "job_description",
            "staff_color", "staff_background", "level", "room_effect",
            "log_commands", "prefix", "prefix_color", "auto_credits_amount",
            "auto_pixels_amount", "auto_gotw_amount", "auto_points_amount",
            "soundboard_cooldown_seconds");

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private static final Pattern ALTER_STATEMENT = Pattern.compile("(?is)^\\s*ALTER\\s+TABLE\\s+`?permissions`?\\s+(.*?)\\s*;?\\s*$");
    private static final Pattern ALTER_ADD_CLAUSE = Pattern.compile("(?is)^ADD\\s+(?:COLUMN\\s+)?(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([A-Za-z_][A-Za-z0-9_]*)`?\\s+(.*)$");
    private static final Pattern ALTER_DROP_CLAUSE = Pattern.compile("(?is)^DROP\\s+(?:COLUMN\\s+)?(?:IF\\s+EXISTS\\s+)?`?([A-Za-z_][A-Za-z0-9_]*)`?\\s*$");
    private static final Pattern ENUM_TYPE = Pattern.compile("(?is)^ENUM\\s*\\(([^)]*)\\)");

    private static final Pattern UPDATE_STATEMENT = Pattern.compile("(?is)^\\s*UPDATE\\s+(`?)permissions\\1\\s+SET\\s+(.*?)\\s*;?\\s*$");
    private static final Pattern UPDATE_TABLE_REF = Pattern.compile("(?is)^(\\s*UPDATE\\s+)`?permissions`?");
    private static final Pattern WHERE_ID_COMPARE = Pattern.compile("(?is)^`?id`?\\s*(=|>=|<=|>|<|!=|<>)\\s*'?(\\d+)'?$");
    private static final Pattern WHERE_ID_IN = Pattern.compile("(?is)^`?id`?\\s+IN\\s*\\(([\\d\\s,']+)\\)$");

    private static final Pattern SELECT_STATEMENT = Pattern.compile("(?is)^\\s*SELECT\\s+(.*?)\\s+FROM\\s+`?permissions`?\\b(.*?)\\s*;?\\s*$");
    private static final Pattern SELECT_FROM_REF = Pattern.compile("(?is)(\\bFROM\\s+)`?permissions`?\\b");
    private static final Pattern SELECT_WHERE_ID = Pattern.compile("(?is)^\\s*WHERE\\s+`?id`?\\s*=\\s*'?(\\d+)'?\\s*$");

    private static final Pattern DELETE_STATEMENT = Pattern.compile("(?is)^(\\s*DELETE\\s+FROM\\s+)`?permissions`?(\\b.*)$");
    private static final Pattern INSERT_STATEMENT = Pattern.compile("(?is)^(\\s*INSERT\\s+(?:IGNORE\\s+)?INTO\\s+)`?permissions`?(\\s*\\(([^)]*)\\).*)$");

    @Override
    public boolean appliesTo(String lowerCaseSql) {
        return containsWord(lowerCaseSql, LEGACY_TABLE);
    }

    @Override
    public String translate(String sql, TranslationContext context) throws SQLException {
        if (!context.isNormalizedPermissionsSchema()) {
            return null;
        }

        String head = sql.stripLeading().toUpperCase(Locale.ROOT);

        if (head.startsWith("ALTER")) {
            return this.translateAlter(sql);
        }

        if (head.startsWith("UPDATE")) {
            return this.translateUpdate(sql, context);
        }

        if (head.startsWith("SELECT")) {
            return this.translateSelect(sql, context);
        }

        if (head.startsWith("DELETE")) {
            Matcher delete = DELETE_STATEMENT.matcher(sql);
            return delete.matches() ? delete.group(1) + "`" + RANKS_TABLE + "`" + delete.group(2) : null;
        }

        if (head.startsWith("INSERT")) {
            return this.translateInsert(sql);
        }

        return null;
    }

    private String translateAlter(String sql) {
        Matcher matcher = ALTER_STATEMENT.matcher(sql);

        if (!matcher.matches()) {
            return null;
        }

        List<String> clauses = splitTopLevel(matcher.group(1), ',');
        List<String> addedKeys = new ArrayList<>();
        List<Integer> addedMaxValues = new ArrayList<>();
        List<String> droppedKeys = new ArrayList<>();
        boolean metadataOnly = true;

        for (String clause : clauses) {
            Matcher add = ALTER_ADD_CLAUSE.matcher(clause.trim());

            if (add.matches()) {
                String name = add.group(1).toLowerCase(Locale.ROOT);

                if (!RANK_METADATA_COLUMNS.contains(name)) {
                    metadataOnly = false;
                    addedKeys.add(name);
                    addedMaxValues.add(maxValueFromType(add.group(2)));
                }

                continue;
            }

            Matcher drop = ALTER_DROP_CLAUSE.matcher(clause.trim());

            if (drop.matches()) {
                String name = drop.group(1).toLowerCase(Locale.ROOT);

                if (!RANK_METADATA_COLUMNS.contains(name)) {
                    metadataOnly = false;
                    droppedKeys.add(name);
                }

                continue;
            }

            // Unrecognised clause (CHANGE, MODIFY, ...) — don't guess.
            return null;
        }

        if (metadataOnly) {
            // Rank-metadata schema change: same clause, new table.
            return "ALTER TABLE `" + RANKS_TABLE + "` " + matcher.group(1);
        }

        if (!addedKeys.isEmpty() && droppedKeys.isEmpty()) {
            StringBuilder insert = new StringBuilder("INSERT INTO " + DEFINITIONS_TABLE + " (permission_key, max_value, comment) VALUES ");

            for (int i = 0; i < addedKeys.size(); i++) {
                if (i > 0) {
                    insert.append(", ");
                }

                insert.append("('").append(addedKeys.get(i)).append("', ").append(addedMaxValues.get(i)).append(", '").append(REGISTERED_COMMENT).append("')");
            }

            return insert.toString();
        }

        if (!droppedKeys.isEmpty() && addedKeys.isEmpty()) {
            return "DELETE FROM " + DEFINITIONS_TABLE + " WHERE permission_key IN (" + quoteList(droppedKeys) + ")";
        }

        return null;
    }

    /**
     * The legacy enum told the emulator which values a permission accepts:
     * ENUM('0','1') caps at 1, ENUM('0','1','2') at 2 (2 = allowed with room
     * rights). Numeric column types default to 1.
     */
    private static int maxValueFromType(String typeSpec) {
        Matcher matcher = ENUM_TYPE.matcher(typeSpec.trim());

        if (matcher.find()) {
            int max = 1;

            for (String value : matcher.group(1).split(",")) {
                String cleaned = value.trim().replace("'", "").replace("\"", "");

                try {
                    max = Math.max(max, Integer.parseInt(cleaned));
                } catch (NumberFormatException ignored) {
                }
            }

            return max;
        }

        return 1;
    }

    private String translateUpdate(String sql, TranslationContext context) throws SQLException {
        Matcher matcher = UPDATE_STATEMENT.matcher(sql);

        if (!matcher.matches()) {
            return null;
        }

        String setAndWhere = matcher.group(2);
        String[] parts = splitTopLevelKeyword(setAndWhere, "WHERE");
        String setPart = parts[0].trim();
        String wherePart = parts[1] == null ? null : parts[1].trim();

        List<String> assignments = splitTopLevel(setPart, ',');
        List<String> names = new ArrayList<>();

        for (String assignment : assignments) {
            int eq = assignment.indexOf('=');

            if (eq < 1) {
                return null;
            }

            String name = stripTicks(assignment.substring(0, eq).trim()).toLowerCase(Locale.ROOT);

            if (!IDENTIFIER.matcher(name).matches()) {
                return null;
            }

            names.add(name);
        }

        boolean allMetadata = names.stream().allMatch(RANK_METADATA_COLUMNS::contains);
        boolean allPermissionKeys = names.stream().noneMatch(RANK_METADATA_COLUMNS::contains);

        if (allMetadata) {
            return UPDATE_TABLE_REF.matcher(sql).replaceFirst("$1`" + RANKS_TABLE + "`");
        }

        if (!allPermissionKeys) {
            return null;
        }

        // Permission values: every assignment must be a plain integer literal.
        List<Integer> values = new ArrayList<>();

        for (String assignment : assignments) {
            String value = assignment.substring(assignment.indexOf('=') + 1).trim();

            if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1).trim();
            }

            try {
                values.add(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        List<Integer> targetRanks = resolveTargetRanks(wherePart, context);

        if (targetRanks == null) {
            return null;
        }

        if (targetRanks.isEmpty()) {
            // Legacy statement matched no ranks; behave like a 0-row update.
            return "UPDATE " + DEFINITIONS_TABLE + " SET max_value = max_value WHERE 1 = 0";
        }

        StringBuilder update = new StringBuilder("UPDATE " + DEFINITIONS_TABLE + " SET ");

        for (int r = 0; r < targetRanks.size(); r++) {
            int rankId = targetRanks.get(r);

            if (r > 0) {
                update.append(", ");
            }

            update.append("rank_").append(rankId).append(" = CASE permission_key");

            for (int i = 0; i < names.size(); i++) {
                update.append(" WHEN '").append(names.get(i)).append("' THEN ").append(values.get(i));
            }

            update.append(" ELSE rank_").append(rankId).append(" END");
        }

        update.append(" WHERE permission_key IN (").append(quoteList(names)).append(")");
        return update.toString();
    }

    /**
     * @return the rank ids a legacy WHERE clause addresses, an empty list when
     * it addresses none, or null when the clause isn't understood (including
     * prepared-statement placeholders).
     */
    private static List<Integer> resolveTargetRanks(String wherePart, TranslationContext context) throws SQLException {
        List<Integer> rankIds = context.rankIds();

        if (wherePart == null || wherePart.isEmpty()) {
            return rankIds;
        }

        if (wherePart.indexOf('?') >= 0) {
            return null;
        }

        Matcher compare = WHERE_ID_COMPARE.matcher(wherePart);

        if (compare.matches()) {
            String operator = compare.group(1);
            int bound = Integer.parseInt(compare.group(2));
            List<Integer> matched = new ArrayList<>();

            for (int rankId : rankIds) {
                boolean matches = switch (operator) {
                    case "=" -> rankId == bound;
                    case ">=" -> rankId >= bound;
                    case "<=" -> rankId <= bound;
                    case ">" -> rankId > bound;
                    case "<" -> rankId < bound;
                    default -> rankId != bound; // != and <>
                };

                if (matches) {
                    matched.add(rankId);
                }
            }

            return matched;
        }

        Matcher in = WHERE_ID_IN.matcher(wherePart);

        if (in.matches()) {
            List<Integer> matched = new ArrayList<>();

            for (String value : in.group(1).split(",")) {
                try {
                    int rankId = Integer.parseInt(value.trim().replace("'", ""));

                    if (rankIds.contains(rankId)) {
                        matched.add(rankId);
                    }
                } catch (NumberFormatException e) {
                    return null;
                }
            }

            return matched;
        }

        return null;
    }

    private String translateSelect(String sql, TranslationContext context) throws SQLException {
        Matcher matcher = SELECT_STATEMENT.matcher(sql);

        if (!matcher.matches()) {
            return null;
        }

        String columnsPart = matcher.group(1).trim();
        String tail = matcher.group(2);

        if (columnsPart.equals("*")) {
            return SELECT_FROM_REF.matcher(sql).replaceFirst("$1`" + RANKS_TABLE + "`");
        }

        List<String> columns = new ArrayList<>();

        for (String column : splitTopLevel(columnsPart, ',')) {
            String name = stripTicks(column.trim());

            if (!IDENTIFIER.matcher(name).matches()) {
                return null;
            }

            columns.add(name.toLowerCase(Locale.ROOT));
        }

        if (columns.stream().allMatch(RANK_METADATA_COLUMNS::contains)) {
            return SELECT_FROM_REF.matcher(sql).replaceFirst("$1`" + RANKS_TABLE + "`");
        }

        if (columns.stream().anyMatch(RANK_METADATA_COLUMNS::contains)) {
            return null;
        }

        // Pure permission lookup: only supported for WHERE id = <literal>.
        Matcher where = SELECT_WHERE_ID.matcher(tail);

        if (!where.matches()) {
            return null;
        }

        int rankId = Integer.parseInt(where.group(1));
        StringBuilder select = new StringBuilder("SELECT ");

        if (!context.rankIds().contains(rankId)) {
            // Unknown rank: the legacy query would have returned no rows.
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    select.append(", ");
                }

                select.append("NULL AS `").append(columns.get(i)).append("`");
            }

            select.append(" FROM ").append(DEFINITIONS_TABLE).append(" WHERE 1 = 0");
            return select.toString();
        }

        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                select.append(", ");
            }

            select.append("MAX(CASE WHEN permission_key = '").append(columns.get(i))
                    .append("' THEN rank_").append(rankId).append(" END) AS `").append(columns.get(i)).append("`");
        }

        select.append(" FROM ").append(DEFINITIONS_TABLE);
        return select.toString();
    }

    private String translateInsert(String sql) {
        Matcher matcher = INSERT_STATEMENT.matcher(sql);

        if (!matcher.matches()) {
            return null;
        }

        for (String column : splitTopLevel(matcher.group(3), ',')) {
            String name = stripTicks(column.trim()).toLowerCase(Locale.ROOT);

            if (!RANK_METADATA_COLUMNS.contains(name)) {
                return null;
            }
        }

        // New rank with metadata only: PermissionsManager adds the missing
        // rank_<id> definition column on the next reload.
        return matcher.group(1) + "`" + RANKS_TABLE + "`" + matcher.group(2);
    }

    static boolean containsWord(String haystack, String word) {
        int index = haystack.indexOf(word);

        while (index >= 0) {
            boolean startOk = index == 0 || !isWordChar(haystack.charAt(index - 1));
            int end = index + word.length();
            boolean endOk = end >= haystack.length() || !isWordChar(haystack.charAt(end));

            if (startOk && endOk) {
                return true;
            }

            index = haystack.indexOf(word, index + 1);
        }

        return false;
    }

    private static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    private static String stripTicks(String value) {
        if (value.length() >= 2 && value.startsWith("`") && value.endsWith("`")) {
            return value.substring(1, value.length() - 1);
        }

        return value;
    }

    private static String quoteList(List<String> names) {
        StringBuilder joined = new StringBuilder();

        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                joined.append(", ");
            }

            joined.append("'").append(names.get(i)).append("'");
        }

        return joined.toString();
    }

    /**
     * Splits on a separator, ignoring separators inside quotes or parentheses.
     */
    static List<String> splitTopLevel(String value, char separator) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inQuote = false;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            if (inQuote) {
                current.append(c);

                if (c == '\'') {
                    inQuote = false;
                }

                continue;
            }

            switch (c) {
                case '\'' -> {
                    inQuote = true;
                    current.append(c);
                }
                case '(' -> {
                    depth++;
                    current.append(c);
                }
                case ')' -> {
                    depth--;
                    current.append(c);
                }
                default -> {
                    if (c == separator && depth == 0) {
                        parts.add(current.toString());
                        current.setLength(0);
                    } else {
                        current.append(c);
                    }
                }
            }
        }

        if (current.length() > 0) {
            parts.add(current.toString());
        }

        return parts;
    }

    /**
     * Splits "a WHERE b" into [a, b] at the first top-level occurrence of the
     * keyword; the second element is null when the keyword is absent.
     */
    static String[] splitTopLevelKeyword(String value, String keyword) {
        String lower = value.toLowerCase(Locale.ROOT);
        String needle = keyword.toLowerCase(Locale.ROOT);
        int depth = 0;
        boolean inQuote = false;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            if (inQuote) {
                if (c == '\'') {
                    inQuote = false;
                }

                continue;
            }

            if (c == '\'') {
                inQuote = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0 && lower.startsWith(needle, i)
                    && (i == 0 || Character.isWhitespace(value.charAt(i - 1)))
                    && (i + needle.length() >= value.length() || Character.isWhitespace(value.charAt(i + needle.length())))) {
                return new String[]{value.substring(0, i), value.substring(i + needle.length())};
            }
        }

        return new String[]{value, null};
    }
}
