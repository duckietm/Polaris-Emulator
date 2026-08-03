package com.eu.habbo.networking.gameserver.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class CmsCommandScopesTest {

    @Test
    void wildcardAllowsAnyNonDeniedCommand() {
        assertTrue(CmsCommandScopes.isAllowed(Set.of("*"), "givecredits"));
        assertTrue(CmsCommandScopes.isAllowed(Set.of("*"), "setrank"));
    }

    @Test
    void wildcardNeverAllowsStressCommands() {
        assertFalse(CmsCommandScopes.isAllowed(Set.of("*"), "stressstart"));
        assertFalse(CmsCommandScopes.isAllowed(Set.of("stressstart"), "stressstart"));
        assertTrue(CmsCommandScopes.isAlwaysDenied("stressstop"));
    }

    @Test
    void groupWildcardAllowsOnlyThatGroup() {
        assertTrue(CmsCommandScopes.isAllowed(Set.of("economy:*"), "givecredits"));
        assertTrue(CmsCommandScopes.isAllowed(Set.of("economy:*"), "sendgift"));
        assertFalse(CmsCommandScopes.isAllowed(Set.of("economy:*"), "setrank"));
    }

    @Test
    void groupCommandScopeAllowsOnlyThatCommand() {
        assertTrue(CmsCommandScopes.isAllowed(Set.of("users:setrank"), "setrank"));
        assertFalse(CmsCommandScopes.isAllowed(Set.of("users:setrank"), "givebadge"));
    }

    @Test
    void bareCommandScopeIsNormalized() {
        assertTrue(CmsCommandScopes.isAllowed(Set.of("givecredits"), "give_credits"));
        assertTrue(CmsCommandScopes.isAllowed(Set.of("givecredits"), "GIVECREDITS"));
    }

    @Test
    void emptyScopesAllowNothing() {
        assertFalse(CmsCommandScopes.isAllowed(Set.of(), "givecredits"));
    }

    @Test
    void groupsAreMappedForKnownCommands() {
        assertEquals("economy", CmsCommandScopes.groupFor("givecredits"));
        assertEquals("moderation", CmsCommandScopes.groupFor("executecommand"));
        assertEquals("cache", CmsCommandScopes.groupFor("updatecatalog"));
    }
}
