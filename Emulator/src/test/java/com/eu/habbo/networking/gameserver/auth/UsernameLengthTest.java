package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** users.username is varchar(25): a name of 26 to 32 characters passed the check and failed the INSERT. */
class UsernameLengthTest {

    @Test
    void aNameFitsTheColumn() {
        assertTrue(AuthHttpUtil.USERNAME_RE.matcher("a".repeat(25)).matches());
        assertFalse(AuthHttpUtil.USERNAME_RE.matcher("a".repeat(26)).matches());
        assertFalse(AuthHttpUtil.USERNAME_RE.matcher("ab").matches());
    }
}
