package com.eu.habbo.database.integrity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegritySqlTest {
    @Test
    void relationSqlIsQuotedReadOnlyAndHonoursZeroSentinels() {
        RelationRequirement requirement = new RelationRequirement(
                "messenger.friend-category-owner",
                "messenger_friendships",
                List.of("category", "user_one_id"),
                "messenger_categories",
                List.of("id", "user_id"),
                List.of("category"),
                "category belongs to friendship owner",
                IntegrityCheckSource.LOGICAL_CONTRACT);

        String count = IntegritySql.relationCount(requirement);
        String samples = IntegritySql.relationSamples(requirement, 5);

        assertTrue(count.startsWith("SELECT COUNT(*)"));
        assertTrue(count.contains("c.`category` = p.`id`"));
        assertTrue(count.contains("c.`user_one_id` = p.`user_id`"));
        assertTrue(count.contains("c.`category` <> 0"));
        assertTrue(samples.endsWith("LIMIT 5"));
        assertFalse(count.toUpperCase().contains("DELETE"));
        assertFalse(count.toUpperCase().contains("UPDATE"));
    }

    @Test
    void duplicateSqlReportsGroupsAndExcessRows() {
        DuplicateRequirement requirement = new DuplicateRequirement(
                "room-rights.unique-user",
                "room_rights",
                List.of("room_id", "user_id"),
                "one rights row per room and user");

        String count = IntegritySql.duplicateCount(requirement);
        String samples = IntegritySql.duplicateSamples(requirement, 3);

        assertTrue(count.contains("SUM(group_size - 1)"));
        assertTrue(count.contains("GROUP BY `room_id`, `user_id`"));
        assertTrue(samples.contains("HAVING COUNT(*) > 1"));
        assertTrue(samples.endsWith("LIMIT 3"));
    }

    @Test
    void requirementConstructionRejectsIdentifierInjection() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DuplicateRequirement(
                        "bad", "users` WHERE 1=1 --", List.of("id"), "bad"));
    }
}
