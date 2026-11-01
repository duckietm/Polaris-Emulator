package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Registration was not transactional, two registrations from one IP could both pass the per-IP quota, and two from
 * different IPs could both take the same e-mail address.
 */
class RegistrationTransactionContractTest {

    @Test
    void oneIpAlwaysGetsTheSameLock() {
        assertSame(SessionEndpoints.registrationLock("10.0.0.1"), SessionEndpoints.registrationLock("10.0.0.1"));
        assertSame(SessionEndpoints.registrationLock(null), SessionEndpoints.registrationLock(null));
    }

    @Test
    void oneAddressAlwaysGetsTheSameLockWhateverItsCase() {
        assertSame(
                SessionEndpoints.registrationEmailLock("Someone@Hotel.com"),
                SessionEndpoints.registrationEmailLock("someone@hotel.com"));
    }

    @Test
    void theAddressLockIsTakenInsideTheIpLock() throws Exception {
        String endpoints = Files.readString(
                Path.of("src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java"));

        int ipLock = endpoints.indexOf("synchronized (registrationLock(ip)) {");
        int emailLock = endpoints.indexOf("synchronized (registrationEmailLock(email)) {", ipLock);
        int register = endpoints.indexOf("register(ctx, req, config, ip, username, email,", emailLock);
        assertTrue(ipLock > 0 && emailLock > ipLock && register > emailLock);
    }

    @Test
    void theQuotaCheckAndTheInsertShareOneLockedTransaction() throws Exception {
        String endpoints = Files.readString(
                Path.of("src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java"));

        int lock = endpoints.indexOf("synchronized (registrationLock(ip)) {");
        int autoCommit = endpoints.indexOf("conn.setAutoCommit(false);", lock);
        int commit = endpoints.indexOf("conn.commit();", autoCommit);
        int ok = endpoints.indexOf("sendJson(ctx, req, HttpResponseStatus.OK, ok);", commit);
        assertTrue(lock > 0 && autoCommit > lock && commit > autoCommit && ok > commit);
        assertTrue(endpoints.indexOf("conn.rollback();", autoCommit) > autoCommit);

        int body = endpoints.indexOf("private static boolean registerInTransaction(");
        assertTrue(endpoints.indexOf("SELECT COUNT(*) FROM users WHERE ip_register = ?", body) > body);
        assertTrue(endpoints.indexOf("INSERT INTO users (username", body) > body);
    }
}
