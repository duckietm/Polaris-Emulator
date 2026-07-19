package com.eu.habbo.database.indexing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexContractLoaderTest {
    @Test
    void packagedContractContainsTheAuditedQueryPaths() {
        IndexContract contract = IndexContractLoader.load(getClass().getClassLoader());

        assertTrue(contract.requirements().size() >= 25);
        assertTrue(contract.requirements().contains(new IndexRequirement(
                "idx_users_badges_user_badge_slot",
                "users_badges",
                List.of("user_id", "badge_code", "slot_id"),
                "Load and mutate a user's badges without scanning the badge table")));
        assertTrue(contract.requirements().contains(new IndexRequirement(
                "idx_room_rights_user_room",
                "room_rights",
                List.of("user_id", "room_id"),
                "Load rooms where a user has rights")));
        assertTrue(contract.requirements().contains(new IndexRequirement(
                "idx_wired_rewards_item_user_time",
                "wired_rewards_given",
                List.of("wired_item", "user_id", "timestamp"),
                "Load recent rewards and delete rewards for a Wired item")));

        Set<String> names = contract.requirements().stream()
                .map(requirement -> requirement.table() + "." + requirement.name())
                .collect(Collectors.toSet());
        assertEquals(contract.requirements().size(), names.size());
    }
}
