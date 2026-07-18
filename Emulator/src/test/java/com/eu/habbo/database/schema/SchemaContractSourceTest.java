package com.eu.habbo.database.schema;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaContractSourceTest {
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+`([^`]+)`\\s*\\((.*?)\\)\\s*"
                    + "(?:ENGINE\\s*=\\s*[^;]+)?;",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern COLUMN = Pattern.compile("(?m)^\\s*`([^`]+)`\\s+");

    @Test
    void packagedContractMatchesTheCanonicalDatabase() throws Exception {
        String sql = Files.readString(Path.of(
                "../Database/Default Database/FullDatabase.sql"));
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        Matcher tables = CREATE_TABLE.matcher(sql);
        while (tables.find()) {
            Set<String> columns = new LinkedHashSet<>();
            Matcher column = COLUMN.matcher(tables.group(2));
            while (column.find()) columns.add(column.group(1).toLowerCase(java.util.Locale.ROOT));
            expected.put(tables.group(1).toLowerCase(java.util.Locale.ROOT), columns);
        }
        expected.get("pet_actions").remove("id");

        SchemaContract actual = SchemaContractLoader.load(getClass().getClassLoader());

        assertEquals(146, expected.size());
        assertEquals(expected, actual.tables());
    }
}
