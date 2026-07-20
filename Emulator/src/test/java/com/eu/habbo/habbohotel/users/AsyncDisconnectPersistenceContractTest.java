package com.eu.habbo.habbohotel.users;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncDisconnectPersistenceContractTest {

    @Test
    void disconnectReleasesItsMonitorBeforeDurableSaves() throws Exception {
        String source = source("Habbo.java");
        int disconnect = source.indexOf("public void disconnect()");
        int monitor = source.indexOf("synchronized (this)", disconnect);
        int leaveRoom = source.indexOf(".leaveRoom(", monitor);
        int removeHabbo = source.indexOf("habboManager.removeHabbo(this)", leaveRoom);
        int submit = source.indexOf("submitDisconnectPersistence(", removeHabbo);
        int persistence = source.indexOf("private void persistDisconnect()", submit);
        int userSave = source.indexOf("this.run()", persistence);
        int inventorySave = source.indexOf("this.getInventory().dispose()", userSave);
        int achievementSave = source.indexOf(
                "AchievementManager.saveAchievements(this)",
                inventorySave);
        int statsSave = source.indexOf("this.habboStats.dispose()", achievementSave);

        assertTrue(disconnect > -1);
        assertFalse(source.contains("public synchronized void disconnect()"));
        assertTrue(monitor > disconnect);
        assertTrue(leaveRoom > monitor);
        assertTrue(submit > removeHabbo);
        assertTrue(userSave > persistence);
        assertTrue(inventorySave > userSave);
        assertTrue(achievementSave > inventorySave);
        assertTrue(statsSave > achievementSave);
    }

    @Test
    void loginWaitsAgainAfterItDisposesADuplicateSession() throws Exception {
        String source = source("HabboManager.java");
        int load = source.indexOf("public Habbo loadHabbo(");
        int firstWait = source.indexOf(
                "awaitDisconnectPersistence(userId)",
                load);
        int cloneCheck = source.indexOf("this.cloneCheck(userId)", firstWait);
        int forceDispose = source.indexOf("forceDisposeClient(", cloneCheck);
        int secondWait = source.indexOf(
                "awaitDisconnectPersistence(userId)",
                forceDispose);
        int reload = source.indexOf(
                "SELECT * FROM users WHERE auth_ticket",
                secondWait);

        assertTrue(firstWait > load);
        assertTrue(cloneCheck > firstWait);
        assertTrue(forceDispose > cloneCheck);
        assertTrue(secondWait > forceDispose);
        assertTrue(reload > secondWait);
    }

    private static String source(String file) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/users",
                file));
    }
}
