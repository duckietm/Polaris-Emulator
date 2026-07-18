package com.eu.habbo.database.compat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class MariaDbSqlCompatibilityAudit {
    private static final List<Rule> RULES = List.of(
            new Rule("PostgreSQL ILIKE", Pattern.compile("\\bILIKE\\b", Pattern.CASE_INSENSITIVE)),
            new Rule("PostgreSQL NULLS FIRST/LAST", Pattern.compile("\\bNULLS\\s+(?:FIRST|LAST)\\b", Pattern.CASE_INSENSITIVE)),
            new Rule("SQLite INSERT OR IGNORE/REPLACE", Pattern.compile("\\bINSERT\\s+OR\\s+(?:IGNORE|REPLACE)\\b", Pattern.CASE_INSENSITIVE)),
            new Rule("SQLite AUTOINCREMENT", Pattern.compile("\\bAUTOINCREMENT\\b", Pattern.CASE_INSENSITIVE)),
            new Rule("PostgreSQL ON CONFLICT", Pattern.compile("\\bON\\s+CONFLICT\\b", Pattern.CASE_INSENSITIVE)),
            new Rule("PostgreSQL FILTER WHERE", Pattern.compile("\\bFILTER\\s*\\(\\s*WHERE\\b", Pattern.CASE_INSENSITIVE)),
            new Rule("PostgreSQL cast operator", Pattern.compile("::\\s*[a-z][a-z0-9_]*", Pattern.CASE_INSENSITIVE)),
            new Rule("reserved ROW_NUMBER alias", Pattern.compile("\\bAS\\s+ROW_NUMBER\\b", Pattern.CASE_INSENSITIVE))
    );

    private MariaDbSqlCompatibilityAudit() {
    }

    public static List<Finding> scanRepository(Path repositoryRoot) throws IOException {
        List<Path> files = new ArrayList<>();
        collect(files, repositoryRoot.resolve("Emulator/src/main/java"), ".java", true);
        collect(files, repositoryRoot.resolve("Database/Database Updates"), ".sql", false);
        files.removeIf(path -> path.getFileName().toString().equals("MariaDbSqlCompatibilityAudit.java"));
        files.sort(Comparator.comparing(Path::toString));

        List<Finding> findings = new ArrayList<>();
        for (Path file : files) {
            String location = repositoryRoot.relativize(file).toString().replace('\\', '/');
            String source = Files.readString(file);
            findings.addAll(file.getFileName().toString().endsWith(".java")
                    ? scanJavaStrings(location, source)
                    : scan(location, source));
        }
        return List.copyOf(findings);
    }

    public static List<Finding> scan(String location, String sql) {
        return scan(location, sql, 1);
    }

    private static List<Finding> scan(String location, String sql, int firstLine) {
        List<Finding> findings = new ArrayList<>();
        String[] lines = sql.split("\\R", -1);
        for (int lineNumber = 0; lineNumber < lines.length; lineNumber++) {
            String line = lines[lineNumber];
            for (Rule rule : RULES) {
                if (rule.pattern().matcher(line).find()) {
                    findings.add(new Finding(location + ":" + (firstLine + lineNumber), rule.description(), line.strip()));
                }
            }
        }
        return List.copyOf(findings);
    }

    private static List<Finding> scanJavaStrings(String location, String source) {
        List<Finding> findings = new ArrayList<>();
        int line = 1;
        for (int index = 0; index < source.length();) {
            char current = source.charAt(index);
            if (current == '\n') {
                line++;
                index++;
                continue;
            }
            if (current == '/' && index + 1 < source.length() && source.charAt(index + 1) == '/') {
                index += 2;
                while (index < source.length() && source.charAt(index) != '\n') index++;
                continue;
            }
            if (current == '/' && index + 1 < source.length() && source.charAt(index + 1) == '*') {
                index += 2;
                while (index + 1 < source.length()
                        && !(source.charAt(index) == '*' && source.charAt(index + 1) == '/')) {
                    if (source.charAt(index++) == '\n') line++;
                }
                index = Math.min(source.length(), index + 2);
                continue;
            }
            if (current == '\'' ) {
                index = skipQuoted(source, index + 1, '\'');
                continue;
            }
            if (current != '"') {
                index++;
                continue;
            }

            boolean textBlock = index + 2 < source.length()
                    && source.charAt(index + 1) == '"' && source.charAt(index + 2) == '"';
            int startLine = line;
            int contentStart = index + (textBlock ? 3 : 1);
            int end = contentStart;
            if (textBlock) {
                while (end + 2 < source.length()
                        && !(source.charAt(end) == '"' && source.charAt(end + 1) == '"'
                        && source.charAt(end + 2) == '"')) {
                    if (source.charAt(end++) == '\n') line++;
                }
            } else {
                boolean escaped = false;
                while (end < source.length()) {
                    char value = source.charAt(end);
                    if (value == '\n') line++;
                    if (!escaped && value == '"') break;
                    escaped = !escaped && value == '\\';
                    if (value != '\\') escaped = false;
                    end++;
                }
            }

            String literal = source.substring(contentStart, Math.min(end, source.length()));
            if (looksLikeSql(literal)) findings.addAll(scan(location, literal, startLine));
            index = Math.min(source.length(), end + (textBlock ? 3 : 1));
        }
        return findings;
    }

    private static int skipQuoted(String source, int index, char delimiter) {
        boolean escaped = false;
        while (index < source.length()) {
            char value = source.charAt(index++);
            if (!escaped && value == delimiter) break;
            escaped = !escaped && value == '\\';
            if (value != '\\') escaped = false;
        }
        return index;
    }

    private static boolean looksLikeSql(String value) {
        return Pattern.compile("\\b(?:SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|WITH|MERGE|REPLACE)\\b",
                Pattern.CASE_INSENSITIVE).matcher(value).find();
    }

    private static void collect(List<Path> target, Path root, String suffix, boolean recursive) throws IOException {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> paths = recursive ? Files.walk(root) : Files.list(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(suffix))
                    .forEach(target::add);
        }
    }

    private record Rule(String description, Pattern pattern) {
    }

    public record Finding(String location, String rule, String source) {
        @Override
        public String toString() {
            return location + " [" + rule + "] " + source;
        }
    }
}
