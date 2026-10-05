package com.eu.habbo.habbohotel.rooms;

import java.util.concurrent.locks.ReentrantLock;

/**
 * One lock per room and user (striped), shared by every instance of a room: loading a user who
 * enters and the web API's reads and writes of that user's saved rows take it, so a saved-row write
 * never races the load. Holders of a stripe never wait for another stripe.
 */
final class UserVariableLocks {
    private static final int STRIPES = 1024;
    private static final ReentrantLock[] LOCKS = createLocks();

    private UserVariableLocks() {}

    static ReentrantLock of(int roomId, int userId) {
        int hash = roomId * 0x9E3779B9 + userId;
        hash ^= hash >>> 16;
        return LOCKS[hash & (STRIPES - 1)];
    }

    private static ReentrantLock[] createLocks() {
        ReentrantLock[] locks = new ReentrantLock[STRIPES];
        for (int index = 0; index < STRIPES; index++) {
            locks[index] = new ReentrantLock();
        }
        return locks;
    }
}
