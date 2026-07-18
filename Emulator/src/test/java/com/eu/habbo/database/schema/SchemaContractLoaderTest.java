package com.eu.habbo.database.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaContractLoaderTest {
    @Test
    void loadsTheCompletePackagedDatabaseContract() {
        SchemaContract contract = SchemaContractLoader.load(getClass().getClassLoader());

        assertEquals(146, contract.tables().size());
        assertTrue(contract.tables().get("users").containsAll(
                java.util.Set.of("id", "username", "mail")));
        assertTrue(contract.tables().get("messenger_messages").contains("id"));
        assertFalse(contract.tables().get("pet_actions").contains("id"));
    }
}
