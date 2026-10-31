package com.eu.habbo.database.compat;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plain old-name -> new-name table translation, driven by the
 * polaris.legacy.bridge.table_renames config value ("old:new;old2:new2").
 *
 * Meant for tables that were renamed 1:1 during the Polaris rename. Structural
 * changes (like the permissions split) need their own translator — see
 * {@link LegacyPermissionsSqlTranslator}.
 */
public class LegacyTableRenameTranslator implements LegacySqlTranslator {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    // Table names only get replaced in table position, right after one of
    // these keywords, so column names that happen to match stay untouched.
    private static final String TABLE_POSITION_KEYWORDS = "(?:from|join|into|update|table|describe|exists)";

    private final Supplier<String> configuredRenames;

    private volatile String parsedFrom = null;
    private volatile Map<String, String> renames = Collections.emptyMap();

    private final Map<String, Pattern> patternCache = new LinkedHashMap<>();

    public LegacyTableRenameTranslator() {
        this(() -> {
            ConfigurationManager config = Emulator.getConfig();
            return config == null ? "" : config.getValue(LegacySqlBridge.CONFIG_TABLE_RENAMES, "");
        });
    }

    LegacyTableRenameTranslator(Supplier<String> configuredRenames) {
        this.configuredRenames = configuredRenames;
    }

    Map<String, String> renames() {
        String raw = this.configuredRenames.get();

        if (raw == null) {
            raw = "";
        }

        if (!raw.equals(this.parsedFrom)) {
            Map<String, String> parsed = new LinkedHashMap<>();

            for (String pair : raw.split(";")) {
                String[] parts = pair.split(":");

                if (parts.length != 2) {
                    continue;
                }

                String oldName = parts[0].trim().toLowerCase();
                String newName = parts[1].trim();

                if (IDENTIFIER.matcher(oldName).matches() && IDENTIFIER.matcher(newName).matches()) {
                    parsed.put(oldName, newName);
                }
            }

            synchronized (this) {
                this.renames = Collections.unmodifiableMap(parsed);
                this.patternCache.clear();
                this.parsedFrom = raw;
            }
        }

        return this.renames;
    }

    @Override
    public boolean appliesTo(String lowerCaseSql) {
        Map<String, String> map = this.renames();

        if (map.isEmpty()) {
            return false;
        }

        for (String oldName : map.keySet()) {
            if (lowerCaseSql.contains(oldName)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String translate(String sql, TranslationContext context) {
        String result = sql;

        synchronized (this) {
            for (Map.Entry<String, String> entry : this.renames().entrySet()) {
                Pattern pattern = this.patternCache.computeIfAbsent(entry.getKey(), oldName ->
                        Pattern.compile("(?i)\\b(" + TABLE_POSITION_KEYWORDS + ")(\\s+)`?" + Pattern.quote(oldName) + "\\b`?"));

                result = replaceOutsideLiterals(
                        result, pattern, "$1$2`" + Matcher.quoteReplacement(entry.getValue()) + "`");
            }
        }

        return result.equals(sql) ? null : result;
    }

    /**
     * Applies the rename only outside quoted strings: a table name inside a value, such as a message that says
     * "from users", is data and must stay as it was.
     */
    static String replaceOutsideLiterals(String sql, Pattern pattern, String replacement) {
        StringBuilder out = new StringBuilder(sql.length());
        int segmentStart = 0;
        int i = 0;

        while (i < sql.length()) {
            char c = sql.charAt(i);

            if (c != '\'' && c != '"') {
                i++;
                continue;
            }

            out.append(pattern.matcher(sql.substring(segmentStart, i)).replaceAll(replacement));

            int end = i + 1;
            while (end < sql.length()) {
                char d = sql.charAt(end);
                if (d == '\\') {
                    end += 2;
                } else if (d == c && end + 1 < sql.length() && sql.charAt(end + 1) == c) {
                    end += 2;
                } else if (d == c) {
                    break;
                } else {
                    end++;
                }
            }

            int literalEnd = Math.min(end + 1, sql.length());
            out.append(sql, i, literalEnd);
            segmentStart = literalEnd;
            i = literalEnd;
        }

        out.append(pattern.matcher(sql.substring(segmentStart)).replaceAll(replacement));
        return out.toString();
    }
}
